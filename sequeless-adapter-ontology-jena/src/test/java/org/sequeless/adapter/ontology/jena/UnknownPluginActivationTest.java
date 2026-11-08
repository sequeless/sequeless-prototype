package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.testkit.Fixtures;

/**
 * Proves the T4 acceptance criterion — "Unknown pluginName in the ontology is reported at snapshot
 * activation, not at read time" — at the {@link JenaOntologyPort} level, against inline Turtle
 * fixtures declaring a {@code sq:Plugin} rule whose {@code sq:pluginName} has no registered {@code
 * org.sequeless.spi.derivation.DerivationPlugin} on the classpath. This module's own test classpath
 * registers none; the only one registered is the testkit's sample plugin, named {@code "workload"},
 * so every {@code pluginName} used below is guaranteed unknown without needing a specially-chosen
 * name.
 *
 * <p>These are deliberately activation-level tests, not {@code SnapshotMapper}/{@code
 * DerivationMappingTest}-level ones: they assert the documented behaviour of {@link
 * JenaOntologyPort#fromDocument}, the ontology reads and {@link
 * JenaOntologyPort#importDocument} — an {@link OntologyException} thrown at activation — not merely
 * that some report field ends up set.
 */
class UnknownPluginActivationTest {

    private static final Scope SCOPE = Fixtures.defaultScope();
    private static final TenantId TENANT = TenantId.DEFAULT;
    private static final String REF_NS = "https://sequeless.dev/ns/ref#";
    private static final String WORKLOAD_IRI = REF_NS + "workload";
    private static final String UNREGISTERED_PLUGIN_NAME = "totally-unregistered-plugin";

    private static final String UNKNOWN_PLUGIN_TURTLE = """
        @prefix sq:   <https://sequeless.dev/ns/meta#> .
        @prefix owl:  <http://www.w3.org/2002/07/owl#> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
        @prefix ex:   <https://sequeless.dev/ns/ref#> .

        <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

        ex:Person a owl:Class .

        ex:workload
            a owl:DatatypeProperty ;
            rdfs:domain ex:Person ;
            rdfs:range xsd:decimal ;
            sq:derivedBy [ a sq:Plugin ; sq:pluginName "totally-unregistered-plugin" ] .
        """;

    @Test
    void fromDocumentThenReadThrowsForAnUnknownPluginName() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                new OntologyDocument(UNKNOWN_PLUGIN_TURTLE, OntologyFormat.TURTLE), ReasonerSetting.NONE,
                () -> Set.of("workload"));

        // fromDocument itself never throws, even for a bad document (F20) -- the failure surfaces
        // only once something asks this port to actually serve what it built, exactly like the
        // existing reserved-term and inconsistent-ontology activation tests.
        assertThatThrownBy(() -> port.schema(TENANT))
                .isInstanceOf(OntologyException.class)
                .satisfies(thrown -> {
                    OntologyException exception = (OntologyException) thrown;
                    assertThat(exception.report().consistent()).isFalse();
                    assertThat(exception.report().issues())
                            .anyMatch(issue -> isUnknownWorkloadPluginIssue(issue, WORKLOAD_IRI));
                });
    }

    @Test
    void importDocumentRejectsAnUnknownPluginNameAndLeavesTheActivePortUnchanged() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.NONE, () -> Set.of("workload"));
        Halves before = Halves.of(port);

        assertThatThrownBy(() -> port.importDocument(
                        SCOPE, new OntologyDocument(UNKNOWN_PLUGIN_TURTLE, OntologyFormat.TURTLE), ImportMode.REPLACE))
                .isInstanceOf(OntologyException.class)
                .satisfies(thrown -> {
                    OntologyException exception = (OntologyException) thrown;
                    assertThat(exception.report().consistent()).isFalse();
                    assertThat(exception.report().issues())
                            .anyMatch(issue -> isUnknownWorkloadPluginIssue(issue, WORKLOAD_IRI));
                });

        // The rejected import must never have replaced this port's active halves, version included.
        Halves after = Halves.of(port);
        assertThat(after).isEqualTo(before);
        assertThat(after.schema().ontologyIri()).isEqualTo(before.schema().ontologyIri());
        assertThat(after.schema().type(Fixtures.TASK_IRI)).isPresent();
    }

    @Test
    void propertyInheritedByMultipleSubtypesProducesExactlyOneIssue() {
        String turtle = """
            @prefix sq:   <https://sequeless.dev/ns/meta#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix ex:   <https://sequeless.dev/ns/ref#> .

            <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

            ex:Person a owl:Class .
            ex:Employee a owl:Class ; rdfs:subClassOf ex:Person .
            ex:Contractor a owl:Class ; rdfs:subClassOf ex:Person .

            ex:workload
                a owl:DatatypeProperty ;
                rdfs:domain ex:Person ;
                rdfs:range xsd:decimal ;
                sq:derivedBy [ a sq:Plugin ; sq:pluginName "totally-unregistered-plugin" ] .
            """;
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                new OntologyDocument(turtle, OntologyFormat.TURTLE), ReasonerSetting.NONE, () -> Set.of("workload"));

        assertThatThrownBy(() -> port.schema(TENANT))
                .isInstanceOf(OntologyException.class)
                .satisfies(thrown -> {
                    OntologyException exception = (OntologyException) thrown;
                    // ex:workload is attributed to Person, Employee AND Contractor (inheritance), but
                    // the unknown-plugin check must be deduplicated by property IRI: exactly one issue,
                    // not three.
                    long matches = exception.report().issues().stream()
                            .filter(issue -> isUnknownWorkloadPluginIssue(issue, WORKLOAD_IRI))
                            .count();
                    assertThat(matches).isEqualTo(1);
                });
    }

    private static boolean isUnknownWorkloadPluginIssue(OntologyIssue issue, String propertyIri) {
        return issue.severity() == Severity.ERROR
                && issue.subjectIri().equals(Optional.of(propertyIri))
                && issue.message()
                        .equals("sq:pluginName '" + UNREGISTERED_PLUGIN_NAME
                                + "' has no registered DerivationPlugin on the classpath.");
    }
}
