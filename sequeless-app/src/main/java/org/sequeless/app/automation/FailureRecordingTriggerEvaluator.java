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
 * Wraps the released {@link TriggerEvaluator} so that a timer's final failure is recorded, as the prototype's
 * {@code TimerWorkflow} recorded it, and the timer's workflow ends normally (spec 17, section 9).
 *
 * <p>Only {@link #onTimerElapsed} records. The prototype's change-event workflow did not record a failure, and this
 * wrapper does not either: a failed {@code onChange} propagates as it did before.
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
        delegate.onChange(scope, changeEvent);
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
