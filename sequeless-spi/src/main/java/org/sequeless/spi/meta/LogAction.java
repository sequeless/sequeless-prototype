package org.sequeless.spi.meta;

/**
 * An {@link Action} that records a log message when a transition fires ({@code sq:Log}),
 * templating {@link #message()} through the {@code ExpressionPort}'s JXLT ({@code ${expr}}
 * placeholder) support, exactly as {@link WebhookAction#url()}/{@link WebhookAction#body()} are.
 *
 * @param message the message to log, a JXLT template ({@code sq:message}); must not be blank
 */
public record LogAction(String message) implements Action {

    public LogAction {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("LogAction message must not be blank");
        }
    }
}
