package org.sequeless.core.validation;

import java.util.List;
import java.util.Objects;
import org.sequeless.spi.validation.Violation;

/**
 * Thrown by a use case when a {@link Violation} is found while adding or editing a {@link
 * org.sequeless.spi.object.BusinessObject} — either during this module's own structural checks
 * ({@link ValueCoercer}, {@link StructuralValidator}) or during the {@code ValidationPort} SHACL
 * check that runs after them. {@link #source()} tells the two apart.
 *
 * <p>This is deliberately unchecked: validation failure is a core-internal outcome, not a
 * condition every intermediate signature up the call chain should have to declare. The REST
 * boundary (Phase 4, out of scope here) is expected to catch it once, at the edge, and translate
 * {@link #source()} and {@link #violations()} into a 400 response body.
 *
 * <p>Follows {@link org.sequeless.core.AuthorizationException}'s "reject the degenerate case in the
 * constructor" idiom (there, an {@link org.sequeless.spi.authz.AccessDecision#allowed()} decision;
 * here, an empty violations list) — a {@code ValidationException} exists to carry failures, not to
 * be thrown when there is nothing wrong.
 */
public final class ValidationException extends RuntimeException {

    /** Which stage of validation produced the {@link #violations()} this exception carries. */
    public enum Source {

        /** {@link ValueCoercer} or {@link StructuralValidator} found the violation. */
        STRUCTURAL,

        /** The {@code ValidationPort} SHACL check found the violation. */
        SHACL
    }

    private final Source source;
    private final List<Violation> violations;

    /**
     * @param source which stage of validation produced {@code violations}; must not be {@code null}
     * @param violations the violations found; must not be {@code null} and must not be empty
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code violations} is empty
     */
    public ValidationException(Source source, List<Violation> violations) {
        super(buildMessage(source, violations));
        this.source = Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(violations, "violations must not be null");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("violations must not be empty");
        }
        this.violations = List.copyOf(violations);
    }

    /**
     * @return which stage of validation produced {@link #violations()}
     */
    public Source source() {
        return source;
    }

    /**
     * @return every violation found; never empty; unmodifiable
     */
    public List<Violation> violations() {
        return violations;
    }

    private static String buildMessage(Source source, List<Violation> violations) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(violations, "violations must not be null");
        return "Validation failed (" + source + "): " + violations.size() + " violation(s): "
            + violations;
    }
}
