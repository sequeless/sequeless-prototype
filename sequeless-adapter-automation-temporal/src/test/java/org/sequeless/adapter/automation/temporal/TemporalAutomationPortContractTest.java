package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpServer;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.testkit.automation.AutomationContract;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;

/**
 * Proves {@link TemporalAutomationPort} satisfies every clause of {@link AutomationPort}'s
 * behavioural contract, exercised against a real workflow/activity execution running inside an
 * in-memory {@link TestWorkflowEnvironment} — no live Temporal server, no Testcontainers (out of
 * scope for this task; see {@code TemporalTestcontainersSupport} in {@code sequeless-app} for the
 * pattern a Testcontainers-backed acceptance test uses instead).
 *
 * <p><b>{@code ingest()} only starts a workflow — fire-and-forget — so {@link #port()}'s wrapper must
 * itself decide, per event type, how to wait for the effect under test to have actually happened before
 * the contract's assertions run.</b> For a {@link TransitionFiredEvent},
 * {@link TemporalAutomationPort#ingest} starts one {@link TransitionActionWorkflow} per action of
 * the transition, with workflow id {@code <event id>:<action index>}, and the wrapper blocks on each
 * of those ids in turn. For every event type that starts a workflow expected to run to completion quickly
 * ({@link ObjectChangedEvent}, via {@link ChangeEventWorkflow}), the wrapper blocks on {@link
 * WorkflowStub#getResult} for the exact workflow id {@link TemporalAutomationPort#ingest} started
 * (see the history of this file for why polling a recorder instead of blocking on the workflow result
 * is a genuine race, not just a style choice).
 *
 * <p><b>A {@code TransitionFired} row also starts its timers, and a timer is different: its workflow is
 * not supposed to complete quickly.</b> {@link TimerWorkflow} stays parked in {@code Workflow.sleep} until
 * its duration elapses (via {@link #advanceTime}), so blocking on its result here would deadlock. Instead,
 * the wrapper polls each started {@link TimerWorkflow}'s {@link TimerWorkflow#isAwaiting()} query (a
 * bounded, short, real-time retry loop — this is test synchronization scaffolding, not the workflow's own
 * logic) until it reports {@code true} before returning. That is what makes {@link #advanceTime} safe to
 * call immediately afterwards: by construction, the workflow has already reached its wait, so skipping
 * {@link TestWorkflowEnvironment} time forward cannot race the workflow's own startup. No timer is
 * cancelled after P3, so there is no cancel wait.
 */
class TemporalAutomationPortContractTest extends AutomationContract {

    private static final String TASK_QUEUE = "sequeless-actions-test";

    private static HttpServer webhookServer;
    private static String webhookUrl;
    private static TestWorkflowEnvironment env;
    private static SwappableActionExecutor executorHolder;
    private static SwappableTriggerEvaluator triggerEvaluatorHolder;
    private static SwappableAutomationSource automationSource;
    private static AutomationPort port;

    private RecordingActionExecutor recorder;
    private RecordingTriggerEvaluator triggerRecorder;

    /**
     * The due instant, in {@link TestWorkflowEnvironment#currentTimeMillis()} terms, of every timer a
     * {@code TransitionFired} row started during the current {@code @Test} method, keyed by its {@code
     * timerKey} (JUnit 5's default per-method instance lifecycle gives this a fresh, empty map per test,
     * exactly like {@link #recorder} and its siblings). An instant is recorded once, on the first
     * ingest, so a redelivered row keeps the due instant of its first delivery, as the port does.
     * {@link #advanceTime} settles only the timers whose due instant has passed; see its javadoc.
     */
    private final Map<String, Long> dueAtByTimerKey = new ConcurrentHashMap<>();

    @BeforeAll
    static void startEnvironment() throws IOException {
        webhookServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        webhookServer.createContext("/hook", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        webhookServer.start();
        webhookUrl = "http://localhost:" + webhookServer.getAddress().getPort() + "/hook";

        executorHolder = new SwappableActionExecutor();
        triggerEvaluatorHolder = new SwappableTriggerEvaluator();
        automationSource = new SwappableAutomationSource();

        // Published before env.start(), mirroring TemporalAutomationAutoConfiguration's own
        // ordering requirement (see TemporalActivityOptions' javadoc for why this must happen first).
        TemporalActivityOptions.ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                .build();

        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
                TransitionActionWorkflowImpl.class, ChangeEventWorkflowImpl.class, TimerWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new ActionActivitiesImpl(executorHolder, automationSource),
                new TriggerActivitiesImpl(triggerEvaluatorHolder));
        env.start();

        port = new TemporalAutomationPort(
                env.getWorkflowClient(), TASK_QUEUE, automationSource, triggerEvaluatorHolder);
    }

    @AfterAll
    static void stopEnvironment() {
        env.close();
        webhookServer.stop(0);
    }

    @BeforeEach
    void freshRecorder() {
        recorder = new RecordingActionExecutor(new ResolvedWebhookRequest(webhookUrl, "POST", Optional.empty()));
        executorHolder.setDelegate(recorder);
        triggerRecorder = new RecordingTriggerEvaluator();
        triggerEvaluatorHolder.setDelegate(triggerRecorder);
        SwappableAutomationSource.reset();
    }

