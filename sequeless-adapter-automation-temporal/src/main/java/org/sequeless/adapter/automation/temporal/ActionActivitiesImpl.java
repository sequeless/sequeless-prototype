package org.sequeless.adapter.automation.temporal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Objects;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The real {@link ActionActivities} implementation: three of its four methods are pure
 * pass-throughs to the injected {@link ActionExecutor}; {@link #executeWebhook} additionally
 * issues the resolved request's actual HTTP call via the JDK's own {@link HttpClient} (no new
 * dependency, matching {@code InProcessAutomationPort}'s choice).
 *
 * <p><b>No retry loop here, unlike the in-process adapter.</b> {@link #executeWebhook} throws a
 * {@link WebhookActivityException} on any non-2xx status or transport failure, letting the
 * exception propagate out of the activity method. Retrying is deliberately not this class's job:
 * the workflow's activity stub is built (see {@link ActionWorkflowImpl}) with an {@code
 * ActivityOptions} carrying the {@code RetryOptions} {@link TemporalAutomationAutoConfiguration}
 * assembles from {@code sequeless.automation.temporal.retry.*}, so Temporal itself retries the
 * whole activity invocation — this is the entire point of using Temporal for this adapter, in
 * contrast to the in-process adapter's hand-rolled fixed-delay loop.
 */
public final class ActionActivitiesImpl implements ActionActivities {

    private final ActionExecutor actionExecutor;
    private final HttpClient httpClient;

    /**
     * @param actionExecutor the {@link ActionExecutor} this activity implementation calls back
     *     into; must not be {@code null}
     */
    public ActionActivitiesImpl(ActionExecutor actionExecutor) {
        this.actionExecutor = Objects.requireNonNull(actionExecutor, "actionExecutor must not be null");
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void applySetProperty(Scope scope, OutboxEntry entry) {
        actionExecutor.applySetProperty(scope, entry);
    }

    @Override
    public void applyCreateObject(Scope scope, OutboxEntry entry) {
        actionExecutor.applyCreateObject(scope, entry);
    }

    @Override
    public void applyLog(Scope scope, OutboxEntry entry) {
        actionExecutor.applyLog(scope, entry);
    }

    @Override
    public void executeWebhook(Scope scope, OutboxEntry entry) {
        ResolvedWebhookRequest resolved = actionExecutor.resolveWebhook(scope, entry);
        deliver(resolved);
    }

    /**
     * Issues {@code resolved}'s HTTP request exactly once, throwing on any transport failure or
     * non-2xx status; Temporal's {@code RetryOptions} is what causes this activity to be invoked
     * again, not this method.
     *
     * @throws WebhookActivityException if the request could not be delivered, or received a
     *     non-2xx status
     */
    private void deliver(ResolvedWebhookRequest resolved) {
        HttpRequest request =
            HttpRequest.newBuilder(URI.create(resolved.url()))
                .method(
                    resolved.method(),
                    resolved.body().map(BodyPublishers::ofString).orElseGet(BodyPublishers::noBody))
                .build();

        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, BodyHandlers.discarding());
        } catch (IOException e) {
            throw new WebhookActivityException("Webhook delivery to " + resolved.url() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebhookActivityException(
                "Webhook delivery to " + resolved.url() + " was interrupted", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new WebhookActivityException(
                "Webhook delivery to "
                    + resolved.url()
                    + " received non-2xx status "
                    + response.statusCode(),
                null);
        }
    }
}
