package org.sequeless.spi.ontology;

import java.util.Objects;
import java.util.Optional;

/**
 * A single finding produced while validating or mapping an ontology document: a severity, an
 * optional subject the finding is about, and a human-readable message. {@code subjectIri} is
 * {@link Optional#empty()} for findings that are not about one particular resource (a malformed
 * document as a whole, for instance) and present when the finding names a specific class, property,
 * or individual — for example the unrecognised-datatype {@link Severity#WARNING} the OWL → snapshot
 * mapping produces, which names the offending property.
 *
 * @param severity how serious the issue is; must not be {@code null}
 * @param subjectIri the IRI of the resource the issue concerns, if any; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 * @param message a human-readable description of the issue; must not be blank
 */
public record OntologyIssue(Severity severity, Optional<String> subjectIri, String message) {

    public OntologyIssue {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(subjectIri, "subjectIri must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("OntologyIssue message must not be blank");
        }
    }
}
