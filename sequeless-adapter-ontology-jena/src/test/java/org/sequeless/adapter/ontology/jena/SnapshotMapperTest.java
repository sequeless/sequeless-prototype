package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.meta.automation.Action;
import org.sequeless.spi.meta.automation.TransitionRef;
import org.sequeless.spi.meta.automation.WebhookAction;
import org.sequeless.spi.meta.behaviour.StateMachineDefinition;
import org.sequeless.spi.meta.behaviour.Transition;
import org.sequeless.spi.meta.schema.AttributeDefinition;
import org.sequeless.spi.meta.schema.PropertyDefinition;
import org.sequeless.spi.meta.schema.RelationshipDefinition;
import org.sequeless.spi.meta.schema.TypeDefinition;
import org.sequeless.testkit.Fixtures;

/**
 * Smoke-tests {@link SnapshotMapper} against the reference ontology fixture under two reasoner
 * settings, proving the two hazards the step plan calls out are actually handled: {@code
 * Task.superTypes()} is reasoner-dependent (grows under {@code owl}) while {@code
 * Task.properties()} attribution — including cardinality narrowed by a restriction declared on
 * {@code Task} itself for a property owned by an ancestor, {@code title} — is identical regardless
 * of reasoner setting. The full acceptance-criteria suite (inverse visibility, transitive,
 * inconsistency, determinism, export round-trip, etc.) is T9's job via {@code
 * JenaOntologyPortContractTest} and friends; this class exists only so this task does not hand back
 * mapping code that has never been executed against the real fixture.
 */
class SnapshotMapperTest {

    @Test
    void taskPropertiesAndCardinalityAreIdenticalUnderOwlAndNone() {
        MappingResult owl = mapReferenceOntology(ReasonerSetting.OWL);
        MappingResult none = mapReferenceOntology(ReasonerSetting.NONE);

        TypeDefinition taskUnderOwl = typeNamed(owl, Fixtures.TASK_IRI);
        TypeDefinition taskUnderNone = typeNamed(none, Fixtures.TASK_IRI);

        // Reasoner-dependent: Task ⊑ WorkItem ⊑ Deliverable, so `owl` sees the transitive closure
        // and `none` sees only the directly asserted edge.
        assertThat(taskUnderOwl.superTypes()).containsExactly(Fixtures.DELIVERABLE_IRI, Fixtures.WORK_ITEM_IRI);
        assertThat(taskUnderNone.superTypes()).containsExactly(Fixtures.WORK_ITEM_IRI);

        // Reasoner-independent: property attribution is computed by SnapshotMapper itself, not
        // delegated to Jena's declaredProperties(), so the set and order are identical.
        List<String> ownPropertyIris =
                taskUnderOwl.properties().stream().map(PropertyDefinition::iri).toList();
        assertThat(ownPropertyIris)
                .containsExactly(
                        Fixtures.TITLE_IRI,
                        Fixtures.STATUS_IRI,
                        Fixtures.PRIORITY_IRI,
                        Fixtures.ASSIGNED_TO_IRI,
                        Fixtures.BELONGS_TO_PROJECT_IRI,
                        Fixtures.CREATED_AT_IRI,
                        Fixtures.ESTIMATED_HOURS_IRI,
                        Fixtures.DUE_DATE_IRI,
                        Fixtures.DESCRIPTION_IRI);
        assertThat(taskUnderNone.properties().stream().map(PropertyDefinition::iri))
                .containsExactlyElementsOf(ownPropertyIris);

        // Cardinality from restrictions declared on Task itself, for a property (title) it does not
        // own (Deliverable does) — the case that requires per-type, not per-property, scoping.
        AttributeDefinition title = attribute(taskUnderOwl, Fixtures.TITLE_IRI);
        assertThat(title.cardinality().min()).isEqualTo(1);
        assertThat(title.cardinality().max()).isEmpty();
        assertThat(attribute(taskUnderNone, Fixtures.TITLE_IRI).cardinality().min())
                .isEqualTo(1);

        RelationshipDefinition assignedTo = relationship(taskUnderOwl, Fixtures.ASSIGNED_TO_IRI);
        assertThat(assignedTo.cardinality().min()).isZero();
        assertThat(assignedTo.cardinality().max()).hasValue(1);
        assertThat(assignedTo.targetTypeIri()).isEqualTo(Fixtures.PERSON_IRI);

        // Title, viewed from its actual owner Deliverable, carries no restriction of its own.
        TypeDefinition deliverableUnderOwl = typeNamed(owl, Fixtures.DELIVERABLE_IRI);
        assertThat(attribute(deliverableUnderOwl, Fixtures.TITLE_IRI)
                        .cardinality()
                        .min())
                .isZero();

        // owl:FunctionalProperty alone (no explicit restriction) yields max 1, min untouched.
        RelationshipDefinition belongsToProject = relationship(taskUnderOwl, Fixtures.BELONGS_TO_PROJECT_IRI);
        assertThat(belongsToProject.cardinality().max()).hasValue(1);
        assertThat(belongsToProject.cardinality().min()).isZero();

        // sq: display hints and flags round-trip.
        assertThat(title.displayHints().order()).isEqualTo(1);
        assertThat(title.searchable()).isTrue();
        assertThat(taskUnderOwl.label()).isEqualTo("Task");
        assertThat(taskUnderOwl.isAbstract()).isFalse();
        assertThat(typeNamed(owl, Fixtures.WORK_ITEM_IRI).isAbstract()).isTrue();

        assertThat(owl.issues()).isEmpty();
        assertThat(none.issues()).isEmpty();
    }

