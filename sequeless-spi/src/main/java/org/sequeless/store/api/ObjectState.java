package org.sequeless.store.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.object.PropertyRef;

/**
 * The part of an object that the Command side reads to decide which transitions are available: the
 * IRI of its ontology type, its state-machine state if the type has one, and its property values.
 * Identity, tenant, version, audit and deletion are not needed for that decision, so they are not
 * carried here.
 *
 * <p>Carries no reference to the query side's object view. A caller that holds a view builds this
 * type from the view's type, state and properties at its own boundary.
 *
 * @param typeIri the IRI of the object's ontology type; must not be {@code null}
 * @param state the object's state-machine state, if the type has one; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 * @param properties the object's property values, keyed by property; must not be {@code null};
 *     returned as an unmodifiable copy so callers cannot mutate the state after construction
 */
public record ObjectState(String typeIri, Optional<String> state, Map<PropertyRef, Value> properties) {

    public ObjectState {
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        properties = Map.copyOf(properties);
    }
}
