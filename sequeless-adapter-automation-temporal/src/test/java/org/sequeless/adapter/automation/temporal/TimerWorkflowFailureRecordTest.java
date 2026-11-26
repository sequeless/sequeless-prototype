package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.EventType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.spi.automation.AutomationFailure;
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TimerScheduledEntry;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * Covers {@link TimerWorkflowImpl}'s failure record (spec 13, R2, guarded by {@code timer-failure-record}) in an
 * in-memory {@link TestWorkflowEnvironment}. The workflow is started under the timer's {@code timerKey}, as
 * {@link TemporalAutomationPort} starts it, so the record's workflow id is checked against that key.
 *
 * <p>The activity's retries are shortened to three attempts. Asserting the write once per workflow run is done
 * here. Asserting that a re-run adds no second row needs Postgres, which is covered by an end-to-end IT in
 * {@code sequeless-app}.
 */
class TimerWorkflowFailureRecordTest {

    private static final String TASK_QUEUE = "sequeless-timers-failure-test";
    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final String TYPE_IRI = "urn:sequeless:test#Thing";
    private static final String STATE = "urn:sequeless:test#Waiting";
    private static final String TRANSITION = "expire";

    private static ScriptedTriggerActivities activities;
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

        activities = new ScriptedTriggerActivities();
        recorder = new RecordingAutomationFailureRecorder();
        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TimerWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities, new AutomationFailureActivitiesImpl(recorder));
        env.start();
    }

    @AfterAll
    static void stopEnvironment() {
        env.close();
    }

    @BeforeEach
    void resetDoubles() {
        activities.reset();
        recorder.reset();
    }

    @Test
    void onTimerElapsedFailingEveryAttemptRecordsTheFailureOnceUnderTheTimerKey() {
        activities.failOnTimerWith(new IllegalStateException("timer evaluation broke"));
        ObjectId objectId = ObjectId.parse(UUID.randomUUID().toString());
        String timerKey = OutboxEntry.timerKey(objectId, STATE, TRANSITION);

        runToCompletion(timer(objectId), timerKey);

        assertThat(activities.onTimerCalls()).isEqualTo(3);
        AutomationFailure record = onlyRecord();
        assertThat(record.workflowId()).isEqualTo(timerKey);
        assertThat(record.workflowType()).isEqualTo("TimerWorkflow");
        assertThat(record.transition()).isNull();
        assertThat(record.scope().tenantId()).isEqualTo(SCOPE.tenantId());
        assertThat(record.cause()).isEqualTo("java.lang.IllegalStateException: timer evaluation broke");
    }

    @Test
    void onTimerElapsedThatSucceedsEndsTheWorkflowAndRecordsNothing() {
        ObjectId objectId = ObjectId.parse(UUID.randomUUID().toString());
        String timerKey = OutboxEntry.timerKey(objectId, STATE, TRANSITION);

        runToCompletion(timer(objectId), timerKey);

        assertThat(activities.onTimerCalls()).isEqualTo(1);
        assertThat(recorder.records()).isEmpty();
    }

    @Test
    void aFailingRecordWriteFailsTheWorkflowInsteadOfEndingItAsSucceeded() {
        activities.failOnTimerWith(new IllegalStateException("timer evaluation broke"));
        recorder.failWith(new IllegalStateException("database unavailable"));
        ObjectId objectId = ObjectId.parse(UUID.randomUUID().toString());
        String timerKey = OutboxEntry.timerKey(objectId, STATE, TRANSITION);

        Throwable thrown =
                catchThrowable(() -> startTimer(timer(objectId), timerKey).getResult(5, TimeUnit.SECONDS, Void.class));

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
     * The history of a recorded timer failure carries the guard's marker and the record command, and replaying
     * it reproduces both without a mismatch. {@link WorkflowReplayer} runs no activities, so it proves the
     * workflow code's determinism, not that a second record is absent; the Postgres end covers that.
     */
    @Test
    void aRecordedTimerFailureReplaysDeterministically() throws Exception {
        activities.failOnTimerWith(new IllegalStateException("timer evaluation broke"));
        ObjectId objectId = ObjectId.parse(UUID.randomUUID().toString());
        String timerKey = OutboxEntry.timerKey(objectId, STATE, TRANSITION);
        runToCompletion(timer(objectId), timerKey);
        WorkflowExecutionHistory history = env.getWorkflowClient().fetchHistory(timerKey);

        assertThat(history.getEvents())
                .as("the guard's marker is recorded on the first execution")
                .anyMatch(event -> event.getEventType() == EventType.EVENT_TYPE_MARKER_RECORDED
                        && event.getMarkerRecordedEventAttributes().toString().contains("timer-failure-record"));
        assertThat(history.getEvents())
                .as("the record command is scheduled after the failed onTimerElapsed")
                .anyMatch(event -> event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED
                        && "RecordFailure"
                                .equals(event.getActivityTaskScheduledEventAttributes()
                                        .getActivityType()
                                        .getName()));

        assertThatCode(() -> WorkflowReplayer.replayWorkflowExecution(history, TimerWorkflowImpl.class))
                .doesNotThrowAnyException();
    }

    private static OutboxEntry timer(ObjectId objectId) {
        ResolvedTimer timer = new ResolvedTimer(
                OutboxEntry.timerKey(objectId, STATE, TRANSITION), Duration.ofSeconds(1), STATE, TRANSITION);
        return TimerScheduledEntry.of(SCOPE, objectId, TYPE_IRI, 1, timer, OCCURRED_AT);
    }

    /** Starts the {@link TimerWorkflow} under {@code timerKey}, as {@link TemporalAutomationPort} does. */
    private static WorkflowStub startTimer(OutboxEntry entry, String timerKey) {
        TimerWorkflow workflow = env.getWorkflowClient()
                .newWorkflowStub(
                        TimerWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(timerKey)
                                .setTaskQueue(TASK_QUEUE)
                                .build());
        WorkflowClient.start(workflow::run, entry, SCOPE);
        return WorkflowStub.fromTyped(workflow);
    }

    /** Starts the timer and waits, bounded, for the workflow to close. */
    private static void runToCompletion(OutboxEntry entry, String timerKey) {
        try {
            startTimer(entry, timerKey).getResult(5, TimeUnit.SECONDS, Void.class);
        } catch (TimeoutException e) {
            throw new AssertionError("Timer " + timerKey + " did not close within 5 seconds", e);
        }
    }

    /** The single failure record the recorder holds, asserting that there is exactly one. */
    private static AutomationFailure onlyRecord() {
        List<AutomationFailure> records = recorder.records();
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    /**
     * A {@link TriggerActivities} whose {@link #onTimerElapsed} fails, or succeeds, as a test sets it, and counts
     * its calls. The change-event method is not reached by this test and throws if it is.
     */
    private static final class ScriptedTriggerActivities implements TriggerActivities {
        private final AtomicInteger onTimerCalls = new AtomicInteger();
        private volatile RuntimeException onTimerFailure;

        @Override
        public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
            throw new UnsupportedOperationException("not reached by TimerWorkflowFailureRecordTest");
        }

        @Override
        public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
            onTimerCalls.incrementAndGet();
            RuntimeException failure = onTimerFailure;
            if (failure != null) {
                throw failure;
            }
        }

        int onTimerCalls() {
            return onTimerCalls.get();
        }

        void failOnTimerWith(RuntimeException failure) {
            this.onTimerFailure = failure;
        }

        void reset() {
            onTimerCalls.set(0);
            onTimerFailure = null;
        }
    }
}
