package org.sequeless.app.rest;

import java.util.List;
import org.sequeless.spi.ontology.ImportReport;

/**
 * JSON response body rendered from an {@link ImportReport} — the body of {@code POST /ontology} on
 * its accepted path. An inconsistent import never reaches this type: {@link
 * org.sequeless.core.api.OntologyAdministration#importTurtle} throws {@code OntologyException}
 * first, which the existing {@link ApiExceptionAdvice#handleOntologyException} renders as a 422
 * {@link OntologyReportResponse} instead — see plan.md §8.
 *
 * @param accepted whether the imported document was applied; always {@code true} when this type is
 *     rendered at all, per {@link ImportReport}'s own contract
 * @param typeCount the number of types in the resulting snapshot
 * @param consistent whether the resulting ontology is consistent; always {@code true} here, for the
 *     same reason {@code accepted} is
 * @param issues the issues found while validating the imported ontology (warnings only, since an
 *     error-level issue would have made the ontology inconsistent and thrown instead)
 */
public record OntologyImportResponse(
        boolean accepted, int typeCount, boolean consistent, List<OntologyReportResponse.Issue> issues) {

    /**
     * Maps a core {@link ImportReport} onto its wire representation, reusing {@link
     * OntologyReportResponse}'s own rendering of {@link org.sequeless.spi.ontology.OntologyReport}
     * rather than duplicating it.
     *
     * @param report the report to map; must not be {@code null}
     * @return the equivalent {@link OntologyImportResponse}
     */
    public static OntologyImportResponse from(ImportReport report) {
        OntologyReportResponse rendered = OntologyReportResponse.from(report.report());
        return new OntologyImportResponse(
                report.accepted(), report.typeCount(), rendered.consistent(), rendered.issues());
    }
}
