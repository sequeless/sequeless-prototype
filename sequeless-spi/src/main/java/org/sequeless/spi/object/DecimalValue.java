package org.sequeless.spi.object;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A {@code DECIMAL} or {@code DOUBLE} property value.
 *
 * <p>Scale is part of the value, not incidental precision: the JSONB encoding stores decimals as
 * JSON numbers, which PostgreSQL keeps as {@code numeric} without losing trailing zeros, so a
 * round trip through storage is expected to preserve the exact scale the value was created with.
 * Accordingly this record does not normalize scale, and {@code equals}/{@code hashCode} use {@link
 * BigDecimal}'s own scale-sensitive definition ({@code new BigDecimal("12.50")} is <em>not</em>
 * equal to {@code new BigDecimal("12.5")}, and neither is equal to {@code BigDecimal.valueOf(12.5)},
 * which has scale 1). Callers that build a {@code DecimalValue} to assert a round trip must
 * construct the {@link BigDecimal} from the exact expected literal, not from {@code valueOf} on a
 * {@code double}.
 *
 * @param value the decimal value, at its original scale; must not be {@code null}
 */
public record DecimalValue(BigDecimal value) implements Value {

    public DecimalValue {
        Objects.requireNonNull(value, "value must not be null");
    }
}
