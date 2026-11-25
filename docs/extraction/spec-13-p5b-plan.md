# Spec 13, P5b: the Store-event ingress (plan)

Status: plan, no code yet. Made against `origin/main` `0b30261` (P4 merged as `d2e28a6`, #212), in the worktree `sequeless-prototype-p5b` (branch `spec-13-p5b-store-event-ingress`). Parent: `docs/extraction/spec-13-prototype-stage4-prepare.md` ("spec"; the P5b row is line 94). "Resume plan" is `docs/extraction/resume-plan-spec13.md`. Tracker: #41 (`Refs #41` only).

Conventions: `{main}` and `{test}` stand for `src/main/java/org/sequeless` and `src/test/java/org/sequeless` inside the named module. Line numbers are `0b30261`. `OQn` is open question n in section 8. A question that the spec, the resume plan or a recorded decision settles is not in section 8; the source is cited where the question comes up.

This file ships inside the P5b code PR, not in a separate docs PR (owner rule: no mid-cycle docs PRs; `docs/agents/handoff.md`, "Next actions"). Its decisions block (section 8) is filled in before the merge.

## 1. Scope and what is settled

- P5b replaces `AutomationPort.dispatch(Scope, OutboxEntry)` with a Store-event ingress whose signature has no `OutboxEntry` (spec line 148). It replaces the kind switches in both adapters (spec line 94). `DefaultTriggerEvaluator.onChange` takes the ingress event (spec lines 45, 51, 94). For `onTimerElapsed`, see OQ4. There is no migration (spec line 94; spec 5.3, line 186).
- It also carries the Temporal removal of `activities.recompute` from `ChangeEventWorkflowImpl.run`, which moved here from P5a (resume plan line 122; spec line 463; `remaining-work.md` line 287). The P5b PR lists `ChangeEventWorkflow` in its O-4 list.
- Order: P2, P3 and P4 are merged (P4 is `d2e28a6`), so the relay already dispatches only the four Store-event kinds (`OutboxRelay.java:51-55`; `OutboxEntryTest.declaresExactlyTheFourKindConstants`, lines 130-143). Spec section 3, line 81, and resume plan section 4, lines 58 and 60, are met.
- O-4 is answered: the only Temporal state was on dev volumes, and those were cleared (`remaining-work.md` line 15). There are no non-dev environments. The hold is lifted. The PR still names every workflow type whose code or input changes (section 6: only `ChangeEventWorkflow`) and records the O-4 result: dev only, cleared. The owner's rule to re-count before any deploy still applies (`handoff.md` line 77), as does the loss acceptance Q-A (line 78).
- B2 is decided: route 1, amended by DR-23 (spec lines 366-371). It does not hold P5b. Under the shape decided in OQ2, the ingress does not read the stamp at all. The stamp checks stay where P2 and P3 put them.
- O-6: names are chosen at implementation and stay inside the prototype (spec line 338). This plan proposes them (section 3). Test class names: O-13 (spec line 356); the PR body names them.
- P5a's REQUIRES_NEW decorator (spec line 116) has shipped. P5b does not touch it.
- UI: `sequeless-ui` is in this repository. P5b changes no REST contract and no wire model. The OQ17 follow-up (`signals-panel.ts:61` still says "takes effect asynchronously") is handoff "Next actions" item 2. The Navigator's decision (section 8) puts its wording change in this PR (set_plan step `ui-wording-update`), so it is part of P5b, not a separate PR.

## 2. Inventory (as of `0b30261`)

SPI (`sequeless-spi/{main}/spi/automation/`):

- `AutomationPort.java`: class javadoc 6-23 (switches on `entry.kind()`, 18-19); `dispatch` javadoc 26-46; method 47.
- `TriggerEvaluator.java`: `onChange` 49; `onTimerElapsed` 67, which takes the `TimerScheduled`-shaped entry built by `TimerScheduledEntry.of`; `timersStartedBy` 87 (added by P3; the spec table predates it). Line 77 says "Unlike the three methods above", but there have been two since P4.
- `ActionExecutor.java`: `applyAction` 43 and `resolveWebhookAction` 62, both on a `TransitionFired` `OutboxEntry`. The spec lists no P5b change here (spec line 167).
- `DerivationRecomputer.java:52`: `recompute(Scope, OutboxEntry)`. The input stays unchanged (spec line 47).
- `TimerScheduledEntry.java`: builds a kind `"TimerScheduled"` `OutboxEntry` that is never persisted (P3). It is the timer input.
- `package-info.java`: 27-36 say every inbound-port method takes `(Scope, OutboxEntry)`; 45-51 describe `dispatch` switching on `entry.kind()`.

Core:

- `sequeless-core/{main}/core/automation/DefaultTriggerEvaluator.java`: `onChange` 76-134. It reads `objectId` and `typeIri` (82-83) and compares the kind with `KIND_OBJECT_DELETED` (94). For a delete it reads the `properties` snapshot (97, 278-284). `onTimerElapsed` 136-158 reads `objectId`, `state` and `transitionName`. `timersStartedBy` 170-206 checks the stamp (176-187) and reads `objectId`, `typeIri` and `toState`.
- `DefaultActionExecutor.java`: `applyAction` 86 and `resolveWebhookAction` 121 read `transitionFired.payload()`. `derivedCreateId(sourceRow.id(), actionIndex)` at 189 makes a `CreateObject`'s id depend on the row id. The stamp is copied at 305-307, and is left out when the source row has none.

App:

- `sequeless-app/{main}/app/automation/OutboxRelay.java`: `DISPATCHABLE_KINDS` 51-55; `OBJECT_LIFECYCLE_KINDS` 57-58. The claim loop is 116-139: recompute for lifecycle kinds 120-123, `dispatch` 124. `buildScope` 172-179. Dead-lettering is done by `OutboxPort.claimNext` (DR-23; javadoc 104-108), not by the relay. Javadoc that names `dispatch`: 23, 29, 106.
- `app/automation/package-info.java`: 4-13.

In-process adapter (`sequeless-adapter-automation-inprocess/{main}/.../inprocess/`):

- `InProcessAutomationPort.java`: `dispatch` 121-142. It runs an `if` for `TransitionFired` at 126-131, a dedupe on the entry id at 133-135, then a `switch` at 137-141 whose lifecycle kinds go to `onChange` (138-139) and whose `default` throws (140). `dispatchTransitionFired` 164-206 checks the stamp (166-178), starts the timers through `timersStartedBy` (182-184), then runs the actions by index with the `(entryId, actionIndex)` redelivery key (`ActionKey`, 94; loop 190-205). There is no recompute call; P5a removed it (javadoc 72). Kind-routing javadoc: 66-77.
- `InProcessTimerScheduler.java`: `sweep` 101-113 rebuilds the entry with `TimerScheduledEntry.of` (106-107) and calls `onTimerElapsed` (108).
- `package-info.java:10` mentions "already-dispatched `OutboxEntry` ids".

Temporal adapter (`sequeless-adapter-automation-temporal/{main}/.../temporal/`):

- `TemporalAutomationPort.java`: `dispatch` 88-99, a `switch` at 93-98 whose lifecycle case goes to `startChangeEventWorkflow` (94-95), with `TransitionFired` at 96 and a `default` that throws at 97. `startChangeEventWorkflow` 101-109 sets the workflow id to `entry.id()`. `startTransitionFiredActions` 134-179: stamp check 135-148; `TimerWorkflow` per timer, input `TimerScheduledEntry.of(...)` (152-162); `TransitionActionWorkflow` per action, id `entryId:index`, input `entry` (169-178). Javadoc 22-30 and 39-42 still describe "recompute then `onChange`".
- `ChangeEventWorkflow.java`: `run(OutboxEntry, Scope)` at 46; javadoc 9-32 and 37-44 describe recompute-then-onChange.
- `ChangeEventWorkflowImpl.java`: `run` 15-24; `activities.recompute(scope, entry)` at **22** (the spec's "line 23" is stale); `activities.onChange` 23.
- `TriggerActivities.java`: `recompute` 21-23, `onChange` 25-27, `onTimerElapsed` 29-31. `TriggerActivitiesImpl.java`: constructor 26-30, which takes a `DerivationRecomputer`; `recompute` 32-35.
- `TemporalAutomationAutoConfiguration.java`: the `DerivationRecomputer` import (14), javadoc (129) and parameter (143); `new TriggerActivitiesImpl(triggerEvaluator, derivationRecomputer)` at 154.
- `TransitionActionWorkflow.java:25` is `execute(OutboxEntry, int)`. `ActionActivities.java:36` is `applyTransitionAction(Scope, OutboxEntry, int)`.
- `TimerWorkflow.java:38` is `run(OutboxEntry, Scope)`. `TimerWorkflowImpl` carries `Workflow.getVersion("timer-body-p3")` (P3). `TimerWorkflowReplayTest` replays two recorded pre-P3 histories (`src/test/resources/timer-histories/`) whose input is a serialized `OutboxEntry`.

Callers of `dispatch` (`git grep -n '\.dispatch(' -- '*.java'`):

- Main code: only `OutboxRelay.java:124`.
- Tests: `AutomationContract` (testkit main code; 20 call sites, 143-299); `InProcessAutomationPortContractTest:92`; `InProcessAutomationPortTransitionFiredTest` (159, 217, 258, 290, 292, 321); `InProcessAutomationPortWebhookTest` (84, 114); `TemporalAutomationPortContractTest` (158, 344, 362, 373); `TemporalAutomationEndToEndTest:234-235` (package `org.sequeless.app`); `CreateObjectRetryProofTest:143` (package `org.sequeless.app.automation`).
- Kind switches on `OutboxEntry.kind()` in main code: the two adapters (above), the relay's lifecycle check (120), and `DefaultTriggerEvaluator:94` and `DefaultDerivationRecomputer:125` (both checking for a delete). The test wrapper `TemporalAutomationPortContractTest` has its own switch at 159-171.

Tests and guards:

- `AutomationContract.java` (`sequeless-spi-testkit/{main}/testkit/automation/`): the abstract hooks include `recordingDerivationRecomputer()` (102-108), which is used only by the two adapter contract tests. The test builders are `objectChangeEntry` (~317), `objectDeletedEntry` (~335) and `transitionFiredEntry` (~382). There is no test of an unsupported kind.
- `RecordingTriggerEvaluator.java`: `onChange` 60, `onTimerElapsed` 65, `timersStartedBy` 74. `RecordingActionExecutor.java`: 61, 66. `RecordingDerivationRecomputer.java`: used only by the contract tests above and `TemporalAutomationAutoConfigurationTest:62`.
- `InProcessAutomationPortContractTest.dispatchMakesNoRecomputeCallForAnObjectLifecycleEntry` (method at 91; javadoc above it).
- `TemporalAutomationPortContractTest`: worker registration 128; the `port()` wrapper 155-172; `dispatchRunsRecomputeBeforeOnChangeFor{ObjectUpdated,ObjectDeleted}` 353-378 with the helper 387-396; `SwappableDerivationRecomputer` 465-477.
- `WorkflowHolderNullTest:38`; `OutboxEntryDataConverterRoundTripTest`; `TransitionActionWorkflowTest`; `TemporalAutomationPortTransitionFiredTest`.
- `OutboxRelayTest.java`: `RecordingAutomationPort` 266-286; the lambda port at 149; the kind assertion at 118.
- `PostgresOutboxPortIT`: it tests `OutboxPort` only, with no `AutomationPort`. P5b leaves it unchanged; it must still pass (spec line 94).
- `OutboxEntryTest:130-143`: already pins four kinds. No change.
- `ArchitectureTest.java` (`sequeless-app/{test}/app/`): `@AnalyzeClasses(..., DoNotIncludeTests)` at 35, so testkit main classes are imported (`sequeless-spi-testkit` is a test dependency of the app, `pom.xml:108-110`). The model rule is `onlyHostPreparesIndexes` at 254-266 (constants 50-52). `suiteActuallyAnalyzesEveryModule` is at 277.

Docs:

- `docs/architecture/automation.md`: 19-32 ("the adapter's `dispatch()` method is a switch over `kind()`", 32-33); 183; 197; 237-248 (workflow id = `entry.id()`); 267-269; 484-495 (the `ChangeEventWorkflow` recompute paragraph, 493-495); the sequence diagram 510-535 (participant `AutomationPort.dispatch`, 510; `dispatch(scope, entry)`, 526); 539-546 ("Each adapter's `dispatch` switches on `kind()`", 543).
- `docs/architecture/01-overview.md`: the port table row at 105; the interface sketch at 152-154 (`void dispatch(Scope scope, OutboxEntry entry)`).
- `docs/architecture/02-decision-records.md`: DR-26 exists on main (heading at 205; P4). The next free number is DR-27.

What moved (stale citations in the parent docs):

- Spec line 94: the in-process switch is at 137-141 (`dispatch` 121-142), not 119-129. The Temporal switch is at 93-98, not 74-81. `onChange` and `onTimerElapsed` are at 77 and 136, not 73 and 133.
- Spec lines 61 and 334: the `activities.recompute` call is at `ChangeEventWorkflowImpl.java:22` (`run` at 15), not line 23.
- Spec line 60: "recompute call at line 122" no longer exists (P5a).
- Resume plan: the step list the task cites as "section 6" is section 4, lines 58 and 60. The heading of section 6 (line 73) is "Review findings".
- `TriggerEvaluator.java:77` says "three methods above" where there are two (fix it in P5b, because the file changes anyway).

## 3. The design

### 3.1 The ingress (recommended; OQ1 and OQ2 confirm it)

In `sequeless-spi`, package `org.sequeless.spi.automation`:

```java
/** A Store event (GLOSSARY line 81) as automation receives it. The id is the outbox row's id. */
public sealed interface StoreEvent permits ObjectChangedEvent, TransitionFiredEvent {
    UUID id();
    Map<String, Object> payload();
    Instant occurredAt();
}

public record ObjectChangedEvent(UUID id, Change change, Map<String, Object> payload, Instant occurredAt)
        implements StoreEvent {
    public enum Change { CREATED, UPDATED, DELETED }
}

public record TransitionFiredEvent(UUID id, Map<String, Object> payload, Instant occurredAt)
        implements StoreEvent {}

public interface AutomationPort {
    void ingest(Scope scope, StoreEvent event);
}
```

- **Method name:** `ingest`. The spec calls the step "the Store-event ingress" (line 94), and #41 says "`AutomationPort` becomes the Observable ingress" (spec line 148). The port keeps its name, `AutomationPort`, because spec line 44 keeps the port and changes only its method.
- **One method, with a sealed event type.** The GLOSSARY defines one term, Store event, with four cases (line 81). #20 point 4 says the relay "sends each Store event to ... automation's Observable ingress port". A single entry point that takes the sealed type matches both.
- **Two records, not four.** Every consumer treats the three lifecycle kinds alike, apart from the delete check (`DefaultTriggerEvaluator:94`, `DefaultDerivationRecomputer:125`). Both adapters route all three to the same place (in-process 138; Temporal 94). The `Change` enum carries the difference.
- **The records keep the row's payload map unchanged** (OQ2). The inbound ports keep reading the same keys, and the event `id` is the outbox row id. So the in-process dedupe set (`InProcessAutomationPort:87`), the `ActionKey` (94), the Temporal workflow ids (`entry.id()` and `entryId:index`) and `DefaultActionExecutor.derivedCreateId(sourceRow.id(), i)` (189) all keep their values. Typed fields are spec 15's Event type, and #24 point 4 keeps it out of this spec (spec line 338).
- **Validation:** each compact constructor checks for nulls and copies the payload (`Map.copyOf`). The payload was already checked for JSON-compatibility when the `OutboxEntry` was built, so the records do not repeat that check.
- **Who routes by kind.** The relay maps the persisted kind string to the type once, at the boundary where the row is read. This is a package-private `StoreEvents.from(OutboxEntry)` in `org.sequeless.app.automation`, with a `switch` over the four `KIND_` constants and a `default` that throws `IllegalArgumentException`. A throw from the mapper counts as a failed dispatch and dead-letters the row on the third failure (DR-23). The relay claims only `DISPATCHABLE_KINDS`, so the `default` is unreachable in practice.
- **The adapters route by type, except on the action path.** Each one matches on the sealed type with an exhaustive `switch (event) { case ObjectChangedEvent c -> ...; case TransitionFiredEvent t -> ...; }`, with no `default` branch. The compiler proves that every case is handled, and the `IllegalArgumentException` branch is deleted (in-process 140; Temporal 97). This replaces the switch: it does not move it. The relay still matches kind strings where it decodes a row. The adapters use kind strings only on the action path: `ActionExecutor`, `TransitionActionWorkflow` and `ActionActivities` keep `OutboxEntry` (OQ3(a)), so the `TransitionFiredEvent` case rebuilds an `OutboxEntry` with kind `KIND_TRANSITION_FIRED` before it calls them. The rebuild must be lossless: the same id, kind, payload, `occurredAt` and stamp as the row, so the action path gets the input it gets today.
- **Why not one port method per kind** (option (b) in OQ1)? It would push routing into the relay, give the port two entry points, and still leave the relay's string match in place. The single sealed entry point keeps the port a stream ingress, as #20 point 4 describes it.
- **Why the mapper lives in the relay and not in the SPI.** A `StoreEvent.from(OutboxEntry)` would tie the event type to the outbox row, which is the coupling P5b removes. The outbox-to-event mapping belongs to the host relay (#20 point 4: "The relay stays in the host").

### 3.2 What changes inward (decided in OQ3 and OQ4)

Decided (OQ3 option (a), OQ4 option (a)):

| Port method | Input after P5b | Why |
|---|---|---|
| `TriggerEvaluator.onChange` | `ObjectChangedEvent` | Spec lines 45 and 94 |
| `TriggerEvaluator.timersStartedBy` | `TransitionFiredEvent` | It reads a Store event; P3 added it after the spec table |
| `ActionExecutor.applyAction`, `resolveWebhookAction` | `OutboxEntry`: kept, unchanged (OQ3(a)) | Under OQ3(a) only `AutomationPort` and `TriggerEvaluator` take Store events. The adapter rebuilds the entry from the `TransitionFiredEvent` (section 3.1) |
| `TriggerEvaluator.onTimerElapsed` | unchanged: the `TimerScheduled`-shaped `OutboxEntry` | A timer that elapses is not a Store event (#20 point 6; GLOSSARY line 81). Changing it would break `TimerWorkflowReplayTest` (OQ4) |
| `DerivationRecomputer.recompute` | unchanged: `OutboxEntry` | Spec line 47. After P5b the relay is its only caller |

`DefaultTriggerEvaluator.onChange` replaces the kind-string comparison at 94 with `event.change() == Change.DELETED`. Everything else reads `event.payload()` exactly as it reads `changeEvent.payload()` today. `timersStartedBy` changes only its parameter type and the `id()` and `payload()` calls on it.

### 3.3 Each adapter, per event (today, then after P5b)

| Event | In-process today | In-process after | Temporal today | Temporal after |
|---|---|---|---|---|
| `ObjectCreated`, `ObjectUpdated`, `ObjectDeleted` | dedupe on the id (133), then `onChange` (138-139); no recompute (P5a) | the same, on `ObjectChangedEvent` | `ChangeEventWorkflow`, id = entry id; `run` calls `recompute` (22), then `onChange` (23) | `ChangeEventWorkflow.run(ObjectChangedEvent, Scope)` calls only `onChange` |
| `TransitionFired` | stamp check, then timers through `timersStartedBy` and `InProcessTimerScheduler.schedule`, then actions through `ActionExecutor` keyed by `(id, index)` (164-206) | the same, on `TransitionFiredEvent`; the action calls get an `OutboxEntry` rebuilt from it (OQ3(a)) | stamp check, then one `TimerWorkflow` per timer (key `timerKey`, input `TimerScheduledEntry.of`), then one `TransitionActionWorkflow` per action (id `entryId:index`) | the same; `TransitionActionWorkflow` keeps `OutboxEntry` as its input (the adapter rebuilds it from the event); `TimerWorkflow` is unchanged |

The Temporal adapter also applies actions, through `TransitionActionWorkflow` and `ActionActivitiesImpl`. Under OQ3(a) it does so through an `OutboxEntry` rebuilt from the `TransitionFiredEvent`, as the in-process adapter does.

### 3.4 Recompute removal (Temporal)

- `ChangeEventWorkflowImpl.run`: delete line 22. Rewrite the javadoc of `ChangeEventWorkflow` (9-32 and 37-44), since there is no longer any recompute in the workflow, and remove the "Recompute concurrency" paragraph of `TemporalAutomationPort` (39-42).
- `TriggerActivities.recompute` (21-23) and `TriggerActivitiesImpl.recompute` (32-35) are deleted. `TriggerActivitiesImpl` no longer takes a `DerivationRecomputer` (26-30), and `TemporalAutomationAutoConfiguration` drops the parameter (14, 129, 143, 154). `Workflow.getVersion` was already rejected for this change (`remaining-work.md` line 287).
- After this change, no adapter takes a `DerivationRecomputer`. `AutomationContract.recordingDerivationRecomputer()` (102-108) and `RecordingDerivationRecomputer` have no remaining use, so both are deleted with their callers (section 4, items 14-16).

## 4. Change list

Lines are `0b30261`.

SPI (`sequeless-spi`):

1. Add `StoreEvent`, `ObjectChangedEvent` and `TransitionFiredEvent` (section 3.1) in `spi/automation`. They use only JDK types, so no POM change is needed.
2. `AutomationPort.java`: replace `dispatch` (26-47) with `ingest(Scope, StoreEvent)`. Rewrite the class javadoc (6-23): the adapter matches on the sealed type instead of switching on `entry.kind()`. Keep the idempotency clause, keyed on `event.id()` with the `(id, action index)` carve-out. Drop the `IllegalArgumentException` `@throws` (40-42), which is unreachable for a sealed type. Keep the `IllegalStateException` for the stamp.
3. `TriggerEvaluator.java`: `onChange(Scope, ObjectChangedEvent)` (49) and `timersStartedBy(Scope, TransitionFiredEvent)` (87), with their javadoc. Fix line 77 ("three" becomes "two"). `onTimerElapsed` stays as it is (OQ4).
4. `ActionExecutor.java`: UNCHANGED (OQ3(a)). `applyAction` (43) and `resolveWebhookAction` (62) keep `OutboxEntry`. Its javadoc (17-20, 28-36) changes only where it needs to.
5. `package-info.java`: rewrite 27-36 (the inbound ports take a Store event, except `onTimerElapsed`, `recompute` and the `ActionExecutor` methods, which keep `OutboxEntry`) and 45-51 (`ingest`, matching on the sealed type).

Core:

6. `DefaultTriggerEvaluator.java`: the `onChange` signature (77) and the delete check (94), and the `timersStartedBy` signature (171). Javadoc 36-48.
7. `DefaultActionExecutor.java`: UNCHANGED (OQ3(a)). Its parameter types stay `OutboxEntry`. The rebuilt entry carries the row id, so `sourceRow.id()` at 189 is unchanged.

App:

8. `OutboxRelay.java`: add the package-private `StoreEvents.from(OutboxEntry)` (a new file in `app/automation`). The claim handler (118-131) keeps `recompute(scope, entry)` for lifecycle rows (120-123) and then calls `automationPort.ingest(scope, StoreEvents.from(entry))`. `OBJECT_LIFECYCLE_KINDS` stays: the recompute input is still the `OutboxEntry` (spec line 47). Update the javadoc that names `dispatch` (23, 29, 106).
9. `app/automation/package-info.java` 4-13: the relay maps each row to a Store event and feeds the ingress.

In-process adapter:

10. `InProcessAutomationPort.java`: `ingest` replaces 121-142 with a sealed `switch`. The `ObjectChangedEvent` case keeps the dedupe on the id (133-135) and calls `onChange`. The `TransitionFiredEvent` case calls `dispatchTransitionFired` (rename it `applyTransitionFired`), changing the parameter type, and rebuilds an `OutboxEntry` with `KIND_TRANSITION_FIRED` (lossless, section 3.1) for the `ActionExecutor` calls (OQ3(a)). Delete the `default` (140). Javadoc 31-78, especially 37-44 and 66-77.
11. `package-info.java:10`: "event ids". `InProcessTimerScheduler` is unchanged.

Temporal adapter:

12. `TemporalAutomationPort.java`: `ingest` replaces 88-99 with a sealed `switch`. `startChangeEventWorkflow(Scope, ObjectChangedEvent)` and `startTransitionFiredActions(Scope, TransitionFiredEvent)`. The timer start (152-162) still builds `TimerScheduledEntry.of(...)`, using `event.occurredAt()`. The action start (169-178) passes an `OutboxEntry` rebuilt from the event (OQ3(a)). Javadoc 21-58.
13. Workflows and activities: `ChangeEventWorkflow(Impl)` (input `ObjectChangedEvent`; recompute removed, section 3.4); `TriggerActivities(Impl)` (`onChange` input; `recompute` removed; constructor); `TemporalAutomationAutoConfiguration` (no `DerivationRecomputer`). `TransitionActionWorkflow(Impl)` and `ActionActivities(Impl)` are unchanged (OQ3(a)). `TimerWorkflow(Impl)` is unchanged (OQ4(a)).

Testkit:

14. `AutomationContract.java`: `port().ingest(...)` everywhere. The builders return `ObjectChangedEvent` and `TransitionFiredEvent`. The two lifecycle tests become `ingestRoutesObjectChangedToOnChange` (`UPDATED`) and `...DeletedToOnChangeToo` (`DELETED`). Delete the `recordingDerivationRecomputer()` hook (102-108). The null checks (195-203) stay, with the argument renamed to `event`.
15. `RecordingTriggerEvaluator.java`: the new parameter types. `RecordingActionExecutor.java` is unchanged (OQ3(a)). `Call.entryId` becomes `eventId`, or is kept as it is: a code choice.
16. Delete `RecordingDerivationRecomputer.java`, which has no remaining user. Reword the `RecordingTriggerEvaluator` javadoc that names it (line 23).

Adapter tests:

17. `InProcessAutomationPortContractTest`: delete `dispatchMakesNoRecomputeCallForAnObjectLifecycleEntry` (91, with its javadoc) and the hook override (108). `InProcessAutomationPortTransitionFiredTest` and `InProcessAutomationPortWebhookTest`: `ingest` with `TransitionFiredEvent` (call sites in section 2).
18. `TemporalAutomationPortContractTest`: the wrapper (155-172) becomes a sealed `switch` with no `default`. Delete the two recompute-order tests (353-378), the helper (387-396), `SwappableDerivationRecomputer` (465-477) and the hook override (298). Update the `SwappableTriggerEvaluator` signatures. `SwappableActionExecutor` is unchanged (OQ3(a)).
19. `TemporalAutomationAutoConfigurationTest:62`: drop the `DerivationRecomputer` bean. `WorkflowHolderNullTest:38`: an `ObjectChangedEvent`. `TemporalAutomationPortTransitionFiredTest`: it now calls `ingest` with a `TransitionFiredEvent`. `TransitionActionWorkflowTest` is unchanged (OQ3(a)).
20. `OutboxEntryDataConverterRoundTripTest`: add a round-trip for `ObjectChangedEvent` (with `Change`) through `DataConverter.getDefaultInstance()`, because it is the one changed workflow input. `TransitionFiredEvent` is not a workflow input under OQ3(a), so it gets no round-trip here. Keep the `OutboxEntry` case, which covers `TimerWorkflow` and `TransitionActionWorkflow`, since both still take one. The name stays, or becomes `WorkflowInputDataConverterRoundTripTest` (O-13).
21. `TimerWorkflowReplayTest`: unchanged. It is the proof for OQ4(a): it must pass with the `OutboxEntry` input.

App tests:

22. `OutboxRelayTest`: `RecordingAutomationPort` (266-286) records `StoreEvent`s. The lambda port (149) becomes `(scope, event) -> ...`. Assert the type at 118 (`TransitionFiredEvent`). Add one case per lifecycle kind that maps to `ObjectChangedEvent` with the right `Change`, and check that the event id equals the row id. Add a test (`StoreEventsTest` or inside `OutboxRelayTest`) that an unknown kind throws `IllegalArgumentException`.
23. `TemporalAutomationEndToEndTest:234-235` and `CreateObjectRetryProofTest:143`: `ingest` with a `TransitionFiredEvent` built from the committed row's id, payload and `occurredAt`.
24. `ArchitectureTest`: the ingress caller rule (spec line 207), modelled on `onlyHostPreparesIndexes` (254-266):

    ```java
    private static final String RELAY_PACKAGE = "org.sequeless.app.automation..";
    private static final String TESTKIT_AUTOMATION_PACKAGE = "org.sequeless.testkit.automation..";
    private static final String INGEST = "ingest";

    @ArchTest
    static final ArchRule onlyTheRelayFeedsAutomation = noClasses()
            .that().resideOutsideOfPackages(RELAY_PACKAGE, TESTKIT_AUTOMATION_PACKAGE)
            .should().callMethodWhere(target(name(INGEST)).and(target(declaredIn(assignableTo(AutomationPort.class)))))
            .because("...");

    @ArchTest
    static final ArchRule theRelayFeedsAutomation = classes()
            .that().haveFullyQualifiedName("org.sequeless.app.automation.OutboxRelay")
            .should().callMethodWhere(<same predicate>);
    ```

    The testkit exclusion is required, because `AutomationContract` (testkit main code) is imported and calls `ingest`. The second rule satisfies spec line 209 ("a new rule must match at least one class"). The class set of the first rule is never empty, but its predicate matches by name, so the positive rule proves that the predicate matches a real call: if the method is renamed, `theRelayFeedsAutomation` fails instead of the first rule passing vacuously. Test code (`TemporalAutomationEndToEndTest` in `org.sequeless.app`) is outside the import (`DoNotIncludeTests`, line 35).

No change: `PostgresOutboxPortIT`, `PostgresOutboxClaimIT`, `OutboxEntryTest`, `OutboxEntry`, `DerivationRecomputer`, `DefaultDerivationRecomputer`, `ActionExecutor`, `DefaultActionExecutor`, `TransitionActionWorkflow(Impl)`, `ActionActivities(Impl)`, `TransitionActionWorkflowTest`, `TimerScheduledEntry`, `InProcessTimerScheduler`, `TimerWorkflow(Impl)`, every migration, every frozen set, the settling IT, and `sequeless-ui` apart from the UI wording change (section 8).

UI (`sequeless-ui`; the Navigator's decision in section 8):

25a. `sequeless-ui/projects/automation/src/lib/signal-catalogue/signals-panel.ts` line 61: the status text becomes `Signal "{{ name }}" dispatched — the transition is applied when the request is accepted.` (the wording `object-signals.ts` line 51 already uses). `signals-panel.spec.ts` line 111 asserts the old text and changes with it; its header comment at line 8 says "takes effect asynchronously" and changes too.

Docs (in the same PR):

25. `docs/architecture/automation.md`: 19-33, 183, 197, 237-248 (workflow id = event id), 267-269, 493-495 (remove the "`ChangeEventWorkflow` still runs its own recompute" sentence), the sequence diagram 510-535 (`AutomationPort.ingest`; `ingest(scope, event)`), 539-546.
26. `docs/architecture/01-overview.md`: 105 and 152-154 (`void ingest(Scope scope, StoreEvent event); // idempotent by event id`).
27. `docs/architecture/02-decision-records.md`: DR-27 at merge (OQ5(a)). It records the ingress shape, the relay's mapping, and the two deviations: `onTimerElapsed` keeps `OutboxEntry` (OQ4(a)), and `ActionExecutor` and the action workflow keep `OutboxEntry` (OQ3(a)).
28. This plan file, with its decisions block filled in.

POM and dependency analyser: no new dependency. The new SPI types use only `java.*`. `sequeless-app`'s test code already depends on `sequeless-spi` (`AutomationPort`). Every module that imports the new types already depends on `sequeless-spi`. No kernel artifact is added to any module's imports, but `mvn -B -Prelease verify` runs `dependency:analyze-only` (`failOnWarning`; resume plan line 315), so confirm it there.

## 5. Proof and gate

Gate, on every commit (resume plan section 21.6, line 445; JDK 25; the default `java` is 21):

    source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify

Run the full gate only. Partial runs give false failures (`handoff.md`, "Hazards").

Spec line 94's checks, and what each proves:

- `AutomationContract` through `InProcessAutomationPortContractTest` and `TemporalAutomationPortContractTest`: each event type reaches the right inbound port; redelivery is idempotent by event id, and by `(id, index)` for actions; a missing stamp on `TransitionFiredEvent` throws `IllegalStateException`; timers fire only after `advanceTime`.
- `OutboxRelayTest`: rows map to the right event type and `Change`; the event id equals the row id; recompute still runs before `ingest` for lifecycle rows; an unknown kind throws.
- `PostgresOutboxPortIT`: unchanged, and green.
- `ArchitectureTest`: `onlyTheRelayFeedsAutomation` and `theRelayFeedsAutomation`.
- `TimerWorkflowReplayTest`: green (the `TimerWorkflow` input did not change).
- `OutboxEntryDataConverterRoundTripTest`: `ObjectChangedEvent` (with `Change`) round-trips; the `OutboxEntry` inputs of `TimerWorkflow` and `TransitionActionWorkflow` still do.
- UI (item 25a): a wording change in `signals-panel.ts` and its spec. Run the `signals-panel.spec.ts` unit spec with `ng test` in `sequeless-ui` (project `automation`), if `node_modules` is present; record the result in the PR body. The Playwright e2e is not run unless the change reaches a flow it covers.

## 6. Mixed versions, deploy, and the O-4 list

- **No persisted change.** P5b writes no new kind and no new payload key, and it has no migration. `sq_outbox` rows are mapped to events when they are read, so a row written by the old version is read by the new relay without a backfill. The four claimed kinds are the same before and after (`OutboxRelay:51-55`). No outbox drain is needed for P5b beyond the owner's standing count (`handoff.md` line 77).
- **One Temporal workflow changes.** Under OQ3(a) and OQ4(a), `TransitionActionWorkflow` and `TimerWorkflow` keep their `OutboxEntry` input, so they are not on the O-4 list. The list (spec step 3, line 104: "each workflow type whose input P5b changes, named in the P5b PR") has one entry:
  - `ChangeEventWorkflow`: its code changes (the `recompute` activity is removed) and its input changes (`OutboxEntry` becomes `ObjectChangedEvent`). The activity `TriggerActivities.recompute` is removed, and `onChange` changes its input.
- **O-4 result to record in the PR:** the only Temporal state was on dev volumes, and it was cleared on 2026-11-14 (`remaining-work.md` line 15). There are no non-dev environments. The O-4 loss gate covers only workflows outside a dev volume (P3 plan, the decided O-4 item at line 224; spec line 105). At deploy, orchestration still counts running `ChangeEventWorkflow` executions on the dev Temporal server after the old version stops (the only workflow type on the list; the owner's re-count rule covers the types whose code or input the step changes) (`handoff.md` line 77). Under Q-A ("I accept", line 78) any that are listed are terminated before the new version starts (spec step 4, line 105). The owner approves the deploy.
- **No overlap.** An old worker that polls the same task queue could pick up a new-shaped `ChangeEventWorkflow` task and fail to decode it. The stop-then-start order of spec section 3 (lines 100-106) prevents that, as it does for P2 to P4.

## 7. Commits inside the one PR (in order; each runs the full gate)

1. `docs(extraction)`: this plan file. It ships in the code PR (section 1).
2. `refactor(temporal)`: remove `activities.recompute` from `ChangeEventWorkflowImpl`, `TriggerActivities.recompute`, the `DerivationRecomputer` wiring, the two recompute-order tests and `SwappableDerivationRecomputer`; delete `AutomationContract`'s recompute hook, `RecordingDerivationRecomputer` and the in-process "no recompute" test (section 3.4; items 13-19, the recompute parts only). This commit does not depend on the ingress, so it lands first and keeps the second diff smaller.
3. `feat(spi)`: add `StoreEvent`, `ObjectChangedEvent`, `TransitionFiredEvent` and their unit tests (null checks, payload copy, equality). The change is additive and compiles alone.
4. `refactor(automation)`: the cut-over. It cannot be split further, because changing an SPI signature breaks every implementer at once: `AutomationPort.ingest`, the `TriggerEvaluator` inputs, core, the relay and `StoreEvents`, both adapters, the `ChangeEventWorkflow` and `TriggerActivities.onChange` inputs, the testkit, and every test (items 2-3, 5-6, 8-15 and 17-23; the recompute parts are in commit 2).
5. `test(app)`: the ingress caller rules in `ArchitectureTest` (item 24).
6. `fix(ui)`: the `signals-panel` wording and its spec (item 25a). It is separate from the Java commits, so the UI diff reviews alone.
7. `docs(architecture)`: `automation.md`, `01-overview.md`, DR-27 (OQ5(a)), and the decisions block in this file.

The PR body records the O-4 list and result (section 6), the test class names (O-13), and the spec deviations: OQ4(a) (against spec line 94), OQ3(a) (against the spec 5.2 table, line 167), and the stale lines listed in section 2.

## 7a. Risks

1. Temporal JSON shape: `ObjectChangedEvent` must round-trip through the default `DataConverter`, including the nested `Change` enum (item 20). If the round-trip fails, a workflow or activity fails at runtime, not at compile time.
2. `DefaultActionExecutor.derivedCreateId` depends on the event id being the row id. `StoreEvents.from` must pass `entry.id()` through unchanged. A test in item 22 pins this.
3. Deleting `RecordingDerivationRecomputer` removes a public testkit class. Nothing outside this repository uses the prototype testkit (it is not released; spec section 8).

## 8. OPEN QUESTIONS FOR THE NAVIGATOR

Each question stands alone. None is answered by the spec, the resume plan or a recorded decision. Settled, so not asked: whether O-4 holds P5b (answered, section 1); B2 (decided; spec line 366); the naming authority (O-6; spec line 338); the Temporal recompute removal (resume plan line 122); and the OQ17 UI follow-up (in P5b by the decision below; `handoff.md` "Next actions" item 2).

**Decisions (Navigator, recorded 2026-11-25, session 06f6998baaef4a1293463bad7fa0fb07):**
- OQ1 = option (a). One method `ingest(Scope, StoreEvent)`; `StoreEvent` is a sealed interface with two records, `ObjectChangedEvent` (with a `Change` enum CREATED/UPDATED/DELETED) and `TransitionFiredEvent`. Each adapter uses an exhaustive type-pattern `switch` with no `default`. The relay maps each row to the type.
- OQ2 = option (a). The event carries the outbox row's id, payload map and `occurredAt`, unchanged. The event id is the row id; inbound ports keep reading the same payload keys.
- OQ3 = option (a), not the recommendation (b). Only `AutomationPort` and `TriggerEvaluator` take the Store-event types. `ActionExecutor` (`applyAction`, `resolveWebhookAction`), the Temporal action workflow (`TransitionActionWorkflow`) and its activity (`ActionActivities.applyTransitionAction`) keep `OutboxEntry`. The adapters rebuild an `OutboxEntry` with kind `KIND_TRANSITION_FIRED` from the `TransitionFiredEvent` before calling them. The O-4 list is only `ChangeEventWorkflow`.
- OQ4 = option (a). `onTimerElapsed(Scope, OutboxEntry)` and `TimerWorkflow.run(OutboxEntry, Scope)` keep their input. This is a deviation from spec line 94. `TimerWorkflow` is not on the O-4 list. `TimerWorkflowReplayTest` is unchanged.
- OQ5 = option (a). DR-27 is added at merge. It records the ingress shape, the relay's mapping, and the two deviations: `onTimerElapsed` keeps `OutboxEntry` (OQ4), and `ActionExecutor` and the action workflow keep `OutboxEntry` (OQ3).
- UI wording (session c7d77f8543ab4dc390045b4804a054fe): signals-panel.ts line 61, its spec line 111 and the header comment at line 8 change in this PR (set_plan step ui-wording-update, option A).

**OQ1. The shape of the ingress.** Spec 13 P5b (line 94) requires `AutomationPort` to take a Store event, with no `OutboxEntry` in the signature (line 148). Today `dispatch(Scope, OutboxEntry)` is one method, and both adapters switch on the kind string, with a `default` that throws. Options:
(a) One method, `ingest(Scope, StoreEvent)`, where `StoreEvent` is a sealed interface with two records: `ObjectChangedEvent` (with a `Change` enum for created, updated and deleted) and `TransitionFiredEvent`. The relay maps the row to the type, and each adapter uses an exhaustive type-pattern `switch` with no `default`.
(b) Two port methods, `objectChanged(Scope, ObjectChangedEvent)` and `transitionFired(Scope, TransitionFiredEvent)`. The relay routes by kind, and the adapters have no switch at all.
(c) As (a), but with four records, one per GLOSSARY case.
**Recommendation: (a).** It gives one Store-event entry point, as #20 point 4 and GLOSSARY line 81 describe, and the compiler checks the routing. (b) widens the port and still matches strings in the relay. (c) adds two records that every consumer treats the same way as `ObjectChangedEvent`.

Decided: option (a).

**OQ2. What the event carries.** Options:
(a) The row's `id`, `payload` map and `occurredAt`, unchanged, so the event id is the row id and the inbound ports read the same payload keys.
(b) Typed fields (`objectId`, `typeIri`, `ontologyVersion`, `toState`, and so on) that the relay parses. Lifecycle rows written by `DefaultActionExecutor` can lack the stamp (`DefaultActionExecutor.java:305-307`), so a typed version needs a rule for a missing stamp, and the relay would read the stamp, which brings B2 into the ingress (spec line 371).
**Recommendation: (a).** It is the minimal change. Workflow ids, redelivery keys and `derivedCreateId` keep their values. The typed cross-repo Store-event type belongs to spec 15 (#24 point 4; spec line 338).

Decided: option (a).

**OQ3. How far the event type reaches inward.** Spec 5.2 (line 167) lists P5b's SPI change as "`AutomationPort` ingress; `TriggerEvaluator` input types", and does not mention `ActionExecutor`. Today the adapters pass the `TransitionFired` `OutboxEntry` to `ActionExecutor.applyAction` and `resolveWebhookAction`, and Temporal passes it as the input of `TransitionActionWorkflow` and `ActionActivities`. Options:
(a) Only `AutomationPort` and `TriggerEvaluator` change. The adapters rebuild an `OutboxEntry` (with `KIND_TRANSITION_FIRED`) from the event for `ActionExecutor` and `TransitionActionWorkflow`. The O-4 list is then only `ChangeEventWorkflow`.
(b) `ActionExecutor` (with `DefaultActionExecutor`) and the Temporal action workflow and activity also take `TransitionFiredEvent`. `TransitionActionWorkflow` joins the O-4 list.
**Recommendation: (b).** Under (a) the adapters write kind strings again, which brings back the coupling that P5b removes. The O-4 cost is nil: the only Temporal state was on dev volumes and it was cleared, and the deploy re-count covers the added type. The PR records the departure from the spec 5.2 table.

Decided: option (a).

**OQ4. `onTimerElapsed`'s input.** Spec line 94 says "`onChange` (73) and `onTimerElapsed` (133) take the ingress event". Since P3, an elapsed timer is not an outbox row. Its input is a `TimerScheduled`-shaped `OutboxEntry` that is never persisted (`TimerScheduledEntry`). #20 point 6 says a timer is automation's own concern: automation schedules it from a Store event, and the elapse leads to a guarded Store Command, not to a Store event. `TimerWorkflowReplayTest` replays pre-P3 histories whose `TimerWorkflow` input is a serialized `OutboxEntry`. Options:
(a) Keep `onTimerElapsed(Scope, OutboxEntry)` and `TimerWorkflow.run(OutboxEntry, Scope)` unchanged, and record this as a deviation from spec line 94.
(b) Add a separate timer record (not a Store event) for `onTimerElapsed` and `TimerWorkflow`. `TimerWorkflow` joins the O-4 list, and the pre-P3 replay histories (and their test) are retired.
(c) Make the elapsed timer a `StoreEvent` case. This contradicts GLOSSARY line 81.
**Recommendation: (a).** An elapsed timer is not a Store event, so the spec line conflicts with its own source (#20 point 6). Keeping the input avoids invalidating the P3 replay proof. Spec 15 can retype timers when automation moves out.

Decided: option (a).

**OQ5. A decision record.** Spec section 10 says "No ADR is added here" (line 294). P2, P3 and P4 each still added a DR-file entry (DR-24, DR-25 and DR-26; DR-26 is at `02-decision-records.md:205`). P5b changes a port signature listed in `01-overview.md` (105, 152-154). Options:
(a) Add DR-27 at merge: the ingress shape, the relay's mapping, and the `onTimerElapsed` deviation.
(b) No DR: update `automation.md` and `01-overview.md` only.
**Recommendation: (a)**, to keep the P2 to P4 pattern. The port and the spec deviation (OQ4) are worth a dated record.

Decided: option (a).

Code choices left to the implementer (not questions): the `Call.entryId` rename in the recorders; the `StoreEvents` file name; whether `dispatchTransitionFired` is renamed.
