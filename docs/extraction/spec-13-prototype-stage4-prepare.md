# Spec 13 — Prototype stage 4 prepare: outbox, automation ingress, Read port

Issue: [#41](https://github.com/sequeless/sequeless-prototype/issues/41). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#16](https://github.com/sequeless/sequeless-prototype/issues/16), [#19](https://github.com/sequeless/sequeless-prototype/issues/19), [#20](https://github.com/sequeless/sequeless-prototype/issues/20), and [#24](https://github.com/sequeless/sequeless-prototype/issues/24) (index preparation, which spec 10 cites). Tracker: `docs/extraction/remaining-work.md`. Glossary: `GLOSSARY.md`.

Status: draft, not reviewed by the owner. Section 3 fixes the order of the sub-steps. Section 12 lists the open items. This draft ran no `mvn`.

Facts are checked at prototype `origin/main` `3328993` (the spec 12 cut-over, subject `(#40) (#112)`, fetched for this draft). Spec 12 was checked at `611d2f0`, so its line numbers do not apply here. Where a count or claim depends on a command, the command is named in place.

## 1. Identity, stage and dependencies

- Stage 4, prepare. Spec 13 of 19. It makes the prototype's outbox, automation boundary and read path match the CQRS layout, so that specs 14 and 15 can move code out without redrawing ports.
- Waits for spec 12 ([#40](https://github.com/sequeless/sequeless-prototype/issues/40)). Done: the cut-over is `3328993`. Spec 12's DoD asked for its tracker row to be ticked, but `docs/extraction/remaining-work.md` line 149 still reads `[ ] | 12 prototype stage 3 cut-over` at `origin/main` (open item O-14).
- Blocks spec 14 ([#42](https://github.com/sequeless/sequeless-prototype/issues/42), `sequeless-query`) and spec 15 ([#43](https://github.com/sequeless/sequeless-prototype/issues/43), `sequeless-automation`). Both name 13 in the tracker's "waits for" column (`remaining-work.md` lines 151 and 152).
- Branch `spec-13-draft`, worktree `~/workspace/sequeless-prototype.worktrees/spec-13`, based on `origin/main` `3328993`.
- This spec does not:
  - create a repo, a release, an artifact or a BOM change (section 8);
  - write `sequeless-query` or `sequeless-automation` code (specs 14 and 15). The Read port and the automation ingress are built inside the prototype;
  - change the REST prefix `/api` (DR-17; issue #41 update);
  - add an ADR (the index-preparation ADR belongs to spec 14; spec 10 line 300);
  - change the SPA, unless section 7 finds a field that cannot stay wire-compatible.

## 2. Moves

This is an in-process refactor. Classes change package or responsibility inside `sequeless-prototype`, and four kinds leave `OutboxEntry`. No code leaves the repo, so spec 11 D15's copy rule and spec 12's import-commit row do not apply.

Provenance:

| Field | Value |
|---|---|
| Source repo | `sequeless/sequeless-prototype` |
| Source SHA | `3328993` (`refactor(extraction): cut over the ontology to the released artifacts (#40) (#112)`, author date 2026-11-11). It is the last commit that touches the Paths below: `git log -1 --format='%h %ad %s' --date=short origin/main -- <Paths>` |
| Paths | The three `sequeless-spi` packages `spi/object`, `spi/automation` and `spi/query` (under `sequeless-spi/src/main/java/org/sequeless/`); `sequeless-core/src/main/java/org/sequeless/core/usecase/DefaultTransitionService.java`; `sequeless-app/src/main/java/org/sequeless/app/automation/OutboxRelay.java`; `sequeless-adapter-persistence-postgres/src/main/java/org/sequeless/adapter/persistence/postgres/PostgresQueryStore.java`; and the migration directory in the next row |
| Migrations | last changed at `7f41469` (2026-09-24, `[T7]`, which wrote V4). `git log -1 --format='%h %ad' origin/main -- sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres` |
| Move | Internal: package moves (section 4), responsibility moves (table below), and kind removals (section 3). No import commit and no target repo |
| Date | the author date of each sub-step's commit. The source state is dated 2026-11-11 |

Classes whose responsibility changes. Paths are under `<module>/src/main/java/org/sequeless/` unless the row names the module.

| File | Responsibility today | Change (step) |
|---|---|---|
| `spi/object/OutboxEntry.java` | 8 `KIND_` constants: `ObjectCreated`, `ObjectUpdated`, `ObjectDeleted`, `TransitionFired`, `ActionRequest`, `TimerScheduled`, `TimerCancelled`, `SignalReceived` (`grep -c 'public static final String KIND_'`) | Keeps the 4 Store-event kinds. Removes 4 (P2, P3, P4) |
| `spi/object/OutboxPort.java` | Claims one row of a caller-chosen kind set. Javadoc (lines 16–17) says `TransitionFired` must never be claimed | Javadoc reversed (P5). Signature unchanged |
| `spi/automation/AutomationPort.java` | `dispatch(Scope, OutboxEntry)` switches on 7 kinds and throws on `TransitionFired` (lines 37–42) | Becomes the Observable ingress (P5) |
| `spi/automation/TriggerEvaluator.java` | `onChange`, `onTimerElapsed`, `onSignal`, each taking `OutboxEntry` | `onSignal` removed (P4). Others take the ingress event (P5) |
| `spi/automation/ActionExecutor.java` | 4 methods take the `ActionRequest` entry | Input becomes the `TransitionFired` row (P2, O-1) |
| `spi/automation/DerivationRecomputer.java` | `recompute(Scope, OutboxEntry)` on object-lifecycle rows | Input type follows P5 |
| `spi/query/QueryPort.java` | `query`, `ensureIndexes`, `aggregate` | `query` moves to the Read port (P6). `aggregate` stays (issue #41). `ensureIndexes` stays in this spec (O-9) |
| `spi/object/ObjectStorePort.java` | `find`, `browse`, `typesOf`, `commit`, `ontologyDocuments` | `find` and `browse` move to the Read port (P6). `typesOf` stays: it returns `TypeRef`, not an Object view, and its one main caller is `core/validation/StructuralValidator.java` line 233. `commit` and `ontologyDocuments` stay |
| `core/usecase/DefaultTransitionService.java` | Writes `TransitionFired` (`transitionFiredEntry`, line 447), `ActionRequest` (`actionRequestEntry`, line 458, called at line 351), `TimerScheduled` and `TimerCancelled` (`addTimerOutboxEntries`, lines 369–385), and `SignalReceived` (`signal`, line 297) | Writes `TransitionFired` only (P2, P3). `signal` becomes a Store Command (P4) |
| `core/automation/DefaultTriggerEvaluator.java` | `onChange` (73), `onTimerElapsed` (133), `onSignal` (157) | `onSignal`'s transition lookup moves into the signal path of `DefaultTransitionService` (P4) |
| `core/automation/DefaultActionExecutor.java` | Writes `ObjectUpdated` (114) and `ObjectCreated` (163) | Store events, kinds unchanged. Side effects keep today's path through `ObjectStorePort.commit` (O-17) |
| `core/derivation/DefaultDerivationRecomputer.java` | Writes `ObjectUpdated` (278) | Kind unchanged |
| `core/usecase/DefaultBusinessObjectService.java` | Writes the three object kinds (266, 312, 345). `browse` calls `queryPort.query` (205). `read` and the edit and delete preconditions call `objectStorePort.find` (221, 291, 335) | `browse` and `read` use the Read port (P6). The Command-side `find` calls are O-7 |
| `core/api/BusinessObjectService.java`, `core/api/TransitionService.java` | `read` returns `BusinessObject`; `browse` returns `QueryResult`; `signal` returns `void` | Return types become Object views (P6). `signal` keeps its signature (P4) |
| `app/automation/OutboxRelay.java` | `DISPATCHABLE_KINDS` lists 7 kinds (lines 46–53) | Lists the 4 Store-event kinds (P5) |
| `app/rest/ObjectsController.java`, `app/rest/ObjectPropertyMapper.java` | Map `BusinessObject` to `BusinessObjectResponse` and `QueryResultResponse`. `signal` returns 202 | Map the Object view instead (P6). JSON and status unchanged (section 7) |
| `app/config/CoreConfiguration.java` | Wires `ObjectStorePort`, `QueryPort`, `OutboxPort` and `AutomationPort` | Adds the Read port bean (P6) |
| `app/config/IndexPreparingOntologyAdministration.java` (line 82), `app/config/OntologyStartupValidator.java` (line 65) | Call `queryPort.ensureIndexes` | Unchanged unless O-9 renames it |
| `sequeless-adapter-automation-inprocess`: `InProcessAutomationPort.java`, `InProcessTimerScheduler.java` | Switch on 7 kinds (lines 120–138). Schedule timers from `TimerScheduled` | Switch on Store events (P5). Timers come from `TransitionFired` (P3) |
| `sequeless-adapter-automation-temporal`: `TemporalAutomationPort.java` (switch, lines 75–80), `TimerWorkflow(Impl)`, `SignalEventWorkflow(Impl)`, `ChangeEventWorkflow(Impl)`, `ActionWorkflow(Impl)`, `ActionActivities(Impl)`, `TriggerActivities(Impl)` | Workflow inputs are `OutboxEntry` | Follow P2 to P5. In-flight workflows: O-4 |
| `sequeless-adapter-persistence-postgres`: `PostgresOutboxPort.java` | `WHERE kind IN (:kinds)` on `sq_outbox` | Code unchanged. The kinds passed change (P5). V5 migration (P5) |
| `sequeless-adapter-persistence-postgres`: `PostgresObjectStore.java` | `find` (72), `browse` (86), `mapRow` (324, reused by `PostgresQueryStore`), `commit`, `insertOutboxEntry` | `find` and `browse` leave (P6) |
| `sequeless-adapter-persistence-postgres`: `PostgresQueryStore.java` | `query` (79), `ensureIndexes` (143), `aggregate` (175). The only main file that names `sq_index_registry` or `sq_searchable_property` (command: `git grep -l -E 'sq_index_registry\|sq_searchable_property' -- '*/src/main/*'`, plus V2) | `query` moves to the Read port (P6). Index separation (P7) |
| `sequeless-adapter-persistence-postgres`: `PostgresPersistenceAutoConfiguration.java` | Beans: `objectStorePort` (84), `queryPort` (112), `outboxPort` (137) | Adds the Read port bean (P6) |

Counts, verified at `3328993`:

- 17 main files reference a `KIND_` constant (`git grep -l -E 'KIND_(ACTION_REQUEST|TRANSITION_FIRED|TIMER_SCHEDULED|TIMER_CANCELLED|SIGNAL_RECEIVED|OBJECT_CREATED|OBJECT_UPDATED|OBJECT_DELETED)' -- '*/src/main/*.java' | wc -l`).
- 13 test files reference a `KIND_` constant (the same command over `*/src/test/*.java`). `OutboxEntryTest` has 11 of them.
- 49 files in all modules import `OutboxEntry` or `OutboxPort` (`git grep -l -E 'import org\.sequeless\.spi\.object\.(OutboxEntry|OutboxPort);' -- '*.java' | wc -l`).
- `new OutboxEntry(` appears 11 times in core main, once in the Postgres adapter (the reconstruction in `PostgresOutboxPort`), and 8 times in testkit main (`AutomationContract` 7, `ObjectStoreContract` 1).
- `spi/object` has 17 files, `spi/query` has 11 and `spi/automation` has 6 (`ls`).

## 3. Prerequisite refactors

**Order (decided here; the reviewer may change it).** P0 is the baseline. P1 to P7 are one PR each, merged in this order. Each PR runs the gate (section 9) and the tests named for it.

- P1 is a package move with no behaviour change. It goes first so that later diffs stay small.
- P2 to P5 change what the outbox holds, in the issue's order: actions, timers, signals, then the ingress. The ingress is retyped once, after the kinds it receives are settled. The target is #20 point 3: "The outbox holds only Store events."
- P6 and P7 are the read side. P6 moves `query`, `find` and `browse`. P7 then separates the index DDL that P6 leaves behind.

| Step | One-line definition | Proof test |
|---|---|---|
| P0 | Baseline: the gate passes on `3328993` with JDK 25. No code change | Gate output recorded in the spec 13 status lines (O-12) |
| P1 | `OutboxEntry` and `OutboxPort` move from `org.sequeless.spi.object` to `org.sequeless.spi.outbox` (section 4). Import rewrites only | Gate green. `git grep -l 'spi\.object\.Outbox' -- '*.java'` prints nothing. `OutboxEntryTest` moves with its class |
| P2 | Actions leave the outbox: `KIND_ACTION_REQUEST` is removed. The fired transition's action payloads ride on the `TransitionFired` row (O-1). The relay and both adapters stop claiming or dispatching `ActionRequest` | New `DefaultTransitionServiceTest` case: a fire commits the `Update` and one `TransitionFired` row, no `ActionRequest`. `TransitionsEndToEndTest` (the kickoff `Task` that the relay creates, line 198) and `AutomationEndToEndTest` pass. `OutboxEntryTest` kind-set assertion (section 6.2) |
| P3 | Timers leave the outbox: `KIND_TIMER_SCHEDULED` and `KIND_TIMER_CANCELLED` are removed. Automation schedules a timer from a Store event (#20 point 6), here `TransitionFired`, which already carries `fromState` and `toState` (`transitionFiredEntry`, lines 447–455). The trigger source is O-2 | New `DefaultTransitionServiceTest` case: entering a timer state commits no `TimerScheduled` row. `AutomationEndToEndTest` timer cases (`expireHold`, with the fake clock) pass. The Temporal end-to-end tests pass |
| P4 | Signals become Store Commands (#20 point 6): `KIND_SIGNAL_RECEIVED` is removed. `TransitionService.signal` runs the matching transition synchronously in one `ChangeSet`. `TriggerEvaluator.onSignal` is removed | `TransitionsEndToEndTest.reopenSignalOnClosedProjectIsAccepted` (line 216) asserts only the 202, so P4 adds an assertion that the project's state changes in the same request. New `DefaultTransitionServiceTest` cases: `signal` commits the transition with no `SignalReceived` row, and an unknown name throws `UnknownSignalException` (`core/api/UnknownSignalException.java`). The response stays 202 (O-5) |
| P5 | The ingress: `AutomationPort` takes Store events. `TransitionFired` becomes claimable, which reverses the `OutboxPort` javadoc. `OutboxRelay` claims only the 4 Store-event kinds. A V5 migration (section 5.3) replaces V4's index and backfills rows (O-3) | `AutomationContract`, `InProcessAutomationPortContractTest`, `TemporalAutomationPortContractTest`, `OutboxRelayTest`, `PostgresOutboxPortIT` pass. A V5 test (name chosen at implementation, O-13) shows that pre-existing unprocessed `TransitionFired` rows are not claimed after migration |
| P6 | The Read port: `find`, `browse` and `query` move to `ReadPort` and return `ObjectView` (section 4). `typesOf` and the Command-side `find` calls stay on `ObjectStorePort` unless O-7 decides otherwise. REST mapping is unchanged | `ObjectStoreContract` read clauses (null clauses at lines 136–166, behaviour clauses at 204–384) and `QueryContract` query clauses run against `ReadPort`. The testkit contract is named `ReadContract`, the name #16 point 12 gives the spec 14 kit, so that the move is mechanical. A JSON golden diff of `GET /api/objects/{type}/{id}` and `GET /api/objects/{type}` before and after P6 is empty. `ObjectsEndToEndTest`, `QueryEndToEndTest` and `AcceptanceCriteriaIT` pass |
| P7 | Index separation: the read-side DDL in V2 (`sq_index_registry`, `sq_searchable_property`, `sq_extract_searchable_text`, the `sq_object` search trigger and the GIN index) and `PostgresQueryStore.ensureIndexes` are separated from the object and outbox migrations. The shape is O-10 | `PostgresObjectStoreContractIT` and `PostgresOutboxPortIT` pass on a database with only the object and outbox migrations applied. `PostgresQueryStoreContractIT` and `PostgresQueryStoreIndexUsageIT` pass with the read migrations applied |

Decided inside the steps, not open:

- `typesOf` stays on `ObjectStorePort`. It returns `TypeRef` values, not Object views, and `StructuralValidator` (Command-side validation) is its only main caller. #19 keeps `ValidationPort` on the Command side, and `typesOf` serves that validation.
- `OutboxPort.claimNext(Set<String>, BiFunction)` keeps its signature. The relay passes the 4 Store-event kinds.
- The V5 migration follows V4's precedent (`V4__outbox_dispatch_kinds.sql`, lines 25–40). V4 marked pre-existing rows of the six kinds processed, so that the relay would not replay history. V5 must do the same for `TransitionFired`, which is about to become claimable.

## 4. Package and port renames

| Item | Today | After spec 13 | Name source and constraint |
|---|---|---|---|
| Outbox types | `org.sequeless.spi.object.OutboxEntry`, `OutboxPort` | `org.sequeless.spi.outbox` (P1) | `docs/extraction/prefactor-plan.md` section 4 proposes this package and says spec 13 picks it. Chosen here. Owner confirms (O-15) |
| Read port | none. `ObjectStorePort.find`, `browse`; `QueryPort.query` | `ReadPort` in `org.sequeless.spi.query` (P6) | #16 point 1: "its own Read port". Package `spi.query` means spec 14 moves one package, and `querySeesOnlySchema` already covers it (section 6.1). Javadoc uses the GLOSSARY meanings of Read (line 31) and Browse (line 27). The class name is confirmed at implementation |
| Object view | `BusinessObject` (`org.sequeless.spi.object`) | `ObjectView` in `org.sequeless.spi.query` (P6) | #16 point 9: the Query side's own result type, which "does not reuse the Command side's" type. GLOSSARY line 47 "Object view". GLOSSARY says to avoid "business object" for what a Read returns |
| Browse result | `QueryResult` with `BusinessObject` items | `QueryResult` with `ObjectView` items | Name kept |
| Automation ingress | `AutomationPort.dispatch(Scope, OutboxEntry)` | `AutomationPort`, with a method that takes a Store event (P5) | #41: "`AutomationPort` becomes the Observable ingress". #20 point 1 lists "Observable ingress (in)" as a port of `sequeless-automation`. Method and event-type names are chosen at implementation (O-6). Candidate event type `DomainEvent`, from GLOSSARY line 77 ("every item in an Observable is a Domain event"). Constraint: the new signature has no `OutboxEntry` |
| Index preparation | `QueryPort.ensureIndexes(Scope, Schema)` | unchanged in this spec | spec 10 D8 and #24 ("`QueryPort` itself is unchanged"). The rename is O-9 |
| Term collision | "read ports" means `SchemaSource`, `BehaviourSource`, `AutomationSource` (DR-03 line 20; spec 10 section 3.1) | unchanged | Different thing. The new port's javadoc must not use "read port" for the ontology ports, or the other way round |

## 5. Modules and dependencies

### 5.1 Root POM and module list

- The root POM lists 9 modules (`grep -c '<module>' pom.xml` = 9; lines 24–32). No module is added or removed by this spec.
- The parent `sequeless-build-parent` is 0.1.2 (line 8). The `sequeless-bom` import is 0.1.3 (line 64), set by spec 12. This spec does not change either.
- The root version is `0.1.0-SNAPSHOT` (line 14).

### 5.2 Modules whose code changes

| Module | Steps | POM change |
|---|---|---|
| `sequeless-spi` | P1, P2 to P6 | None expected |
| `sequeless-spi-testkit` | P2 to P7 (contracts, `Recording*` doubles) | None expected |
| `sequeless-core` | P2 to P6 | None expected |
| `sequeless-app` | P4 to P6 (relay, controller, config, `ArchitectureTest`) | None expected |
| `sequeless-adapter-automation-inprocess` | P2 to P5 | None expected |
| `sequeless-adapter-automation-temporal` | P2 to P5 | None expected |
| `sequeless-adapter-persistence-postgres` | P5 to P7 (adapters, V5 migration, V2 split) | None expected |
| `sequeless-adapter-ontology-jena` | none expected. `JenaOntologyAutoConfiguration.java` imports `ObjectStorePort` (line 13) and calls it only for `ontologyDocuments()`, which stays | None |
| `sequeless-adapter-authz-permitall` | none (`git grep` finds no outbox or port type) | None |
| `sequeless-ui` | P6 only if O-8 forces a field change | None |

The gate's `dependency:analyze-only` check (spec 12 section 5.4) decides any POM change. A dependency left unused by a step is removed in that step's PR.

### 5.3 Migrations

- V5 (P5) drops `sq_outbox_unprocessed_dispatchable_idx` (created by V4, which lists 7 kinds and excludes `TransitionFired`). It creates a partial index over the 4 Store-event kinds, and it backfills `processed_at` for pre-existing unprocessed `TransitionFired` rows and for any removed-kind rows that O-3 decides to close. The file name is chosen at implementation.
- P7 (O-10) may add a second migration location. No table is dropped.

## 6. ArchitectureTest changes

Source: `sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java`. It has 14 `@ArchTest` members (`grep -c '@ArchTest'` = 14). Line numbers below are the `@ArchTest` lines.

### 6.1 Existing rules

| Rule (line) | Change in spec 13 |
|---|---|
| `spiDependsOnlyOnJdkAndItself` (65) | None. `spi.outbox` and `spi.query` stay inside `SPI_PACKAGE` |
| `coreDependsOnlyOnJdkSpiAndItself` (97) | None |
| `spiAndCoreDoNotDependOnSpring` (130), `onlyAppMayDependOnSpringMvc` (142), `onlyAppRestAndConfigMayDependOnCore` (154), `noAdapterDependsOnCore` (167), `adaptersDoNotDependOnEachOther` (178), `noLombokInSpiOrCore` (186), `coreDoesNotDependOnAdapterSpecificLibraries` (197), `onlyTemporalAdapterMayDependOnTemporal` (210), `noJenaInSpiOrCore` (222) | None |
| `querySeesOnlySchema` (238) | None. It covers `ReadPort` automatically because `ReadPort` is in `spi.query` (`QUERY_PACKAGE`, line 46). `ReadPort` and `ObjectView` must not depend on `org.sequeless.ontology.api..` (`ONTOLOGY_PACKAGE`, line 47). `Schema` from `ontology.schema.api` is allowed |
| `onlyHostPreparesIndexes` (254) | None while `ensureIndexes` stays on `QueryPort` (O-9). If it moves, `ENSURE_INDEXES` (line 52) and the `QueryPort.class` target change with it |
| `suiteActuallyAnalyzesEveryModule` (277) | None required. Optionally assert the new `ReadPort` class exists. Name chosen at implementation |

### 6.2 New rules and tests (names chosen at implementation)

- Ingress caller rule (P5). No class outside `org.sequeless.app.automation..` calls the ingress method of `AutomationPort`. It is modelled on `onlyHostPreparesIndexes`, and its name is chosen at implementation.
- Outbox kind test (P2 to P4). A unit test, not an ArchUnit rule, asserts that the public `KIND_` constants of `OutboxEntry` are exactly `KIND_OBJECT_CREATED`, `KIND_OBJECT_UPDATED`, `KIND_OBJECT_DELETED` and `KIND_TRANSITION_FIRED`. It is added to `OutboxEntryTest`, which has 11 `KIND_` references today.
- A new rule must match at least one class. Spec 12 section 6.1 gives the reason, and no released rule sets `allowEmptyShould`.

## 7. Wire compatibility

REST paths and the `/api` prefix do not change (`ApiPathPrefixConfiguration`, default `/api`; DR-17). The SPA's wire models are hand-written (DR-20, `docs/architecture/02-decision-records.md` lines 114–119). They are in `sequeless-ui/projects/core-api/src/lib/models/index.ts`.

### 7.1 Endpoints

| Endpoint (`ObjectsController.java` line) | Body | Changed by | Status |
|---|---|---|---|
| `GET /api/objects/{type}` (144) | `QueryResultResponse`: `items` (`BusinessObjectResponse[]`), `page`, `size`, `totalItems`, `totalPages`, `facets` | P6 mapping only | unchanged |
| `GET /api/objects/{type}/{id}` (264) | `BusinessObjectResponse`, with `ETag` from `version` | P6 | unchanged |
| `POST /api/objects/{type}` (290), `PUT` (332), `POST .../transitions/{name}` (458) | `BusinessObjectResponse` | P6 | unchanged |
| `POST /api/objects/{type}/{id}/signals/{name}` (501) | no body, 202 Accepted | P4 (synchronous Store Command) | 202 kept (O-5) |
| `GET /api/objects/{type}/{id}/transitions` (398) | `TransitionResponse[]` | none | unchanged |

### 7.2 Fields of `BusinessObjectResponse`

Source: `sequeless-app/src/main/java/org/sequeless/app/rest/BusinessObjectResponse.java` (record, lines 26–33). UI: `index.ts` lines 115–130.

| JSON field | UI model | Object view field after P6 | Wire-compatible? |
|---|---|---|---|
| `id` (UUID) | `id: string` (line 123) | `ObjectId id` | yes |
| `type` (short name) | `type: string` (124) | computed in REST as today | yes |
| `typeIri` | `typeIri: string` (125) | type IRI | yes |
| `version` (long) | `version: number` (126) | `version` (#16 point 9) | yes |
| `state` (string or null) | `state: string \| null` (127) | `state` | yes, only if the Object view keeps `state` (O-8) |
| `properties` (by short name) | `Record<string, unknown>` (128) | values mapped by `ObjectPropertyMapper` | yes |
| `audit` (4 fields) | `AuditResponse` (115–120, 129) | `audit` | yes, only if the Object view keeps `audit` (O-8) |
| not in JSON: `tenant`, `deleted` on `BusinessObject` | none | not in the Object view | n/a. Reads already exclude deleted objects, and the tenant comes from `Scope` |

GLOSSARY line 48 defines Object view as "its identity, type, property values and version", and #16 point 9 lists the same fields plus a display label. Neither lists `state` or `audit`. Following that text literally would drop two JSON fields that the SPA's model requires. The display label is not rendered in JSON today, so adding it to the Object view is not a JSON change. O-8 records the choice (recommended: keep `state` and `audit`, and amend the glossary).

`FacetResponseMapper` and `FacetBucketResponse` (`index.ts` lines 132–135) are not changed.

### 7.3 Internal shapes (not REST)

`OutboxEntry` payloads are JSON in `sq_outbox` and in Temporal workflow inputs. They change with P2 to P5. This is not a REST contract. The compatibility risks are O-3 (table rows) and O-4 (running workflows).

### 7.4 Verdict and checks

- Wire-compatible, with no UI change, if O-8 keeps `state` and `audit`. Each field is in the table above.
- UI checks after P4 and P6: `ng test` in `sequeless-ui` (the `test` script; `models.spec.ts` has 8 references to `BusinessObjectResponse`), and `playwright test` (the `e2e` script) with `sequeless-ui/e2e/objects-stale-conflict.spec.ts` (ETag) and `automation-signals.spec.ts` (signal POST).
- The JSON golden diff in P6 (section 3) is the server-side check.

## 8. Release

None. The prototype is not released. The root version stays `0.1.0-SNAPSHOT` (line 14). No artifact is produced, no dependency is added (section 5.1), and the BOM is unchanged.

## 9. CI and gates

- Gate, run by the driver (this draft ran none):

  ```
  source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify
  ```

  `~/.bashrc.ext` supplies the Packages token, so it must be sourced in the same command as every `mvn` run. `./mvnw` does not exist: `git ls-files | grep -c '^mvnw'` prints 0 at `3328993`.
- `*IT` classes (the Postgres ITs, `AcceptanceCriteriaIT`, `FatJarServesUiAndApiIT`, `OntologyPersistenceAcrossRestartIT`) run in Failsafe's integration-test phase (`sequeless-app/pom.xml` line 199). Their run in the gate for every module is unverified.
- Acceptance and e2e tests must stay green at every step:
  - Java: 13 files named `*EndToEndTest` or `AcceptanceCriteriaIT` hold 55 `@Test` methods (`git ls-files | grep -E 'EndToEnd|AcceptanceCriteria'`, then `grep -c '@Test'`).
  - Playwright: 4 spec files in `sequeless-ui/e2e` hold 6 `test(` calls (`git grep -c -E '\btest\(' -- sequeless-ui/e2e`).
  - 19 `sequeless-app` test files reference `"/api` (`git grep -l '"/api' -- sequeless-app/src/test | wc -l`).
  - The issue says "24 acceptance/e2e tests". No count above gives 24. Unverified (O-11).
- SNAPSHOT rule (spec 12 section 9): `git grep -n -- SNAPSHOT -- '*pom.xml' | grep -v '<version>0.1.0-SNAPSHOT</version>'` prints nothing at `3328993`.
- Prototype CI: `.github/workflows/ci.yml` line 13 pins `sequeless/sequeless-build` `maven-ci.yml@workflows-v0.1.0`. This spec does not change the pin. The prototype's CI must be green on every step's PR.
- Each step's PR must pass the gate, the `ArchitectureTest` run inside it, and the tests named in section 3.

## 10. ADRs

`docs/adr/` does not exist in the prototype (`ls -d docs/adr` fails). The decisions that govern this spec are in `docs/architecture/02-decision-records.md` and in the issues.

| Record | Text (summary) | Effect on spec 13 |
|---|---|---|
| DR-09 "State machines and automation" (line 56) | "state change and outbox rows committed atomically"; Temporal is the default `AutomationPort` adapter | Atomic commit stays (P2 to P4 write one `ChangeSet`). Outbox rows now hold Store events only |
| DR-12 "Events and outbox" (line 73) | "the core emits `DomainEvent`s and `ActionRequest`s into the `ChangeSet`" | Amended: `ActionRequest` leaves (P2). A DR entry or in-place amendment is O-16 |
| DR-08 "Derived properties" (line 50) | `QueryPort.aggregate` executes rollups | Unchanged: `aggregate` stays on `QueryPort` |
| DR-03 (line 17) | ontology read ports `SchemaSource`, `BehaviourSource`, `AutomationSource` | Term collision (section 4) |
| DR-10 (line 64), DR-17 (line 98) | REST resources; `/api` prefix | Unchanged |
| DR-20 (line 114) | hand-written wire models | Section 7 |
| #16 point 1 | "Read moves to the Query side ... its own Read port" | P6 |
| #19, #20 | vocabulary; what stays in the prototype; where automation and Observable go | P2 to P5. Open questions in O-1, O-17 |
| #24 (closed) | index preparation by host call; "`QueryPort` itself is unchanged" | O-9 |
| #23 | extraction order and history | Section 3 order |

No ADR is added here. The index-preparation ADR belongs to spec 14 in `sequeless-query` (spec 10 line 300).

## 11. Definition of done

Copied from issue #41:

- The new repo's CI is green.
- The prototype's CI is green against *released* artifacts (never a SNAPSHOT on `main`).
- The BOM's CI, which runs every testkit contract, is green.
- The release and the BOM re-release are done.
- For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA.

Status for spec 13:

| Clause | Status |
|---|---|
| The new repo's CI is green | Not applicable. No new repo (section 1) |
| The prototype's CI is green against released artifacts | Applies. The PR's CI run is the proof. No new released artifact is consumed. The SNAPSHOT grep (section 9) prints nothing |
| The BOM's CI is green | Not applicable. The BOM's CI runs released contracts. The prototype testkit is not released, so no contract here changes that CI |
| The release and the BOM re-release are done | Not applicable (section 8) |
| For cut-over specs, the commit names the target repo and the import SHA | Not applicable. Nothing moves to another repo. Each step's commit names issue #41 and the source SHA `3328993` |

Added by this draft:

- The acceptance and e2e tests stay green at every step: the 13 Java files (55 `@Test` methods) and the 4 Playwright spec files (6 `test(` calls). The set that the issue's "24" refers to is O-11.
- The UI wire contract is verified: `index.ts` matches the JSON after P6 (the golden diff in section 3), and `ng test` and `playwright test` pass after P4 and P6. No field changes without a matching edit to `index.ts`.
- The PR closes [#41](https://github.com/sequeless/sequeless-prototype/issues/41), and the spec 13 row (`remaining-work.md` line 150) is ticked.
- Spec 17 is the stage 4 cut-over in the tracker (`remaining-work.md`, row 17). Spec 13 is a prepare step, so the cut-over clause above does not apply to it.

## 12. Open items for the driver

Each item names the evidence, the command that settles it, and the decision it needs. Settled decisions are in sections 2 to 7.

1. **O-1. Where a fired transition's actions live once `ActionRequest` leaves (P2).** Option (a): extend the `TransitionFired` payload with the frozen action payloads. Today `actionRequestPayload` (`DefaultTransitionService` line 479) builds them, and `transitionFiredEntry` (lines 447–455) carries only `objectId`, `tenantId`, `principalId`, `transitionName`, `fromState` and `toState`. Option (b): automation reads the actions from the state machine at dispatch time. That breaks the frozen-payload rule (`spi/automation/package-info.java` lines 33–35). Settle: `git grep -n 'actionRequestPayload' -- sequeless-core`; the driver measures a typical `TransitionFired` row under (a). Decision: owner (a or b).
2. **O-2. Timer trigger source (P3).** Does automation read the `TimerTrigger` from the `TransitionFired` payload, or from the state machine? Today the timer rows are built in core (`timerScheduledEntry`, lines 398–414). #20 point 6 says automation "schedules a timer from a Store event", and #20 point 9 says subscriptions are per-tenant runtime data in `sequeless-automation`. Settle: `git grep -n 'TimerTrigger' -- sequeless-core/src sequeless-adapter-automation-*/src/main`. Decision: owner. The question "Where subscriptions are defined" is quoted from #19 in #20's body.
3. **O-3. Unprocessed rows of removed kinds, and V5 (P5).** V5 must backfill pre-existing unprocessed `TransitionFired` rows. Decide what happens to unprocessed `ActionRequest`, `TimerScheduled`, `TimerCancelled` and `SignalReceived` rows: drain them first, mark them processed, or dispatch them once. Settle: `SELECT kind, count(*) FROM sq_outbox WHERE processed_at IS NULL GROUP BY kind;` against any database that ran V4, and `git grep -n 'jdbc:postgresql' -- '*.yml' '*.properties' '*.env'` to find databases outside Testcontainers. Decision: owner. The driver runs the query.
4. **O-4. In-flight Temporal workflows (P2 to P5).** Timer, signal and action workflows take `OutboxEntry` inputs, so a workflow started before the change holds a removed kind. Settle: list running workflows on the dev Temporal server (`temporal workflow list --query 'ExecutionStatus="Running"'`; the CLI is unverified on this machine). Decision: owner (drain, or accept loss in the unreleased prototype).
5. **O-5. Signal response (P4).** Keep 202 Accepted, as today. A synchronous signal could also return 200 or 204. Settle: `sed -n '91p' docs/plans/phase-6-objects.md` (states 202) and the signal call in `sequeless-ui/projects/objects` and `projects/automation`. Decision: owner (wire). Recommended: keep 202.
6. **O-6. Ingress method and event type (P5).** Candidate event type `DomainEvent`. Settle: `sed -n '73,84p' GLOSSARY.md` (Observable, Domain event, Store event) and `gh issue view 20 -R sequeless/sequeless-prototype` (points 1 and 4). Decision: owner (naming).
7. **O-7. Command-side `find` (P6).** `DefaultBusinessObjectService` calls `objectStorePort.find` in the edit and delete preconditions (lines 291 and 335). Does that stay on `ObjectStorePort` (recommended: it needs the stored version and is not a Read), or move to `ReadPort`? Settle: `git grep -n 'objectStorePort\.find' -- sequeless-core/src/main` and #16 point 1. Decision: owner.
8. **O-8. Object view contents (P6).** The glossary (lines 47–48) omits `state` and `audit`, which `BusinessObjectResponse` and `index.ts` (lines 122–130) require. Keep both and amend the glossary (recommended), or drop them from JSON, which needs a UI change. Settle: `sed -n '47,48p' GLOSSARY.md` and `sed -n '122,130p' sequeless-ui/projects/core-api/src/lib/models/index.ts`. Decision: owner.
9. **O-9. Index preparation name and signature (P6, P7).** Sources disagree. #16 point 3 and #24 point (d) say `prepare(tenant)`. #11's summary of #24 says `prepare(tenant, version)`. Spec 10 D8 and line 224 say `prepare(tenant, schema.ontologyVersion())` in spec 13. Spec 10 line 403 says version keying lands in spec 15. `ArchitectureTest.java` line 263 says "`prepare(tenant, version)` call of spec 13". This spec keeps `ensureIndexes`. Settle: `gh issue view 24 -R sequeless/sequeless-prototype --json body --jq .body | grep -n prepare` and `grep -n 'prepare' docs/extraction/spec-10-prototype-stage3-prepare.md`. Decision: owner. Recommended: rename in spec 14, with the index-preparation ADR.
10. **O-10. What "separated" means for the index DDL (P7).** #16 point 3 already decides that index DDL is the Query side's, so only the shape is open. Class separation already holds: `PostgresQueryStore` is its own class, and it is the only main file that names the registry tables. What remains is V2 (`V2__query_indexes_and_search.sql`), which puts the registry tables, `sq_extract_searchable_text`, the `sq_object` search trigger and the GIN index in the same migration set as V1. Options: (a) a second migration location, applied by the query adapter; (b) close P7 with no DDL change. Settle: `git grep -l -E 'sq_index_registry|sq_searchable_property' -- '*/src/main/*'` and the runner `PostgresMigrations#migrate`, which reads `classpath:db/migration/sequeless/postgres` (named in the javadoc of `PostgresPersistenceAutoConfiguration.java`, lines 32–41). Decision: owner.
11. **O-11. The "24 acceptance and e2e tests" count (section 9).** Not reproduced: 13 Java files (55 `@Test`) and 4 Playwright files (6 `test(`). Settle: `git ls-files | grep -E 'EndToEnd|AcceptanceCriteria'` and `git grep -c -E '\btest\(' -- sequeless-ui/e2e`. Decision: owner or driver defines the set that the DoD counts.
12. **O-12. Gate on JDK 25 for each step (P0 to P7).** Not run in this draft. Settle: the gate in section 9. Decision: driver. Record each result in the PR body.
13. **O-13. Test class names (P5, P7).** The V5 migration test and the Read contract runner are named at implementation. Decision: driver names them in the PR bodies.
14. **O-14. Spec 12 tracker row.** `remaining-work.md` line 149 is still `[ ]` at `3328993`. Settle: `sed -n '149p' docs/extraction/remaining-work.md`. Decision: driver (tick in the spec 13 PR or in a separate docs PR).
15. **O-15. Outbox package (P1).** `org.sequeless.spi.outbox` follows the prefactor-plan proposal. Settle: the import count in section 2 (49), which matches the prefactor plan's figure, and `sed -n '50,58p' docs/extraction/prefactor-plan.md`. Decision: owner (name).
16. **O-16. Recording the `ActionRequest` removal (P2).** Add a DR entry, or amend DR-12 in place. Settle: `sed -n '73,76p' docs/architecture/02-decision-records.md`. Decision: owner.
17. **O-17. Side effects aimed at a Store (#19's open question).** `applySetProperty` and `applyCreateObject` keep today's path: `DefaultActionExecutor` builds its `OutboxEntry` (lines 114 and 163) and calls `objectStorePort.commit` directly. #19 asks whether they should go through the Command API instead. Settle: `gh issue view 20 -R sequeless/sequeless-prototype --json body --jq .body | grep -n 'Side effect aimed'` (the bullet quotes #19's question). Decision: owner. This spec keeps today's path.
