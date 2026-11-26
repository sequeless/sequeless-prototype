package org.sequeless.app.automation;

import java.util.Objects;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.ResolvedWebhookRequest;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.WebhookAction;

/**
 * Wraps the released {@link ActionExecutor} so that a transition action's final failure is recorded, as the prototype's
 * {@code TransitionActionWorkflow} recorded it (spec 17, section 9).
 *
 * <p>{@link #applyAction} is the case the prototype covered. Its final failure is recorded and then swallowed, so the
 * activity, and the workflow, end normally. A non-final failure is rethrown, so Temporal retries the activity.
 *
 * <p>{@link #resolveWebhookAction} has to return a request, so a final failure there is recorded and then rethrown:
 * the workflow fails. This differs from the prototype. Webhook delivery itself happens in the released activity,
 * outside this wrapper, so its final failure is not recorded (spec 17, section 9).
 */
final class FailureRecordingActionExecutor implements ActionExecutor {

    private final ActionExecutor delegate;
    private final TemporalFailureRecorder failures;

    /**
     * @param delegate the released executor the actions go to; must not be {@code null}
     * @param failures the recorder and the attempt check; must not be {@code null}
     */
    FailureRecordingActionExecutor(ActionExecutor delegate, TemporalFailureRecorder failures) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.failures = Objects.requireNonNull(failures, "failures must not be null");
    }

    @Override
    public void applyAction(Scope scope, StoreEventRow transitionFired, Action action, int actionIndex) {
        try {
            delegate.applyAction(scope, transitionFired, action, actionIndex);
        } catch (RuntimeException failure) {
            if (!failures.isFinalAttempt()) {
                throw failure;
            }
            failures.record(
                    scope,
                    transitionFired.id() + ":" + actionIndex,
                    TemporalFailureRecorder.TRANSITION_ACTION_WORKFLOW,
                    TemporalFailureRecorder.transitionOf(transitionFired),
                    failure);
        }
    }

    @Override
    public ResolvedWebhookRequest resolveWebhookAction(
            Scope scope, StoreEventRow transitionFired, WebhookAction action, int actionIndex) {
        try {
            return delegate.resolveWebhookAction(scope, transitionFired, action, actionIndex);
        } catch (RuntimeException failure) {
            if (failures.isFinalAttempt()) {
                failures.record(
                        scope,
                        transitionFired.id() + ":" + actionIndex,
                        TemporalFailureRecorder.TRANSITION_ACTION_WORKFLOW,
                        TemporalFailureRecorder.transitionOf(transitionFired),
                        failure);
            }
            throw failure;
        }
    }
}
