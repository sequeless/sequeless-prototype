package org.sequeless.core.validation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.validation.Violation;

/**
 * The outcome of {@link ValueCoercer#coerce}: either every raw property coerced successfully to a
 * {@link PropertyRef}/{@link Value} pair, or every {@link Violation} found while trying — never a
 * mix of the two. This mirrors the {@code AccessDecision.permit}/{@code deny} idiom already used in
 * {@code sequeless-spi}: a small, self-describing result type rather than a raw pair the caller has
 * to interpret.
 *
 * <p>{@link #isSuccess()} is defined purely by {@link #violations()} being empty. A {@code
 * CoercionResult} with an empty {@link #properties()} map and no violations legitimately means "an
 * object with no properties, successfully coerced" — it is not a degenerate failure.
 *
 * @param properties the coerced properties on success, or an empty map on failure; must not be
 *     {@code null}; returned as an unmodifiable copy
 * @param violations the violations found on failure, or empty on success; must not be {@code null};
 *     returned as an unmodifiable copy
 */
public record CoercionResult(Map<PropertyRef, Value> properties, List<Violation> violations) {

    public CoercionResult {
        Objects.requireNonNull(properties, "properties must not be null");
        properties = Map.copyOf(properties);
        Objects.requireNonNull(violations, "violations must not be null");
        violations = List.copyOf(violations);
    }

    /**
     * @param properties every raw property, successfully coerced; must not be {@code null}
     * @return a successful result carrying {@code properties} and no violations
     */
    public static CoercionResult success(Map<PropertyRef, Value> properties) {
        return new CoercionResult(properties, List.of());
    }

    /**
     * @param violations every violation found while coercing; must not be {@code null}
     * @return a failed result carrying no properties and {@code violations}
     */
    public static CoercionResult failure(List<Violation> violations) {
        return new CoercionResult(Map.of(), violations);
    }

    /**
     * @return {@code true} if {@link #violations()} is empty
     */
    public boolean isSuccess() {
        return violations.isEmpty();
    }
}
