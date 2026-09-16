package org.sequeless.spi.object;

import java.util.Objects;

/**
 * The lexical form of a {@code STRING}, {@code ANY_URI}, {@code TIME}, or {@code DURATION}
 * property value. All four datatypes share this representation because none of them benefit from
 * a richer in-memory type: they are compared, stored, and rendered as text.
 *
 * @param value the lexical form; must not be {@code null}
 */
public record TextValue(String value) implements Value {

    public TextValue {
        Objects.requireNonNull(value, "value must not be null");
    }
}
