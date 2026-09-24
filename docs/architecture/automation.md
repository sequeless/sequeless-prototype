# Automation: outbox, relay, and durable actions

Phase 5 (DR-09) makes a type's `sq:StateMachine` fire actions when a transition succeeds. This
document describes what actually landed: the outbox contract, the relay that drains it, the two
`AutomationPort` adapters (`inprocess`, `temporal`), the idempotency mechanisms that make a
retried or redispatched action safe, and how to run Temporal locally for development and tests.
It does not restate the vocabulary — see `sq-vocabulary.md`'s "Terms in use (phase 5)" section for
`sq:StateMachine`/`sq:State`/`sq:Transition`/action-kind terms — or the guard/interpreter design;
this document starts where a transition has already been decided available and is being fired.

## 1. Overview

`DefaultTransitionService.fire` (`sequeless-core`, `org.sequeless.core.usecase`) moves an object's
`state` and, in the same `ChangeSet`/`ObjectStorePort#commit` call, writes one `TransitionFired`
outbox event plus one `ActionRequest` outbox event per `sq:action` the fired transition defines —
all committed atomically with the state change, so there is no window where the state has moved
but the record of what should happen next has not been durably written (or vice versa). `sq_outbox`
is the same transactional-outbox table `ObjectCreated`/`ObjectUpdated`/`ObjectDeleted` events have
used since DR-12; Phase 5 adds two new `kind` values to it and a `processed_at` marker column.
Phase 6 (DR-11) adds three more `kind` values (§2), for a total of eight, seven of which are ever
claimed for dispatch (§9's "dispatchable set" — `TransitionFired` stays audit-only).

`OutboxRelay` (`sequeless-app`, `org.sequeless.app.automation`) is a `@Scheduled` poller that
drains the full **dispatchable** outbox kind set, one row at a time, into whichever
`AutomationPort` adapter is currently configured via `sequeless.automation.adapter` — `inprocess`
(synchronous, in the relay's own thread; for tests and local development) or `temporal` (durable,
retried, the production default per DR-09). Both adapters call back into the same `ActionExecutor`
implementation (`DefaultActionExecutor` in `sequeless-core`) to actually apply an `ActionRequest`;
the other dispatchable kinds route to `TriggerEvaluator`/`DerivationRecomputer` instead — see §9.

Phase 6 (DR-11) widens this considerably: what was originally a single `ActionRequest`-only queue
is now a 7-kind dispatchable set (§2), and the adapter's `dispatch()` method is a switch over
`kind()`, not a single hard-coded call.

## 2. The outbox and the `ActionRequest` payload shape

`OutboxEntry` (`sequeless-spi/src/main/java/org/sequeless/spi/object/OutboxEntry.java`) is the one
record type every outbox row uses, keyed by a `kind` string. Phase 5 adds two constants to it,
deliberately declared on `OutboxEntry` itself rather than on `DefaultTransitionService`: an
adapter that must filter on `kind` (the relay, an `AutomationPort`) cannot depend on
`sequeless-core`, and the `noAdapterDependsOnCore` ArchUnit rule forbids it from trying.

- `OutboxEntry.KIND_TRANSITION_FIRED = "TransitionFired"` — an audit event, one per fired
  transition. This is the **only** kind that is never claimed for dispatch — every other kind
  `OutboxEntry` defines is in the dispatchable set (§9).
- `OutboxEntry.KIND_ACTION_REQUEST = "ActionRequest"` — one per `sq:action` in the fired
  transition's action list, carrying a payload that is fully self-contained and frozen at
  transition-fire time.

Phase 6 (DR-11) adds three more constants, all documented directly on `OutboxEntry` itself (the
authoritative list — this document doesn't repeat their payload shapes beyond what's useful here;
see the javadoc for the exact field list of each):

- `OutboxEntry.KIND_TIMER_SCHEDULED = "TimerScheduled"` — recorded when an object enters a state a
  timer-triggered transition waits in.
- `OutboxEntry.KIND_TIMER_CANCELLED = "TimerCancelled"` — recorded when an object leaves that state
  for any reason, whether the timer transition itself fired or some other transition departed
  first.
- `OutboxEntry.KIND_SIGNAL_RECEIVED = "SignalReceived"` — recorded when `POST
  /objects/{type}/{id}/signals/{name}` is called (§12).

Together with the three pre-existing object-lifecycle kinds (`ObjectCreated`/`ObjectUpdated`/
`ObjectDeleted`, in use since DR-12) and `ActionRequest`, that makes **seven dispatchable kinds**
in total — `OutboxRelay` claims across all seven in one poll loop (§3, §9); only
`TransitionFired` is excluded.

`OutboxEntry`'s constructor enforces the payload is JSON-compatible end to end (`String`,
`Number`, `Boolean`, or nested `List`/`Map` of the same; no `null` anywhere) and contains a
`String`-valued `objectId` entry — checked eagerly so a later JSON-serializing adapter fails fast
here, not deep inside a codec.

### `TransitionFired` payload (built by `DefaultTransitionService.transitionFiredEntry`)

