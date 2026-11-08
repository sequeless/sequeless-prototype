package org.sequeless.testkit.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.meta.automation.Automation;
import org.sequeless.spi.meta.automation.AutomationSource;
import org.sequeless.spi.meta.behaviour.Behaviour;
import org.sequeless.spi.meta.behaviour.BehaviourSource;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.SchemaSource;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyAdministration;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link OntologyAdministration}'s
 * interface-level javadoc, and of the read-port contract every {@link SchemaSource}, {@link
 * BehaviourSource} and {@link AutomationSource} must satisfy. Every implementation — adapter or test
 * double — is expected to satisfy every clause of those contracts, and this class exercises each
 * clause it can test once, against whatever {@link OntologyPorts} {@link #portFor(OntologyDocument)} supplies for
 * a given fixture document.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #portFor(OntologyDocument)} to return the implementation under test, freshly loaded with the
 * given document:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends OntologyContract {
 *     protected OntologyPorts portFor(OntologyDocument document) {
 *         return OntologyPorts.of(MyAdapter.fromDocument(document));
 *     }
 * }
 * }</pre>
 *
 * <p><b>What this contract deliberately does not check.</b> No assertion here inspects which
 * supertypes a {@code TypeDefinition} reports, whether an object property's inverse is visible, or
 * any other value that depends on which reasoner setting an adapter happens to run with. Every
 * assertion is about shape (non-null, well-formed results), null-safety (rejecting missing
 * arguments), determinism (repeated calls agreeing with <em>each other</em>, not with some fixed
 * expectation), and the structural guarantees the interface javadocs make: a failed import leaves
 * the previous halves intact, export and import round-trip, versions move as the administration
 * port documents, and an inconsistent ontology is always thrown, never returned. A failed reload is
 * not tested here: these interfaces cannot make a port's source inconsistent, so each adapter tests
 * that clause itself. A trivial in-memory
 * port that does no OWL reasoning at all and a full OWL reasoner must both be able to pass this
 * contract unmodified; reasoner-specific assertions (which superTypes appear, whether an inverse is
 * inferred) belong to an adapter's own test suite, never here. A contract that only a reasoning
 * implementation could pass would make "this adapter passes the shared contract" a worthless claim.
 */
public abstract class OntologyContract {

    /**
     * Builds the implementation under test for one fixture document.
     *
     * @param document the document the returned ports' current halves must reflect exactly
     * @return the four ports of one implementation under test, freshly loaded from {@code document};
     *     invoked fresh for every {@code @Test} method and every call within a test method that
     *     needs an independent port
     */
    protected abstract OntologyPorts portFor(OntologyDocument document);

