package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.spi.meta.automation.Action;
import org.sequeless.spi.meta.automation.Automation;
import org.sequeless.spi.meta.automation.CreateObjectAction;
import org.sequeless.spi.meta.automation.TransitionRef;
import org.sequeless.spi.meta.automation.WebhookAction;
import org.sequeless.spi.meta.behaviour.Behaviour;
import org.sequeless.spi.meta.behaviour.DerivationRule;
import org.sequeless.spi.meta.behaviour.PluginRule;
import org.sequeless.spi.meta.behaviour.RollupRule;
import org.sequeless.spi.meta.behaviour.StateMachineDefinition;
import org.sequeless.spi.meta.behaviour.Transition;
import org.sequeless.spi.meta.schema.AggregateFunction;
import org.sequeless.spi.meta.schema.AttributeDefinition;
import org.sequeless.spi.meta.schema.PropertyDefinition;
import org.sequeless.spi.meta.schema.RelationshipDefinition;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.TypeDefinition;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;
import org.sequeless.testkit.Fixtures;

/**
 * Proves, at the {@link JenaOntologyPort} level (not the lower-level {@code SnapshotMapper} /
 * {@code ConsistencyChecker} unit tests already written), the three acceptance criteria the whole
 * phase is stated against, plus every other clause the step plan's §6 test list calls for that is
 * not already covered by an existing T7/T8 test class:
 *
 * <ul>
 *   <li>the reference schema shows {@code Task} with {@code superTypes} containing {@code WorkItem},
 *       {@code hasTask} inferred as the inverse of {@code belongsToProject}, and labels and display
 *       hints populated (acceptance criterion 1);
 *   <li>the inconsistent ontology is rejected with an {@code OntologyReport} that names the
 *       offending class, {@code ex:Cyborg} (acceptance criterion 2);
 *   <li>{@code reasoner=none} yields a schema without inferred {@code superTypes} — {@code
 *       Task.superTypes()} contains {@code WorkItem} but not {@code Deliverable} (acceptance
 *       criterion 3).
 * </ul>
 */
class JenaOntologyPortAcceptanceTest {

    private static final Scope SCOPE = Fixtures.defaultScope();
    private static final TenantId TENANT = TenantId.DEFAULT;

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
    void referenceSchemaProvesAcceptanceCriterion1() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        Schema schema = port.schema(TENANT);

        TypeDefinition task = type(schema, Fixtures.TASK_IRI);
        assertThat(task.superTypes()).contains(Fixtures.WORK_ITEM_IRI);
        assertThat(task.label()).isEqualTo("Task");
        assertThat(task.displayHints()).isNotNull();

        TypeDefinition project = type(schema, Fixtures.PROJECT_IRI);
        RelationshipDefinition hasTask = relationship(project, Fixtures.HAS_TASK_IRI);
        assertThat(hasTask.inverseIri()).contains(Fixtures.BELONGS_TO_PROJECT_IRI);
        assertThat(hasTask.label()).isEqualTo("Has Task");