All `String`: `objectId`, `tenantId`, `principalId`, `transitionName`, `fromState`, `toState`
(the transition's `fromStateIri`/`toStateIri`, i.e. full state IRIs).

### `ActionRequest` payload (built by `DefaultTransitionService.actionRequestPayload`)

Fields common to every action kind:

| Field | Type | Meaning |
|---|---|---|
| `objectId` | `String` | the transitioning object's id (also required by `OutboxEntry` itself) |
| `tenantId`, `principalId` | `String` | reconstruct a `Scope` for the `ActionExecutor` callback |
| `transitionName` | `String` | provenance |
| `actionIndex` | `Integer`, 0-based | provenance, and (with `objectId`/`entry.id()`) the seed `applyCreateObject`'s idempotent id derivation uses (§5) |
| `typeIri` | `String` | the transitioning object's type |
| `state` | `String` | the object's **new** state — the transition's `toStateIri` — not its state before the transition |
| `self` | `Map<String, Object>` | a snapshot of the transitioning object's own properties, keyed by property **short name** (not IRI), each value tagged per `PayloadValueCodec`'s scheme |
| `actionKind` | `String` | one of `"SetProperty"`, `"CreateObject"`, `"Webhook"`, `"Log"` |

Kind-specific fields, each present only when the underlying `sq:` node defines it (an absent
optional field is omitted from the map entirely — never a `null` placeholder, since `OutboxEntry`
rejects `null` anywhere in the payload):

- **`SetProperty`**: `property` (full property IRI) plus exactly one of `value` (a
  `PayloadValueCodec`-tagged map) or `expression` (bare JEXL source, no `${}`).
- **`CreateObject`**: `createType` (full type IRI to create), `createProperties`
  (`Map<String, Object>` keyed by full property IRI, each value itself a nested map holding
  exactly one of `value`/`expression`, same shape as `SetProperty`'s pair).
- **`Webhook`**: `url` (JXLT `${expr}` template), `method` (literal HTTP method string), `body`
  (JXLT template, present iff the `sq:Webhook` node defined one).
- **`Log`**: `message` (JXLT `${expr}` template).

`self`'s values, and every `value`/tagged-value field, are encoded by
`org.sequeless.core.statemachine.PayloadValueCodec` as single-key tagged maps: `{text: String}`,
`{integer: Long}`, `{decimal: BigDecimal}`, `{bool: Boolean}`, `{dateTime: String}` (via
`Instant.toString()`), `{date: String}` (via `LocalDate.toString()`), `{ref: String}` (a UUID),
`{list: List<Object>}` (each element itself tagged) — mirroring the shape the postgres adapter's
own `ValueJsonCodec` already uses for `sq_object.props`.

**Why the payload is frozen at fire time, not re-read at dispatch time.** Every field an
`ActionExecutor` method needs — the object's properties (`self`), its new state, the acting
principal — is baked into the payload the moment the transition commits. A Temporal activity retry
(hours later, after a crash) or an in-process relay redispatch therefore never reads the object's
*current*, possibly-since-changed, mutable state to reproduce its effect: it always sees the exact
`self` snapshot as of the moment the transition fired. This is what makes "no OntologyPort, no
re-read" a design property rather than an incidental optimization — `ActionExecutor`'s four
methods need nothing beyond the frozen payload to do their job, confirmed by a test that feeds a
real `DefaultTransitionService.fire(...)` call's genuine `OutboxEntry` list straight into
`DefaultActionExecutor`.

## 3. `OutboxRelay`

`OutboxRelay` (`sequeless-app/src/main/java/org/sequeless/app/automation/OutboxRelay.java`) is a
plain `@Component` with one `@Scheduled(fixedDelayString =
"${sequeless.automation.relay.poll-interval-ms:1000}")` method, `pollOnce()`.

**Claim → dispatch → mark processed, in one short transaction.** `OutboxRelay` doesn't touch SQL
itself; it calls `OutboxPort#claimNext(Set<String> kinds, BiFunction<String, OutboxEntry, T>
handler)` (`sequeless-spi/src/main/java/org/sequeless/spi/object/OutboxPort.java`) — Phase 6
(DR-11) widened this from the original `claimNextActionRequest()` (no `kinds` parameter, hardcoded
to `ActionRequest` alone) to take an explicit, caller-chosen kind set, because a single relay now
fans its one polling loop out to several inbound ports (`ActionExecutor`, `TriggerEvaluator`,
`DerivationRecomputer`), not just `ActionExecutor`. `OutboxRelay` builds the set it passes,
`DISPATCHABLE_KINDS`, once as a `static final Set<String>` field (not reconstructed per poll) —
see §9 for exactly which seven kinds it contains. The sole implementation (the postgres adapter)
runs `SELECT ... FROM sq_outbox WHERE kind = ANY(?) AND processed_at IS NULL ORDER BY occurred_at
LIMIT 1 FOR UPDATE SKIP LOCKED` against that set, calls `handler` once with the claimed row's raw
`tenantId` string and the reconstructed `OutboxEntry`, then stamps `processed_at = now()` — all
inside one short transaction. If `handler` throws, the transaction rolls back (the row stays
unprocessed, its lock released) and the exception propagates to `OutboxRelay`. `OutboxPort` is
deliberately a sibling port to `ObjectStorePort`, not a method added to it: several
`ObjectStorePort` implementers (in-memory test doubles) have no use for an outbox-claiming method,
the same reasoning that already keeps `QueryPort` a sibling rather than an `ObjectStorePort`
method.

**Drain-until-empty within one tick, but stop (not hot-loop) on a dispatch exception.**
`pollOnce()` loops calling `claimNext(DISPATCHABLE_KINDS, ...)` until it returns
`Optional.empty()` (queue drained — this is why the handler must return a non-`null` sentinel,
`Boolean.TRUE`: `claimNext` wraps a legitimately-`null` handler result and "no row available"
identically in `Optional.empty()`, so the sentinel is what makes them distinguishable). If a
dispatch throws a `RuntimeException`, the loop **breaks**, not continues: a systemic failure
(Temporal unreachable, for example) should not be hammered in a hot loop within one tick — the
next `@Scheduled` tick retries naturally, at the configured poll interval, instead.

**Why `AutomationPort` is resolved via `ObjectProvider`, not a plain constructor parameter, and
not `@ConditionalOnBean`.** `OutboxRelay`'s constructor takes `ObjectProvider<AutomationPort>` and
calls `.getIfAvailable()` inside `pollOnce()` itself, at every tick — not once, at construction.
This is a real gotcha documented in the class's own javadoc: `AutomationPort` is supplied by an
`@AutoConfiguration` class (`InProcessAutomationAutoConfiguration` or
`TemporalAutomationAutoConfiguration`) registered via Spring Boot's deferred-import mechanism. A
plain `@Component`'s own `@Conditional` — had `OutboxRelay` instead been gated with
`@ConditionalOnBean(AutomationPort.class)` — is evaluated before deferred auto-configurations are
fully processed relative to it, so that conditional would be at real risk of seeing "absent" even
when an automation adapter is in fact configured; Spring Boot's own documentation restricts
`@ConditionalOnBean` to auto-configuration classes for exactly this reason. Resolving the port
lazily, at actual call time, sidesteps the ordering hazard entirely, and also lets `OutboxRelay`
run harmlessly (never touching the database — the `ObjectProvider` short-circuit happens before
`outboxPort` is touched) in any deployment or test that never sets
`sequeless.automation.adapter` at all.

## 4. Adapters

`sequeless.automation.adapter` selects exactly one of two `AutomationPort` beans, each wired by
its own `@AutoConfiguration` gated with `@ConditionalOnProperty(name =
"sequeless.automation.adapter", havingValue = "inprocess" | "temporal")`.

### In-process (`sequeless.automation.adapter=inprocess`)

`InProcessAutomationPort` (`sequeless-adapter-automation-inprocess`) runs an `ActionRequest`'s
action synchronously, to completion, on `OutboxRelay`'s own calling thread, by invoking the
matching `ActionExecutor` method directly — there is no separate durable engine to hand off to.
A `sq:Webhook` action's HTTP call (via the JDK's own `HttpClient`, no new dependency) is wrapped in
a small **fixed** retry loop, configured by `InProcessAutomationProperties`
(`sequeless.automation.inprocess.*`): `retryAttempts` (default **3**, total attempts including the
first) and `retryDelay` (default **200ms**, fixed — not exponential — between attempts). Only the
HTTP call itself is retried; `ActionExecutor#resolveWebhook`'s own template-resolution failures (a
malformed `${expr}`) propagate immediately on the first attempt, since retrying a deterministic
template error can never succeed.

