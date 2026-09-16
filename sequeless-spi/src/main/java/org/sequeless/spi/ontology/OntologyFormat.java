package org.sequeless.spi.ontology;

/**
 * The serialisation an {@link OntologyDocument} is written in. Phase 1 supports only {@link
 * #TURTLE} — the format the phase-1 fixtures and the Jena adapter's exporter use — but {@link
 * OntologyDocument} carries this alongside its content from the start so {@link
 * OntologyPort#export} and {@link OntologyPort#importDocument} do not have to change shape when
 * RDF/XML and JSON-LD are added in a later phase.
 */
public enum OntologyFormat {

    /** {@code text/turtle}: the only format Phase 1 reads or writes. */
    TURTLE
}
