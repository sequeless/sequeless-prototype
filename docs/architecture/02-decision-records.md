# Decision records


Format: context, decision, alternatives, consequences, reversibility.

### DR-01 Audience and tenancy
Context: first version must prove the core idea fast. Decision: single dataset; a `TenantId` is part
of `Scope` and every port call, but only one value is used. Alternatives: team with permissions;
multi-tenant now. Consequences: no isolation logic yet; adapters can add tenant scoping without a
core change. Reversibility: moderate.

### DR-02 Types defined at runtime, seeded from files
Context: meta-model-driven behaviour only pays off if types change without redeploy. Decision: the
ontology is runtime data, importable and exportable as Turtle. Alternatives: build-time only; runtime
only. Consequences: all storage, validation and API paths are generic. Reversibility: hard.

### DR-03 OWL ontology is the runtime meta-model, behind a port
Context: user chose OWL as the core; hexagonal principle forbids Jena in the core. Decision: the OWL
ontology plus `sq:` vocabulary is the source of truth; the core consumes the `Schema`, `Behaviour`
and `Automation` records through the read ports `SchemaSource`, `BehaviourSource` and
`AutomationSource`; no Jena type crosses the SPI. Alternatives: internal model with OWL as an
export adapter; core using Jena directly. Consequences: a mapping layer and a custom vocabulary to
maintain; open-world reasoning stays in the adapter; closed-world validation via SHACL and structural
checks. Reversibility: moderate.

### DR-04 Instances in PostgreSQL, one JSONB table
Context: relational stores excel at list, filter, page, aggregate. Decision: `sq_object` table with
identity, type, tenant, version, state, audit columns and a JSONB `props` column; GIN index plus
expression indexes created from `sq:indexed` hints. Alternatives: RDF store for everything; table per
type; hybrid columns. Consequences: no runtime DDL; property types enforced by validation, not the
database; column promotion is an adapter-internal optimisation. Reversibility: easy to moderate.

### DR-05 Reasoning
Context: user wants full OWL reasoning; no complete OWL 2 DL reasoner is maintained for Jena 6
(Openllet last released 2019). Decision: Jena's built-in OWL rules reasoner over the ontology (type
level) only, selected by `sequeless.ontology.reasoner`. Alternatives: Openllet; RDFS only.
Consequences: subclass, equivalence, inverse, transitive, domain/range inference and basic
consistency checks; incomplete for DL; no reasoning over instances. Reversibility: easy.

### DR-06 Validation
Decision: SHACL shapes in the ontology evaluated by `ValidationPort`; the core performs structural
checks (required, cardinality, datatype, reference type) from the snapshot before calling the port.
Consequence: closed-world rules despite OWL. Reversibility: easy.

### DR-07 Ontology storage
Decision: Turtle documents stored through `ObjectStorePort` (table `sq_ontology_document`, versioned),
loaded into an in-memory Jena model at startup and on import. Consequence: one database, snapshot
rebuilt on change. Reversibility: easy.

### DR-08 Derived properties
Decision: any type may declare derived properties with `sq:derivedBy` (a `sq:Rollup` with function,
source type, relationship and filters, or a `sq:Plugin` by name). `QueryPort.aggregate` executes
rollups; on-read first, materialised mode via events later. Stateful derived objects are not a
separate concept. Alternatives: expression strings; code only. Reversibility: easy.

### DR-09 State machines and automation
Context: Spring Statemachine archived July 2026; Drools is a rules engine, not a state engine.
Decision: state machines are `sq:StateMachine` data in the ontology, interpreted in the core; guards
via `ExpressionPort` (JEXL default); state change and outbox rows committed atomically; Temporal is
the default `AutomationPort` adapter from the start, with an in-process adapter for tests.
Alternatives: in-process only first; Temporal plus Drools. Consequences: Temporal server is required
infrastructure; durable, retried, idempotent actions. Reversibility: moderate.

