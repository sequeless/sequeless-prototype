package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.testkit.Fixtures;

/**
 * Verifies the behaviour the T8 handback's probe established empirically against the real {@code
 * jena-ontapi:6.1.0} and the committed {@code inconsistent.ttl} fixture: the {@code owl} reasoner
 * detects the disjointness violation and names {@code ex:Cyborg}, the {@code rdfs} reasoner reports
 * the same fixture as valid (RDFS inference never notices {@code owl:disjointWith}), and the {@code
 * none} setting has no reasoner attached at all — {@link ConsistencyChecker} treats that as
 * "nothing to check" rather than propagating the {@code OntJenaException.Unsupported} Jena itself
 * throws.
 */
class ConsistencyCheckerTest {

    @Test
    void owlReasonerFindsTheInconsistencyAndNamesTheCulprit() {
        OntologyReport report = check(Fixtures.inconsistentOntology().content(), ReasonerSetting.OWL);

        assertThat(report.consistent()).isFalse();
        assertThat(report.issues()).isNotEmpty();
        assertThat(report.issues()).anyMatch(issue -> issue.severity() == Severity.ERROR);
        assertThat(report.issues())
            .anyMatch(issue -> issue.subjectIri().map(iri -> iri.endsWith("Cyborg")).orElse(false));
    }

    @Test
    void rdfsReasonerDoesNotDetectThisParticularInconsistency() {
        // Empirically verified: RDFS-only inference has no notion of owl:disjointWith, so the same
        // fixture that owl rejects comes back clean under rdfs. This is a property of the reasoner,
        // not a defect in ConsistencyChecker -- documented here so nobody "fixes" it later by
        // mistake.
        OntologyReport report = check(Fixtures.inconsistentOntology().content(), ReasonerSetting.RDFS);

        assertThat(report.consistent()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void noneSettingHasNoReasonerAttachedAndIsTreatedAsConsistent() {
        // Empirically verified: OntModel.asInferenceModel() throws OntJenaException.Unsupported
        // under ReasonerSetting.NONE's specification -- there is no inference engine to validate
        // with at all. ConsistencyChecker must swallow that, not let it escape as a raw Jena
        // exception.
        OntologyReport report = check(Fixtures.inconsistentOntology().content(), ReasonerSetting.NONE);

        assertThat(report.consistent()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void aCleanOntologyIsReportedConsistentUnderOwl() {
        OntologyReport report = check(Fixtures.referenceOntology().content(), ReasonerSetting.OWL);

        assertThat(report.consistent()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    private static OntologyReport check(String turtle, ReasonerSetting reasoner) {
        return ConsistencyChecker.check(buildModel(turtle, reasoner));
    }

    private static OntModel buildModel(String turtle, ReasonerSetting reasoner) {
        Model rdfModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(
            rdfModel,
            new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
            "urn:sequeless:test",
            Lang.TURTLE);
        return OntModelFactory.createModel(
            rdfModel.getGraph(), reasoner.specification(), ImportResolver.withBundledMeta());
    }
}
