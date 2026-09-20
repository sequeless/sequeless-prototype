package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The four Temporal activities {@link ActionWorkflowImpl} calls, one per {@code sq:} action kind.
 * Deliberately four thin methods rather than one internally-dispatching method, so each kind could
 * later be given its own {@code ActivityOptions} (timeout, {@code RetryOptions}) without touching
 * the others — today all four share the single {@code ActivityOptions} {@link
 * TemporalAutomationAutoConfiguration} builds from {@code sequeless.automation.temporal.retry.*}.
 *
 * <p>Every method is a thin wrapper over the matching {@link ActionExecutor} method, except {@link
 * #executeWebhook}, which additionally performs the real HTTP call (via {@link
 * java.net.http.HttpClient}) after resolving the request through {@link
 * ActionExecutor#resolveWebhook} — see {@link ActionActivitiesImpl} for why that HTTP call throws
 * on a non-2xx status rather than retrying itself.
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
}
