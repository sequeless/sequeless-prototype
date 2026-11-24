# Spec 13, P4: signals become Store Commands (plan)

Status: plan only, no code. Made against `origin/main` `5d3a0dc` (#172), in the worktree `p4-plan` (branch `docs/spec-13-p4-plan`). Parent: `docs/extraction/spec-13-prototype-stage4-prepare.md` (the P4 row is at line 93; "spec" below means that file). "Resume plan" is `docs/extraction/resume-plan-spec13.md`. Tracker: #41. The P2d branch is `origin/spec-13-p2d-flip-impl` at `1a25fd6` (PR #168, open, not merged).

Conventions: as in the P3 plan. `{main}` and `{test}` stand for `src/main/java/org/sequeless` and `src/test/java/org/sequeless` inside the module named first. Line numbers are main `5d3a0dc`. P4 lands after P2d, P2e and P3, so every line moves; the P4 PR re-cites them. `OQn` means open question n in section 8. A question the spec or the resume plan already settles is not in section 8; the place that settles it is cited where the question arises.

## 1. Scope and base

- P4 makes `TransitionService.signal` run the matching transition synchronously in one `ChangeSet`. It writes no `SignalReceived` row, `KIND_SIGNAL_RECEIVED` leaves the outbox, and `SignalEventWorkflow` is removed with its kind (spec row P4, line 93; also lines 172 and 334).
- Main state (resume plan sections 17 to 20): the P2 steps p2-1, 2a, 2b and 2c are merged (#158, #159, #160, #162). P2d (PR #168) is open, held on the owner's dev-stack check. The P2e and P3 plans are merged (#170, #171); their code is not. Order: P2, P3, P4, then P5b (resume section 15).
- P4 code waits for the P3 merge (section 9). P4 planning may run now (resume section 20, line 371).
- P4's changes apply on top of P2d and P2e, not on main. Section 4, item 27 depends on P2d's edit to the settling test.
- B2 does not hold P4 (spec row P4, line 93; resume line 57). P4 reads the active `SchemaAndBehaviour` at request time (`DefaultTransitionService.java:272`) and runs the automation-version check that `commitTransition` already runs (326-331). It reads no outbox stamp. Spec line 371 settles this (P2a, P4 and P5a do not read the stamp; P4 commits through the agreement check, which the spec cites as lines 325 to 330), and spec line 93 and resume line 57 say the same. Resume line 159 settles B2 as route 1.
- Holds: O-3 and O-4. Both were answered 2026-11-14, before P2d, P2e and P3. The spec requires a fresh count at each removing step's deploy (spec line 332; step 3 at line 104), so P4 takes fresh counts at its own deploy. What a zero means on the dev stack is OQ16.
- Stale line numbers in the spec and the resume plan are listed in section 2 ("What moved").

## 2. Inventory

Main code:

- `sequeless-core/{main}/usecase/DefaultTransitionService.java`: `fire` 119-167 (machine lookup and guard checks 134-149; trigger-kind check 159-164; `commitTransition` call 166). `fireAutomated` 204-254 (its lookup 240-247; its guard check 232-237 returns empty when the guard fails). `signal` javadoc 256-263 (it describes routing to `onSignal`); method 265-301: `SchemaAndBehaviour.read` 272; `authorize(TRANSITION)` 276; object and type check 278-279; machine lookup 281-283 (`UnknownSignalException` if none); type-wide "declared" check 284-289; payload 291-297 (`ontologyVersion` put at 297); `SignalReceived` entry 298; `commit(ChangeSet(List.of(), [entry]))` 300. `commitTransition` javadoc 303-317, method 318; automation-version check 326-331.
- `sequeless-core/{main}/api/TransitionService.java`: `signal` javadoc 89-119: "durably" at 90-91; the type-wide rationale at 101-102; the `body` "for audit" text at 110-111; `@throws UnknownSignalException` at 117-118. Declaration 120.
- `sequeless-core/{main}/automation/DefaultTriggerEvaluator.java`: `onSignal` `@Override` 156, method 157-189. It reads `behaviourSource.behaviour` at 171 and loops over the transitions at 180-187. It calls `fireAutomated` (184) and returns (185) after the first transition whose from-state and signal name match. A state-less or missing object returns at 166-168. The no-match comment is at 188.
- `sequeless-spi/{main}/outbox/OutboxEntry.java`: class javadoc 21-25 (the signal text at 22-24); `KIND_SIGNAL_RECEIVED` javadoc 97-105, constant 106. Eight `KIND_` constants on main (46-106).
- `sequeless-spi/{main}/automation/TriggerEvaluator.java`: `onSignal` 82, javadoc 69-81 (it calls a non-matching signal a "silent no-op" at 73). `AutomationPort.java`: `@throws` list 37-42, signal kind at 41.
- `sequeless-spi/{main}/object/ChangeSet.java`: javadoc 12-14 ("a SignalReceived-only commit").
- `sequeless-app/{main}/automation/OutboxRelay.java`: class javadoc 20-49 (signal kind at 25; "never configures `sequeless.automation.adapter`" at 46-48); `DISPATCHABLE_KINDS` 55-63, eight entries, signal at 63 (comma at 62); comment 153; `buildScope` 180-183 reads the payload's `principalId` (181). `pollOnce` returns at once when no adapter is configured (121-123).
- `sequeless-app/{main}/rest/ObjectsController.java`: `signal` javadoc 478-489 (body "for audit" at 482-484; the 202 paragraph at 485-488); `@Operation` summary 490 ("does not itself fire any transition"); `@ApiResponse` 202 at 491-493 (its description at 493 says "fires asynchronously"); 400 at 494-497; 404 at 498-501; `@PostMapping` 502; method 503-511. No 409 annotation.
- `sequeless-app/{main}/rest/ApiExceptionAdvice.java`: `StaleObjectException` to 409 `stale-object` (159-); `TransitionNotAvailableException` to 409 `transition-not-available` (204-211); `UnknownSignalException` to 400 (219-). Its `@ExceptionHandler` methods span 75-219. This is the only `@RestControllerAdvice` in main code (line 67). No handler maps `IllegalStateException` or a generic `Exception` (section 7, risk 3).
- `sequeless-adapter-automation-inprocess/{main}/.../InProcessAutomationPort.java`: javadoc 68-71 (`SignalReceived` to `onSignal` at 69-70); `dispatch` switch 135-143 (signal case 141; default 142); line 100 ("or signal entry"). `InProcessAutomationAutoConfiguration.java:35` is `@ConditionalOnProperty(..., havingValue = "inprocess")` with no `matchIfMissing`.
- `sequeless-adapter-automation-temporal/{main}/.../TemporalAutomationPort.java`: javadoc 22-26 (signal at 24-25); switch 84-94 (signal case 90; default 93); `startSignalEventWorkflow` 117-125. `SignalEventWorkflow.java` (25 lines); `SignalEventWorkflowImpl.java` (21 lines; reads `ActionWorkflowImpl.ACTIVITY_OPTIONS` at line 13). `ActionWorkflowImpl.java:37` holds `ACTIVITY_OPTIONS`. P2e moves that holder (P2e plan line 10; item 3 at line 83). Where it goes is P2e's question Q2 (P2e plan line 177), so this plan does not decide it. `TriggerActivities.java`: javadoc 11-12 (names `SignalEventWorkflowImpl` at 12); `onSignal` 34-36. `TriggerActivitiesImpl.java`: javadoc 25 (`{@link #onSignal}`); `onSignal` 51-53. `TimerWorkflow.java:16` (`{@link SignalEventWorkflow}`). `TransitionActionWorkflowImpl.java` javadoc 16-18 (names `SignalEventWorkflowImpl` at 17). `TemporalAutomationAutoConfiguration.java:48` (`havingValue = "temporal"`, no `matchIfMissing`); the `ACTIVITY_OPTIONS` writer at 141; the registration of `SignalEventWorkflowImpl` at 150.
- `sequeless-spi-testkit/{main}/automation/RecordingTriggerEvaluator.java`: `METHOD_ON_SIGNAL` 38-39; javadoc 45 and 78; `onSignal` 64-67. `AutomationContract.java`: javadoc 74; `dispatchRoutesSignalReceivedToOnSignal` 241-249; `signalReceivedEntry` 331-344.
- `sequeless-adapter-persistence-postgres/{main}/.../PostgresOutboxPort.java`: claim `WHERE kind IN (:kinds) AND processed_at IS NULL AND dead_lettered_at IS NULL` at 84. Unchanged by P4.
- Migrations on main. Command, `db/migration/sequeless/postgres/`: V1, V3, V4, V5 (P2a), V6 (`search_vector_command`), V7 (`outbox_dead_letter`). Query, `db/migration/sequeless/postgres-query/`: V1, V2 (P7). The Command V2 is retired. The next free Command version on main is V8, which P2d takes on #168.

Tests:

- `DefaultTransitionServiceTest.java`: signal tests at 749 (commits a `SignalReceived` row; kind asserted at 763 and 784), 773 (a signal whose transition does not depart the current state "succeeds"; asserted at 783-784), 788 (`ObjectNotFound`), 799 (`UnknownSignal`), 816 (no state machine), 837 (authorization), 848 (null arguments). `fire` non-user trigger case at 865.
- `DefaultTriggerEvaluatorTest.java`: `ARCHIVE_SIGNAL` 119-126, referenced only at 132 (the `PROJECT_LIFECYCLE` transition list); helper `signalReceivedEvent` 217-223; the `onSignal` section 356-405 with tests at 359, 371, 384 and 398; the wiring helper `evaluator` at 409.
- `ChangeSetTest.java:58` uses `KIND_SIGNAL_RECEIVED` as a sample kind. Compile-breaking when the constant goes.
- `OutboxEntryTest.java`: 11 `KIND_` references (lines 33-123) and no kind-set assertion, on main and on the P2d branch (grep on both). The spec's claim that a kind-set test exists (spec line 208) is therefore not true today. Section 4, item 18.
- `TemporalAutomationPortContractTest.java`: javadoc 52-57; `SignalEventWorkflowImpl.class` registration 139; dispatch wrapper case 174; `onSignal` delegate 449-450.
- `sequeless-adapter-automation-inprocess/{test}`: no signal reference (grep). The in-process port runs the shared `AutomationContract`.
- `TransitionsEndToEndTest.java`: javadoc 204-214 (it says the test checks the REST contract only); `reopenSignalOnClosedProjectIsAccepted` 216 (asserts 202 only); `unknownSignalNameIs400` 258.
- `AutomationEndToEndTest.java`: `reopenSignalMovesAClosedProjectBackToActive` 256-276; the POST at 265-273 expects 202 (at 271); `awaitState(..., "Active", 6 s)` is at 275 (272 is `.expectBody()`). It is the only app-level signal-driven automation test: the other signal POSTs (`TransitionsEndToEndTest` 241 and 263) are REST-level.
- `ApiExceptionAdviceTest.java`: 409 test 70-80 (asserts at 75); 400 test 82-95. No change.
- `PostgresOutboxV5MigrationIT.java`: literal kinds (45, 72); already pinned to `migrateTo("4")` (65) and `migrateTo("5")` (75, 89), so no change.
- `PostgresOutboxClaimIT.java`: `CLAIMABLE_KINDS` 38-46 includes `"SignalReceived"` (46); it passes its own kind set to `claimNext`. `PostgresOutboxPortIT.java` and `OutboxRelayTest.java`: no signal reference (grep).
- `PostgresMigrationsSettlingIT.java` (main): javadoc 21-31; starting states 3328993 and fbb82c5 (constants 38-39). On the P2d branch the same file also hard-codes the Command history of the final set in three places: `FINAL_COMMAND_HISTORY` (44-45) and the two `containsExactly` lists, whose last entries are on lines 103 and 133. P2d added `8 SUCCESS` to all three. Section 4, item 27.
- `PostgresMigrationFrozenSetsTest.java`: frozen `post-p7/command` (V1, V3 to V6).

UI:

- `sequeless-ui/projects/automation/src/lib/signal-catalogue/signals-panel.ts` (the live panel): header comment 20-24 ("handled asynchronously (202)" at 22); status text 61 ("dispatched — takes effect asynchronously"). The error handler at 126-131 sends every error except `unknown-signal` to `this.error` (131), which the problem banner (`sq-problem-banner`, line 64) shows. `sequeless-ui/projects/sequeless-shell/src/app/app.routes.ts` 12-16 binds `provideAutomationSignalsPanel()` on the objects routes (line 16). The spec row P4 names only the other panel (line 93).
- `sequeless-ui/projects/objects/src/lib/signals/object-signals.ts`: header 18; `OBJECTS_SIGNAL_REFETCH_DELAY_MS` factory 1500 at line 21 (kept, R1); status text 47; error branch 49-54 (banner at 53); default token factory 28-31 (used only when no automation panel is provided).
- `object-signals.spec.ts`: header 4; "on 202 reports ... dispatched" case 110-118 (the word check at 116); failure case 128-150.
- `signals-panel.spec.ts`: header 8-9; full-text assertion 111; refetch-after-delay case 128-136.
- `sequeless-ui/e2e/automation-signals.spec.ts`: header 3-5; line 7 says "Verified against a live run"; `toContainText('dispatched')` 31; `toBeVisible` 32; `toBeHidden({ timeout: 5000 })` 34.
- `sequeless-ui/projects/core-api/src/lib/models/index.ts`: no change (no wire field changes).

Docs:

- `docs/architecture/automation.md`: 60-61 (the `KIND_SIGNAL_RECEIVED` bullet); 446 (names a signal as an outbox event that may be stale); 495 (`onSignal` participant); 522-523; section 12 (heading 623), whose signal paragraphs are 625-642, with the type-wide text at 628-633 and "recorded verbatim for audit" at 636.
- `docs/architecture/01-overview.md:105` (the `AutomationPort` row: "external signals from the outbox").
- `docs/architecture/02-decision-records.md`: the last DR on main is DR-23 (heading at line 145). DR-24 is on the P2d branch.

What moved (stale line numbers):

- Spec line 50: "`signal`, line 297" is the `ontologyVersion` payload put. The `SignalReceived` entry is 298, and the method is 265-301. The same line gives the version check as 326-329; it is 326-331 in `commitTransition`.
- Spec line 56: `DISPATCHABLE_KINDS` "lists 7 kinds (lines 46–53)". Main has eight kinds at 55-63, because P2a already added `TransitionFired`.
- Spec line 61 gives the Temporal switch as lines 74-81; main has it at 84-94 (signal case 90). Spec line 171 gives the in-process `TransitionFired` no-op as lines 119-129; the main dispatch switch is at 135-143 (signal case 141).
- The agreement check is at 326-331 in `DefaultTransitionService`. Spec row P2a (line 89) says 326; spec section 12 (lines 367 and 371) and resume line 57 say "325 to 330". The `signal` path does not reach the check on main: it runs 265-301 and never calls `commitTransition`.
- Spec line 127 (final version set): V7 is `outbox_dead_letter` on main, so the spec's "V7 (P2)" and the sequence after it are stale. P4's number follows section 5.
- Spec line 137 places the C7 signal rejection under "Decided inside the steps, not open" (heading at line 130). Spec line 93 calls C7 a "driver decision", and line 222 gives 409 for a rejected signal. R4 (line 461, under the heading at line 456) lists the same behaviour for owner review, and line 326 says DECIDED items may be changed by the owner later. These do not conflict, so C7 is settled (section 3, step 3).
- Spec row P4 (line 93) names only `object-signals.ts` line 47 for the UI text. The live panel is `signals-panel.ts` (section 2).
- Resume section 17, items 1 to 8 (lines 298 to 305), are the P2e list, not P4. Only item 7 (`automation.md`) touches a file P4 also edits. The corrections at lines 283 to 287 concern P2 and the B2 route, not P4.
- Confirmed unchanged on main: `DefaultTriggerEvaluator.onSignal` at 157; `ApiExceptionAdvice` 204; `TransitionsEndToEndTest` 216; `object-signals.ts` 21 and 47; `DefaultTransitionService` 272.

## 3. The new data path

Today (main). `POST .../signals/{name}` goes to `ObjectsController.signal` (503-511), then `DefaultTransitionService.signal` (265-301). That reads the halves (272), authorizes `TRANSITION` (276), checks the object's type (278-279), finds the machine (281-283), checks that some transition declares the name (284-289), and commits one `SignalReceived` row with `objectId`, `tenantId`, `principalId`, `signalName`, `body` and `ontologyVersion` (291-300). The reply is 202. Later the relay claims the row (`DISPATCHABLE_KINDS`, 63), builds the scope from `principalId` (180-183), and the adapter calls `TriggerEvaluator.onSignal` (in-process 141; Temporal `TemporalAutomationPort` 90, then `SignalEventWorkflow` and `TriggerActivitiesImpl.onSignal`, 51-53). `onSignal` finds the first transition from the current state with that name (180-187) and calls `fireAutomated`, which checks the guard (232-237) and then runs `commitTransition`.

After P4 (spec row P4). `TransitionService.signal` does this in one request:

1. Reads the halves, resolves the type, authorizes, reads the object, checks its type: unchanged (272-279).
2. A name no transition declares anywhere on the resolved machine, or no machine: `UnknownSignalException` (400), unchanged (281-289). Spec row P4 keeps the 400 for an unknown name, so the check stays type-wide (settled; OQ5(i) is closed). The javadoc rationale at `TransitionService` 101-102 no longer fits the synchronous path and needs rewording (section 4, item 15).
3. Finds the transition departing the object's current state with an `ExternalSignalTrigger` of that name (new). None: `TransitionNotAvailableException` (409). This is C7 (spec line 93, "driver decision"; under "Decided", lines 130 and 137), so it is settled (OQ4 is closed). A state-less object is OQ5(ii).
4. Evaluates the transition's guard through `StateMachineInterpreter.availableTransitions`, as `fire` does (134-149). Guard false: `TransitionNotAvailableException` with its reason (409).
5. Calls `commitTransition(scope, halves, resolved, existing, transition, existing.version(), now())` (318): one `ChangeSet` with the `Update` and one `TransitionFired` row. Actions and timers come from the row in P2 and P3, exactly as for a user fire. Returns 202.

What changes beyond the status codes:

- Guard timing moves from the relay to the request.
- `commitTransition` throws `IllegalStateException` when the automation half is at another version (326-331). Today's signal path never reaches that check (OQ8).
- `expectedVersion` is `existing.version()`. A concurrent write gives `StaleObjectException`, mapped to 409 `stale-object` (`ApiExceptionAdvice` 159). The signal request carries no version (OQ6).
- The body is recorded today only in the `SignalReceived` payload (296), which P4 removes. Nothing reads it: `DefaultTriggerEvaluator` reads only `objectId` and `signalName` (162-163) (OQ7).
- The principal does not change. Today the relay builds the scope from the payload's `principalId` (`OutboxRelay` 180-183). After P4 the commit uses the request's scope, which carries the same principal.
- Today the lookup at 180-187 takes the first match, and a failing guard on it is a silent no-op (the guard is checked at `DefaultTransitionService` 236-237). After P4 the lookup moves into the signal path (spec line 93), and a failing guard is rejected with 409 (C7, spec line 93; step 4 of the list below). Only the case of several matching transitions is open (OQ9).
- P4 reads the halves it already holds and no outbox stamp, so the B2 route does not affect the read (spec row P4; resume line 57). The automation-version check is settled in section 1 (spec lines 371 and 93; resume line 159); how a mismatch is handled is OQ8.

## 4. Change list

Lines are main `5d3a0dc`. Every step runs on top of P3, so the PR re-cites them. P2d and P2e come before it (section 9). Items 1 to 13 remove the signal kind and its consumers; 14 to 16 are the core and REST change; 16a to 16e are javadoc that goes stale in P4 (letters, so no other number moves); 17 to 24 are tests; 25 to 27 are persistence; 28 to 31 are UI; 32 to 34 are docs.

Delete or remove:

1. `OutboxEntry.java`: `KIND_SIGNAL_RECEIVED` (106) and its javadoc (97-105). Reword the class javadoc (22-24).
2. `DefaultTriggerEvaluator.java`: `onSignal` (156-189).
3. `TriggerEvaluator.java` (SPI): `onSignal` (69-82), with its javadoc.
4. `TriggerActivities.java` (javadoc 11-12; `onSignal` 34-36) and `TriggerActivitiesImpl.java` (`onSignal` 51-53; javadoc 25, `{@link #onSignal}`).
5. `InProcessAutomationPort.java`: signal case (141); javadoc 69-70; the `triggerEvaluator` parameter text at 100 ("or signal entry").
6. `TemporalAutomationPort.java`: signal case (90), `startSignalEventWorkflow` (117-125), javadoc 24-25.
7. Temporal workflow: `SignalEventWorkflow.java`, `SignalEventWorkflowImpl.java`, and the registration at `TemporalAutomationAutoConfiguration.java:150`. Spec lines 93, 172 and 334 remove `SignalEventWorkflow` in P4, so it goes in this step. Reword `TransitionActionWorkflowImpl.java` 16-18 and `TimerWorkflow.java:16`. This step needs P2e's holder move first (section 2; P2e plan line 10).
8. `OutboxRelay.java`: `KIND_SIGNAL_RECEIVED` (63, with the comma at 62), the signal text in the class javadoc (25), comment 153.
9. `AutomationPort.java`: the signal entry in the `@throws` list (41).
10. `ChangeSet.java` javadoc 12-14: the "SignalReceived-only commit" example.
11. `RecordingTriggerEvaluator.java`: `METHOD_ON_SIGNAL` (38-39), javadoc 45 and 78, `onSignal` (64-67).
12. `AutomationContract.java`: test 241-249 and builder 331-344; javadoc 74.
13. `TemporalAutomationPortContractTest.java`: registration 139, wrapper case 174, delegate 449-450; javadoc 52-57.

Edit (core and REST):

14. `DefaultTransitionService.java` `signal` (265-301): keep the declared check (284-289), which is the 400 of spec row P4. After it, add the current-state lookup and the guard (section 3, steps 3 and 4). Replace the payload and entry (291-300) with the `commitTransition` call. Rewrite the javadoc at 256-263, which describes routing to `onSignal`, and note in `commitTransition`'s javadoc (303-317) that `signal` is a caller. Check the imports `UUID`, `HashMap` and `OutboxEntry` after the edit.
15. `TransitionService.java`: `signal` javadoc 89-119. Remove "durably" (90-91). Reword the type-wide rationale (101-102). Reword the `body` text "for audit" (110-111) per OQ7. Add `@throws TransitionNotAvailableException` (409), and `StaleObjectException` if OQ6 keeps it.
16. `ObjectsController.java`: javadoc 478-489 (body "for audit" at 482-484; the 202 paragraph at 485-488 names `SignalReceived` and says the transition fires later); the `@Operation` summary (490, "does not itself fire any transition", which becomes false); the 202 description (493, "fires asynchronously"); add a 409 `@ApiResponse` after 497. The 202 status and the method body (503-511) do not change.

Javadoc that goes stale in P4 (found after the first draft; each one names the signal path or the signal kind):

16a. `sequeless-app/{main}/automation/package-info.java` lines 7-8, on the P2d branch only (main has no signal wording here, and P4 builds on P2d, section 1). Remove the signal-kind clause, " and the signal kind ({@code SignalReceived})", and reword the join so the list ends at the timer kinds. P3 item 19 rewords line 7 for the timer kinds, so the two edits go in one change.
16b. `sequeless-adapter-automation-inprocess/{main}/.../InProcessAutomationAutoConfiguration.java` lines 107-108: "an object-lifecycle, timer, or signal entry is routed to" becomes "an object-lifecycle or timer entry is routed to". P3 item 19 cites line 109 in the same javadoc.
16c. `sequeless-app/{main}/rest/ObjectsController.java` lines 388-389: "only `TransitionService#fireAutomated` can fire those, and it is never invoked from a client request" is false for EXTERNAL_SIGNAL after P4, because the signal request fires those transitions through `commitTransition` (section 3, step 5). The second half still holds, since the signal request does not call `fireAutomated`. Reword the sentence so that it names ON_CHANGE and TIMER, and says that the signal request fires EXTERNAL_SIGNAL transitions.
16d. `sequeless-core/{main}/api/TransitionService.java` line 67: remove "or an external signal" from the `fireAutomated` javadoc (the list becomes a change to another object or an elapsed timer).
16e. `sequeless-core/{main}/usecase/DefaultTransitionService.java` line 172: remove "or an external signal" from the `fireAutomated` javadoc. After item 2, no code calls `fireAutomated` for a signal (the other callers in `DefaultTriggerEvaluator` are lines 153 and 221, for TIMER and ON_CHANGE).

Tests:

17. `DefaultTransitionServiceTest.java`: rewrite the test at 749 (no `SignalReceived` row; commits `Update` and `TransitionFired`; the kind assertions at 763 change). Rewrite the test at 773 (the "succeeds" case) as a `TransitionNotAvailableException` case (C7). Keep 788, 799, 816, 837 and 848, checking their messages.
18. `OutboxEntryTest.java`: add the kind-set assertion. None exists on main or on the P2d branch (grep), so the spec's line 208 describes a test that is not there. P4 sets the count to four constants (`KIND_OBJECT_CREATED`, `KIND_OBJECT_UPDATED`, `KIND_OBJECT_DELETED` and `KIND_TRANSITION_FIRED`), as spec line 208 and row P4 (line 93) say. That count holds only if P2e deletes `KIND_ACTION_REQUEST`. P2e Q1 (P2e plan section 7, item 1, line 176) asks whether it is deleted or kept `@Deprecated` (`OutboxEntry.java:73-74` on the P2d branch). If it is kept, the count is five. Whether P3 or P4 adds the test depends on P3 OQ13 (P3 plan section 8, line 169), which asks whether P3 adds it, expecting five constants. Whichever step adds it, P4 sets the final count: four, or five if Q1 keeps the constant.
19. `DefaultTriggerEvaluatorTest.java`: remove the `onSignal` section (356-405), the helper `signalReceivedEvent` (217-223), and `ARCHIVE_SIGNAL` (119-126) with its entry in the `PROJECT_LIFECYCLE` list (132). `ARCHIVE_SIGNAL` is used nowhere else (grep), but check that no other test counts the machine's transitions.
20. `ChangeSetTest.java` line 58: use another kind (compile-breaking).
21. `AutomationEndToEndTest.java` 256-276: the 202 assertion (271) stays. The `awaitState` at 275 becomes a direct assertion or stays (OQ12).
22. `TransitionsEndToEndTest.java`: rewrite the javadoc at 204-214, which describes a 202-only check. `reopenSignalOnClosedProjectIsAccepted` (216) adds a state assertion right after the 202 (spec row P4). Add a case: `reopen` on an `Active` Project returns 409 `transition-not-available` with `transitionName` (C7). `unknownSignalNameIs400` (258) stays.
23. `PostgresOutboxClaimIT.java` (optional): drop `"SignalReceived"` from `CLAIMABLE_KINDS` (38-46) to match the relay set. The test passes either way.
24. The current-state lookup (section 3, step 3) may live beside `fireAutomated`'s lookup (240-247). The shape is a code decision, not a plan decision.

No change: `ApiExceptionAdvice` (204-211 already maps `TransitionNotAvailableException` to 409), `ApiExceptionAdviceTest` (70-80, 82-95), `PostgresOutboxV5MigrationIT`, `PostgresOutboxPortIT`, `OutboxRelayTest`.

Persistence:

25. New Command migration (number in section 5): `UPDATE sq_outbox SET processed_at = now() WHERE kind = 'SignalReceived' AND processed_at IS NULL;`, after the drain (B1). The dead-letter rule is OQ11. The statement follows V5's first statement (V5 lines 8-10).
26. New `PostgresOutboxV<n>MigrationIT.java`, modelled on `PostgresOutboxV5MigrationIT.java`: migrates to the previous version (`migrateTo`), seeds `SignalReceived` rows and other kinds, and checks that only the signal rows are stamped.
27. `PostgresMigrationsSettlingIT.java`: add one entry for P4's version to each of the three Command lists: `FINAL_COMMAND_HISTORY` (44-45 on the P2d branch) and the two `containsExactly` lists (last entries on lines 103 and 133 on that branch). Each list then ends with P4's `<n> SUCCESS` after P2d's `8 SUCCESS`. This applies on top of P2d and P2e, because P2d (PR #168) is not merged. P3's migration needs the same edit for its own version. The P3 plan does not mention this test (grep finds no match in `docs/extraction/spec-13-p3-plan.md`), so that gap is recorded here, not decided.

UI:

28. `signals-panel.ts` (the live panel): header 22 ("handled asynchronously (202)") and status text 61. The new wording is OQ17. A 409 reaches the problem banner through the error branch at 126-131 (line 131 sets `this.error`; the banner is at 64).
29. `object-signals.ts` (default panel): header 18 and status text 47 change to the wording spec row P4 gives: "the transition is applied when the request is accepted" (spec line 93). Keep the 1500 ms refetch (21), which is R1. A 409 reaches the banner through the else branch (52-53).
30. `signals-panel.spec.ts` (8-9, 111) and `object-signals.spec.ts` (header 4; the word check at 116): update them to the wording each panel uses. The refetch cases (`signals-panel.spec.ts` 128-136) need no change for P4.
31. `automation-signals.spec.ts`: header 3-5; line 31 (`toContainText('dispatched')`) follows the wording. Add a rejected-signal case: the page loads, the state changes by API, then `reopen` is clicked and the 409 banner is asserted. The banner renders `<div role="alert" class="sq-problem-banner">` (`sequeless-ui/projects/ui-kit/src/lib/problem-banner/problem-banner.ts` line 16), so the 409 case can assert on that element.

Docs:

32. `docs/architecture/automation.md`: 60-61, 446, 495, 522-523, and the signal paragraphs 625-642 (the type-wide text at 628-633; the body "recorded verbatim for audit" at 636). Rewrite them for a synchronous signal.
33. `docs/architecture/01-overview.md:105`: the `AutomationPort` row ("external signals from the outbox").
34. A DR for the signal change, only if OQ14 decides to add one. Spec section 10 adds none ("No ADR is added here", spec line 294).

Add: the migration (25), its IT (26) and the settling-test edit (27). No new module, port or dependency. The `ArchitectureTest` rules are unaffected in P4 (the ingress rule is P5b).

## 5. Migration

- P4 adds one Command migration. Main ends at V7 (Command) and V2 (Query). The number is taken when P4 merges, in merge order (spec line 127; resume line 261). P2 takes V8 (#168). The held index branch `spec-13-dead-letter-index-v8` renumbers to V9 when the owner picks its window (owner decision (ix), `docs/extraction/remaining-work.md` line 41; resume line 261). If the index window merges first, resume line 261 says a new Navigator decision is needed before that merge. P3's number is P3 OQ7 (P3 plan line 163). So P4's version is the next free one after P2, P3 and the index branch, and its exact value waits on P3 OQ7 and decision (ix). It is written as V<n> until then.
- Content: V5's shape, for `SignalReceived` only. The relay must have drained the kind first (B1; spec line 98). A row still unprocessed at the migration is a signal that returned 202 whose transition never ran. Stamping it loses that transition with no record. The O-3 counts and the old version's stop gate the deploy (spec steps 3 and 4, lines 104 and 105).
- Dead-lettered rows: V7's header (lines 1-9) says they keep `processed_at` NULL, and the claim skips them (`PostgresOutboxPort` 84). Nothing claims `SignalReceived` after P4, so these rows keep O-3 above zero (OQ11).
- Timing: the migration runs at startup of the new version, after the old version has stopped and the counts have passed (spec steps 3 to 5, lines 104 to 106).
- Settling and frozen sets: a new migration file adds no frozen copy, so `PostgresMigrationFrozenSetsTest` is unaffected (its frozen copy is `post-p7/command`). The settling test does change: its three Command lists each need one more entry (section 4, item 27). Spec line 128 asks each step that adds a Command migration to save its merged set as a frozen copy and add a pair. Main has no such chain (`PostgresMigrationsSettlingIT` has only the two starting states), and the P3 plan's section 5 (line 117) says a new migration adds no frozen copy. That conflicts with spec line 128 (OQ15).
- Index: unchanged. The claim index still names `SignalReceived` (V5 line 25); narrowing it is optional (spec 5.3, line 186).

## 6. Proof and tests

Gate, exactly (JDK 25 is required; the default `java` is 21):

    source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify

Proof, by layer:

- Core, `DefaultTransitionServiceTest`: a signal commits one `Update` and one `TransitionFired` and no `SignalReceived` (spec row P4). Each old `onSignal` case gets a signal-side counterpart: fires (`DefaultTriggerEvaluatorTest` 359) becomes "the signal commits the transition"; unknown name (371) becomes `UnknownSignalException`, not a no-op (settled, section 3, step 2); declared but not from the current state (384) becomes `TransitionNotAvailableException` (C7, settled, section 3, step 3); object gone (398) is covered by 788. New cases: guard false gives 409 with the reason; a version race gives `StaleObjectException` if OQ6 keeps it; an automation-version mismatch gives `IllegalStateException` (OQ8).
- Core, `DefaultTriggerEvaluatorTest`: no `onSignal` case remains. The `onChange` and `onTimerElapsed` cases are unchanged.
- Contract, both adapters (`AutomationContract`): the signal case (241-249) goes. If the contract pins the removal, a `SignalReceived` row dispatched to either adapter throws `IllegalArgumentException` (the default branch of both switches: in-process 142, Temporal 93).
- REST, `TransitionsEndToEndTest`: `reopenSignalOnClosedProjectIsAccepted` asserts 202 and `state` `Active` immediately after the call (spec row P4). The new case: `reopen` on an `Active` Project gives 409 `transition-not-available`. `unknownSignalNameIs400` (258) is unchanged.
- App, `AutomationEndToEndTest.reopenSignalMovesAClosedProjectBackToActive`: keeps its 202 assertion (271). After P4 it no longer exercises the automation path (OQ12).
- Postgres: the new migration IT stamps `SignalReceived` rows, leaves the other kinds, and leaves dead-lettered rows as OQ11 decides. `PostgresOutboxV5MigrationIT` is unchanged. `PostgresMigrationsSettlingIT` passes with one more entry in each of its three lists (section 4, item 27).
- Kind set (`OutboxEntryTest`): four constants, or five if P2e keeps `KIND_ACTION_REQUEST` (section 4, item 18).
- UI: `ng test` (objects and automation projects) and `playwright test` on `automation-signals.spec.ts` (spec line 251 lists both as P4 checks). The rejected-signal case asserts the 409 banner.
- Dev stack, before the new version starts: O-3 shows zero for every claimed kind after the old version stops, and O-4 lists no running `SignalEventWorkflow` (spec steps 3 and 4, lines 104 and 105; O-3 is at spec line 332 and O-4 at line 334). Record both results in the PR body. OQ16 says what those counts cover.

## 7. Risks

1. REST behaviour change: a signal with no valid transition returns 409 instead of 202 (C7; spec line 93). The UI shows the problem banner: `signals-panel.ts` 126-131 and 64, and `object-signals.ts` 52-53.
2. The guard and the object are now read at request time. A race with another write gives 409 `stale-object`.
3. An automation-version mismatch during an ontology change makes the signal throw `IllegalStateException`. Nothing maps it: `git grep -n 'RestControllerAdvice\|IllegalStateException' origin/main -- '*.java'`, filtered to `/src/main/`, shows that `ApiExceptionAdvice` (line 67) is the only `@RestControllerAdvice` and that every `IllegalStateException` hit is a throw or a javadoc line, none mapped. No `server.error` setting, `ErrorController` or `ErrorAttributes` exists in main Java or YAML. So the response is Spring Boot's default for an unhandled exception, HTTP 500. The choice of what to do is OQ8.
4. The signal body stops being recorded unless OQ7 moves it.
5. An undrained `SignalReceived` row loses its transition with no record (B1).
6. Dead-lettered `SignalReceived` rows (DR-23, V7) keep O-3 above zero, and nothing claims them after P4 (OQ11).
7. Running `SignalEventWorkflow` executions are stranded when the type is removed unless they drain (O-4; spec line 334). The removal itself is settled (section 4, item 7).
8. Mixed versions: an old relay that still claims `SignalReceived` and dispatches to a new adapter reaches the `default` branch and throws `IllegalArgumentException`. The stop order in spec section 3 (lines 100 to 108) prevents overlap.
9. Coverage shrinks. After P4 no test drives a signal through the relay. A signal's actions and timers are covered only by the `TransitionFired` tests of P2 and P3 (OQ12).
10. If only `object-signals.ts` changes, the live panel's text and comment stay stale (section 2; section 4, item 28).
11. The dev stack's adapter is unknown, and the repo does not set one (OQ16). With no adapter, `OutboxRelay.pollOnce` returns at 121-123 and claims nothing, so no signal would be applied and O-3 would not mean a backlog.

## 8. OPEN QUESTIONS FOR THE NAVIGATOR

These are decisions the repo cannot settle, or that the spec leaves to the owner. The plan does not decide them. Sections 3 to 7 refer to them by number. Settled questions are not listed, and the place that settles each one is cited where it arises: OQ1 (section 4, item 7; spec lines 93, 172 and 334), OQ2 (section 5; spec line 127; resume line 261), OQ3 (section 1; spec line 332 and step 3 at line 104), OQ4 (section 3, step 3; spec lines 93, 130 and 137, with R4 at line 461 for owner review), OQ5(i) (section 3, step 2; spec row P4, line 93), and the object-signals.ts wording of OQ13 (section 4, item 29; spec line 93). Numbers are kept so the references stay valid.

**OQ5.** State-less object (OQ5(ii)): today the signal returns 202, and `onSignal` returns at 166-168 for a state-less or missing object. The `fire` path already answers a state-less object with 409: `StateMachineInterpreter.java` lines 98-100 return an empty list when the state is empty, and `DefaultTransitionService.java` lines 141-144 then throw `TransitionNotAvailableException` (the `match.isEmpty()` check is at 143, its throw at 144). C7's "no transition from the current state" reads the same way (section 3, step 3). Options: (a) 409; (b) 202 as a no-op; (c) 400.
**OQ6.** Version race: options: (a) keep `existing.version()` and map `StaleObjectException` to 409 `stale-object` (current mapping, `ApiExceptionAdvice` 159); (b) retry the read and commit once inside `signal`; (c) add an `expectedVersion` to the signal request (a wire change, with `index.ts` and UI edits).
**OQ7.** Signal body: options: (a) drop it (the `body` javadoc in `TransitionService` at 110-111 says it is recorded "for audit"; `ObjectsController` 482-484 and `automation.md` 636 say the same); (b) carry it in the `TransitionFired` payload under a new key (the payload is checked eagerly in `OutboxEntry`); (c) audit it elsewhere (no table exists).
**OQ8.** Automation-version mismatch at signal time: options: (a) let `IllegalStateException` surface, which is HTTP 500 under Spring Boot's default (section 7, risk 3); (b) add an `ApiExceptionAdvice` handler mapping it to 409 or 503; (c) document it. `fire` already throws the same exception (`commitTransition` is shared), so only signals are new.
**OQ9.** Several transitions match one name from the same state. This is the only open part of the lookup: a single failing guard is already a settled 409 (spec line 93; section 3, step 4). Options: (a) first in declaration order, and a failing guard on that first match rejects (409); (b) first in declaration order whose guard passes; (c) reject the ontology as ambiguous (outside spec 13). Today's `onSignal` takes the first match (180-187), and a failing guard on it is a silent no-op (`DefaultTransitionService` 236-237).
**OQ11.** Dead-lettered `SignalReceived` rows (V7; DR-23). Resume section 13, step 4 (line 200) says "P2 to P4 drain the dead-lettered rows"; resume section 20 (line 364) describes the re-arm used for P2d. Precedents: the P2d migration (V8, lines 5-9 on the P2d branch) stamps dead-lettered `ActionRequest` rows, as its comment at line 6 says; P3 OQ8 (P3 plan section 8, line 164) asks the same question for timers; and V7's header (lines 5-6) says a dead-lettered row keeps `processed_at` NULL, so it still counts for O-3. Item 25's statement already has the effect of option (b), because its `WHERE` clause does not exclude dead-lettered rows. That is a property of the draft SQL, not a decision: the question stays open. Options: (a) before the old version stops, re-arm them (`fail_count` 0, `dead_lettered_at` and `error` NULL) so the old relay drains them; (b) have the migration stamp them, which drops failed signals silently; (c) hold P4 until the count is zero.
**OQ12.** Automation coverage: spec row P4 names only `TransitionsEndToEndTest`. Options: keep `awaitState` (275) in `AutomationEndToEndTest.reopenSignalMovesAClosedProjectBackToActive`; make it a direct assertion; or add a test that a signal's `TransitionFired` row drives actions.
**OQ14.** DR: does this step add a DR at all? Spec section 10 adds no ADR (line 294). The DR file `02-decision-records.md` ends at DR-23 (line 145); DR-24 is on the P2d branch, and P3's DR is P3 OQ14 (P3 plan line 170). If a DR is added, which number: the next free one at merge, or another?
**OQ15.** Settling sets: does P4 save a frozen set and add a pair (spec line 128), given that main has no such chain and the P3 plan's section 5 (line 117) says a new migration adds no frozen copy? Options: (a) follow the spec and start the chain at P4, which means P2 and P3 also owe sets; (b) follow the P3 plan and correct spec line 128; (c) correct spec line 128 first.
**OQ16.** Dev-stack adapter: which adapter, if any, does the dev stack run, and so which adapter must the O-3 and O-4 counts cover? Facts on main: (1) no YAML sets `sequeless.automation.adapter`; the only application file is `sequeless-app/src/main/resources/application.yaml`; (2) `docker-compose.yml` has `postgres` (line 16) and `temporal` (line 30) services, no application service, and two volumes (42-43); (3) both adapter auto-configurations are conditional on the property with no `matchIfMissing` (`InProcessAutomationAutoConfiguration.java:35`, `TemporalAutomationAutoConfiguration.java:48`); (4) with no adapter, `OutboxRelay.pollOnce` returns at 121-123; (5) `automation-signals.spec.ts` line 7 says "verified against a live run" (line 34 has no such text), and the repo does not say which adapter that run used; (6) the P2d procedure's step 1 asks the owner to check the setting (resume section 20, line 361). The repo cannot answer the question: the setting, if it is set, is set outside the repo. P3 OQ16 (P3 plan line 172) asks the same question.
**OQ17.** Live panel wording (new): spec row P4 fixes the new text for `object-signals.ts` only. The live panel, `signals-panel.ts`, is not named in the spec, but its line 61 carries the same stale text ("dispatched — takes effect asynchronously"), for the reason spec line 93 gives for `object-signals.ts`: it is "stale after this step, so it changes here". Options: (a) use the same wording in both panels (the reading this plan takes for section 4, item 28); (b) keep "dispatched" in the live panel, which describes the old asynchronous behaviour and would leave false text there; (c) a different wording for the live panel.

## 9. Merge order

1. P2d (PR #168) merges after the owner's dev-stack check (resume section 20, the owner's gate, steps 1 to 4).
2. P2e merges. It moves the `ACTIVITY_OPTIONS` holder off `ActionWorkflowImpl` (P2e plan line 10; item 3 at line 83) before any signal-workflow reader is removed (section 2).
3. P3 merges. P4 code does not start before that. Before any P4 code: a read-only review of this plan, and answers to OQ5 to OQ9, OQ11, OQ12 and OQ16 (they shape code or the migration). OQ14, OQ15 and OQ17 are needed before the PR.
4. The P4 PR is cut from the new `origin/main` after P3, with the migration at the number section 5 gives. Its body records the O-3 and O-4 counts at deploy (spec line 332) and the dev-stack adapter (OQ16).
5. The P5b plan follows P4 (resume section 20, step 6, line 381). P5b code waits for P4's merge.

Nothing in this file is code. The plan is complete when the owner or Navigator has answered OQ5 to OQ9, OQ11, OQ12, OQ14 to OQ17.
