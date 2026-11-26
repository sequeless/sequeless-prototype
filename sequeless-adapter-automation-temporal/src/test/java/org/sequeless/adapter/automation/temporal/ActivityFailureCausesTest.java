package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.api.enums.v1.RetryState;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link ActivityFailureCauses} directly. {@code ChangeEventWorkflowTest} shows the outcome of a cancellation
 * (no record), but it cannot prove this predicate: once the workflow is cancelled, a record activity scheduled after
 * the cancel is never dispatched, so no record is written whichever branch runs. This test proves the predicate itself.
 */
class ActivityFailureCausesTest {

    @Test
    void aCancellationCauseIsACancellation() {
        assertThat(ActivityFailureCauses.isCancellation(activityFailure(new CanceledFailure("cancelled"))))
                .isTrue();
    }

    @Test
    void anApplicationFailureCauseIsNotACancellation() {
        assertThat(ActivityFailureCauses.isCancellation(
                        activityFailure(ApplicationFailure.newFailure("boom", "java.lang.IllegalStateException"))))
                .isFalse();
    }

    @Test
    void describesAnApplicationFailureAsItsTypeAndMessage() {
        ActivityFailure failure =
                activityFailure(ApplicationFailure.newFailure("boom", "java.lang.IllegalStateException"));

        assertThat(ActivityFailureCauses.describe(failure)).isEqualTo("java.lang.IllegalStateException: boom");
    }

    @Test
    void describesAnApplicationFailureWithNoMessageAsItsTypeAlone() {
        ActivityFailure failure =
                activityFailure(ApplicationFailure.newFailure(null, "java.lang.IllegalStateException"));

        assertThat(ActivityFailureCauses.describe(failure)).isEqualTo("java.lang.IllegalStateException");
    }

    @Test
    void describesAnApplicationFailureWithABlankMessageAsItsTypeAlone() {
        // A failure decoded from history carries an empty message, not null, so this is the case production hits.
        ActivityFailure failure = activityFailure(ApplicationFailure.newFailure("", "java.lang.IllegalStateException"));

        assertThat(ActivityFailureCauses.describe(failure)).isEqualTo("java.lang.IllegalStateException");
    }

    @Test
    void truncatesALongCauseToMaxCodePointsWithoutSplittingASurrogatePair() {
        // 1500 emoji are 3000 chars but 1500 code points; the type prefix adds three more code points.
        ActivityFailure failure = activityFailure(ApplicationFailure.newFailure("😀".repeat(1500), "T"));

        String described = ActivityFailureCauses.describe(failure);

        assertThat(described.codePointCount(0, described.length())).isEqualTo(ActivityFailureCauses.MAX_CAUSE_LENGTH);
        assertThat(Character.isHighSurrogate(described.charAt(described.length() - 1)))
                .isFalse();
    }

    @Test
    void keepsACauseThatFitsUnchanged() {
        ActivityFailure failure = activityFailure(ApplicationFailure.newFailure("short", "T"));

        assertThat(ActivityFailureCauses.describe(failure)).isEqualTo("T: short");
    }

    private static ActivityFailure activityFailure(Throwable cause) {
        return new ActivityFailure(
                "activity failed",
                1L,
                2L,
                "recordFailure",
                "activity-1",
                RetryState.RETRY_STATE_NON_RETRYABLE_FAILURE,
                "identity",
                cause);
    }
}
