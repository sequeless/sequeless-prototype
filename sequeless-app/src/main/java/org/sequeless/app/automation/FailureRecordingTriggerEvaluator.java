package org.sequeless.app.automation;

import java.util.List;
import java.util.Objects;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.ResolvedTimer;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TransitionFiredEvent;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.kernel.identity.Scope;

/**
 * Wraps the released {@link TriggerEvaluator} so that the final failure of {@link #onChange} and of
 * {@link #onTimerElapsed} is recorded, as the prototype's {@code ChangeEventWorkflow} and {@code TimerWorkflow} recorded
 * them, and the workflow then ends normally (spec 13, R2; spec 17, section 9). A non-final failure is rethrown, so
 * Temporal retries the activity.
 */
final class FailureRecordingTriggerEvaluator implements TriggerEvaluator {

    private final TriggerEvaluator delegate;
    private final TemporalFailureRecorder failures;

    /**
     * @param delegate the released evaluator the calls go to; must not be {@code null}
     * @param failures the recorder and the attempt check; must not be {@code null}
     */
    FailureRecordingTriggerEvaluator(TriggerEvaluator delegate, TemporalFailureRecorder failures) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.failures = Objects.requireNonNull(failures, "failures must not be null");
    }

    @Override
    public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
        try {
            delegate.onChange(scope, changeEvent);
        } catch (RuntimeException failure) {
            if (!failures.isFinalAttempt()) {
                throw failure;
            }
            // The released port starts a change event's workflow under the event id, so that is the workflow id.
            failures.record(
                    scope, changeEvent.id().toString(), TemporalFailureRecorder.CHANGE_EVENT_WORKFLOW, null, failure);
        }
    }

    @Override
    public void onTimerElapsed(Scope scope, StoreEventRow timerScheduled) {
        try {
            delegate.onTimerElapsed(scope, timerScheduled);
        } catch (RuntimeException failure) {
            if (!failures.isFinalAttempt()) {
                throw failure;
            }
            // The timer's workflow id is its timer key, as the prototype's was.
            failures.record(
                    scope,
                    String.valueOf(timerScheduled.payload().get("timerKey")),
                    TemporalFailureRecorder.TIMER_WORKFLOW,
                    null,
                    failure);
        }
    }

    @Override
    public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
        return delegate.timersStartedBy(scope, transitionFired);
    }
}
