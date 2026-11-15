package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The five Temporal activity methods of this interface. Four are the {@code ActionRequest}
 * activities {@link ActionWorkflowImpl} calls, one per {@code sq:} action kind. The fifth, {@link
 * #applyTransitionAction}, applies one action of a {@code TransitionFired} row, and only {@link
 * TransitionActionWorkflowImpl} calls it.
 *
 * <p>Deliberately five methods rather than one internally-dispatching method, so each could later
 * be given its own {@code ActivityOptions} (timeout, {@code RetryOptions}) without touching the
 * others — today all five share the single {@code ActivityOptions} {@link
 * TemporalAutomationAutoConfiguration} builds from {@code sequeless.automation.temporal.retry.*}.
 *
 * <p>Every {@code ActionRequest} method is a thin wrapper over the matching {@link ActionExecutor}
 * method, except {@link #executeWebhook}, which additionally performs the real HTTP call (via
 * {@link java.net.http.HttpClient}) after resolving the request through {@link
 * ActionExecutor#resolveWebhook} — see {@link ActionActivitiesImpl} for why that HTTP call throws
 * on a non-2xx status rather than retrying itself. {@link #applyTransitionAction} is not a thin
 * wrapper: it reads the automation and checks the row's stamp and index before it applies anything.
 */
@ActivityInterface
public interface ActionActivities {

    /** Applies a {@code sq:SetProperty} action. See {@link ActionExecutor#applySetProperty}. */
    @ActivityMethod
    void applySetProperty(Scope scope, OutboxEntry entry);

    /** Applies a {@code sq:CreateObject} action. See {@link ActionExecutor#applyCreateObject}. */
    @ActivityMethod
    void applyCreateObject(Scope scope, OutboxEntry entry);

    /** Applies a {@code sq:Log} action. See {@link ActionExecutor#applyLog}. */
    @ActivityMethod
    void applyLog(Scope scope, OutboxEntry entry);

    /**
     * Resolves and delivers a {@code sq:Webhook} action's HTTP request, throwing on a non-2xx
     * status (or a transport failure) so the workflow's configured {@code RetryOptions} retries the
     * activity as a whole.
     */
    @ActivityMethod
    void executeWebhook(Scope scope, OutboxEntry entry);

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
