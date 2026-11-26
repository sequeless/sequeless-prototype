package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.api.enums.v1.RetryState;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link ActivityFailureCauses} directly. The workflow tests show the outcome of a cancellation (no record),
 * but a cancelled activity is short-circuited before it is dispatched, so the predicate itself is proven here.
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
