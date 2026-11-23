# Spec 13, P3: timers leave the outbox (plan)

Status: plan only, no code. Made against the P2d branch head `197e36c` (PR #168, not merged). Parent: `docs/extraction/spec-13-prototype-stage4-prepare.md` (the P3 row is at line 92; "spec" below means that file). "Resume plan" is `docs/extraction/resume-plan-spec13.md`. Tracker: #41.

Conventions: `{main}` stands for `src/main/java/org/sequeless` and `{test}` for `src/test/java/org/sequeless`, inside the module named first. Line numbers are P2d unless marked "main" (`da3c08d`).

## 1. Scope and base

- P3 is planned as a stack on P2d. P2d is not merged, and P3 changes code that P2d leaves in place.
- No P3 code starts until P2d and P2e have merged. The handoff orders P3 after them (resume plan line 345: "After P2d merges: P2e ..., then P3, P4 and P5b"). The spec gives the same order (line 76). P2e removes `KIND_ACTION_REQUEST` (`{main}/spi/outbox/OutboxEntry.java:64-74`, deprecated in P2d) and `ActionWorkflow`.
- B2 is settled as route 1 (resume plan lines 159 and 262). DR-23 changed its wording to "three failed dispatches, then dead-letter" (`docs/architecture/02-decision-records.md:145-161`). The spec's B2 item still reads "OWNER DECISION, open" (line 366), and its route 1 reads "fail and retry" (line 367). The spec is stale here.
- O-3 and O-4 were answered on 2026-11-14 (resume plan lines 239-240), before P2d. The spec's policy (line 332, and steps 3 and 4 at lines 104-105) asks for the count again when each removing step deploys.
- The spec's line numbers for this code are stale. Row P3 (line 92) and O-2 (line 330) cite `addTimerOutboxEntries` at 369-385, `timerScheduledEntry` at 398-414, and the lookup at 377-378. At P2d these are 362-382, 392-411 and 372. On main, `addTimerOutboxEntries` starts at 372. This plan uses P2d numbers.

## 2. Inventory

Main code (P2d):

- `sequeless-spi/{main}/outbox/OutboxEntry.java`: class javadoc 23-24; `KIND_TIMER_SCHEDULED` at 86 and `KIND_TIMER_CANCELLED` at 97, with javadoc 76-96.
- `sequeless-core/{main}/usecase/DefaultTransitionService.java`: `TimerTrigger` import 35; `commitTransition` javadoc 299-305 and call 346; `addTimerOutboxEntries` 353-382 (lookup loop 371-381, `instanceof TimerTrigger` 372, cancel 376, schedule 379); `timerScheduledEntry` 384-411 (`timer.after()` at 407, kind at 410); `timerCancelledEntry` 413-428 (kind at 427); `timerKey` 430-438, used by both builders at 408 and 425; `transitionFiredEntry` 444-464.
- `sequeless-core/{main}/automation/DefaultTriggerEvaluator.java`: constructor 61-70 (holds `schemaSource` and `behaviourSource`); `onSignal` 157-189 (its transition lookup at 171-173 is the pattern for the timer lookup); `onTimerElapsed` 133-154 (payload read 137-140, staleness check 148-152, fire 153).
- `sequeless-spi/{main}/automation/TriggerEvaluator.java`: `onTimerElapsed` 49-67 (line 55 cites `KIND_TIMER_CANCELLED`). `AutomationPort.java` 41-45 (the `@throws` list names both timer kinds).
- `sequeless-adapter-automation-inprocess/{main}/adapter/automation/inprocess/InProcessTimerScheduler.java`: the whole class (98 lines); `schedule` 61-67, `cancel` 76-79, `sweep` 87-97, javadoc 13-28.
- `...inprocess/InProcessAutomationPort.java`: constructor 104-116 (scheduler at 108); dispatch switch 134-141 (timer cases 137-138); `dispatchTransitionFired` 157-193 (action loop 177-192); the gate comment for TransitionFired at 124-125.
- `...inprocess/InProcessAutomationAutoConfiguration.java`: `clock` 58-62; `timerTickExecutor` 64-71 (its javadoc sits at 39-48, above the clock's javadoc); `timerScheduler` bean 91-100 (`scheduleWithFixedDelay` at 98).
- `sequeless-adapter-automation-temporal/{main}/adapter/automation/temporal/TimerWorkflow.java`: interface 26-56; `run` 36-37; `cancel` 44-45; `isAwaiting` 54-55; javadoc 10-24.
- `...temporal/TimerWorkflowImpl.java`: `run` 22-40 (`after` parsed at 30, `Workflow.await` at 36, `onTimerElapsed` at 38); `cancel` 43-45; `isAwaiting` 48-50.
- `...temporal/TemporalAutomationPort.java`: switch 83-91 (timer cases 86-87); `startTransitionFiredActions` 129-160 (zero-action sentence at 121); `startTimerWorkflow` 169-177; `signalTimerCancel` 185-193; `workflowOptions` 195-201 (REJECT_DUPLICATE at 199).
- `...temporal/TriggerActivities.java` 30-32 and `TriggerActivitiesImpl.java` 45-48 (`onTimerElapsed`).
- `...temporal/TemporalAutomationAutoConfiguration.java` 145-150 (`TimerWorkflowImpl` registered at 149).
- `sequeless-app/{main}/app/automation/OutboxRelay.java`: javadoc 21-30; `DISPATCHABLE_KINDS` 53-60 (timer kinds at 58-59); comment at 150. `package-info.java` line 7 names the timer kinds.
- `sequeless-app/{main}/app/config/CoreConfiguration.java:342` (javadoc).
- `sequeless-app/{main}/app/rest/TypeResponseMapper.java` 26, 168, 181, 195: reads `TimerTrigger` for REST output. Not changed by P3.
- `sequeless-adapter-persistence-postgres/{main}/.../postgres/PostgresOutboxPort.java` 83-85: claim SQL `WHERE kind IN (:kinds)`. Not changed by P3.
- Migrations V4 and V5 (`sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres/`) list both timer kinds in the claim index. Main ends at V7. V8 exists only at P2d.

Tests (P2d):

- `sequeless-core/{test}/usecase/DefaultTransitionServiceTest.java`: fixture 187; entering a timer state 526-539 (`hasSize(2)` at 536); leaving 553-570 (`hasSize(2)` at 563).
- `sequeless-core/{test}/automation/DefaultTriggerEvaluatorTest.java`: `TimerTrigger` fixture 114; helper `timerScheduledEvent` 209-215; `onTimerElapsed` tests from 319.
- `sequeless-spi-testkit/{main}/testkit/automation/AutomationContract.java`: `advanceTime` 107-120; timer contracts 240-297; entry builders 332-362.
- `RecordingTriggerEvaluator.java` 35-36 and 60 (keeps `onTimerElapsed`; no change).
- `sequeless-adapter-automation-inprocess/{test}/...`: `InProcessAutomationPortContractTest.java` 70-73 (builds the scheduler) and 113-115 (`advanceTime` sweeps); `InProcessAutomationPortTransitionFiredTest.java` 64 and 178 and `InProcessAutomationPortWebhookTest.java` 135 (build the scheduler); `MutableClock.java` 11-12 (javadoc).
- `sequeless-adapter-automation-temporal/{test}/...TemporalAutomationPortContractTest.java`: javadoc 65-75 and 102-105; `TimerWorkflowImpl` registered at 141; dispatch wrapper cases 178-184; `awaitTimerWorkflowParked` 225-245; `awaitCancellationTakesEffect` 248-260; settle 269-301; delegate 480-481.
- `sequeless-app/{test}/app/AutomationEndToEndTest.java`: javadoc 44-46 and 91; awaits on `TimerScheduled` at 197; the hold-then-resume test 231-253, with awaits on `TimerScheduled` and `TimerCancelled` at 247-248.
- `sequeless-app/{test}/app/TypesEndToEndTest.java` 226-229: REST expectations for `expireHold` (TIMER, PT72H). Not changed by P3.
- `sequeless-adapter-persistence-postgres/{test}/...`: `PostgresOutboxDecodeFailureIT.java` 43-44 (uses `KIND_ACTION_REQUEST` and `KIND_TIMER_SCHEDULED`); `PostgresOutboxPortIT.java` 109 and 112 (constants). `PostgresOutboxClaimIT.java` 44-45 and 69, and `PostgresOutboxV5MigrationIT.java` 43-44 and 70-71, use string literals and compile after P3.
- `sequeless-spi/{test}/outbox/OutboxEntryTest.java`: 11 `KIND_` references and no assertion on the set of kinds. The kind-set test that the spec describes (line 208) does not exist yet.

Docs (not code): `docs/architecture/automation.md` and `docs/architecture/sq-vocabulary.md` name the timer kinds. `docs/architecture/02-decision-records.md` has DR-24 at P2d.

## 3. The new data path

Today (P2d). `commitTransition` (314-351) puts one `TransitionFired` row in the outbox and, through `addTimerOutboxEntries` (346), one `TimerScheduled` for each timer transition that departs `toState`, and one `TimerCancelled` for each that departs `fromState`. All of it goes in one `ChangeSet` (348). The relay claims both timer kinds (`OutboxRelay.java:58-59`), and the adapter schedules or cancels.

After P3 (spec row P3, line 92, and #20 point 6):

- A move writes only `TransitionFired`. Its payload (`transitionFiredEntry`, 444-464) holds `objectId`, `tenantId`, `principalId`, `transitionName`, `fromState`, `toState`, `ontologyVersion`, `self`, `typeIri` and `machineIri`.
- The row does not hold the timer transition's name or its `after`. Both come from the behaviour: `timer.after()` is read at 407 today. The timer key stays `objectId|state|transitionName` (`timerKey`, 436-438).
- The lookup loop (371-381) moves to `DefaultTriggerEvaluator` (O-2). It reads the machine from the behaviour, as `onSignal` does (171-173), and selects the `TimerTrigger` transitions that depart `toState`. Which version it reads is open question 5.
- Readers of the row after P3: the relay (`OutboxRelay`, unchanged for `TransitionFired`), and `InProcessAutomationPort.dispatchTransitionFired` and `TemporalAutomationPort.startTransitionFiredActions`. Both read `ontologyVersion`, `machineIri`, `fromState` and `transitionName` today.
- Zero-action transitions. Both adapters start nothing when the transition has no actions: the loop at `InProcessAutomationPort.java:177` runs zero times, and `TemporalAutomationPort.java:121` says so. The timer start must sit outside that loop, or a transition with only a timer would lose its timer. This plan puts it outside.
- Cancellation. The row's `fromState` says an object left a state, but a cancel needs the timer key, which needs the same lookup on `fromState`. The spec says `TimerCancelled` has no reader after P3 (row P3). The only guard left is the staleness check (`DefaultTriggerEvaluator.java:143-152`). Open question 1.
- Start time. The in-process scheduler starts the clock at dispatch (`InProcessTimerScheduler.java:66`), and Temporal starts the workflow at dispatch. P3 does not change when the clock starts.

## 4. Change list

Delete:

1. `OutboxEntry.java`: `KIND_TIMER_SCHEDULED` (86), `KIND_TIMER_CANCELLED` (97) and their javadoc (76-96). Reword 23-24.
2. `DefaultTransitionService.java`: the import (35), the call (346), the javadoc at 302-305, `addTimerOutboxEntries` (353-382), `timerScheduledEntry` (384-411) and `timerCancelledEntry` (413-428). `timerKey` (430-438) moves to the place chosen in open question 12.
3. `InProcessTimerScheduler.java`: `cancel` (69-79). Its only caller is `InProcessAutomationPort.java:138`.
4. `InProcessAutomationPort.java`: the timer cases (137-138).
5. `TemporalAutomationPort.java`: the timer cases (86-87), `startTimerWorkflow` (169-177) and `signalTimerCancel` (185-193). The start moves into `startTransitionFiredActions`.
6. `OutboxRelay.java`: the timer kinds leave `DISPATCHABLE_KINDS` (58-59). Rewrite the javadoc (21-30) and the comment at 150.
7. `AutomationPort.java` 41-45: remove the two timer kinds from the `@throws` list.

Edit:

8. `DefaultTriggerEvaluator.java`: add the timer lookup, and a method that returns the timers a `TransitionFired` row starts (its inputs and outputs depend on open questions 3 and 5). `onTimerElapsed` keeps the staleness check (148-152); its input changes from a `TimerScheduled` entry.
9. `TriggerEvaluator.java` (SPI): rewrite the javadoc at 49-66 (line 55). Add a method only if open question 3 needs one.
10. `InProcessTimerScheduler.java`: `schedule` takes a timer descriptor (key, due duration, scope, `objectId`, `state`, `transitionName`) instead of an `OutboxEntry` (61-67). Rewrite the javadoc at 13-28, which names the deleted kind.
11. `InProcessAutomationPort.java`: `dispatchTransitionFired` (157-193) starts the row's timers outside the action loop. The per-action redelivery record stays.
12. `InProcessAutomationAutoConfiguration.java`: move the javadoc at 39-48 onto `timerTickExecutor` (64). No bean signature change is expected.
13. `TimerWorkflow.java`: `run` (36-37) takes the new input (open question 4). Rewrite the javadoc at 10-24, which describes the `TimerScheduled` input.
14. `TimerWorkflowImpl.java`: `run` reads `after` from its input, not from `entry.payload()` (30). `Workflow.await` (36) and the activity call (38) stay.
15. `TriggerActivities.java` and `TriggerActivitiesImpl.java`: change only if open question 4 changes the input.
16. `TemporalAutomationPort.java`: `startTransitionFiredActions` (129-160) starts the timer workflow for the row, keyed by `timerKey`, with `workflowOptions` (195-201). Rewrite the javadoc at 22-23, 163-168 and 180-184.
17. `PostgresOutboxDecodeFailureIT.java` (43-44) and `PostgresOutboxPortIT.java` (109, 112): replace the deleted constant. The first file also names `KIND_ACTION_REQUEST`, which P2e removes.
18. `DefaultTransitionService.java`: `commitTransition` (314-351) keeps the version check (323-327) and the single `TransitionFired` entry (345). Reword the class javadoc at 67.
19. Comments that name the timer kinds, at `OutboxRelay.java:150`, `CoreConfiguration.java:342` and `package-info.java:7`: reword.

Add:

20. A migration `V9__...sql` (name at merge; section 5), under `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres/`.
21. `PostgresOutboxV9MigrationIT.java`, modelled on `PostgresOutboxV8MigrationIT.java` (P2d, 120 lines).
22. A kind-set assertion in `OutboxEntryTest` (open question 13).
23. A DR entry, and doc updates to `docs/architecture/automation.md` and `sq-vocabulary.md` (open question 14).

Tests changed:

24. `DefaultTransitionServiceTest` 526-570: entering a timer state commits one row. Change `hasSize(2)` (536, 563) and the timer assertions (537-550, 564-571).
25. `DefaultTriggerEvaluatorTest`: the helper (209-215) and the tests from 319 move to the new input. Add cases for the lookup: state, duration, key, no timers for a transition without a timer trigger, and a stamp failure.
26. `AutomationContract.java`: the timer contracts (240-297) are driven from a `TransitionFired` row. The redispatch contract (284-297) keeps dispatching the same row twice. The builders (332-362) change.
27. `TemporalAutomationPortContractTest.java`: the wrapper cases (178-184) change. `awaitCancellationTakesEffect` (248-260) goes if open question 1 drops cancellation.
28. `AutomationEndToEndTest.java`: the awaits at 197 and 247-248 wait on `TransitionFired` rows, and the comment at 241-242 changes. The assertion (no auto-close after resume) stays.

No change: `RecordingTriggerEvaluator`, `PostgresOutboxClaimIT`, `PostgresOutboxV5MigrationIT`, `TypeResponseMapper`, `TypesEndToEndTest`, `TemporalAutomationAutoConfiguration`, `PostgresOutboxPort`. `InProcessAutomationPortContractTest` changes only if the scheduler constructor changes.

## 5. Migration

P3 needs one. The spec says so: a migration, numbered at merge, that "marks the remaining `TimerScheduled` and `TimerCancelled` rows processed, after their drain" (line 184; row P3, line 92). What the repo adds:

- Main ends at V7. P2d adds V8, whose statement (lines 5-9) stamps `ActionRequest` rows, and its comment says "including dead-lettered ones (V7)". The same statement shape would stamp dead-lettered timer rows.
- DR-23 keeps dead-lettered rows unprocessed, so that O-3 counts them. It says the removing steps "must drain or resolve dead-lettered rows before their kinds leave the outbox" (lines 156-157). That conflicts with the V8 shape for timer rows. Open question 8.
- Number: V9 is the next free number after P2d's V8. The resume plan says the held index branch renumbers to V9 (line 334). Open question 7.
- Index: the spec's migration list gives P3 no index change (lines 182-186). The claim index (V4, V5) keeps both timer kinds, which is harmless. Narrowing is optional and outside the spec (line 186).
- Frozen sets: `frozen-migrations/post-p7/command` holds V1 and V3 to V6, and `PostgresMigrationFrozenSetsTest` checks that each frozen file matches its live file byte for byte. A new migration adds no frozen copy and does not change that test.
- Timing: the migration runs at startup of the new version, after the O-3 count (spec step 5, line 106). A row left unprocessed at that point is marked processed, and its timer is lost with no record (spec line 108).

## 6. Proof and tests

Gate, exactly:

    source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify

JDK 25 is required; the default `java` is 21 (resume plan line 348).

Proof, by layer:

- Core: entering a timer state commits one `TransitionFired` row and no `TimerScheduled` (spec line 92). Leaving commits no `TimerCancelled`.
- Core lookup: a `TransitionFired` row into `OnHold` yields `expireHold`, `PT72H` and key `objectId|OnHold|expireHold`. A transition with no timer yields nothing. A stamp mismatch fails in the way open question 6 settles.
- Core staleness: the three `onTimerElapsed` cases, with the new input.
- Contract, both adapters: fires only after `advanceTime`; the same row dispatched twice schedules once; a transition with no actions still schedules its timer.
- End to end, in-process: hold, then advance, auto-closes; hold, resume, advance, does not.
- End to end, Temporal: `TemporalAutomationEndToEndTest`. The test that redispatches the same row after a restart (line 232) keeps its premise.
- Postgres: `PostgresOutboxV9MigrationIT`. It stamps timer rows and leaves the rest alone, and leaves dead-lettered rows alone if open question 8 says so.
- Kind set: `OutboxEntryTest` expects five constants after P3 (spec line 208), with P2e merged.
- Dev stack, before the new version starts: the O-3 count is zero for every claimed kind, and O-4 lists no running `TimerWorkflow` (spec steps 3 and 4, lines 104-105). Record both results in the PR.

No test covers replay. The P2d code has no replay test and no `Workflow.getVersion` call (grep of the worktree). Section 7 depends on this.

## 7. Risks

1. In-flight timers. In-process timers live in memory and are lost at a stop (`InProcessTimerScheduler.java:19-22`). Temporal timers can wait 72 hours (`reference.ttl:310`). The mixed-version procedure applies (spec lines 100-108): stop writes, drain every claimed kind, then wait for or terminate the running timers. Terminating needs the owner's written acceptance of the loss (spec line 105).
2. Replay. `TimerWorkflowImpl.run` changes its input. A running old execution replays its recorded history under the new code, and a changed workflow can fail on replay. The spec avoids this with drain or terminate, not versioning. Only P5a mentions `Workflow.getVersion` (line 90). Open question 11.
3. Memory. Without `TimerCancelled`, a timer stays pending until it is due, up to 72 hours per hold, and then no-ops at the staleness check. Pending entries for objects that left the state pile up in the in-process map, and a restart drops them.
4. Re-hold on Temporal (read from the code, not run). A second entry into the same state for the same object has the same `timerKey`, so the same workflow id. `workflowOptions` uses REJECT_DUPLICATE (199), which refuses that id even after a closed run (javadoc 42-51). `startTimerWorkflow` catches the error and does nothing (174-176). Today the first run is cancelled on leaving, so a second hold gets no timer at all. After P3 nothing cancels it, so the second hold keeps the first timer's remaining time, not a new 72 hours. The in-process adapter overwrites the pending entry instead (`InProcessTimerScheduler.java:66`), so the two adapters would diverge. The one resume test (`AutomationEndToEndTest.java:231`) resumes once and never holds again. Open question 2.
5. Zero-action transitions. A timer that depends on actions would be lost (section 3). The test in section 6 covers it.
6. Dead-lettered rows (DR-23). After three failed dispatches a row is dead-lettered. Its timer and its actions then never start, and nothing retries it. A stamp mismatch counts toward the three (DR-23, line 158).
7. Stale B2 wording (section 1). The timer path must follow one wording.

## 8. OPEN QUESTIONS FOR THE NAVIGATOR

These are decisions the repo cannot settle. The plan does not decide them. Sections 3 to 7 refer to them by number.

1. Cancellation after P3: is it accepted that leaving a timer state no longer cancels the timer, with the staleness check as the only guard (spec row P3)? Or should the row's `fromState` drive a cancel, at the cost of a second behaviour lookup?
2. Re-hold on Temporal (section 7, risk 4): keep `timerKey` as the workflow id, use a key per row, or accept that the adapters diverge? Should a hold, resume, hold test be written in P3?
3. Crossing the adapter boundary: O-2 puts the lookup in core, but the adapters cannot depend on core (`noAdapterDependsOnCore`). Which shape carries a timer to the adapter: a new `TriggerEvaluator` method, a new `AutomationPort` method, or the adapter reading the behaviour through the ontology artifact?
4. Temporal shape: does `TimerWorkflow.run` take the `TransitionFired` row (spec row P3), with the lookup in an activity as `ActionActivities.applyTransitionAction` does, or with the resolved timer passed in? Does `TriggerActivities.onTimerElapsed` keep its `OutboxEntry` input?
5. Version checked by the lookup: `SchemaAndBehaviour.read` (`{main}/core/ontology/SchemaAndBehaviour.java:42-65`) already requires schema and behaviour to agree, and the adapters check the automation against the row's stamp. Must the stamp also match the behaviour the lookup reads? Which half does route 1 compare?
6. B2 wording: does P3 follow "fail" (spec lines 366-367; resume plan line 159) or "three failed dispatches, then dead-letter" (DR-23)? Should the spec's B2 item be corrected before the detailed P3 plan?
7. Migration number: P2d takes V8, and the held index branch is to renumber to V9 (resume plan line 334). Which number does P3 take, and does the held branch move?
8. Dead-lettered timer rows: should the P3 migration stamp them, as the V8 statement does, or leave them for O-3 and the removing steps to resolve (DR-23, lines 156-157)?
9. O-3 and O-4 timing: the answers of 2026-11-14 predate P2d. Does each removing step need a fresh count at its deploy (this plan's reading of spec line 332 and step 3), or does the earlier answer cover P3?
10. Zero-action and failing-action cases: confirm that a timer is scheduled whether or not the transition has actions, and that a failing action does not block the timer. The plan assumes both.
11. Temporal versioning: change `TimerWorkflowImpl.run` in place, which needs drain or terminate (spec step 4), or add `Workflow.getVersion`, as P5a may (spec line 90)?
12. Where `timerKey` lives: core uses it (`DefaultTransitionService.java:430-438`), and so do both adapters. Move it to `DefaultTriggerEvaluator`, or to `spi/outbox` beside the kinds, so that one definition serves all?
13. Kind-set test: the spec (line 208) describes an `OutboxEntryTest` assertion that does not exist at P2d. Does P3 add it, expecting five constants?
14. DR entry: does P3 record a DR for removing the timer kinds and ending cancellation, as DR-21 did for `ActionRequest` (O-16, spec line 362)? Under which number?
15. In-process growth: pending timers have no bound and no cancel. Accept that for the test adapter, or add a bound?
16. Dev-stack adapter: the repo's `application.yaml` does not set `sequeless.automation.adapter`, and no compose file does. Which adapter does the dev stack run, and so which adapter must O-4 cover?

## 9. Merge order

1. P2d (PR #168) merges after the owner's dev-stack check with the old version (resume plan line 343).
2. P2e merges (resume plan section 17 list; `ActionWorkflow` and `KIND_ACTION_REQUEST` go).
3. P3 merges. Before any P3 code: a read-only plan review (resume plan line 311) and answers to open questions 1 to 6. At deploy: the O-3 and O-4 checks on the stopped dev stack, then the new version runs V9 at startup (spec steps 3 to 5).
4. P4 follows P3 (resume plan line 120). P4 and P5b are out of scope for this document.
