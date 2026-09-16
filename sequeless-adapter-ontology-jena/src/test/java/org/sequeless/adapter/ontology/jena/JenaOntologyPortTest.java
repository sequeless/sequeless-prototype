package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.testkit.Fixtures;

/**
 * Exercises {@link JenaOntologyPort}'s own construction API and its "build fresh, validate, swap
 * only on success" discipline — caching, {@code reload}, and {@code importDocument} semantics —
 * beyond what {@code OntologyContract} covers generically (that contract is T9's job to wire up via
 * {@code JenaOntologyPortContractTest}). This class exists so this task does not hand back the port
 * implementation without having run it against the real fixtures at all.
 */
class JenaOntologyPortTest {

    private static final Scope SCOPE = Fixtures.defaultScope();

    @Test
    void fromDocumentNeverThrowsEvenForAnInconsistentDocument() {
        // F20: building must succeed regardless of what the document contains; only snapshot() (and
        // friends) may throw.
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.inconsistentOntology(), ReasonerSetting.OWL);

        assertThatThrownBy(() -> port.snapshot(SCOPE)).isInstanceOf(OntologyException.class);
    }

    @Test
    void snapshotOfACleanDocumentIsCachedAndStable() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);

        MetaModelSnapshot first = port.snapshot(SCOPE);
        MetaModelSnapshot second = port.snapshot(SCOPE);

        assertThat(first).isEqualTo(second);
        assertThat(first.report().consistent()).isTrue();
        assertThat(first.names()).contains("Task", "Project", "Person", "WorkItem", "Deliverable");
    }

    @Test
    void reloadOnAnUnchangedDocumentReturnsAnEqualSnapshot() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);

        MetaModelSnapshot before = port.snapshot(SCOPE);
        MetaModelSnapshot reloaded = port.reload(SCOPE);

        assertThat(reloaded).isEqualTo(before);
    }

    @Test
    void fromSourceReloadsFromTheConfiguredClasspathLocation() {
        // fromSource(...) exercises the OntologySource-backed loader path, distinct from
        // fromDocument(...)'s in-memory bytes; the adapter's own bundled sq-meta.ttl is a small but
        // genuinely valid, consistent ontology, so it doubles as a convenient fixture here without
        // needing a dedicated test-only resource.
        JenaOntologyPort port = JenaOntologyPort.fromSource(
            "test.ontology.source", "classpath:ontology/sq-meta.ttl", ReasonerSetting.OWL);

        MetaModelSnapshot before = port.snapshot(SCOPE);
        assertThat(before.report().consistent()).isTrue();
        assertThat(before.ontologyIri()).isEqualTo(ImportResolver.SQ_META_IRI);

        MetaModelSnapshot reloaded = port.reload(SCOPE);
        assertThat(reloaded).isEqualTo(before);
    }

    @Test
    void fromSourceRejectsAnUnresolvableLocationAtConstructionRatherThanDeferringIt() {
        // A misconfigured sequeless.ontology.source is a startup-time configuration error, not an
        // "inconsistent ontology" -- it must fail fast, unlike F20's deferred-validation guarantee
        // for a document whose *content* is merely inconsistent.
        assertThatThrownBy(
                () ->
                    JenaOntologyPort.fromSource(
                        "test.ontology.source", "classpath:ontology/does-not-exist.ttl", ReasonerSetting.OWL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("test.ontology.source");
    }

    @Test
    void failedImportDocumentPreservesThePreviousGoodSnapshot() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        MetaModelSnapshot before = port.snapshot(SCOPE);

        assertThatThrownBy(
                () -> port.importDocument(SCOPE, Fixtures.inconsistentOntology(), ImportMode.REPLACE))
            .isInstanceOf(OntologyException.class);

        assertThat(port.snapshot(SCOPE)).isEqualTo(before);
    }

    @Test
    void successfulImportDocumentReplacesTheLiveState() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        MetaModelSnapshot before = port.snapshot(SCOPE);

        ImportReport report =
            port.importDocument(SCOPE, Fixtures.inverseTransitiveOntology(), ImportMode.REPLACE);

        assertThat(report.accepted()).isTrue();
        assertThat(report.report().consistent()).isTrue();
        assertThat(port.snapshot(SCOPE)).isNotEqualTo(before);
        assertThat(port.snapshot(SCOPE).ontologyIri()).isEqualTo(Fixtures.INVERSE_TRANSITIVE_ONTOLOGY_IRI);
    }

    @Test
    void exportThenImportRoundTripsToAnEqualSnapshot() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        MetaModelSnapshot before = port.snapshot(SCOPE);

        OntologyDocument exported = port.export(SCOPE, OntologyFormat.TURTLE);
        port.importDocument(SCOPE, exported, ImportMode.REPLACE);

        assertThat(port.snapshot(SCOPE)).isEqualTo(before);
    }

    @Test
    void twoIndependentlyBuiltPortsFromTheSameDocumentAgreeOnTheSnapshot() {
        JenaOntologyPort first = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        JenaOntologyPort second = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);

        assertThat(first.snapshot(SCOPE)).isEqualTo(second.snapshot(SCOPE));
    }

    @Test
    void constructingWithNoneReasonerNeverDetectsTheInconsistentFixtureAsInvalid() {
        // Documents the reasoner=none / rdfs hazard: this is a property of the reasoner, not a
        // defect, and is exactly why the shipped default (JenaOntologyProperties, added in a later
        // task) is `owl`.
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.inconsistentOntology(), ReasonerSetting.NONE);

        assertThat(port.snapshot(SCOPE).report().consistent()).isTrue();
    }
}
