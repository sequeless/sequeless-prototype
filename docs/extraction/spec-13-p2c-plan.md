# Spec 13 P2c: Temporal per-action path for TransitionFired (dormant)

Status: plan rev 6. Opus read-only review of rev 5 returned APPROVE with six one-line fixes and wording notes; rev 6 applies them. This is the version to implement. No code before this plan is committed.
Base: origin/main fbea272 (P2a #159 and P2b #160 merged). Issue #41. Resume plan: `docs/extraction/resume-plan-spec13.md` section 17.

## Decisions (navigator session 48d53ab8, recorded)

- **D1, shape A.** One Temporal activity per action: `ActionActivities.applyTransitionAction(Scope, OutboxEntry transitionFired, int actionIndex)`. The activity resolves the action and applies it, or resolves a webhook and delivers it, in one call. Nothing else crosses an activity boundary: no `Action` value and no `ResolvedWebhookRequest`. This amends resume-plan section 17's "resolve activity, then execute activity" shape. The PR body must record the amendment.
- **D2, count and fail-fast check in the port.** `TemporalAutomationPort.startTransitionFiredActions(Scope, OutboxEntry)` (package-private, dormant) reads the automation through `AutomationSource`, checks the stamp, and starts one workflow per action index. A failed stamp check there throws `IllegalStateException` before any workflow starts. That is subject to DR-23 in the relay.
- **D3, activity-level stamp check is non-retryable.** Inside `applyTransitionAction`, a missing, non-Number or mismatched `ontologyVersion` throws `ApplicationFailure.newNonRetryableFailure(message, type, ...)` (temporal-sdk 1.39.0 has this signature). The message names the row id, the expected stamp and the actual version. `buildActivityOptions` does not change. The row is already processed by then (`PostgresOutboxPort` lines 99-105), so no dead-letter applies. The failure is visible only in Temporal history.
- **D4, Scope is rebuilt from the payload.** `TransitionActionWorkflowImpl` rebuilds `Scope` from `tenantId`/`principalId` with a private `scopeFrom` copied from `ActionWorkflowImpl` (D5 forbids changing that class). The workflow input is `(OutboxEntry, int)`, which is Jackson-safe (`OutboxEntry` round trip is tested).
- **D5, workflow identity.** `TransitionActionWorkflow` (new interface) with `@WorkflowMethod execute(OutboxEntry entry, int actionIndex)`. Workflow id `"<entryId>:<index>"` (C6), through the existing `workflowOptions` helper, so it uses `REJECT_DUPLICATE`. `ActionWorkflow` and `ActionWorkflowImpl` are not changed.
- **D6, activity options.** The new workflow impl reads `ActionWorkflowImpl.ACTIVITY_OPTIONS`, with the same null check as `SignalEventWorkflowImpl` (lines 13-18). It does not add a new static field and does not add a new assignment in the autoconfig.
- **D7, AutomationSource is required.** `automationPort` and `workerFactory` take an `AutomationSource` parameter, injected eagerly (not `@Lazy`). The running app has the bean: `application.yaml` line 15 sets `ontology.adapter: jena`, and `JenaOntologyAutoConfiguration` (lines 62-63) provides a `JenaOntologyPort` that implements `AutomationSource`. The two Temporal end-to-end tests keep that yaml and run in the full gate, so they cover startup.
- **D8, driver choices (not navigator-decided):**
  - Zero actions. `Automation.actions` returns `List.of()` for an unknown `TransitionRef` (`getOrDefault`). The port then starts nothing and does not throw.
  - Index past the end of the list. The port cannot produce it, because it loops over `0..size-1`. The activity can see it only if the list changed under the same stamp, so it throws the same non-retryable failure as D3.
- **D9, SwappableActionExecutor is not extended in 2c.** Nothing in 2c calls `applyAction` or `resolveWebhookAction` through the contract test. The delegation is added in 2d, when the flip needs it.

## Reviewer findings adopted (Opus reviews of rev 1 to rev 5)

1. Live wiring changes. `automationPort` and `workerFactory` gain `AutomationSource` (D7). `TemporalAutomationPort.dispatch` is unchanged. The constructor gains a third parameter.
2. Missing and non-Number stamps are checked at both sites (D2, D3), with a test for each site (see Tests).
3. POM: `sequeless-adapter-automation-temporal/pom.xml` declares neither `automation-contract` nor `schema-contract`. Add both, mirroring `sequeless-adapter-automation-inprocess/pom.xml` lines 28-35 (no `<version>`; the sequeless-bom 0.1.5 import manages them). Without them the dependency analysis (`failOnWarning`) fails the gate, as it did twice in 2b. Also update the comment on the `temporal-testing` dependency in that pom, which names only the contract test; name the new test classes too.
4. Contract test (`TemporalAutomationPortContractTest`): the constructor calls at lines 129 and 133 change to the new signatures. The worker registration at lines 123-127 adds `TransitionActionWorkflowImpl`. The contract test uses the shared `SwappableAutomationSource` double (below).
5. `ActionActivities` javadoc: "five methods: four ActionRequest activities plus `applyTransitionAction`" (see the wording list).
6. Dormancy test, a new `@Test` named `dispatchDoesNotStartTransitionActionWorkflows` in `TemporalAutomationPortContractTest`. It does NOT reuse `AutomationContract.dispatchAcceptsTransitionFiredAsANoOp` (sequeless-spi-testkit lines 169-185), because that entry has no `ontologyVersion` and no `machineIri`, and its id is local to the method. The new test:
   - builds a `TransitionFired` entry with a fixed id, stamped at version N, with `machineIri`, `fromState` and `transitionName`;
   - sets `SwappableAutomationSource` to an `Automation` at version N that has at least one action for that `TransitionRef`;
   - calls `port.dispatch(SCOPE, entry)`;
   - asserts that `env.getWorkflowClient().newUntypedWorkflowStub(id + ":0").describe()` throws `WorkflowNotFoundException`.
   A copy of the in-process test's empty `new Automation(new OntologyVersion(1), Map.of())` would pass even after the 2d flip, so it must not be used.
7. Passing `Scope` into the workflow is not adopted. It conflicts with D4.

## Activity check order (blocking, reviewer rev 3)

`ActionActivitiesImpl.applyTransitionAction` runs its steps in this order, on every attempt:
1. `automationSource.automation(scope.tenantId())`, always first. Each attempt therefore reads the automation once, so the attempt counter in the tests is exact.
2. The stamp checks: a missing or non-Number `ontologyVersion`, then a version mismatch. Both throw the non-retryable failure (D3).
3. The index check (D8).
4. `applyAction` for a non-webhook action, or `resolveWebhookAction` plus the private `deliver` for a webhook.

This is the order of `InProcessAutomationPort.dispatchTransitionFired` (lines 176-193). An implementer who checks the stamp before reading the automation gets a count of 0 on every attempt and cannot test retries.

## Rejected claim (recorded with the navigator)

Under option A, a stamp drift could end in a DR-23 dead-letter. It cannot. DR-23 counts failures of the relay's own dispatch: the relay calls dispatch at `OutboxRelay` line 132, inside the claim handler, and the failure count is kept in `PostgresOutboxPort.recordFailure` (lines 133-146, threshold 3). The row is marked processed in the same claim transaction once dispatch returns (`PostgresOutboxPort` lines 99-105: the handler call at 99, the `processed_at` UPDATE at 101-105). So a failure inside a later activity is visible only in Temporal history. The PR body must say this.

## Files

Main code, `sequeless-adapter-automation-temporal`:
- `pom.xml`: add `automation-contract` and `schema-contract` (item 3), and update the `temporal-testing` comment.
- `ActionActivities.java`: add `applyTransitionAction`; rewrite the javadoc and rationale to "five methods" (item 5). Add `@param`s where needed.
- `ActionActivitiesImpl.java`: constructor `(ActionExecutor actionExecutor, AutomationSource automationSource)`, with `requireNonNull` and `@param` for each. Implement `applyTransitionAction` in the check order above. Fix the class javadoc (line 17).
- `TransitionActionWorkflow.java` (new): `@WorkflowInterface`, `@WorkflowMethod execute(OutboxEntry entry, int actionIndex)`.
- `TransitionActionWorkflowImpl.java` (new): `public`, with the implicit no-arg constructor (Temporal creates it by reflection). It has the ACTIVITY_OPTIONS null check (D6), a private `scopeFrom` (D4), `Workflow.newActivityStub(ActionActivities.class, activityOptions)`, then `applyTransitionAction(scope, entry, actionIndex)`.
- `TemporalAutomationPort.java`: constructor `(WorkflowClient workflowClient, String taskQueue, AutomationSource automationSource)`, each with `requireNonNull`, and `@param`. Add package-private `startTransitionFiredActions(Scope scope, OutboxEntry entry)`. Its logic (D2 and D8):
  1. `Automation automation = automationSource.automation(scope.tenantId())`.
  2. Missing or non-Number `ontologyVersion`: throw `IllegalStateException`, with a message naming the row id.
  3. Version mismatch: throw `IllegalStateException`, with a message naming the row id, the active version and the row's stamp.
  4. `TransitionRef` from `machineIri`, `fromState` and `transitionName`.
  5. For `i` in `0..actions.size()-1`: start `TransitionActionWorkflow` with id `entryId + ":" + i` through `workflowOptions`. Catch `WorkflowExecutionAlreadyStarted` **inside the loop**, for each index, and continue. One catch around the whole loop would skip later indices after a partial start.
  Use `InProcessAutomationPort.dispatchTransitionFired` (lines 174-193) as the template. `dispatch` is unchanged, and `KIND_TRANSITION_FIRED` stays `{}` at line 82. Update the class javadoc.
- `TemporalAutomationAutoConfiguration.java`: `automationPort(WorkflowClient, TemporalAutomationProperties, AutomationSource)`. `workerFactory` gains `AutomationSource` (its position does not matter; Spring injects by type), registers `TransitionActionWorkflowImpl`, and builds `new ActionActivitiesImpl(actionExecutor, automationSource)`. Add `@param` to both bean methods.
- Wording fixes (the target text is given for each):
  - `ActionWorkflow.java` line 25: "ActionActivities' four methods" becomes "the four ActionRequest activity methods of ActionActivities; `applyTransitionAction` is the fifth".
  - `TemporalAutomationProperties.java` line 65: "applied uniformly to all four ActionActivities" becomes "applied uniformly to all five ActionActivities". Line 66 already begins with "methods", so the phrase reads "all five ActionActivities methods" across the two lines. Do not write "methods methods".
  - `ActionActivitiesImpl.java` line 17: "three of its four methods" becomes "three of its four ActionRequest methods".
  - `package-info.java` line 8: use the `ActionWorkflow.java` line 25 form: "the four ActionRequest activity methods of ActionActivities; `applyTransitionAction` is the fifth". Do NOT write "exactly one of ... five activities": `ActionWorkflow` still calls exactly one of the four ActionRequest methods.
  - `docs/architecture/automation.md` line 204: the same line-25 form.
  - `docs/architecture/automation.md` line 214: "all five activity methods".
  - `docs/architecture/automation.md` line 119: "ActionExecutor's four methods" becomes "ActionExecutor's four ActionRequest methods". `ActionExecutor` has four abstract methods and two defaulted ones (`applyAction`, `resolveWebhookAction`), which take the adapter-built `Action`.
  - In every wording change, `ActionWorkflowImpl` still calls four ActionRequest activities, and the retry options apply to all five activity methods.

Failure messages (the implementer copies these shapes; the row id appears in each; plain string concatenation):
- Activity, missing or non-Number stamp: `ApplicationFailure.newNonRetryableFailure("TransitionFired row <id> has no ontologyVersion stamp", "TransitionFiredStampMissing")`.
- Activity, mismatch: `ApplicationFailure.newNonRetryableFailure("Automation is at ontology version <active> but TransitionFired row <id> was written at version <stamp>", "TransitionFiredStampMismatch")`.
- Activity, index past end: `ApplicationFailure.newNonRetryableFailure("TransitionFired row <id> has no action at index <i>", "TransitionFiredIndexOutOfRange")`.
- Port: the same texts in `IllegalStateException`, without a type string.

Client-side exception chain (for assertions):
- The thrown exception from `getResult` is `WorkflowFailedException`. Its `getCause()` is an `ActivityFailure` with retry state `MAXIMUM_ATTEMPTS_REACHED` (control case) or `NON_RETRYABLE_FAILURE` (stamp cases). Its `getCause()` is an `ApplicationFailure`.
- Stamp cases: that `ApplicationFailure` has the type string from above and `isNonRetryable()` true. Assert the message with `hasMessageContaining`. On the client side, `ApplicationFailure.getMessage()` returns `message='...', type='...', nonRetryable=true`. An exact-text check needs `getOriginalMessage()`.
- Control case: that `ApplicationFailure` has type `java.lang.RuntimeException` and `isNonRetryable()` false.
- Do NOT assert `isInstanceOf(ActivityFailure.class)` on the exception thrown by `getResult`. It is `WorkflowFailedException`, so the test must unwrap through `getCause()`.

Waiting for a run to close:
- Use the bounded `getResult(5, TimeUnit.SECONDS, Void.class)`, as the contract test's `awaitWorkflowResult` does (lines 182-189). Do NOT use the unbounded `getResult(Void.class)`: a stuck workflow would hang the test.
- Workflow test: start the typed stub, then call the bounded `getResult` on it, or start it and then wait on its untyped stub.
- Port test: the success and partial-start cases wait on each `<entryId>:i` with the bounded `getResult` before they count anything.

Tests, `sequeless-adapter-automation-temporal/src/test`:

Shared doubles, each in its own file (no test file has two top-level classes):
- `SwappableAutomationSource.java` (new, package-private, implements `AutomationSource`). It holds a static `Automation current` and a static `AtomicInteger calls`, which `automation(TenantId)` increments on every call. It has a switch `failEveryCall` that makes it throw a plain `RuntimeException` (used only by the control case). `reset()` clears all three. Every test class that uses it calls `reset()` in `@BeforeEach`. The contract test, the autoconfig test, the workflow test and the port test all use this one class.
- `RecordingTransitionExecutor.java` (new, package-private, implements `ActionExecutor`).
  - It records a `Call(String method, int actionIndex, UUID entryId, Action action)` for `applyAction`, and for `resolveWebhookAction` (with `action` the `WebhookAction`). The accessor is `calls()`, returning an unmodifiable list. The test asserts the recorded `action` equals the `actions.get(i)` it set up, so it proves the action at that index was applied, not only that the index matched.
  - `reset()` clears the recorded calls. It does NOT clear the webhook URL.
  - The webhook URL is set by `setWebhookUrl(String)`, called in `@BeforeAll` after the local server starts. `resolveWebhookAction` returns a `ResolvedWebhookRequest` built on that URL.
  - Its four abstract methods (`applySetProperty`, `applyCreateObject`, `resolveWebhook`, `applyLog`) throw `UnsupportedOperationException`. The workflow test never reaches them.
  - The testkit is not changed.

Per class:
- `TemporalAutomationAutoConfigurationTest.java`: the runner gets `SwappableAutomationSource` as the `AutomationSource` bean. Its `workerFactory` writes `ActionWorkflowImpl.ACTIVITY_OPTIONS` (line 133) inside each `@Test`, through the context, and has no `env`.
- `TemporalAutomationPortContractTest.java`: constructor calls (item 4); worker registration; `SwappableAutomationSource`; the dormancy test (item 6). It keeps `ACTIVITY_OPTIONS` at `maximumAttempts(1)` with `setStartToCloseTimeout(10 s)` (lines 116-119).
- `TransitionActionWorkflowTest.java` (new, `TestWorkflowEnvironment`):
  - `@BeforeAll`: set `ActionWorkflowImpl.ACTIVITY_OPTIONS` with **both** `setStartToCloseTimeout(10 s)` and `setRetryOptions` (`maximumAttempts(3)`, a short initial interval of 10 ms, backoff 1.0). Without the timeout, the 1.39.0 test server refuses the activity ("A valid StartToClose or ScheduleToCloseTimeout is not set"). That fails the workflow task, not the workflow, so it retries forever.
  - `@BeforeAll`: start a local `HttpServer` on `new InetSocketAddress("localhost", 0)`. Serve `/hook`, return 200, and count hits with an `AtomicInteger`. Build the URL from `getAddress().getPort()`, then call `executor.setWebhookUrl(...)`. Build `TestWorkflowEnvironment`, register `TransitionActionWorkflowImpl` and `ActionActivitiesImpl(executor, SwappableAutomationSource)`, then `env.start()`.
  - `@AfterAll`: `env.close()` and `server.stop(0)`, as the contract test does.
  - `@BeforeEach`: reset `SwappableAutomationSource` and `RecordingTransitionExecutor`. Reset does not clear the webhook URL (above).
  - It starts `TransitionActionWorkflow` directly through `env.getWorkflowClient()`. Going through the port would throw first.
  - Per case: non-webhook kinds (SetProperty, CreateObject, Log) are applied by index, and the recorded `action` is the one at that index. Webhook: resolved with the right index, and the `hits` count goes up by one.
  - Stamp failures (mismatch, missing, non-Number) and the index-out-of-range case: the `calls` count is 1 (one attempt, non-retryable), the workflow fails, and the client-side chain is as stated above with `isNonRetryable()` true.
  - Control case: `failEveryCall` is on, so every call throws a plain `RuntimeException`. The activity is retried until the count reaches `maximumAttempts` (3), and the workflow then fails. The chain is as stated above, with type `java.lang.RuntimeException` and `isNonRetryable()` false. This shows the counter really detects retries.
  - Entry builder: the helper `transitionFired(int ontologyVersion)` in `InProcessAutomationPortTransitionFiredTest` (line 174), copied with its payload keys. The OutboxEntry constructor rejects a payload with no String `objectId`. The helper already carries `tenantId` and `principalId`. A missing stamp means omitting the `ontologyVersion` key, because null values are rejected. A non-Number stamp is the String `"1"`.
  - Test actions: `new SetPropertyAction(property, Optional.empty(), Optional.of(expression))`. Do not import `kernel-values`: it is used but undeclared, and `failOnWarning` would fail the gate.
- `TemporalAutomationPortTransitionFiredTest.java` (new, renamed from `TransitionFiredStartTest` to mirror the in-process test).
  - Setup, teardown and the `ACTIVITY_OPTIONS` setting are as in the workflow test, with no `HttpServer`. `RecordingActionExecutor` has no `setWebhookUrl`, and its default URL is `example.invalid`, so the automation for this test must not contain a webhook action.
  - Its executor is the testkit's `RecordingActionExecutor`. It counts `applyAction` calls by filtering `calls()` on `Call.method()` (equal to `METHOD_APPLY_ACTION`) and `Call.entryId()`, not by global `countOf`, because workflows from earlier tests are fire-and-forget and may still record calls.
  - Port stamp checks: missing, non-Number and mismatched stamps each throw `IllegalStateException`, and no workflow is started.
  - Zero actions: nothing is started, and nothing is thrown.
  - Success: one workflow per index with id `<entryId>:<i>`. The automation for this test has at least two actions. Wait on each id with the bounded `getResult` before counting.
  - Partial start: the test builds its own `WorkflowOptions` (id `<entryId>:0`, the same task queue, `REJECT_DUPLICATE`), because the port's `workflowOptions` is private, and starts `<entryId>:0` first. It then calls `startTransitionFiredActions`, and `<entryId>:1` must be started. This checks the per-index catch (port step 5).
  - Redispatch: wait for each first run to close with the bounded `getResult`. Then call `startTransitionFiredActions` again. Show that nothing new started: the run id of each `<entryId>:i`, from `describe().getExecution().getRunId()`, is unchanged, and the filtered `applyAction` count has not changed. A run that is still open rejects a duplicate id, and that holds only with the FAIL conflict policy, which is the default and what the port relies on. The closed wait is required before `REJECT_DUPLICATE` can be shown.

Test isolation: `ActionWorkflowImpl.ACTIVITY_OPTIONS` is a static field. It is written by `TemporalAutomationPortContractTest` (line 116), by `TemporalAutomationAutoConfigurationTest` (through `workerFactory` line 133, inside each `@Test`), and by the two new classes (in `@BeforeAll`). The static `SwappableAutomationSource` holder is shared the same way. Surefire 3.6.0 runs the classes sequentially in one JVM with its defaults, and no parallel setting is in the root pom, the module pom, or `sequeless-build-parent` 0.1.2 (which has no parent). Do not enable parallel test classes in this module.

## Gate and merge

- Full gate, on JDK 25, from the worktree root: `source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify`. A module-only run is not enough, because the dependency analysis runs at the full build.
- One Opus read-only review of the diff before the PR. Then the PR (pre-approved), a squash merge pinned to the reviewed head with `--match-head-commit`, and an update on issue #41.
- Javadoc runs under `-Prelease`: no `{@link}` to private members, and no broken `@param`.
- Files also touched: `docs/architecture/automation.md` (lines 119, 204 and 214, the wording) and `package-info.java` (line 8).

## Out of scope for 2c

- The flip (2d): `dispatch`'s `KIND_TRANSITION_FIRED` case calling `startTransitionFiredActions`. `SwappableActionExecutor` delegation is added then (D9).
- V8 migration, and the owner's index-window decision (ix). Never merge V9 before V8.
- P3, P4, P5b.
