package org.sequeless.spi.meta;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single OWL class as the application sees it: its supertypes, the properties attributed to it,
 * its display hints, whether it is abstract, and its state machine if it has one. See the OWL →
 * snapshot mapping table for how each component is derived, and note in particular that {@code
 * superTypes} is reasoner-dependent (see "What the reasoner setting changes" in the vocabulary
 * specification) while {@code properties} attribution is not.
 *
 * @param iri the type's IRI; must not be blank
 * @param label the type's display label; must not be blank
 * @param superTypes the IRIs of this type's named superclasses; must not be {@code null}; returned
 *     as an unmodifiable copy so callers cannot mutate a snapshot after construction
 * @param properties the properties attributed to this type; must not be {@code null}; returned as
 *     an unmodifiable copy so callers cannot mutate a snapshot after construction
 * @param displayHints this type's ordering, grouping, and visibility hints; must not be {@code
 *     null}
 * @param isAbstract whether this type exists purely for other types to specialise and should not
 *     be offered as directly instantiable
 * @param stateMachine this type's state machine, if it has one; must not be {@code null} (the
 *     {@link Optional} wrapper itself, not just its contents)
 */
public record TypeDefinition(
    String iri,
    String label,
    List<String> superTypes,
    List<PropertyDefinition> properties,
    DisplayHints displayHints,
    boolean isAbstract,
    Optional<StateMachineDefinition> stateMachine) {

    public TypeDefinition {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("TypeDefinition iri must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("TypeDefinition label must not be blank");
        }
        Objects.requireNonNull(superTypes, "superTypes must not be null");
        superTypes = List.copyOf(superTypes);
        Objects.requireNonNull(properties, "properties must not be null");
        properties = List.copyOf(properties);
        Objects.requireNonNull(displayHints, "displayHints must not be null");
        Objects.requireNonNull(stateMachine, "stateMachine must not be null");
    }
}