### DR-10 API only, REST
Decision: no UI in this plan; generic REST resources (`/types`, `/objects/{type}`,
`/objects/{type}/{id}`, `/objects/{type}/{id}/transitions`, `/ontology`); display hints are
returned in type descriptors. GraphQL possible later as an inbound adapter. Reversibility: easy.

### DR-11 Authorisation port first
Decision: `AuthorizationPort` with permit-all default from phase 0; every use case consults it;
Spring Security with OIDC and `sq:permission` annotations arrive in phase 8. Reversibility: easy.

### DR-12 Events and outbox
Decision: the core emits `DomainEvent`s and `ActionRequest`s into the `ChangeSet`; the store writes
them to `sq_outbox` in the same transaction; the automation adapter relays them. External publishing
is a later adapter. Reversibility: easy.

### DR-13 Search
Decision: PostgreSQL full-text search (`tsvector` over `sq:searchable` properties) inside the default
query adapter; a separate search index adapter only if facet queries prove too slow. Reversibility: easy.

### DR-14 History and deletion
Decision: soft delete (`deleted_at`), optimistic locking (`version`), created/updated audit fields;
full history deferred and addable inside the persistence adapter. Reversibility: moderate.

### DR-15 Platform and discovery
Decision: Java 25, Spring Boot 4.1.x, Maven; records and no Lombok in SPI and core; Lombok allowed
in adapters and app; Spring Boot auto-configuration keyed by `sequeless.<port>.adapter` with
ServiceLoader descriptors for listing; ArchUnit, japicmp, Testcontainers, Flyway, Docker Compose.
Reversibility: easy.

### DR-16 Meta-model evolution
Decision: ontology documents are versioned; additive changes take effect on import; breaking changes
(property removed or retyped, cardinality tightened, state removed) are detected by diffing snapshots
and require a recorded migration plan executed against JSONB data before the new version activates.
Reversibility: moderate.

### DR-17 API prefix and co-served SPA
Decision: the REST API lives under `/api`, applied by `addPathPrefix` to the `org.sequeless.app.rest` package
(`ApiPathPrefixConfiguration`; a blank property opts out). The Angular build is served from the same jar at `/`,
with client-side deep links answered by `index.html` while real files and reserved server prefixes
(`/api`, Actuator, springdoc) are never answered with the UI shell. Reversibility: easy.

### DR-18 Client-side RDF authoring
Decision: ontology edits round-trip Turtle in the browser (`projects/rdf`). Restriction edits mint blank nodes
on the client (`edits.ts`); `POST /api/ontology` stays authoritative. Forward-compatible with backend Phase 7.
Reversibility: moderate.

### DR-19 Frontend pluggability
Decision: UI extension points are DI tokens with defaults (`OBJECT_SIGNALS_PANEL`, `ONTOLOGY_EDITOR_PANELS`,
`STATE_MACHINE_DIAGRAM`), composed by `provide*()` functions. This mirrors the backend adapter swap of DR-15.
The shell adds automation to a route only when that route loads (`app.routes.ts`). Reversibility: easy.

### DR-20 Hand-written wire models
Decision: TypeScript wire models are hand-written, not OpenAPI-generated, for two reasons recorded in
`docs/specs/phase-3-core-api.md` (Wire models, lines 54-60): `PropertyResponse` is a sealed interface with a
plain `kind` discriminator that springdoc flattens, so a generator loses the discriminated union; and the
`filter[x][y]` family is inexpressible in OpenAPI (`docs/architecture/query-port.md:341-348`). Reversibility: moderate.

