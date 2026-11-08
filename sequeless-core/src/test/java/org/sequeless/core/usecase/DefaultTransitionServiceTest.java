package org.sequeless.core.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Duration;
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
import org.junit.jupiter.api.Test;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.TransitionNotAvailableException;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.core.api.UnknownSignalException;
import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.meta.automation.Action;
import org.sequeless.spi.meta.automation.Automation;
import org.sequeless.spi.meta.automation.AutomationSource;
import org.sequeless.spi.meta.automation.CreateObjectAction;
import org.sequeless.spi.meta.automation.LogAction;
import org.sequeless.spi.meta.automation.PropertyAssignment;
import org.sequeless.spi.meta.automation.SetPropertyAction;
import org.sequeless.spi.meta.automation.TransitionRef;
import org.sequeless.spi.meta.automation.WebhookAction;
import org.sequeless.spi.meta.behaviour.Behaviour;
import org.sequeless.spi.meta.behaviour.BehaviourSource;
import org.sequeless.spi.meta.behaviour.ExternalSignalTrigger;
import org.sequeless.spi.meta.behaviour.OnChangeTrigger;
import org.sequeless.spi.meta.behaviour.State;
import org.sequeless.spi.meta.behaviour.StateMachineDefinition;
import org.sequeless.spi.meta.behaviour.TimerTrigger;
import org.sequeless.spi.meta.behaviour.Transition;
import org.sequeless.spi.meta.behaviour.TriggerKind;
import org.sequeless.spi.meta.behaviour.UserActionTrigger;
import org.sequeless.spi.meta.schema.AttributeDefinition;
import org.sequeless.spi.meta.schema.Cardinality;
import org.sequeless.spi.meta.schema.Datatype;
import org.sequeless.spi.meta.schema.DisplayHints;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.SchemaSource;
import org.sequeless.spi.meta.schema.TypeDefinition;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;

/**
 * Unit tests for {@link DefaultTransitionService}, built against a hand-written {@code Project}
 * type carrying a {@code ProjectLifecycle} state machine (Draft/Active/Closed/Archived), the same
 * idiom {@link DefaultBusinessObjectServiceTest} uses: every port double is either a plain lambda
 * ({@link AuthorizationPort}) or a small hand-written fake ({@link FakeOntology}, {@link
 * FakeObjectStorePort}, {@link FakeExpressionPort}) rather than a mocking framework. There is no
 * {@link ExpressionPort} adapter {@code sequeless-core} can depend on (the JEXL adapter lives in a
 * different module), so {@link FakeExpressionPort} hand-special-cases the one guard expression
 * these fixtures use, exactly as {@link FakeOntology}/{@code FakeQueryPort} elsewhere in this
 * package are hand-rolled rather than mocked.
 */
class DefaultTransitionServiceTest {

    private static final String NS = "https://sequeless.dev/ns/txn#";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String TASK_IRI = NS + "Task";
    private static final String OWNER_IRI = NS + "owner";
    private static final String STATUS_IRI = NS + "status";
    private static final String TITLE_IRI = NS + "title";

    private static final String DRAFT_IRI = NS + "Draft";
    private static final String ACTIVE_IRI = NS + "Active";
    private static final String CLOSED_IRI = NS + "Closed";
    private static final String ARCHIVED_IRI = NS + "Archived";

    /** The only guard expression these fixtures use; {@link FakeExpressionPort} special-cases it. */
    private static final String OWNER_GUARD = "self.owner != null";

    private static final String ACTIVATE_GUARD_MESSAGE = "Project must have an owner before it can be activated";

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
    private static final State CLOSED = new State(CLOSED_IRI, "Closed", 2);
    private static final State ARCHIVED = new State(ARCHIVED_IRI, "Archived", 3);