### Temporal (`sequeless.automation.adapter=temporal`)

`TemporalAutomationPort.dispatch` is fire-and-forget: it calls `WorkflowClient.start(workflow::execute,
entry)` — the async start call, not the blocking `execute()` — so the outbox row is marked
processed as soon as the `ActionWorkflow` execution durably **starts**, not when it completes.
`ActionWorkflow` (`@WorkflowInterface`, one `@WorkflowMethod execute(OutboxEntry entry)`) takes the
raw `OutboxEntry` as its workflow input with no bespoke DTO — it's a plain record of
JSON-compatible types, which Temporal's default Jackson-based `DataConverter` round-trips natively.
`ActionWorkflowImpl.execute` reads `payload.actionKind` and calls exactly one of
`ActionActivities`' four methods (`applySetProperty`/`applyCreateObject`/`applyLog`, thin
pass-throughs to `ActionExecutor`, or `executeWebhook`, which additionally performs the real HTTP
call and throws `WebhookActivityException` on a non-2xx status or transport failure).

Retries are Temporal's own, not a hand-rolled loop: `ActionActivitiesImpl.executeWebhook`
deliberately does **not** retry — it throws once, letting Temporal's `RetryOptions` (attached to
the `ActivityOptions` every activity stub is built with) retry the whole activity invocation. The
policy is bound from `sequeless.automation.temporal.retry.*`
(`TemporalAutomationProperties.Retry`): `initial-interval` (default **1s**), `backoff-coefficient`
(default **2.0**), `maximum-attempts` (default **5**, including the first attempt) — applied
uniformly to all four activities. This is the exact mechanism a "500, 500, 500, 200" acceptance
test exercises.

**Genuine Temporal SDK constraint worth knowing before touching this wiring.** Temporal
instantiates `ActionWorkflowImpl` itself, reflectively, once per workflow execution — a workflow
implementation must have a public no-arg constructor, so `ActivityOptions` (and the `RetryOptions`
it carries) cannot be constructor-injected the way `ActionActivitiesImpl` takes its
`ActionExecutor`. The chosen workaround is a `static volatile ActivityOptions ACTIVITY_OPTIONS`
field on `ActionWorkflowImpl`, published exactly once by
`TemporalAutomationAutoConfiguration.workerFactory` **before** `WorkerFactory.start()` is called;
every subsequent workflow execution (a fresh `ActionWorkflowImpl` instance each time) reads the
same already-published value. `execute()` throws `IllegalStateException` if this field is still
`null` when read — a loud failure for the wiring bug of starting a worker without the
auto-configuration having run, rather than a silent `NullPointerException` deep in workflow logic.

## 5. Idempotency

`AutomationPort#dispatch`'s contract requires that a second call for an already-dispatched
`entry.id()` must not invoke the underlying `ActionExecutor` method a second time. Three
independent mechanisms, at three different layers, jointly guarantee this — deliberately
independent, so a gap in one does not silently break the guarantee.

