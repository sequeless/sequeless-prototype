package org.sequeless.spi.meta;

import java.util.Optional;

/**
 * A property declared on a {@link TypeDefinition}: either an {@link AttributeDefinition} (a
 * scalar-valued property, {@code rdfs:range} a datatype) or a {@link RelationshipDefinition} (an
 * object-valued property, {@code rdfs:range} another type). The two are sealed to exactly these
 * two permitted implementations — every property in an OWL ontology is one or the other, never
 * both — so a {@code switch} over {@code PropertyDefinition} is exhaustive without a default case,
 * and adding a third kind of property is a deliberate, compiler-enforced decision rather than
 * something that can silently fall through existing code.
 *
 * <p>{@code permits} is declared explicitly rather than left implicit because {@link
 * AttributeDefinition} and {@link RelationshipDefinition} live in their own files, not nested
 * inside this one; implicit permits only works for subtypes nested in the sealed type's own file.
 */
public sealed interface PropertyDefinition permits AttributeDefinition, RelationshipDefinition {

    /**
     * @return the property's IRI; never blank
     */
    String iri();

    /**
     * @return the property's display label, resolved per the {@code sq:label} /
     *     {@code rdfs:label} / IRI-local-name priority order; never blank
     */
    String label();

    /**
     * @return how many values this property may hold
     */
    Cardinality cardinality();

    /**
     * @return whether this property is usable as a facet in search/filtering
     */
    boolean facet();

    /**
     * @return whether this property's values are indexed
     */
    boolean indexed();

    /**
     * @return whether this property's values are full-text searchable
     */
    boolean searchable();

    /**
     * @return whether this property is computed and not writable through the generic API
     */
    boolean readOnly();

    /**
     * @return this property's ordering, grouping, and visibility hints
     */
    DisplayHints displayHints();

    /**
     * @return the rule that derives this property's value, if it is a derived property; {@link
     *     Optional#empty()} for an ordinary stored property
     */
    Optional<DerivationRule> derivation();

    /**
     * @return {@link #displayHints()}' {@code order()} — a convenience so callers sorting
     *     properties do not need to unwrap {@link #displayHints()} themselves
     */
    default int order() {
        return displayHints().order();
    }
}
