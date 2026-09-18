package org.sequeless.core.api;

import java.util.List;
import java.util.Objects;
import org.sequeless.spi.validation.Violation;

/**
 * Thrown by the browse use case (Phase 3, {@code org.sequeless.core.usecase}, out of scope here)
 * when a {@link BrowseQuery}'s filter, sort, or facet property names or operators are invalid
 * against the current {@link org.sequeless.spi.meta.MetaModelSnapshot} — for example, an unknown
 * property, an operator not applicable to the property's datatype, a non-facet property requested
 * as a facet, or a multi-valued property used somewhere only a scalar is supported.
 *
 * <p>This is deliberately not a subtype of {@link org.sequeless.core.validation.ValidationException}:
 * that exception carries failures found while checking an object's <em>content</em> against
 * coercion, structural, and SHACL rules; this one carries failures found while checking a query's
 * <em>shape</em> before any object content is ever touched. The two are different concerns that
 * happen to reuse the same {@link Violation} carrier.
 *
 * <p>Like {@link org.sequeless.core.validation.ValidationException}, this is deliberately unchecked:
 * an invalid query is a core-internal outcome, not a condition every intermediate signature up the
 * call chain should have to declare. The REST boundary (Phase 4, out of scope here) is expected to
 * catch it once, at the edge, and translate {@link #violations()} into a 400 response body.
 *
 * <p>Follows {@link org.sequeless.core.validation.ValidationException}'s "reject the degenerate
 * case in the constructor" idiom — an empty violations list — since an {@code
 * InvalidQueryException} exists to carry failures, not to be thrown when there is nothing wrong.
 */
public final class InvalidQueryException extends RuntimeException {

    private final List<Violation> violations;

    /**
     * @param violations the violations found; must not be {@code null} and must not be empty
     * @throws NullPointerException if {@code violations} is {@code null}
     * @throws IllegalArgumentException if {@code violations} is empty
     */
    public InvalidQueryException(List<Violation> violations) {
        super(buildMessage(violations));
        Objects.requireNonNull(violations, "violations must not be null");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("violations must not be empty");
        }
        this.violations = List.copyOf(violations);
    }

    /**
     * @return every violation found; never empty; unmodifiable
     */
    public List<Violation> violations() {
        return violations;
    }

    private static String buildMessage(List<Violation> violations) {
        Objects.requireNonNull(violations, "violations must not be null");
        return "Invalid query: " + violations.size() + " violation(s): " + violations;
    }
}
