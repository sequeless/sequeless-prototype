package org.sequeless.spi.meta;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * How many values a property may hold, derived from an OWL cardinality restriction or from {@code
 * owl:FunctionalProperty} per the OWL → snapshot mapping table. {@code max} is {@link
 * OptionalInt#empty()} when the property is unbounded above; use the static factories below rather
 * than the canonical constructor so a call site reads as "optional", "required", or "exactly n"
 * instead of a bare pair of numbers.
 *
 * @param min the minimum number of values, inclusive; must be non-negative
 * @param max the maximum number of values, inclusive, or {@link OptionalInt#empty()} if unbounded;
 *     must not be {@code null} (the wrapper itself); if present, must be greater than or equal to
 *     {@code min}
 */
public record Cardinality(int min, OptionalInt max) {

    public Cardinality {
        Objects.requireNonNull(max, "max must not be null");
        if (min < 0) {
            throw new IllegalArgumentException("Cardinality min must not be negative: " + min);
        }
        if (max.isPresent() && max.getAsInt() < min) {
            throw new IllegalArgumentException(
                "Cardinality max (" + max.getAsInt() + ") must not be less than min (" + min + ")");
        }
    }

    /**
     * @return {@code min 0, max unbounded} — the default cardinality when neither a restriction
     *     nor {@code owl:FunctionalProperty} applies
     */
    public static Cardinality optional() {
        return new Cardinality(0, OptionalInt.empty());
    }

    /**
     * @return {@code min 1, max unbounded}
     */
    public static Cardinality required() {
        return new Cardinality(1, OptionalInt.empty());
    }

    /**
     * @param n the exact number of values required; must be non-negative
     * @return {@code min n, max n}
     */
    public static Cardinality exactly(int n) {
        return new Cardinality(n, OptionalInt.of(n));
    }

    /**
     * @param n the maximum number of values allowed; must be non-negative
     * @return {@code min 0, max n} — the shape {@code owl:FunctionalProperty} produces absent an
     *     explicit min ({@code n = 1})
     */
    public static Cardinality atMost(int n) {
        return new Cardinality(0, OptionalInt.of(n));
    }

    /**
     * @param min the minimum number of values, inclusive; must be non-negative
     * @param max the maximum number of values, inclusive; must be greater than or equal to {@code
     *     min}
     * @return {@code min min, max max}
     */
    public static Cardinality range(int min, int max) {
        return new Cardinality(min, OptionalInt.of(max));
    }
}