    private static final SetPropertyAction SET_PROPERTY_ACTION =
            new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("kicked-off")), Optional.empty());
    private static final CreateObjectAction CREATE_OBJECT_ACTION = new CreateObjectAction(
            TASK_IRI, List.of(new PropertyAssignment(TITLE_IRI, Optional.of(Value.text("Kickoff")), Optional.empty())));
    private static final WebhookAction WEBHOOK_ACTION =
            new WebhookAction("https://example.com/hook", "POST", Optional.of("${self.owner}"));
    private static final LogAction LOG_ACTION = new LogAction("Activated by ${self.owner}");

    /** Uses {@code expression}, not {@code value} — the mirror-image branch of {@link #SET_PROPERTY_ACTION}. */
    private static final SetPropertyAction SET_PROPERTY_EXPRESSION_ACTION =
            new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.status"));
    /** Has no {@code body} — the mirror-image branch of {@link #WEBHOOK_ACTION}. */
    private static final WebhookAction WEBHOOK_NO_BODY_ACTION =
            new WebhookAction("https://example.com/reopen", "GET", Optional.empty());

    private static final Transition ACTIVATE = new Transition(
            "activate",
            DRAFT_IRI,
            ACTIVE_IRI,
            new UserActionTrigger(),
            Optional.of(OWNER_GUARD),
            Optional.of(ACTIVATE_GUARD_MESSAGE));
    /** Guarded, but with no {@code guardMessage} — exercises the generic fallback text. */
    private static final Transition ARCHIVE = new Transition(
            "archive", DRAFT_IRI, ARCHIVED_IRI, new UserActionTrigger(), Optional.of(OWNER_GUARD), Optional.empty());
    /** Unguarded — always available from {@code Active}. */
    private static final Transition CLOSE = new Transition(
            "close", ACTIVE_IRI, CLOSED_IRI, new UserActionTrigger(), Optional.empty(), Optional.empty());
    /** Unguarded, exercises the "omitted, not null" payload shapes {@link #ACTIVATE} does not. */
    private static final Transition REOPEN = new Transition(
            "reopen", CLOSED_IRI, DRAFT_IRI, new UserActionTrigger(), Optional.empty(), Optional.empty());

    private static final StateMachineDefinition PROJECT_LIFECYCLE = new StateMachineDefinition(
            NS + "ProjectLifecycle",
            List.of(DRAFT, ACTIVE, CLOSED, ARCHIVED),
            DRAFT,
            List.of(ACTIVATE, ARCHIVE, CLOSE, REOPEN));

    private static final TypeDefinition PROJECT =
            new TypeDefinition(PROJECT_IRI, "Project", List.of(), List.of(OWNER), DisplayHints.none(), false);

    private static final TypeDefinition NO_MACHINE_TYPE =
            new TypeDefinition(NS + "Widget", "Widget", List.of(), List.of(), DisplayHints.none(), false);

    // --- a second, independent state machine dedicated to timer/fireAutomated tests, so its
    // fixtures never perturb the outbox sizes the Project-based tests above already assert on ---

    private static final String TIMER_WIDGET_IRI = NS + "TimerWidget";
    private static final String START_IRI = NS + "Start";
    private static final String WAITING_IRI = NS + "Waiting";
    private static final String ELSEWHERE_IRI = NS + "Elsewhere";
    private static final String DONE_IRI = NS + "Done";
    private static final Duration EXPIRE_AFTER = Duration.ofHours(1);

    private static final State TW_START = new State(START_IRI, "Start", 0);
    private static final State TW_WAITING = new State(WAITING_IRI, "Waiting", 1);
    private static final State TW_ELSEWHERE = new State(ELSEWHERE_IRI, "Elsewhere", 2);
    private static final State TW_DONE = new State(DONE_IRI, "Done", 3);

    /** Enters {@code Waiting}, which {@link #EXPIRE} departs via a timer — schedules on entry. */
    private static final Transition ENTER_WAITING = new Transition(
            "enter", START_IRI, WAITING_IRI, new UserActionTrigger(), Optional.empty(), Optional.empty());
    /** The timer transition itself, departing {@code Waiting}. */
    private static final Transition EXPIRE = new Transition(
            "expire", WAITING_IRI, DONE_IRI, new TimerTrigger(EXPIRE_AFTER), Optional.empty(), Optional.empty());
    /** Leaves {@code Waiting} by a different transition than {@link #EXPIRE} — cancels on exit. */
    private static final Transition LEAVE_WAITING = new Transition(
            "leave", WAITING_IRI, ELSEWHERE_IRI, new UserActionTrigger(), Optional.empty(), Optional.empty());
    /** Neither endpoint has a timer transition departing it — no timer entries expected. */
    private static final Transition NO_TIMER_TRANSITION = new Transition(
            "noop", START_IRI, ELSEWHERE_IRI, new UserActionTrigger(), Optional.empty(), Optional.empty());
    /** Unguarded {@code OnChange} transition, with one action, for {@code fireAutomated}'s happy path. */
    private static final Transition AUTO_ADVANCE = new Transition(
            "autoAdvance", START_IRI, DONE_IRI, new OnChangeTrigger(List.of()), Optional.empty(), Optional.empty());
    /** Guarded {@code OnChange} transition; {@link #OWNER_GUARD} always fails on a TimerWidget. */
    private static final Transition GUARDED_ON_CHANGE = new Transition(
            "guardedAdvance",
            START_IRI,
            DONE_IRI,
            new OnChangeTrigger(List.of()),
            Optional.of(OWNER_GUARD),
            Optional.empty());
    /**
     * Departs {@code Done}, not {@code Start} — used to prove {@code signal} validates a signal
     * name against the whole state machine, not just the object's current state.
     */
    private static final String WAKE_SIGNAL = "wake";

    private static final Transition WAKE = new Transition(
            "wake", DONE_IRI, START_IRI, new ExternalSignalTrigger(WAKE_SIGNAL), Optional.empty(), Optional.empty());

    private static final StateMachineDefinition TIMER_WIDGET_LIFECYCLE = new StateMachineDefinition(
            NS + "TimerWidgetLifecycle",
            List.of(TW_START, TW_WAITING, TW_ELSEWHERE, TW_DONE),
            TW_START,
            List.of(ENTER_WAITING, EXPIRE, LEAVE_WAITING, NO_TIMER_TRANSITION, AUTO_ADVANCE, GUARDED_ON_CHANGE, WAKE));

    private static final TypeDefinition TIMER_WIDGET =
            new TypeDefinition(TIMER_WIDGET_IRI, "TimerWidget", List.of(), List.of(), DisplayHints.none(), false);

    private static final Schema SCHEMA = new Schema(
            new OntologyVersion(1),
            NS.substring(0, NS.length() - 1),
            Optional.empty(),
            Map.of(),
            List.of(PROJECT, NO_MACHINE_TYPE, TIMER_WIDGET));

    /** The state machine on each type that has one, keyed by the type IRI it governs. */
    private static final Behaviour BEHAVIOUR = new Behaviour(
            new OntologyVersion(1),
            Map.of(PROJECT_IRI, PROJECT_LIFECYCLE, TIMER_WIDGET_IRI, TIMER_WIDGET_LIFECYCLE),
            Map.of());

    /** The actions of each transition that has any, keyed by the transition's {@link TransitionRef}. */
    private static final Automation AUTOMATION = new Automation(
            new OntologyVersion(1),
            Map.of(
                    new TransitionRef(PROJECT_LIFECYCLE.iri(), DRAFT_IRI, "activate"),
                    List.<Action>of(SET_PROPERTY_ACTION, CREATE_OBJECT_ACTION, WEBHOOK_ACTION, LOG_ACTION),
                    new TransitionRef(PROJECT_LIFECYCLE.iri(), CLOSED_IRI, "reopen"),
                    List.<Action>of(SET_PROPERTY_EXPRESSION_ACTION, WEBHOOK_NO_BODY_ACTION),
                    new TransitionRef(TIMER_WIDGET_LIFECYCLE.iri(), START_IRI, "autoAdvance"),
                    List.<Action>of(LOG_ACTION)));

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Scope ALICE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static AuthorizationPort denyAll() {
        return (scope, operation, resource) -> AccessDecision.deny("nope");
    }

    private static DefaultTransitionService service(FakeObjectStorePort store, AuthorizationPort authorizationPort) {
        return service(store, authorizationPort, new FakeOntology(SCHEMA, BEHAVIOUR, AUTOMATION));
    }

    private static DefaultTransitionService service(
            FakeObjectStorePort store, AuthorizationPort authorizationPort, FakeOntology ontology) {
        return new DefaultTransitionService(
                ontology, ontology, ontology, store, authorizationPort, new FakeExpressionPort(), CLOCK);
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

    private static BusinessObject timerWidget(ObjectId id, long version, String state) {
        return new BusinessObject(
                id,
                new TypeRef(TIMER_WIDGET_IRI),
                new TenantId("acme"),
                version,
                Optional.of(state),
                Map.of(),
                new Audit(CREATED_AT, "carol", CREATED_AT, "carol"),
                false);
    }

    // --- happy path: guard passes, state moves, one TransitionFired + one ActionRequest per action ---

    @Test
    void fireThrowsIllegalStateWhenAutomationIsAtAnotherOntologyVersionAndCommitsNothing() {
        ObjectId id = ObjectId.random();
        Map<PropertyRef, Value> properties = Map.of(new PropertyRef(OWNER_IRI), Value.text("alice"));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(id, 5, DRAFT_IRI, properties));
        Automation otherVersion = new Automation(new OntologyVersion(2), AUTOMATION.transitionActions());
        DefaultTransitionService service =
                service(store, permitAll(), new FakeOntology(SCHEMA, BEHAVIOUR, otherVersion));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.fire(ALICE, "Project", id, "activate", 5));
        assertThat(store.commits).isEmpty();
    }

    @Test
    void fireCommitsUpdateMovingStateAndDispatchesTransitionFiredPlusOneActionRequestPerAction() {
        ObjectId id = ObjectId.random();
        Map<PropertyRef, Value> properties = Map.of(new PropertyRef(OWNER_IRI), Value.text("alice"));
        BusinessObject existing = project(id, 5, DRAFT_IRI, properties);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        BusinessObject result = service.fire(ALICE, "Project", id, "activate", 5);

        assertThat(result.state()).contains(ACTIVE_IRI);
        assertThat(result.version()).isEqualTo(6);
        assertThat(result.properties()).isEqualTo(properties);

        assertThat(store.commits).hasSize(1);
        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.mutations()).hasSize(1);
        Update update = (Update) changeSet.mutations().get(0);
        assertThat(update.expectedVersion()).isEqualTo(5);
        assertThat(update.object().state()).contains(ACTIVE_IRI);
        assertThat(update.object().properties()).isEqualTo(properties);

        assertThat(changeSet.outbox()).hasSize(5);
        OutboxEntry transitionFired = changeSet.outbox().get(0);
        assertThat(transitionFired.kind()).isEqualTo(OutboxEntry.KIND_TRANSITION_FIRED);
        assertThat(transitionFired.payload())
                .containsEntry("objectId", id.value().toString())
                .containsEntry("tenantId", "acme")
                .containsEntry("principalId", "alice")
                .containsEntry("transitionName", "activate")
                .containsEntry("fromState", DRAFT_IRI)
                .containsEntry("toState", ACTIVE_IRI);

        List<OutboxEntry> actionRequests = changeSet.outbox().subList(1, 5);
        for (int i = 0; i < actionRequests.size(); i++) {
            OutboxEntry entry = actionRequests.get(i);
            assertThat(entry.kind()).isEqualTo(OutboxEntry.KIND_ACTION_REQUEST);
            assertThat(entry.payload())
                    .containsEntry("objectId", id.value().toString())
                    .containsEntry("tenantId", "acme")
                    .containsEntry("principalId", "alice")
                    .containsEntry("transitionName", "activate")
                    .containsEntry("actionIndex", i)
                    .containsEntry("typeIri", PROJECT_IRI)
                    .containsEntry("state", ACTIVE_IRI);
            // The single most important assertion: self is keyed by SHORT NAME ("owner"), not IRI.
            @SuppressWarnings("unchecked")
            Map<String, Object> self = (Map<String, Object>) entry.payload().get("self");
            assertThat(self).containsOnlyKeys("owner");
            assertThat(self.get("owner")).isEqualTo(Map.of("text", "alice"));
            assertThat(self).doesNotContainKey(OWNER_IRI);
        }

        // actionIndex 0: SetProperty — value present, expression absent.
        Map<String, Object> setPropertyPayload = actionRequests.get(0).payload();
        assertThat(setPropertyPayload.get("actionKind")).isEqualTo("SetProperty");
        assertThat(setPropertyPayload.get("property")).isEqualTo(STATUS_IRI);
        assertThat(setPropertyPayload.get("value")).isEqualTo(Map.of("text", "kicked-off"));
        assertThat(setPropertyPayload).doesNotContainKey("expression");

        // actionIndex 1: CreateObject.
        Map<String, Object> createObjectPayload = actionRequests.get(1).payload();
        assertThat(createObjectPayload.get("actionKind")).isEqualTo("CreateObject");
        assertThat(createObjectPayload.get("createType")).isEqualTo(TASK_IRI);
        @SuppressWarnings("unchecked")
        Map<String, Object> createProperties = (Map<String, Object>) createObjectPayload.get("createProperties");
        assertThat(createProperties).containsOnlyKeys(TITLE_IRI);
        assertThat(createProperties.get(TITLE_IRI)).isEqualTo(Map.of("value", Map.of("text", "Kickoff")));

        // actionIndex 2: Webhook — url/method/body all present.
        Map<String, Object> webhookPayload = actionRequests.get(2).payload();
        assertThat(webhookPayload.get("actionKind")).isEqualTo("Webhook");
        assertThat(webhookPayload.get("url")).isEqualTo("https://example.com/hook");
        assertThat(webhookPayload.get("method")).isEqualTo("POST");
        assertThat(webhookPayload.get("body")).isEqualTo("${self.owner}");

        // actionIndex 3: Log.
        Map<String, Object> logPayload = actionRequests.get(3).payload();
        assertThat(logPayload.get("actionKind")).isEqualTo("Log");
        assertThat(logPayload.get("message")).isEqualTo("Activated by ${self.owner}");
    }

    @Test
    void fireActionRequestOmitsAbsentOptionalFieldsRatherThanNull() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 1, CLOSED_IRI, Map.of());
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        service.fire(ALICE, "Project", id, "reopen", 1);

        ChangeSet changeSet = store.commits.get(0);
        List<OutboxEntry> actionRequests =
                changeSet.outbox().subList(1, changeSet.outbox().size());
        assertThat(actionRequests).hasSize(2);

        Map<String, Object> setPropertyPayload = actionRequests.get(0).payload();
        assertThat(setPropertyPayload.get("actionKind")).isEqualTo("SetProperty");
        assertThat(setPropertyPayload.get("expression")).isEqualTo("self.status");
        assertThat(setPropertyPayload).doesNotContainKey("value");

        Map<String, Object> webhookPayload = actionRequests.get(1).payload();
        assertThat(webhookPayload.get("actionKind")).isEqualTo("Webhook");
        assertThat(webhookPayload).doesNotContainKey("body");
    }

    @Test
    void fireOnUnguardedTransitionAlwaysSucceeds() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 2, ACTIVE_IRI, Map.of());
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        BusinessObject result = service.fire(ALICE, "Project", id, "close", 2);

        assertThat(result.state()).contains(CLOSED_IRI);
        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.outbox()).hasSize(1); // TransitionFired only, no actions defined.
    }

    // --- guard failure ---

    @Test
    void fireWithFailingGuardThrowsTransitionNotAvailableWithGuardMessage() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 1, DRAFT_IRI, Map.of());
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(TransitionNotAvailableException.class)
                .isThrownBy(() -> service.fire(ALICE, "Project", id, "activate", 1))
                .satisfies(exception -> {
                    assertThat(exception.transitionName()).isEqualTo("activate");
                    assertThat(exception.reason()).isEqualTo(ACTIVATE_GUARD_MESSAGE);
                });
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void fireWithFailingGuardAndNoGuardMessageUsesFallbackTextVerbatim() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 1, DRAFT_IRI, Map.of());
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(TransitionNotAvailableException.class)
                .isThrownBy(() -> service.fire(ALICE, "Project", id, "archive", 1))
                .satisfies(exception ->
                        assertThat(exception.reason()).isEqualTo("Guard for transition 'archive' was not satisfied"));
        assertThat(store.commitCalls).isZero();
    }

    // --- not found / not available ---

    @Test
    void fireWithUnknownTransitionNameThrowsTransitionNotAvailable() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 1, DRAFT_IRI, Map.of(new PropertyRef(OWNER_IRI), Value.text("x")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(TransitionNotAvailableException.class)
                .isThrownBy(() -> service.fire(ALICE, "Project", id, "bogus", 1))
                .satisfies(exception -> assertThat(exception.reason())
                        .isEqualTo("No transition named 'bogus' is available from the object's current state"));
    }

    @Test
    void fireWithTransitionNotAvailableFromCurrentStateThrowsTransitionNotAvailable() {
        ObjectId id = ObjectId.random();
        // "close" only departs from Active; this object is in Draft.
        BusinessObject existing = project(id, 1, DRAFT_IRI, Map.of());
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(TransitionNotAvailableException.class)
                .isThrownBy(() -> service.fire(ALICE, "Project", id, "close", 1));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void fireOnTypeWithNoStateMachineThrowsTransitionNotAvailable() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = new BusinessObject(
                id,
                new TypeRef(NO_MACHINE_TYPE.iri()),
                new TenantId("acme"),
                1,
                Optional.empty(),
                Map.of(),
                new Audit(CREATED_AT, "carol", CREATED_AT, "carol"),
                false);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(TransitionNotAvailableException.class)
                .isThrownBy(() -> service.fire(ALICE, "Widget", id, "anything", 1));
    }

    // --- authz / type / object resolution ---

    @Test
    void fireThrowsAuthorizationExceptionOnDenyBeforeReadingObject() {
        ObjectId id = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTransitionService service = service(store, denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.fire(ALICE, "Project", id, "activate", 1));
        assertThat(store.findCalls).isZero();
    }

    @Test
    void fireThrowsTypeNotFoundForUnknownType() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(TypeNotFoundException.class)
                .isThrownBy(() -> service.fire(ALICE, "NoSuchType", ObjectId.random(), "activate", 1));
    }

    @Test
    void fireThrowsObjectNotFoundForMissingObject() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
                .isThrownBy(() -> service.fire(ALICE, "Project", ObjectId.random(), "activate", 1));
    }

    @Test
    void fireThrowsStaleObjectExceptionOnVersionMismatch() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = project(id, 5, DRAFT_IRI, Map.of(new PropertyRef(OWNER_IRI), Value.text("alice")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(StaleObjectException.class)
                .isThrownBy(() -> service.fire(ALICE, "Project", id, "activate", 4));
    }

    @Test
    void fireRejectsNullArguments() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTransitionService service = service(store, permitAll());
        ObjectId id = ObjectId.random();

        assertThatNullPointerException().isThrownBy(() -> service.fire(null, "Project", id, "activate", 1));
        assertThatNullPointerException().isThrownBy(() -> service.fire(ALICE, null, id, "activate", 1));
        assertThatNullPointerException().isThrownBy(() -> service.fire(ALICE, "Project", null, "activate", 1));
        assertThatNullPointerException().isThrownBy(() -> service.fire(ALICE, "Project", id, null, 1));
    }

    // --- timer outbox entries ---

    @Test
    void firingATransitionIntoAStateWithATimerTransitionWritesTimerScheduled() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = timerWidget(id, 1, START_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        service.fire(ALICE, "TimerWidget", id, "enter", 1);

        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.outbox()).hasSize(2); // TransitionFired + TimerScheduled
        assertThat(changeSet.outbox().get(0).kind()).isEqualTo(OutboxEntry.KIND_TRANSITION_FIRED);
        OutboxEntry scheduled = changeSet.outbox().get(1);
        assertThat(scheduled.kind()).isEqualTo(OutboxEntry.KIND_TIMER_SCHEDULED);
        String expectedTimerKey = id.value().toString() + "|" + WAITING_IRI + "|expire";
        assertThat(scheduled.payload())
                .containsEntry("objectId", id.value().toString())
                .containsEntry("tenantId", "acme")
                .containsEntry("principalId", "alice")
                .containsEntry("typeIri", TIMER_WIDGET_IRI)
                .containsEntry("state", WAITING_IRI)
                .containsEntry("transitionName", "expire")
                .containsEntry("after", EXPIRE_AFTER.toString())
                .containsEntry("timerKey", expectedTimerKey);
    }

    @Test
    void firingATransitionOutOfAStateWithATimerTransitionWritesTimerCancelled() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = timerWidget(id, 1, WAITING_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        service.fire(ALICE, "TimerWidget", id, "leave", 1);

        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.outbox()).hasSize(2); // TransitionFired + TimerCancelled
        OutboxEntry cancelled = changeSet.outbox().get(1);
        assertThat(cancelled.kind()).isEqualTo(OutboxEntry.KIND_TIMER_CANCELLED);
        String expectedTimerKey = id.value().toString() + "|" + WAITING_IRI + "|expire";
        assertThat(cancelled.payload())
                .containsEntry("objectId", id.value().toString())
                .containsEntry("tenantId", "acme")
                .containsEntry("timerKey", expectedTimerKey);
    }

    @Test
    void firingATransitionBetweenTwoStatesWithNoTimersWritesNoTimerEntries() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = timerWidget(id, 1, START_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        service.fire(ALICE, "TimerWidget", id, "noop", 1);

        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.outbox()).hasSize(1); // TransitionFired only, no timer entries.
        assertThat(changeSet.outbox().get(0).kind()).isEqualTo(OutboxEntry.KIND_TRANSITION_FIRED);
    }

    // --- fireAutomated ---

    @Test
    void fireAutomatedReturnsEmptyAndCommitsNothingWhenObjectIsAbsent() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTransitionService service = service(store, permitAll());

        Optional<BusinessObject> result =
                service.fireAutomated(ALICE, ObjectId.random(), "autoAdvance", TriggerKind.ON_CHANGE);

        assertThat(result).isEmpty();
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void fireAutomatedReturnsEmptyWhenTransitionDoesNotDepartCurrentState() {
        ObjectId id = ObjectId.random();
        // "autoAdvance" only departs Start; this object is in Elsewhere.
        BusinessObject existing = timerWidget(id, 1, ELSEWHERE_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        Optional<BusinessObject> result = service.fireAutomated(ALICE, id, "autoAdvance", TriggerKind.ON_CHANGE);

        assertThat(result).isEmpty();
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void fireAutomatedReturnsEmptyWhenGuardFails() {
        ObjectId id = ObjectId.random();
        // TimerWidget objects never carry an "owner" property, so OWNER_GUARD always fails.
        BusinessObject existing = timerWidget(id, 1, START_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        Optional<BusinessObject> result = service.fireAutomated(ALICE, id, "guardedAdvance", TriggerKind.ON_CHANGE);

        assertThat(result).isEmpty();
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void fireAutomatedReturnsEmptyWhenTriggerKindDoesNotMatchExpected() {
        ObjectId id = ObjectId.random();
        // "autoAdvance"'s trigger is OnChange, not UserAction.
        BusinessObject existing = timerWidget(id, 1, START_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        Optional<BusinessObject> result = service.fireAutomated(ALICE, id, "autoAdvance", TriggerKind.USER_ACTION);

        assertThat(result).isEmpty();
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void fireAutomatedFiresNormallyMovingStateAndDispatchingTransitionFiredPlusActionRequest() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = timerWidget(id, 3, START_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        Optional<BusinessObject> result = service.fireAutomated(ALICE, id, "autoAdvance", TriggerKind.ON_CHANGE);

        assertThat(result).isPresent();
        assertThat(result.get().state()).contains(DONE_IRI);
        assertThat(result.get().version()).isEqualTo(4);

        assertThat(store.commits).hasSize(1);
        ChangeSet changeSet = store.commits.get(0);
        Update update = (Update) changeSet.mutations().get(0);
        assertThat(update.expectedVersion()).isEqualTo(3); // fireAutomated reads the version itself.

        assertThat(changeSet.outbox()).hasSize(2); // TransitionFired + one ActionRequest (Log).
        assertThat(changeSet.outbox().get(0).kind()).isEqualTo(OutboxEntry.KIND_TRANSITION_FIRED);
        assertThat(changeSet.outbox().get(1).kind()).isEqualTo(OutboxEntry.KIND_ACTION_REQUEST);
    }

    // --- signal ---

    @Test
    void signalCommitsSignalReceivedEntryWithNoMutations() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = timerWidget(id, 1, DONE_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        service.signal(ALICE, "TimerWidget", id, WAKE_SIGNAL, Map.of("reason", "manual"));

        assertThat(store.commits).hasSize(1);
        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.mutations()).isEmpty();
        assertThat(changeSet.outbox()).hasSize(1);
        OutboxEntry entry = changeSet.outbox().get(0);
        assertThat(entry.kind()).isEqualTo(OutboxEntry.KIND_SIGNAL_RECEIVED);
        assertThat(entry.payload())
                .containsEntry("objectId", id.value().toString())
                .containsEntry("tenantId", "acme")
                .containsEntry("principalId", "alice")
                .containsEntry("signalName", WAKE_SIGNAL)
                .containsEntry("body", Map.of("reason", "manual"));
    }

    @Test
    void signalSucceedsForASignalDeclaredOnATransitionNotDepartingTheCurrentState() {
        ObjectId id = ObjectId.random();
        // "wake" departs Done, but this object is in Start — signal validation is type-wide.
        BusinessObject existing = timerWidget(id, 1, START_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        service.signal(ALICE, "TimerWidget", id, WAKE_SIGNAL, Map.of());

        assertThat(store.commits).hasSize(1);
        assertThat(store.commits.get(0).outbox().get(0).kind()).isEqualTo(OutboxEntry.KIND_SIGNAL_RECEIVED);
    }

    @Test
    void signalThrowsObjectNotFoundForMissingObject() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTransitionService service = service(store, permitAll());
        ObjectId id = ObjectId.random();

        assertThatExceptionOfType(ObjectNotFoundException.class)
                .isThrownBy(() -> service.signal(ALICE, "TimerWidget", id, WAKE_SIGNAL, Map.of()));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void signalThrowsUnknownSignalExceptionWhenNoTransitionDeclaresIt() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = timerWidget(id, 1, START_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(UnknownSignalException.class)
                .isThrownBy(() -> service.signal(ALICE, "TimerWidget", id, "noSuchSignal", Map.of()))
                .satisfies(exception -> {
                    assertThat(exception.typeIri()).isEqualTo(TIMER_WIDGET_IRI);
                    assertThat(exception.signalName()).isEqualTo("noSuchSignal");
                });
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void signalThrowsUnknownSignalExceptionOnTypeWithNoStateMachine() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = new BusinessObject(
                id,
                new TypeRef(NS + "Widget"),
                new TenantId("acme"),
                1,
                Optional.empty(),
                Map.of(),
                new Audit(CREATED_AT, "carol", CREATED_AT, "carol"),
                false);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(UnknownSignalException.class)
                .isThrownBy(() -> service.signal(ALICE, "Widget", id, WAKE_SIGNAL, Map.of()));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void signalThrowsAuthorizationExceptionOnDenyBeforeReadingObject() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTransitionService service = service(store, denyAll());
        ObjectId id = ObjectId.random();

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.signal(ALICE, "TimerWidget", id, WAKE_SIGNAL, Map.of()));
        assertThat(store.findCalls).isZero();
    }

    @Test
    void signalRejectsNullArguments() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTransitionService service = service(store, permitAll());
        ObjectId id = ObjectId.random();

        assertThatNullPointerException()
                .isThrownBy(() -> service.signal(null, "TimerWidget", id, WAKE_SIGNAL, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.signal(ALICE, null, id, WAKE_SIGNAL, Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> service.signal(ALICE, "TimerWidget", null, WAKE_SIGNAL, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.signal(ALICE, "TimerWidget", id, null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.signal(ALICE, "TimerWidget", id, WAKE_SIGNAL, null));
    }

    // --- fire rejects non-UserAction triggers ---

    @Test
    void fireThrowsTransitionNotAvailableForATransitionWhoseTriggerIsNotUserAction() {
        ObjectId id = ObjectId.random();
        BusinessObject existing = timerWidget(id, 1, START_IRI);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultTransitionService service = service(store, permitAll());

        assertThatExceptionOfType(TransitionNotAvailableException.class)
                .isThrownBy(() -> service.fire(ALICE, "TimerWidget", id, "autoAdvance", 1))
                .satisfies(exception -> assertThat(exception.transitionName()).isEqualTo("autoAdvance"));
        assertThat(store.commitCalls).isZero();
    }

    // --- fakes ---

    private static final class FakeExpressionPort implements ExpressionPort {

        @Override
        public Value evaluate(String expression, ExpressionContext context) {
            Objects.requireNonNull(expression, "expression must not be null");
            Objects.requireNonNull(context, "context must not be null");
            if (OWNER_GUARD.equals(expression)) {
                return Value.bool(context.selfProperties().containsKey("owner"));
            }
            throw new UnsupportedOperationException("Unrecognized expression in test fixture: " + expression);
        }

        @Override
        public String renderTemplate(String template, ExpressionContext context) {
            throw new UnsupportedOperationException("not exercised by these tests");
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
        public PageResult<BusinessObject> browse(Scope scope, Set<TypeRef> types, Page page) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public CommitResult commit(Scope scope, ChangeSet changeSet) {
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
            return new CommitResult(resultObjects, outboxIds);
        }

        @Override
        public OntologyDocumentStore ontologyDocuments() {
            throw new UnsupportedOperationException("not exercised by these tests");
        }
    }
}
