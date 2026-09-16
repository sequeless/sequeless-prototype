package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.testkit.Fixtures;

/**
 * Proves, at the {@link JenaOntologyPort} level (not the lower-level {@code SnapshotMapper} /
 * {@code ConsistencyChecker} unit tests T8 already wrote), the three acceptance criteria the whole
 * phase is stated against, plus every other clause the step plan's §6 test list calls for that is
 * not already covered by an existing T7/T8 test class:
 *
 * <ul>
 *   <li>the reference snapshot shows {@code Task} with {@code superTypes} containing {@code
 *       WorkItem}, {@code hasTask} inferred as the inverse of {@code belongsToProject}, and labels
 *       and display hints populated (acceptance criterion 1);
 *   <li>the inconsistent ontology is rejected with an {@code OntologyReport} that names the
 *       offending class, {@code ex:Cyborg} (acceptance criterion 2);
 *   <li>{@code reasoner=none} yields a snapshot without inferred {@code superTypes} — {@code
 *       Task.superTypes()} contains {@code WorkItem} but not {@code Deliverable} (acceptance
 *       criterion 3).
 * </ul>
 */
class JenaOntologyPortAcceptanceTest {

    private static final Scope SCOPE = Fixtures.defaultScope();

    /**
     * {@code inverse-transitive.ttl} reuses the same {@code ex:} namespace as {@code reference.ttl}
     * (see its header comment), but {@link Fixtures} exposes only the {@code REFERENCE_*}-fixture
     * IRI constants, not the {@code Component}/{@code partOf}/{@code parentOf}/{@code childOf} terms
     * unique to this fixture. These are derived off {@link Fixtures#REFERENCE_ONTOLOGY_IRI} rather
     * than written as raw literals, the same way {@code Fixtures} itself builds its own constants
     * off that namespace.
     */
    private static final String REF_NS = Fixtures.REFERENCE_ONTOLOGY_IRI + "#";

    private static final String COMPONENT_IRI = REF_NS + "Component";
    private static final String PART_OF_IRI = REF_NS + "partOf";
    private static final String PARENT_OF_IRI = REF_NS + "parentOf";
    private static final String CHILD_OF_IRI = REF_NS + "childOf";

    @Test
    void referenceSnapshotProvesAcceptanceCriterion1() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        MetaModelSnapshot snapshot = port.snapshot(SCOPE);

        TypeDefinition task = type(snapshot, Fixtures.TASK_IRI);
        assertThat(task.superTypes()).contains(Fixtures.WORK_ITEM_IRI);
        assertThat(task.label()).isEqualTo("Task");
        assertThat(task.displayHints()).isNotNull();

        TypeDefinition project = type(snapshot, Fixtures.PROJECT_IRI);
        RelationshipDefinition hasTask = relationship(project, Fixtures.HAS_TASK_IRI);
        assertThat(hasTask.inverseIri()).contains(Fixtures.BELONGS_TO_PROJECT_IRI);
        assertThat(hasTask.label()).isEqualTo("Has Task");

