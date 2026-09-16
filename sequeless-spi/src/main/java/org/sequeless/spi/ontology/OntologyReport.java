package org.sequeless.spi.ontology;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The outcome of validating an ontology document: whether it is consistent, and the issues found
 * along the way. This is what {@link org.sequeless.spi.ontology.OntologyPort#snapshot} and {@code
 * reload} attach to {@link OntologyException} when an ontology is inconsistent, and it is also the
 * shape an HTTP 422 body renders directly via {@link #describe()} — which is why an inconsistent
 * report without a single {@link Severity#ERROR} issue is rejected at construction: it would reach
 * a caller as "this is broken" with no explanation of why.
 *
 * @param consistent whether the ontology is consistent
 * @param issues the issues found; must not be {@code null}; if {@code consistent} is {@code false}
 *     this must contain at least one {@link Severity#ERROR} issue
 */
public record OntologyReport(boolean consistent, List<OntologyIssue> issues) {

    public OntologyReport {
        Objects.requireNonNull(issues, "issues must not be null");
        issues = List.copyOf(issues);
        if (!consistent && issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR)) {
            throw new IllegalArgumentException(
                "An inconsistent OntologyReport must contain at least one ERROR issue explaining why");
        }
    }

    /**
     * A deterministic, human-readable rendering of this report, preserving {@link #issues()}
     * order. Used both for logging and as the body of the HTTP 422 response an inconsistent
     * ontology produces.
     *
     * @return {@code "Ontology is consistent."} or {@code "Ontology is inconsistent."} when there
     *     are no issues, otherwise a header naming the issue count followed by one indented line
     *     per issue in the form {@code "  [SEVERITY] subjectIri: message"} (the {@code
     *     "subjectIri: "} prefix omitted when absent)
     */
    public String describe() {
        if (issues.isEmpty()) {
            return consistent ? "Ontology is consistent." : "Ontology is inconsistent.";
        }
        String header = "Ontology is " + (consistent ? "consistent" : "inconsistent") + " (" + issues.size()
            + " issue(s)):";
        String lines = issues.stream()
            .map(issue -> "  [" + issue.severity() + "] " + issue.subjectIri().map(iri -> iri + ": ").orElse("")
                + issue.message())
            .collect(Collectors.joining("\n"));
        return header + "\n" + lines;
    }
}
