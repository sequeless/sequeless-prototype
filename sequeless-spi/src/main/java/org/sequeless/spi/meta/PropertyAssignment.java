package org.sequeless.spi.meta;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.object.Value;

/**
 * A single property assignment within a {@link CreateObjectAction#properties()} list ({@code
 * sq:PropertyAssignment}), either a literal {@link #value()} or the result of evaluating {@link
 * #expression()} through the {@code ExpressionPort}. Not itself an {@link Action} — it only ever
 * appears as an element of {@link CreateObjectAction#properties()}.
 *
 * <p>This record does not itself enforce that exactly one of {@link #value()}/{@link
 * #expression()} is present — that coherence check is the Jena mapper's job when parsing {@code
 * sq:PropertyAssignment} nodes, exactly as {@link SetPropertyAction} disclaims the same coherence
 * for its own {@link SetPropertyAction#value()}/{@link SetPropertyAction#expression()}.
 *
 * @param propertyIri the IRI of the property to assign ({@code sq:property}); must not be blank
 * @param value the literal value to assign ({@code sq:value}), per this record's disclaimer above;
 *     must not be {@code null} (the {@link Optional} wrapper itself, not just its contents)
 * @param expression JEXL source evaluated through the {@code ExpressionPort} to compute the value
 *     to assign ({@code sq:expression}), per this record's disclaimer above; must not be {@code
 *     null} (the {@link Optional} wrapper itself, not just its contents)
 */
public record PropertyAssignment(String propertyIri, Optional<Value> value, Optional<String> expression) {

    public PropertyAssignment {
        if (propertyIri == null || propertyIri.isBlank()) {
            throw new IllegalArgumentException("PropertyAssignment propertyIri must not be blank");
        }
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(expression, "expression must not be null");
    }
}
