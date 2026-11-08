package org.sequeless.spi.ontology;

/**
 * How an {@link OntologyDocument} passed to {@link OntologyAdministration#importDocument} is applied to the
 * ontology already loaded. Phase 1 supports only {@link #REPLACE} — the mode {@link
 * OntologyAdministration}'s export/import round-trip clause relies on — leaving room for a future {@code
 * MERGE} mode that layers a document onto the existing ontology instead of replacing it wholesale.
 */
public enum ImportMode {

    /**
     * Discard the ontology currently loaded and rebuild the ontology entirely from the imported
     * document.
     */
    REPLACE
}
