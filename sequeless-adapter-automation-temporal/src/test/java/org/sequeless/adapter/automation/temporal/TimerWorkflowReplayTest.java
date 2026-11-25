package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Replays {@link TimerWorkflowImpl} against histories recorded from the pre-P3 implementation, so a
 * change to the workflow body that breaks determinism against an in-flight execution fails here.
 *
 * <p>The histories under {@code src/test/resources/timer-histories/} were recorded from the pre-P3
 * {@code TimerWorkflowImpl} at origin/main {@code beec6de}: {@code timer-fires-pre-p3.json} is a
 * timer that expires and runs {@code onTimerElapsed}, and {@code timer-cancel-signal-pre-p3.json} is
 * a timer that receives a {@code cancel} signal before it expires. They must not be regenerated when
 * P3 changes the workflow body. Regenerating them would replace the recorded pre-P3 behaviour with the
 * new code's behaviour, and the test would then prove nothing about in-flight executions.
 */
class TimerWorkflowReplayTest {

    @BeforeAll
    static void publishActivityOptions() {
        // TimerWorkflowImpl.run reads these while it replays, exactly as it does in production.
        TemporalActivityOptions.ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                .build();
    }

    @Test
    void replaysTimerThatFiresRecordedBeforeP3() throws Exception {
        WorkflowExecutionHistory history = loadHistory("/timer-histories/timer-fires-pre-p3.json");

        assertThatCode(() -> WorkflowReplayer.replayWorkflowExecution(history, TimerWorkflowImpl.class))
                .doesNotThrowAnyException();
    }

    @Test
    void replaysTimerCancelledBySignalRecordedBeforeP3() throws Exception {
        WorkflowExecutionHistory history = loadHistory("/timer-histories/timer-cancel-signal-pre-p3.json");

        assertThatCode(() -> WorkflowReplayer.replayWorkflowExecution(history, TimerWorkflowImpl.class))
                .doesNotThrowAnyException();
    }

    private static WorkflowExecutionHistory loadHistory(String resource) throws IOException {
        try (InputStream in = TimerWorkflowReplayTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("history resource %s", resource).isNotNull();
            return WorkflowExecutionHistory.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
