package org.sequeless.app.automation;

import java.util.Objects;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.spi.automation.AutomationFailure;
import org.sequeless.spi.automation.AutomationFailureRecorder;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Writes the host's {@code sq_automation_failure} record for a Temporal automation failure that Temporal has given
 * up on. The released automation has no failure port (spec 15 has none), so the record is written from the host's
 * wrappers around {@code ActionExecutor} and {@code TriggerEvaluator}, which run inside the activities (spec 17,
 * section 10, Q2).
 *
 * <p>The prototype's workflows recorded a failure once their activity had used its retries. The wrappers do the same
 * test: {@link #isFinalAttempt()} reads Temporal's attempt number inside the activity. Outside an activity there are
 * no retries, so every failure is final.
 */
final class TemporalFailureRecorder {

    /** The workflow type the prototype wrote for a transition action; equals the interface's simple name. */
    static final String TRANSITION_ACTION_WORKFLOW = "TransitionActionWorkflow";

    /** The workflow type the prototype wrote for a timer; equals the interface's simple name. */
    static final String TIMER_WORKFLOW = "TimerWorkflow";

    /** The workflow type the prototype wrote for a change event; equals the interface's simple name. */
    static final String CHANGE_EVENT_WORKFLOW = "ChangeEventWorkflow";

    /** The longest cause the prototype stored, in code points. */
    static final int MAX_CAUSE_LENGTH = 1000;

    private final ObjectProvider<AutomationFailureRecorder> recorder;
    private final int maximumAttempts;

    /**
     * @param recorder the persistence adapter's recorder, resolved at the time of a failure; must not be {@code null}
     * @param maximumAttempts the retry limit of {@code sequeless.automation.temporal.retry.maximum-attempts}; zero or
     *     less means Temporal retries without a limit, so no failure is ever final
     */
    TemporalFailureRecorder(ObjectProvider<AutomationFailureRecorder> recorder, int maximumAttempts) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
        this.maximumAttempts = maximumAttempts;
    }

    /**
     * @return whether the current call is the last attempt its activity will make, so that a failure here is final
     */
    boolean isFinalAttempt() {
        if (maximumAttempts <= 0) {
            return false;
        }
        int attempt;
        try {
            attempt = io.temporal.activity.Activity.getExecutionContext()
                    .getInfo()
                    .getAttempt();
        } catch (IllegalStateException notInActivity) {
            return true;
        }
        return attempt >= maximumAttempts;
    }

    /**
     * Writes one failure record, which the persistence adapter keys by its workflow id (a second record under the same
     * id is ignored).
     *
     * @throws IllegalStateException if no {@link AutomationFailureRecorder} bean exists, so the failure cannot be
     *     recorded
     */
    void record(
            Scope scope, String workflowId, String workflowType, TransitionRef transition, RuntimeException failure) {
        AutomationFailureRecorder target = recorder.getIfAvailable();
        if (target == null) {
            throw new IllegalStateException(
                    "no AutomationFailureRecorder bean, so the failure of " + workflowId + " cannot be recorded",
                    failure);
        }
        target.record(new AutomationFailure(scope, workflowId, workflowType, transition, describe(failure)));
    }

    /**
     * @param transitionFired the transition-fired row of a transition action
     * @return the transition the row records, or {@code null} if the row lacks one of its three keys
     */
    static TransitionRef transitionOf(StoreEventRow transitionFired) {
        Object machine = transitionFired.payload().get("machineIri");
        Object from = transitionFired.payload().get("fromState");
        Object name = transitionFired.payload().get("transitionName");
        if (machine instanceof String m && from instanceof String f && name instanceof String n) {
            return new TransitionRef(m, f, n);
        }
        return null;
    }

    /** The class name and message of {@code failure}, as the prototype's workflow recorded an activity failure. */
    static String describe(Throwable failure) {
        String message = failure.getMessage();
        String text = failure.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
        if (text.codePointCount(0, text.length()) <= MAX_CAUSE_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_CAUSE_LENGTH));
    }
}
