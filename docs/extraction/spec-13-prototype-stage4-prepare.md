# Spec 13 — Prototype stage 4 prepare: outbox, automation ingress, Read port

Issue: [#41](https://github.com/sequeless/sequeless-prototype/issues/41). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#16](https://github.com/sequeless/sequeless-prototype/issues/16), [#19](https://github.com/sequeless/sequeless-prototype/issues/19), [#20](https://github.com/sequeless/sequeless-prototype/issues/20), and [#24](https://github.com/sequeless/sequeless-prototype/issues/24) (index preparation, which spec 10 cites). Tracker: `docs/extraction/remaining-work.md`. Glossary: `GLOSSARY.md`.

Status: draft, revised after an independent review of the first draft. Every finding is applied or recorded in section 12. Not reviewed by the owner. Section 3 fixes the order of the sub-steps (P0 to P7, with P2a). Section 12 records each decision with its evidence; O-8 is the only owner decision. This draft ran no `mvn`.

Facts are checked at prototype `origin/main` `3328993` (the spec 12 cut-over, subject `(#40) (#112)`). Tracker lines are given at `origin/main` `dbf20e6` (#113), which moved after `3328993`. Spec 12 was checked at `611d2f0`, so its line numbers do not apply here. Where a count or claim depends on a command, the command is named in place.

## 1. Identity, stage and dependencies

- Stage 4, prepare. Spec 13 of 19. It makes the prototype's outbox, automation boundary and read path match the CQRS layout, so that specs 14 and 15 can move code out without redrawing ports.
- Waits for spec 12 ([#40](https://github.com/sequeless/sequeless-prototype/issues/40)). Done: the cut-over is `3328993`, and the tracker row reads `[x]` at `dbf20e6` (O-14).
- Blocks spec 14 ([#42](https://github.com/sequeless/sequeless-prototype/issues/42), `sequeless-query`) and spec 15 ([#43](https://github.com/sequeless/sequeless-prototype/issues/43), `sequeless-automation`). Both name 13 in the tracker's "waits for" column (rows 14 and 15, lines 193 and 194 at `dbf20e6`).
- Branch `spec-13-draft`, worktree `~/workspace/sequeless-prototype.worktrees/spec-13`, based on `origin/main` `3328993`.
- This spec does not:
  - create a repo, a release, an artifact or a BOM change (section 8);
  - write `sequeless-query` or `sequeless-automation` code (specs 14 and 15). The Read and Browse ports and the automation ingress are built inside the prototype;
  - move `QueryPort.aggregate` or its types. They stay on the Command side (section 4);
  - change the REST prefix `/api` (DR-17; issue #41 update);
  - add an ADR (the index-preparation ADR belongs to spec 14, per #24). The ActionRequest removal is recorded as a DR entry (O-16);
  - change the SPA, unless section 7 finds a field that cannot stay wire-compatible.

## 2. Moves

This is an in-process refactor. Classes change package or responsibility inside `sequeless-prototype`, and four kinds leave `OutboxEntry` (P2, P3, P4). No code leaves the repo, so spec 11 D15's copy rule and spec 12's import-commit row do not apply.

Provenance:

| Field | Value |
|---|---|
| Source repo | `sequeless/sequeless-prototype` |
| Source SHA | `3328993` (`refactor(extraction): cut over the ontology to the released artifacts (#40) (#112)`, author date 2026-11-11). It is the last commit that touches the Paths below: `git log -1 --format='%h %ad %s' --date=short 3328993 -- <Paths>` |
| Paths | The three `sequeless-spi` packages `spi/object`, `spi/automation` and `spi/query` (under `sequeless-spi/src/main/java/org/sequeless/`); `sequeless-core/src/main/java/org/sequeless/core/usecase/DefaultTransitionService.java`; `sequeless-app/src/main/java/org/sequeless/app/automation/OutboxRelay.java`; `sequeless-adapter-persistence-postgres/src/main/java/org/sequeless/adapter/persistence/postgres/PostgresQueryStore.java`; and the migration directory in the next row |
| Migrations | last changed at `7f41469` (2026-09-24, `[T7]`, which wrote V4). `git log -1 --format='%h %ad' 3328993 -- sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres` |
| Move | Internal: package moves (section 4), responsibility moves (table below), and kind removals (section 3). No import commit and no target repo |
| Date | the author date of each sub-step's commit. The source state is dated 2026-11-11 |

Classes whose responsibility changes. Paths are under `<module>/src/main/java/org/sequeless/` unless the row names the module.

| File | Responsibility today | Change (step) |
|---|---|---|
| `spi/object/OutboxEntry.java` | 8 `KIND_` constants: `ObjectCreated`, `ObjectUpdated`, `ObjectDeleted`, `TransitionFired`, `ActionRequest`, `TimerScheduled`, `TimerCancelled`, `SignalReceived` (`grep -c 'public static final String KIND_'`) | Keeps the 4 Store-event kinds. Removes 4 (P2, P3, P4). P2a removes none |
| `spi/object/OutboxPort.java` | Claims one row of a caller-chosen kind set. Javadoc (lines 16–17, and the `KIND_TRANSITION_FIRED` sentence) says `TransitionFired` must never be claimed. Line 28 asks a stale "(display name? roles? …)" question | Javadoc reversed (P2a). Stale question dropped (P1). Signature unchanged |
| `spi/automation/AutomationPort.java` | `dispatch(Scope, OutboxEntry)` switches on 7 kinds and throws on `TransitionFired` (javadoc lines 37–42) | Javadoc updated (P2a). Becomes the Observable ingress (P5) |
| `spi/automation/TriggerEvaluator.java` | `onChange`, `onTimerElapsed`, `onSignal`, each taking `OutboxEntry` | `onSignal` removed (P4). Others take the ingress event (P5) |
| `spi/automation/ActionExecutor.java` | 4 methods take the `ActionRequest` entry | Input becomes the `TransitionFired` row (P2, O-1) |
| `spi/automation/DerivationRecomputer.java` | `recompute(Scope, OutboxEntry)` on object-lifecycle rows | Input type follows P5 |
| `spi/query/QueryPort.java` | `query`, `ensureIndexes`, `aggregate` | `query` moves to `BrowsePort` (P6). `ensureIndexes` is removed (P6, O-9). `aggregate` stays on the Command side (#41; section 3) |
| `spi/object/ObjectStorePort.java` | `find`, `browse`, `typesOf`, `commit`, `ontologyDocuments` | `find` stays (O-7). `browse` is deleted (P6): no main caller. `typesOf`, `commit` and `ontologyDocuments` stay (section 3) |
| `core/usecase/DefaultTransitionService.java` | Writes `TransitionFired` (`transitionFiredEntry`, lines 447–455), `ActionRequest` (`actionRequestEntry`, line 458, called at line 351), `TimerScheduled` and `TimerCancelled` (`addTimerOutboxEntries`, lines 369–385; `timerScheduledEntry`, lines 398–414), and `SignalReceived` (`signal`, line 297) | Stamps the Ontology version (P2a). `TransitionFired` carries the `self` snapshot, and `ActionRequest` is removed (P2). Timer rows are removed (P3). `signal` becomes a Store Command (P4) |
| `core/automation/DefaultTriggerEvaluator.java` | `onChange` (73), `onTimerElapsed` (133), `onSignal` (157) | `onSignal`'s transition lookup moves into the signal path of `DefaultTransitionService` (P4). The timer trigger lookup stays here (O-2, P3). Ingress input (P5) |
| `core/automation/DefaultActionExecutor.java` | Writes `ObjectUpdated` (114) and `ObjectCreated` (163) through `ObjectStorePort.commit` | Store events, kinds unchanged. Input changes (P2). Today's direct path stays in this spec (section 3, O-17) |
| `core/derivation/DefaultDerivationRecomputer.java` | Writes `ObjectUpdated` (278). `recompute(Scope, OutboxEntry)` at line 108. Uses `QueryPort` for `aggregate` | Kind unchanged. Input follows P5. The `aggregate` use stays (section 3) |
| `core/usecase/DefaultBusinessObjectService.java` | Writes the three object kinds (266, 312, 345). `browse` calls `queryPort.query` (205). `read` (221) and the edit and delete preconditions (291, 335) call `objectStorePort.find` | `read` uses `ReadPort` (P6). `browse` uses `BrowsePort` (P6). The Command-side `find` calls at 291 and 335 stay (O-7) |
| `core/api/BusinessObjectService.java`, `core/api/TransitionService.java` | `read` returns `BusinessObject`; `browse` returns `QueryResult`; `signal` returns `void` | `read` returns an `ObjectView` (P6). `browse` returns `QueryResult` of `ObjectView` items (P6). `signal` keeps its signature (P4) |
| `app/automation/OutboxRelay.java` | `DISPATCHABLE_KINDS` lists 7 kinds (lines 46–53) | Adds `TransitionFired` (P2a). Drops the removed kinds, one per step (P2, P3, P4). Runs the recompute, then the ingress (P5) |
| `app/rest/ObjectsController.java`, `app/rest/ObjectPropertyMapper.java` | Map `BusinessObject` to `BusinessObjectResponse` and `QueryResultResponse`. `signal` returns 202 | Map `ObjectView` for read and browse, and keep `BusinessObject` for create, edit and fire (P6, C8). `signal` keeps 202, and a rejection gives 409 (P4) |
| `app/config/CoreConfiguration.java` | Wires `ObjectStorePort`, `QueryPort`, `OutboxPort` and `AutomationPort` | Adds the Read, Browse and index-preparation beans (P6) |
| `app/config/IndexPreparingOntologyAdministration.java` (line 82), `app/config/OntologyStartupValidator.java` (line 65) | Call `queryPort.ensureIndexes` | Call `prepare(tenant, Ontology version)` (P6, O-9) |
| `sequeless-adapter-automation-inprocess`: `InProcessAutomationPort.java` (switch, lines 120–138), `InProcessTimerScheduler.java` | Switch on 7 kinds. Schedule timers from `TimerScheduled` | Accept `TransitionFired` as a no-op (P2a). Actions from `TransitionFired` (P2). Timers from `TransitionFired` (P3). Switch on Store events (P5) |
| `sequeless-adapter-automation-temporal`: `TemporalAutomationPort.java` (switch, lines 75–81), `TimerWorkflow(Impl)`, `SignalEventWorkflow(Impl)`, `ChangeEventWorkflow(Impl)`, `ActionWorkflow(Impl)`, `ActionActivities(Impl)`, `TriggerActivities(Impl)` | Workflow inputs are `OutboxEntry` | Follow P2a to P5. In-flight workflows: O-4 |
| `sequeless-adapter-persistence-postgres`: `PostgresOutboxPort.java` | `WHERE kind IN (:kinds)` on `sq_outbox` | Code unchanged. The V5 migration is in P2a (section 5.3) |
| `sequeless-adapter-persistence-postgres`: `PostgresObjectStore.java` | `find` (72), `browse` (86), `mapRow` (324, reused by `PostgresQueryStore`), `commit`, `insertOutboxEntry` | `browse` is removed (P6). `find` stays (O-7) |
| `sequeless-adapter-persistence-postgres`: `PostgresQueryStore.java` | `query` (79), `ensureIndexes` (143), `aggregate` (175). The only main file that names `sq_index_registry` or `sq_searchable_property`, plus V2 (command: `git grep -l -E 'sq_index_registry\|sq_searchable_property' 3328993 -- '*/src/main/*'`) | Implements `ReadPort`, `BrowsePort` and the index-preparation port (P6). `aggregate` stays (section 3). Index separation (P7) |
| `sequeless-adapter-persistence-postgres`: `PostgresPersistenceAutoConfiguration.java` | Beans: `objectStorePort` (84), `queryPort` (112), `outboxPort` (137) | Adds the Read, Browse and index-preparation beans (P6) |

Counts, verified at `3328993`:

- 17 main files reference a `KIND_` constant (`git grep -l -E 'KIND_(ACTION_REQUEST|TRANSITION_FIRED|TIMER_SCHEDULED|TIMER_CANCELLED|SIGNAL_RECEIVED|OBJECT_CREATED|OBJECT_UPDATED|OBJECT_DELETED)' 3328993 -- '*/src/main/*.java' | wc -l`). 13 test files reference one (the same command over `*/src/test/*.java`). `OutboxEntryTest` has 11 of them.
- 49 files import `OutboxEntry` or `OutboxPort` (`git grep -l -E 'import org\.sequeless\.spi\.object\.(OutboxEntry|OutboxPort);' 3328993 -- '*.java' | wc -l`), in 7 modules: spi 4, testkit 7, core 11, Postgres 4, Temporal 15, in-process 4, app 4. Four more users in `spi.object` need no import today (`ChangeSet`, `CommitResult`, `ObjectStorePort`, `package-info`) and gain one in P1.
- `new OutboxEntry(` appears 11 times in core main, once in the Postgres adapter (the reconstruction in `PostgresOutboxPort`), and 8 times in testkit main (`AutomationContract` 7, `ObjectStoreContract` 1).
- `spi/object` has 17 files, `spi/query` 11 and `spi/automation` 6 (`git ls-tree --name-only 3328993 <dir>/ | wc -l`).

## 3. Prerequisite refactors

**Order (decided here).** P0 is the baseline. P1, P2a and P2 to P7 are one PR each, merged in this order. Each PR runs the gate (section 9) and the tests named for it.

- P1 is a package move with no behaviour change, so later diffs stay small.
- P2a comes before any step that gives `TransitionFired` work. Today the relay does not claim it, and both automation adapters throw on it (`OutboxPort` lines 16–17; `AutomationPort` lines 37–42). Without P2a, the proof tests of P2 to P4 cannot pass.
- P2 to P4 each remove one kind's producer and its consumer in one PR: actions, then timers, then signals. P5 changes the consumer: `AutomationPort` takes Store events, and the relay feeds the recomputer.
- P6 and P7 are the read side. P6 moves `read` and `browse` onto their own ports and switches index preparation to `prepare`. P7 separates the index DDL.
- Alternative, the owner's choice: merge P2 to P5 into one PR. P2a stays separate. The proof tests then run once, at the merged PR.

| Step | Change | Proof test |
|---|---|---|
| P0 | Baseline: the gate passes on `3328993` with JDK 25. No code change | Gate output in the PR body and the status lines (O-12) |
| P1 | `OutboxEntry` and `OutboxPort` move from `org.sequeless.spi.object` to `org.sequeless.spi.outbox` (section 4). Imports rewritten in 7 modules (49 files), and the 4 same-package users gain imports. Postgres adapter: 4 files. The stale "(display name? roles? …)" question in the `OutboxPort` javadoc (line 28) is dropped, as prefactor-plan section 4 asks | Gate green. `git grep -l 'spi\.object\.Outbox' -- '*.java'` prints nothing. `OutboxEntryTest` moves with its class |
| P2a | (a) Store events carry the Ontology version. Each Store-event builder in core writes the version of the snapshot its write validated against: `Schema.ontologyVersion()`, from the `SchemaAndBehaviour` pair in `core/ontology`, which `DefaultTransitionService` (line 326) already checks for agreement. P2a first checks which snapshot each object write path loads, then adds the stamp (C1; #20 point 3; GLOSSARY line 82). (b) V5 migration: marks unprocessed `TransitionFired` rows processed (V4 precedent, V4 lines 25–40); marks unprocessed rows of the four removed kinds processed (O-3 default); replaces V4's partial index (`sq_outbox_unprocessed_dispatchable_idx`, V4 lines 13–23) with one over the 8 claimable kinds, `TransitionFired` included. (c) `OutboxRelay.DISPATCHABLE_KINDS` adds `TransitionFired` (lines 46–53). The `OutboxPort` javadoc that forbids claiming it is reversed. (d) Both automation adapters accept `TransitionFired` as a no-op until P2 gives it actions (`InProcessAutomationPort` lines 120–138; `TemporalAutomationPort` lines 75–81) | `OutboxRelayTest` claims a `TransitionFired` row. A V5 test on a database at V4 shows that pre-existing `TransitionFired` rows are not claimed, and that rows of the four removed kinds are processed. `PostgresOutboxPortIT` passes. The in-process and Temporal contract tests accept `TransitionFired`. A payload test shows that an `ObjectCreated` row carries the version |
| P2 | Actions leave the outbox. The `TransitionFired` payload gains the frozen `self` snapshot that `actionRequestPayload` builds today (`DefaultTransitionService` line 479). `DefaultActionExecutor` takes the `TransitionFired` row and builds the actions from the automation-contract of the event's Ontology version (O-1). `actionRequestEntry` (line 458, called at 351) and `KIND_ACTION_REQUEST` go. The relay drops `ActionRequest` from `DISPATCHABLE_KINDS`, and both adapters stop dispatching it. The Temporal action workflow key moves from the outbox entry id to (event id, action index), because several actions now share one row (C6) | `DefaultTransitionServiceTest`: a fire commits the `Update` and one `TransitionFired` row carrying the snapshot, and no `ActionRequest`. `TransitionsEndToEndTest` (the kickoff `Task` the relay creates, line 198) and `AutomationEndToEndTest` pass. `TemporalTransitionActivationEndToEndTest`, `dispatchingSameActionTwiceCreatesOnlyOneTask` and `webhookRetriesThreeTimesThenSucceedsAndMarksOutboxRowProcessed` pass with the new key. Kind-set test: 7 constants (section 6.2) |
| P3 | Timers leave the outbox. `KIND_TIMER_SCHEDULED` and `KIND_TIMER_CANCELLED` go, with `addTimerOutboxEntries` (369–385) and `timerScheduledEntry` (398–414). Automation schedules a timer from the `TransitionFired` row (#20 point 6), which carries `fromState` and `toState` (lines 447–455). The timer trigger lookup stays in core's `DefaultTriggerEvaluator` (O-2). `TimerCancelled` has no reader after this: the staleness check in `DefaultTriggerEvaluator` (lines 144–149) is the safety net. The in-process timer scheduler and the Temporal `TimerWorkflow` take the `TransitionFired` row | `DefaultTransitionServiceTest`: entering a timer state commits no `TimerScheduled` row. `AutomationEndToEndTest` timer cases (`expireHold`, fake clock) and the Temporal end-to-end tests pass. Kind-set test: 5 constants |
| P4 | Signals become Store Commands (#20 point 6). `KIND_SIGNAL_RECEIVED` goes, and the relay drops it. `TransitionService.signal` runs the matching transition in one `ChangeSet`, synchronously. `DefaultTriggerEvaluator.onSignal` (line 157) goes, and its transition lookup moves into the signal path of `DefaultTransitionService`. An accepted signal returns 202 (O-5). A rejected one returns 409 through the existing `TransitionNotAvailableException` mapping (`ApiExceptionAdvice` line 204). A type-valid signal with no transition from the current state, or with a failing guard, is rejected (C7, driver decision). An unknown name stays 400 (`UnknownSignalException`) | `TransitionsEndToEndTest.reopenSignalOnClosedProjectIsAccepted` (line 216) keeps its 202 and adds an assertion that the project's state changed in the same request. New `DefaultTransitionServiceTest` cases: `signal` commits the transition with no `SignalReceived` row; an unknown name throws `UnknownSignalException`; a signal with no transition from the current state throws `TransitionNotAvailableException`. UI: `sequeless-ui/e2e/automation-signals.spec.ts` is updated. Its header comment describes the asynchronous dispatch that P4 removes, and a rejected-signal case is added, asserting the error the UI shows for a 409 (checked in P4). Kind-set test: 4 constants |
| P5 | The ingress. `AutomationPort` takes a Store event. Method and type names: O-6, using GLOSSARY line 81's "Store event". The relay (`OutboxRelay`, app) feeds two places, as #20 point 4 says: automation's ingress and store-core's materialised-rollup recomputer. It runs the recompute first, then the ingress (C2). The order matters: the `autoClose` guard reads the materialised `openTaskCount` (`sequeless-app/src/main/resources/ontology/reference.ttl` lines 263–265 and 303–306). The recompute call leaves `InProcessAutomationPort` and the Temporal `ChangeEventWorkflowImpl.run` (line 23, `activities.recompute`). `DefaultTriggerEvaluator.onChange` (73) and `onTimerElapsed` (133) take the ingress event. `DefaultDerivationRecomputer.recompute` (line 108) takes the Store event. No migration in P5 | `AutomationContract`, `InProcessAutomationPortContractTest`, `TemporalAutomationPortContractTest`, `OutboxRelayTest` (a fake recomputer and a fake ingress record the call order: recompute first, for an `ObjectUpdated` row that triggers `autoClose`) and `PostgresOutboxPortIT` pass. The ingress caller rule (section 6.2) passes |
| P6 | The Read and Browse ports (#16 points 1, 6, 9, 10 and 12). Each port is in `spi.query` and returns Object views (section 4). `ReadPort` reads one object by id and takes a Read request record (#24 point 2). `DefaultBusinessObjectService.read` (line 221) uses it. `BrowsePort` takes `QueryPort.query` and serves `browse` (line 205, called from `ObjectsController` line 180). The index-preparation port takes `prepare(tenant, Ontology version)` (#24; spec 10 line 224), and `QueryPort.ensureIndexes` is removed. The three host call sites swap (`IndexPreparingOntologyAdministration` line 82, `OntologyStartupValidator` line 65). `prepare` delegates to today's index code (`PostgresQueryStore.ensureIndexes`, line 143), so the DDL does not change. The version guard and the "done or durably accepted" promise are spec 14's (#24 points 8 to 10). `ObjectStorePort.find` stays (O-7). `ObjectStorePort.browse` is deleted, because it has no main caller. `QueryPort` keeps `aggregate` (section 4). The controller maps Object views for read and browse. Create, edit and fire still return `BusinessObject` from commit, so the controller has two mappings to one JSON shape (C8) | `ReadContract` (ReadPort) and `BrowseContract` (BrowsePort, the prototype's `QueryContract` ported, #16 point 12) run against the Postgres and in-memory adapters. `ObjectStoreContract`'s `find` and `commit` clauses pass. Its `typesOf` clauses (161–166, 294, 306–308, 382) stay on `ObjectStorePort`, unedited. Its `browse` clauses (147–153, 292–380) move to `BrowseContract`. Test doubles changed in P6: 10 `ObjectStorePort` doubles (`PortRegistryContextRunnerTest`; core's `DefaultActionExecutorTest`, `DefaultTriggerEvaluatorTest`, `DefaultDerivationRecomputerTest`, `DefaultBusinessObjectServiceTest`, `DefaultTransitionServiceTest` and `StructuralValidatorTest`; testkit's `InMemoryObjectStorePort` and `InMemoryAggregateObjectStorePort`; the anonymous one in `CoreConfigurationContextRunnerTest`, line 225). 5 `QueryPort` doubles (`CoreConfigurationContextRunnerTest` line 260, `IndexPreparingOntologyAdministrationTest` line 174, `OntologyStartupValidatorTest` line 71, `DefaultDerivationRecomputerTest` line 336, `DefaultBusinessObjectServiceTest` line 1268). The ITs that call `ensureIndexes` (`PostgresQueryStoreContractIT` line 77, `PostgresQueryStoreIndexUsageIT` lines 117 and 169, `PostgresQueryStoreAggregateContractIT` line 60) call `prepare`. A JSON golden diff of `GET /api/objects/{type}`, `GET /api/objects/{type}/{id}`, `POST`, `PUT` and the transition response is empty. `ObjectsEndToEndTest`, `QueryEndToEndTest` and `AcceptanceCriteriaIT` pass. `onlyHostPreparesIndexes` is updated (section 6.1) |
| P7 | Index separation (O-10, option (a), decided). V2's Query-side DDL moves to a second migration location, `db/migration/sequeless/query`, applied by a second Flyway instance with its own history table (name at implementation). The objects are `sq_index_registry` (V2 line 5), `sq_searchable_property` (13), `ALTER TABLE sq_object ADD COLUMN search_vector` (19), `sq_extract_searchable_text` (28), `sq_object_update_search_vector()` (54), its trigger `sq_object_search_vector_trigger` (68) and the GIN index `sq_object_search_vector_gin_idx` (72). `PostgresMigrations` runs the Command instance, then the Query instance. The migration design is the C5 design below | The migration-path test in the C5 design below. `PostgresObjectStoreContractIT` and `PostgresOutboxPortIT` pass with only the Command migrations applied. `PostgresQueryStoreContractIT` and `PostgresQueryStoreIndexUsageIT` pass with both sets applied |

**P2a caveat.** V4 (lines 29–30) left `ActionRequest` rows unprocessed, because they were claimable and had to keep dispatching. P2a marks the four removed kinds too. Rows of those kinds that are still pending at P2a's deploy are then dropped while the old code could still dispatch them. O-3 gates this. If a non-dev database has pending rows of those kinds, move the removed-kind part of V5 into P2, P3 and P4, each in the PR that removes that kind. The `TransitionFired` backfill stays in P2a.

**C5 design (driver decision, recorded).** V2 is not edited, because its checksum is recorded on every database that ran it. Its file moves to the query location, and a second Flyway instance runs there with its own history table. On an existing database, the Command instance sets `ignoreMigrationPatterns` to `*:missing`, so the recorded V2 row is tolerated. The query instance is baselined at version 2 (recorded, not run), so the objects are not created twice. On a fresh database, the Command location runs first (V1, V3 to V5), then the query location (V2), because V2's trigger needs `sq_object` from V1. Settling test: a test runs V1..V7 on a database that recorded V2 at the old location, and on a fresh database. It asserts success and that no object or registry row is duplicated. The numbers are the owner's range; the test runs the final set when the steps land (today: V1, V3, V4 and V5 in the Command location, and V2 in the query location).

**Decided inside the steps, not open.**

- `typesOf` stays on `ObjectStorePort`. It returns `TypeRef` values, not Object views. `StructuralValidator` (line 233) is its only main caller, and #19 point 2 keeps `typesOf` on the Command side.
- `ontologyDocuments()` stays on `ObjectStorePort` for now. #19 point 2 moves it to the ontology adapter, a move that #41 does not list.
- `OutboxPort.claimNext(Set<String>, BiFunction)` keeps its signature. The relay passes the claimable set.
- Aggregate (C3, driver decision). Spec 13 leaves `QueryPort.aggregate`, `AggregateRequest`, `AggregateResult`, `AggregateContract`, `InMemoryAggregateQueryPort`, `InMemoryAggregateObjectStorePort` and the aggregate use in `DefaultDerivationRecomputer` where they are. Basis: #41 ("`QueryPort.aggregate` and `AggregateContract` stay on the Command side"), #16 point 2 (aggregate becomes a query the Command side runs against its own store), and #19 point 7. Spec 14 leaves them behind (section 4).
- Side effects aimed at a Store (O-17, decided by #20 point 5). The outbound port for them belongs to automation. `sequeless-automation` has "Store Command (out)" among its ports (#20 point 1). The host binds that port in-process to store-core's inbound Store Command port, acting as the automation Principal. This spec keeps `DefaultActionExecutor`'s direct `ObjectStorePort.commit` path (lines 114 and 163), because #41's scope does not list the port.
- Signal rejection (C7): P4, with the UI test named there.

## 4. Package and port renames

| Item | Today | After spec 13 | Name source and constraint |
|---|---|---|---|
| Outbox types | `org.sequeless.spi.object.OutboxEntry`, `OutboxPort` | `org.sequeless.spi.outbox` (P1) | Decided (O-15). #41's comment of 2026-11-05 delegates the package to this spec ("This spec rewrites `OutboxEntry` anyway, so it picks the package"). prefactor-plan section 4 proposes this package |
| Read port | none. Object reads go through `ObjectStorePort.find` | `ReadPort` in `org.sequeless.spi.query` (P6), reads one object by id | #16 point 1 ("its own Read port") and point 12 (`ReadContract`). `find` stays on `ObjectStorePort` (O-7) |
| Browse port | `QueryPort.query`; `ObjectStorePort.browse` (no main caller) | `BrowsePort` in `spi.query` (P6). `browse` deleted from `ObjectStorePort` | #16 point 6 (Browse, Read and index-preparation ports) and point 12 (`BrowseContract`) |
| Object view | `BusinessObject` (`org.sequeless.spi.object`) | `ObjectView` in `org.sequeless.spi.query` (P6) | #16 point 9: the Query side's own result type, which does not reuse the Command side's. GLOSSARY line 47 "Object view". GLOSSARY says to avoid "business object" for what a Read returns |
| Browse result | `QueryResult` with `BusinessObject` items | `QueryResult` with `ObjectView` items | Name kept |
| Automation ingress | `AutomationPort.dispatch(Scope, OutboxEntry)` | `AutomationPort`, with a method that takes a Store event (P5) | #41: "`AutomationPort` becomes the Observable ingress". #20 point 1 lists "Observable ingress (in)". Method and type names: O-6. Constraint: the new signature has no `OutboxEntry` |
| Index preparation | `QueryPort.ensureIndexes(Scope, Schema)` | `prepare(tenant, Ontology version)` on the index-preparation port in `spi.query` (P6). `ensureIndexes` removed from `QueryPort` | #24, Spec 13 list, and spec 10 line 224 (O-9, decided). The version guard and the promise are spec 14's (#24 points 8 to 10) |
| Aggregate | `QueryPort.aggregate`, `AggregateRequest`, `AggregateResult`, `AggregateContract` | Unchanged in spec 13: they stay on the Command side | #41; #16 point 2; #19 point 7 (C3). After P6, `QueryPort` holds only `aggregate` |
| Term collision | "read ports" means `SchemaSource`, `BehaviourSource`, `AutomationSource` (DR-03 line 20; spec 10 section 3.1) | unchanged | Different thing. The new ports' javadoc must not use "read port" for the ontology ports, or the other way round |

Spec 14 moves the Browse, Read and index-preparation ports, their types and `ObjectView` out of `spi.query` into `sequeless-query`. It leaves `QueryPort`, `AggregateRequest`, `AggregateResult` and `AggregateContract` behind on the Command side, and it states where they go in the prototype (#41; #16 point 2; #19 point 7). Spec 13 does not move them.

## 5. Modules and dependencies

### 5.1 Root POM and module list

- The root POM lists 9 modules (`grep -c '<module>' pom.xml` = 9; lines 24–32). No module is added or removed by this spec.
- The parent `sequeless-build-parent` is 0.1.2 (line 8). The `sequeless-bom` import is 0.1.3 (line 64), set by spec 12. This spec does not change either.
- The root version is `0.1.0-SNAPSHOT` (line 14).

### 5.2 Modules whose code changes, by step

| Module | Steps and changes | POM change |
|---|---|---|
| `sequeless-spi` | P1: package move; imports in the 49-file set (spi's 4 files); same-package users `ChangeSet`, `CommitResult`, `ObjectStorePort`, `package-info`. P2a: `AutomationPort` javadoc (lines 37–42). P2: `ActionExecutor` input. P4: `TriggerEvaluator.onSignal` removed. P5: `AutomationPort` ingress; `TriggerEvaluator` and `DerivationRecomputer` input types. P6: `ReadPort`, `BrowsePort`, the index-preparation port, `ObjectView` and the request records; `QueryPort` keeps `aggregate`; `ObjectStorePort.browse` removed | None expected |
| `sequeless-spi-testkit` | P1: 7 files import the types. P2 to P4: the `AutomationContract` seeds that use removed kinds (7 `new OutboxEntry(` sites). P5: `AutomationContract` ingress. P6: `ReadContract`; `BrowseContract` (`QueryContract` ported); `browse` clauses move; the doubles listed in P6. P7: none expected | None |
| `sequeless-core` | P1: 11 files import the types. P2a: the Store-event builders in `DefaultBusinessObjectService` and `DefaultTransitionService` (`transitionFiredEntry`) write the version. P2: `DefaultTransitionService` (`actionRequestEntry` removed; snapshot in `TransitionFired`); `DefaultActionExecutor` input. P3: timer builders removed from `DefaultTransitionService`; timer trigger lookup in `DefaultTriggerEvaluator` (O-2). P4: `DefaultTriggerEvaluator.onSignal` removed; signal path in `DefaultTransitionService`. P5: `DefaultTriggerEvaluator` `onChange` and `onTimerElapsed` take the ingress event; `DefaultDerivationRecomputer` input. P6: `DefaultBusinessObjectService` `read` and `browse`; test doubles (P6) | None expected |
| `sequeless-app` | P1: 4 files import the types. P2a: `OutboxRelay.DISPATCHABLE_KINDS` adds `TransitionFired`. P2 to P4: the relay drops the removed kinds, one per step. P5: `OutboxRelay` runs the recompute, then the ingress; the ingress caller rule in `ArchitectureTest`. P6: `ObjectsController` and `ObjectPropertyMapper` mappings; `CoreConfiguration` beans; `IndexPreparingOntologyAdministration` and `OntologyStartupValidator`; `ArchitectureTest.onlyHostPreparesIndexes`. P7: none expected | None expected |
| `sequeless-adapter-automation-inprocess` | P1: 4 files. P2a: `TransitionFired` accepted as a no-op. P2: `ActionRequest` case removed; actions from `TransitionFired`. P3: `InProcessTimerScheduler` takes `TransitionFired`. P4: `SignalReceived` case removed. P5: switch replaced by the Store-event ingress | None expected |
| `sequeless-adapter-automation-temporal` | P1: 15 files. P2a: `TransitionFired` accepted as a no-op. P2: `ActionWorkflow(Impl)` and `ActionActivities(Impl)` take the `TransitionFired` row; key (event id, action index). P3: `TimerWorkflow(Impl)` takes `TransitionFired`. P4: `SignalEventWorkflow(Impl)` removed with its kind. P5: `TemporalAutomationPort` switch (lines 75–81) replaced; `ChangeEventWorkflowImpl` no longer calls the recompute activity. In-flight workflows: O-4 | None expected |
| `sequeless-adapter-persistence-postgres` | P1: 4 files. P2a: V5 migration (section 5.3). `PostgresOutboxPort` code unchanged. P6: `PostgresObjectStore.browse` (86) removed; `PostgresQueryStore` implements `ReadPort`, `BrowsePort` and the index-preparation port; `aggregate` (175) stays; autoconfig beans (84, 112, 137). P7: V2 moves to the query location; `PostgresMigrations` runs two instances | None expected |
| `sequeless-adapter-ontology-jena` | None. `JenaOntologyAutoConfiguration` imports `ObjectStorePort` (line 13) for `ontologyDocuments()`, which stays (section 3) | None |
| `sequeless-adapter-authz-permitall` | None (`git grep` finds no outbox or port type) | None |
| `sequeless-ui` | P4: `automation-signals.spec.ts` (section 3). P6: none, unless O-8 forces a field change | None |

The gate's `dependency:analyze-only` check (spec 12 section 5.4) decides any POM change. A dependency left unused by a step is removed in that step's PR.

### 5.3 Migrations

- P2a: V5 (`db/migration/sequeless/postgres/V5__…`, name at implementation). It has three parts, as P2a lists: marks unprocessed `TransitionFired` rows processed (V4 precedent); marks unprocessed rows of the four removed kinds processed (O-3 default, with the caveat in section 3); and replaces V4's partial index (`sq_outbox_unprocessed_dispatchable_idx`) with one over the 8 claimable kinds.
- P5: none. Narrowing the index to four kinds is optional and is not part of this spec.
- P7: V2 moves to the query location, with the Flyway instances and history tables of the C5 design (section 3).

## 6. ArchitectureTest changes

Source: `sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java`. It has 14 `@ArchTest` members (`grep -c '@ArchTest'` = 14). Line numbers below are the `@ArchTest` lines.

### 6.1 Existing rules

| Rule (line) | Change in spec 13 |
|---|---|
| `spiDependsOnlyOnJdkAndItself` (65) | None. `spi.outbox` stays inside `SPI_PACKAGE` |
| `coreDependsOnlyOnJdkSpiAndItself` (97) | None |
| `spiAndCoreDoNotDependOnSpring` (130), `onlyAppMayDependOnSpringMvc` (142), `onlyAppRestAndConfigMayDependOnCore` (154), `noAdapterDependsOnCore` (167), `adaptersDoNotDependOnEachOther` (178), `noLombokInSpiOrCore` (186), `coreDoesNotDependOnAdapterSpecificLibraries` (197), `onlyTemporalAdapterMayDependOnTemporal` (210), `noJenaInSpiOrCore` (222) | None |
| `querySeesOnlySchema` (238) | None. It covers `ReadPort`, `BrowsePort` and `ObjectView`, because they sit in `spi.query` (`QUERY_PACKAGE`, line 46). They must not depend on `org.sequeless.ontology.api..` (`ONTOLOGY_PACKAGE`, line 47). `Schema` from `ontology.schema.api` is allowed |
| `onlyHostPreparesIndexes` (254) | P6. The rule guards `QueryPort#ensureIndexes`, which is removed. Its target moves to the index-preparation port's `prepare` (`ENSURE_INDEXES`, line 52, and the `QueryPort.class` target). The javadoc and `because` text (lines 250–262, which quote "the prepare(tenant, version) call of spec 13") name the new port and `prepare(tenant, Ontology version)`. Its exclusions (the host config package and the testkit query kit) stay |
| `suiteActuallyAnalyzesEveryModule` (277) | None required. Optionally assert that the new ports exist. Names chosen at implementation |

### 6.2 New rules and tests (names chosen at implementation)

- Ingress caller rule (P5). No class outside `org.sequeless.app.automation..` calls the ingress method of `AutomationPort`. It is modelled on `onlyHostPreparesIndexes`.
- Kind-set test (P2, P3, P4). A unit test in `OutboxEntryTest` asserts the public `KIND_` constants. The expected set is 8 through P2a (P2a removes no constant), 7 after P2, 5 after P3, and 4 after P4: `KIND_OBJECT_CREATED`, `KIND_OBJECT_UPDATED`, `KIND_OBJECT_DELETED` and `KIND_TRANSITION_FIRED`. The expected set is updated in each of P2, P3 and P4, so a fixed set of 4 cannot pass before P4. `OutboxEntryTest` has 11 `KIND_` references today.
- A new rule must match at least one class. Spec 12 section 6.1 gives the reason, and no released rule sets `allowEmptyShould`.

## 7. Wire compatibility

REST paths and the `/api` prefix do not change (`ApiPathPrefixConfiguration`, default `/api`; DR-17). The SPA's wire models are hand-written (DR-20, `docs/architecture/02-decision-records.md` lines 114–119). They are in `sequeless-ui/projects/core-api/src/lib/models/index.ts`.

### 7.1 Endpoints

| Endpoint (`ObjectsController.java` line) | Body | Changed by | Status |
|---|---|---|---|
| `GET /api/objects/{type}` (144) | `QueryResultResponse`: `items` (`BusinessObjectResponse[]`), `page`, `size`, `totalItems`, `totalPages`, `facets` | P6 mapping (Object view items) | unchanged |
| `GET /api/objects/{type}/{id}` (264) | `BusinessObjectResponse`, with `ETag` from `version` | P6 (read path) | unchanged |
| `POST /api/objects/{type}` (290), `PUT` (332), `POST .../transitions/{name}` (458) | `BusinessObjectResponse` | none (still mapped from `BusinessObject`) | unchanged |
| `POST /api/objects/{type}/{id}/signals/{name}` (501) | no body; 202 Accepted | P4 | 202 kept (O-5). A rejected signal now returns 409 (C7) |
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

GLOSSARY line 48 defines Object view as "its identity, type, property values and version", and #16 point 9 lists the same fields plus a display label. Neither lists `state` or `audit`. Following that text literally would drop two JSON fields that the SPA's model requires. The display label is not rendered in JSON today, so adding it to the Object view is not a JSON change. O-8 records the choice.

`FacetResponseMapper` and `FacetBucketResponse` (`index.ts` lines 132–135) are not changed.

### 7.3 Internal shapes (not REST)

`OutboxEntry` payloads are JSON in `sq_outbox` and in Temporal workflow inputs. They change in P2a to P5 (the version stamp is added in P2a). This is not a REST contract. The compatibility risks are O-3 (table rows) and O-4 (running workflows).

### 7.4 Verdict and checks

- Wire-compatible, with no UI change, if O-8 keeps `state` and `audit`. The one behaviour change is that a rejected signal returns 409 instead of a silent 202 (P4). The P4 UI test covers it.
- UI checks after P4 and P6: `ng test` in `sequeless-ui` (the `test` script; `models.spec.ts` has 8 references to `BusinessObjectResponse`), and `playwright test` (the `e2e` script) with `objects-stale-conflict.spec.ts` (ETag) and `automation-signals.spec.ts` (signal POST).
- The JSON golden diff in P6 (section 3) is the server-side check.

## 8. Release config

None. The prototype is not released. The root version stays `0.1.0-SNAPSHOT` (line 14). No artifact is produced, no dependency is added (section 5.1), and the BOM is unchanged.

## 9. CI and workflows

- Gate, run by the driver at P0 and at each step (this draft ran none):

  ```
  source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify
  ```

  `~/.bashrc.ext` supplies the Packages token, so it must be sourced in the same command as every `mvn` run. `./mvnw` does not exist: `git ls-tree -r --name-only 3328993 | grep -c '^mvnw'` prints 0.
- `*IT` classes (the Postgres ITs, `AcceptanceCriteriaIT`, `FatJarServesUiAndApiIT`, `OntologyPersistenceAcrossRestartIT`) run in Failsafe's integration-test phase (`sequeless-app/pom.xml` line 199). Their run in the gate for every module is unverified.
- Acceptance and e2e tests stay green at every step. The set is decided (O-11):
  - Java: 13 files named `*EndToEndTest` or `AcceptanceCriteriaIT`. `git ls-tree -r --name-only 3328993 | grep -E 'EndToEnd|AcceptanceCriteria'` lists them. Then `git grep -c -E '^\s*@Test\s*$' 3328993 -- <those 13 files>` counts 55 `@Test` methods in total. The anchored pattern skips `@TestConfiguration` and javadoc mentions.
  - Playwright: 4 spec files. `git grep -c -E '\btest\(' 3328993 -- 'sequeless-ui/e2e/*.spec.ts'` gives 6 `test(` calls in total. `support/browser-check.ts` and `support/test.ts` are outside the set.
  - 19 `sequeless-app` test files reference `"/api`: `git grep -l '"/api' 3328993 -- sequeless-app/src/test | wc -l`.
  - The issue says "24 acceptance/e2e tests". The commands above give 55 Java and 6 Playwright (61 in all). No source for 24 was found in the repo, in issue search or in merged PR bodies. The count is not reproduced (O-11).
- SNAPSHOT rule (spec 12 section 9): `git grep -n -- SNAPSHOT 3328993 -- '*pom.xml' | grep -v '<version>0.1.0-SNAPSHOT</version>'` prints nothing.
- Prototype CI: `.github/workflows/ci.yml` line 13 pins `sequeless/sequeless-build` `maven-ci.yml@workflows-v0.1.0`. This spec does not change the pin. The prototype's CI must be green on every step's PR.
- Each step's PR passes the gate, the `ArchitectureTest` run inside it, and the tests named in section 3.

## 10. ADRs

`docs/adr/` does not exist in the prototype (`ls -d docs/adr` fails). The decisions that govern this spec are in `docs/architecture/02-decision-records.md` and in the issues.

| Record | Text (summary) | Effect on spec 13 |
|---|---|---|
| DR-09 "State machines and automation" (line 56) | "state change and outbox rows committed atomically"; Temporal is the default `AutomationPort` adapter | Atomic commit stays (P2 to P4 write one `ChangeSet`). Outbox rows hold Store events only once P4 has landed |
| DR-12 "Events and outbox" (line 73) | "the core emits `DomainEvent`s and `ActionRequest`s into the `ChangeSet`" | Text stays. A new DR entry (DR-21) records that `ActionRequest` leaves (P2; O-16) |
| DR-08 "Derived properties" (line 50) | `QueryPort.aggregate` executes rollups | Unchanged: `aggregate` stays on `QueryPort`, on the Command side |
| DR-03 (line 17) | ontology read ports `SchemaSource`, `BehaviourSource`, `AutomationSource` | Term collision (section 4) |
| DR-10 (line 64), DR-17 (line 98) | REST resources; `/api` prefix | Unchanged |
| DR-20 (line 114) | hand-written wire models | Section 7 |
| #16 points 1, 2, 6, 9, 12 | Read and Browse each through its own port; aggregate leaves the Query side; Object views; the contract kit | P6; section 4 |
| #20 points 3 to 6 | Store events only, stamped with the Ontology version; the relay feeds automation and the recomputer; automation owns the outbound port; timers and signals | P2a to P5; O-1, O-2, O-17 |
| #24 (resolution, Spec 13 list) | `prepare(tenant, Ontology version)` replaces `ensureIndexes` at the three host call sites | P6; O-9 |
| #23 | extraction order and history | Section 3 order |

No ADR is added here. The index-preparation ADR belongs to spec 14 in `sequeless-query` (#24, the process note). DR-21 is a DR-file entry (O-16), not an ADR.

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

- The acceptance and e2e tests stay green at every step: the 13 Java files (55 `@Test` methods) and the 4 Playwright spec files (6 `test(` calls). O-11 decides this set.
- The UI wire contract is verified: `index.ts` matches the JSON after P6 (the golden diff in section 3), and `ng test` and `playwright test` pass after P4 and P6. No field changes without a matching edit to `index.ts`.
- The PR closes [#41](https://github.com/sequeless/sequeless-prototype/issues/41), and the spec 13 row of `remaining-work.md` is ticked when the last step lands.
- Spec 17 is the stage 4 cut-over in the tracker (row 17). Spec 13 is a prepare step, so the cut-over clause above does not apply to it.
- Status lines, to fill before merge: the gate result for each step (P0 to P7) and the PR's checks. None were run in this draft.

## 12. Open items for the driver

Each item names the evidence and the command that settles it. Items marked DECIDED are recorded with their evidence, and the owner may change them later. Only O-8 is an owner decision. Settled decisions are in sections 2 to 7.

1. **O-1. Where a fired transition's actions live (P2). Driver decision (recorded): DECIDED, option (b).** The Command side does not build action payloads. `TransitionFired` carries a frozen snapshot of `self`, which `actionRequestPayload` freezes today (`DefaultTransitionService` line 479). Automation builds the actions from the automation-contract of the Ontology version stamped on the event. Evidence: #20 point 3 ("so the Command side no longer knows the shape of actions") and point 5 (triggers are evaluated against the automation-contract of the version stamped on the event). Option (a) contradicts point 3. The snapshot question is decided too: keep a snapshot of `self` in the event, so expressions stay deterministic. The frozen-payload rule (`spi/automation/package-info.java` lines 33–35) still holds, because the snapshot is frozen in the event. Settle: `gh issue view 20 --json comments --jq '.comments[].body' | grep -n -F 'no longer knows the shape'`; `git grep -n actionRequestPayload 3328993 -- sequeless-core`.

2. **O-2. Timer trigger source (P3). Driver decision (recorded): DECIDED.** The `TimerTrigger` lookup stays in core, in `DefaultTriggerEvaluator` (`core/automation`). It reads the timer trigger from the behaviour of the event's Ontology version. The duration stays on `Transition` (spec 10 Q4: actions-only automation). Evidence: #20 point 2 (`behaviour-contract` holds "which transitions are timer- or signal-triggered") and point 6 (automation schedules a timer from a Store event). Today `TimerTrigger` appears in `DefaultTransitionService` (lines 40, 378 and 403) and in no adapter. Settle: `git grep -n 'TimerTrigger' 3328993 -- sequeless-core/src sequeless-adapter-automation-inprocess/src/main sequeless-adapter-automation-temporal/src/main`.

3. **O-3. Unprocessed rows of removed kinds (P2a). Owner check (not run here). Default: mark processed, per V4's precedent.** Settle, once per database that ran V4: `SELECT kind, count(*) FROM sq_outbox WHERE processed_at IS NULL GROUP BY kind;`. The repo shows one database, the compose service (`docker-compose.yml`: user and database `sequeless`). With the compose stack up: `docker compose exec postgres psql -U sequeless -d sequeless -c "SELECT kind, count(*) FROM sq_outbox WHERE processed_at IS NULL GROUP BY kind;"`. To find other databases: `git grep -n 'jdbc:postgresql' 3328993 -- '*.yml' '*.yaml' '*.properties' '*.env'` (this search finds only `application.yaml` line 25, `localhost:5432`). Not run in this draft: no Postgres container is running and `psql` is not installed. Default policy: V5 runs as written in P2a. If a non-dev database has pending rows of the four removed kinds, drain them with the old code first, or move the removed-kind part of V5 into P2 to P4 (section 3 caveat).

4. **O-4. In-flight Temporal workflows (P2 to P4). Owner check (not run here). Default: accept loss only for dev volumes; otherwise drain.** Settle: `docker volume ls --format '{{.Name}}' | grep -E 'sequeless-(pgdata|temporal)'` lists the dev volumes that `docker-compose.yml` names. With the compose stack up, `temporal workflow list --address 127.0.0.1:7233 --query 'ExecutionStatus="Running"'` lists running workflows on the dev server. The `temporal` CLI is not installed in this draft's environment, so these flags are unverified. Default policy: a dev volume may lose its running workflows. Any other environment drains them before the step that changes their kind deploys.

5. **O-5. Signal response code (P4). Driver decision (recorded): DECIDED, keep 202 for an accepted signal.** Evidence: `docs/plans/phase-6-objects.md` line 91 states 202. The UI client (`sequeless-ui/projects/core-api/src/lib/clients/objects-client.ts`, line 78) returns `Observable<void>`, so any 2xx reads alike. #41's update requires wire compatibility. Rejections are C7 (P4): 409. Settle: `sed -n '91p' docs/plans/phase-6-objects.md`; `sed -n '78p' sequeless-ui/projects/core-api/src/lib/clients/objects-client.ts`.

6. **O-6. Ingress method and event type names (P5). Driver decision (recorded): DECIDED, names stay inside the prototype.** #24 point 4 says the cross-repo Store event type "isn't placed here", leaves it to automation's spec (15), and rejects a shared type now. The ingress takes a Store event, the term GLOSSARY line 81 defines ("the kind of Domain event a Store produces when it accepts a Store Command"). "Domain event" (line 77) is the wider term. Method and class names are chosen at implementation. Settle: `gh issue view 24 --json comments --jq '.comments[].body' | grep -n -F "isn't placed here"`; `sed -n '77,82p' GLOSSARY.md`.

7. **O-7. Command-side `find` (P6). Driver decision (recorded): DECIDED. `find` stays on `ObjectStorePort`, and `ReadPort` gets its own read path.** Evidence: `git grep -n 'objectStorePort\.find' 3328993 -- sequeless-core/src/main` gives 14 calls. Only `DefaultBusinessObjectService.read` (line 221) is a Read. The other 13 are Command-side or automation callers: the edit and delete preconditions (291, 335), `DefaultTransitionService` (131, 212, 278), `DefaultTriggerEvaluator` (95, 142, 165, 200), `DefaultActionExecutor` (84, 133) and `DefaultDerivationRecomputer` (130, 192). #19 point 2 narrows `ObjectStorePort` to `find`, `typesOf`, `commit` and the aggregate query. Settle: the grep above, and `gh issue view 19 --json comments --jq '.comments[].body' | grep -n -F 'narrows'`.

8. **O-8. Object view contents (P6). OWNER DECISION. The only one.** Options:
   - (a) Keep `state` and `audit` in the Object view and in the JSON, and amend GLOSSARY lines 47–48 to list them. Recommended. #41's wire rule keeps the JSON, and the SPA's model requires both fields.
   - (b) Drop `state` and `audit` from the Object view and from the REST JSON. This changes `BusinessObjectResponse`, and the SPA model (`index.ts` lines 115–130) must change in the same PR.

   Settle: `sed -n '47,48p' GLOSSARY.md`; `sed -n '115,130p' sequeless-ui/projects/core-api/src/lib/models/index.ts`; `sed -n '26,33p' sequeless-app/src/main/java/org/sequeless/app/rest/BusinessObjectResponse.java`; and point 9's field list, `gh issue view 16 --json comments --jq '.comments[].body' | grep -n -F 'Object view'`.

9. **O-9. Index preparation name and signature (P6). Driver decision (recorded): DECIDED, from #24's resolution (the Spec 13 list).** Spec 13 swaps the three host call sites (`IndexPreparingOntologyAdministration` line 82, `OntologyStartupValidator` line 65, and the reload path that #24 point 7 names) to `prepare(tenant, Ontology version)`, and removes `ensureIndexes` from `QueryPort`. The prototype's `prepare` delegates to today's index code, so the DDL does not change. The version guard and the "done or durably accepted" promise belong to spec 14 (#24 points 8 to 10 and its Spec 14 list). #24's Spec 13 note, "nothing changes in the events, the outbox or the relay", describes this swap, not the rest of spec 13. Other sources agree: #11's summary of #24; spec 10 lines 31 and 224 (which put the swap in spec 13); and `ArchitectureTest.java` line 263. Settle: `gh issue view 24 --json comments --jq '.comments[].body' | grep -n -E 'prepare\(|ensureIndexes'` (the Spec 13 list is among the matches); `git show 3328993:docs/extraction/spec-10-prototype-stage3-prepare.md | grep -n 'prepare'`.

10. **O-10. Index DDL separation (P7). Driver decision (recorded): DECIDED, option (a), a separate migration location for the Query side.** Evidence: #19 point 3 ("`sequeless-query` owns only its indexes"); #16 point 3 (index DDL is the Query side's); #41's scope ("separated from the object and outbox tables"). Option (b), no DDL change, fails #41's scope. The design and its settling test are the C5 design (section 3). Open owner question, non-blocking, and not a decision item: should the `search_vector` column on `sq_object` (V2 line 19), its function (line 54) and its trigger (line 68) stay with the Query side? #19 point 3 calls the object table a published Command-side contract. The first P7 commit is not blocked: it moves V2 with the column into the query location, and the owner answers before any later change to the column. Settle: `git grep -l -E 'sq_index_registry|sq_searchable_property' 3328993 -- '*/src/main/*'`; `git show 3328993:sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres/V2__query_indexes_and_search.sql | sed -n '19p;54p;68,72p'`.

11. **O-11. The "24 acceptance and e2e tests" count (section 9). Driver decision (recorded): DECIDED, the set is the 13 Java files (55 `@Test` methods) and the 4 Playwright spec files (6 tests).** The issue says only "keep them green", and no command here reproduces 24. Settle: the `git ls-tree` and `git grep -c` commands in section 9.

12. **O-12. Gate on JDK 25 at each step (P0 to P7). Driver check.** Not run in this draft, because the gate starts with `mvn spotless:apply`, which rewrites sources. Prerequisites: `/opt/sdkman/candidates/java/25.0.3-tem` exists on this machine (checked), and `./mvnw` is absent at `3328993` (section 9). Settle: the gate in section 9, at P0 and at every step. Record each result in the PR body and the status lines.

13. **O-13. Test class names (P2a, P5, P6, P7). Driver decision (recorded): the driver names them in the PR bodies.** The V5 test, the Read and Browse contract runners and the query-migration test are named at implementation, as in spec 12 item 3.

14. **O-14. Spec 12 tracker row. Driver check.** Row 12 reads `[x] | 12 prototype stage 3 cut-over` at `origin/main` `dbf20e6` (cut-over `3328993`; #40 closed). No change is needed. Settle: `git show origin/main:docs/extraction/remaining-work.md | grep -n -F '| 12 prototype stage 3'`.

15. **O-15. Outbox package name (P1). Driver decision (recorded): DECIDED, `org.sequeless.spi.outbox`.** Evidence: #41's comment of 2026-11-05 ("This spec rewrites `OutboxEntry` anyway, so it picks the package"). prefactor-plan section 4 proposes `spi.outbox`. The import count (49, section 2) matches the plan's figure. Settle: `gh issue view 41 --json comments --jq '.comments[].body' | grep -n -F 'picks the package'`; `git show 3328993:docs/extraction/prefactor-plan.md | sed -n '50,58p'`.

16. **O-16. Recording the `ActionRequest` removal (P2). Driver decision (recorded): DECIDED, a new DR entry, DR-21.** The DR file has no amendment precedent: `git show 3328993:docs/architecture/02-decision-records.md | grep -c -i -E 'amend|supersed'` prints 0, and DR-20 is the last entry. The removal is decided by #20 point 3. DR-12's text stays as written. Settle: the grep above, and `git show 3328993:docs/architecture/02-decision-records.md | sed -n '73,76p'`.

O-17 (side effects aimed at a Store) is not open. #20's resolution point 5 decides it: the outbound port belongs to automation, and the host binds it to store-core's Store Command port. Section 3 records the decision. This spec keeps today's path.
