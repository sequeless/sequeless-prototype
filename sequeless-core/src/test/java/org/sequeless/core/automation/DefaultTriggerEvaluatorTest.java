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
import org.junit.jupiter.api.Test;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.core.usecase.DefaultTransitionService;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.expression.ExpressionContext;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.ExternalSignalTrigger;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.OnChangeTrigger;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.State;
import org.sequeless.spi.meta.StateMachineDefinition;
import org.sequeless.spi.meta.TimerTrigger;
import org.sequeless.spi.meta.Transition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.meta.UserActionTrigger;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectNotFoundException;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
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
 * Unit tests for {@link DefaultTriggerEvaluator}, built against a hand-written {@code Project}
 * (an {@code OnChange}-triggered {@code autoClose}, a {@code Timer}-triggered {@code expireHold},
 * and an {@code ExternalSignal}-triggered {@code reopen}, mirroring the reference ontology's own
 * shape) and a {@code Task} type whose {@code belongsToProject} relationship is the watched
 * property. As with {@link org.sequeless.core.usecase.DefaultTransitionServiceTest}, every port
 * double is a small hand-written fake, and {@link DefaultTriggerEvaluator} is wired against a real
 * {@link DefaultTransitionService} — not a fake — so these tests exercise the actual {@code
 * fireAutomated} contract, not an assumption about it.
 */
class DefaultTriggerEvaluatorTest {

    private static final String NS = "https://sequeless.test/trigger-evaluator#";

    private static final String PROJECT_IRI = NS + "Project";
    private static final String TASK_IRI = NS + "Task";
    private static final String BELONGS_TO_PROJECT_IRI = NS + "belongsToProject";

    private static final String ACTIVE_IRI = NS + "Active";
    private static final String CLOSED_IRI = NS + "Closed";
    private static final String ON_HOLD_IRI = NS + "OnHold";

    private static final State ACTIVE = new State(ACTIVE_IRI, "Active", 0);
    private static final State CLOSED = new State(CLOSED_IRI, "Closed", 1);
    private static final State ON_HOLD = new State(ON_HOLD_IRI, "OnHold", 2);

    private static final RelationshipDefinition BELONGS_TO_PROJECT =
        new RelationshipDefinition(
            BELONGS_TO_PROJECT_IRI, "belongsToProject", Cardinality.optional(), false, false,
            false, false, false, DisplayHints.none(), Optional.empty(), PROJECT_IRI,
            Optional.empty(), false);

    /** {@code OnChange}, unguarded: fires whenever any watched {@code Task} changes. */
    private static final Transition AUTO_CLOSE =
        new Transition(
            "autoClose", ACTIVE_IRI, CLOSED_IRI,
            new OnChangeTrigger(List.of(BELONGS_TO_PROJECT_IRI)), Optional.empty(),
            Optional.empty(), List.of());
    private static final Transition EXPIRE_HOLD =
        new Transition(
            "expireHold", ON_HOLD_IRI, CLOSED_IRI, new TimerTrigger(java.time.Duration.ofHours(72)),
            Optional.empty(), Optional.empty(), List.of());
    private static final Transition REOPEN =
        new Transition(
            "reopen", CLOSED_IRI, ACTIVE_IRI, new ExternalSignalTrigger("reopen"), Optional.empty(),
            Optional.empty(), List.of());
    /** A second signal-triggered transition, so onSignal's name-matching is actually exercised. */
    private static final Transition ARCHIVE_SIGNAL =
        new Transition(
            "archiveSignal", CLOSED_IRI, CLOSED_IRI, new ExternalSignalTrigger("archive"),
            Optional.empty(), Optional.empty(), List.of());

    private static final StateMachineDefinition PROJECT_LIFECYCLE =
        new StateMachineDefinition(
            NS + "ProjectLifecycle", List.of(ACTIVE, CLOSED, ON_HOLD), ACTIVE,
            List.of(AUTO_CLOSE, EXPIRE_HOLD, REOPEN, ARCHIVE_SIGNAL));

    private static final TypeDefinition PROJECT =
        new TypeDefinition(
            PROJECT_IRI, "Project", List.of(), List.of(), DisplayHints.none(), false,
            Optional.of(PROJECT_LIFECYCLE));

    private static final TypeDefinition TASK =
        new TypeDefinition(
            TASK_IRI, "Task", List.of(), List.of(BELONGS_TO_PROJECT), DisplayHints.none(), false,
            Optional.empty());

