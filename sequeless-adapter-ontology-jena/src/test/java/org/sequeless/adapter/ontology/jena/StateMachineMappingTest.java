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
import org.sequeless.spi.meta.Action;
import org.sequeless.spi.meta.CreateObjectAction;
import org.sequeless.spi.meta.LogAction;
import org.sequeless.spi.meta.PropertyAssignment;
import org.sequeless.spi.meta.SetPropertyAction;
import org.sequeless.spi.meta.State;
import org.sequeless.spi.meta.StateMachineDefinition;
import org.sequeless.spi.meta.Transition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.meta.WebhookAction;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.Severity;
import org.sequeless.testkit.Fixtures;

/**
 * Unit-tests {@link SnapshotMapper}'s {@code sq:StateMachine} parsing in isolation, against small
 * inline Turtle fixtures rather than {@code reference.ttl}: a malformed state machine must never
 * live in the known-good reference ontology. Covers the well-formed shapes (a full {@code
 * ex:ProjectLifecycle}-like state machine, each of the four action kinds, {@code sq:value} vs
 * {@code sq:expression}), one test per rule-shape validation in {@code sq-vocabulary.md}'s "OWL /
 * sq: construct" table, a reasoner-interaction regression, and one assertion against the real
 * {@code reference.ttl}.
 */
class StateMachineMappingTest {

    private static final String REF_NS = "https://sequeless.dev/ns/ref#";
    private static final String PROJECT_IRI = REF_NS + "Project";
    private static final String TASK_IRI = REF_NS + "Task";
    private static final String TITLE_IRI = REF_NS + "title";
    private static final String PRIORITY_IRI = REF_NS + "priority";
    private static final String PROJECT_LIFECYCLE_IRI = REF_NS + "ProjectLifecycle";
    private static final String DRAFT_IRI = REF_NS + "Draft";
    private static final String ACTIVE_IRI = REF_NS + "Active";
    private static final String ON_HOLD_IRI = REF_NS + "OnHold";

    /**
     * {@code ex:Project}/{@code ex:Task}/{@code ex:Person} plus the ordinary (non-state-machine)
     * properties every fixture below needs reachable, and the two {@code sq:State} individuals
     * ({@code ex:Draft}, {@code ex:Active}) common to most fixtures; {@code ex:OnHold} is declared
     * per-test where a third state is actually needed, to keep each fixture's own state machine
     * block legible.
     */
    private static final String HEADER =
        """
        @prefix sq:   <https://sequeless.dev/ns/meta#> .
        @prefix owl:  <http://www.w3.org/2002/07/owl#> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
        @prefix ex:   <https://sequeless.dev/ns/ref#> .

        <https://sequeless.dev/ns/ref> a owl:Ontology ; owl:imports <https://sequeless.dev/ns/meta> .

        ex:Project a owl:Class .
        ex:Task a owl:Class .
        ex:Person a owl:Class .

        ex:title    a owl:DatatypeProperty ; rdfs:domain ex:Project ; rdfs:range xsd:string .
        ex:priority a owl:DatatypeProperty ; rdfs:domain ex:Project ; rdfs:range xsd:integer .
        ex:owner    a owl:ObjectProperty ;   rdfs:domain ex:Project ; rdfs:range ex:Person .

        ex:Draft  a sq:State ; sq:label "Draft"   ; sq:displayOrder "1"^^xsd:integer .
        ex:Active a sq:State ; sq:label "Active"  ; sq:displayOrder "2"^^xsd:integer .
        ex:OnHold a sq:State ; sq:label "On Hold" ; sq:displayOrder "3"^^xsd:integer .

        """;

    // -- Well-formed shapes ---------------------------------------------------------------------

