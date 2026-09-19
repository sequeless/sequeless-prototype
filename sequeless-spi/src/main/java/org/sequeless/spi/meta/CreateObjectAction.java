package org.sequeless.spi.meta;

import java.util.List;
import java.util.Objects;

/**
 * An {@link Action} that creates a new object of {@link #typeIri()} when a transition fires
 * ({@code sq:CreateObject}), setting each of {@link #properties()} on the new object, exactly as
 * {@code activate} on {@code ex:ProjectLifecycle} creates a kickoff {@code ex:Task}.
 *
 * @param typeIri the IRI of the type to create ({@code sq:type}); must not be blank
 * @param properties the properties to set on the new object ({@code sq:properties}); must not be
 *     {@code null}; returned as an unmodifiable copy so callers cannot mutate this action after
 *     construction
 */
public record CreateObjectAction(String typeIri, List<PropertyAssignment> properties) implements Action {

    public CreateObjectAction {
        if (typeIri == null || typeIri.isBlank()) {
            throw new IllegalArgumentException("CreateObjectAction typeIri must not be blank");
        }
        Objects.requireNonNull(properties, "properties must not be null");
        properties = List.copyOf(properties);
    }
}