    @Test
    void schemaRejectsNullTenant() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException().isThrownBy(() -> ports.schemaSource().schema(null));
    }

    @Test
    void behaviourRejectsNullTenant() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
                .isThrownBy(() -> ports.behaviourSource().behaviour(null));
    }

    @Test
    void automationRejectsNullTenant() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
                .isThrownBy(() -> ports.automationSource().automation(null));
    }

    @Test
    void reloadRejectsNullScope() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException().isThrownBy(() -> ports.administration().reload(null));
    }

    @Test
    void exportRejectsNullScope() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException().isThrownBy(() -> ports.administration().export(null, OntologyFormat.TURTLE));
    }

    @Test
    void exportRejectsNullFormat() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException().isThrownBy(() -> ports.administration().export(Fixtures.defaultScope(), null));
    }

    @Test
    void importDocumentRejectsNullScope() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
                .isThrownBy(() ->
                        ports.administration().importDocument(null, Fixtures.referenceOntology(), ImportMode.REPLACE));
    }

    @Test
    void importDocumentRejectsNullDocument() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
                .isThrownBy(
                        () -> ports.administration().importDocument(Fixtures.defaultScope(), null, ImportMode.REPLACE));
    }

    @Test
    void importDocumentRejectsNullMode() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
                .isThrownBy(() -> ports.administration()
                        .importDocument(Fixtures.defaultScope(), Fixtures.referenceOntology(), null));
    }

    @Test
    void readsAreDeterministicAndExportDoesNotChangeThem() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());

        Halves first = Halves.read(ports);
        Halves second = Halves.read(ports);
        assertThat(second).isEqualTo(first);

        ports.administration().export(Fixtures.defaultScope(), OntologyFormat.TURTLE);
        assertThat(Halves.read(ports)).isEqualTo(first);
    }

    @Test
    void failedImportLeavesTheHalvesAndTheirVersionUnchanged() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();

        Halves before = Halves.read(ports);

        assertThatThrownBy(() -> ports.administration()
                        .importDocument(scope, Fixtures.inconsistentOntology(), ImportMode.REPLACE))
                .isInstanceOf(OntologyException.class);

        assertThat(Halves.read(ports)).isEqualTo(before);
    }

    @Test
    void reloadOnAnUnchangedSourceGivesAConsistentReportAndKeepsTheHalves() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();

        Halves before = Halves.read(ports);
        OntologyReport report = ports.administration().reload(scope);
        Halves after = Halves.read(ports);

        assertThat(report.consistent()).isTrue();
        assertThat(after.withoutVersion()).isEqualTo(before.withoutVersion());
        assertThat(after.version().value())
                .isGreaterThanOrEqualTo(before.version().value());
    }

    @Test
    void exportThenImportKeepsTheContentAndGivesAGreaterVersion() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();

        Halves before = Halves.read(ports);
        OntologyDocument exported = ports.administration().export(scope, OntologyFormat.TURTLE);
        ImportReport report = ports.administration().importDocument(scope, exported, ImportMode.REPLACE);

        assertThat(report).isNotNull();
        Halves after = Halves.read(ports);
        assertThat(after.withoutVersion()).isEqualTo(before.withoutVersion());
        assertThat(after.version().value()).isGreaterThan(before.version().value());
    }

    @Test
    void inconsistentOntologyMakesSchemaThrow() {
        OntologyPorts ports = portFor(Fixtures.inconsistentOntology());
        assertInconsistent(() -> ports.schemaSource().schema(TenantId.DEFAULT));
    }

    @Test
    void inconsistentOntologyMakesBehaviourThrow() {
        OntologyPorts ports = portFor(Fixtures.inconsistentOntology());
        assertInconsistent(() -> ports.behaviourSource().behaviour(TenantId.DEFAULT));
    }

    @Test
    void inconsistentOntologyMakesAutomationThrow() {
        OntologyPorts ports = portFor(Fixtures.inconsistentOntology());
        assertInconsistent(() -> ports.automationSource().automation(TenantId.DEFAULT));
    }

    @Test
    void theThreeHalvesShareOneVersionAfterEveryActivation() {
        OntologyPorts ports = portFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();

        Halves.read(ports).assertSharesOneVersion();

        ports.administration().reload(scope);
        Halves.read(ports).assertSharesOneVersion();

        ports.administration().importDocument(scope, Fixtures.referenceOntology(), ImportMode.REPLACE);
        Halves.read(ports).assertSharesOneVersion();
    }

    private static void assertInconsistent(Supplier<?> read) {
        assertThatThrownBy(read::get).isInstanceOf(OntologyException.class).satisfies(thrown -> {
            OntologyException exception = (OntologyException) thrown;
            assertThat(exception.report().consistent()).isFalse();
            assertThat(exception.report().issues()).anyMatch(issue -> issue.severity() == Severity.ERROR);
        });
    }

    /**
     * The three halves that the read ports return for {@link TenantId#DEFAULT}, read together so
     * that a test can compare one activation with another.
     *
     * @param schema the Schema half
     * @param behaviour the Behaviour half
     * @param automation the Automation half
     */
    private record Halves(Schema schema, Behaviour behaviour, Automation automation) {

        static Halves read(OntologyPorts ports) {
            return new Halves(
                    ports.schemaSource().schema(TenantId.DEFAULT),
                    ports.behaviourSource().behaviour(TenantId.DEFAULT),
                    ports.automationSource().automation(TenantId.DEFAULT));
        }

        OntologyVersion version() {
            return schema.ontologyVersion();
        }

        /**
         * @return these halves with every version replaced by version 1, so that two activations
         *     can be compared on their content alone
         */
        Halves withoutVersion() {
            OntologyVersion any = new OntologyVersion(1);
            return new Halves(
                    new Schema(any, schema.ontologyIri(), schema.versionIri(), schema.prefixes(), schema.types()),
                    new Behaviour(any, behaviour.stateMachines(), behaviour.derivations()),
                    new Automation(any, automation.transitionActions()));
        }

        void assertSharesOneVersion() {
            assertThat(behaviour.ontologyVersion()).isEqualTo(version());
            assertThat(automation.ontologyVersion()).isEqualTo(version());
        }
    }
}
