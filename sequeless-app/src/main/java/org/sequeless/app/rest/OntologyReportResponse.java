package org.sequeless.app.rest;

import java.util.List;
import org.sequeless.spi.ontology.OntologyReport;

/**
 * JSON response body rendered from an {@link OntologyReport} — on the success path, the body of
 * {@code POST /types/reload}; on the failure path, the body of the HTTP 422 {@link
 * ApiExceptionAdvice} produces for an {@code OntologyException}. Rendering the identical shape on
 * both paths means a client only needs one response type to handle either outcome of a reload.
 *
 * @param consistent whether the ontology is consistent
 * @param issues the issues found while validating the ontology
 */
public record OntologyReportResponse(boolean consistent, List<Issue> issues) {

    /**
     * JSON response shape for a single {@code org.sequeless.spi.ontology.OntologyIssue}.
     *
     * @param severity the issue's severity, e.g. {@code "ERROR"} or {@code "WARNING"}
     * @param subjectIri the IRI of the resource the issue concerns, or {@code null} if none
     * @param message a human-readable description of the issue
     */
    public record Issue(String severity, String subjectIri, String message) {}

    /**
     * Maps a core {@link OntologyReport} onto its wire representation.
     *
     * @param report the report to map; must not be {@code null}
     * @return the equivalent {@link OntologyReportResponse}
     */
    public static OntologyReportResponse from(OntologyReport report) {
        List<Issue> issues =
                report.issues().stream()
                        .map(
                                issue ->
                                        new Issue(
                                                issue.severity().name(),
                                                issue.subjectIri().orElse(null),
                                                issue.message()))
                        .toList();
        return new OntologyReportResponse(report.consistent(), issues);
    }
}
