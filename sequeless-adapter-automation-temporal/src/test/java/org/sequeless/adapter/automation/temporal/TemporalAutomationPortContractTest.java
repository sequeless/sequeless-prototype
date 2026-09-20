package org.sequeless.adapter.automation.temporal;

import com.sun.net.httpserver.HttpServer;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.testkit.automation.AutomationContract;
import org.sequeless.testkit.automation.RecordingActionExecutor;

/**
 * Proves {@link TemporalAutomationPort} satisfies every clause of {@link AutomationPort}'s
 * behavioural contract, exercised against a real {@link ActionWorkflow}/{@link ActionActivities}
 * execution running inside an in-memory {@link TestWorkflowEnvironment} — no live Temporal server,
 * no Testcontainers (out of scope for this task; see {@code sequeless-adapter-persistence-postgres}
 * for the pattern a later Testcontainers-backed acceptance test would follow instead).
 *
 * <p>{@link AutomationContract#redispatchWithSameEntryIdIsIdempotent} genuinely exercises {@link
 * io.temporal.client.WorkflowExecutionAlreadyStarted} end to end here: unlike {@code
 * InProcessAutomationPortContractTest}, which relies on its adapter's own hand-rolled {@code
 * Set<UUID>} guard, this test's idempotency guarantee comes entirely from Temporal's workflow-id
 * mechanism, exactly as {@link TemporalAutomationPort}'s own javadoc describes.
 *
 * <p><b>Avoiding T9's webhook-target trap (see that task's recorded finding).</b> {@link
 * AutomationContract}'s default no-arg {@link RecordingActionExecutor} constructor points {@link
 * RecordingActionExecutor#resolveWebhook} at {@code http://example.invalid/hook}, an RFC
 * 2606-reserved, never-resolving host. This adapter's {@link ActionActivitiesImpl#executeWebhook}
 * does perform a real HTTP call (inside the Temporal activity, not inside {@code dispatch} itself),
 * so, like {@code InProcessAutomationPortContractTest}, this class wires {@link #recorder} to a real
 * local {@link HttpServer} that always answers 200.
 *
 * <p><b>{@code dispatch()} only starts the workflow (fire-and-forget) — {@link #port()}'s wrapper
 * blocks on completion instead of polling.</b> An earlier version of this test polled ({@code
 * Awaitility}) for the recorder to show a call for {@code entry.id()}, on the theory that {@code
 * TestWorkflowEnvironment} runs workflows fast enough that this would be equivalent to "wait for
 * completion." That was subtly wrong: the activity call gets <em>recorded</em> fractionally before
 * the workflow execution is marked closed server-side, so a redispatch issued right after the poll
 * observed the recorded call could still race a not-yet-fully-closed first execution. Blocking on
 * {@link WorkflowStub#getResult} for the exact workflow id {@code TemporalAutomationPort.dispatch}
 * started removes that race entirely: by the time {@link #port()}'s wrapper returns, the workflow is
 * unambiguously closed.
 *
 * <p><b>{@link #recorder} is reset fresh per {@code @Test}, unlike {@link #env}/{@link #worker}.</b>
 * The expensive {@link TestWorkflowEnvironment} setup/teardown is shared across the whole class via
 * {@code @BeforeAll}/{@code @AfterAll} — including the one {@link ActionActivitiesImpl} registered
 * with the worker — but a {@link RecordingActionExecutor}'s {@code countOf(...)} assertions (used by
 * several {@link AutomationContract} test methods, e.g. both {@code dispatchRoutesLogToApplyLog} and
 * {@link AutomationContract#redispatchWithSameEntryIdIsIdempotent} count {@code applyLog} calls)
 * count every call ever recorded, with no per-entry scoping; a single recorder instance shared
 * across all eight {@code @Test} methods would let an earlier test's recorded call leak into a
 * later test's count (confirmed empirically: {@code redispatchWithSameEntryIdIsIdempotent} failed
 * with "expected 1 but was 2" only when the full class ran, never in isolation, because an earlier
 * test's own {@code applyLog} call was still sitting in the shared recorder). {@link
 * #SwappableActionExecutor} is the fixed point registered with the worker once; {@link #recorder}
 * swaps out its delegate in {@link #freshRecorder} before every test, exactly mirroring the fresh
 * instance {@code InProcessAutomationPortContractTest} gets for free from JUnit's default
 * per-method test instance lifecycle (an option not available here, since {@code env}/{@code worker}
 * must survive across the whole class).
 */
class TemporalAutomationPortContractTest extends AutomationContract {

    private static final String TASK_QUEUE = "sequeless-actions-test";

    private static HttpServer webhookServer;
    private static String webhookUrl;
    private static TestWorkflowEnvironment env;
    private static SwappableActionExecutor executorHolder;
    private static AutomationPort port;

    private RecordingActionExecutor recorder;

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

        // Published before env.start(), mirroring TemporalAutomationAutoConfiguration's own
        // ordering requirement (see ActionWorkflowImpl's javadoc for why this must happen first).
        ActionWorkflowImpl.ACTIVITY_OPTIONS =
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                .build();

        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ActionWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ActionActivitiesImpl(executorHolder));
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
    }

    @Override
    protected AutomationPort port() {
        return (scope, entry) -> {
            port.dispatch(scope, entry);
            // dispatch() only starts the workflow (fire-and-forget); block until the workflow this
            // entry id names is actually closed before the contract's own assertions run - see this
            // class's own javadoc for why polling the recorder instead of blocking here was a race.
            WorkflowStub stub = env.getWorkflowClient().newUntypedWorkflowStub(entry.id().toString());
            try {
                stub.getResult(5, TimeUnit.SECONDS, Void.class);
            } catch (TimeoutException e) {
                throw new AssertionError(
                    "Workflow for entry " + entry.id() + " did not close within 5 seconds", e);
            }
        };
    }

    @Override
    protected RecordingActionExecutor recordingExecutor() {
        return recorder;
    }

    /**
     * The fixed {@link ActionExecutor} instance registered with the worker once, at
     * {@code @BeforeAll} time, which simply forwards to whichever {@link RecordingActionExecutor}
     * {@link #freshRecorder} most recently swapped in — see this class's own javadoc for why the
     * indirection exists.
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
}