    @Test
    void reservedTermIsRejectedWithTheDocumentedMessage() {
        String turtle = """
            @prefix sq:   <https://sequeless.dev/ns/meta#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix ex:   <https://sequeless.dev/ns/ref#> .

            <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

            ex:Widget a owl:Class ; sq:permission true .
            """;
        OntModel model = buildModel(turtle, ReasonerSetting.NONE);

        Optional<String> message = SqVocabulary.rejectionMessageIfReserved(model);

        assertThat(message).contains("sq:permission is reserved for Phase 8 (authorisation) and is not supported yet.");
    }

    @Test
    void shaclShapesDoNotAppearAsTypesOrProperties() {
        MappingResult owl = mapReferenceOntology(ReasonerSetting.OWL);

        assertThat(owl.types().stream().map(TypeDefinition::iri))
                .doesNotContain(
                        Fixtures.REFERENCE_ONTOLOGY_IRI + "#TaskShape",
                        Fixtures.REFERENCE_ONTOLOGY_IRI + "#PersonShape");
        assertThat(owl.issues()).isEmpty();
    }

    /**
     * Two transitions of one state machine that share a {@link TransitionRef} (same from-state and
     * name) can never both fire, because a request fires the first match. The automation half keeps
     * the actions of the first one in {@code transitions()} order and drops the second's. Their
     * guards differ, so the test can tell which action list belongs to which transition.
     */
    @Test
    void firstTransitionWinsWhenTwoShareATransitionRef() {
        String turtle = """
            @prefix sq:   <https://sequeless.dev/ns/meta#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix ex:   <https://sequeless.dev/ns/ref#> .

            <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

            ex:Project a owl:Class .
            ex:Draft  a sq:State ; sq:label "Draft"  ; sq:displayOrder "1"^^xsd:integer .
            ex:Active a sq:State ; sq:label "Active" ; sq:displayOrder "2"^^xsd:integer .

            ex:Lifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "fire" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ; sq:guard "first" ;
                      sq:action ( [ a sq:Webhook ; sq:url "https://example.org/hooks/first" ; sq:method "POST" ] ) ] ,
                    [ a sq:Transition ; sq:name "fire" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ; sq:guard "second" ;
                      sq:action ( [ a sq:Webhook ; sq:url "https://example.org/hooks/second" ; sq:method "POST" ] ) ] .
            """;
        OntModel model = buildModel(turtle, ReasonerSetting.NONE);

        MappingResult result = SnapshotMapper.map(model);

        assertThat(result.issues()).isEmpty();
        StateMachineDefinition machine = result.stateMachines().get(Fixtures.PROJECT_IRI);
        assertThat(machine.transitions()).hasSize(2);
        String firstGuard = machine.transitions().get(0).guard().orElseThrow();

        TransitionRef shared = new TransitionRef(machine.iri(), Fixtures.DRAFT_IRI, "fire");
        List<Action> kept = result.transitionActions().get(shared);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0)).isInstanceOf(WebhookAction.class);
        assertThat(((WebhookAction) kept.get(0)).url()).isEqualTo("https://example.org/hooks/" + firstGuard);
        assertThat(result.transitionActions()).hasSize(1);
    }

    /**
     * The same rule when the first of the two has no actions: it still claims the TransitionRef, so
     * the second one's actions are not promoted into its place. Which of the two comes first is
     * whatever {@code transitions()} says, so the expectation is read from that order.
     */
    @Test
    void aFirstTransitionWithNoActionsStillClaimsItsTransitionRef() {
        String turtle = """
            @prefix sq:   <https://sequeless.dev/ns/meta#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
            @prefix ex:   <https://sequeless.dev/ns/ref#> .

            <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

            ex:Project a owl:Class .
            ex:Draft  a sq:State ; sq:label "Draft"  ; sq:displayOrder "1"^^xsd:integer .
            ex:Active a sq:State ; sq:label "Active" ; sq:displayOrder "2"^^xsd:integer .

            ex:Lifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "fire" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ; sq:guard "plain" ] ,
                    [ a sq:Transition ; sq:name "fire" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ; sq:guard "act" ;
                      sq:action ( [ a sq:Webhook ; sq:url "https://example.org/hooks/act" ; sq:method "POST" ] ) ] .
            """;
        MappingResult result = SnapshotMapper.map(buildModel(turtle, ReasonerSetting.NONE));

        assertThat(result.issues()).isEmpty();
        StateMachineDefinition machine = result.stateMachines().get(Fixtures.PROJECT_IRI);
        Transition first = machine.transitions().get(0);
        List<Action> kept = result.transitionActions()
                .getOrDefault(new TransitionRef(machine.iri(), Fixtures.DRAFT_IRI, "fire"), List.of());
        assertThat(kept).hasSize(first.guard().orElseThrow().equals("act") ? 1 : 0);
    }

    private static MappingResult mapReferenceOntology(ReasonerSetting reasoner) {
        OntModel model = buildModel(Fixtures.referenceOntology().content(), reasoner);
        return SnapshotMapper.map(model);
    }

    private static OntModel buildModel(String turtle, ReasonerSetting reasoner) {
        Model rdfModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(
                rdfModel,
                new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
                Fixtures.REFERENCE_ONTOLOGY_IRI,
                Lang.TURTLE);
        return OntModelFactory.createModel(
                rdfModel.getGraph(), reasoner.specification(), ImportResolver.withBundledMeta());
    }

    private static TypeDefinition typeNamed(MappingResult result, String iri) {
        return result.types().stream()
                .filter(type -> type.iri().equals(iri))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No type mapped for " + iri));
    }

    private static AttributeDefinition attribute(TypeDefinition type, String propertyIri) {
        return (AttributeDefinition) propertyNamed(type, propertyIri);
    }

    private static RelationshipDefinition relationship(TypeDefinition type, String propertyIri) {
        return (RelationshipDefinition) propertyNamed(type, propertyIri);
    }

    private static PropertyDefinition propertyNamed(TypeDefinition type, String propertyIri) {
        return type.properties().stream()
                .filter(property -> property.iri().equals(propertyIri))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No property " + propertyIri + " on " + type.iri()));
    }
}
