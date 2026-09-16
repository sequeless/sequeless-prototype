package org.sequeless.spi.object;

/**
 * The IRI of an ontology property, used as the key in a {@link BusinessObject}'s {@code
 * properties} map. Like {@link TypeRef}, this is a plain IRI reference, independent of the full
 * meta-model {@code PropertyDefinition}.
 *
 * @param iri the property's IRI; must not be blank
 */
public record PropertyRef(String iri) {

    public PropertyRef {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("PropertyRef iri must not be blank");
        }
    }
}
