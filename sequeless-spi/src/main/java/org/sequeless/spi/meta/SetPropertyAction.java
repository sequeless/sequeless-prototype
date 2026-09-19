package org.sequeless.spi.meta;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.object.Value;

/**
 * An {@link Action} that sets a property on the object whose transition fired ({@code
 * sq:SetProperty}), either to a literal {@link #value()} or to the result of evaluating {@link
 * #expression()} through the {@code ExpressionPort}.
 *
 * <p>This record does not itself enforce that exactly one of {@link #value()}/{@link
 * #expression()} is present — that coherence check is the Jena mapper's job when parsing {@code
 * sq:SetProperty} nodes, exactly as {@link RollupRule} already disclaims function/property
 * coherence for the rule it carries.
 *
 * @param propertyIri the IRI of the property to set ({@code sq:property}); must not be blank
 * @param value the literal value to set ({@code sq:value}), per this record's disclaimer above;
 *     must not be {@code null} (the {@link Optional} wrapper itself, not just its contents)
 * @param expression JEXL source evaluated through the {@code ExpressionPort} to compute the value
 *     to set ({@code sq:expression}), per this record's disclaimer above; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 */
public record SetPropertyAction(String propertyIri, Optional<Value> value, Optional<String> expression)
    implements Action {

    public SetPropertyAction {
        if (propertyIri == null || propertyIri.isBlank()) {
            throw new IllegalArgumentException("SetPropertyAction propertyIri must not be blank");
        }
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(expression, "expression must not be null");
    }
}