    @Test
    void fullStateMachineMapsToStateMachineDefinitionWithSortedStatesAndTransitions() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active, ex:OnHold ;
                sq:transition
                    [ a sq:Transition ; sq:name "hold" ; sq:from ex:Active ; sq:to ex:OnHold ;
                      sq:trigger sq:UserAction ] ,
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:guard "self.owner != null" ;
                      sq:guardMessage "Project must have an owner" ] .
            """);
        assertThat(errors(result)).isEmpty();

        StateMachineDefinition definition = stateMachine(result, PROJECT_IRI);
        assertThat(definition.iri()).isEqualTo(PROJECT_LIFECYCLE_IRI);
        assertThat(definition.states().stream().map(State::iri))
            .containsExactly(DRAFT_IRI, ACTIVE_IRI, ON_HOLD_IRI);
        assertThat(definition.initialState().iri()).isEqualTo(DRAFT_IRI);
        assertThat(definition.initialState().label()).isEqualTo("Draft");

        // sq:transition is plain multi-valued (declared out of order above); the mapper sorts by name.
        assertThat(definition.transitions().stream().map(Transition::name))
            .containsExactly("activate", "hold");

        Transition activate = transition(definition, "activate");
        assertThat(activate.fromStateIri()).isEqualTo(DRAFT_IRI);
        assertThat(activate.toStateIri()).isEqualTo(ACTIVE_IRI);
        assertThat(activate.guard()).contains("self.owner != null");
        assertThat(activate.guardMessage()).contains("Project must have an owner");
        assertThat(activate.actions()).isEmpty();
    }

    @Test
    void transitionWithNoGuardIsAlwaysAvailable() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ] .
            """);
        assertThat(errors(result)).isEmpty();

        Transition activate = transition(stateMachine(result, PROJECT_IRI), "activate");
        assertThat(activate.guard()).isEmpty();
        assertThat(activate.guardMessage()).isEmpty();
    }

    @Test
    void setPropertyActionWithValueMapsToSetPropertyAction() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ a sq:SetProperty ; sq:property ex:priority ; sq:value "5" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        Action action = onlyAction(result);
        assertThat(action).isInstanceOf(SetPropertyAction.class);
        SetPropertyAction setProperty = (SetPropertyAction) action;
        assertThat(setProperty.propertyIri()).isEqualTo(PRIORITY_IRI);
        assertThat(setProperty.value()).contains(new IntegerValue(5));
        assertThat(setProperty.expression()).isEmpty();
    }

    @Test
    void setPropertyActionWithExpressionMapsToSetPropertyAction() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action
                        ( [ a sq:SetProperty ; sq:property ex:priority ; sq:expression "self.priority + 1" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        SetPropertyAction setProperty = (SetPropertyAction) onlyAction(result);
        assertThat(setProperty.value()).isEmpty();
        assertThat(setProperty.expression()).contains("self.priority + 1");
    }

    @Test
    void createObjectActionMapsToCreateObjectActionWithValueAndExpressionPropertyAssignments() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action
                        ( [ a sq:CreateObject ;
                            sq:type ex:Task ;
                            sq:properties
                              ( [ a sq:PropertyAssignment ; sq:property ex:title ; sq:value "Kickoff" ]
                                [ a sq:PropertyAssignment ; sq:property ex:title ;
                                  sq:expression "'Kickoff: ' + self.title" ] ) ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        CreateObjectAction createObject = (CreateObjectAction) onlyAction(result);
        assertThat(createObject.typeIri()).isEqualTo(TASK_IRI);
        assertThat(createObject.properties()).hasSize(2);

        PropertyAssignment byValue = createObject.properties().get(0);
        assertThat(byValue.propertyIri()).isEqualTo(TITLE_IRI);
        assertThat(byValue.value()).contains(new TextValue("Kickoff"));
        assertThat(byValue.expression()).isEmpty();

        PropertyAssignment byExpression = createObject.properties().get(1);
        assertThat(byExpression.value()).isEmpty();
        assertThat(byExpression.expression()).contains("'Kickoff: ' + self.title");
    }

    @Test
    void webhookActionMapsToWebhookAction() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action
                        ( [ a sq:Webhook ;
                            sq:url "https://example.org/hooks/x" ;
                            sq:method "PUT" ;
                            sq:body "{\\"x\\": 1}" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        WebhookAction webhook = (WebhookAction) onlyAction(result);
        assertThat(webhook.url()).isEqualTo("https://example.org/hooks/x");
        assertThat(webhook.method()).isEqualTo("PUT");
        assertThat(webhook.body()).contains("{\"x\": 1}");
    }

    @Test
    void webhookMethodAbsentDefaultsToPostAndIsNotAnError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ a sq:Webhook ; sq:url "https://example.org/hooks/x" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        WebhookAction webhook = (WebhookAction) onlyAction(result);
        assertThat(webhook.method()).isEqualTo("POST");
        assertThat(webhook.body()).isEmpty();
    }

    @Test
    void logActionMapsToLogAction() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ a sq:Log ; sq:message "activated: ${self.title}" ] ) ] .
            """);
        assertThat(errors(result)).isEmpty();

        LogAction log = (LogAction) onlyAction(result);
        assertThat(log.message()).isEqualTo("activated: ${self.title}");
    }

    // -- Rule-shape validation --------------------------------------------------------------------

    @Test
    void missingAppliesToIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft .
            """);
        assertSingleError(result, "must declare sq:appliesTo naming a named owl:Class");
    }

    @Test
    void missingStateIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft .
            """);
        assertSingleError(result, "must declare at least one sq:state");
    }

    @Test
    void initialStateNotInDeclaredStatesIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Active ;
                sq:state ex:Draft .
            """);
        assertSingleError(result, "sq:initialState must name one of its own sq:state values");
    }

    @Test
    void transitionMissingNameIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition [ a sq:Transition ; sq:from ex:Draft ; sq:to ex:Active ; sq:trigger sq:UserAction ] .
            """);
        assertSingleError(result, "is missing sq:name");
    }

    @Test
    void transitionMissingFromIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition [ a sq:Transition ; sq:name "activate" ; sq:to ex:Active ; sq:trigger sq:UserAction ] .
            """);
        assertSingleError(result, "sq:from must name one of the state machine's sq:state values");
    }

    @Test
    void transitionMissingToIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:trigger sq:UserAction ] .
            """);
        assertSingleError(result, "sq:to must name one of the state machine's sq:state values");
    }

    @Test
    void fromNotADeclaredStateIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:OnHold ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ] .
            """);
        assertSingleError(result, "sq:from must name one of the state machine's sq:state values");
    }

    @Test
    void toNotADeclaredStateIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:OnHold ;
                      sq:trigger sq:UserAction ] .
            """);
        assertSingleError(result, "sq:to must name one of the state machine's sq:state values");
    }

    @Test
    void unknownTriggerIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger ex:SomeOtherTrigger ] .
            """);
        assertSingleError(result, "sq:trigger must be sq:UserAction");
    }

    @Test
    void actionNodeUntypedIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ sq:property ex:priority ; sq:value "5" ] ) ] .
            """);
        assertSingleError(result, "must be typed exactly one of sq:SetProperty, sq:CreateObject, sq:Webhook, sq:Log");
    }

    @Test
    void actionNodeDualTypedIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action
                        ( [ a sq:SetProperty, sq:Log ; sq:property ex:priority ; sq:value "5" ;
                            sq:message "x" ] ) ] .
            """);
        assertSingleError(result, "must be typed exactly one of sq:SetProperty, sq:CreateObject, sq:Webhook, sq:Log");
    }

    @Test
    void setPropertyWithBothValueAndExpressionIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action
                        ( [ a sq:SetProperty ; sq:property ex:priority ; sq:value "5" ; sq:expression "1+1" ] ) ] .
            """);
        assertSingleError(result, "must declare exactly one of sq:value or sq:expression");
    }

    @Test
    void setPropertyWithNeitherValueNorExpressionIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ a sq:SetProperty ; sq:property ex:priority ] ) ] .
            """);
        assertSingleError(result, "must declare exactly one of sq:value or sq:expression");
    }

    @Test
    void propertyAssignmentWithBothValueAndExpressionIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action
                        ( [ a sq:CreateObject ; sq:type ex:Task ;
                            sq:properties
                              ( [ a sq:PropertyAssignment ; sq:property ex:title ; sq:value "x" ;
                                  sq:expression "y" ] ) ] ) ] .
            """);
        assertSingleError(result, "must declare exactly one of sq:value or sq:expression");
    }

    @Test
    void createObjectMissingTypeIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ a sq:CreateObject ] ) ] .
            """);
        assertSingleError(result, "is missing sq:type");
    }

    @Test
    void malformedPropertiesListIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action
                        ( [ a sq:CreateObject ; sq:type ex:Task ; sq:properties ex:title ] ) ] .
            """);
        assertSingleError(result, "must be a well-formed RDF list");
    }

    @Test
    void malformedActionListIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ex:Draft ] .
            """);
        assertSingleError(result, "must be a well-formed RDF list");
    }

    @Test
    void webhookMissingUrlIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ a sq:Webhook ; sq:method "POST" ] ) ] .
            """);
        assertSingleError(result, "is missing sq:url");
    }

    @Test
    void logMissingMessageIsError() {
        MappingResult result = mapTurtle(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ a sq:Log ] ) ] .
            """);
        assertSingleError(result, "is missing sq:message");
    }

    // -- Reasoner interaction --------------------------------------------------------------------

    /**
     * Regression test for T1's union-domain fix on {@code sq:property}/{@code sq:value} (no longer
     * {@code rdfs:domain sq:Criterion}, since RDFS domains combine by intersection, not union — see
     * {@code sq-vocabulary.md}): under {@link ReasonerSetting#OWL}, the setting the running
     * application actually uses, a {@code sq:SetProperty} node using the reused {@code sq:property}/
     * {@code sq:value} terms must not spuriously entail {@code rdf:type sq:Criterion} (or vice
     * versa) and must still map correctly, exactly as {@code DerivationMappingTest}'s sibling
     * regression test proves for {@code sq:derivedBy}.
     *
     * <p>Deliberately targets {@code ex:title} ({@code xsd:string}), not {@code ex:priority}
     * ({@code xsd:integer}): verified empirically that under {@link ReasonerSetting#OWL}, Jena's
     * rule reasoner also entails {@code rdfs:range xsd:decimal} onto any {@code xsd:integer}-ranged
     * property (it encodes the XSD numeric type hierarchy), and {@link #resolveValueForProperty} —
     * like the pre-existing {@code dataPropertyMeta}/{@code criterionValue} it is shared with —
     * picks a datatype by {@code sorted().findFirst()} over every candidate range, which resolves to
     * {@code DECIMAL} (alphabetically before {@code INTEGER}) in that case. That is a real,
     * pre-existing latent bug affecting ordinary (non-state-machine) property/criterion datatype
     * resolution too, out of scope for this task; flagged as a finding rather than fixed here. A
     * string-typed property has no such entailed-supertype ambiguity, so it isolates the actual
     * thing this test means to prove (the domain-union fix) from that unrelated hazard.
     */
    @Test
    void stateMachineWithSetPropertyMapsUnderTheOwlReasonerTheAppActuallyUses() {
        MappingResult result = mapTurtleUnderReasoner(
            """
            ex:ProjectLifecycle
                a sq:StateMachine ;
                sq:appliesTo ex:Project ;
                sq:initialState ex:Draft ;
                sq:state ex:Draft, ex:Active ;
                sq:transition
                    [ a sq:Transition ; sq:name "activate" ; sq:from ex:Draft ; sq:to ex:Active ;
                      sq:trigger sq:UserAction ;
                      sq:action ( [ a sq:SetProperty ; sq:property ex:title ; sq:value "Kickoff" ] ) ] .
            """,
            ReasonerSetting.OWL);
        assertThat(errors(result)).isEmpty();

        SetPropertyAction setProperty = (SetPropertyAction) onlyAction(result);
        assertThat(setProperty.propertyIri()).isEqualTo(TITLE_IRI);
        assertThat(setProperty.value()).contains(new TextValue("Kickoff"));
    }

    // -- Against the real reference.ttl ------------------------------------------------------------

    @Test
    void referenceOntologyProjectStateMachineMatchesExpectedShapeAndTaskHasNone() {
        OntModel model = buildModel(Fixtures.referenceOntology().content(), ReasonerSetting.OWL);
        MappingResult result = SnapshotMapper.map(model);
        assertThat(errors(result)).isEmpty();

        TypeDefinition project = typeNamed(result, Fixtures.PROJECT_IRI);
        assertThat(project.stateMachine()).isPresent();
        StateMachineDefinition definition = project.stateMachine().orElseThrow();
        assertThat(definition.iri()).isEqualTo(Fixtures.PROJECT_LIFECYCLE_IRI);
        assertThat(definition.states().stream().map(State::iri))
            .containsExactly(Fixtures.DRAFT_IRI, Fixtures.ACTIVE_IRI, Fixtures.ON_HOLD_IRI, Fixtures.CLOSED_IRI);
        assertThat(definition.initialState().iri()).isEqualTo(Fixtures.DRAFT_IRI);
        assertThat(definition.transitions().stream().map(Transition::name))
            .containsExactly("activate", "close", "hold", "resume");

        Transition activate = transition(definition, "activate");
        assertThat(activate.guard()).contains("self.owner != null");
        assertThat(activate.guardMessage()).contains("Project must have an owner before it can be activated");
        assertThat(activate.actions()).hasSize(2);
        assertThat(activate.actions().get(0)).isInstanceOf(CreateObjectAction.class);
        assertThat(activate.actions().get(1)).isInstanceOf(WebhookAction.class);

        CreateObjectAction kickoff = (CreateObjectAction) activate.actions().get(0);
        assertThat(kickoff.typeIri()).isEqualTo(Fixtures.TASK_IRI);
        assertThat(kickoff.properties()).hasSize(1);
        assertThat(kickoff.properties().get(0).propertyIri()).isEqualTo(Fixtures.TITLE_IRI);
        assertThat(kickoff.properties().get(0).expression()).contains("'Kickoff: ' + self.title");

        WebhookAction webhook = (WebhookAction) activate.actions().get(1);
        assertThat(webhook.url()).isEqualTo("https://example.org/hooks/project-activated");
        assertThat(webhook.method()).isEqualTo("POST");
        assertThat(webhook.body()).isPresent();

        for (String alwaysAvailable : List.of("hold", "resume", "close")) {
            Transition t = transition(definition, alwaysAvailable);
            assertThat(t.guard()).isEmpty();
        }

        TypeDefinition task = typeNamed(result, Fixtures.TASK_IRI);
        assertThat(task.stateMachine()).isEmpty();
    }

    // -- Fixtures ---------------------------------------------------------------------------------

    /**
     * Pinned to {@link ReasonerSetting#NONE}, deliberately, mirroring {@code
     * DerivationMappingTest#mapTurtle}: rule-shape validation is asserted independent of
     * reasoner-entailed triples. See {@link #stateMachineWithSetPropertyMapsUnderTheOwlReasonerTheAppActuallyUses}
     * for the {@link ReasonerSetting#OWL} regression coverage.
     */
    private static MappingResult mapTurtle(String stateMachineBlock) {
        return mapTurtleUnderReasoner(stateMachineBlock, ReasonerSetting.NONE);
    }

    private static MappingResult mapTurtleUnderReasoner(String stateMachineBlock, ReasonerSetting reasoner) {
        OntModel model = buildModel(HEADER + stateMachineBlock, reasoner);
        return SnapshotMapper.map(model);
    }

    private static OntModel buildModel(String turtle, ReasonerSetting reasoner) {
        Model rdfModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(
            rdfModel,
            new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
            "https://sequeless.dev/ns/ref",
            Lang.TURTLE);
        return OntModelFactory.createModel(
            rdfModel.getGraph(), reasoner.specification(), ImportResolver.withBundledMeta());
    }

    private static List<OntologyIssue> errors(MappingResult result) {
        return result.issues().stream().filter(issue -> issue.severity() == Severity.ERROR).toList();
    }

    private static void assertSingleError(MappingResult result, String messageFragment) {
        List<OntologyIssue> errors = errors(result);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains(messageFragment);
    }

    private static TypeDefinition typeNamed(MappingResult result, String iri) {
        return result.types().stream()
            .filter(t -> t.iri().equals(iri))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No type mapped for " + iri));
    }

    private static StateMachineDefinition stateMachine(MappingResult result, String typeIri) {
        return typeNamed(result, typeIri).stateMachine()
            .orElseThrow(() -> new AssertionError("No state machine mapped for " + typeIri));
    }

    private static Transition transition(StateMachineDefinition definition, String name) {
        return definition.transitions().stream()
            .filter(t -> t.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No transition named " + name));
    }

    private static Action onlyAction(MappingResult result) {
        Transition activate = transition(stateMachine(result, PROJECT_IRI), "activate");
        assertThat(activate.actions()).hasSize(1);
        return activate.actions().get(0);
    }
}
