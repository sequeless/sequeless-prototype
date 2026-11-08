package org.sequeless.spi.meta.schema;

import java.util.List;
import java.util.Objects;

/**
 * A single OWL class as the application sees it: its supertypes, the properties attributed to it,
 * its display hints, and whether it is abstract. See the OWL → snapshot mapping table for how each
 * component is derived, and note in particular that {@code superTypes} is reasoner-dependent (see
 * "What the reasoner setting changes" in the vocabulary specification) while {@code properties}
 * attribution is not. A type's state machine is not part of this record: it is held by the
 * behaviour half, keyed by this type's IRI.
 *
 * @param iri the type's IRI; must not be blank
 * @param label the type's display label; must not be blank
 * @param superTypes the IRIs of this type's named superclasses; must not be {@code null}; returned
 *     as an unmodifiable copy so callers cannot mutate this type after construction
 * @param properties the properties attributed to this type; must not be {@code null}; returned as
 *     an unmodifiable copy so callers cannot mutate this type after construction
 * @param displayHints this type's ordering, grouping, and visibility hints; must not be {@code
 *     null}
 * @param isAbstract whether this type exists purely for other types to specialise and should not
 *     be offered as directly instantiable
 */
public record TypeDefinition(
        String iri,
        String label,
        List<String> superTypes,
        List<PropertyDefinition> properties,
        DisplayHints displayHints,
        boolean isAbstract) {

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
    }
}