    @Override
    protected AutomationPort port() {
        return (scope, event) -> {
            port.ingest(scope, event);
            switch (event) {
                case ObjectChangedEvent changed ->
                    awaitWorkflowResult(changed.id().toString());
                case TransitionFiredEvent fired -> {
                    awaitTransitionFiredTimers(fired);
                    awaitTransitionFiredActions(fired);
                }
            }
        };
    }

    /**
     * Blocks until each {@link TimerWorkflow} that {@link TemporalAutomationPort#ingest} started for
     * {@code event} has reached its wait, and records its due instant. The timers are the ones the port
     * received from {@link TriggerEvaluator#timersStartedBy} for this event, read back from the holder, so the
     * lookup is not made a second time.
     */
    private void awaitTransitionFiredTimers(TransitionFiredEvent event) {
        for (ResolvedTimer timer : triggerEvaluatorHolder.timersResolvedFor(event.id())) {
            awaitTimerWorkflowParked(timer.timerKey());
            dueAtByTimerKey.putIfAbsent(
                    timer.timerKey(), env.currentTimeMillis() + timer.after().toMillis());
        }
    }

    /**
     * Blocks until the workflow named {@code workflowId} is closed — used for every kind whose
     * workflow is expected to run to completion promptly (see this class's own javadoc).
     */
    private static void awaitWorkflowResult(String workflowId) {
        WorkflowStub stub = env.getWorkflowClient().newUntypedWorkflowStub(workflowId);
        try {
            stub.getResult(5, TimeUnit.SECONDS, Void.class);
        } catch (TimeoutException e) {
            throw new AssertionError("Workflow " + workflowId + " did not close within 5 seconds", e);
        }
    }

    /**
     * Blocks until every {@link TransitionActionWorkflow} that {@link TemporalAutomationPort#ingest}
     * started for {@code event} has closed, one per action of the event's transition. The action count
     * comes from the test's automation, read for the transition the event names, as the production path
     * reads it.
     */
    private static void awaitTransitionFiredActions(TransitionFiredEvent event) {
        TransitionRef transition = new TransitionRef(
                (String) event.payload().get("machineIri"),
                (String) event.payload().get("fromState"),
                (String) event.payload().get("transitionName"));
        int actionCount = SwappableAutomationSource.current.actions(transition).size();
        for (int i = 0; i < actionCount; i++) {
            awaitWorkflowResult(event.id() + ":" + i);
        }
    }

