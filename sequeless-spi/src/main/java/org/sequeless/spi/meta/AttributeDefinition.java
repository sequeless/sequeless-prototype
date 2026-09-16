package org.sequeless.spi.meta;

import java.util.Objects;
import java.util.Optional;

/**
 * A scalar-valued property: an OWL datatype property whose most-specific declared {@code
 * rdfs:range} is an XSD datatype rather than another type. See the OWL → snapshot mapping table
 * for how each component is derived from the ontology.
 *
 * @param iri the property's IRI; must not be blank
 * @param label the property's display label; must not be blank
 * @param cardinality how many values this property may hold; must not be {@code null}
 * @param facet whether this property is usable as a facet in search/filtering
 * @param indexed whether this property's values are indexed
 * @param searchable whether this property's values are full-text searchable
 * @param readOnly whether this property is computed and not writable through the generic API
 * @param displayHints this property's ordering, grouping, and visibility hints; must not be
 *     {@code null}
 * @param derivation the rule that derives this property's value, if any; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 * @param datatype the scalar type this property's values hold; must not be {@code null}
 */
public record AttributeDefinition(
    String iri,
    String label,
    Cardinality cardinality,
    boolean facet,
    boolean indexed,
    boolean searchable,
    boolean readOnly,
    DisplayHints displayHints,
    Optional<DerivationRule> derivation,
    Datatype datatype)
    implements PropertyDefinition {

    public AttributeDefinition {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("AttributeDefinition iri must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("AttributeDefinition label must not be blank");
        }
        Objects.requireNonNull(cardinality, "cardinality must not be null");
        Objects.requireNonNull(displayHints, "displayHints must not be null");
        Objects.requireNonNull(derivation, "derivation must not be null");
        Objects.requireNonNull(datatype, "datatype must not be null");
    }
}