    private static final MetaModelSnapshot SNAPSHOT =
        new MetaModelSnapshot(
            NS.substring(0, NS.length() - 1), Optional.empty(), Map.of(), List.of(PROJECT, TASK),
            new OntologyReport(true, List.of()));

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Scope ALICE =
        new Scope(new TenantId("acme"), new Principal("alice", "Alice", Set.of("member")));

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static BusinessObject project(ObjectId id, long version, String state) {
        return new BusinessObject(
            id, new TypeRef(PROJECT_IRI), new TenantId("acme"), version, Optional.of(state),
            Map.of(), new Audit(CREATED_AT, "carol", CREATED_AT, "carol"), false);
    }

    private static BusinessObject task(ObjectId id, long version, ObjectId belongsToProjectId) {
        Map<PropertyRef, Value> properties =
            belongsToProjectId == null
                ? Map.of()
                : Map.of(new PropertyRef(BELONGS_TO_PROJECT_IRI), Value.ref(belongsToProjectId));
        return new BusinessObject(
            id, new TypeRef(TASK_IRI), new TenantId("acme"), version, Optional.empty(), properties,
            new Audit(CREATED_AT, "carol", CREATED_AT, "carol"), false);
    }

    private static OutboxEntry objectUpdatedEvent(ObjectId taskId) {
        return new OutboxEntry(
            UUID.randomUUID(), OutboxEntry.KIND_OBJECT_UPDATED,
            Map.of("objectId", taskId.value().toString(), "typeIri", TASK_IRI), NOW);
    }

    private static OutboxEntry objectDeletedEvent(ObjectId taskId, ObjectId belongsToProjectId) {
        Map<String, Object> properties =
            Map.of(
                BELONGS_TO_PROJECT_IRI,
                PayloadValueCodec.toPayload(Value.ref(belongsToProjectId)));
        return new OutboxEntry(
            UUID.randomUUID(), OutboxEntry.KIND_OBJECT_DELETED,
            Map.of("objectId", taskId.value().toString(), "typeIri", TASK_IRI, "properties", properties),
            NOW);
    }

    private static OutboxEntry timerScheduledEvent(ObjectId projectId, String state, String transitionName) {
        return new OutboxEntry(
            UUID.randomUUID(), OutboxEntry.KIND_TIMER_SCHEDULED,
            Map.of(
                "objectId", projectId.value().toString(), "state", state, "transitionName",
                transitionName),
            NOW);
    }

    private static OutboxEntry signalReceivedEvent(ObjectId projectId, String signalName) {
        return new OutboxEntry(
            UUID.randomUUID(), OutboxEntry.KIND_SIGNAL_RECEIVED,
            Map.of("objectId", projectId.value().toString(), "signalName", signalName), NOW);
    }

    // --- onChange ---

