package org.sequeless.core.derivation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.AggregateFunction;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.RollupRule;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.IntegerValue;
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
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;

/**
 * Unit tests for {@link DefaultDerivationRecomputer}, built against a hand-written {@code Project}
 * (a materialised {@code sq:Rollup} {@code openTaskCount}, {@code sq:function sq:count}, {@code
 * sq:over ex:Task}, {@code sq:via belongsToProject}) and a {@code Task} type whose {@code
 * belongsToProject} relationship is the rollup's {@code via}, mirroring {@link
 * org.sequeless.core.automation.DefaultTriggerEvaluatorTest}'s fixture shape. Every port double is
 * a small hand-written fake; {@link DerivationPlanner} is real, not faked, so these tests exercise
 * the actual {@code computeRule} aggregate path.
 */
class DefaultDerivationRecomputerTest {

    private static final String NS = "https://sequeless.test/derivation-recomputer#";

    private static final String PROJECT_IRI = NS + "Project";
    private static final String TASK_IRI = NS + "Task";
    private static final String BELONGS_TO_PROJECT_IRI = NS + "belongsToProject";
    private static final String OPEN_TASK_COUNT_IRI = NS + "openTaskCount";

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Scope ALICE =
        new Scope(new TenantId("acme"), new Principal("alice", "Alice", Set.of("member")));

    private static final RelationshipDefinition BELONGS_TO_PROJECT =
        new RelationshipDefinition(
            BELONGS_TO_PROJECT_IRI, "belongsToProject", Cardinality.optional(), false, false,
            false, false, false, DisplayHints.none(), Optional.empty(), PROJECT_IRI,
            Optional.empty(), false);

    private static RollupRule openTaskCountRule(boolean materialised) {
        return new RollupRule(
            TASK_IRI, BELONGS_TO_PROJECT_IRI, AggregateFunction.COUNT, Optional.empty(), List.of(),
            materialised);
    }

    private static AttributeDefinition openTaskCountAttribute(boolean materialised) {
        return new AttributeDefinition(
            OPEN_TASK_COUNT_IRI, "openTaskCount", Cardinality.required(), false, false, false, true,
            false, DisplayHints.none(), Optional.of(openTaskCountRule(materialised)),
            Datatype.INTEGER);
    }

    private static TypeDefinition projectType(boolean materialised) {
        return new TypeDefinition(
            PROJECT_IRI, "Project", List.of(), List.of(openTaskCountAttribute(materialised)),
            DisplayHints.none(), false, Optional.empty());
    }

    private static final TypeDefinition TASK =
        new TypeDefinition(
            TASK_IRI, "Task", List.of(), List.of(BELONGS_TO_PROJECT), DisplayHints.none(), false,
            Optional.empty());

    private static MetaModelSnapshot buildSnapshot(boolean materialised) {
        return new MetaModelSnapshot(
            NS.substring(0, NS.length() - 1), Optional.empty(), Map.of(),
            List.of(projectType(materialised), TASK), new OntologyReport(true, List.of()));
    }

    private static BusinessObject project(ObjectId id, long version, Integer openTaskCount) {
        Map<PropertyRef, Value> properties =
            openTaskCount == null
                ? Map.of()
                : Map.of(new PropertyRef(OPEN_TASK_COUNT_IRI), new IntegerValue(openTaskCount));
        return new BusinessObject(
            id, new TypeRef(PROJECT_IRI), new TenantId("acme"), version, Optional.empty(),
            properties, new Audit(CREATED_AT, "carol", CREATED_AT, "carol"), false);
    }

