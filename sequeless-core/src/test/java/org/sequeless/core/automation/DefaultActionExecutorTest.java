package org.sequeless.core.automation;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.expression.ExpressionContext;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.CreateObjectAction;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.LogAction;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyAssignment;
import org.sequeless.spi.meta.SetPropertyAction;
import org.sequeless.spi.meta.State;
import org.sequeless.spi.meta.StateMachineDefinition;
import org.sequeless.spi.meta.Transition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.meta.WebhookAction;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.DateTimeValue;
import org.sequeless.spi.object.DateValue;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectNotFoundException;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.ontology.OntologyReport;

/**
 * Unit tests for {@link DefaultActionExecutor}, built with the same hand-written-fixture idiom as
 * {@code DefaultTransitionServiceTest}/{@code DefaultBusinessObjectServiceTest} — no Mockito, just
 * small lambdas ({@link AuthorizationPort}) and hand-rolled fakes ({@link FakeObjectStorePort},
 * {@link FakeExpressionPort}, {@link FakeOntologyPort}).
 *
 * <p>{@link #applyingEveryActionKindProducedByARealFireCallAppliesThemCorrectly()} is the single
 * most important test here: rather than hand-building {@code ActionRequest} payloads and hoping
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

    private static final AttributeDefinition OWNER =
        new AttributeDefinition(
            OWNER_IRI, "owner", Cardinality.atMost(1), false, false, false, false, false,
            DisplayHints.none(), Optional.empty(), Datatype.STRING);

    private static final State DRAFT = new State(DRAFT_IRI, "Draft", 0);
    private static final State ACTIVE = new State(ACTIVE_IRI, "Active", 1);

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Scope ALICE =
        new Scope(new TenantId("acme"), new Principal("alice", "Alice", Set.of("member")));

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static BusinessObject project(
        ObjectId id, long version, String state, Map<PropertyRef, Value> properties) {
        return new BusinessObject(
            id, new TypeRef(PROJECT_IRI), new TenantId("acme"), version, Optional.of(state),
            properties, new Audit(CREATED_AT, "carol", CREATED_AT, "carol"), false);
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
        CreateObjectAction createObjectValue =
            new CreateObjectAction(
                TASK_IRI,
                List.of(new PropertyAssignment(TITLE_IRI, Optional.of(Value.text("Kickoff")), Optional.empty())));
        CreateObjectAction createObjectExpression =
            new CreateObjectAction(
                TASK_IRI,
                List.of(new PropertyAssignment(TITLE_IRI, Optional.empty(), Optional.of("self.owner"))));
        WebhookAction webhookWithBody =
            new WebhookAction(
                "https://example.com/hook?owner=${self.owner}", "POST", Optional.of("${self.owner}"));
        WebhookAction webhookNoBody = new WebhookAction("https://example.com/reopen", "GET", Optional.empty());
        LogAction logAction = new LogAction("Activated by ${self.owner}");

        Transition activate =
            new Transition(
                "activate", DRAFT_IRI, ACTIVE_IRI, Optional.of(OWNER_GUARD), Optional.empty(),
                List.of(
                    setPropertyValue,
                    setPropertyExpression,
                    createObjectValue,
                    createObjectExpression,
                    webhookWithBody,
                    webhookNoBody,
                    logAction));
        StateMachineDefinition lifecycle =
            new StateMachineDefinition(NS + "ProjectLifecycle", List.of(DRAFT, ACTIVE), DRAFT, List.of(activate));
        TypeDefinition projectType =
            new TypeDefinition(
                PROJECT_IRI, "Project", List.of(), List.of(OWNER), DisplayHints.none(), false,
                Optional.of(lifecycle));
        MetaModelSnapshot snapshot =
            new MetaModelSnapshot(
                NS.substring(0, NS.length() - 1), Optional.empty(), Map.of(),
                List.of(projectType), new OntologyReport(true, List.of()));

        ObjectId projectId = ObjectId.random();
        Map<PropertyRef, Value> properties = Map.of(new PropertyRef(OWNER_IRI), Value.text("alice"));
        BusinessObject existing = project(projectId, 5, DRAFT_IRI, properties);
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        FakeExpressionPort expressionPort = new FakeExpressionPort();

        DefaultTransitionService transitionService =
            new DefaultTransitionService(
                new FakeOntologyPort(snapshot), store, permitAll(), expressionPort, CLOCK);
        transitionService.fire(ALICE, "Project", projectId, "activate", 5);

        // The real payload DefaultTransitionService produced — not hand-built.
        List<OutboxEntry> outbox = store.commits.get(0).outbox();
        assertThat(outbox).hasSize(8); // 1 TransitionFired + 7 ActionRequest
        assertThat(outbox.get(0).kind()).isEqualTo(OutboxEntry.KIND_TRANSITION_FIRED);
        List<OutboxEntry> actionRequests = outbox.subList(1, outbox.size());

        DefaultActionExecutor executor = new DefaultActionExecutor(expressionPort, store, CLOCK);

        // actionIndex 0: SetProperty, value variant — status differs from absent, commits.
        executor.applySetProperty(ALICE, actionRequests.get(0));
        BusinessObject afterFirstSet = store.store.get(projectId);
        assertThat(afterFirstSet.properties().get(new PropertyRef(STATUS_IRI))).isEqualTo(Value.text("kicked-off"));

        // actionIndex 1: SetProperty, expression variant ("self.owner") — self is frozen at
        // transition-fire time, so it still resolves to "alice" even though status is now
        // "kicked-off" in the live store; differs from "kicked-off", so commits again.
        executor.applySetProperty(ALICE, actionRequests.get(1));
        BusinessObject afterSecondSet = store.store.get(projectId);
        assertThat(afterSecondSet.properties().get(new PropertyRef(STATUS_IRI))).isEqualTo(Value.text("alice"));

        // actionIndex 2: CreateObject, value variant.
        executor.applyCreateObject(ALICE, actionRequests.get(2));
        UUID entryId2 = actionRequests.get(2).id();
        ObjectId derivedId2 =
            new ObjectId(UUID.nameUUIDFromBytes((entryId2 + ":2").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        BusinessObject task1 = store.store.get(derivedId2);
        assertThat(task1).isNotNull();
        assertThat(task1.type()).isEqualTo(new TypeRef(TASK_IRI));
        assertThat(task1.properties().get(new PropertyRef(TITLE_IRI))).isEqualTo(Value.text("Kickoff"));
        assertThat(task1.state()).isEmpty();

        // Idempotency: firing the very same ActionRequest a second time must not create a duplicate.
        int commitsBeforeRetry = store.commitCalls;
        executor.applyCreateObject(ALICE, actionRequests.get(2));
        assertThat(store.commitCalls).isEqualTo(commitsBeforeRetry);
        assertThat(store.store.get(derivedId2)).isEqualTo(task1);

        // actionIndex 3: CreateObject, expression variant ("self.owner").
        executor.applyCreateObject(ALICE, actionRequests.get(3));
        UUID entryId3 = actionRequests.get(3).id();
        ObjectId derivedId3 =
            new ObjectId(UUID.nameUUIDFromBytes((entryId3 + ":3").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        BusinessObject task2 = store.store.get(derivedId3);
        assertThat(task2).isNotNull();
        assertThat(task2.properties().get(new PropertyRef(TITLE_IRI))).isEqualTo(Value.text("alice"));

        // actionIndex 4: Webhook, with a templated URL and body.
        ResolvedWebhookRequest resolved1 = executor.resolveWebhook(ALICE, actionRequests.get(4));
        assertThat(resolved1.url()).isEqualTo("https://example.com/hook?owner=alice");
        assertThat(resolved1.method()).isEqualTo("POST");
        assertThat(resolved1.body()).contains("alice");

        // actionIndex 5: Webhook, no body — url has no placeholders, passes through untouched.
        int findCallsBefore = store.findCalls;
        int commitCallsBefore = store.commitCalls;
        ResolvedWebhookRequest resolved2 = executor.resolveWebhook(ALICE, actionRequests.get(5));
        assertThat(resolved2.url()).isEqualTo("https://example.com/reopen");
        assertThat(resolved2.method()).isEqualTo("GET");
        assertThat(resolved2.body()).isEmpty();
        assertThat(store.findCalls).isEqualTo(findCallsBefore);
        assertThat(store.commitCalls).isEqualTo(commitCallsBefore);

        // actionIndex 6: Log.
        expressionPort.renderTemplateCalls.clear();
        executor.applyLog(ALICE, actionRequests.get(6));
        assertThat(expressionPort.renderTemplateCalls).hasSize(1);
        assertThat(expressionPort.renderTemplateCalls.get(0).template()).isEqualTo("Activated by ${self.owner}");
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfProperties())
            .containsEntry("owner", Value.text("alice"));
    }

    // --- applySetProperty ---

    @Test
    void applySetPropertyIsANoOpWhenValueAlreadyMatchesValueVariant() {
        ObjectId id = ObjectId.random();
        BusinessObject existing =
            project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("kicked-off")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        OutboxEntry entry =
            setPropertyEntry(id, "kicked-off-transition", 0, Map.of("owner", tag(Value.text("alice"))))
                .valueVariant(Value.text("kicked-off"))
                .build();

        executor.applySetProperty(ALICE, entry);

        assertThat(store.commitCalls).isZero();
    }

    @Test
    void applySetPropertyCommitsExactlyOneUpdateWithObjectUpdatedOutboxWhenValueDiffersValueVariant() {
        ObjectId id = ObjectId.random();
        BusinessObject existing =
            project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("old")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        OutboxEntry entry =
            setPropertyEntry(id, "activate", 0, Map.of("owner", tag(Value.text("alice"))))
                .valueVariant(Value.text("new"))
                .build();

        executor.applySetProperty(ALICE, entry);

        assertThat(store.commitCalls).isEqualTo(1);
        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.mutations()).hasSize(1);
        Update update = (Update) changeSet.mutations().get(0);
        assertThat(update.expectedVersion()).isEqualTo(3);
        assertThat(update.object().properties().get(new PropertyRef(STATUS_IRI))).isEqualTo(Value.text("new"));
        assertThat(changeSet.outbox()).hasSize(1);
        assertThat(changeSet.outbox().get(0).kind()).isEqualTo(OutboxEntry.KIND_OBJECT_UPDATED);
        assertThat(changeSet.outbox().get(0).payload())
            .containsEntry("objectId", id.value().toString())
            .containsEntry("version", 4L);
    }

    @Test
    void applySetPropertyIsANoOpWhenValueAlreadyMatchesExpressionVariant() {
        ObjectId id = ObjectId.random();
        BusinessObject existing =
            project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("alice")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        OutboxEntry entry =
            setPropertyEntry(id, "activate", 0, Map.of("owner", tag(Value.text("alice"))))
                .expressionVariant("self.owner")
                .build();

        executor.applySetProperty(ALICE, entry);

        assertThat(store.commitCalls).isZero();
    }

    @Test
    void applySetPropertyCommitsWhenValueDiffersExpressionVariant() {
        ObjectId id = ObjectId.random();
        BusinessObject existing =
            project(id, 3, ACTIVE_IRI, Map.of(new PropertyRef(STATUS_IRI), Value.text("old")));
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(existing);
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        OutboxEntry entry =
            setPropertyEntry(id, "activate", 0, Map.of("owner", tag(Value.text("alice"))))
                .expressionVariant("self.owner")
                .build();

        executor.applySetProperty(ALICE, entry);

        assertThat(store.commitCalls).isEqualTo(1);
        Update update = (Update) store.commits.get(0).mutations().get(0);
        assertThat(update.object().properties().get(new PropertyRef(STATUS_IRI))).isEqualTo(Value.text("alice"));
    }

    @Test
    void applySetPropertyIsASafeNoOpWhenTargetObjectNoLongerExists() {
        ObjectId id = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort(); // nothing seeded
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        OutboxEntry entry =
            setPropertyEntry(id, "activate", 0, Map.of("owner", tag(Value.text("alice"))))
                .valueVariant(Value.text("new"))
                .build();

        executor.applySetProperty(ALICE, entry); // must not throw

        assertThat(store.commitCalls).isZero();
    }

    // --- applyCreateObject ---

    @Test
    void applyCreateObjectDerivesIdFromEntryIdAndActionIndexAndIsIdempotent() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        UUID entryId = UUID.randomUUID();
        int actionIndex = 2;
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", ObjectId.random().value().toString());
        payload.put("tenantId", "acme");
        payload.put("principalId", "alice");
        payload.put("transitionName", "activate");
        payload.put("actionIndex", actionIndex);
        payload.put("typeIri", PROJECT_IRI);
        payload.put("state", ACTIVE_IRI);
        payload.put("self", Map.of("owner", tag(Value.text("alice"))));
        payload.put("actionKind", "CreateObject");
        payload.put("createType", TASK_IRI);
        payload.put("createProperties", Map.of(TITLE_IRI, Map.of("value", tag(Value.text("Kickoff")))));
        OutboxEntry entry = new OutboxEntry(entryId, OutboxEntry.KIND_ACTION_REQUEST, payload, NOW);

        executor.applyCreateObject(ALICE, entry);

        ObjectId expectedId =
            new ObjectId(
                UUID.nameUUIDFromBytes(
                    (entryId + ":" + actionIndex).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(store.commitCalls).isEqualTo(1);
        BusinessObject created = store.store.get(expectedId);
        assertThat(created).isNotNull();
        assertThat(created.properties().get(new PropertyRef(TITLE_IRI))).isEqualTo(Value.text("Kickoff"));
        assertThat(created.state()).isEmpty();

        // Idempotent retry: the derived id already exists, so no second Create is committed.
        executor.applyCreateObject(ALICE, entry);
        assertThat(store.commitCalls).isEqualTo(1);
    }

    @Test
    void applyCreateObjectSupportsExpressionVariantProperties() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        UUID entryId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", ObjectId.random().value().toString());
        payload.put("tenantId", "acme");
        payload.put("principalId", "alice");
        payload.put("transitionName", "activate");
        payload.put("actionIndex", 0);
        payload.put("typeIri", PROJECT_IRI);
        payload.put("state", ACTIVE_IRI);
        payload.put("self", Map.of("owner", tag(Value.text("alice"))));
        payload.put("actionKind", "CreateObject");
        payload.put("createType", TASK_IRI);
        payload.put("createProperties", Map.of(TITLE_IRI, Map.of("expression", "self.owner")));
        OutboxEntry entry = new OutboxEntry(entryId, OutboxEntry.KIND_ACTION_REQUEST, payload, NOW);

        executor.applyCreateObject(ALICE, entry);

        ObjectId expectedId =
            new ObjectId(
                UUID.nameUUIDFromBytes((entryId + ":0").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        BusinessObject created = store.store.get(expectedId);
        assertThat(created.properties().get(new PropertyRef(TITLE_IRI))).isEqualTo(Value.text("alice"));
    }

    // --- resolveWebhook ---

    @Test
    void resolveWebhookRendersUrlAndBodyAndMakesNoObjectStoreCalls() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", ObjectId.random().value().toString());
        payload.put("tenantId", "acme");
        payload.put("principalId", "alice");
        payload.put("transitionName", "activate");
        payload.put("actionIndex", 0);
        payload.put("typeIri", PROJECT_IRI);
        payload.put("state", ACTIVE_IRI);
        payload.put("self", Map.of("owner", tag(Value.text("alice"))));
        payload.put("actionKind", "Webhook");
        payload.put("url", "https://example.com/hook?owner=${self.owner}");
        payload.put("method", "POST");
        payload.put("body", "${self.owner}");
        OutboxEntry entry = new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, NOW);

        ResolvedWebhookRequest resolved = executor.resolveWebhook(ALICE, entry);

        assertThat(resolved.url()).isEqualTo("https://example.com/hook?owner=alice");
        assertThat(resolved.method()).isEqualTo("POST");
        assertThat(resolved.body()).contains("alice");
        assertThat(store.findCalls).isZero();
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void resolveWebhookBodyIsAbsentWhenPayloadHasNoBodyKey() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultActionExecutor executor = new DefaultActionExecutor(new FakeExpressionPort(), store, CLOCK);

        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", ObjectId.random().value().toString());
        payload.put("tenantId", "acme");
        payload.put("principalId", "alice");
        payload.put("transitionName", "activate");
        payload.put("actionIndex", 0);
        payload.put("typeIri", PROJECT_IRI);
        payload.put("state", ACTIVE_IRI);
        payload.put("self", Map.of("owner", tag(Value.text("alice"))));
        payload.put("actionKind", "Webhook");
        payload.put("url", "https://example.com/reopen");
        payload.put("method", "GET");
        OutboxEntry entry = new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, NOW);

        ResolvedWebhookRequest resolved = executor.resolveWebhook(ALICE, entry);

        assertThat(resolved.body()).isEmpty();
        assertThat(store.findCalls).isZero();
        assertThat(store.commitCalls).isZero();
    }

    // --- applyLog ---

    @Test
    void applyLogRendersMessageThroughExpressionPort() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeExpressionPort expressionPort = new FakeExpressionPort();
        DefaultActionExecutor executor = new DefaultActionExecutor(expressionPort, store, CLOCK);

        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", ObjectId.random().value().toString());
        payload.put("tenantId", "acme");
        payload.put("principalId", "alice");
        payload.put("transitionName", "activate");
        payload.put("actionIndex", 0);
        payload.put("typeIri", PROJECT_IRI);
        payload.put("state", ACTIVE_IRI);
        payload.put("self", Map.of("owner", tag(Value.text("alice"))));
        payload.put("actionKind", "Log");
        payload.put("message", "Activated by ${self.owner}");
        OutboxEntry entry = new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, NOW);

        executor.applyLog(ALICE, entry);

        assertThat(expressionPort.renderTemplateCalls).hasSize(1);
        assertThat(expressionPort.renderTemplateCalls.get(0).template()).isEqualTo("Activated by ${self.owner}");
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfProperties())
            .containsEntry("owner", Value.text("alice"));
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfState()).contains(ACTIVE_IRI);
        assertThat(expressionPort.renderTemplateCalls.get(0).context().principalId()).isEqualTo("alice");
    }

    // --- payload-building helpers, mirroring DefaultTransitionService's own map-building style ---

    private static Object tag(Value value) {
        return PayloadValueCodec.toPayload(value);
    }

    private static SetPropertyEntryBuilder setPropertyEntry(
        ObjectId objectId, String transitionName, int actionIndex, Map<String, Object> self) {
        return new SetPropertyEntryBuilder(objectId, transitionName, actionIndex, self);
    }

    private static final class SetPropertyEntryBuilder {
        private final Map<String, Object> payload = new HashMap<>();

        SetPropertyEntryBuilder(ObjectId objectId, String transitionName, int actionIndex, Map<String, Object> self) {
            payload.put("objectId", objectId.value().toString());
            payload.put("tenantId", "acme");
            payload.put("principalId", "alice");
            payload.put("transitionName", transitionName);
            payload.put("actionIndex", actionIndex);
            payload.put("typeIri", PROJECT_IRI);
            payload.put("state", ACTIVE_IRI);
            payload.put("self", self);
            payload.put("actionKind", "SetProperty");
            payload.put("property", STATUS_IRI);
        }

        SetPropertyEntryBuilder valueVariant(Value value) {
            payload.put("value", tag(value));
            return this;
        }

        SetPropertyEntryBuilder expressionVariant(String expression) {
            payload.put("expression", expression);
            return this;
        }

        OutboxEntry build() {
            return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, NOW);
        }
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
            throw new UnsupportedOperationException(
                "Unrecognized expression in test fixture: " + expression);
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

    private static final class FakeOntologyPort implements OntologyPort {
        private final MetaModelSnapshot snapshot;

        FakeOntologyPort(MetaModelSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public MetaModelSnapshot snapshot(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            return snapshot;
        }

        @Override
        public MetaModelSnapshot reload(Scope scope) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public OntologyDocument export(Scope scope, OntologyFormat format) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
            throw new UnsupportedOperationException("not exercised by these tests");
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