    @Test
    void onChangeFiresTheWatchingProjectsOnChangeTransition() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, ACTIVE_IRI));
        store.seed(task(taskId, 1, projectId));
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onChange(ALICE, objectUpdatedEvent(taskId));

        BusinessObject updated = store.store.get(projectId);
        assertThat(updated.state()).contains(CLOSED_IRI);
        assertThat(store.commitCalls).isEqualTo(1);
    }

    @Test
    void onChangeForAnObjectDeletedEventResolvesTheWatchFromThePayloadSnapshotNotFromFind() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, ACTIVE_IRI));
        // Deliberately do NOT seed the task: find(taskId) must return empty, proving the watch is
        // resolved from the ObjectDeleted payload's embedded properties snapshot, not from a store
        // read of the (necessarily unreadable, since it's soft-deleted) task itself.
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onChange(ALICE, objectDeletedEvent(taskId, projectId));

        BusinessObject updated = store.store.get(projectId);
        assertThat(updated.state()).contains(CLOSED_IRI);
    }

    @Test
    void onChangeConsidersTheChangedObjectItselfACandidateForItsOwnOnChangeTransitions() {
        ObjectId projectId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, ACTIVE_IRI));
        DefaultTriggerEvaluator evaluator = evaluator(store);

        OutboxEntry projectChanged =
            new OutboxEntry(
                UUID.randomUUID(), OutboxEntry.KIND_OBJECT_UPDATED,
                Map.of("objectId", projectId.value().toString(), "typeIri", PROJECT_IRI), NOW);
        evaluator.onChange(ALICE, projectChanged);

        // AUTO_CLOSE departs Active with no guard, so the changed Project itself is a candidate
        // and fires it directly, with no Task involved at all.
        assertThat(store.store.get(projectId).state()).contains(CLOSED_IRI);
    }

    @Test
    void onChangeFiresAtMostOneTransitionPerCandidate() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, ACTIVE_IRI));
        store.seed(task(taskId, 1, projectId));
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onChange(ALICE, objectUpdatedEvent(taskId));

        // AUTO_CLOSE is the only OnChange transition departing Active; the candidate set collapses
        // the Task-watch resolution and the (here, absent) self-candidacy into one project id, so
        // exactly one commit happens regardless.
        assertThat(store.commitCalls).isEqualTo(1);
    }

    @Test
    void onChangeIgnoresAWatchIriNotDeclaredOnTheChangedObjectsType() {
        ObjectId projectId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, ACTIVE_IRI));
        DefaultTriggerEvaluator evaluator = evaluator(store);

        // A Project itself does not declare belongsToProject, so a Project-typed change event
        // must not resolve any candidate via AUTO_CLOSE's watch — it can still be a candidate via
        // AUTO_CLOSE's own self-candidacy path, which is exercised separately above; here we
        // confirm the *watch* scan does not spuriously match a type that doesn't declare the
        // property, by using a type with no state machine and no relationship of that name.
        OutboxEntry unrelatedChange =
            new OutboxEntry(
                UUID.randomUUID(), OutboxEntry.KIND_OBJECT_UPDATED,
                Map.of("objectId", ObjectId.random().value().toString(), "typeIri", "urn:unknown:Type"),
                NOW);

        evaluator.onChange(ALICE, unrelatedChange);

        assertThat(store.commitCalls).isZero();
    }

    // --- onTimerElapsed ---

    @Test
    void onTimerElapsedFiresWhenTheObjectIsStillInTheWaitedOnState() {
        ObjectId projectId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, ON_HOLD_IRI));
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onTimerElapsed(ALICE, timerScheduledEvent(projectId, ON_HOLD_IRI, "expireHold"));

        assertThat(store.store.get(projectId).state()).contains(CLOSED_IRI);
    }

    @Test
    void onTimerElapsedIsANoOpWhenTheObjectHasLeftTheWaitedOnState() {
        ObjectId projectId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, ACTIVE_IRI)); // left OnHold before the timer fired
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onTimerElapsed(ALICE, timerScheduledEvent(projectId, ON_HOLD_IRI, "expireHold"));

        assertThat(store.store.get(projectId).state()).contains(ACTIVE_IRI);
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void onTimerElapsedIsANoOpWhenTheObjectIsGone() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onTimerElapsed(
            ALICE, timerScheduledEvent(ObjectId.random(), ON_HOLD_IRI, "expireHold"));

        assertThat(store.commitCalls).isZero();
    }

    // --- onSignal ---

    @Test
    void onSignalFiresTheTransitionWhoseSignalNameMatchesFromTheCurrentState() {
        ObjectId projectId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, CLOSED_IRI));
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onSignal(ALICE, signalReceivedEvent(projectId, "reopen"));

        assertThat(store.store.get(projectId).state()).contains(ACTIVE_IRI);
    }

    @Test
    void onSignalIsANoOpForAnUnknownSignalName() {
        ObjectId projectId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, CLOSED_IRI));
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onSignal(ALICE, signalReceivedEvent(projectId, "no-such-signal"));

        assertThat(store.store.get(projectId).state()).contains(CLOSED_IRI);
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void onSignalIsANoOpWhenNoTransitionDepartsTheCurrentStateWithThatName() {
        ObjectId projectId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        // "reopen" only departs Closed; from Active it must not fire.
        store.seed(project(projectId, 1, ACTIVE_IRI));
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onSignal(ALICE, signalReceivedEvent(projectId, "reopen"));

        assertThat(store.store.get(projectId).state()).contains(ACTIVE_IRI);
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void onSignalIsANoOpWhenTheObjectIsGone() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultTriggerEvaluator evaluator = evaluator(store);

        evaluator.onSignal(ALICE, signalReceivedEvent(ObjectId.random(), "reopen"));

        assertThat(store.commitCalls).isZero();
    }

    // --- wiring helper ---

    private static DefaultTriggerEvaluator evaluator(FakeObjectStorePort store) {
        DefaultTransitionService transitionService =
            new DefaultTransitionService(
                new FakeOntologyPort(), store, permitAll(), new FakeExpressionPort(), CLOCK);
        return new DefaultTriggerEvaluator(new FakeOntologyPort(), store, transitionService);
    }

    // --- fakes ---

    private static final class FakeExpressionPort implements ExpressionPort {

        @Override
        public Value evaluate(String expression, ExpressionContext context) {
            throw new UnsupportedOperationException(
                "no guard is exercised by these fixtures: " + expression);
        }

        @Override
        public String renderTemplate(String template, ExpressionContext context) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }
    }

    private static final class FakeOntologyPort implements OntologyPort {

        @Override
        public MetaModelSnapshot snapshot(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            return SNAPSHOT;
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
        private int commitCalls;

        void seed(BusinessObject object) {
            store.put(object.id(), object);
        }

        @Override
        public Optional<BusinessObject> find(Scope scope, ObjectId id) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(id, "id must not be null");
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
