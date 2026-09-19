package org.sequeless.spi.expression;

/**
 * Thrown by {@link ExpressionPort#evaluate} and {@link ExpressionPort#renderTemplate} when the
 * expression they were asked to evaluate cannot produce a result: a syntax error, a sandbox
 * rejection (an attempt at reflection or a static method/class reference), or a JEXL-{@code null}
 * result.
 *
 * <p>This is deliberately unchecked, for the same reason {@link
 * org.sequeless.spi.ontology.OntologyException} and {@code
 * org.sequeless.spi.object.StaleObjectException} are: a malformed or rejected expression is a
 * condition every intermediate signature up the call chain — the core interpreter, the automation
 * adapters dispatching an action — should not have to declare, and the REST boundary (or any other
 * inbound adapter) is expected to catch it once, at the edge, to produce a response naming the
 * transition or action whose expression failed.
 *
 * <p>Unlike {@code OntologyException}, this exception carries no structured report — an
 * implementation wraps whatever underlying failure it caught (typically a JEXL library's own
 * exception type) as the {@linkplain #getCause() cause}, so a two-argument constructor is provided
 * for that purpose; {@code OntologyException} has no analogous need because its own {@code
 * OntologyReport} payload is always constructed directly, never wrapped from a caught exception.
 */
public final class ExpressionException extends RuntimeException {

    /**
     * @param message a message describing why the expression could not produce a result; may be
     *     {@code null}
     */
    public ExpressionException(String message) {
        super(message);
    }

    /**
     * @param message a message describing why the expression could not produce a result; may be
     *     {@code null}
     * @param cause the underlying failure this exception wraps, typically thrown by the JEXL
     *     library an adapter is built on; may be {@code null}
     */
    public ExpressionException(String message, Throwable cause) {
        super(message, cause);
    }
}
