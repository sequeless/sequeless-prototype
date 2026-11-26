package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.WorkflowReplayer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Replays the three guarded workflows against histories recorded before their R2 failure-record guard
 * existed, so the default path (no marker, the failure propagates) must still run as it did. A change that
 * breaks that path fails here, as {@link TimerWorkflowReplayTest} fails a break in the P3 timer body.
 *
 * <p>The histories were recorded once and must not be regenerated, because the default path is what they
 * prove:
 *
 * <ul>
 *   <li>{@code timer-histories/timer-fires-post-p3.json} and {@code
 *       timer-histories/timer-activity-fails-post-p3.json}: {@link TimerWorkflowImpl} at origin/main {@code
 *       d3c0fb5}, after P3 and before the timer failure record. They carry the {@code timer-body-p3} marker
 *       and no {@code timer-failure-record} marker.
 *   <li>{@code r2-histories/*-pre-r2.json}: {@link ChangeEventWorkflowImpl} and {@link
 *       TransitionActionWorkflowImpl} at {@code d3c0fb5^}, the commit before the R2 failure record. They carry
 *       no marker and no record activity.
 * </ul>
 */
class FailureRecordReplayTest {

    @BeforeAll
    static void publishActivityOptions() {
        // The workflows read these while they replay, exactly as they do in production.
        TemporalActivityOptions.ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                .build();
    }

    @Test
    void timerThatFiresRecordedBeforeTheFailureRecordReplaysOnItsDefaultPath() throws IOException {
        replay(TimerWorkflowImpl.class, "/timer-histories/timer-fires-post-p3.json");
    }

    @Test
    void timerWhoseActivityFailedRecordedBeforeTheFailureRecordReplaysOnItsDefaultPath() throws IOException {
        replay(TimerWorkflowImpl.class, "/timer-histories/timer-activity-fails-post-p3.json");
    }

    @Test
    void changeEventThatFiresRecordedBeforeR2ReplaysOnItsDefaultPath() throws IOException {
        replay(ChangeEventWorkflowImpl.class, "/r2-histories/change-event-fires-pre-r2.json");
    }

    @Test
    void changeEventWhoseActivityFailedRecordedBeforeR2ReplaysOnItsDefaultPath() throws IOException {
        replay(ChangeEventWorkflowImpl.class, "/r2-histories/change-event-fails-pre-r2.json");
    }

    @Test
    void transitionActionWhoseActivityFailedRecordedBeforeR2ReplaysOnItsDefaultPath() throws IOException {
        replay(TransitionActionWorkflowImpl.class, "/r2-histories/transition-action-fails-pre-r2.json");
    }

    private static void replay(Class<?> workflowType, String resource) throws IOException {
        WorkflowExecutionHistory history = loadHistory(resource);

        assertThatCode(() -> WorkflowReplayer.replayWorkflowExecution(history, workflowType))
                .as("replay of %s against %s", resource, workflowType.getSimpleName())
                .doesNotThrowAnyException();
    }

    private static WorkflowExecutionHistory loadHistory(String resource) throws IOException {
        try (InputStream in = FailureRecordReplayTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("history resource not found: " + resource);
            }
            return WorkflowExecutionHistory.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
