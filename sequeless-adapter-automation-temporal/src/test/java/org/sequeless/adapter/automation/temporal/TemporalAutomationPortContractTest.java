package org.sequeless.adapter.automation.temporal;

import com.sun.net.httpserver.HttpServer;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.testkit.automation.AutomationContract;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingDerivationRecomputer;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;

/**
 * Proves {@link TemporalAutomationPort} satisfies every clause of {@link AutomationPort}'s
 * behavioural contract, exercised against a real workflow/activity execution running inside an
 * in-memory {@link TestWorkflowEnvironment} — no live Temporal server, no Testcontainers (out of
 * scope for this task; see {@code TemporalTestcontainersSupport} in {@code sequeless-app} for the
 * pattern a Testcontainers-backed acceptance test uses instead).
 *
 * <p><b>{@code dispatch()} only starts (or signals) a workflow — fire-and-forget — so {@link
 * #port()}'s wrapper must itself decide, per kind, how to wait for the effect under test to have
 * actually happened before the contract's assertions run.</b> For {@code ActionRequest} and every
 * kind that starts a workflow expected to run to completion quickly ({@code ObjectCreated}/{@code
 * ObjectUpdated}/{@code ObjectDeleted} via {@link ChangeEventWorkflow}, {@code SignalReceived} via
 * {@link SignalEventWorkflow}), the wrapper blocks on {@link WorkflowStub#getResult} for the exact
 * workflow id {@link TemporalAutomationPort#dispatch} started, exactly as the pre-existing {@code
 * ActionWorkflow} wrapper already did (see the history of this file for why polling a recorder
 * instead of blocking on the workflow result is a genuine race, not just a style choice).
 *
 * <p><b>{@code TimerScheduled} is different: its workflow is not supposed to complete quickly.</b>
 * {@link TimerWorkflow} stays parked in {@code Workflow.await} until either the configured
 * duration elapses (via {@link #advanceTime}) or a {@code cancel} signal arrives, so blocking on
 * its result here would deadlock. Instead, the wrapper polls the started {@link TimerWorkflow}'s
 * {@link TimerWorkflow#isAwaiting()} query (a bounded, short, real-time retry loop — this is test
 * synchronization scaffolding, not the workflow's own logic) until it reports {@code true} before
 * returning. That is what makes {@link #advanceTime} safe to call immediately afterwards: by
 * construction, the workflow has already reached its {@code Workflow.await} call, so skipping
 * {@link TestWorkflowEnvironment} time forward cannot race the workflow's own startup.
 *
 * <p><b>{@code TimerCancelled} also needs a synchronization point.</b> Signalling {@code cancel()}
 * only records the signal event; the actual re-evaluation of {@code Workflow.await}'s condition
 * (and the workflow's consequent completion, since it exits without firing) happens on the next
 * workflow task, asynchronously with respect to the signal call returning. The wrapper blocks on
 * {@link WorkflowStub#getResult} for the timer's workflow id (bounded, short timeout, swallowing
 * both a timeout and {@link WorkflowNotFoundException}) so that, by the time {@link #advanceTime}
 * runs next, a genuine cancellation has already taken deterministic effect — never racing the
 * subsequent time-skip.
 */
class TemporalAutomationPortContractTest extends AutomationContract {

    private static final String TASK_QUEUE = "sequeless-actions-test";

    private static HttpServer webhookServer;
    private static String webhookUrl;
    private static TestWorkflowEnvironment env;
    private static SwappableActionExecutor executorHolder;
    private static SwappableTriggerEvaluator triggerEvaluatorHolder;
    private static SwappableDerivationRecomputer derivationRecomputerHolder;
    private static AutomationPort port;

    private RecordingActionExecutor recorder;
    private RecordingTriggerEvaluator triggerRecorder;
    private RecordingDerivationRecomputer recomputerRecorder;

