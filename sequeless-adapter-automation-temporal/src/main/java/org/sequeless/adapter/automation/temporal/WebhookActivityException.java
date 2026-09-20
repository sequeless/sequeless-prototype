package org.sequeless.adapter.automation.temporal;

/**
 * Thrown by {@link ActionActivitiesImpl#executeWebhook} when a {@code sq:Webhook} action's HTTP
 * request could not be delivered (a transport failure) or received a non-2xx status. Deliberately
 * unchecked and deliberately <b>not</b> retried by this class itself: thrown out of a Temporal
 * activity method, it becomes an {@code ActivityFailure} that Temporal's own {@code RetryOptions}
 * (configured on the workflow's activity stub, see {@link ActionWorkflowImpl}) decides whether and
 * how to retry — the mirror image of {@code
 * org.sequeless.adapter.automation.inprocess.WebhookDeliveryException}, which is thrown only after
 * that adapter's own hand-rolled retry loop is exhausted.
 */
public final class WebhookActivityException extends RuntimeException {

    /**
     * @param message a message describing why delivery failed; may be {@code null}
     * @param cause the underlying transport failure this exception wraps, or {@code null} if
     *     delivery failed only because of a non-2xx status rather than a thrown exception
     */
    public WebhookActivityException(String message, Throwable cause) {
        super(message, cause);
    }
}