    /**
     * Blocks until the {@link TimerWorkflow} named {@code timerKey} reports {@link
     * TimerWorkflow#isAwaiting()}, so a subsequent {@link #advanceTime} call cannot race the
     * workflow's own startup. Bounded, short, real-time polling: this is test synchronization
     * scaffolding, not part of the timer's own deterministic logic.
     */
    private static void awaitTimerWorkflowParked(String timerKey) {
        TimerWorkflow stub = env.getWorkflowClient().newWorkflowStub(TimerWorkflow.class, timerKey);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (stub.isAwaiting()) {
                return;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for timer " + timerKey + " to park", e);
            }
        }
        throw new AssertionError("Timer workflow " + timerKey + " never reached its wait within 5 seconds");
    }

    /**
     * {@link TestWorkflowEnvironment#sleep} advances the environment's virtual clock and processes
     * every workflow-side event that unblocks as a result, but — verified empirically, the failure
     * this comment documents having actually been observed — it does <b>not</b> wait for a
     * consequently-scheduled activity task (here, {@link TriggerActivities#onTimerElapsed}, run by
     * a real background poller thread, not the virtual clock) to have actually finished executing
     * before returning. A first version of this method called only {@code env.sleep(by)}, and
     * {@code timerFiresOnlyAfterAdvanceTime} failed intermittently ("expected 1 but was 0")
     * precisely because the assertion ran before the poller thread had recorded its call on {@link
     * #recordingTriggerEvaluator()}. Blocking on each pending {@link TimerWorkflow}'s own {@link
     * WorkflowStub#getResult} afterwards closes that gap: a {@link TimerWorkflow} cannot complete
     * until its {@code onTimerElapsed} activity call (if any) has actually returned, so once every
     * tracked timer workflow that is due has settled, the contract's synchronicity requirement
     * genuinely holds.
     *
     * <p>Only the timers that are due at the new "now" are settled. A timer not yet due is left alone,
     * because calling {@link WorkflowStub#getResult} on it would let the test environment skip time and
     * fire it early. A timer is due when the due instant recorded for it by {@link #awaitTransitionFiredTimers}
     * is no later than the environment clock after the sleep.
     */
    @Override
    protected void advanceTime(Duration by) {
        env.sleep(by);
        long now = env.currentTimeMillis();
        for (Map.Entry<String, Long> timer : dueAtByTimerKey.entrySet()) {
            if (timer.getValue() <= now) {
                awaitTimerWorkflowSettled(timer.getKey());
            }
        }
    }

    /**
     * Blocks briefly on {@code timerKey}'s {@link TimerWorkflow} completing. Called only for a timer that is
     * due, so the wait ends once its {@code onTimerElapsed} activity has returned. A timeout is swallowed here
     * rather than failed: the test's own assertions on the recording double decide correctness.
     */
    private static void awaitTimerWorkflowSettled(String timerKey) {
        WorkflowStub stub = env.getWorkflowClient().newUntypedWorkflowStub(timerKey);
        try {
            stub.getResult(2, TimeUnit.SECONDS, Void.class);
        } catch (TimeoutException e) {
            // Not settled within the bound (or already observed by an earlier advanceTime call in this
            // same test) - not this method's concern; the test's own assertions decide correctness.
        }
    }

    @Override
    protected RecordingActionExecutor recordingExecutor() {
        return recorder;
    }

    @Override
    protected RecordingTriggerEvaluator recordingTriggerEvaluator() {
        return triggerRecorder;
    }

    /** Serves the actions through {@link SwappableAutomationSource}, the source the activities read from. */
    @Override
    protected void serveAutomationWithActions(Map<TransitionRef, List<Action>> actionsByTransition) {
        SwappableAutomationSource.current = new Automation(new OntologyVersion(1), actionsByTransition);
    }

    /**
     * Ingesting a {@link TransitionFiredEvent} starts the per-action workflows. The one for action
     * index 0 of the event's transition, {@code <event id>:0}, exists straight after {@code ingest}
     * returns, and it runs to completion.
     */
    @Test
    void ingestStartsOnePerActionWorkflowPerAction() {
        UUID entryId = UUID.fromString("7d3f2a10-5b8e-4c4d-9a61-2e0f8b6c1d27");
        TransitionFiredEvent event = new TransitionFiredEvent(
                entryId,
                Map.of(
                        "objectId",
                        UUID.randomUUID().toString(),
                        "tenantId",
                        SCOPE.tenantId().value(),
                        "principalId",
                        SCOPE.principal().id(),
                        "machineIri",
                        "http://example.org/ProjectLifecycle",
                        "fromState",
                        "http://example.org/Draft",
                        "toState",
                        "http://example.org/Active",
                        "transitionName",
                        "activate",
                        "ontologyVersion",
                        1),
                Instant.now());
        SwappableAutomationSource.current = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(
                                "http://example.org/ProjectLifecycle", "http://example.org/Draft", "activate"),
                        List.<Action>of(new LogAction("project activated"))));

        port.ingest(SCOPE, event);

        assertThatCode(() -> env.getWorkflowClient()
                        .newUntypedWorkflowStub(entryId + ":0")
                        .describe())
                .doesNotThrowAnyException();
        awaitWorkflowResult(entryId + ":0");
    }

    /**
     * The fixed {@link ActionExecutor} instance registered with the worker once, at
     * {@code @BeforeAll} time, which simply forwards to whichever {@link RecordingActionExecutor}
     * {@link #freshRecorder} most recently swapped in — see this class's own javadoc (inherited
     * from the pre-T10 version of this file) for why the indirection exists: the expensive {@link
     * TestWorkflowEnvironment}/worker setup is shared across the whole class, but each recording
     * double's counts must not leak between {@code @Test} methods.
     */
    private static final class SwappableActionExecutor implements ActionExecutor {

        private volatile ActionExecutor delegate;

        void setDelegate(ActionExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void applyAction(Scope scope, OutboxEntry transitionFired, Action action, int actionIndex) {
            delegate.applyAction(scope, transitionFired, action, actionIndex);
        }

        @Override
        public ResolvedWebhookRequest resolveWebhookAction(
                Scope scope, OutboxEntry transitionFired, WebhookAction action, int actionIndex) {
            return delegate.resolveWebhookAction(scope, transitionFired, action, actionIndex);
        }
    }

    /**
     * Same swap-the-delegate indirection as {@link SwappableActionExecutor}, for {@link TriggerEvaluator}.
     * {@link #timersStartedBy} delegates to the recording double, which records the call and returns the
     * timers the test set with {@code setTimersStartedBy}. This class also keeps the timers each row
     * resolved to, as the port received them, so {@link #port()} can wait for those timer workflows.
     */
    private static final class SwappableTriggerEvaluator implements TriggerEvaluator {

        private volatile TriggerEvaluator delegate;
        private final Map<UUID, List<ResolvedTimer>> resolvedByRow = new ConcurrentHashMap<>();

        void setDelegate(TriggerEvaluator delegate) {
            this.delegate = delegate;
            resolvedByRow.clear();
        }

        @Override
        public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
            delegate.onChange(scope, changeEvent);
        }

        @Override
        public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
            delegate.onTimerElapsed(scope, timerScheduled);
        }

        @Override
        public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
            List<ResolvedTimer> timers = delegate.timersStartedBy(scope, transitionFired);
            resolvedByRow.put(transitionFired.id(), timers);
            return timers;
        }

        /** @return the timers the port resolved for the {@code TransitionFired} event {@code rowId}, or none */
        List<ResolvedTimer> timersResolvedFor(UUID rowId) {
            return resolvedByRow.getOrDefault(rowId, List.of());
        }
    }
}
