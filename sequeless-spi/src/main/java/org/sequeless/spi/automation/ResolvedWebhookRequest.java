package org.sequeless.spi.automation;

import java.util.Objects;
import java.util.Optional;

/**
 * The fully-rendered HTTP request a {@code sq:Webhook} action describes, as handed back by {@link
 * ActionExecutor#resolveWebhook}: {@code url} and {@code body}'s {@code ${expr}} placeholders have
 * already been substituted via the {@link org.sequeless.spi.expression.ExpressionPort}, and {@code
 * method} is the literal HTTP verb. This record carries no HTTP I/O of its own — resolving the
 * request and actually issuing it are deliberately separate steps, so the calling automation
 * adapter (the in-process relay, or a Temporal activity) owns the HTTP call, its retry policy, and
 * its timeout, none of which this SPI package concerns itself with.
 *
 * @param url the fully-rendered target URL; must not be blank
 * @param method the HTTP method (e.g. {@code "POST"}); must not be blank
 * @param body the fully-rendered request body, if the action defined one; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 */
public record ResolvedWebhookRequest(String url, String method, Optional<String> body) {

    public ResolvedWebhookRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("ResolvedWebhookRequest url must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("ResolvedWebhookRequest method must not be blank");
        }
        Objects.requireNonNull(body, "body must not be null");
    }
}