        AttributeDefinition title = attribute(task, Fixtures.TITLE_IRI);
        assertThat(title.displayHints().order()).isEqualTo(1);
        assertThat(title.displayHints().group()).contains("General");
    }

    @Test
    void inconsistentOntologyReportNamesTheOffendingClassProvingAcceptanceCriterion2() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.inconsistentOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));

        assertThatThrownBy(() -> port.schema(TENANT))
                .isInstanceOf(OntologyException.class)
                .satisfies(thrown -> {
                    OntologyException exception = (OntologyException) thrown;
                    assertThat(exception.report().consistent()).isFalse();
                    assertThat(exception.report().issues())
                            .anyMatch(issue -> issue.subjectIri()
                                    .map(iri -> iri.endsWith("Cyborg"))
                                    .orElse(false));
                });
    }

    @Test
    void reasonerNoneOmitsInferredSuperTypesProvingAcceptanceCriterion3() {
        JenaOntologyPort owlPort = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        JenaOntologyPort nonePort = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.NONE, () -> Set.of("workload"));

        TypeDefinition taskUnderOwl = type(owlPort.schema(TENANT), Fixtures.TASK_IRI);
        assertThat(taskUnderOwl.superTypes()).contains(Fixtures.WORK_ITEM_IRI, Fixtures.DELIVERABLE_IRI);

        TypeDefinition taskUnderNone = type(nonePort.schema(TENANT), Fixtures.TASK_IRI);
        assertThat(taskUnderNone.superTypes()).contains(Fixtures.WORK_ITEM_IRI);
        assertThat(taskUnderNone.superTypes()).doesNotContain(Fixtures.DELIVERABLE_IRI);
    }

    @Test
    void inverseIsVisibleOnlyUnderTheOwlReasoner() {
        JenaOntologyPort owlPort = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        JenaOntologyPort nonePort = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.NONE, () -> Set.of("workload"));

        TypeDefinition projectUnderOwl = type(owlPort.schema(TENANT), Fixtures.PROJECT_IRI);
        assertThat(relationship(projectUnderOwl, Fixtures.HAS_TASK_IRI).inverseIri())
                .contains(Fixtures.BELONGS_TO_PROJECT_IRI);

        TypeDefinition projectUnderNone = type(nonePort.schema(TENANT), Fixtures.PROJECT_IRI);
        assertThat(relationship(projectUnderNone, Fixtures.HAS_TASK_IRI).inverseIri())
                .isEmpty();
    }

    @Test
    void inverseTransitiveFixtureShowsTransitiveFlagAndReverseAssertedInverse() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.inverseTransitiveOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        TypeDefinition component = type(port.schema(TENANT), COMPONENT_IRI);

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
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        TypeDefinition task = type(port.schema(TENANT), Fixtures.TASK_IRI);

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
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        TypeDefinition task = type(port.schema(TENANT), Fixtures.TASK_IRI);

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
        String turtle = """
            @prefix sq:   <https://sequeless.dev/ns/meta#> .
            @prefix owl:  <http://www.w3.org/2002/07/owl#> .
            @prefix ex:   <https://sequeless.dev/ns/ref#> .

            <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

            ex:Widget a owl:Class ; sq:permission true .
            """;
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                new OntologyDocument(turtle, OntologyFormat.TURTLE), ReasonerSetting.OWL, () -> Set.of("workload"));

        assertThatThrownBy(() -> port.schema(TENANT))
                .isInstanceOf(OntologyException.class)
                .satisfies(thrown -> {
                    OntologyException exception = (OntologyException) thrown;
                    assertThat(exception.report().consistent()).isFalse();
                    assertThat(exception.report().issues())
                            .anyMatch(issue -> issue.message()
                                    .equals("sq:permission is reserved for Phase 8 (authorisation)"
                                            + " and is not supported yet."));
                });
    }

    /**
     * T5 regression guard: the real, shipped {@code reference.ttl} (not a small inline fixture
     * such as {@code DerivationMappingTest}'s) maps {@code ex:Project}'s two derived properties to
     * the exact {@link RollupRule}s the design commits to, under {@link ReasonerSetting#OWL} — the
     * reasoner setting the running application actually configures per {@code
     * JenaOntologyProperties}'s default. This is the closest thing to an end-to-end proof, short of
     * booting the app itself, that the reference ontology's derivation vocabulary is well-formed
     * and reads back correctly.
     */
    @Test
    void referenceBehaviourMapsProjectRollupsUnderTheOwlReasonerTheAppActuallyUses() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        TypeDefinition project = type(port.schema(TENANT), Fixtures.PROJECT_IRI);
        Behaviour behaviour = port.behaviour(TENANT);

        AttributeDefinition openTaskCount = attribute(project, Fixtures.OPEN_TASK_COUNT_IRI);
        assertThat(openTaskCount.readOnly()).isTrue();
        DerivationRule openTaskCountDerivation =
                behaviour.derivation(Fixtures.OPEN_TASK_COUNT_IRI).orElseThrow();
        assertThat(openTaskCountDerivation).isInstanceOf(RollupRule.class);
        RollupRule openTaskCountRule = (RollupRule) openTaskCountDerivation;
        assertThat(openTaskCountRule.sourceTypeIri()).isEqualTo(Fixtures.TASK_IRI);
        assertThat(openTaskCountRule.viaIri()).isEqualTo(Fixtures.BELONGS_TO_PROJECT_IRI);
        assertThat(openTaskCountRule.function()).isEqualTo(AggregateFunction.COUNT);
        assertThat(openTaskCountRule.ofPropertyIri()).isEmpty();
        assertThat(openTaskCountRule.criteria())
                .containsExactly(new Criterion(Fixtures.STATUS_IRI, Operator.NE, Optional.of(new TextValue("done"))));

        AttributeDefinition totalEstimatedHours = attribute(project, Fixtures.TOTAL_ESTIMATED_HOURS_IRI);
        assertThat(totalEstimatedHours.readOnly()).isTrue();
        DerivationRule totalEstimatedHoursDerivation =
                behaviour.derivation(Fixtures.TOTAL_ESTIMATED_HOURS_IRI).orElseThrow();
        assertThat(totalEstimatedHoursDerivation).isInstanceOf(RollupRule.class);
        RollupRule totalEstimatedHoursRule = (RollupRule) totalEstimatedHoursDerivation;
        assertThat(totalEstimatedHoursRule.sourceTypeIri()).isEqualTo(Fixtures.TASK_IRI);
        assertThat(totalEstimatedHoursRule.viaIri()).isEqualTo(Fixtures.BELONGS_TO_PROJECT_IRI);
        assertThat(totalEstimatedHoursRule.function()).isEqualTo(AggregateFunction.SUM);
        assertThat(totalEstimatedHoursRule.ofPropertyIri()).hasValue(Fixtures.ESTIMATED_HOURS_IRI);
        assertThat(totalEstimatedHoursRule.criteria()).isEmpty();
    }

    /**
     * The reference ontology's Behaviour and Automation halves answer what the accessors this split
     * removed answered at {@code aafb5a9}: {@code TypeDefinition.stateMachine()}, {@code
     * PropertyDefinition.derivation()} and {@code Transition.actions()}. The expected values are the
     * ones {@code StateMachineMappingTest} and this class's rollup test asserted there.
     */
    @Test
    void referenceBehaviourAndAutomationAnswerWhatTheRemovedAccessorsAnsweredAtAafb5a9() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referenceOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        Behaviour behaviour = port.behaviour(TENANT);
        Automation automation = port.automation(TENANT);

        // Was TypeDefinition.stateMachine(): only ex:Project is governed, and by ProjectLifecycle.
        StateMachineDefinition lifecycle =
                behaviour.stateMachine(Fixtures.PROJECT_IRI).orElseThrow();
        assertThat(lifecycle.iri()).isEqualTo(Fixtures.PROJECT_LIFECYCLE_IRI);
        assertThat(lifecycle.initialState().iri()).isEqualTo(Fixtures.DRAFT_IRI);
        assertThat(lifecycle.transitions().stream().map(Transition::name))
                .containsExactly("activate", "autoClose", "close", "expireHold", "hold", "reopen", "resume");
        assertThat(behaviour.stateMachine(Fixtures.TASK_IRI)).isEmpty();
        assertThat(behaviour.stateMachines()).hasSize(1);

        // Was PropertyDefinition.derivation(): the two Project rollups, and nothing else.
        assertThat(behaviour.derivation(Fixtures.TITLE_IRI)).isEmpty();
        assertThat(behaviour.derivations())
                .containsOnlyKeys(Fixtures.OPEN_TASK_COUNT_IRI, Fixtures.TOTAL_ESTIMATED_HOURS_IRI);

        // Was Transition.actions(): only "activate" has actions, so it is the only entry.
        TransitionRef activate = new TransitionRef(Fixtures.PROJECT_LIFECYCLE_IRI, Fixtures.DRAFT_IRI, "activate");
        List<Action> activateActions = automation.actions(activate);
        assertThat(activateActions).hasSize(2);
        assertThat(activateActions.get(0)).isInstanceOf(CreateObjectAction.class);
        assertThat(activateActions.get(1)).isInstanceOf(WebhookAction.class);
        assertThat(automation.transitionActions()).hasSize(1);
        assertThat(automation.actions(new TransitionRef(Fixtures.PROJECT_LIFECYCLE_IRI, Fixtures.ACTIVE_IRI, "close")))
                .isEmpty();
    }

    /**
     * T5 regression guard, first positive proof {@code ServiceLoader} plug-in discovery works end
     * to end: {@code reference-plugin.ttl}'s {@code ex:workload} names {@code "workload"}, which
     * this reactor's {@code sequeless-spi-testkit} module now registers via the testkit's services
     * registration of the sample plugin ({@code WorkloadDerivationPlugin}). Activation must succeed
     * — T4 already proves the opposite case (an unregistered name fails activation) in {@code
     * UnknownPluginActivationTest}.
     */
    @Test
    void referencePluginFixtureActivatesCleanlyNowThatWorkloadIsRegistered() {
        JenaOntologyPort port = JenaOntologyPort.fromDocument(
                Fixtures.referencePluginOntology(), ReasonerSetting.OWL, () -> Set.of("workload"));
        Schema schema = port.schema(TENANT);

        OntologyReport report = port.reload(SCOPE);
        assertThat(report.consistent()).isTrue();
        assertThat(report.issues()).isEmpty();

        TypeDefinition person = type(schema, Fixtures.PERSON_IRI);
        AttributeDefinition workload = attribute(person, Fixtures.WORKLOAD_IRI);
        assertThat(workload.readOnly()).isTrue();
        DerivationRule derivation =
                port.behaviour(TENANT).derivation(Fixtures.WORKLOAD_IRI).orElseThrow();
        assertThat(derivation).isInstanceOf(PluginRule.class);
        assertThat(((PluginRule) derivation).pluginName()).isEqualTo("workload");
    }

    private static List<String> propertyIris(ReasonerSetting reasoner, String typeIri) {
        JenaOntologyPort port =
                JenaOntologyPort.fromDocument(Fixtures.referenceOntology(), reasoner, () -> Set.of("workload"));
        return type(port.schema(TENANT), typeIri).properties().stream()
                .map(PropertyDefinition::iri)
                .toList();
    }

    private static TypeDefinition type(Schema schema, String iri) {
        return schema.type(iri).orElseThrow(() -> new AssertionError("No type mapped for " + iri));
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
