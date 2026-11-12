package org.sequeless.spi.object;

/**
 * The IRI of an ontology type, as carried by a {@link BusinessObject} or returned by {@link
 * ObjectStorePort#typesOf}. This is a plain IRI reference, not a {@code
 * org.sequeless.ontology.schema.api.TypeDefinition} — the object store never needs the full meta-model to
 * store or filter by type, only its identity.
 *
 * @param iri the type's IRI; must not be blank
 */
public record TypeRef(String iri) {

    public TypeRef {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("TypeRef iri must not be blank");
        }
    }
}
