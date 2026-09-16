package org.sequeless.spi.ontology;

/**
 * How serious a single {@link OntologyIssue} is. An ontology can be inconsistent for reasons an
 * application must refuse to operate on ({@link #ERROR}) or merely worth surfacing without
 * blocking anything ({@link #WARNING}) — for example the unrecognised-datatype fallback the OWL →
 * snapshot mapping documents, which degrades gracefully to {@code Datatype.STRING} rather than
 * failing the whole import.
 */
public enum Severity {

    /** The issue makes the ontology unusable as-is; at least one {@code ERROR} forces {@code
     * OntologyReport.consistent()} to be {@code false}. */
    ERROR,

    /** The issue is worth surfacing but does not by itself make the ontology inconsistent. */
    WARNING
}