    /**
     * Every {@code timerKey} a {@code TimerScheduled} entry was dispatched for during the current
     * {@code @Test} method (JUnit 5's default per-method instance lifecycle gives this a fresh,
     * empty list per test, exactly like {@link #recorder} and its siblings). {@link #advanceTime}
     * uses this to know which {@link TimerWorkflow} executions might have just become due, and
     * blocks on each one settling before returning — see {@link #advanceTime}'s own javadoc for why
     * {@link TestWorkflowEnvironment#sleep} alone is not sufficient for the contract's
     * synchronicity requirement.
     */
    private final List<String> dispatchedTimerKeys = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startEnvironment() throws IOException {
        webhookServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        webhookServer.createContext(
            "/hook",
            exchange -> {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
        webhookServer.start();
        webhookUrl = "http://localhost:" + webhookServer.getAddress().getPort() + "/hook";

        executorHolder = new SwappableActionExecutor();
        triggerEvaluatorHolder = new SwappableTriggerEvaluator();
        derivationRecomputerHolder = new SwappableDerivationRecomputer();

        // Published before env.start(), mirroring TemporalAutomationAutoConfiguration's own
        // ordering requirement (see ActionWorkflowImpl's javadoc for why this must happen first).
        ActionWorkflowImpl.ACTIVITY_OPTIONS =
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                .build();

        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
            ActionWorkflowImpl.class,
            ChangeEventWorkflowImpl.class,
            TimerWorkflowImpl.class,
            SignalEventWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            new ActionActivitiesImpl(executorHolder),
            new TriggerActivitiesImpl(triggerEvaluatorHolder, derivationRecomputerHolder));
        env.start();

        port = new TemporalAutomationPort(env.getWorkflowClient(), TASK_QUEUE);
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
        recomputerRecorder = new RecordingDerivationRecomputer();
        derivationRecomputerHolder.setDelegate(recomputerRecorder);
    }

    @Override
    protected AutomationPort port() {
        return (scope, entry) -> {
            port.dispatch(scope, entry);
            switch (entry.kind()) {
                case OutboxEntry.KIND_ACTION_REQUEST,
                    OutboxEntry.KIND_OBJECT_CREATED,
                    OutboxEntry.KIND_OBJECT_UPDATED,
                    OutboxEntry.KIND_OBJECT_DELETED,
                    OutboxEntry.KIND_SIGNAL_RECEIVED -> awaitWorkflowResult(entry.id().toString());
                case OutboxEntry.KIND_TIMER_SCHEDULED -> {
                    String timerKey = (String) entry.payload().get("timerKey");
                    awaitTimerWorkflowParked(timerKey);
                    dispatchedTimerKeys.add(timerKey);
                }
                case OutboxEntry.KIND_TIMER_CANCELLED -> awaitCancellationTakesEffect(
                    (String) entry.payload().get("timerKey"));
                default -> {
                    // TransitionFired (and anything else): dispatch() itself already threw
                    // synchronously, nothing further to wait for.
                }
            }
        };
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
        throw new AssertionError("Timer workflow " + timerKey + " never reached Workflow.await within 5 seconds");
    }

    /**
     * Blocks until the {@link TimerWorkflow} named {@code timerKey} has closed as a result of a
     * {@code cancel()} signal just sent to it, so a subsequent {@link #advanceTime} call cannot
     * race the cancellation taking effect. Swallows both a bounded timeout and {@link
     * WorkflowNotFoundException} — {@code TemporalAutomationPort.dispatch}'s own cancel-signal path
     * already tolerates a nonexistent workflow as a legitimate no-op, and this method must too.
     */
    private static void awaitCancellationTakesEffect(String timerKey) {
        WorkflowStub stub = env.getWorkflowClient().newUntypedWorkflowStub(timerKey);
        try {
            stub.getResult(5, TimeUnit.SECONDS, Void.class);
        } catch (TimeoutException e) {
            throw new AssertionError("Timer workflow " + timerKey + " did not close within 5 seconds", e);
        } catch (WorkflowNotFoundException e) {
            // Idempotent no-op: nothing was there to cancel.
        }
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
     */
    @Override
    protected void advanceTime(Duration by) {
        env.sleep(by);
        for (String timerKey : dispatchedTimerKeys) {
            awaitTimerWorkflowSettledIfDue(timerKey);
        }
    }

    /**
     * Blocks briefly on {@code timerKey}'s {@link TimerWorkflow} completing. A timer not yet due by
     * this point in the test (a scenario this contract does not currently exercise, since every
     * test's {@link #advanceTime} call advances by at least the full configured duration) would
     * make this method wait out its bounded timeout and swallow it as "not settled yet" rather than
     * fail — this method proves a due timer's effects are visible, it does not assert that every
     * tracked timer is due.
     */
    private static void awaitTimerWorkflowSettledIfDue(String timerKey) {
        WorkflowStub stub = env.getWorkflowClient().newUntypedWorkflowStub(timerKey);
        try {
            stub.getResult(2, TimeUnit.SECONDS, Void.class);
        } catch (TimeoutException e) {
            // Not due yet (or already observed/settled by an earlier advanceTime call in this same
            // test) - not this method's concern; the test's own assertions decide correctness.
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

    @Override
    protected RecordingDerivationRecomputer recordingDerivationRecomputer() {
        return recomputerRecorder;
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
        public void applySetProperty(Scope scope, OutboxEntry entry) {
            delegate.applySetProperty(scope, entry);
        }

        @Override
        public void applyCreateObject(Scope scope, OutboxEntry entry) {
            delegate.applyCreateObject(scope, entry);
        }

        @Override
        public ResolvedWebhookRequest resolveWebhook(Scope scope, OutboxEntry entry) {
            return delegate.resolveWebhook(scope, entry);
        }

        @Override
        public void applyLog(Scope scope, OutboxEntry entry) {
            delegate.applyLog(scope, entry);
        }
    }

    /** Same swap-the-delegate indirection as {@link SwappableActionExecutor}, for {@link TriggerEvaluator}. */
    private static final class SwappableTriggerEvaluator implements TriggerEvaluator {

        private volatile TriggerEvaluator delegate;

        void setDelegate(TriggerEvaluator delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onChange(Scope scope, OutboxEntry changeEvent) {
            delegate.onChange(scope, changeEvent);
        }

        @Override
        public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
            delegate.onTimerElapsed(scope, timerScheduled);
        }

        @Override
        public void onSignal(Scope scope, OutboxEntry signalReceived) {
            delegate.onSignal(scope, signalReceived);
        }
    }

    /** Same swap-the-delegate indirection as {@link SwappableActionExecutor}, for {@link DerivationRecomputer}. */
    private static final class SwappableDerivationRecomputer implements DerivationRecomputer {

        private volatile DerivationRecomputer delegate;

        void setDelegate(DerivationRecomputer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void recompute(Scope scope, OutboxEntry changeEvent) {
            delegate.recompute(scope, changeEvent);
        }
    }
}