**(a) Temporal workflow-id reuse policy (dispatch layer).**
`TemporalAutomationPort.dispatch` uses `entry.id().toString()`, exactly, as the workflow id, and
explicitly sets `WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE` on the
`WorkflowOptions` it builds. This was a real bug caught during implementation via a failing
`TestWorkflowEnvironment` contract test, not a hypothetical: the Temporal SDK's **default** reuse
policy is `ALLOW_DUPLICATE`, under which starting a workflow whose previous run with the same id
had already *completed* silently starts a brand-new execution instead of raising
`WorkflowExecutionAlreadyStarted` — which would re-run the action a second time on redispatch,
exactly the bug idempotency is supposed to prevent. `REJECT_DUPLICATE` makes
`WorkflowExecutionAlreadyStarted` unconditional for any duplicate workflow id (running, completed,
or failed), which `dispatch`'s `catch (WorkflowExecutionAlreadyStarted e)` block treats as a
successful no-op.

**(b) `DefaultActionExecutor`'s own idempotent writes (action-execution layer, independent of
(a)).** Even a duplicate dispatch that somehow got past the workflow-id guard (or an in-process
relay retry, which has no equivalent guard of its own — see (c)) cannot produce a duplicate
side-effect, because `DefaultActionExecutor` (`sequeless-core`,
`org.sequeless.core.automation.DefaultActionExecutor`) is idempotent by construction:

- `applyCreateObject` derives the new object's id deterministically —
  `UUID.nameUUIDFromBytes((entry.id() + ":" + actionIndex).getBytes(UTF_8))` — rather than
  generating one randomly, and checks `ObjectStorePort#find` at that derived id first. A retried
  call finds the object already exists and returns immediately without creating a duplicate.
- `applySetProperty` resolves the target value, then compares it against the property's *current*
  value and only calls `commit` if they differ — a no-op write on retry. It is also a safe no-op
  (never `ObjectNotFoundException`) if the target object has since been deleted, since a retry must
  always be able to succeed.

**(c) The in-process adapter's own weaker, process-lifetime-only guard.**
`InProcessAutomationPort` keeps an in-memory `ConcurrentHashMap`-backed `Set<UUID>` of every
`entry.id()` it has already dispatched, short-circuiting a repeat call as a no-op — it has no
Temporal-style durable-engine mechanism to lean on for free, so it tracks dispatched ids itself.
This set grows unbounded for the process's lifetime and is explicitly **not crash-safe**: a process
restart forgets every id it ever saw, so a redispatch after a restart would fall through to (b) as
the only remaining guard. This is an accepted limitation, not a bug — durability across restarts is
Temporal's job; the in-process adapter exists only for tests and local development, where that
gap doesn't matter.

## 6. Known limitations (accepted, not fixed this phase)

- **`sq_outbox.attempts` is write-only.** The column exists from the original (Phase 2) outbox
  migration — every insert sets it to `0` — but nothing reads or increments it. There is no
  backoff or retry-count logic keyed off it; `OutboxRelay` retries a failed row purely by leaving
  it unclaimed for the next scheduled tick.
- **No dead-letter mechanism.** `OutboxPort#claimNext` always claims the oldest unprocessed row
  among the dispatchable kind set first (`ORDER BY occurred_at`), and there is no skip-ahead or
  quarantine path. Since Phase 6 (DR-11) widened that set from `ActionRequest` alone to all seven
  dispatchable kinds (§2, §9), a single "poison pill" row of **any** dispatchable kind — an
  `ActionRequest`, but equally a `TimerScheduled`, `SignalReceived`, or object-lifecycle row whose
  payload always makes `dispatch()` throw — now blocks every row behind it indefinitely, at
  one-scheduled-tick granularity, not just a malformed action.
