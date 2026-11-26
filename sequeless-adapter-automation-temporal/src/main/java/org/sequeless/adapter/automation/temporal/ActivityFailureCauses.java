package org.sequeless.adapter.automation.temporal;

import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;

/**
 * Reads an {@link ActivityFailure} for the durable failure record, and tells a cancellation apart from a
 * real failure. Used by {@link ChangeEventWorkflowImpl}, {@link TransitionActionWorkflowImpl} and {@link
 * TimerWorkflowImpl}, which catch an activity failure only after Temporal's retries are exhausted.
 *
 * <p>A workflow cannot rebuild the original exception, so the cause text is the application failure's
 * type and message. That is the same text the record holds, and it is deterministic on replay, because
 * the failure comes from the workflow history.
 */
final class ActivityFailureCauses {

    /**
     * The longest cause text the record keeps, in code points. A longer one is cut here, so one failure
     * cannot bloat a row.
     */
    static final int MAX_CAUSE_LENGTH = 1000;

    private static final String UNKNOWN_CAUSE = "activity failed without a message";

    private ActivityFailureCauses() {}

    /**
     * @param failure an activity failure the workflow has caught
     * @return {@code true} when the activity failed because its workflow was cancelled, which is not a
     *     failure to record and must propagate
     */
    static boolean isCancellation(ActivityFailure failure) {
        return failure.getCause() instanceof CanceledFailure;
    }

    /**
     * @param failure an activity failure the workflow has caught, not a cancellation
     * @return the application failure's type and message (the type alone when it has no message), or the
     *     activity failure's own message when it has no application failure as its cause; never blank, and at
     *     most {@link #MAX_CAUSE_LENGTH} code points
     */
    static String describe(ActivityFailure failure) {
        String text;
        if (failure.getCause() instanceof ApplicationFailure application) {
            String message = application.getOriginalMessage();
            text = message == null || message.isBlank()
                    ? application.getType()
                    : application.getType() + ": " + message;
        } else {
            text = failure.getMessage();
        }
        if (text == null || text.isBlank()) {
            return UNKNOWN_CAUSE;
        }
        return truncate(text);
    }

    /** Cuts {@code text} to at most {@link #MAX_CAUSE_LENGTH} code points, so a surrogate pair is never split. */
    private static String truncate(String text) {
        if (text.codePointCount(0, text.length()) <= MAX_CAUSE_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_CAUSE_LENGTH));
    }
}
