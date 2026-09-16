package org.sequeless.spi.authz;

import java.util.Objects;

/**
 * The outcome of an {@link AuthorizationPort#decide(org.sequeless.spi.Scope, Operation, String)}
 * call: whether the operation is allowed, and a human-readable reason a caller can log or surface
 * back to an end user without needing to know anything about the adapter that produced it.
 *
 * <p>{@code reason} must never be {@code null} — every decision, permit or deny, must be
 * explainable — but it may be blank, since some policies (a permit-all default, for instance)
 * have nothing more specific to say. Use {@link #permit(String)} and {@link #deny(String)} rather
 * than the canonical constructor directly: they make the {@code allowed} value obvious at the
 * call site instead of leaving a bare boolean literal for a reader to interpret.
 *
 * @param allowed whether the operation is authorized
 * @param reason a human-readable explanation for the decision; must not be {@code null}
 */
public record AccessDecision(boolean allowed, String reason) {

    public AccessDecision {
        Objects.requireNonNull(reason, "reason must not be null");
    }

    /**
     * @param reason a human-readable explanation for the permit; must not be {@code null}
     * @return an allowed decision carrying {@code reason}
     */
    public static AccessDecision permit(String reason) {
        return new AccessDecision(true, reason);
    }

    /**
     * @param reason a human-readable explanation for the denial; must not be {@code null}
     * @return a disallowed decision carrying {@code reason}
     */
    public static AccessDecision deny(String reason) {
        return new AccessDecision(false, reason);
    }
}
