package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The Temporal activity interface of the per-action path. Its one method, {@link
 * #applyTransitionAction}, applies one action of a {@code TransitionFired} row, and only {@link
 * TransitionActionWorkflowImpl} calls it. The method is not a thin wrapper: it reads the automation
 * and checks the row's stamp and index before it applies anything.
 */
@ActivityInterface
public interface ActionActivities {

    /**
     * Applies the one action at {@code actionIndex} of a {@code TransitionFired} row, from the
     * automation of the row's ontology version. A webhook action is resolved through {@link
     * ActionExecutor#resolveWebhookAction} and then delivered over HTTP; any other action is applied
     * through {@link ActionExecutor#applyAction}.
     *
     * <p>The automation is read first. Then the row's {@code ontologyVersion} stamp is checked, and
     * then the index. A missing or non-Number stamp, a version mismatch, or an index outside the
     * automation's list each fails with a non-retryable {@code ApplicationFailure}, so Temporal
     * does not retry it. See {@link ActionActivitiesImpl#applyTransitionAction} for the order of
     * these checks.
     *
     * @param scope the tenant and principal to apply the action on behalf of
     * @param transitionFired the {@code TransitionFired}-kind outbox entry the action originates from
     * @param actionIndex the action's 0-based position in the automation's list for the row's
     *     transition
     */
    @ActivityMethod
    void applyTransitionAction(Scope scope, OutboxEntry transitionFired, int actionIndex);
}
