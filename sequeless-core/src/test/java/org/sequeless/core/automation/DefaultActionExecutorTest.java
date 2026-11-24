package org.sequeless.core.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.core.usecase.DefaultTransitionService;
import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.PropertyAssignment;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.State;
import org.sequeless.ontology.behaviour.api.StateMachineDefinition;
import org.sequeless.ontology.behaviour.api.Transition;
import org.sequeless.ontology.behaviour.api.UserActionTrigger;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.ontology.spi.OntologyDocumentStore;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * Unit tests for {@link DefaultActionExecutor}, built with the same hand-written-fixture idiom as
 * {@code DefaultTransitionServiceTest}/{@code DefaultBusinessObjectServiceTest} — no Mockito, just
 * small lambdas ({@link AuthorizationPort}) and hand-rolled fakes ({@link FakeObjectStorePort},
 * {@link FakeExpressionPort}, {@link FakeOntology}).
 *
 * <p>{@link #applyingEveryActionKindProducedByARealFireCallAppliesThemCorrectly()} is the single
 * most important test here: rather than hand-building {@code TransitionFired} payloads and hoping
 * they match what {@link DefaultTransitionService} actually emits, it calls {@code
 * DefaultTransitionService#fire} for real and feeds its genuine {@link OutboxEntry} output
 * straight into {@link DefaultActionExecutor}, proving both sides of the outbox payload format
 * agree — this matters more here than almost anywhere else in the codebase, since {@link
 * DefaultTransitionService} (the encoder) and {@link DefaultActionExecutor} (the decoder) were
 * built as separate tasks against a shared written spec. The remaining tests hand-build payloads,
 * copying {@code DefaultTransitionServiceTest}'s own map-building style, to isolate individual
 * behaviours (no-op vs. commit, idempotency, absence-safety) that are awkward to provoke through a
 * single {@code fire()} call.
 */
class DefaultActionExecutorTest {

    private static final String NS = "https://sequeless.dev/ns/automation#";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String TASK_IRI = NS + "Task";
    private static final String OWNER_IRI = NS + "owner";
    private static final String STATUS_IRI = NS + "status";
    private static final String TITLE_IRI = NS + "title";

    private static final String DRAFT_IRI = NS + "Draft";
    private static final String ACTIVE_IRI = NS + "Active";

    private static final String OWNER_GUARD = "self.owner != null";

    private static final AttributeDefinition OWNER = new AttributeDefinition(
            OWNER_IRI,
            "owner",
            Cardinality.atMost(1),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            Datatype.STRING);

    private static final State DRAFT = new State(DRAFT_IRI, "Draft", 0);
    private static final State ACTIVE = new State(ACTIVE_IRI, "Active", 1);

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Scope ALICE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static BusinessObject project(ObjectId id, long version, String state, Map<PropertyRef, Value> properties) {
        return new BusinessObject(
                id,
                new TypeRef(PROJECT_IRI),
                new TenantId("acme"),
                version,
                Optional.of(state),
                properties,
                new Audit(CREATED_AT, "carol", CREATED_AT, "carol"),
                false);
    }

    // --- the genuine cross-check: real DefaultTransitionService output fed into DefaultActionExecutor ---

    @Test
    void applyingEveryActionKindProducedByARealFireCallAppliesThemCorrectly() {
        // Set up a real ProjectLifecycle state machine whose "activate" transition exercises every
        // action kind, and both the "value" and "expression" payload variants for SetProperty and
        // CreateObject.
        SetPropertyAction setPropertyValue =
                new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("kicked-off")), Optional.empty());
        SetPropertyAction setPropertyExpression =
                new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner"));
        CreateObjectAction createObjectValue = new CreateObjectAction(
                TASK_IRI,
                List.of(new PropertyAssignment(TITLE_IRI, Optional.of(Value.text("Kickoff")), Optional.empty())));
        CreateObjectAction createObjectExpression = new CreateObjectAction(
                TASK_IRI, List.of(new PropertyAssignment(TITLE_IRI, Optional.empty(), Optional.of("self.owner"))));
        WebhookAction webhookWithBody =
                new WebhookAction("https://example.com/hook?owner=${self.owner}", "POST", Optional.of("${self.owner}"));
        WebhookAction webhookNoBody = new WebhookAction("https://example.com/reopen", "GET", Optional.empty());
        LogAction logAction = new LogAction("Activated by ${self.owner}");

        Transition activate = new Transition(
                "activate", DRAFT_IRI, ACTIVE_IRI, new UserActionTrigger(), Optional.of(OWNER_GUARD), Optional.empty());
        StateMachineDefinition lifecycle =
                new StateMachineDefinition(NS + "ProjectLifecycle", List.of(DRAFT, ACTIVE), DRAFT, List.of(activate));
        TypeDefinition projectType =
                new TypeDefinition(PROJECT_IRI, "Project", List.of(), List.of(OWNER), DisplayHints.none(), false);
        Schema schema = new Schema(
                new OntologyVersion(1),
                NS.substring(0, NS.length() - 1),
                Optional.empty(),
                Map.of(),
                List.of(projectType));
        Behaviour behaviour = new Behaviour(new OntologyVersion(1), Map.of(PROJECT_IRI, lifecycle), Map.of());
        // The test's own action list. The index of each entry is the actionIndex it is applied with.
        List<Action> actions = List.<Action>of(
                setPropertyValue,
                setPropertyExpression,
                createObjectValue,
                createObjectExpression,
                webhookWithBody,
                webhookNoBody,
                logAction);
        Automation automation = new Automation(
                new OntologyVersion(1), Map.of(new TransitionRef(lifecycle.iri(), DRAFT_IRI, "activate"), actions));

        ObjectId projectId = ObjectId.random();
        Map<PropertyRef, Value> properties = Map.of(new PropertyRef(OWNER_IRI), Value.text("alice"));
        BusinessObject existing = project(projectId, 5, DRAFT_IRI, properties);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        FakeExpressionPort expressionPort = new FakeExpressionPort();

        FakeOntology ontology = new FakeOntology(schema, behaviour, automation);
        DefaultTransitionService transitionService =
                new DefaultTransitionService(ontology, ontology, ontology, store, permitAll(), expressionPort, CLOCK);
        transitionService.fire(ALICE, "Project", projectId, "activate", 5);

        // The real payload DefaultTransitionService produced, not hand-built. The fire writes one
        // TransitionFired row, and the actions are applied from that row.
        List<OutboxEntry> outbox = store.commits.get(0).outbox();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).kind()).isEqualTo(OutboxEntry.KIND_TRANSITION_FIRED);
        OutboxEntry firedRow = outbox.get(0);

        DefaultActionExecutor executor = new DefaultActionExecutor(expressionPort, store, CLOCK);

        // actionIndex 0: SetProperty, value variant — status differs from absent, commits.
        executor.applyAction(ALICE, firedRow, actions.get(0), 0);
        BusinessObject afterFirstSet = store.store.get(projectId);
        assertThat(afterFirstSet.properties().get(new PropertyRef(STATUS_IRI))).isEqualTo(Value.text("kicked-off"));
        // The ObjectUpdated event carries the ontologyVersion that the TransitionFired row was stamped with.
        assertThat(store.commits.get(1).outbox().get(0).payload()).containsEntry("ontologyVersion", 1);

        // actionIndex 1: SetProperty, expression variant ("self.owner") — self is frozen at
        // transition-fire time, so it still resolves to "alice" even though status is now
        // "kicked-off" in the live store; differs from "kicked-off", so commits again.
        executor.applyAction(ALICE, firedRow, actions.get(1), 1);
        BusinessObject afterSecondSet = store.store.get(projectId);
        assertThat(afterSecondSet.properties().get(new PropertyRef(STATUS_IRI))).isEqualTo(Value.text("alice"));

        // actionIndex 2: CreateObject, value variant.
        executor.applyAction(ALICE, firedRow, actions.get(2), 2);
        ObjectId derivedId2 = new ObjectId(
                UUID.nameUUIDFromBytes((firedRow.id() + ":2").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        BusinessObject task1 = store.store.get(derivedId2);
        assertThat(task1).isNotNull();
        assertThat(task1.type()).isEqualTo(new TypeRef(TASK_IRI));
        assertThat(task1.properties().get(new PropertyRef(TITLE_IRI))).isEqualTo(Value.text("Kickoff"));
        assertThat(task1.state()).isEmpty();

        // Idempotency: applying the same CreateObject (same index) again must not commit again.
        int commitsBeforeRetry = store.commitCalls;
        executor.applyAction(ALICE, firedRow, actions.get(2), 2);
        assertThat(store.commitCalls).isEqualTo(commitsBeforeRetry);
        assertThat(store.store.get(derivedId2)).isEqualTo(task1);

        // actionIndex 3: CreateObject, expression variant ("self.owner").
        executor.applyAction(ALICE, firedRow, actions.get(3), 3);
        ObjectId derivedId3 = new ObjectId(
                UUID.nameUUIDFromBytes((firedRow.id() + ":3").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        BusinessObject task2 = store.store.get(derivedId3);
        assertThat(task2).isNotNull();
        assertThat(task2.properties().get(new PropertyRef(TITLE_IRI))).isEqualTo(Value.text("alice"));

        // actionIndex 4: Webhook, with a templated URL and body.
        ResolvedWebhookRequest resolved1 = executor.resolveWebhookAction(ALICE, firedRow, webhookWithBody, 4);
        assertThat(resolved1.url()).isEqualTo("https://example.com/hook?owner=alice");
        assertThat(resolved1.method()).isEqualTo("POST");
        assertThat(resolved1.body()).contains("alice");

        // actionIndex 5: Webhook, no body — url has no placeholders, passes through untouched.
        int findCallsBefore = store.findCalls;
        int commitCallsBefore = store.commitCalls;
        ResolvedWebhookRequest resolved2 = executor.resolveWebhookAction(ALICE, firedRow, webhookNoBody, 5);
        assertThat(resolved2.url()).isEqualTo("https://example.com/reopen");
        assertThat(resolved2.method()).isEqualTo("GET");
        assertThat(resolved2.body()).isEmpty();
        assertThat(store.findCalls).isEqualTo(findCallsBefore);
        assertThat(store.commitCalls).isEqualTo(commitCallsBefore);

        // actionIndex 6: Log.
        expressionPort.renderTemplateCalls.clear();
        executor.applyAction(ALICE, firedRow, actions.get(6), 6);
        assertThat(expressionPort.renderTemplateCalls).hasSize(1);
        assertThat(expressionPort.renderTemplateCalls.get(0).template()).isEqualTo("Activated by ${self.owner}");
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfProperties())
                .containsEntry("owner", Value.text("alice"));
    }

    // --- applyAction, SetProperty ---

    @Test
    void applyActionIsANoOpWhenValueAlreadyMatchesValueVariant() {
        ObjectId id = ObjectId.random();
        BusinessObject existing =
                project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("kicked-off")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(id, Map.of("owner", tag(Value.text("alice"))));
        SetPropertyAction action =
                new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("kicked-off")), Optional.empty());

        executor.applyAction(ALICE, row, action, 0);

        assertThat(store.commitCalls).isZero();
    }

    @Test
    void applyActionCommitsExactlyOneUpdateWithObjectUpdatedOutboxWhenValueDiffersValueVariant() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("old")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(id, Map.of("owner", tag(Value.text("alice"))));
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        executor.applyAction(ALICE, row, action, 0);

        assertThat(store.commitCalls).isEqualTo(1);
        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.mutations()).hasSize(1);
        Update update = (Update) changeSet.mutations().get(0);
        assertThat(update.expectedVersion()).isEqualTo(3);
        assertThat(update.object().properties().get(new PropertyRef(STATUS_IRI)))
                .isEqualTo(Value.text("new"));
        assertThat(changeSet.outbox()).hasSize(1);
        assertThat(changeSet.outbox().get(0).kind()).isEqualTo(OutboxEntry.KIND_OBJECT_UPDATED);
        assertThat(changeSet.outbox().get(0).payload())
                .containsEntry("objectId", id.value().toString())
                .containsEntry("version", 4L)
                .containsEntry("typeIri", PROJECT_IRI);
    }

    @Test
    void applyActionLeavesTheVersionOutOfTheOutboxOfAnUnstampedRow() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("old")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        // A row written before the stamp existed has no ontologyVersion key.
        Map<String, Object> payload = new HashMap<>(transitionFiredRow(id, Map.of("owner", tag(Value.text("alice"))))
                .payload());
        payload.remove("ontologyVersion");
        OutboxEntry unstamped = new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, NOW);
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        executor.applyAction(ALICE, unstamped, action, 0);

        assertThat(store.commitCalls).isEqualTo(1);
        assertThat(store.commits.get(0).outbox().get(0).payload()).doesNotContainKey("ontologyVersion");
    }

    @Test
    void applyActionIsANoOpWhenValueAlreadyMatchesExpressionVariant() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("alice")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(id, Map.of("owner", tag(Value.text("alice"))));
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner"));

        executor.applyAction(ALICE, row, action, 0);

        assertThat(store.commitCalls).isZero();
    }

    @Test
    void applyActionIsASafeNoOpWhenTargetObjectNoLongerExists() {
        ObjectId id = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort(); // nothing seeded
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(id, Map.of("owner", tag(Value.text("alice"))));
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        executor.applyAction(ALICE, row, action, 0); // must not throw

        assertThat(store.commitCalls).isZero();
    }

    // --- applyAction, CreateObject ---

    @Test
    void applyActionCreateObjectSupportsExpressionVariantProperties() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(ObjectId.random(), Map.of("owner", tag(Value.text("alice"))));
        CreateObjectAction action = new CreateObjectAction(
                TASK_IRI, List.of(new PropertyAssignment(TITLE_IRI, Optional.empty(), Optional.of("self.owner"))));

        executor.applyAction(ALICE, row, action, 0);

        ObjectId expectedId = new ObjectId(
                UUID.nameUUIDFromBytes((row.id() + ":0").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        BusinessObject created = store.store.get(expectedId);
        assertThat(created.properties().get(new PropertyRef(TITLE_IRI))).isEqualTo(Value.text("alice"));
    }

    // --- resolveWebhookAction ---

    @Test
    void resolveWebhookActionBodyIsAbsentWhenTheActionHasNoBody() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(ObjectId.random(), Map.of("owner", tag(Value.text("alice"))));
        WebhookAction action = new WebhookAction("https://example.com/reopen", "GET", Optional.empty());

        ResolvedWebhookRequest resolved = executor.resolveWebhookAction(ALICE, row, action, 5);

        assertThat(resolved.body()).isEmpty();
        assertThat(store.findCalls).isZero();
        assertThat(store.commitCalls).isZero();
    }

    // --- applyAction, Log ---

    @Test
    void applyActionLogRendersMessageWithTheToStateAndPrincipalContext() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeExpressionPort expressionPort = new FakeExpressionPort();
        DefaultActionExecutor executor = new DefaultActionExecutor(expressionPort, store, CLOCK);
        OutboxEntry row = transitionFiredRow(ObjectId.random(), Map.of("owner", tag(Value.text("alice"))));

        executor.applyAction(ALICE, row, new LogAction("Activated by ${self.owner}"), 6);

        assertThat(expressionPort.renderTemplateCalls).hasSize(1);
        assertThat(expressionPort.renderTemplateCalls.get(0).template()).isEqualTo("Activated by ${self.owner}");
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfProperties())
                .containsEntry("owner", Value.text("alice"));
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfState())
                .contains(ACTIVE_IRI);
        assertThat(expressionPort.renderTemplateCalls.get(0).context().principalId())
                .isEqualTo("alice");
    }

    // --- applyAction and resolveWebhookAction, the typed entry points for a TransitionFired row ---

    @Test
    void applyActionSetsALiteralPropertyAndCopiesTheOntologyVersionOntoTheCommit() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("old")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(id, Map.of("owner", tag(Value.text("alice"))));
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        executor.applyAction(ALICE, row, action, 0);

        assertThat(store.store.get(id).properties().get(new PropertyRef(STATUS_IRI)))
                .isEqualTo(Value.text("new"));
        assertThat(store.commits.get(0).outbox().get(0).payload()).containsEntry("ontologyVersion", 1);
    }

    @Test
    void applyActionEvaluatesAnExpressionAgainstTheToStateAsTheState() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 3, DRAFT_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("old")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(id, Map.of("owner", tag(Value.text("alice"))));
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.state"));

        executor.applyAction(ALICE, row, action, 0);

        // The row's fromState is DRAFT and its toState is ACTIVE; the expression sees ACTIVE.
        assertThat(store.store.get(id).properties().get(new PropertyRef(STATUS_IRI)))
                .isEqualTo(Value.text(ACTIVE_IRI));
    }

    @Test
    void applyActionCreatesTheObjectAtTheDerivedIdAndCreatesNothingOnReplay() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(ObjectId.random(), Map.of("owner", tag(Value.text("alice"))));
        CreateObjectAction action = new CreateObjectAction(
                TASK_IRI,
                List.of(new PropertyAssignment(TITLE_IRI, Optional.of(Value.text("Kickoff")), Optional.empty())));
        ObjectId expectedId = new ObjectId(
                UUID.nameUUIDFromBytes((row.id() + ":2").getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        executor.applyAction(ALICE, row, action, 2);

        assertThat(store.commitCalls).isEqualTo(1);
        assertThat(store.commits.get(0).outbox()).anySatisfy(entry -> {
            assertThat(entry.kind()).isEqualTo(OutboxEntry.KIND_OBJECT_CREATED);
            assertThat(entry.payload()).containsEntry("typeIri", TASK_IRI);
        });
        BusinessObject created = store.store.get(expectedId);
        assertThat(created.type()).isEqualTo(new TypeRef(TASK_IRI));
        assertThat(created.properties().get(new PropertyRef(TITLE_IRI))).isEqualTo(Value.text("Kickoff"));

        executor.applyAction(ALICE, row, action, 2);

        assertThat(store.commitCalls).isEqualTo(1);
        assertThat(store.store.get(expectedId)).isEqualTo(created);
    }

    @Test
    void applyActionLogRendersItsMessageAndWritesNoRow() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeExpressionPort expressionPort = new FakeExpressionPort();
        DefaultActionExecutor executor = new DefaultActionExecutor(expressionPort, store, CLOCK);
        OutboxEntry row = transitionFiredRow(ObjectId.random(), Map.of("owner", tag(Value.text("alice"))));

        executor.applyAction(ALICE, row, new LogAction("Activated by ${self.owner}"), 6);

        assertThat(expressionPort.renderTemplateCalls).hasSize(1);
        assertThat(expressionPort.renderTemplateCalls.get(0).template()).isEqualTo("Activated by ${self.owner}");
        assertThat(store.commitCalls).isZero();
        assertThat(store.store).isEmpty();
    }

    @Test
    void applyActionRefusesAWebhookActionAndSendsItToResolveWebhookAction() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(ObjectId.random(), Map.of("owner", tag(Value.text("alice"))));
        WebhookAction action = new WebhookAction("https://example.com/reopen", "GET", Optional.empty());

        assertThatThrownBy(() -> executor.applyAction(ALICE, row, action, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved through resolveWebhookAction, not applyAction");
    }

    @Test
    void resolveWebhookActionRendersUrlAndBodyAndMakesNoObjectStoreCalls() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);
        OutboxEntry row = transitionFiredRow(ObjectId.random(), Map.of("owner", tag(Value.text("alice"))));
        WebhookAction action =
                new WebhookAction("https://example.com/hook?owner=${self.owner}", "POST", Optional.of("${self.owner}"));

        ResolvedWebhookRequest resolved = executor.resolveWebhookAction(ALICE, row, action, 4);

        assertThat(resolved.url()).isEqualTo("https://example.com/hook?owner=alice");
        assertThat(resolved.method()).isEqualTo("POST");
        assertThat(resolved.body()).contains("alice");
        assertThat(store.findCalls).isZero();
        assertThat(store.commitCalls).isZero();
    }

    // --- payload-building helpers, mirroring DefaultTransitionService's own map-building style ---

    /** A {@code TransitionFired} row carrying the keys {@code DefaultTransitionService} writes on one. */
    private static OutboxEntry transitionFiredRow(ObjectId objectId, Map<String, Object> self) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", objectId.value().toString());
        payload.put("tenantId", "acme");
        payload.put("principalId", "alice");
        payload.put("transitionName", "activate");
        payload.put("fromState", DRAFT_IRI);
        payload.put("toState", ACTIVE_IRI);
        payload.put("ontologyVersion", 1);
        payload.put("self", self);
        payload.put("typeIri", PROJECT_IRI);
        payload.put("machineIri", NS + "ProjectLifecycle");
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, NOW);
    }

    private static Object tag(Value value) {
        return PayloadValueCodec.toPayload(value);
    }

    // --- fakes ---

    /** Records one {@link ExpressionPort#renderTemplate} invocation for test assertions. */
    private record RenderTemplateCall(String template, ExpressionContext context) {}

    /**
     * A generic fake: {@code self.<name>} resolves {@link ExpressionContext#selfProperties()},
     * {@code self.state} resolves {@link ExpressionContext#selfState()}, {@code principal}
     * resolves {@link ExpressionContext#principalId()}, and the one guard expression these
     * fixtures use ({@link #OWNER_GUARD}) is special-cased — mirroring real JEXL/JXLT semantics
     * closely enough for these tests without pulling in a JEXL adapter.
     */
    private static final class FakeExpressionPort implements ExpressionPort {

        private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)}");

        final List<RenderTemplateCall> renderTemplateCalls = new ArrayList<>();

        @Override
        public Value evaluate(String expression, ExpressionContext context) {
            Objects.requireNonNull(expression, "expression must not be null");
            Objects.requireNonNull(context, "context must not be null");
            if (OWNER_GUARD.equals(expression)) {
                return Value.bool(context.selfProperties().containsKey("owner"));
            }
            if ("self.state".equals(expression)) {
                return Value.text(context.selfState().orElseThrow());
            }
            if (expression.startsWith("self.")) {
                String name = expression.substring("self.".length());
                Value value = context.selfProperties().get(name);
                if (value == null) {
                    throw new UnsupportedOperationException("No self property named '" + name + "'");
                }
                return value;
            }
            if ("principal".equals(expression)) {
                return Value.text(context.principalId());
            }
            throw new UnsupportedOperationException("Unrecognized expression in test fixture: " + expression);
        }

        @Override
        public String renderTemplate(String template, ExpressionContext context) {
            Objects.requireNonNull(template, "template must not be null");
            Objects.requireNonNull(context, "context must not be null");
            renderTemplateCalls.add(new RenderTemplateCall(template, context));
            Matcher matcher = PLACEHOLDER.matcher(template);
            StringBuilder result = new StringBuilder();
            int last = 0;
            while (matcher.find()) {
                result.append(template, last, matcher.start());
                result.append(renderValue(evaluate(matcher.group(1), context)));
                last = matcher.end();
            }
            result.append(template.substring(last));
            return result.toString();
        }

        private static String renderValue(Value value) {
            return switch (value) {
                case TextValue text -> text.value();
                case IntegerValue integer -> Long.toString(integer.value());
                case DecimalValue decimal -> decimal.value().toString();
                case BoolValue bool -> Boolean.toString(bool.value());
                case DateTimeValue dateTime -> dateTime.value().toString();
                case DateValue date -> date.value().toString();
                case ReferenceValue ref -> ref.target().value().toString();
                case ListValue list -> list.values().toString();
            };
        }
    }

    /** Hand-written stand-in for the three read ports, each serving one fixed half. */
    private static final class FakeOntology implements SchemaSource, BehaviourSource, AutomationSource {
        private final Schema schema;
        private final Behaviour behaviour;
        private final Automation automation;

        FakeOntology(Schema schema, Behaviour behaviour, Automation automation) {
            this.schema = schema;
            this.behaviour = behaviour;
            this.automation = automation;
        }

        @Override
        public Schema schema(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            return schema;
        }

        @Override
        public Behaviour behaviour(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            return behaviour;
        }

        @Override
        public Automation automation(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            return automation;
        }
    }

    private static final class FakeObjectStorePort implements ObjectStorePort {

        private final Map<ObjectId, BusinessObject> store = new HashMap<>();
        private final List<ChangeSet> commits = new ArrayList<>();
        private int findCalls;
        private int commitCalls;

        void seed(BusinessObject object) {
            store.put(object.id(), object);
        }

        @Override
        public Optional<BusinessObject> find(Scope scope, ObjectId id) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(id, "id must not be null");
            findCalls++;
            BusinessObject found = store.get(id);
            if (found == null || found.deleted()) {
                return Optional.empty();
            }
            return Optional.of(found);
        }

        @Override
        public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public org.sequeless.spi.object.CommitResult commit(Scope scope, ChangeSet changeSet) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(changeSet, "changeSet must not be null");
            commitCalls++;
            commits.add(changeSet);

            List<BusinessObject> resultObjects = new ArrayList<>();
            for (Mutation mutation : changeSet.mutations()) {
                if (mutation instanceof Create create) {
                    store.put(create.object().id(), create.object());
                    resultObjects.add(create.object());
                } else if (mutation instanceof Update update) {
                    BusinessObject current = store.get(update.object().id());
                    if (current == null || current.deleted()) {
                        throw new ObjectNotFoundException(update.object().id());
                    }
                    if (current.version() != update.expectedVersion()) {
                        throw new StaleObjectException(update.object().id(), update.expectedVersion());
                    }
                    store.put(update.object().id(), update.object());
                    resultObjects.add(update.object());
                } else if (mutation instanceof Delete delete) {
                    throw new UnsupportedOperationException("not exercised by these tests");
                }
            }

            List<UUID> outboxIds = new ArrayList<>();
            for (OutboxEntry ignored : changeSet.outbox()) {
                outboxIds.add(UUID.randomUUID());
            }
            return new org.sequeless.spi.object.CommitResult(resultObjects, outboxIds);
        }

        @Override
        public OntologyDocumentStore ontologyDocuments() {
            throw new UnsupportedOperationException("not exercised by these tests");
        }
    }
}
