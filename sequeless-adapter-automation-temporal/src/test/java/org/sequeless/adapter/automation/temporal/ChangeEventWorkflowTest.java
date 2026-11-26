package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.automation.AutomationFailure;
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * Covers {@link ChangeEventWorkflowImpl}'s failure path (spec 13, R2) in an in-memory {@link TestWorkflowEnvironment}.
 * The workflow is started directly through the environment's client, so the test reaches the workflow's own catch,
 * which the port's start path never does.
 *
 * <p>The activity's retries are shortened to three attempts, and the {@code onChange} double counts its calls, so an
 * exhausted retry is visible as exactly three calls. A failing record write is covered too, and so is a cancellation:
 * a cancelled workflow must not write a record.
 */
class ChangeEventWorkflowTest {

    private static final String TASK_QUEUE = "sequeless-change-events-test";
    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static ScriptedTriggerEvaluator evaluator;
    private static RecordingAutomationFailureRecorder recorder;
    private static TestWorkflowEnvironment env;

    @BeforeAll
    static void startEnvironment() {
        // Published before env.start(), as TemporalAutomationAutoConfiguration does.
        TemporalActivityOptions.ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofMillis(10))
                        .setBackoffCoefficient(1.0)
                        .build())
                .build();

        evaluator = new ScriptedTriggerEvaluator();
        recorder = new RecordingAutomationFailureRecorder();
        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ChangeEventWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new TriggerActivitiesImpl(evaluator), new AutomationFailureActivitiesImpl(recorder));
        env.start();
    }

    @AfterAll
    static void stopEnvironment() {
        env.close();
    }

    @BeforeEach
    void resetDoubles() {
        evaluator.reset();
        recorder.reset();
    }

    @Test
    void onChangeFailingEveryAttemptEndsTheWorkflowAndRecordsTheFailureOnce() {
        evaluator.failOnChangeWith(new IllegalStateException("trigger evaluation broke"));
        ObjectChangedEvent event = change();

        runToCompletion(event);

        assertThat(evaluator.onChangeCalls()).isEqualTo(3);
        AutomationFailure record = onlyRecord();
        assertThat(record.workflowId()).isEqualTo(event.id().toString());
        assertThat(record.workflowType()).isEqualTo("ChangeEventWorkflow");
        assertThat(record.transition()).isNull();
        assertThat(record.scope().tenantId()).isEqualTo(SCOPE.tenantId());
        assertThat(record.cause()).isEqualTo("java.lang.IllegalStateException: trigger evaluation broke");
    }

    @Test
    void onChangeThatSucceedsEndsTheWorkflowAndRecordsNothing() {
        ObjectChangedEvent event = change();

        runToCompletion(event);

        assertThat(evaluator.onChangeCalls()).isEqualTo(1);
        assertThat(recorder.records()).isEmpty();
    }

    @Test
    void aFailingRecordWriteFailsTheWorkflowInsteadOfEndingItAsSucceeded() {
        evaluator.failOnChangeWith(new IllegalStateException("trigger evaluation broke"));
        recorder.failWith(new IllegalStateException("database unavailable"));
        ObjectChangedEvent event = change();

        Throwable thrown = catchThrowable(() -> startWorkflow(event).getResult(5, TimeUnit.SECONDS, Void.class));

        // The workflow failed on the record activity itself, not on a swallowed or unrelated error.
        assertThat(thrown).isInstanceOf(WorkflowFailedException.class);
        assertThat(thrown.getCause()).isInstanceOf(ActivityFailure.class);
        ActivityFailure recordFailure = (ActivityFailure) thrown.getCause();
        assertThat(recordFailure.getActivityType()).isEqualTo("RecordFailure");
        assertThat(recordFailure.getCause()).isInstanceOf(ApplicationFailure.class);
        assertThat(((ApplicationFailure) recordFailure.getCause()).getOriginalMessage())
                .isEqualTo("database unavailable");
        assertThat(recorder.records()).isEmpty();
    }

    /**
     * A cancellation is not recorded. The workflow tells a cancellation apart by {@link ActivityFailureCauses}, and
     * {@link ActivityFailureCausesTest} covers that predicate directly. This test shows the outcome: no record.
     */
    @Test
    void aCancelledWorkflowIsNotRecorded() throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        evaluator.blockOnChangeUntil(gate);
        ObjectChangedEvent event = change();
        WorkflowStub stub = startWorkflow(event);
        try {
            awaitOnChangeStarted();
            stub.cancel();

            Throwable thrown = catchThrowable(() -> stub.getResult(5, TimeUnit.SECONDS, Void.class));

            assertThat(thrown).isInstanceOf(WorkflowFailedException.class);
            assertThat(thrown).hasRootCauseInstanceOf(CanceledFailure.class);
            assertThat(recorder.records()).isEmpty();
        } finally {
            gate.countDown();
        }
    }

    private static ObjectChangedEvent change() {
        return new ObjectChangedEvent(
                UUID.randomUUID(),
                ObjectChangedEvent.Change.CREATED,
                Map.of("objectId", UUID.randomUUID().toString()),
                OCCURRED_AT);
    }

    /** Starts the {@link ChangeEventWorkflow} for {@code event} without waiting for it. */
    private static WorkflowStub startWorkflow(ObjectChangedEvent event) {
        ChangeEventWorkflow workflow = env.getWorkflowClient()
                .newWorkflowStub(
                        ChangeEventWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(event.id().toString())
                                .setTaskQueue(TASK_QUEUE)
                                .build());
        WorkflowClient.start(workflow::run, event, SCOPE);
        return WorkflowStub.fromTyped(workflow);
    }

    /** Starts the workflow for {@code event} and waits, bounded, for it to complete. */
    private static void runToCompletion(ObjectChangedEvent event) {
        try {
            startWorkflow(event).getResult(5, TimeUnit.SECONDS, Void.class);
        } catch (TimeoutException e) {
            throw new AssertionError("Workflow " + event.id() + " did not close within 5 seconds", e);
        }
    }

    /** Polls, bounded, until the {@code onChange} activity has started, so a cancel lands on a running activity. */
    private static void awaitOnChangeStarted() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (evaluator.onChangeCalls() == 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("onChange did not start within 5 seconds");
            }
            Thread.sleep(10);
        }
    }

    /** The single failure record the recorder holds, asserting that there is exactly one. */
    private static AutomationFailure onlyRecord() {
        List<AutomationFailure> records = recorder.records();
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    /**
     * A {@link TriggerEvaluator} whose {@link #onChange} fails, or blocks, as a test sets it, and counts its calls.
     * The other two methods are not reached by this test and throw if they are.
     */
    private static final class ScriptedTriggerEvaluator implements TriggerEvaluator {

        private final AtomicInteger onChangeCalls = new AtomicInteger();
        private volatile RuntimeException onChangeFailure;
        private volatile CountDownLatch onChangeGate;

        @Override
        public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
            onChangeCalls.incrementAndGet();
            CountDownLatch gate = onChangeGate;
            if (gate != null) {
                try {
                    gate.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            RuntimeException failure = onChangeFailure;
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
            throw new UnsupportedOperationException("not reached by ChangeEventWorkflowTest");
        }

        @Override
        public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
            throw new UnsupportedOperationException("not reached by ChangeEventWorkflowTest");
        }

        int onChangeCalls() {
            return onChangeCalls.get();
        }

        void failOnChangeWith(RuntimeException failure) {
            this.onChangeFailure = failure;
        }

        void blockOnChangeUntil(CountDownLatch gate) {
            this.onChangeGate = gate;
        }

        void reset() {
            onChangeCalls.set(0);
            onChangeFailure = null;
            onChangeGate = null;
        }
    }
}