### DR-21 Browse result order and id order
Context: `BrowsePort` promised a `(createdAt, id)` order but not how ids compare. PostgreSQL orders
`uuid` as unsigned bytes (checked on 18.6); `InMemoryBrowsePort` used the signed `UUID.compareTo`, so
the two disagreed on half of random id pairs, unseen because no contract test had a tie. Decision: ids,
and relationship values used as sort keys, compare in unsigned byte order of the 16-byte UUID
(lowercase canonical-string order). Explicit sorts tie-break by `id`; text search by relevance, then
`createdAt`, then `id`; the default is `createdAt`, then `id`. Missing values sort last ascending and
first descending. `createdAt` compares as stored, at the adapter's precision. `BrowseContract` pins each
tie-break with fixed ids. Alternatives: Java signed order (a non-native PostgreSQL `ORDER BY`); an
unspecified id order (the contract cannot pin ties, so the double and PostgreSQL can disagree); an order per adapter (the double stops
standing in for PostgreSQL). Consequences: PostgreSQL is unchanged; a keyword sort on the id string, as
an OpenSearch store would use, agrees; no decision is recorded for OpenSearch; facet bucket order is not
covered. Reversibility: easy.

### DR-22 Facet bucket order
Context: DR-21 left facet bucket order uncovered. The scalar facet `ORDER BY` had no tie-break, the
reference facet had no tie-break and no null-key filter, and the in-memory double sorted ties by Java
String order. Decision: buckets sort by count descending, then by bucket value in Unicode code point
order. PostgreSQL uses `COLLATE "C"` (UTF-8 byte order, which equals code point order). Null bucket keys
are excluded in both facets. The in-memory double compares by code point. Alternatives: (a) the database
collation, which orders mixed-case values differently from the double; (b) an id-based key, which does not
apply to scalar buckets and matches neither the double nor the label. Consequences: a target with no label
no longer makes browse throw; it adds no bucket. The scalar and reference facets agree; the double and
PostgreSQL agree for all code points. Reversibility: easy (query and test-double change only).