        AttributeDefinition title = attribute(task, Fixtures.TITLE_IRI);
        assertThat(title.displayHints().order()).isEqualTo(1);
        assertThat(title.displayHints().group()).contains("General");
    }

    @Test
    void inconsistentOntologyReportNamesTheOffendingClassProvingAcceptanceCriterion2() {
        JenaOntologyPort port =
            JenaOntologyPort.fromDocument(Fixtures.inconsistentOntology(), ReasonerSetting.OWL);

        assertThatThrownBy(() -> port.snapshot(SCOPE))
            .isInstanceOf(OntologyException.class)
            .satisfies(
                thrown -> {
                    OntologyException exception = (OntologyException) thrown;
                    assertThat(exception.report().consistent()).isFalse();
                    assertThat(exception.report().issues())
                        .anyMatch(issue -> issue.subjectIri().map(iri -> iri.endsWith("Cyborg")).orElse(false));
                });
    }

    @Test
    void reasonerNoneOmitsInferredSuperTypesProvingAcceptanceCriterion3() {
        JenaOntologyPort owlPort = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        JenaOntologyPort nonePort = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.NONE);

        TypeDefinition taskUnderOwl = type(owlPort.snapshot(SCOPE), Fixtures.TASK_IRI);
        assertThat(taskUnderOwl.superTypes()).contains(Fixtures.WORK_ITEM_IRI, Fixtures.DELIVERABLE_IRI);

        TypeDefinition taskUnderNone = type(nonePort.snapshot(SCOPE), Fixtures.TASK_IRI);
        assertThat(taskUnderNone.superTypes()).contains(Fixtures.WORK_ITEM_IRI);
        assertThat(taskUnderNone.superTypes()).doesNotContain(Fixtures.DELIVERABLE_IRI);
    }

    @Test
    void inverseIsVisibleOnlyUnderTheOwlReasoner() {
        JenaOntologyPort owlPort = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        JenaOntologyPort nonePort = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.NONE);

        TypeDefinition projectUnderOwl = type(owlPort.snapshot(SCOPE), Fixtures.PROJECT_IRI);
        assertThat(relationship(projectUnderOwl, Fixtures.HAS_TASK_IRI).inverseIri())
            .contains(Fixtures.BELONGS_TO_PROJECT_IRI);

        TypeDefinition projectUnderNone = type(nonePort.snapshot(SCOPE), Fixtures.PROJECT_IRI);
        assertThat(relationship(projectUnderNone, Fixtures.HAS_TASK_IRI).inverseIri()).isEmpty();
    }

    @Test
    void inverseTransitiveFixtureShowsTransitiveFlagAndReverseAssertedInverse() {
        JenaOntologyPort port =
            JenaOntologyPort.fromDocument(Fixtures.inverseTransitiveOntology(), ReasonerSetting.OWL);
        TypeDefinition component = type(port.snapshot(SCOPE), COMPONENT_IRI);

        RelationshipDefinition partOf = relationship(component, PART_OF_IRI);
        assertThat(partOf.transitive()).isTrue();

        // childOf carries no explicit owl:inverseOf of its own -- the assertion sits on parentOf,
        // in the opposite direction from reference.ttl's belongsToProject/hasTask pair -- so this
        // is only visible when the owl reasoner infers the reverse direction.
        RelationshipDefinition childOf = relationship(component, CHILD_OF_IRI);
        assertThat(childOf.inverseIri()).contains(PARENT_OF_IRI);
    }

    @Test
    void propertyAttributionIsIdenticalAcrossAllThreeReasonerSettings() {
        List<String> owlProperties = propertyIris(ReasonerSetting.OWL, Fixtures.TASK_IRI);
        List<String> rdfsProperties = propertyIris(ReasonerSetting.RDFS, Fixtures.TASK_IRI);
        List<String> noneProperties = propertyIris(ReasonerSetting.NONE, Fixtures.TASK_IRI);

        assertThat(rdfsProperties).isEqualTo(owlProperties);
        assertThat(noneProperties).isEqualTo(owlProperties);
    }

    @Test
    void cardinalityFromRestrictionsAndFromFunctionalPropertyIsMapped() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        TypeDefinition task = type(port.snapshot(SCOPE), Fixtures.TASK_IRI);

        AttributeDefinition title = attribute(task, Fixtures.TITLE_IRI);
        assertThat(title.cardinality().min()).isEqualTo(1);

        RelationshipDefinition assignedTo = relationship(task, Fixtures.ASSIGNED_TO_IRI);
        assertThat(assignedTo.cardinality().max()).hasValue(1);

        // owl:FunctionalProperty alone, with no explicit owl:Restriction, still yields max 1.
        RelationshipDefinition belongsToProject = relationship(task, Fixtures.BELONGS_TO_PROJECT_IRI);
        assertThat(belongsToProject.cardinality().max()).hasValue(1);
    }

    @Test
    void displayHintsAndFlagsMapCorrectlyForEveryAnnotatedProperty() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), ReasonerSetting.OWL);
        TypeDefinition task = type(port.snapshot(SCOPE), Fixtures.TASK_IRI);

        AttributeDefinition title = attribute(task, Fixtures.TITLE_IRI);
        assertThat(title.searchable()).isTrue();
        assertThat(title.displayHints().group()).contains("General");
        assertThat(title.displayHints().order()).isEqualTo(1);

        AttributeDefinition status = attribute(task, Fixtures.STATUS_IRI);
        assertThat(status.facet()).isTrue();
        assertThat(status.indexed()).isTrue();

        AttributeDefinition createdAt = attribute(task, Fixtures.CREATED_AT_IRI);
        assertThat(createdAt.displayHints().hidden()).isTrue();
        assertThat(createdAt.readOnly()).isTrue();

        RelationshipDefinition assignedTo = relationship(task, Fixtures.ASSIGNED_TO_IRI);
        assertThat(assignedTo.facet()).isTrue();
    }

    @Test
    void reservedTermIsRejectedThroughThePortWithTheDocumentedMessage() {
        // Same wording sq-vocabulary.md commits to and SqVocabulary implements (finding F1):
        // "sq:<term> is reserved for Phase <N> (<topic>) and is not supported yet." Exercised here
        // through JenaOntologyPort.buildStateFrom (wired in T8, per finding F35), not just directly
        // against SqVocabulary as SnapshotMapperTest already does.
        String turtle =
            """
            @prefix sq:   <https://sequeless.dev/ns/meta#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix ex:   <https://sequeless.dev/ns/ref#> .

            <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

            ex:Widget a owl:Class ; sq:derivedBy ex:someRule .
            """;
        JenaOntologyPort port =
            JenaOntologyPort.fromDocument(new OntologyDocument(turtle, OntologyFormat.TURTLE), ReasonerSetting.OWL);

        assertThatThrownBy(() -> port.snapshot(SCOPE))
            .isInstanceOf(OntologyException.class)
            .satisfies(
                thrown -> {
                    OntologyException exception = (OntologyException) thrown;
                    assertThat(exception.report().consistent()).isFalse();
                    assertThat(exception.report().issues())
                        .anyMatch(
                            issue ->
                                issue.message()
                                    .equals(
                                        "sq:derivedBy is reserved for Phase 4 (derived properties)"
                                            + " and is not supported yet."));
                });
    }

    private static List<String> propertyIris(ReasonerSetting reasoner, String typeIri) {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), reasoner);
        return type(port.snapshot(SCOPE), typeIri).properties().stream().map(PropertyDefinition::iri).toList();
    }

    private static TypeDefinition type(MetaModelSnapshot snapshot, String iri) {
        return snapshot.type(iri).orElseThrow(() -> new AssertionError("No type mapped for " + iri));
    }

    private static AttributeDefinition attribute(TypeDefinition type, String propertyIri) {
        return (AttributeDefinition) property(type, propertyIri);
    }

    private static RelationshipDefinition relationship(TypeDefinition type, String propertyIri) {
        return (RelationshipDefinition) property(type, propertyIri);
    }

    private static PropertyDefinition property(TypeDefinition type, String propertyIri) {
        return type.properties().stream()
            .filter(property -> property.iri().equals(propertyIri))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No property " + propertyIri + " on " + type.iri()));
    }
}
