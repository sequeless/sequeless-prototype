package org.sequeless.spi.meta;

import java.util.Objects;
import java.util.Optional;

/**
 * An {@link Action} that calls an HTTP webhook when a transition fires ({@code sq:Webhook}),
 * templating {@link #url()} and {@link #body()} (when present) through the {@code
 * ExpressionPort}'s JXLT ({@code ${expr}} placeholder) support rather than the bare JEXL source
 * {@link SetPropertyAction#expression()} uses, so a literal URL or body with no placeholders needs
 * no special-casing.
 *
 * <p>{@code sq:method}'s {@code "POST"} default is resolved by the Jena mapper (a later task)
 * before constructing this record — the record itself just requires a non-blank {@link #method()}.
 *
 * @param url the webhook URL, a JXLT template ({@code sq:url}); must not be blank
 * @param method the HTTP method to use ({@code sq:method}); must not be blank
 * @param body the request body, a JXLT template ({@code sq:body}); must not be {@code null} (the
 *     {@link Optional} wrapper itself, not just its contents)
 */
public record WebhookAction(String url, String method, Optional<String> body) implements Action {

    public WebhookAction {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("WebhookAction url must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("WebhookAction method must not be blank");
        }
        Objects.requireNonNull(body, "body must not be null");
    }
}