### DR-23 Relay dead-letter after three failed dispatches
Context: under B2 route 1 (resume plan spec 13, section 3), a row whose dispatch fails is left unprocessed
and retried, so a permanently failing row blocks every row behind it. `OutboxRelay` recorded this as an
accepted limitation with no dead-letter. `sq_outbox.attempts` is write-only, so it cannot count failures.
Decision: B2 route 1 changes from "fail and retry" to "three failed dispatches, then dead-letter". Three V7
columns (`fail_count`, `dead_lettered_at` and `error`) are added to `sq_outbox`. A failed dispatch rolls back its claim, then increments
`fail_count` in its own transaction; the third failure stamps `dead_lettered_at` and `error` in that
transaction. A payload that fails to decode counts as a failed dispatch, with the same threshold. The claim query skips dead-lettered rows, so the queue continues. A dead-lettered row keeps
`processed_at` NULL. Alternatives: skip-ahead, where the claim moves past a failing row (the row is silently
lost); a separate dead-letter table (the row leaves `sq_outbox`, so O-3's count no longer sees it); set
`processed_at` on failure (silently removes the row from the O-3 count).
Consequences: O-3's command still counts dead-lettered rows as unprocessed, so the kind-removal gate sees
them. The P2 to P4 plans must drain or resolve dead-lettered rows before their kinds leave the outbox. A
stamp mismatch under B2 that throws in dispatch now dead-letters after three tries instead of blocking the
queue. The main cost: during a systemic failure (for example, the Temporal server being down), the oldest
row fails on every poll, so a healthy row is dead-lettered every three polls. Nothing retries a
dead-lettered row; recovery is manual: reset `dead_lettered_at`, `fail_count` and `error`.
Reversibility: moderate (three V7 columns are added; the claim query changes).

### DR-24 Actions travel on the TransitionFired row
Context: spec 13 P2d flips the automation dispatch from `ActionRequest` rows, one per `sq:action`, to one
`TransitionFired` row per fired transition that carries the actions. Spec line 91 said `ActionWorkflow`
takes the `TransitionFired` row. Step P2c's decision D5 made a separate `TransitionActionWorkflow`, keyed
`<entryId>:<index>`, instead. No document recorded that change until this entry.
Decision: (a) P2d removes the `ActionRequest` writer and its dispatch cases in both adapters, and removes
`ActionRequest` from the relay's dispatchable set. The `TransitionFired` row carries what the adapters need
to apply its actions by index: `ontologyVersion`, `self`, `typeIri` and `machineIri`.
`OutboxEntry.KIND_ACTION_REQUEST` is deprecated in P2d and removed in P2e. (b) Deviation from spec line 91:
the per-action path uses `TransitionActionWorkflow`, keyed `<entryId>:<index>`, not `ActionWorkflow`.
`ActionWorkflow` stays registered on the worker and is never started; P2e deletes it.
Alternatives: one workflow per row, keyed by the entry id. Rejected, because a row carries several actions,
and the workflow-id reuse policy would start only the first one.
Consequences: an `ActionRequest` row that the old version did not drain before the flip is no longer claimed,
so the owner's mixed-version procedure (docs/extraction/spec-13-p2d-plan.md, section 8) drains them before the P2d PR merges.
V8 (`V8__outbox_actions_drained.sql`) then stamps any remaining `ActionRequest` row, and any `TransitionFired`
row with no `self` key, as processed, so neither is dispatched. The drain methods of `ActionExecutor`
(`applySetProperty` and the others) have no caller in the running application, and are removed in P2e.
Reversibility: moderate. V8 marks rows processed, and reverting the code does not unmark them.

### DR-25 Timers start from the TransitionFired row, and cancellation ends
Context: spec 13 P3 (`docs/extraction/spec-13-p3-plan.md`) takes timers off the outbox. Until P3,
`commitTransition` wrote a `TimerScheduled` row for each timer transition the object entered and a
`TimerCancelled` row for each it left, and the relay dispatched both kinds. Open question 1 of the plan
asked whether leaving a timer state must still cancel the timer.
Decision: (a) The `TimerScheduled` and `TimerCancelled` kinds are removed, and the relay stops claiming
them. V9 marks their remaining rows processed, after their drain. (b) The timer starts from the
`TransitionFired` row. `TriggerEvaluator.timersStartedBy` resolves the row's timers from the behaviour, and
it checks the behaviour version against the row's stamp. (c) Leaving a timer state cancels nothing. The
staleness check in `DefaultTriggerEvaluator.onTimerElapsed` is the only guard. (d) The in-process scheduler
keeps the first due instant on a redispatch (`putIfAbsent`). (e) `TimerWorkflowImpl.run` is guarded with
`Workflow.getVersion`, so recorded executions replay on the default branch. No running timer is drained or
terminated.
Alternatives: a cancel on leaving the state, which needs a second behaviour lookup on `fromState`; a
per-row timer key, which changes the workflow-id scheme. That change is outside P3 and needs its own spec row; the timer key stays the workflow id.
Consequences: a pending timer whose object has left the state fires and no-ops at the staleness check. The
in-process map can grow with objects that have left the state, with no bound; the adapter exists only for
tests and local development. A timer can outlive a state change by up to its duration (72 hours for
`expireHold`).
Reversibility: moderate. The kinds are gone, and the V9 marks cannot be undone.

### DR-26 Signals run in the request, and the SignalReceived kind is removed
Context: spec 13 P4 (`docs/extraction/spec-13-p4-plan.md`) takes signals off the outbox. Through P3,
`POST /api/objects/{type}/{id}/signals/{name}` wrote a `SignalReceived` row and returned 202.
`OutboxRelay` then routed the row to `TriggerEvaluator.onSignal`, which made a state-less object or a
failed guard a silent no-op, so the 202 promised an effect that might never happen.
Decision: (a) The `SignalReceived` kind is removed. A signal writes no outbox row, `SignalEventWorkflow`
is removed from the Temporal adapter, and neither adapter routes a signal. (b) A signal runs its matching
transition synchronously, in the request, in one `ChangeSet`: an `Update` and one `TransitionFired` row.
The 202 is returned only after the transition is applied. A concurrent write that changes the object
between the read and the commit rejects the signal with 409 `stale-object` (the existing
`StaleObjectException` mapping). (c) C7: if no transition from the object's current state carries the
signal name (a state-less object has none), or the matching guard fails, the request gets 409
`transition-not-available`. The unknown-name check stays 400 (`UnknownSignalException`). An accepted
signal gets 202. (d) When several transitions from the current state match the name, the first in
transition order is taken. If its guard fails, the request is rejected; there is no fall-through to a
later match. (e) The signal body is dropped: it is validated but not recorded. The REST contract still
accepts it. (f) An automation-version mismatch during a signal throws `IllegalStateException`, which is
not mapped, so it returns HTTP 500. (g) Migration V11 stamps the leftover `SignalReceived` rows as
processed before the new version starts, including dead-lettered rows, which keep `processed_at` NULL
until then. The drain precondition and the owner's O-3 count gate the deploy (see V11's header).
Alternatives: a 202 no-op for a state-less object or a failed guard (the old behaviour); falling through
to the next matching transition when a guard fails; carrying the body in the `TransitionFired` payload,
or auditing it elsewhere; a 409 or 503 mapping for the version mismatch; re-arming dead-lettered signal
rows instead of stamping them.
Consequences: a signal rejected with 409 is a client-visible change for the UI's signal panels. The live
panel still says "takes effect asynchronously" (a known follow-up, not decided here). The signal body is
no longer audited.
Reversibility: moderate. The kind is gone from the code, and the V11 marks cannot be undone.