- **No secondary sort key beyond `occurred_at`.** When a single transition fires multiple actions
  (e.g. `activate`'s `CreateObject` then `Webhook`), every row that transition produces shares the
  exact same commit timestamp. Their relative dispatch order relative to each other is therefore
  not guaranteed by the claim query.
- **Kickoff Task titles render bracketed.** `activate`'s `CreateObject` action sets the new Task's
  title from the JEXL expression `'Kickoff: ' + self.title`. Because `ex:title` has unbounded
  cardinality in the reference ontology, `self.title` binds to a JEXL `ArrayList`, and JEXL's `+`
  operator falls back to `Object#toString()` when concatenating a `String` with a `List` — so the
  rendered title is `"Kickoff: [Launch rocket]"`, not `"Kickoff: Launch rocket"`. This is
  deliberately **not** fixed: restricting `ex:title`'s cardinality to `max 1` would break other,
  unrelated existing tests that rely on posting `title` as a JSON list. Anyone touching
  `reference.ttl`'s `ex:title` declaration should know this bracket rendering is the reason it's
  still unbounded, and that "fixing" it has a wider blast radius than it looks.

## 7. Running Temporal locally

`docker-compose.yml`'s `temporal` service (verified against the actual file, not paraphrased):

```yaml
temporal:
  image: temporalio/temporal:1.9.1
  command: server start-dev --ip 0.0.0.0 --ui-port 8233 --db-filename /home/temporal/temporal.db
  ports: ["7233:7233", "8233:8233"]
  volumes: ["sequeless-temporal:/home/temporal"]
  healthcheck:
    test: ["CMD", "temporal", "operator", "cluster", "health", "--address", "127.0.0.1:7233"]
    interval: 5s
    timeout: 5s
    retries: 10
```

The image runs as a non-root user (`temporal`, uid 1000) whose only image-owned writable directory
is `/home/temporal`; the named volume is mounted there and `--db-filename` points at a file inside
it, so the dev-server's embedded-sqlite state survives `docker compose down` (but not `docker
compose down -v`). `server start-dev` is self-contained — an embedded sqlite-backed server with no
external database dependency — and auto-registers the `default` namespace the application's
`WorkflowServiceStubs`/`WorkflowClient`/`WorkerFactory` need.

Application-side configuration (`TemporalAutomationProperties`,
`sequeless.automation.temporal.*`): `target` (`host:port` gRPC target) is blank/unset by default,
which means "use `WorkflowServiceStubs.newLocalServiceStubs()`", targeting `127.0.0.1:7233` without
eagerly connecting — i.e. it matches the Docker Compose service above with zero configuration.
`task-queue` defaults to `"sequeless-actions"` — the queue both `TemporalAutomationPort.dispatch`
starts workflows on and `TemporalAutomationAutoConfiguration.workerFactory`'s worker polls.

**Why `temporal-spring-boot-starter` is deliberately not used.** That starter transitively pulls
Spring Boot 2.7.18 (verified via `mvn dependency:tree`), incompatible with this project's Spring
Boot 4.1.1 line and risking `NoSuchMethodError`/`ClassNotFoundException` from stale bytecode even
after this reactor's `dependencyManagement` force-upgrades the jar versions. `temporal-sdk` itself
has zero Spring dependency (confirmed via `mvn dependency:tree`, no `org.springframework`
transitives), so `TemporalAutomationAutoConfiguration` hand-rolls its own `@AutoConfiguration`
directly against `temporal-sdk` — building `WorkflowServiceStubs`, `WorkflowClient`, and
`WorkerFactory` itself — the same pattern `PostgresPersistenceAutoConfiguration` and
`JexlExpressionAutoConfiguration` already use for their own adapters.

One further operational note: `WorkerFactory.start()` is **not** lazy (unlike
`WorkflowServiceStubs`/`WorkflowClient` construction) — it issues a blocking `DescribeNamespace`
gRPC call and throws if no server is reachable. `workerFactory`'s `@Bean` method catches that
failure and logs a warning rather than failing the whole Spring context, so the application still
starts — and `TemporalAutomationPort` can still durably queue work via the lazy `WorkflowClient` —
even if Temporal is briefly unreachable at boot; no worker polls until a restart in that case. This
is a known gap (no retry/reconnect strategy or readiness gate), not something this phase closes.

## 8. Testing notes

`TemporalTestcontainersSupport`
(`sequeless-app/src/test/java/org/sequeless/app/support/TemporalTestcontainersSupport.java`)
extends `PostgresTestcontainersSupport` and adds a `GenericContainer` running the identical image,
command, and healthcheck `docker-compose.yml` already validates — not the originally-guessed
`temporalio/auto-setup` + log-line-wait design:

```java
new GenericContainer<>("temporalio/temporal:1.9.1")
    .withCommand("server", "start-dev", "--ip", "0.0.0.0", "--db-filename", "/home/temporal/temporal.db")
    .withExposedPorts(7233)
    .waitingFor(
        Wait.forSuccessfulCommand("temporal operator cluster health --address 127.0.0.1:7233")
            .withStartupTimeout(Duration.ofSeconds(90)));
```

No `--db-filename` volume mount is needed here (unlike Compose): a Testcontainers container is
ephemeral for the JVM fork's lifetime, so there's nothing to persist. A `@DynamicPropertySource`
sets `sequeless.automation.temporal.target` to the container's mapped host:port.

**Local-HTTP-stub caveat.** `reference.ttl`'s `ex:ProjectLifecycle` state machine's `activate`
transition includes a `sq:Webhook` action targeting `https://example.org/hooks/project-activated` —
a hardcoded, unreachable external URL (fails DNS resolution fast, ~10ms, in a sandboxed
environment, so it doesn't stall the relay, but it obviously can't be asserted against). Any test
that needs to assert webhook delivery/retry semantics therefore does **not** fire a transition
through the reference ontology; it constructs its own `ActionRequest` `OutboxEntry` directly (via a
real `ObjectStorePort#commit` call, bypassing `TransitionService`/the ontology entirely) pointing
at a local JDK `HttpServer` stub instead.

**Testcontainers gotcha from the acceptance-test task.** Two `@SpringBootTest` classes that both
extend `TemporalTestcontainersSupport`/`PostgresTestcontainersSupport` must **not** use
byte-identical `@SpringBootTest` `properties` arrays. JUnit 5's `@Testcontainers` extension manages
the static `@Container` fields with **per-test-class** lifecycle — started in that class's
`beforeAll` if not already running, stopped in that class's `afterAll` — so a second test class
gets its own fresh containers on new ports. If that second class's Spring `properties` array is
byte-identical to the first class's, Spring's test-context cache reuses the first class's cached
`ApplicationContext`/`DataSource` — which now points at a container Testcontainers has already
killed and recreated on different ports — causing spurious connection failures
(`CannotCreateTransactionException` / connection refused). The fix each acceptance test class uses
is to intentionally diverge at least one property value (e.g. a `retry.maximum-attempts` override)
so each class gets its own fresh context and containers, matching what every other
Testcontainers-backed test class in this codebase already does. (A more thorough fix — adopting
Testcontainers' actual "singleton container" pattern, started from a static initializer with no
`@Container`/`@Testcontainers`-managed stop, if context-cache reuse across Testcontainers-backed
classes is ever wanted — is a follow-up, not something this phase implements.)

The full acceptance picture is green under `mvn verify` for both adapters: `TransitionsEndToEndTest`
(in-process) and `TemporalAutomationEndToEndTest` (webhook-retry, crash-recovery) /
`TemporalTransitionActivationEndToEndTest` (REST guard/activate/kickoff-Task flow) for Temporal.
Kickoff Task titles render bracketed identically under both adapters (see §6) — not an
adapter-specific quirk.

## 9. Trigger semantics (Phase 6 / DR-11)

Through Phase 5, `sq:trigger` accepted exactly one legal value (`sq:UserAction`) and a transition
could only ever fire from `POST /objects/{type}/{id}/transitions/{name}`. Phase 6 widens this to a
sealed `TriggerSpec` hierarchy (`org.sequeless.spi.meta`) with a `TriggerKind kind()` discriminator
— `USER_ACTION`, `ON_CHANGE`, `TIMER`, `EXTERNAL_SIGNAL` — so a transition can also fire from a
data change, a timer, or an external signal, without becoming reachable from the REST fire
endpoint (which now rejects any transition whose `trigger().kind() != TriggerKind.USER_ACTION`
with the same `TransitionNotAvailableException`/409 an unavailable transition already gets).

- **`OnChangeTrigger`** carries `sq:watch` — zero or more forward object-property IRIs on the
  *changed* object (e.g. `ex:belongsToProject`, domain `ex:Task`, range `ex:Project`). Absent
  `sq:watch` means "watch self only": the transition re-evaluates when the object it's declared on
  changes directly, with no relationship hop. `sq:watch` is a **forward** relationship precisely so
  candidate resolution never needs an `owl:inverseOf` declaration — one hop, straight off the
  changed object's own property.
- **`TimerTrigger`** carries `sq:after`, a strictly-positive ISO-8601 duration (`"PT72H"`), parsed
  via `Duration.parse`.
- **`ExternalSignalTrigger`** carries `sq:signalName`, an arbitrary string matched against the name
  in `POST /objects/{type}/{id}/signals/{name}` (§12).

See `sq-vocabulary.md`'s "Terms in use (phase 6)" section for the full IRI/domain/range table; this
section covers only the runtime *behaviour* those terms produce.

**`fireAutomated` never throws for a stale trigger.** `TransitionService.fireAutomated(Scope,
ObjectId, String transitionName, TriggerKind expected)` is the one entry point
`DefaultTriggerEvaluator` calls for all three automated trigger kinds. It re-reads the object's
*current* version itself, re-checks the named transition both exists and departs the object's
*current* state, re-checks its guard, and confirms `transition.trigger().kind() == expected` —
and returns `Optional.empty()`, silently, if any of those checks fails, rather than throwing. This
is deliberate: the outbox event that prompted the call (a change, a timer, a signal) may by now be
stale — the object may have moved on, been deleted, or the guard may no longer hold — and a stale
automation hint is the normal case, not an error condition. `fireAutomated` performs no
authorization check either (no `Operation.TRANSITION`); it is system-triggered, not
user-triggered. When a transition *does* fire this way, it shares the exact same private commit
path `fire()` (the REST path) uses — guard, state move, `TransitionFired`, one `ActionRequest` per
`sq:action` — so an automated transition is indistinguishable, in the audit trail and in its
side-effects, from one a user fired directly.

**The `timerKey` scheme.** Every successful transition, automated or user-fired, additionally
writes — in the *same* `ChangeSet` as the state move — a `TimerCancelled` entry per timer
transition departing the state just left, and a `TimerScheduled` entry per timer transition
departing the state just entered. Both entries carry a `timerKey`, built by one private static
helper in `DefaultTransitionService` (`objectId + "|" + stateIri + "|" + transitionName`, where
`stateIri` is the timer transition's own `fromStateIri` — the state it waits in) that both the
schedule and cancel call sites share. Building it in exactly one place, rather than duplicating the
concatenation at each call site, is what guarantees a schedule and its matching cancel can never
drift apart: an adapter finds a running timer to cancel by looking up this same string, whether
it's a `ScheduledExecutorService` key (in-process) or a Temporal workflow id (Temporal).

**Recompute-then-`onChange` ordering.** For a single dispatched change event
(`ObjectCreated`/`ObjectUpdated`/`ObjectDeleted`), both automation adapters call
`DerivationRecomputer.recompute` **before** `TriggerEvaluator.onChange`, for the same event. This
matters concretely: the reference ontology's `autoClose` transition guards on `self.openTaskCount
== 0`, read from *stored* props — so the materialised count must already be fresh by the time
`onChange` evaluates that guard, or the happy path (closing the last open Task should immediately
make the Project eligible to auto-close) would take an extra hop to converge. The system still
converges even if a particular adapter ever called these out of order — recompute's own
`ObjectUpdated` write re-triggers evaluation regardless — but the fixed order makes the common case
resolve in one hop instead of two. `InProcessAutomationPort`'s own class javadoc documents this
ordering guarantee as load-bearing for exactly this reason.

## 10. Event flow

The following traces one HTTP request through to eventual convergence — a client updates a Task,
which triggers a Project recompute and possibly an automated transition, which itself writes more
outbox rows that are drained on a later poll:

```mermaid
sequenceDiagram
    participant Client
    participant REST as REST (ObjectsController)
    participant Core as Core commit<br/>(ChangeSet: mutation + outbox rows)
    participant DB as sq_object / sq_outbox
    participant Relay as OutboxRelay (poll loop)
    participant Port as AutomationPort.dispatch
    participant Recomp as DerivationRecomputer.recompute
    participant Trig as TriggerEvaluator<br/>(onChange / onTimerElapsed / onSignal)
    participant TS as TransitionService.fireAutomated

    Client->>REST: PUT /objects/Task/{id} (status=done)
    REST->>Core: edit()
    Core->>DB: commit(Update) + ObjectUpdated outbox row
    Core-->>Client: 200 OK

    loop @Scheduled poll tick
        Relay->>DB: claimNext(DISPATCHABLE_KINDS) — oldest unprocessed row
        DB-->>Relay: ObjectUpdated(Task)
        Relay->>Port: dispatch(scope, entry)
        Port->>Recomp: recompute(scope, entry)
        Recomp->>DB: read Project fresh, computeRule, commit only if changed
        Note over Recomp,DB: writes its own ObjectUpdated(Project)<br/>row if openTaskCount changed
        Port->>Trig: onChange(scope, entry)
        Trig->>TS: fireAutomated(Project, "autoClose", ON_CHANGE)
        TS->>DB: guard true (openTaskCount == 0, already fresh) →<br/>commit state move + TransitionFired + ActionRequest(s)<br/>+ TimerCancelled/TimerScheduled as applicable
        DB-->>Relay: row marked processed_at
    end

    loop next poll tick(s)
        Relay->>DB: claimNext — drains the rows fireAutomated just wrote
        Note over Relay,Port: ActionRequest → ActionExecutor;<br/>ObjectUpdated(Project, again) → recompute finds<br/>nothing changed → loop terminates (§11)
    end
```

A `TimerScheduled`/`TimerCancelled`/`SignalReceived` row follows the same claim → dispatch shape,
just routed to `onTimerElapsed`/(cancel by `timerKey`)/`onSignal` instead of `onChange` — see §5's
per-kind adapter table. `TransitionFired` rows are written for audit alongside every fired
transition but are never claimed by the relay at all.

## 11. Materialised derivations (Phase 6 / DR-11)

A derived property (`sq-vocabulary.md`'s `sq:derivedBy`, Phase 4/DR-08) is, through Phase 5, always
computed fresh on every read and never stored — so it can never appear in a `filter[...]`/`sort=`
request. Phase 6 adds `sq:materialised true` on a rollup so its value is *also* kept correct in
`sq_object.props`, purely so `QueryPort` can filter and sort on it — a `GET`/browse response still
always returns the freshly recomputed aggregate, never the stored value; materialisation never
changes what a client reads, only what a client can query on. See `derived-properties.md`'s
"Known limitations" section for the client-facing contract (the 400 on filtering/sorting a
non-materialised derived property, and the one residual defect it documents).

**`Recompute` is not a new outbox kind.** A recompute is driven by the same
`ObjectCreated`/`ObjectUpdated`/`ObjectDeleted` events the store already writes — there is no
separate `KIND_RECOMPUTE`. `DefaultDerivationRecomputer.recompute(Scope, OutboxEntry changeEvent)`
finds every materialised rule whose `sourceTypeIri` covers the changed object's type and whose
`viaIri` is declared on it, resolves the target(s) (the property's current values on the changed
object), and per `(target, propertyIri)` re-reads the target fresh, computes the rule via
`DerivationPlanner.computeRule(...)` (the same aggregate code path a live `GET` uses — extracted
from `DerivationPlanner.apply` specifically so read and recompute never diverge), and **commits
only if the freshly computed value differs from what's stored**.

That commit-only-if-changed check is doing two jobs at once:

1. **Correctness under concurrency.** Every recompute re-reads and re-aggregates from scratch, so
   even a racing sequence of writes always converges on the *true* current aggregate — there is no
   accumulator to get out of sync.
2. **The loop terminator.** A recompute's own commit writes a fresh `ObjectUpdated` row (same
   payload shape as any other edit), which the relay dispatches back through `recompute` again.
   Without the equality check, that would loop forever; with it, the second pass finds the value
   already correct, skips the commit, and the chain stops. This is the same mechanism that makes
   `autoClose`'s guard read a value that's already fresh (§9's ordering note) without recompute and
   `onChange` ever needing to coordinate directly.

The commit itself bypasses `ValueCoercer`/`StructuralValidator` (both of which reject a client
write to a derived property) — it calls `ObjectStorePort.commit(Update(...))` directly, which is
how a materialised value stays `readOnly` to clients while core can still write it.

**Retry on `StaleObjectException`.** Because the commit targets a specific version, a concurrent
write to the same target between the read and the commit raises `StaleObjectException`.
`DefaultDerivationRecomputer` retries the *entire* read-recompute-commit cycle intra-call (so a
retry always re-reads and re-aggregates fresh — it can never converge on a stale value), configured
by:

- `sequeless.automation.recompute.retry.max-attempts` (default **8**)
- `sequeless.automation.recompute.retry.base-delay` (default **PT0.02S**, ~20ms, jittered)

If every attempt is exhausted, the exception propagates uncaught out of `recompute()` — the
dispatching adapter's own redispatch/retry mechanism (an unprocessed outbox row for in-process; a
Temporal activity retry for Temporal) is what recovers from there, not a second retry loop inside
`recompute` itself. This is exactly what a 200-concurrent-Task-update load test exercises: settling
on the correct `openTaskCount` losslessly under real contention (20-thread pool, 200 futures)
depends on both the retry loop and the commit-only-if-changed rule holding.

**Concurrency modes.** `sequeless.automation.recompute.mode` selects one of three
`InProcessRecomputeProperties.Mode` values, **`COALESCE`** by default:

- **`RETRY_ONLY`** — dispatch straight through to `recompute`; the intra-call retry above is the
  only correctness guard. Same-target recomputes can run concurrently on different threads.
- **`SERIALIZE`** — the in-process adapter keys work by `tenant|objectId|propertyIri` (a striped
  lock) so same-target recomputes never overlap.
- **`COALESCE`** — `SERIALIZE` plus collapsing pending duplicate recomputes for the same key into
  one run, via a pending-set + single-flight runner. This is safe specifically because a recompute
  always reads fresh state: the one surviving run still sees the final value, so collapsing several
  queued recomputes into one can never lose an update.

**The Temporal adapter only implements `RETRY_ONLY`** — this is a deliberate, stated scope
reduction, not an oversight, and there is no `sequeless.automation.temporal.recompute.mode`
property at all. `ChangeEventWorkflow` always calls the `recompute` then `onChange` activities
directly, relying on `DefaultDerivationRecomputer`'s own intra-call retry (plus Temporal's
activity-level `RetryOptions` on top) — the same effective behaviour as the in-process adapter's
`RETRY_ONLY` mode. Building a Temporal equivalent of `SERIALIZE`/`COALESCE` would need a per-key
`RecomputeWorkflow` driven by `signalWithStart` — but `WorkflowClient.signalWithStart`/
`WorkflowStub.signalWithStart` in this SDK are fire-and-forget: they start and signal a workflow but
hand back no handle a caller can use to learn when *that specific signal's own effect* has actually
finished. Correlating "this event's own coalesced recompute has completed" with that fire-and-forget
shape would need an additional callback round-trip (a signal back from the per-key workflow, or a
side-channel activity) — more new surface than this phase's Temporal work took on. Adding true
serialize/coalesce support to the Temporal adapter is recorded as follow-up work, not done here.

**A datatype-inference fix this materialisation work exposed.** Persisting a materialised value and
later reading it back through a datatype-aware cast surfaced a real, general bug in
`SnapshotMapper.dataPropertyMeta` (`sequeless-adapter-ontology-jena`): the OWL reasoner's
`property.ranges()` leaks the inferred XSD supertype (`xsd:decimal`) alongside a property's own
narrower asserted range (`xsd:integer`/`xsd:long`), and the old code picked among the candidates
alphabetically (`.sorted().findFirst()`), which silently chose `xsd:decimal` — `"decimal" <
"integer"` — over the correct, asserted type. This broke `filter[openTaskCount][eq]=0` (and would
have broken sorting too) for *any* integer-typed property, derived or not, invisibly, since before
Phase 6 a derived value was never persisted and cast back out of `props`. The fix is a new
`mostSpecificXsdIri` helper, mirroring the pre-existing `mostSpecificNamed` helper already used for
class-hierarchy domain/range leakage, that prefers the most specific XSD candidate
(`INTEGER`/`LONG` over `DECIMAL`/`DOUBLE`) rather than sorting alphabetically.

## 12. REST: the signal endpoint and the trigger wire format

**`POST /objects/{type}/{id}/signals/{name}`** accepts an optional, arbitrary JSON request body
(treated as an opaque map, defaulting to empty if omitted) and returns **`202 Accepted` with no
response body**. `404` if the object doesn't exist. `400` (`UnknownSignalException`, `ProblemDetail`
type suffix `unknown-signal`, `typeIri`/`signalName` properties set) if no transition anywhere on
that type declares an `ExternalSignalTrigger` with that `signalName` — note this check is
**type-wide**, not scoped to the object's current state: a signal that's structurally valid for the
type but not applicable from the object's current state is still accepted here and durably
recorded; rejecting an inapplicable-*right-now* signal is `TriggerEvaluator.onSignal`'s job
downstream, not this endpoint's.

**`signal()` only durably records the event; it does not fire anything itself.** It writes a
`SignalReceived` outbox row (payload includes the raw request body, recorded verbatim for audit —
see §11's cross-reference to the "known limitation" that the body is never bound into the guard's
`ExpressionContext`) and returns. Routing that event to the matching transition — finding the
transition departing the object's *current* state whose `signalName` matches, and calling
`fireAutomated` — happens later, downstream, when `OutboxRelay` dispatches the row to
`TriggerEvaluator.onSignal`. The two-step separation (durably record now, act on it once dispatched)
is deliberate: it's the same shape every other outbox-driven kind uses.

**Wire-format convention: `trigger` is the raw `TriggerKind.name()`.** `TransitionSummaryResponse`
(the DTO both the type descriptor's state-machine section and `GET
/objects/{type}/{id}/transitions` use) gained `trigger`, `after`, `signalName`, `watch` fields
alongside its Phase-5 `name`/`from`/`to`/`hasGuard`. `trigger` is serialized as `TriggerKind.name()`
verbatim — SCREAMING_SNAKE_CASE (`"USER_ACTION"`, `"ON_CHANGE"`, `"TIMER"`, `"EXTERNAL_SIGNAL"`),
not a lower-cased or hyphenated form — matching `sq-vocabulary.md`'s existing description of
`sq:trigger`'s individuals being matched, on the way in, against `TriggerKind`'s own enum constant
names. `after`/`signalName`/`watch` are populated only for the trigger kind that defines them (a
`TIMER` transition has `after` but not `signalName`, and so on) — absent, not `null`, otherwise.

**Both transition-listing endpoints filter to `USER_ACTION` only.** The type descriptor (`GET
/types/{type}`) and the per-object transitions listing (`GET /objects/{type}/{id}/transitions`)
both filter their `TransitionSummaryResponse` lists down to `trigger().kind() == USER_ACTION` —
the descriptor answers "what can a client fire directly," not "every transition this state machine
defines." `StateMachineInterpreter.availableTransitions`, by contrast, stays deliberately
unfiltered: `fire()` and `fireAutomated()` both share it for eligibility checking, and an automated
transition must remain visible to that shared logic even though it's invisible in these two REST
listings.

**The `Clock` injection seam.** `InProcessAutomationAutoConfiguration` exposes a `@Bean
@ConditionalOnMissingBean Clock clock()`, defaulting to `Clock.systemUTC()`, and
`InProcessTimerScheduler` takes that injected `Clock` rather than calling `Clock.systemUTC()`
itself. This exists specifically so a test can register its own `@Primary` mutable `Clock` bean and
call `.advance(Duration)` directly, making timer-elapsed behaviour deterministically testable
without a real wall-clock wait — the same seam `AutomationContract`'s `advanceTime(Duration)` hook
(the in-process side of it) relies on.
