package org.sequeless.spi.query;

import java.util.Objects;

/**
 * A single ordering clause in a {@link Query}: sort by {@code property} in {@code direction}.
 * {@link Query#sorts()} is a list, so several {@code Sort}s compose into a multi-key ordering,
 * applied in list order.
 *
 * @param property the property IRI to sort by; must not be blank
 * @param direction the direction to sort in; must not be {@code null}
 */
public record Sort(String property, Direction direction) {

    public Sort {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Sort property must not be blank");
        }
        Objects.requireNonNull(direction, "direction must not be null");
    }
}
