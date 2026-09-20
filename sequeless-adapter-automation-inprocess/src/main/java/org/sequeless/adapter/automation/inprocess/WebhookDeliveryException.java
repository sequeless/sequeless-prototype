package org.sequeless.adapter.automation.inprocess;

/**
 * Thrown by {@link InProcessAutomationPort} when a {@code sq:Webhook} action's HTTP request could
 * not be delivered after {@link InProcessAutomationProperties#getRetryAttempts()} attempts, each
 * separated by {@link InProcessAutomationProperties#getRetryDelay()} — every attempt either threw
 * an {@link java.io.IOException} (the request never reached the server, or its response could not
 * be read) or received a non-2xx HTTP status.
 *
 * <p>Deliberately unchecked, for the same reason {@code org.sequeless.spi.expression.
 * ExpressionException} is: a delivery failure is a condition the relay that called {@link
 * InProcessAutomationPort#dispatch} is expected to let propagate (and, in a later task, log or
 * surface as a failed outbox row), not a condition every intermediate signature should have to
 * declare.
 */
public final class WebhookDeliveryException extends RuntimeException {

    /**
     * @param message a message describing why delivery ultimately failed; may be {@code null}
     * @param cause the last underlying failure this exception wraps (an {@link
     *     java.io.IOException}, or {@code null} if the final attempt failed only because of a
     *     non-2xx status rather than a thrown exception)
     */
    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
