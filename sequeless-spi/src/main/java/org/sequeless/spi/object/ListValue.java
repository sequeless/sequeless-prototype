package org.sequeless.spi.object;

import java.util.List;
import java.util.Objects;

/**
 * The values of a multi-valued property (one whose relationship or attribute cardinality allows
 * more than one value). Nesting is deliberately disallowed: a property is either scalar or a flat
 * list of scalars, never a list of lists, so {@code List<Value>} elsewhere in the SPI never has to
 * special-case a {@code ListValue} appearing where a scalar {@link Value} is expected.
 *
 * @param values the property's values, in order; must not be {@code null}, must not contain a
 *     nested {@code ListValue}; returned as an unmodifiable copy so callers cannot mutate a value
 *     after construction
 */
public record ListValue(List<Value> values) implements Value {

    public ListValue {
        Objects.requireNonNull(values, "values must not be null");
        values = List.copyOf(values);
        if (values.stream().anyMatch(value -> value instanceof ListValue)) {
            throw new IllegalArgumentException("ListValue must not contain nested ListValue");
        }
    }
}
