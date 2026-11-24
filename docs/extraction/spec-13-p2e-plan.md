# Spec 13, P2e: delete ActionWorkflow and the section 17 items (plan)

- Status: Rev 2, 2026-11-24: questions 1 to 11 decided (section 7). PR #168 merged as d577b20, so P2e code can start. The P2e merge waits on the owner's zero check of running `ActionWorkflow` executions (Q8) and on V8 recorded as applied (section 8, step 3).
- Rev 1: plan only, docs only. Recorded 2026-11-23. Not reviewed.
- Base: `origin/main` `d577b20` (PR #168 squash-merged). Rev 1 cited `197e36c`; no Java file changed between `197e36c` and `d577b20`, so the citations hold.
- Citations are `path:line` at `197e36c`, repo-relative. Shorthand: `T/` is `sequeless-adapter-automation-temporal/src/main/java/org/sequeless/adapter/automation/temporal/`, and `TT/` is the same path under `src/test/java`.

## 1. Scope and base

- P2e is cut from `origin/main` `d577b20` (PR #168 merged). This document is the only file changed on its branch.
- P2e does three things. (a) It deletes `ActionWorkflow` and its impl, and moves the `ACTIVITY_OPTIONS` holder, because nothing starts `ActionWorkflow` after the flip. The flip removed its only starter: `T/TemporalAutomationPort.java` has no `ActionWorkflow` reference, and the decision is in `docs/extraction/spec-13-p2d-plan.md` section 10.1 (lines 184 to 192). (b) It does section 17 items 1 to 8 of `docs/extraction/resume-plan-spec13.md` (lines 298 to 305). (c) It converts or deletes the ActionRequest-form tests that P2d kept on purpose (Q-E: P2d deletes none).
- Q-D is decided: `OutboxEntry.KIND_ACTION_REQUEST` is deleted in P2e (section 7, item 1).
- Not in P2e: any migration. The V3, V4, V5 and V8 SQL files under `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres/`, and their frozen copies under `src/test/resources/frozen-migrations/`, are not edited. Applied migrations are never edited.
- Not in P2e: P3, P4 and P5b.

## 2. Inventory at 197e36c

### 2.1 ActionWorkflow and its holder

| Location | Lines | What it is |
|---|---|---|
| `T/ActionWorkflow.java` | 8-11, 17-18, 28-29 | the interface (30 lines). Its javadoc says it is registered and never started. `@WorkflowMethod execute(OutboxEntry)`. No explicit workflow name at 17 |
| `T/ActionWorkflowImpl.java` | 29 | the impl (76 lines) |
| `T/ActionWorkflowImpl.java` | 31-36, 37 | the holder, `static volatile ActivityOptions ACTIVITY_OPTIONS` at 37, with its javadoc (31-36); the class javadoc is 12-28 |
| `T/ActionWorkflowImpl.java` | 46-50 | null check, which throws naming `ActionWorkflowImpl.ACTIVITY_OPTIONS` |
| `T/ActionWorkflowImpl.java` | 54-62 | `actionKind` switch into the four drain activities (56-59); line 61 throws on an unknown kind |
| `T/TemporalAutomationAutoConfiguration.java` | 97-99 | javadoc naming the holder and `ActionWorkflow` |
| `T/TemporalAutomationAutoConfiguration.java` | 141 | the only production writer, which runs before the worker starts |
| `T/TemporalAutomationAutoConfiguration.java` | 146 | worker registration of `ActionWorkflowImpl.class` |
| `T/ChangeEventWorkflowImpl.java`, `T/SignalEventWorkflowImpl.java`, `T/TimerWorkflowImpl.java`, `T/TransitionActionWorkflowImpl.java` | 17, 13, 23, 24 | the four readers of the holder. Each throws on null (19, 15, 25, 26) |

Holder and `ActionWorkflow` names in javadoc only: `T/ChangeEventWorkflow.java:12`, `T/ChangeEventWorkflowImpl.java:10` (`{@link ActionWorkflowImpl#ACTIVITY_OPTIONS}`), `T/SignalEventWorkflow.java:11`, `T/TimerWorkflow.java:16,23`, `T/TimerWorkflowImpl.java:10-12`, `T/TransitionActionWorkflow.java:8`, `T/TransitionActionWorkflowImpl.java:16,35`, `T/WebhookActivityException.java:8`, `T/package-info.java:13-14,30,37`, `T/TemporalAutomationProperties.java:8,11,22,38,43`, `sequeless-adapter-automation-temporal/pom.xml:15-16`.

Test writers of the holder: `TT/TemporalAutomationPortContractTest.java:130` (with its registration at 138), `TT/TemporalAutomationPortTransitionFiredTest.java:71`, `TT/TransitionActionWorkflowTest.java:80`.

### 2.2 The ActionRequest-only paths in main code

| Location | Lines | What it is |
|---|---|---|
| `T/ActionActivities.java` | 31-32, 35-36, 39-40, 47-48 (javadoc 10-11, 20, 41-46) | the four ActionRequest activity methods: applySetProperty, applyCreateObject, applyLog, executeWebhook. Only `ActionWorkflowImpl` calls them (56-59) |
| `T/ActionActivities.java` | 67-68 | `applyTransitionAction`, kept. Its only caller is `T/TransitionActionWorkflowImpl.java:30` |
| `T/ActionActivitiesImpl.java` | 58, 63, 68, 73-75 | the four drain implementations. Line 74 calls `resolveWebhook`, and 75 calls `deliver` |
| `T/ActionActivitiesImpl.java` | 25, 33 (javadoc), 97-101, 128, 142 | `applyTransitionAction` and its stamp check (101). `deliver` is called from 128 and defined at 142, so it stays |
| `sequeless-spi/src/main/java/org/sequeless/spi/automation/ActionExecutor.java` | 81, 108, 134, 153 | the four drain methods. The two-path paragraph at 17-23 (class javadoc 8-53) and the payload list at 25-52 describe the drain path. The per-action methods are at 177 and 202 |
| `sequeless-core/src/main/java/org/sequeless/core/automation/DefaultActionExecutor.java` | 91, 103, 118, 131 | drain implementations. `applyAction` (140) and `resolveWebhookAction` (174) are the per-action path. Private helpers run from 190 to 393 |
| `sequeless-spi/src/main/java/org/sequeless/spi/outbox/OutboxEntry.java` | 64-74 | javadoc "Removed in P2e (spec 13)" (64-72); `@Deprecated` at 73; `KIND_ACTION_REQUEST` at 74 |
| `sequeless-app/src/main/java/org/sequeless/app/automation/OutboxRelay.java` | 53-60, 150 | `DISPATCHABLE_KINDS` has no ActionRequest. The javadoc at 150 still names it |

There is no main-code writer of the ActionRequest kind, and the constant is used in no main code except its declaration.

Implementers of the drain methods, five in all: `DefaultActionExecutor` (`sequeless-core/src/main/java/org/sequeless/core/automation/DefaultActionExecutor.java:67`) is the fifth and the production one. The other four break if the methods go (see Q3): `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/automation/RecordingActionExecutor.java:26`; `TT/RecordingTransitionExecutor.java:20,23` (javadoc, class); `TT/TemporalAutomationPortContractTest.java:425,435-450` (`SwappableActionExecutor` delegates); `sequeless-app/src/test/java/org/sequeless/app/TransitionsEndToEndTest.java:108,133-148` (`WebhookStandInActionExecutor` delegates).

### 2.3 Tests and fixtures that use the ActionRequest form

`KIND_ACTION_REQUEST` appears 45 times in test code: `PostgresOutboxPortIT` 34, `DefaultActionExecutorTest` 6, `PostgresOutboxDecodeFailureIT` 2, and one each in `OutboxRelayTest`, `DefaultTransitionServiceTest` and `OutboxEntryDataConverterRoundTripTest`.

| Location | Lines | Use |
|---|---|---|
| `sequeless-core/src/test/java/org/sequeless/core/automation/DefaultActionExecutorTest.java` | 280-392 | applySetProperty tests. They build rows through `SetPropertyEntryBuilder` (683-720), whose constant is at 715 |
| same | 327 | `objectUpdatedFromAnActionRowWrittenBe...`, which uses the same builder |
| same | 400-437 | `applyCreateObjectDerivesIdFromEntryIdAndActionIndexAndIsIdempotent`, constant at 418 (section 17 item 8) |
| same | 440-469, 470-498, 499-526, 527-559 | drain tests for create (constant at 457), resolveWebhook (487, 515) and log (543) |
| same | 560-632, 645-663 | per-action tests for set, create, log (560, 577, 594, 618) and for `resolveWebhookAction` (645) |
| same | 633-643 | `applyActionRefusesAWebhookActionAndSendsItToResolveWebhookAction` (section 17 item 3) |
| `sequeless-core/src/test/java/org/sequeless/core/usecase/DefaultTransitionServiceTest.java` | 313, 347 | the test name ends "AndNoActionRequest"; 347 asserts `doesNotContain(OutboxEntry.KIND_ACTION_REQUEST)` |
| `sequeless-app/src/test/java/org/sequeless/app/automation/OutboxRelayTest.java` | 123-131, 184-191 | `relayDoesNotClaimActionRequestRows`, and the builder `actionRequest()`, which uses the constant at 191 |
| `sequeless-adapter-persistence-postgres/src/test/java/org/sequeless/adapter/persistence/postgres/PostgresOutboxPortIT.java` | 30 (javadoc); 68-353; 419-423 | about 34 uses of the constant as the claimed kind, for example `claimSkipsNonActionRequestKinds` (75) and `rowWhoseHandlerAlwaysThrowsIsDeadLetteredAfterThreeFailedDispatches` (192). The helper `insertOutboxRow` takes the kind (419, 423) |
| `.../postgres/PostgresOutboxDecodeFailureIT.java` | 44, 147-154 | the constant in a dispatch kind set (44) and in an insert helper (154) |
| `.../postgres/PostgresOutboxClaimIT.java` | 39, 68 | the literal string `"ActionRequest"`, not the constant |
| `.../postgres/PostgresOutboxV5MigrationIT.java`, `.../postgres/PostgresOutboxV8MigrationIT.java` | 38, 69, 80; 21-22, 53-67 | literal strings in migration tests. Keep |
| `TT/OutboxEntryDataConverterRoundTripTest.java` | 23 (javadoc), 45-52 (constant at 51) | the round trip uses an ActionRequest-kind entry. `TransitionActionWorkflow` also takes an `OutboxEntry` (`T/TransitionActionWorkflow.java`), so the round trip still matters |
| `sequeless-adapter-automation-inprocess/src/test/java/org/sequeless/adapter/automation/inprocess/InProcessAutomationPortTransitionFiredTest.java` | 143-170 | section 17 items 1 and 2 |

Javadoc-only mentions of the drain path: `sequeless-adapter-automation-inprocess/src/main/java/org/sequeless/adapter/automation/inprocess/package-info.java:6`, `sequeless-core/src/main/java/org/sequeless/core/automation/DefaultActionExecutor.java:44,49,62,286,330,343,377`, `sequeless-core/src/main/java/org/sequeless/core/automation/package-info.java:7,25`, `sequeless-core/src/main/java/org/sequeless/core/statemachine/PayloadValueCodec.java:35`, `T/package-info.java:14`. Also `sequeless-spi/src/main/java/org/sequeless/spi/automation/ActionExecutor.java:186` (`{@link #resolveWebhook}`), and `sequeless-core/src/main/java/org/sequeless/core/automation/DefaultActionExecutor.java:53-58, 185, 233, 302, 313` (links to the drain methods).

## 3. Change list, file by file

Tags: [M] depends on the owner's mixed-version procedure (section 6, risk 1; section 8, step 3). Every question is decided in section 7; [D n] names the decision that governs the row. Nothing here is decided by this plan; the tags say what waits.

**Delete**

1. `T/ActionWorkflow.java` (30 lines). [M]
2. `T/ActionWorkflowImpl.java` (76 lines), after the holder moves (row 3). [M]
3. Holder. Move `static volatile ActivityOptions ACTIVITY_OPTIONS` (`T/ActionWorkflowImpl.java:37`, javadoc 31-36) to the class chosen in Q2. Change the writer (`T/TemporalAutomationAutoConfiguration.java:141`), the four readers (section 2.1), the four "was never set" messages, and the three test writers (section 2.1). Not [M]: the holder name is not in any workflow history.
4. `T/TemporalAutomationAutoConfiguration.java:146`: remove `ActionWorkflowImpl.class` from `registerWorkflowImplementationTypes`. [M]
5. `TT/TemporalAutomationPortContractTest.java:138`: remove the same registration. [M]
6. `T/ActionActivities.java:31-32, 35-36, 39-40, 41-48`, and the implementations `T/ActionActivitiesImpl.java:58-75`. Keep `applyTransitionAction` (67-68, 97-101) and `deliver` (142). [M] [D 3]
7. The drain path in the SPI and in core: `ActionExecutor.java:81, 108, 134, 153` with its drain javadoc (25-52); `DefaultActionExecutor.java:90-137`; the private helpers that only those methods call (the compiler lists them, this plan does not); and the five implementers in section 2.2 (`DefaultActionExecutor` and the four others named there). [D 3]
8. `OutboxEntry.java:64-74`, deleted (Q1). [D 1] No database effect if V8 has stamped every ActionRequest row, which needs the P2d hold met (section 8, step 3).

**Convert or delete (tests)**

9. `DefaultActionExecutorTest.java` drain tests (280-559 and the builder at 683-720) to the per-action form, or delete the ones the per-action tests cover. [D 5]
10. `PostgresOutboxPortIT.java`, `PostgresOutboxDecodeFailureIT.java`, and `OutboxRelayTest.java:127-131, 184-191`: replace or delete the constant per Q4. [D 4]
11. `DefaultTransitionServiceTest.java:313` (rename) and `:347` (the `doesNotContain` assertion). [D 1]
12. `TT/OutboxEntryDataConverterRoundTripTest.java:23, 51`: javadoc and kind. [D 1]
13. `TT/RecordingTransitionExecutor.java:20` (javadoc, and the four methods if Q3 deletes them). [D 3]
14. `TT/TemporalAutomationPortContractTest.java:425-450` and `TransitionsEndToEndTest.java:108, 133-148`: delegates for the drain methods. Delete with Q3, otherwise keep. [D 3]
15. `InProcessAutomationPortTransitionFiredTest.java`: a new non-Number stamp case (section 4, item 2).

**Edit (javadoc, pom and docs)**

A dangling `{@link}` fails `javadoc:jar` under `-Prelease`, so every javadoc row here is required for the gate, not cosmetic.

16. Javadoc in section 2.1 and 2.2: `T/package-info.java:13-14,30,37`; `T/TimerWorkflow.java:16,23`; `T/ChangeEventWorkflow.java:12`; `T/ChangeEventWorkflowImpl.java:10`; `T/SignalEventWorkflow.java:11`; `T/TimerWorkflowImpl.java:10-12,25`; `T/TransitionActionWorkflow.java:8`; `T/TransitionActionWorkflowImpl.java:16,35`; `T/WebhookActivityException.java:8`; `T/TemporalAutomationProperties.java:8,11,22,38,43` (the task queue is still used by `TransitionActionWorkflow`, so only the wording changes); `T/TemporalAutomationAutoConfiguration.java:97-99`; `T/ActionActivitiesImpl.java:24-37`; `T/ActionActivities.java:9-25`.
17. `sequeless-adapter-automation-temporal/pom.xml:15-16`: the description says the adapter "drives an ActionWorkflow per ActionRequest outbox entry". Reword it to the per-action path.
18. `sequeless-spi/pom.xml:15-19`: add automation-contract to the description (section 4, item 5).
19. Docs: `docs/architecture/automation.md:46` (the deprecated constant), `:207-212` (ActionWorkflow registered, never started), `:111` (the drain path, removed in P2e), `:214-220` (`executeWebhook`, and "all five activity methods"), `:267` (`applySetProperty` retry idempotency), `:224-230` (the holder). DR-24 (`docs/architecture/02-decision-records.md:164-182`) is not edited (Q11). Do not rewrite `docs/extraction/resume-plan-spec13.md` or `docs/extraction/spec-13-p2c-plan.md`. They are history, as `docs/extraction/spec-13-p2d-plan.md` p2d-7 (line 208) also says.

**Not changed by P2e**

- All SQL migrations and their frozen copies (section 1).
- `PostgresOutboxClaimIT.java` (literal strings only, no compile dependency).
- `PostgresOutboxV5MigrationIT.java` and `PostgresOutboxV8MigrationIT.java` (they prove the migrations).

## 4. Section 17 items 1 to 8

Each item is quoted from `docs/extraction/resume-plan-spec13.md`, with its status at 197e36c and the change.

1. Quote: "`InProcessAutomationPortTransitionFiredTest`, the "nothing applied" assertion (about line 151): the automation is empty, so the assertion is vacuous. Use an automation that holds a LogAction for the row's transition."
   Status: OPEN. The vacuous test is still present at `InProcessAutomationPortTransitionFiredTest.java` lines 159 to 169: line 160 builds an empty automation with `Map.of()`, and line 168 asserts `recorder.calls()` is empty. The fix is to give that test an automation that holds a `LogAction` for the row's transition, so the check can fail. The test `dispatchAppliesTheSingleActionOfAStampedTransitionFiredRowOnce` (`InProcessAutomationPortTransitionFiredTest.java:143-156`) already holds a `LogAction` (146-148) and asserts one `METHOD_APPLY_ACTION` call (154-155), which is the shape to copy. Change: open, see the status above; the test needs a non-empty automation.

2. Quote: "Add a case for a present but non-Number stamp (for example the string "1"), asserting IllegalStateException naming the row id."
   Status: open. The test file has only the absent-stamp case (`dispatchTransitionFiredRefusesARowWithNoOntologyVersionStampAndNamesTheRow`, 159-169). The adapter check is `sequeless-adapter-automation-inprocess/src/main/java/org/sequeless/adapter/automation/inprocess/InProcessAutomationPort.java:160-161` (`instanceof Number`, then the throw). Change: add the case to the in-process test, shaped like 159-169 with a `"1"` stamp. The new test must use a non-empty automation (a `LogAction` for the row's transition), so that the "nothing applied" check can fail. Proof: it fails if the `instanceof Number` check is replaced by a cast.

3. Quote: "`DefaultActionExecutorTest` (about lines 635 to 643): the resolveWebhookAction test checks only the exception type, and its `commitCalls` check cannot fail. Add a message assertion."
   Status: open. `DefaultActionExecutorTest.java:633-643` asserts only `isInstanceOf(IllegalArgumentException.class)` and `store.commitCalls` isZero. Change: add `hasMessageContaining` with the text `DefaultActionExecutor.applyAction` throws (`DefaultActionExecutor.java:140-172`). Whether to drop the `commitCalls` check is Q9.

4. Quote: "`ActionExecutor.applyAction` @throws line (about line 168): use `{@code action}` and `{@link WebhookAction}` style."
   Status: open. `sequeless-spi/src/main/java/org/sequeless/spi/automation/ActionExecutor.java:175` reads "if action is a WebhookAction; deliver it through resolveWebhookAction instead". Change: "if {@code action} is a {@link WebhookAction}; deliver it through {@link #resolveWebhookAction} instead".

5. Quote: "`sequeless-spi/pom.xml` `<description>` (lines 15 to 19) still lists only ontology-core and schema-contract. Add automation-contract."
   Status: open. `sequeless-spi/pom.xml:15-19` names only ontology-core and schema-contract. The SPI does import automation-contract (`ActionExecutor.java:4`). Change: add it to the description.

6. Quote: "`ActionExecutor` class javadoc (lines 15 to 22) still describes ActionRequest-kind entries. Rewrite it in 2d."
   Status: partly rewritten in 2d (lines 17 to 23 are the two-path paragraph, and line 25 has the words On the drain path); the remaining ActionRequest wording is still open. The class javadoc (`ActionExecutor.java:17-31`, and the payload list at 25-52) still describes the drain path. Lines 18-20 call that path legacy and say no code writes such a row. Change: after Q3. If the drain methods go, the javadoc covers `applyAction` and `resolveWebhookAction` only, and 25-52 is removed. If they stay, the text says why.

7. Quote: "`docs/architecture/automation.md` TransitionFired key list (about line 75) lacks `ontologyVersion`, `self`, `typeIri` and `machineIri`."
   Status: DONE by the flip. `docs/architecture/automation.md` lines 84 to 86 already list `machineIri`, `typeIri`, `ontologyVersion` and `self`. Change: none. Re-read it and record it in the PR body.

8. Quote: "Core tests for the derived create id under the TransitionFired id: the review said the id differs from the ActionRequest id, which is safe only because of the drain gate. Record it in the 2d PR body."
   Status: DONE. Coverage is at `DefaultActionExecutorTest` 594-615 and 228-242, and the note is in the PR #168 body (Q6). Change: none; no new test.

## 5. Proof and tests

Gate, for the P2e head. This is the only gate that counts:

```
source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify
```

JDK 25 is required; the default `java` is 21. `~/.bashrc.ext` supplies the Maven Packages token. A test-only run misses the dependency analysis.

Proof, by change:

- Holder move and ActionWorkflow deletion: `TT/TransitionActionWorkflowTest.java` (writes the holder at 80 and runs `TransitionActionWorkflowImpl`), `TT/TemporalAutomationPortTransitionFiredTest.java` (71), and `TT/TemporalAutomationPortContractTest.java` (130, 138). The other three readers (ChangeEvent, Timer, SignalEvent) are not each named by a test here. Before relying on the Temporal end-to-end tests for them, check which tests start those workflows (the P3 and P4 proof columns in `docs/extraction/spec-13-prototype-stage4-prepare.md` name the timer and signal end-to-end tests). Q2 decides whether a null-holder test per reader is added.
- Registration: `TT/TemporalAutomationAutoConfigurationTest.java` exists. It was not read for this plan, so read it before deciding whether it asserts the registration list. After the change, `git grep -n -w 'ActionWorkflow\|ActionWorkflowImpl' -- '*.java'` must print nothing. Compilation proves the deletion; the grep catches dangling javadoc links.
- Drain deletion (if Q3 deletes it): `DefaultActionExecutorTest`, `OutboxRelayTest`, `PostgresOutboxPortIT`, `PostgresOutboxDecodeFailureIT`, `OutboxEntryDataConverterRoundTripTest` and `TransitionsEndToEndTest` must still pass. Each converted or deleted drain test names the per-action test that replaces it, so no case is lost silently (risk 7).
- Constant (if Q1 deletes it): `PostgresOutboxPortIT`, which runs against Postgres through Testcontainers, proves the claim, dead-letter and concurrency behaviour under the new kind. `git grep -n 'KIND_ACTION_REQUEST' -- '*.java'` must print nothing.
- Section 17: item 2 is a new test, and it must fail under a mutation of `InProcessAutomationPort.java:160` (the `instanceof Number` check replaced by a cast). Item 3's message assertion must fail when the message is changed. Item 8 needs no new test (Q6).

## 6. Risks

1. A running `ActionWorkflow` during the deploy. Since P2d nothing starts it, so only executions started before the flip can still be open. An open execution waiting on a webhook retry, for example, is stuck once its activity methods (`ActionActivities.java:31-48`) or its type are gone. Before the P2e worker starts, the running `ActionWorkflow` executions on the dev stack must be handled. Decided (Q8): a zero check of running `ActionWorkflow` executions only; the owner runs it before the P2e merge. [M]
2. Replay of an old history. A worker replays an open execution's history when it picks up a task. After the type is deleted, replaying an open `ActionWorkflow` fails its workflow task on every retry. Closed executions are not replayed in normal flow. Risk 1 covers open executions only. [M]
3. A holder reader or writer is missed. A reader throws `IllegalStateException` on a null holder (for example `T/ChangeEventWorkflowImpl.java:19`). It throws at workflow run time, not at startup. Keep the order: the holder is set in `T/TemporalAutomationAutoConfiguration.java:141`, before the worker starts (the holder's javadoc says so at `ActionWorkflowImpl.java:21-22`).
4. A dangling `{@link}`. `javadoc:jar` runs under `-Prelease` and fails on a dangling link. Every javadoc row in section 3 is required for the gate.
5. A public SPI break. Removing the four drain methods from `ActionExecutor` (Q3) breaks any implementer outside this repository. The repository does not show whether one exists.
6. Rollback. A rollback to P2d after P2e is safe, because P2d writes and drains no ActionRequest rows. A rollback to a version before P2d is not. That is already true at P2d, and `docs/architecture/automation.md:88-92` describes the same case.
7. Lost coverage. Converting a drain test to the per-action form can drop a case that only the drain test covers. The derived-id idempotency case is covered at `DefaultActionExecutorTest.java:604-613` and `237-242`. If the per-action tests stay, converting or deleting the drain test at `400-437` does not lose that case (Q5).
8. Sequencing. If P2e merges before the P2d hold is met, V8 may not have stamped the ActionRequest rows. They would stay unprocessed, with no constant left to name them. Section 8 blocks this.

## 7. OPEN QUESTIONS FOR THE NAVIGATOR

Each question keeps its Rev 1 text; the **Decided:** line under it is the Navigator's answer.

1. Q-D: is `OutboxEntry.KIND_ACTION_REQUEST` (`OutboxEntry.java:73-74`) deleted in P2e, or kept `@Deprecated`? The javadoc says "Removed in P2e" (`OutboxEntry.java:64-72`). `docs/architecture/automation.md:46` says "deprecated". The decision record (DR-24, `docs/architecture/02-decision-records.md:172`) says deprecated in P2d and removed in P2e. The Navigator decided in P2d to keep it `@Deprecated` until P2e (`docs/extraction/spec-13-p2d-plan.md` line 224 at `197e36c`); what P2e does with it is still open. There are 45 test uses (section 2.3) and no main-code use. Whether anything outside this repository reads the constant is not visible in the repo.

   **Decided:** Delete `OutboxEntry.KIND_ACTION_REQUEST` in P2e. Recorded as DR-24 (`docs/architecture/02-decision-records.md`, the DR-24 heading).

2. Where does the `ACTIVITY_OPTIONS` holder go? Options: a new holder class in `T/`, a static on `TransitionActionWorkflowImpl`, or a static on `TemporalAutomationAutoConfiguration`. The four readers and three test writers change with the choice.

   **Decided:** New holder class `TemporalActivityOptions` in package `org.sequeless.adapter.automation.temporal`, with a single `static volatile ActivityOptions ACTIVITY_OPTIONS` and a class javadoc explaining why a Temporal-instantiated workflow uses a once-published static. The four readers, the production writer in `TemporalAutomationAutoConfiguration` (around line 141), and the three test writers move to `TemporalActivityOptions.ACTIVITY_OPTIONS`. The reader javadoc becomes a one-line pointer to the holder.

3. Which drain-path code goes in P2e? Option (a): only `ActionWorkflow`, its impl, and the four ActionRequest activity methods and their implementations (`ActionActivities.java:31-48`, `ActionActivitiesImpl.java:58-75`). Option (b): also the four drain methods on `ActionExecutor` (`ActionExecutor.java:81-153`), `DefaultActionExecutor.java:90-137`, their private helpers, and the five implementers in section 2.2 (`DefaultActionExecutor` and the four others named there). Section 17 does not list (b). Evidence that the drain removal was already decided: DR-24 (`docs/architecture/02-decision-records.md` lines 180 to 181) says the drain methods of `ActionExecutor` ... are removed in P2e, and `docs/architecture/automation.md` line 111 says the drain path, which is removed in P2e. The question is the scope of that removal. After `ActionWorkflow` goes, `ActionActivitiesImpl` is the only main caller of the drain methods (`ActionActivitiesImpl.java:59, 64, 69, 74`).

   **Decided:** Option (b), the full drain-path removal. Delete `ActionWorkflow` and `ActionWorkflowImpl`; the four ActionRequest activity methods on `ActionActivities` and their implementations in `ActionActivitiesImpl`; the four drain methods on the `ActionExecutor` SPI (`applySetProperty`, `applyCreateObject`, `resolveWebhook`, `applyLog`) and their implementations in all five implementers; the private helpers used only by the drain implementations; the drain-path tests. The PR body records that removing abstract SPI methods breaks out-of-repo implementers (a one-way door). Helpers shared with the per-action path stay.

4. Which kind replaces `KIND_ACTION_REQUEST` in the tests? Candidates: `OutboxEntry.KIND_TRANSITION_FIRED`, the claimable automation kind since V5, or a test-only string. Also: `claimSkipsNonActionRequestKinds` (`PostgresOutboxPortIT.java:75`) and `relayDoesNotClaimActionRequestRows` (`OutboxRelayTest.java:127`) test that a non-dispatched kind is not claimed. Keep them as negative tests with another kind, or delete them?

   **Decided:** Replace `KIND_ACTION_REQUEST` in the converted tests with `OutboxEntry.KIND_TRANSITION_FIRED`. Both negative tests stay: `claimSkipsNonActionRequestKinds` becomes `claimSkipsNonTransitionFiredKinds` (row kind `ObjectCreated`, claimed set `{TransitionFired}`); `relayDoesNotClaimActionRequestRows` keeps its assertion with the string literal `"ActionRequest"` as the row kind, because the constant is deleted.

5. Q-E follow-up: convert or delete the drain tests? Q-E says convert, and P2d deletes none. Does P2e also delete a drain test that a per-action test covers, or convert every one? Per-action tests exist for set, create, log and webhook (`DefaultActionExecutorTest.java:560-663`). The test at line 594 asserts replay idempotency at lines 611 to 613.

   **Decided:** Option (2). Delete the three drain tests that a per-action test already covers: `applySetPropertyCommitsWhenValueDiffersExpressionVariant`, `applyCreateObjectDerivesIdFromEntryIdAndActionIndexAndIsIdempotent`, `resolveWebhookRendersUrlAndBodyAndMakesNoObjectStoreCalls`. Convert the other eight to per-action form (`applyAction` / `resolveWebhookAction` with a TransitionFired row and the PropertyAssignment list), keeping each unique assertion: no-op when the value already matches (literal and expression variants); safe no-op when the target object is missing; an unstamped row leaves `ontologyVersion` out of the outbox payload; the Update keeps `expectedVersion` at the current version (3 in the existing test) and the outbox payload carries version+1 (4) and `typeIri` with kind `KIND_OBJECT_UPDATED`; expression-variant create properties; webhook body absent when there is no body; the log context assertions (`selfProperties`, `selfState`, `principalId`).

6. Section 17 item 8: is it a test, or a note in the PR body? The text says "Core tests for the derived create id under the TransitionFired id" and then "Record it in the 2d PR body", but the item sits under step 2e. If it is a test, which assertion?

   **Decided:** No new test. `applyActionCreatesTheObjectAtTheDerivedIdAndCreatesNothingOnReplay` covers the derived create id. The "Record it in the 2d PR body" note was added to PR #168's body.

7. Section 17 item 2 names only the in-process adapter. Should the same non-Number case cover the Temporal stamp checks at `T/TemporalAutomationPort.java:131-132` and `T/ActionActivitiesImpl.java:101`?

   **Decided:** Option (2). Add a non-Number stamp case to the IN-PROCESS adapter test only (resume plan section 17 item 2). No new Temporal test: both Temporal stamp checks already have named tests (`TemporalAutomationPortTransitionFiredTest.refusesEachBadStampBeforeStartingAnyWorkflow` and `TransitionActionWorkflowTest.refusesANonNumberStampWithANonRetryableFailure`).

8. Which step does P2e need for running `ActionWorkflow` executions: (a) a zero check of running `ActionWorkflow` executions only, or (b) a full stop-and-drain step? The P2d procedure concerns outbox rows, and P2e changes no database state. Since P2d nothing starts `ActionWorkflow`, so an old P2d worker cannot start one. That leaves open executions (risk 1).

   **Decided:** Option (a). A zero check of running `ActionWorkflow` executions only (no stop-and-drain step). The owner runs it before the P2e merge.

9. Item 3 says the `commitCalls` check cannot fail. Keep it beside the new message assertion, or remove it?

   **Decided:** Option (2). Remove the `commitCalls` check from the webhook refusal test (`applyActionRefusesAWebhookActionAndSendsItToResolveWebhookAction`) and keep only the new message assertion. `applyAction`'s `WebhookAction` branch is a bare throw that never reaches `ObjectStorePort`.

10. After Q3, is the `ActionExecutor` class javadoc rewritten to the per-action path only, or does it keep a note about the removed drain path? (Section 4, item 6.)

   **Decided:** Option (1). Rewrite the `ActionExecutor` class javadoc to describe the per-action path only, with no mention of the removed drain methods. In the same P2e PR, also rewrite the javadoc links to the drain method names in `DefaultActionExecutor` (class javadoc and the shared helper javadocs) and in the testkit `RecordingActionExecutor`, or javadoc fails under `-Prelease`.

11. DR-24 (`docs/architecture/02-decision-records.md:164-182`) says P2e deletes `ActionWorkflow`. Is it amended in the P2e PR, or left unchanged with a new P2e entry?

   **Decided:** Option (3). Leave DR-24 unchanged. Record the P2e removals only in this plan and in the P2e PR body. No new DR entry.

## 8. Gate and merge order

1. P2d first. Done: PR #168 merged as d577b20. Only the P2e merge is gated (step 3).
2. Cut the P2e branch from the new `origin/main` (PR #168 is merged as d577b20). Do not rebase. The `--match-head-commit` pin in the P2d plan (section 10.4) applies to the P2e merge too.
3. Pre-merge checks on the dev stack (operational, not part of the PR): the owner's zero check of running `ActionWorkflow` executions (Q8, risk 1); and V8 recorded as applied (`sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres/V8__outbox_actions_drained.sql`).
4. The gate runs on the final head with the command in section 5. Then open the PR, with the section 4 statuses in its body.
5. Merge: squash, pinned to the reviewed head. An `--admin` merge only under the standing owner approval for a PR blocked only by the update rule, with required checks green, and recorded.
6. Commit order in the PR, one commit per group, as P2d did: (a) holder move; (b) `ActionWorkflow` and its registration deleted; (c) section 17 items; (d) test conversions; (e) the constant deleted (Q1); (f) the SPI drain removal (Q3).
7. After P2e: P3, then P4 and P5b. P3 needs P2 on main.
