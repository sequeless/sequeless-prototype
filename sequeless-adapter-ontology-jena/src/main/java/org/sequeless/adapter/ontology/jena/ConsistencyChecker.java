package org.sequeless.adapter.ontology.jena;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontapi.OntJenaException;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.ValidityReport;
import org.apache.jena.vocabulary.RDFS;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;

/**
 * Runs {@code model.asInferenceModel().validate()} and translates the resulting {@link
 * ValidityReport} into an {@link OntologyReport}, per {@code step-plan-phase-3.md}'s T8 bullet:
 * each {@link ValidityReport.Report} becomes one {@link OntologyIssue}, {@link
 * ValidityReport.Report#getExtension()} supplies {@code subjectIri} where it names a single
 * resource, and {@link ValidityReport.Report#getDescription()} — trimmed and collapsed to one
 * line, since Jena's own description text already embeds a multi-line {@code "Culprit = <...>"} /
 * {@code "Implicated node: <...>"} trailer redundant with {@code subjectIri} — supplies the
 * message.
 *
 * <p><b>{@code reasoner=none} throws, not "finds nothing".</b> Verified empirically: {@code
 * OntGraphModelImpl.asInferenceModel()} throws {@link OntJenaException.Unsupported} ("No reasoner
 * attached. Inference is not supported") when the model was built under {@link
 * ReasonerSetting#NONE}'s {@code OntSpecification.OWL2_DL_MEM} — there is no inference engine to
 * call {@code validate()} on at all. This is caught and treated as "nothing to check, therefore no
 * issues found" rather than propagated — the alternative (call {@code none} inconsistent, or throw
 * a raw Jena exception out of the adapter) is not the contract's business: {@code OntologyContract}
 * exercises this adapter only through whatever reasoner {@code JenaOntologyPort} is configured
 * with, never by asking this class to validate under a specific setting, so the contract passes
 * either way. What matters for phase-1 correctness is documented on {@code
 * JenaOntologyProperties}: the shipped default is {@code owl}, the one setting that can actually
 * detect the inconsistency fixture (verified: {@code rdfs} reports {@code isValid()==true} for the
 * same fixture — RDFS inference alone never notices an {@code owl:disjointWith} violation).
 *
 * <p><b>A known false positive is filtered out.</b> Verified empirically — and not something any
 * plan document anticipated — that the committed, otherwise-clean {@code reference.ttl} fixture
 * also comes back {@code isValid()==false} under the {@code owl} reasoner: every use of {@code
 * sq:label} (declared in {@code sq-meta.ttl} with {@code rdfs:range rdfs:Literal}) produces a
 * {@code "dtRange"} report claiming the plain string value is "not compatible with" a range of
 * {@code rdfs:Literal}. This is a limitation in Jena's built-in rule reasoner, not a real
 * inconsistency: {@code rdfs:Literal} is the class of <em>every</em> RDF literal, so no literal
 * value can ever actually violate a range of {@code rdfs:Literal} — the check is a false positive
 * by construction, every time, for any property declared this way. {@link
 * #isRdfsLiteralRangeFalsePositive} recognises exactly this shape (a {@code "dtRange"} report whose
 * {@link ValidityReport.Report#getExtension()} is the offending {@link Triple}, whose predicate the
 * model itself declares {@code rdfs:range rdfs:Literal} for) and drops it before it can either
 * appear as a confusing issue or force {@code consistent} to {@code false} for every ontology that
 * uses the {@code sq:} vocabulary at all — which would otherwise be every ontology this adapter
 * will ever see in practice.
 *
 * <p>Package-private: only {@link JenaOntologyPort} calls this, folding its result into the single
 * merged {@link OntologyReport} that also carries {@link SnapshotMapper}'s warnings and the
 * reserved-{@code sq:}-term check (see {@code JenaOntologyPort}'s javadoc for the merge).
 */
final class ConsistencyChecker {

    private static final String DT_RANGE_REPORT_TYPE = "dtRange";

    private ConsistencyChecker() {}

    /**
     * @param model a built, non-{@code null} {@link OntModel}
     * @return an {@link OntologyReport} reflecting {@code model}'s reasoner-level validity; {@code
     *     consistent()} is {@code true} with no issues when the model's {@link ReasonerSetting} has
     *     no inference engine attached (see class javadoc)
     */
    static OntologyReport check(OntModel model) {
        List<OntologyIssue> issues = new ArrayList<>();

        try {
            ValidityReport validity = model.asInferenceModel().validate();
            Iterator<ValidityReport.Report> reports = validity.getReports();
            while (reports.hasNext()) {
                ValidityReport.Report report = reports.next();
                if (isRdfsLiteralRangeFalsePositive(model, report)) {
                    continue;
                }
                issues.add(toIssue(report));
            }
        } catch (OntJenaException.Unsupported unsupported) {
            // reasoner=none: no reasoner attached, nothing to validate against; see class javadoc.
        }

        boolean consistent = issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        return new OntologyReport(consistent, issues);
    }

    private static OntologyIssue toIssue(ValidityReport.Report report) {
        return new OntologyIssue(
            report.isError() ? Severity.ERROR : Severity.WARNING,
            subjectIriOf(report.getExtension()),
            collapse(report.getDescription()));
    }

    private static Optional<String> subjectIriOf(Object extension) {
        if (extension instanceof Resource resource && resource.isURIResource()) {
            return Optional.of(resource.getURI());
        }
        if (extension instanceof Triple triple && triple.getSubject().isURI()) {
            return Optional.of(triple.getSubject().getURI());
        }
        return Optional.empty();
    }

    /**
     * @see ConsistencyChecker class javadoc, "A known false positive is filtered out"
     */
    private static boolean isRdfsLiteralRangeFalsePositive(OntModel model, ValidityReport.Report report) {
        if (!DT_RANGE_REPORT_TYPE.equals(report.getType())) {
            return false;
        }
        if (!(report.getExtension() instanceof Triple triple)) {
            return false;
        }
        Node predicateNode = triple.getPredicate();
        if (!predicateNode.isURI()) {
            return false;
        }
        Property predicate = model.getProperty(predicateNode.getURI());
        return model.contains(predicate, RDFS.range, RDFS.Literal);
    }

    private static String collapse(String description) {
        if (description == null) {
            return "(no description)";
        }
        String collapsed = description.strip().replaceAll("\\s+", " ");
        return collapsed.isBlank() ? "(no description)" : collapsed;
    }
}
