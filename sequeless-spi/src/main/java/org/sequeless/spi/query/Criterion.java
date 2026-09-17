package org.sequeless.spi.query;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.Value;

/**
 * A single filter clause in a {@link Query}: match {@code property} against {@code operator} and
 * {@code value}. The shape of {@code value} depends on {@code operator}: for {@link
 * Operator#IS_NULL} and {@link Operator#NOT_NULL}, {@code value} is {@link Optional#empty()},
 * since neither operator compares against a value; for {@link Operator#IN}, {@code value} is
 * present and wraps a {@link ListValue} of the candidate values; for every other operator, {@code
 * value} is present and wraps a scalar {@link Value} matching the property's datatype.
 *
 * <p>This record does not itself enforce operator/value/datatype coherence — for example, it does
 * not check that {@code value} is empty for {@link Operator#IS_NULL}, or that a {@link
 * Operator#IN} criterion's value is actually a {@link ListValue} rather than some other {@link
 * Value} variant. That coherence check is the caller's (core's) responsibility, performed before a
 * {@link Criterion} is ever handed to a {@link QueryPort}.
 *
 * @param property the property IRI to filter on; must not be blank
 * @param operator the comparison to apply; must not be {@code null}
 * @param value the value(s) to compare against, per the shape described above; must not be {@code
 *     null} (the {@link Optional} wrapper itself, not just its contents)
 */
public record Criterion(String property, Operator operator, Optional<Value> value) {

    public Criterion {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Criterion property must not be blank");
        }
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