    private static BusinessObject task(ObjectId id, ObjectId belongsToProjectId) {
        return new BusinessObject(
            id, new TypeRef(TASK_IRI), new TenantId("acme"), 1, Optional.empty(),
            Map.of(new PropertyRef(BELONGS_TO_PROJECT_IRI), Value.ref(belongsToProjectId)),
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

    private static DefaultDerivationRecomputer recomputer(
        FakeObjectStorePort store, FakeQueryPort queryPort, boolean materialised, int maxAttempts) {
        DerivationPlanner planner = new DerivationPlanner(queryPort, DerivationPluginRegistry.fromServiceLoader());
        return new DefaultDerivationRecomputer(
            new FakeOntologyPort(materialised), store, planner, maxAttempts, Duration.ofMillis(1), CLOCK);
    }

    @Test
    void objectUpdatedRecomputesAndCommitsWhenAggregateDiffersFromStored() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 3, 0));
        store.seed(task(taskId, projectId));
        FakeQueryPort queryPort = new FakeQueryPort(2); // two open tasks now
        DefaultDerivationRecomputer recomputer = recomputer(store, queryPort, true, 8);

        recomputer.recompute(ALICE, objectUpdatedEvent(taskId));

        BusinessObject updated = store.store.get(projectId);
        assertThat(updated.properties().get(new PropertyRef(OPEN_TASK_COUNT_IRI)))
            .isEqualTo(new IntegerValue(2));
        assertThat(updated.version()).isEqualTo(4);
        assertThat(store.commitCalls).isEqualTo(1);
        assertThat(store.lastOutboxKind).isEqualTo(OutboxEntry.KIND_OBJECT_UPDATED);
    }

    @Test
    void noCommitWhenRecomputedValueEqualsStoredValue() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 3, 2));
        store.seed(task(taskId, projectId));
        FakeQueryPort queryPort = new FakeQueryPort(2); // matches what's already stored
        DefaultDerivationRecomputer recomputer = recomputer(store, queryPort, true, 8);

        recomputer.recompute(ALICE, objectUpdatedEvent(taskId));

        assertThat(store.commitCalls).isEqualTo(0);
        assertThat(store.store.get(projectId).version()).isEqualTo(3);
    }

    @Test
    void objectDeletedResolvesTargetFromPayloadSnapshotNotFromFind() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 1, 1));
        // Deliberately do NOT seed the task: find(taskId) must return empty, proving the via value
        // is resolved from the ObjectDeleted payload's embedded properties snapshot.
        FakeQueryPort queryPort = new FakeQueryPort(0); // the deleted task no longer counts
        DefaultDerivationRecomputer recomputer = recomputer(store, queryPort, true, 8);

        recomputer.recompute(ALICE, objectDeletedEvent(taskId, projectId));

        BusinessObject updated = store.store.get(projectId);
        assertThat(updated.properties().get(new PropertyRef(OPEN_TASK_COUNT_IRI)))
            .isEqualTo(new IntegerValue(0));
        assertThat(store.commitCalls).isEqualTo(1);
    }

    @Test
    void staleObjectExceptionOnFirstAttemptIsRetriedAndConverges() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 3, 0));
        store.seed(task(taskId, projectId));
        store.failNextCommitsWithStaleVersion(projectId, 2);
        FakeQueryPort queryPort = new FakeQueryPort(1);
        DefaultDerivationRecomputer recomputer = recomputer(store, queryPort, true, 8);

        recomputer.recompute(ALICE, objectUpdatedEvent(taskId));

        BusinessObject updated = store.store.get(projectId);
        assertThat(updated.properties().get(new PropertyRef(OPEN_TASK_COUNT_IRI)))
            .isEqualTo(new IntegerValue(1));
        // 2 injected stale failures + 1 succeeding commit attempt.
        assertThat(store.commitCalls).isEqualTo(3);
    }

    @Test
    void exceptionPropagatesAfterMaxAttemptsExhausted() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 3, 0));
        store.seed(task(taskId, projectId));
        store.failNextCommitsWithStaleVersion(projectId, 10);
        FakeQueryPort queryPort = new FakeQueryPort(1);
        DefaultDerivationRecomputer recomputer = recomputer(store, queryPort, true, 3);

        assertThatThrownBy(() -> recomputer.recompute(ALICE, objectUpdatedEvent(taskId)))
            .isInstanceOf(StaleObjectException.class);
        assertThat(store.commitCalls).isEqualTo(3);
    }

    @Test
    void nonMaterialisedRollupIsNeverRecomputed() {
        ObjectId projectId = ObjectId.random();
        ObjectId taskId = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort();
        store.seed(project(projectId, 3, 0));
        store.seed(task(taskId, projectId));
        FakeQueryPort queryPort = new FakeQueryPort(5);
        DefaultDerivationRecomputer recomputer = recomputer(store, queryPort, false, 8);

        recomputer.recompute(ALICE, objectUpdatedEvent(taskId));

        assertThat(store.commitCalls).isEqualTo(0);
        assertThat(queryPort.aggregateRequests).isEmpty();
    }

    // --- fakes ---

    private static final class FakeOntologyPort implements OntologyPort {

        private final boolean materialised;

        FakeOntologyPort(boolean materialised) {
            this.materialised = materialised;
        }

        @Override
        public MetaModelSnapshot snapshot(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            return buildSnapshot(materialised);
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

    /**
     * Fake {@link QueryPort} whose {@link #aggregate} answers a {@code sq:count} rollup with a
     * fixed, canned open-task count for every requested target — enough to drive {@link
     * DerivationPlanner#computeRule} through a real {@link RollupRule} without reimplementing a
     * general-purpose aggregate engine.
     */
    private static final class FakeQueryPort implements QueryPort {

        private final int count;
        private final List<AggregateRequest> aggregateRequests = new ArrayList<>();

        FakeQueryPort(int count) {
            this.count = count;
        }

        @Override
        public QueryResult query(Scope scope, MetaModelSnapshot snapshot, Query query) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public void ensureIndexes(Scope scope, MetaModelSnapshot snapshot) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public AggregateResult aggregate(Scope scope, MetaModelSnapshot snapshot, AggregateRequest request) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            Objects.requireNonNull(request, "request must not be null");
            aggregateRequests.add(request);
            Map<ObjectId, Value> values = new HashMap<>();
            for (ObjectId id : request.targetIds()) {
                values.put(id, new IntegerValue(count));
            }
            return new AggregateResult(values);
        }
    }

    private static final class FakeObjectStorePort implements ObjectStorePort {

        private final Map<ObjectId, BusinessObject> store = new HashMap<>();
        private final Map<ObjectId, Integer> staleFailuresRemaining = new HashMap<>();
        private int commitCalls;
        private String lastOutboxKind;

        void seed(BusinessObject object) {
            store.put(object.id(), object);
        }

        void failNextCommitsWithStaleVersion(ObjectId id, int times) {
            staleFailuresRemaining.put(id, times);
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
                    ObjectId id = update.object().id();
                    int remaining = staleFailuresRemaining.getOrDefault(id, 0);
                    if (remaining > 0) {
                        staleFailuresRemaining.put(id, remaining - 1);
                        // Simulate a racing concurrent writer bumping the stored version between
                        // this recompute attempt's read and its commit.
                        BusinessObject current = store.get(id);
                        store.put(
                            id,
                            new BusinessObject(
                                current.id(), current.type(), current.tenant(),
                                current.version() + 1, current.state(), current.properties(),
                                current.audit(), current.deleted()));
                        throw new StaleObjectException(id, update.expectedVersion());
                    }
                    BusinessObject current = store.get(id);
                    if (current == null || current.deleted()) {
                        throw new ObjectNotFoundException(id);
                    }
                    if (current.version() != update.expectedVersion()) {
                        throw new StaleObjectException(id, update.expectedVersion());
                    }
                    store.put(id, update.object());
                    resultObjects.add(update.object());
                } else if (mutation instanceof Delete delete) {
                    throw new UnsupportedOperationException("not exercised by these tests");
                }
            }

            List<UUID> outboxIds = new ArrayList<>();
            for (OutboxEntry entry : changeSet.outbox()) {
                lastOutboxKind = entry.kind();
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
