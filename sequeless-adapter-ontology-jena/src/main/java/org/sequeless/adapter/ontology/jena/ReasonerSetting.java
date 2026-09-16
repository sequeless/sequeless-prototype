package org.sequeless.adapter.ontology.jena;

import org.apache.jena.ontapi.OntSpecification;

/**
 * The three reasoning levels this adapter supports over the type-level ontology (DR-05), each
 * mapped to the {@link OntSpecification} verified empirically against Jena 6.1.0 to produce it.
 * Spring binds {@code sequeless.ontology.reasoner} against these constant names via relaxed
 * binding (e.g. {@code owl}, {@code rdfs}, {@code none}, case-insensitively), so the names
 * themselves are part of the adapter's configuration surface and must not be renamed casually.
 *
 * <p>{@link #NONE} still runs under {@code OntSpecification.OWL2_DL_MEM}, not a bare RDFS/OWL-free
 * model: the ontapi personality is what makes {@code OntClass}/{@code OntModel} accessors usable at
 * all, independent of whether any inference rules run over the resulting graph.
 */
public enum ReasonerSetting {

    /** Full OWL2 DL rule inference: superclass closure, inverse properties, transitivity, etc. */
    OWL(OntSpecification.OWL2_DL_MEM_RULES_INF),

    /** RDFS-only inference: asserted subclass axioms are visible, OWL-specific inference is not. */
    RDFS(OntSpecification.OWL2_DL_MEM_RDFS_INF),

    /** No inference: only explicitly asserted statements are visible. */
    NONE(OntSpecification.OWL2_DL_MEM);

    private final OntSpecification specification;

    ReasonerSetting(OntSpecification specification) {
        this.specification = specification;
    }

    /**
     * @return the {@link OntSpecification} this setting builds {@code OntModel} instances with
     */
    OntSpecification specification() {
        return specification;
    }
}
