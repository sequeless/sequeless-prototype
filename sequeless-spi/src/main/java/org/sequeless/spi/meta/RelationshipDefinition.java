package org.sequeless.spi.meta;

import java.util.Objects;
import java.util.Optional;

/**
 * An object-valued property: an OWL object property whose most-specific declared {@code
 * rdfs:range} is another type. See the OWL → snapshot mapping table for how each component is
 * derived from the ontology.
 *
 * @param iri the property's IRI; must not be blank
 * @param label the property's display label; must not be blank
 * @param cardinality how many values this property may hold; must not be {@code null}
 * @param facet whether this property is usable as a facet in search/filtering
 * @param indexed whether this property's values are indexed
 * @param searchable whether this property's values are full-text searchable
 * @param readOnly whether this property is computed and not writable through the generic API
 * @param displayLabel whether this property's value is used as its type's display label when
 *     rendering a reference to an instance of that type
 * @param displayHints this property's ordering, grouping, and visibility hints; must not be
 *     {@code null}
 * @param derivation the rule that derives this property's value, if any; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 * @param targetTypeIri the IRI of the type this relationship points to; must not be {@code null}
 * @param inverseIri the IRI of this relationship's {@code owl:inverseOf} partner, if declared;
 *     must not be {@code null} (the {@link Optional} wrapper itself, not just its contents)
 * @param transitive whether this relationship is declared {@code owl:TransitiveProperty}
 */
public record RelationshipDefinition(
    String iri,
    String label,
    Cardinality cardinality,
    boolean facet,
    boolean indexed,
    boolean searchable,
    boolean readOnly,
    boolean displayLabel,
    DisplayHints displayHints,
    Optional<DerivationRule> derivation,
    String targetTypeIri,
    Optional<String> inverseIri,
    boolean transitive)
    implements PropertyDefinition {

    public RelationshipDefinition {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("RelationshipDefinition iri must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("RelationshipDefinition label must not be blank");
        }
        Objects.requireNonNull(cardinality, "cardinality must not be null");
        Objects.requireNonNull(displayHints, "displayHints must not be null");
        Objects.requireNonNull(derivation, "derivation must not be null");
        Objects.requireNonNull(targetTypeIri, "targetTypeIri must not be null");
        Objects.requireNonNull(inverseIri, "inverseIri must not be null");
    }
}
