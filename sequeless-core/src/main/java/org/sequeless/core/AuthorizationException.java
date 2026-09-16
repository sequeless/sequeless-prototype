package org.sequeless.core;

import java.util.Objects;
import org.sequeless.spi.authz.AccessDecision;

/**
 * Thrown by a use case when an {@link org.sequeless.spi.authz.AuthorizationPort} denies the
 * operation the use case was about to perform.
 *
 * <p>This is deliberately unchecked: authorization failure is a core-internal outcome, not a
 * condition every intermediate signature up the call chain should have to declare. The REST
 * boundary (or any other inbound adapter) is expected to catch it once, at the edge, and translate
 * it into the appropriate transport-level response.
 *
 * <p>This exception deliberately carries only the denying {@link AccessDecision} — not the {@link
 * org.sequeless.spi.Scope}, {@link org.sequeless.spi.authz.Operation}, or resource that produced
 * it. Add those only when a real caller needs them; until then they would be unused surface area.
 */
public final class AuthorizationException extends RuntimeException {

    private final AccessDecision decision;

    /**
     * @param decision the denying decision this exception carries; must not be {@code null} and
     *     must not be {@link AccessDecision#allowed() allowed}
     * @throws NullPointerException if {@code decision} is {@code null}
     * @throws IllegalArgumentException if {@code decision} is {@link AccessDecision#allowed()
     *     allowed} — this exception exists to carry a denial, not a permit
     */
    public AuthorizationException(AccessDecision decision) {
        super(Objects.requireNonNull(decision, "decision must not be null").reason());
        if (decision.allowed()) {
            throw new IllegalArgumentException("decision must not be allowed");
        }
        this.decision = decision;
    }

    /**
     * @return the denying decision that caused this exception
     */
    public AccessDecision decision() {
        return decision;
    }
}