### DR-27 Automation ingress takes Store events; the relay maps the outbox row
Context: spec 13 P5b (`docs/extraction/spec-13-p5b-plan.md`) replaces `AutomationPort.dispatch(Scope, OutboxEntry)`,
one method whose adapters switched on the kind string, with a typed Store-event ingress. Spec line 94
(`docs/extraction/spec-13-prototype-stage4-prepare.md`) asked for `onChange` and `onTimerElapsed` to take the
ingress event. Since P3, an elapsed timer is not an outbox row.
Decision: (a) The ingress is one method, `ingest(Scope, StoreEvent)`. `StoreEvent` is a sealed interface over two
records: `ObjectChangedEvent` (with a `Change` enum: CREATED, UPDATED, DELETED) and `TransitionFiredEvent`. Each
adapter matches on the sealed type with no `default` branch, so the compiler checks the routing. (b) `OutboxRelay`
maps each row with `StoreEvents.from(row)`. For an object-lifecycle row it runs `DerivationRecomputer.recompute`
before the ingress (P5a). The event id is the row id, and the payload is unchanged, so workflow ids and redelivery
keys keep their values. (c) Deviation 1 from spec line 94: `TriggerEvaluator.onTimerElapsed` keeps `OutboxEntry`,
because an elapsed timer is not a Store event (GLOSSARY lines 77-82; OQ4(a)). (d) Deviation 2 from the spec 5.2
table: `ActionExecutor` (`applyAction`, `resolveWebhookAction`), the Temporal `TransitionActionWorkflow` and its
activity keep `OutboxEntry`. The adapters rebuild it from the `TransitionFiredEvent` with kind
`KIND_TRANSITION_FIRED`, so the kind string stays in the adapters on the action path (OQ3(a)). (e) Decided by the
Navigator (session 06f6998baaef4a1293463bad7fa0fb07), recorded in `docs/extraction/spec-13-p5b-plan.md` section 8.
(f) The Temporal `ChangeEventWorkflow` input changed from `OutboxEntry` to `ObjectChangedEvent`, and its recompute
activity is removed, because the relay now runs the recompute. It is the only Temporal workflow on the O-4 list
(only dev Temporal state existed, cleared 2026-11-14).
Alternatives: two port methods, `objectChanged` and `transitionFired`, with the relay routing on the kind string
(the port widens, and the string match stays in the relay); four records, one per GLOSSARY case (two records that
every consumer treats alike); typed fields that the relay parses out of the row (a missing ontology-version stamp
then needs its own rule); a separate timer record (the pre-P3 replay histories would be retired), or a `StoreEvent`
case for timers (contradicts GLOSSARY line 81); typing `ActionExecutor` and the action workflow with
`TransitionFiredEvent` too (OQ3(b), the plan's recommendation, not taken).
Consequences: no row, kind or migration changes. Rows written before the deploy are mapped when the new relay reads
them. `ChangeEventWorkflow` is the only Temporal workflow whose input changes, so it is the only O-4 item; no non-dev Temporal state exists, so the loss gate is met. The deploy stops the old version before the new one starts, so an
old worker cannot decode a new-shaped `ChangeEventWorkflow` task. `TimerWorkflow` keeps its input type, and so does
`TransitionActionWorkflow`.
Reversibility: moderate. No row, kind or migration changes, but the `ChangeEventWorkflow` input shape is part of a running workflow's history, so a revert must also stop the new worker before the old one
starts, the same order as the deploy.

### DR-28 A failed Temporal activity is recorded, not lost, and each workflow is guarded by a version marker
Context: spec 13 R2 (PR #220, fix #41). A `ChangeEventWorkflow` or `TransitionActionWorkflow` whose activity
failed after its retries failed silently, because the relay had already marked the outbox row processed. A
`TimerWorkflow` whose `onTimerElapsed` failed had the same gap. The record needs a table (V12, on main), and the record
write is a new Temporal command in each workflow's history.
Decision: (a) Each workflow catches the `ActivityFailure` once its retries are exhausted, writes one
`AutomationFailure` through `AutomationFailureActivities#recordFailure`, and ends normally. The record table is
`sq_automation_failure` (V12, already on main), keyed by the workflow id, with `ON CONFLICT DO NOTHING`. (b) The
timer workflow records too, keyed by its `timerKey`, which is the workflow id that `TemporalAutomationPort` starts it
under. (c) Each workflow guards the new command with `Workflow.getVersion` under its own change id, `change-event-failure-record`, `transition-action-failure-record`, or `timer-failure-record`, with `Workflow.DEFAULT_VERSION` as the minimum and 1 as the maximum. An execution recorded before the guard has no marker and keeps the old behaviour: the failure propagates. (d) A cancellation is not recorded, and a failed record write propagates so the execution shows as failed. (e) The schema changes once, in V12, and this unit adds no migration (V13 stays free). (f) Decided by the Navigator (session a8653f325ba448cab8ae305fdea665b2), Q-A option 1 and Q-B option 1.
Alternatives: a retry cap on the timer's activity, so an endlessly failing timer is dropped (rejected: a timer that fails after its retries is recorded, not lost); leaving timers out of the record (rejected: the same silent loss); a table per workflow type (more schema for the same shape); no guard, so the new command reaches histories written before the deploy (rejected: an old execution replays into a command it never issued, which is non-determinism).
Consequences: each guarded workflow's history gains one marker and, on a failure, one record command. A deploy stops every old worker before it starts a new one, and it runs V12 first (the operator steps are in `automation.md`, "Deploying the failure record"). The guards stay until no open execution predates them (has no marker for its change id), then they can be removed. The R2 commit `d3c0fb5` must never be deployed alone. A record write that keeps failing fails its workflow, so the failure stays visible. `maximumAttempts` must stay above zero, because zero means unlimited retries.
Reversibility: low after the first record. A reverted worker cannot replay a history that holds a record command, so a rollback needs the same stop-then-start order and strands those executions. Roll forward instead.

### DR-29 One Store repository, and the ontology Postgres adapter stays in the ontology repository
Context: spec 18 (`docs/extraction/spec-18-prototype-stage5-final-fold.md`, issue #46) folds the Store out of the prototype. #46 names four artifacts: `store-core`, `store-testkit`, `store-postgres-jsonb-adapter` and `ontology-postgres-adapter`.
Decision: (a) One new repository, `sequeless-store`, with the three Store modules (spec section 2, Q1). (b) `ontology-postgres-adapter` is a module of `sequeless-ontology`, released with it (Q13). It has no compile-time dependency on the Store adapter and ships no migration; its tests take the Store adapter at test scope (Q2, Q-F). (c) The host keeps all Spring wiring, including the Postgres autoconfiguration and the adapter descriptor (Q8, Q-A).
Alternatives: four repositories (four release pipelines for one table contract); an ontology adapter inside the Store repository (the wrong family).
Consequences: one release train for the Store. The ontology adapter ships with `sequeless-ontology`, which keeps its own release order.
Reversibility: moderate until 0.1.0 is released, then low.

### DR-30 Store packages are `org.sequeless.store.{spi,api,core,postgres}`, renamed before the move
Context: the prototype's packages are `org.sequeless.spi` and `org.sequeless.core`. Every released family has its own `org.sequeless.<family>` root, and publishing the prototype's names would put them into a released contract.
Decision: (a) The four packages are `store.spi` (outbound ports that adapters implement and the core calls), `store.api` (the inbound Store Command port and its types), `store.core` (use cases) and `store.postgres` (the Postgres adapter) (Q7). (b) The renames land in the prototype as rename-only commits with CI green, before the import SHA, so the move is a pure copy (units U2 to U4). (c) `DerivationRecomputer` is in `store.api`; `DerivationPlugin`, `DerivationContext` and `QueryPort` are in `store.spi` (Q12). `AuthorizationPort` is in `store.spi`; `AccessDecision` and `Operation` move to `store.api` in U3 (Q-C). (d) The Flyway location is unchanged, and `app.*` and the host stay under `org.sequeless.app`.
Alternatives: keep `org.sequeless.spi` in the released artifacts (clashes with the query and automation families); Option C of Q7 (`store.spi` for ports, `store.api` for types, `store.core`, `store.postgres`) as the fallback if one shape is needed.
Consequences: the released package names freeze at 0.1.0.
Reversibility: low after 0.1.0.

### DR-31 The Store owns migrations V1 and V3 to V12 on the default history; the query set runs separately
Context: V1 creates `sq_object`, `sq_ontology_document` and `sq_outbox` in one file on one Flyway history. V12 creates `sq_automation_failure`. The query set has its own history, `flyway_schema_history_query`. V1 is frozen by `PostgresMigrationFrozenSetsTest`.
Decision: (a) `store-postgres-jsonb-adapter` owns V1 and V3 to V12 (there is no V2), byte-identical, including `sq_ontology_document`, as the published table contract (Q2, Q4). (b) Its `PostgresMigrations` runs the command set only, on `flyway_schema_history` (Q23). (c) The host runs the command set and then the query set, in that order (Q23). (d) The frozen command sets that move are `post-p2`, `post-p3`, `post-p4` and `post-p7` (Q15). `frozen-migrations/3328993` and `frozen-migrations/fbb82c5` stay in the host (Q-B). `post-p7/query` is retired from the Store repository.
Alternatives: the Store runs both sets (couples the Store release to the query migration history; rejected); the ontology document table in its own history (forks V1; rejected).
Consequences: the table layout is a published contract. A breaking change to it is a major version of the module. The host's migrate order is recorded here so the cut-over can name it.
Reversibility: low. V1 and V3 to V12 are frozen, and a change to their bytes or their history breaks deployed databases.

### DR-32 Command contract doubles move to `store-testkit`; Query contracts stay with `query-testkit`
Context: `query-testkit` 0.2.1 publishes the Query contracts and their in-memory doubles. The prototype's `sequeless-spi-testkit` holds both the Command and the Query doubles.
Decision: (a) The Command doubles (`InMemoryObjectStorePort`, `InMemoryAggregateQueryPort`, `InMemoryAggregateObjectStorePort`, `WorkloadDerivationPlugin` with its service entry and `ontology/unknown-plugin.ttl`) move to `store-testkit`'s main sources (Q20). The Command-side `schema()` builder is split out of `QueryFixtures` into `store-testkit` (Q19). (b) The Query-side testkit files are deleted from the prototype when browse and read move to the host (Q24, U6). (c) `store-core`'s tests do not depend on `store-testkit`, so there is no Maven cycle (Q21).
Alternatives: keep duplicate Query contracts in the Store (duplicates a released contract; rejected).
Consequences: the `store-testkit` surface is published at 0.1.0. U6 lists each class, because the surface is a one-way door.
Reversibility: moderate before 0.1.0; low after.

### DR-33 Browse and read are host use cases; Command code does not depend on Query types
Context: `DefaultBusinessObjectService` holds both the Command and the browse and read use cases. `StateMachineInterpreter` and `PostgresObjectStore` import `ObjectView`, a Query type.
Decision: (a) The browse and read use cases, with `ObjectViewMapper`, move to the host's query package (Q18, U6). `ObjectView` leaves the Command code. (b) `QueryPort`, `Criterion`, `Operator`, `AggregateRequest` and `AggregateResult` move to `store.spi` as one group, because the Command side needs them for derivation rollups (Q18). (c) The rest of `spi.query` stays in the host.
Alternatives: move the Query types into `store-core` (duplicates a released contract; rejected); keep the Command code importing Query types (reverses the family dependency; rejected).
Consequences: the import SHA gives a pure copy, and the Command side needs no new contract.
Reversibility: moderate.

### DR-34 First versions are 0.1.0, and the BOM moves in feature steps
Context: #23 requires first versions at 0.1.0. `sequeless-build`'s release-please uses `bump-minor-pre-major`, so a `feat` commit on the BOM releases a minor and a `fix` commit releases a patch (unverified against the current release config; U10 checks it).
Decision: (a) `store-core`, `store-testkit` and `store-postgres-jsonb-adapter` start at 0.1.0 (Q6, U9). (b) The BOM goes from 0.3.2 to 0.4.0 with a `feat(bom)` commit that lists the Store artifacts (Q6, U10). (c) `ontology-postgres-adapter` starts at 0.1.0 in `sequeless-ontology`, and the BOM goes to 0.5.0 with a `feat(bom)` commit that lists its entries and pins `ontology-core` 0.2.0 in `bom-contracts` (Q13, U13, U13b). (d) The release order is the Store release, then the BOM, then the prototype cut-over.
Alternatives: a `fix(bom)` commit (releases a patch, which signals no new surface to consumers; rejected).
Consequences: every release PR gets a user empty-commit re-trigger, and it merges only on green required checks.
Reversibility: low. Released versions are immutable.

### DR-35 The host is one module, and the emptied modules go after the fold
Context: #46 says "the single host". #19 keeps all Spring wiring in the host, and the adapter modules depend on the root `sequeless-spi` for the descriptor types until they fold in.
Decision: (a) `sequeless-app` is the one host module. The persistence, Jena and permit-all wiring fold into it, in that order, one module at a time, each as a CI-green commit (Q9, U12a to U12c). (b) The root `sequeless-spi` keeps `AdapterDescriptor`, `AdapterDescriptorProvider` and `SpiVersion` until the fold, because moving them earlier makes a Maven cycle. The fold moves them into `sequeless-app` and deletes the module (Q22, U12d). This withdraws Q17.
Alternatives: move the descriptor types into `sequeless-app` in U6 (a Maven cycle until the fold; rejected).
Consequences: `FatJarServesUiAndApiIT` stays green on every fold unit.
Reversibility: moderate. The host is not published.

### DR-36 The automation failure record is part of the Store contract at 0.1.0
Context: `V12` and `sq_automation_failure` are owned by the Store adapter, and the recorder writes that table. `sequeless-automation` #4 tracks an upstream failure port that does not exist yet.
Decision: (a) `AutomationFailure` and `AutomationFailureRecorder` move to `store.spi`, and `PostgresAutomationFailureRecorder` moves to `store.postgres` unchanged (Q11). (b) The recorder SPI is frozen at 0.1.0. `sequeless-automation` #4 adds an upstream port later, additively, which the Store recorder then implements. (c) The fix to #4 is behaviour work and does not block the move.
Alternatives: keep the recorder in the host (it would split the table contract; rejected).
Consequences: the recorder's signature is part of the 0.1.0 Store contract.
Reversibility: low after 0.1.0.
