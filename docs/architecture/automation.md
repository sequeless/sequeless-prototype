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

`OutboxRelay` (`sequeless-app`, `org.sequeless.app.automation`) is a `@Scheduled` poller that
drains `ActionRequest` rows, one at a time, into whichever `AutomationPort` adapter is currently
configured via `sequeless.automation.adapter` — `inprocess` (synchronous, in the relay's own
thread; for tests and local development) or `temporal` (durable, retried, the production default
per DR-09). Both adapters call back into the same `ActionExecutor` implementation
(`DefaultActionExecutor` in `sequeless-core`) to actually apply an action, so the validated,
audited object-store write path is identical regardless of which adapter dispatched the request.

## 2. The outbox and the `ActionRequest` payload shape

`OutboxEntry` (`sequeless-spi/src/main/java/org/sequeless/spi/object/OutboxEntry.java`) is the one
record type every outbox row uses, keyed by a `kind` string. Phase 5 adds two constants to it,
deliberately declared on `OutboxEntry` itself rather than on `DefaultTransitionService`: an
adapter that must filter on `kind` (the relay, an `AutomationPort`) cannot depend on
`sequeless-core`, and the `noAdapterDependsOnCore` ArchUnit rule forbids it from trying.

- `OutboxEntry.KIND_TRANSITION_FIRED = "TransitionFired"` — an audit event, one per fired
  transition. `OutboxPort`/the relay ignore rows of this kind; they filter strictly on
  `KIND_ACTION_REQUEST`.
- `OutboxEntry.KIND_ACTION_REQUEST = "ActionRequest"` — one per `sq:action` in the fired
  transition's action list, carrying a payload that is fully self-contained and frozen at
  transition-fire time.

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
itself; it calls `OutboxPort#claimNextActionRequest(BiFunction<String, OutboxEntry, T> handler)`
(`sequeless-spi/src/main/java/org/sequeless/spi/object/OutboxPort.java`), whose sole
implementation (the postgres adapter) runs `SELECT ... FROM sq_outbox WHERE kind='ActionRequest'
AND processed_at IS NULL ORDER BY occurred_at LIMIT 1 FOR UPDATE SKIP LOCKED`, calls `handler`
once with the claimed row's raw `tenantId` string and the reconstructed `OutboxEntry`, then stamps
`processed_at = now()` — all inside one short transaction. If `handler` throws, the transaction
rolls back (the row stays unprocessed, its lock released) and the exception propagates to
`OutboxRelay`. `OutboxPort` is deliberately a sibling port to `ObjectStorePort`, not a method added
to it: several `ObjectStorePort` implementers (in-memory test doubles) have no use for an
outbox-claiming method, the same reasoning that already keeps `QueryPort` a sibling rather than an
`ObjectStorePort` method.

**Drain-until-empty within one tick, but stop (not hot-loop) on a dispatch exception.**
`pollOnce()` loops calling `claimNextActionRequest` until it returns `Optional.empty()` (queue
drained — this is why the handler must return a non-`null` sentinel, `Boolean.TRUE`: `claimNextActionRequest`
wraps a legitimately-`null` handler result and "no row available" identically in
`Optional.empty()`, so the sentinel is what makes them distinguishable). If a dispatch throws a
`RuntimeException`, the loop **breaks**, not continues: a systemic failure (Temporal unreachable,
for example) should not be hammered in a hot loop within one tick — the next `@Scheduled` tick
retries naturally, at the configured poll interval, instead.

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
- **No dead-letter mechanism.** `OutboxPort#claimNextActionRequest` always claims the oldest
  unprocessed `ActionRequest` row first (`ORDER BY occurred_at`), and there is no skip-ahead or
  quarantine path. A single "poison pill" row — one whose payload always makes `dispatch()` throw —
  therefore blocks every row behind it indefinitely, at one-scheduled-tick granularity.
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
