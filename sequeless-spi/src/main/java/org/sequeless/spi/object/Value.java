package org.sequeless.spi.object;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A single property value carried by a {@link BusinessObject}, sealed to exactly the variants an
 * ontology-typed property value can take: {@link TextValue} (the lexical form of {@code STRING},
 * {@code ANY_URI}, {@code TIME}, and {@code DURATION}), {@link IntegerValue} ({@code INTEGER} and
 * {@code LONG}), {@link DecimalValue} ({@code DECIMAL} and {@code DOUBLE}), {@link BoolValue},
 * {@link DateTimeValue} (UTC instants), {@link DateValue}, {@link ReferenceValue} (a pointer to
 * another {@link BusinessObject}), and {@link ListValue} (a multi-valued property's values,
 * non-nested). Which variant a given property's values must be is dictated by the property's
 * {@code Datatype} in the meta-model; this package does not enforce that mapping itself.
 *
 * <p>{@code permits} is declared explicitly rather than left implicit because every implementation
 * lives in its own file, not nested inside this one; implicit permits only works for subtypes
 * nested in the sealed type's own file.
 */
public sealed interface Value
    permits TextValue, IntegerValue, DecimalValue, BoolValue, DateTimeValue, DateValue,
        ReferenceValue, ListValue {

    /**
     * @param value the lexical form to wrap; must not be {@code null}
     * @return a {@link TextValue} wrapping {@code value}
     */
    static Value text(String value) {
        return new TextValue(value);
    }

    /**
     * @param value the integral value to wrap
     * @return an {@link IntegerValue} wrapping {@code value}
     */
    static Value integer(long value) {
        return new IntegerValue(value);
    }

    /**
     * @param value the decimal value to wrap; must not be {@code null}
     * @return a {@link DecimalValue} wrapping {@code value}
     */
    static Value decimal(BigDecimal value) {
        return new DecimalValue(value);
    }

    /**
     * @param value the boolean value to wrap
     * @return a {@link BoolValue} wrapping {@code value}
     */
    static Value bool(boolean value) {
        return new BoolValue(value);
    }

    /**
     * @param value the UTC instant to wrap; must not be {@code null}
     * @return a {@link DateTimeValue} wrapping {@code value}
     */
    static Value dateTime(Instant value) {
        return new DateTimeValue(value);
    }

    /**
     * @param value the date to wrap; must not be {@code null}
     * @return a {@link DateValue} wrapping {@code value}
     */
    static Value date(LocalDate value) {
        return new DateValue(value);
    }

    /**
     * @param target the referenced object's id; must not be {@code null}
     * @return a {@link ReferenceValue} wrapping {@code target}
     */
    static Value ref(ObjectId target) {
        return new ReferenceValue(target);
    }

    /**
     * @param values the multi-valued property's values; must not be {@code null}, must not
     *     contain a nested {@link ListValue}
     * @return a {@link ListValue} wrapping an unmodifiable copy of {@code values}
     */
    static Value list(List<Value> values) {
        return new ListValue(values);
    }
}
