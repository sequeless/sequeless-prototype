package org.sequeless.spi.ontology;

import java.util.Objects;

/**
 * The outcome of an {@link OntologyPort#importDocument} call: whether the imported document was
 * accepted, the {@link OntologyReport} produced while validating it, and how many types the
 * resulting snapshot contains. {@code accepted} is {@code false} only when the caller opted into
 * seeing a rejection rather than an {@link OntologyException} — the port contract itself throws on
 * inconsistency, so a conforming implementation's {@code importDocument} either returns an accepted
 * report or throws; it does not return a rejected one.
 *
 * @param accepted whether the imported document was applied
 * @param report the outcome of validating the imported document; must not be {@code null}
 * @param typeCount the number of types in the resulting snapshot; must not be negative
 */
public record ImportReport(boolean accepted, OntologyReport report, int typeCount) {

    public ImportReport {
        Objects.requireNonNull(report, "report must not be null");
        if (typeCount < 0) {
            throw new IllegalArgumentException("ImportReport typeCount must not be negative");
        }
    }
}
