package org.sequeless.testkit.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link OntologyPort}'s
 * interface-level javadoc. Every {@link OntologyPort} implementation — adapter or test double —
 * is expected to satisfy every clause of that javadoc, and this class exercises each clause once,
 * against whatever port {@link #portFor(OntologyDocument)} supplies for a given fixture document.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #portFor(OntologyDocument)} to return the implementation under test, freshly loaded with the
 * given document:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends OntologyContract {
 *     protected OntologyPort portFor(OntologyDocument document) {
 *         return MyAdapter.fromDocument(document);
 *     }
 * }
 * }</pre>
 *
 * <p><b>What this contract deliberately does not check.</b> No assertion here inspects which
 * supertypes a {@code TypeDefinition} reports, whether an object property's inverse is visible, or
 * any other value that depends on which reasoner setting an adapter happens to run with. Every
 * assertion is about shape (non-null, well-formed results), null-safety (rejecting missing
 * arguments), determinism (repeated calls agreeing with <em>each other</em>, not with some fixed
 * expectation), and the three structural guarantees {@link OntologyPort}'s javadoc makes: a failed
 * mutation leaves the previous snapshot intact, export and import round-trip, and an inconsistent
 * ontology is always thrown, never returned. A trivial in-memory port that does no OWL reasoning at
 * all and a full OWL reasoner must both be able to pass this contract unmodified; reasoner-specific
 * assertions (which superTypes appear, whether an inverse is inferred) belong to an adapter's own
 * test suite, never here. A contract that only a reasoning implementation could pass would make
 * "this adapter passes the shared contract" a worthless claim.
 */
public abstract class OntologyContract {

    /**
     * @param document the document the returned port's current snapshot must reflect exactly
     * @return an {@link OntologyPort} implementation under test, freshly loaded from {@code
     *     document}; invoked fresh for every {@code @Test} method and every call within a test
     *     method that needs an independent port
     */
    protected abstract OntologyPort portFor(OntologyDocument document);

    @Test
    void snapshotRejectsNullScope() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException().isThrownBy(() -> port.snapshot(null));
    }

    @Test
    void reloadRejectsNullScope() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException().isThrownBy(() -> port.reload(null));
    }

    @Test
    void exportRejectsNullScope() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
            .isThrownBy(() -> port.export(null, OntologyFormat.TURTLE));
    }

    @Test
    void exportRejectsNullFormat() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException().isThrownBy(() -> port.export(Fixtures.defaultScope(), null));
    }

    @Test
    void importDocumentRejectsNullScope() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
            .isThrownBy(
                () ->
                    port.importDocument(
                        null, Fixtures.referenceOntology(), ImportMode.REPLACE));
    }

    @Test
    void importDocumentRejectsNullDocument() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
            .isThrownBy(
                () -> port.importDocument(Fixtures.defaultScope(), null, ImportMode.REPLACE));
    }

    @Test
    void importDocumentRejectsNullMode() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        assertThatNullPointerException()
            .isThrownBy(
                () ->
                    port.importDocument(
                        Fixtures.defaultScope(), Fixtures.referenceOntology(), null));
    }

    @Test
    void snapshotIsDeterministicAcrossRepeatedCalls() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();

        MetaModelSnapshot first = port.snapshot(scope);
        MetaModelSnapshot second = port.snapshot(scope);
        assertThat(second).isEqualTo(first);

        port.export(scope, OntologyFormat.TURTLE);
        MetaModelSnapshot third = port.snapshot(scope);
        assertThat(third).isEqualTo(first);
    }

    @Test
    void failedImportPreservesThePreviousSnapshot() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();

        MetaModelSnapshot before = port.snapshot(scope);

        assertThatThrownBy(
                () ->
                    port.importDocument(
                        scope, Fixtures.inconsistentOntology(), ImportMode.REPLACE))
            .isInstanceOf(OntologyException.class);

        assertThat(port.snapshot(scope)).isEqualTo(before);
    }

    @Test
    void reloadOnAnUnchangedSourceMatchesSnapshot() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();

        MetaModelSnapshot snapshot = port.snapshot(scope);
        MetaModelSnapshot reloaded = port.reload(scope);
        assertThat(reloaded).isEqualTo(snapshot);
    }

    @Test
    void exportThenImportRoundTrips() {
        OntologyPort port = portFor(Fixtures.referenceOntology());
        Scope scope = Fixtures.defaultScope();

        MetaModelSnapshot before = port.snapshot(scope);
        OntologyDocument exported = port.export(scope, OntologyFormat.TURTLE);
        ImportReport report = port.importDocument(scope, exported, ImportMode.REPLACE);

        assertThat(report).isNotNull();
        assertThat(port.snapshot(scope)).isEqualTo(before);
    }

    @Test
    void inconsistentOntologyMakesSnapshotThrow() {
        OntologyPort port = portFor(Fixtures.inconsistentOntology());
        Scope scope = Fixtures.defaultScope();

        assertThatThrownBy(() -> port.snapshot(scope))
            .isInstanceOf(OntologyException.class)
            .satisfies(
                thrown -> {
                    OntologyException exception = (OntologyException) thrown;
                    assertThat(exception.report().consistent()).isFalse();
                    assertThat(exception.report().issues())
                        .anyMatch(issue -> issue.severity() == Severity.ERROR);
                });
    }
}
