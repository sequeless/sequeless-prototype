package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.testkit.Fixtures;

/**
 * Exercises {@link JenaOntologyPort}'s own construction API and its "build fresh, validate, swap
 * only on success" discipline — reads, {@code reload}, and {@code importDocument} semantics, and the
 * version each adopted activation takes when there is no document store — beyond what {@code
 * OntologyContract} covers generically (that contract is wired up via {@code
 * JenaOntologyPortContractTest}). Also the failed-reload clause, which the contract cannot test
 * because its interfaces cannot make a source inconsistent.
 */
class JenaOntologyPortTest {

    private static final Scope SCOPE = Fixtures.defaultScope();

    @Test
    void fromDocumentNeverThrowsEvenForAnInconsistentDocument() {
        // F20: building must succeed regardless of what the document contains; only the reads (and
        // friends) may throw.
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.inconsistentOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));

        assertThatThrownBy(() -> Halves.of(port)).isInstanceOf(OntologyException.class);
    }

    @Test
    void schemaOfACleanDocumentIsCachedAndStable() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));

        Schema first = port.schema(TenantId.DEFAULT);
        Schema second = port.schema(TenantId.DEFAULT);

        assertThat(first).isEqualTo(second);
        assertThat(first.names()).contains("Task", "Project", "Person", "WorkItem", "Deliverable");
    }

    @Test
    void reloadOnAnUnchangedDocumentKeepsTheContent() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));

        Halves before = Halves.of(port);
        OntologyReport reloaded = port.reload(SCOPE);

        assertThat(reloaded.consistent()).isTrue();
        assertThat(Halves.of(port).withoutVersion()).isEqualTo(before.withoutVersion());
    }

    @Test
    void fromSourceReloadsFromTheConfiguredClasspathLocation() {
        // fromSource(...) exercises the OntologySource-backed loader path, distinct from
        // fromDocument(...)'s in-memory bytes; the adapter's own bundled sq-schema.ttl (the schema
        // half of the sq: vocabulary, consistent on its own) is a small but genuinely valid,
        // consistent ontology, so it doubles as a convenient fixture here without needing a
        // dedicated test-only resource.
        JenaOntologyPort port = JenaOntologyPort.fromSource(
                "test.ontology.source",
                "classpath:ontology/sq-schema.ttl",
                ReasonerSetting.OWL,
                () -> Set.of("workload"));

        Halves before = Halves.of(port);
        assertThat(before.schema().ontologyIri()).isEqualTo(ImportResolver.SQ_META_IRI);

        assertThat(port.reload(SCOPE).consistent()).isTrue();
        assertThat(Halves.of(port).withoutVersion()).isEqualTo(before.withoutVersion());
    }

    @Test
    void fromSourceRejectsAnUnresolvableLocationAtConstructionRatherThanDeferringIt() {
        // A misconfigured sequeless.ontology.source is a startup-time configuration error, not an
        // "inconsistent ontology" -- it must fail fast, unlike F20's deferred-validation guarantee
        // for a document whose *content* is merely inconsistent.
        assertThatThrownBy(() -> JenaOntologyPort.fromSource(
                        "test.ontology.source",
                        "classpath:ontology/does-not-exist.ttl",
                        ReasonerSetting.OWL,
                        () -> Set.of("workload")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.ontology.source");
    }

    @Test
    void failedImportDocumentPreservesThePreviousGoodHalves() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        Halves before = Halves.of(port);

        assertThatThrownBy(() -> port.importDocument(SCOPE, Fixtures.inconsistentOntology(), ImportMode.REPLACE))
                .isInstanceOf(OntologyException.class);

        assertThat(Halves.of(port)).isEqualTo(before);
    }

    /**
     * Hand-off C. The contract's interfaces cannot make a source inconsistent, so this starts from
     * an inconsistent document, replaces it by a good import, and then reloads. Without a store,
     * {@code reload} re-reads the original source, which is still the inconsistent document, so it
     * throws. The three halves, with the version the import gave them, must stay as they were.
     */
    @Test
    void failedReloadLeavesTheThreeHalvesAndTheirVersionUnchanged() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.inconsistentOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        port.importDocument(SCOPE, Fixtures.referenceOntology(), ImportMode.REPLACE);
        Halves before = Halves.of(port);
        assertThat(before.version()).isEqualTo(new OntologyVersion(2));

        assertThatThrownBy(() -> port.reload(SCOPE)).isInstanceOf(OntologyException.class);

        assertThat(Halves.of(port)).isEqualTo(before);

        // The failed reload took no number, so the next accepted activation is 3, not 4.
        port.importDocument(SCOPE, Fixtures.inverseTransitiveOntology(), ImportMode.REPLACE);
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(3));
    }

    @Test
    void successfulImportDocumentReplacesTheLiveState() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        Halves before = Halves.of(port);

        ImportReport report = port.importDocument(SCOPE, Fixtures.inverseTransitiveOntology(), ImportMode.REPLACE);

        assertThat(report.accepted()).isTrue();
        assertThat(report.report().consistent()).isTrue();
        assertThat(report.typeCount())
                .isEqualTo(port.schema(TenantId.DEFAULT).types().size());
        assertThat(Halves.of(port).withoutVersion()).isNotEqualTo(before.withoutVersion());
        assertThat(port.schema(TenantId.DEFAULT).ontologyIri()).isEqualTo(Fixtures.INVERSE_TRANSITIVE_ONTOLOGY_IRI);
    }

    @Test
    void exportThenImportRoundTripsToEqualHalvesWithAGreaterVersion() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        Halves before = Halves.of(port);

        OntologyDocument exported = port.export(SCOPE, OntologyFormat.TURTLE);
        port.importDocument(SCOPE, exported, ImportMode.REPLACE);

        Halves after = Halves.of(port);
        assertThat(after.withoutVersion()).isEqualTo(before.withoutVersion());
        assertThat(after.version().value()).isGreaterThan(before.version().value());
    }

    @Test
    void twoIndependentlyBuiltPortsFromTheSameDocumentAgreeOnTheHalves() {
        JenaOntologyPort first = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        JenaOntologyPort second = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));

        assertThat(Halves.of(first)).isEqualTo(Halves.of(second));
    }

    @Test
    void constructingWithNoneReasonerNeverDetectsTheInconsistentFixtureAsInvalid() {
        // Documents the reasoner=none / rdfs hazard: this is a property of the reasoner, not a
        // defect, and is exactly why the shipped default (JenaOntologyProperties, added in a later
        // task) is `owl`.
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.inconsistentOntology(), ReasonerSetting.NONE, () -> Set.of("workload"));

        assertThat(Halves.of(port)).isNotNull();
        assertThat(port.reload(SCOPE).consistent()).isTrue();
    }

    @Test
    void withoutAStoreTheVersionIsACounterThatOnlySuccessfulActivationsAdvance() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(1));

        port.importDocument(SCOPE, Fixtures.inverseTransitiveOntology(), ImportMode.REPLACE);
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(2));

        port.reload(SCOPE);
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(3));

        // A rejected import takes no number, so the next accepted one is 4, not 5.
        assertThatThrownBy(() -> port.importDocument(SCOPE, Fixtures.inconsistentOntology(), ImportMode.REPLACE))
                .isInstanceOf(OntologyException.class);
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(3));

        port.importDocument(SCOPE, Fixtures.referenceOntology(), ImportMode.REPLACE);
        assertThat(Halves.of(port).version()).isEqualTo(new OntologyVersion(4));
    }
}
