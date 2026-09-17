package org.sequeless.spi.query;

import java.util.Objects;

/**
 * A single value/count pair within one property's facet, as returned by {@link
 * QueryPort#query}. {@code value} is always a display string, never a raw identifier: for a
 * reference-typed facet property it is the target object's {@code sq:displayLabel}-marked
 * property value, or the target id as text if the target type declares no {@code
 * sq:displayLabel} property, but never a raw UUID; for a scalar facet property it is the lexical
 * value of that property.
 *
 * @param value the display string this bucket counts; must not be {@code null}
 * @param count the number of matching objects with this value; must not be negative
 */
public record FacetBucket(String value, long count) {

    public FacetBucket {
        Objects.requireNonNull(value, "value must not be null");
        if (count < 0) {
            throw new IllegalArgumentException("FacetBucket count must not be negative");
        }
    }
}
