package org.sequeless.testkit.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
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
import org.sequeless.spi.object.StoredOntologyDocument;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link ObjectStorePort}'s and
 * {@link OntologyDocumentStore}'s interface-level javadoc. Every {@link ObjectStorePort}
 * implementation — adapter or test double — is expected to satisfy every clause of that javadoc,
 * and this class exercises each clause once, against whatever port {@link #freshStore()} supplies.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #freshStore()} to return a brand-new, empty implementation under test:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends ObjectStoreContract {
 *     protected ObjectStorePort freshStore() {
 *         return new MyAdapter();
 *     }
 * }
 * }</pre>
 *
 * <p>Every fixture object this contract builds uses arbitrary {@link TypeRef}/{@link PropertyRef}
 * IRIs of its own devising, never the reference-domain ontology — {@link ObjectStorePort} has no
 * notion of an ontology at all, only opaque type and property IRIs, so a conforming implementation
 * must not need one either.
 *
 * <p><b>What this contract deliberately does not check.</b> No assertion here depends on a
 * particular storage technology, on SQL, on JSONB, or on any adapter-specific configuration. Every
 * assertion is about shape (non-null results, correctly-shaped pages), null-safety (rejecting
 * missing arguments), tenant isolation, the optimistic-locking and soft-delete guarantees {@link
 * ObjectStorePort#commit}'s javadoc makes, and the all-or-nothing rollback guarantee. A trivial
 * in-memory implementation with no SQL, no JSONB, and no Flyway migration must be able to pass this
 * contract unmodified, exactly as {@code sequeless-spi-testkit}'s own {@code
 * InMemoryObjectStorePortContractTest} proves; a contract that only a relational adapter could pass
 * would make "this adapter passes the shared contract" a worthless claim.
 *
 * <p><b>The outbox hook.</b> {@link #persistedOutboxIds(Scope)} defaults to returning an empty
 * list and is not abstract, so this contract still compiles and runs every clause that does not
 * depend on it for any implementor that does not override it. An implementor that wants the
 * outbox-row-count assertions in {@link #outboxIdsInCommitResultMatchPersistedRows()} and {@link
 * #staleUpdateThrowsAndWritesNothing()} to run against its own real persisted rows must override
 * it; skipping the override silently skips those specific assertions rather than failing them.
 */
public abstract class ObjectStoreContract {

    private static final String NS = "https://example.org/objectstore#";

    private static final TypeRef TYPE_A = new TypeRef(NS + "TypeA");
    private static final TypeRef TYPE_B = new TypeRef(NS + "TypeB");

    private static final PropertyRef TEXT_PROP = new PropertyRef(NS + "text");
    private static final PropertyRef INTEGER_PROP = new PropertyRef(NS + "integer");
    private static final PropertyRef DECIMAL_PROP = new PropertyRef(NS + "decimal");
    private static final PropertyRef BOOL_PROP = new PropertyRef(NS + "bool");
    private static final PropertyRef DATETIME_PROP = new PropertyRef(NS + "dateTime");
    private static final PropertyRef DATE_PROP = new PropertyRef(NS + "date");
    private static final PropertyRef REF_PROP = new PropertyRef(NS + "ref");
    private static final PropertyRef LIST_PROP = new PropertyRef(NS + "list");

    /**
     * @return the {@link ObjectStorePort} implementation under test, brand-new and empty; invoked
     *     fresh for every {@code @Test} method, so implementors may return a new instance each time
     *     or a shared one, whichever suits the port under test — but the returned port must not
     *     carry over state from a previous test
     */
    protected abstract ObjectStorePort freshStore();

    /**
     * A default no-op hook overridden only by implementors that want the outbox-row-count
     * assertions in this contract to run against real, independently-queried storage. The default
     * implementation returns an empty list unconditionally, which is indistinguishable from "no
     * outbox rows exist yet" — so a subclass that does not override this method simply does not get
     * those specific assertions exercised, rather than failing them.
     *
     * @param scope the tenant and principal to query persisted outbox rows for
     * @return the ids of every outbox row currently persisted for {@code scope.tenantId()}, or an
     *     empty list if this hook is not overridden
     */
    protected List<UUID> persistedOutboxIds(Scope scope) {
        return List.of();
    }

    @Test
    void findRejectsNullScopeOrId() {
        ObjectStorePort store = freshStore();
        assertThatNullPointerException().isThrownBy(() -> store.find(null, ObjectId.random()));
        assertThatNullPointerException()
            .isThrownBy(() -> store.find(Fixtures.defaultScope(), null));
    }

    @Test
    void browseRejectsNullArguments() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        Page page = new Page(0, 20);
        Set<TypeRef> types = Set.of(TYPE_A);

        assertThatNullPointerException().isThrownBy(() -> store.browse(null, types, page));
        assertThatNullPointerException().isThrownBy(() -> store.browse(scope, null, page));
        assertThatNullPointerException().isThrownBy(() -> store.browse(scope, types, null));

        Set<TypeRef> withNull = new HashSet<>();
        withNull.add(null);
        assertThatNullPointerException().isThrownBy(() -> store.browse(scope, withNull, page));
    }

    @Test
    void typesOfRejectsNullArguments() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();

        assertThatNullPointerException().isThrownBy(() -> store.typesOf(null, Set.of()));
        assertThatNullPointerException().isThrownBy(() -> store.typesOf(scope, null));

        Set<ObjectId> withNull = new HashSet<>();
        withNull.add(null);
        assertThatNullPointerException().isThrownBy(() -> store.typesOf(scope, withNull));
    }

    @Test
    void commitRejectsNullArguments() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ChangeSet changeSet =
            new ChangeSet(
                List.of(new Create(newObject(scope, ObjectId.random(), TYPE_A, Map.of()))),
                List.of());

        assertThatNullPointerException().isThrownBy(() -> store.commit(null, changeSet));
        assertThatNullPointerException().isThrownBy(() -> store.commit(scope, null));
    }

    @Test
    void ontologyDocumentsRejectsNullArguments() {
        ObjectStorePort store = freshStore();
        OntologyDocumentStore documents = store.ontologyDocuments();
        Scope scope = Fixtures.defaultScope();
        OntologyDocument document = new OntologyDocument("content", OntologyFormat.TURTLE);

        assertThatNullPointerException().isThrownBy(() -> documents.active(null));
        assertThatNullPointerException().isThrownBy(() -> documents.activate(null, document));
        assertThatNullPointerException().isThrownBy(() -> documents.activate(scope, null));
    }

    @Test
    void createThenFindRoundTripsEveryValueVariantAndAudit() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ObjectId id = ObjectId.random();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        Audit audit = new Audit(at, "alice", at, "alice");
        ObjectId referenced = ObjectId.random();

        Map<PropertyRef, Value> properties =
            Map.of(
                TEXT_PROP, Value.text("hello"),
                INTEGER_PROP, Value.integer(42),
                DECIMAL_PROP, Value.decimal(new BigDecimal("12.50")),
                BOOL_PROP, Value.bool(true),
                DATETIME_PROP, Value.dateTime(at),
                DATE_PROP, Value.date(LocalDate.of(2026, 1, 1)),
                REF_PROP, Value.ref(referenced),
                LIST_PROP, Value.list(List.of(Value.text("a"), Value.text("b"))));

        BusinessObject toCreate =
            new BusinessObject(
                id, TYPE_A, scope.tenantId(), 1, Optional.empty(), properties, audit, false);

        store.commit(scope, new ChangeSet(List.of(new Create(toCreate)), List.of()));

        Optional<BusinessObject> found = store.find(scope, id);
        assertThat(found).contains(toCreate);
        assertThat(found.orElseThrow().audit()).isEqualTo(audit);
    }

    @Test
    void createStartsAtVersionOneThenUpdateIncrements() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ObjectId id = ObjectId.random();

        CommitResult createResult =
            store.commit(
                scope,
                new ChangeSet(
                    List.of(
                        new Create(newObject(scope, id, TYPE_A, Map.of(TEXT_PROP, Value.text("v1"))))),
                    List.of()));
        assertThat(createResult.objects()).hasSize(1);
        assertThat(createResult.objects().get(0).version()).isEqualTo(1);

        CommitResult updateResult =
            store.commit(
                scope,
                new ChangeSet(
                    List.of(
                        new Update(
                            newObject(scope, id, TYPE_A, Map.of(TEXT_PROP, Value.text("v2"))), 1)),
                    List.of()));
        assertThat(updateResult.objects()).hasSize(1);
        assertThat(updateResult.objects().get(0).version()).isEqualTo(2);

        Optional<BusinessObject> found = store.find(scope, id);
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().version()).isEqualTo(2);
        assertThat(found.orElseThrow().properties()).containsEntry(TEXT_PROP, Value.text("v2"));
    }

    @Test
    void staleUpdateThrowsAndWritesNothing() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ObjectId id = ObjectId.random();
        ObjectId secondId = ObjectId.random();
        List<UUID> outboxBefore = persistedOutboxIds(scope);

        store.commit(
            scope, new ChangeSet(List.of(new Create(newObject(scope, id, TYPE_A, Map.of()))), List.of()));

        Mutation staleUpdate =
            new Update(newObject(scope, id, TYPE_A, Map.of(TEXT_PROP, Value.text("nope"))), 99);
        Mutation unrelatedCreate = new Create(newObject(scope, secondId, TYPE_A, Map.of()));

        assertThatThrownBy(
                () ->
                    store.commit(
                        scope, new ChangeSet(List.of(staleUpdate, unrelatedCreate), List.of())))
            .isInstanceOf(StaleObjectException.class);

        assertThat(store.find(scope, secondId)).isEmpty();
        assertThat(persistedOutboxIds(scope)).hasSameSizeAs(outboxBefore);
    }

    @Test
    void updateOfMissingThrowsObjectNotFound() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ObjectId missing = ObjectId.random();
        Mutation update = new Update(newObject(scope, missing, TYPE_A, Map.of()), 1);

        assertThatThrownBy(
                () -> store.commit(scope, new ChangeSet(List.of(update), List.of())))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void deleteOfMissingThrowsObjectNotFound() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ObjectId missing = ObjectId.random();
        Mutation delete = new Delete(missing, 1, Instant.now(), "tester");

        assertThatThrownBy(
                () -> store.commit(scope, new ChangeSet(List.of(delete), List.of())))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void softDeleteHidesFromFindBrowseAndTypesOf() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ObjectId id = ObjectId.random();

        store.commit(
            scope, new ChangeSet(List.of(new Create(newObject(scope, id, TYPE_A, Map.of()))), List.of()));
        store.commit(
            scope,
            new ChangeSet(List.of(new Delete(id, 1, Instant.now(), "tester")), List.of()));

        assertThat(store.find(scope, id)).isEmpty();
        PageResult<BusinessObject> page = store.browse(scope, Set.of(TYPE_A), new Page(0, 20));
        assertThat(page.items()).extracting(BusinessObject::id).doesNotContain(id);
        assertThat(store.typesOf(scope, Set.of(id))).isEmpty();
    }

    @Test
    void typesOfResolvesKnownObjectsAndOmitsUnknown() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ObjectId known = ObjectId.random();
        ObjectId unknown = ObjectId.random();

        store.commit(
            scope,
            new ChangeSet(List.of(new Create(newObject(scope, known, TYPE_A, Map.of()))), List.of()));

        Map<ObjectId, TypeRef> resolved = store.typesOf(scope, Set.of(known, unknown));
        assertThat(resolved).containsExactly(Map.entry(known, TYPE_A));
        assertThat(store.typesOf(scope, Set.of())).isEmpty();
    }

    @Test
    void browsePagingTotalsOrderingAndOverrun() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        List<ObjectId> expectedOrder = new ArrayList<>();
        List<Mutation> mutations = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ObjectId id = ObjectId.random();
            expectedOrder.add(id);
            mutations.add(new Create(objectAt(scope, id, TYPE_A, base.plusSeconds(i))));
        }
        for (int i = 0; i < 2; i++) {
            ObjectId id = ObjectId.random();
            expectedOrder.add(id);
            mutations.add(new Create(objectAt(scope, id, TYPE_B, base.plusSeconds(10 + i))));
        }
        store.commit(scope, new ChangeSet(mutations, List.of()));

        PageResult<BusinessObject> page0 =
            store.browse(scope, Set.of(TYPE_A, TYPE_B), new Page(0, 3));
        assertThat(page0.items())
            .extracting(BusinessObject::id)
            .containsExactlyElementsOf(expectedOrder.subList(0, 3));
        assertThat(page0.totalItems()).isEqualTo(5);
        assertThat(page0.totalPages()).isEqualTo(2);

        PageResult<BusinessObject> page1 =
            store.browse(scope, Set.of(TYPE_A, TYPE_B), new Page(1, 3));
        assertThat(page1.items())
            .extracting(BusinessObject::id)
            .containsExactlyElementsOf(expectedOrder.subList(3, 5));
        assertThat(page1.totalItems()).isEqualTo(5);

        PageResult<BusinessObject> overrun =
            store.browse(scope, Set.of(TYPE_A, TYPE_B), new Page(5, 3));
        assertThat(overrun.items()).isEmpty();
        assertThat(overrun.totalItems()).isEqualTo(5);
        assertThat(overrun.totalPages()).isEqualTo(2);
    }

    @Test
    void outboxIdsInCommitResultMatchPersistedRows() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        ObjectId first = ObjectId.random();
        ObjectId second = ObjectId.random();
        OutboxEntry entry1 = outboxEntry(first);
        OutboxEntry entry2 = outboxEntry(second);

        ChangeSet changeSet =
            new ChangeSet(
                List.of(
                    new Create(newObject(scope, first, TYPE_A, Map.of())),
                    new Create(newObject(scope, second, TYPE_A, Map.of()))),
                List.of(entry1, entry2));

        CommitResult result = store.commit(scope, changeSet);

        assertThat(result.outboxIds()).hasSize(2);
        List<UUID> persisted = persistedOutboxIds(scope);
        if (!persisted.isEmpty()) {
            assertThat(persisted).containsExactlyInAnyOrderElementsOf(result.outboxIds());
        }
    }

    @Test
    void objectsAreIsolatedByTenant() {
        ObjectStorePort store = freshStore();
        Scope alice = Fixtures.scope("alice");
        Scope otherTenant = new Scope(new TenantId("other-tenant"), Principal.ANONYMOUS);
        ObjectId id = ObjectId.random();

        store.commit(
            alice, new ChangeSet(List.of(new Create(newObject(alice, id, TYPE_A, Map.of()))), List.of()));

        assertThat(store.find(otherTenant, id)).isEmpty();
        assertThat(store.browse(otherTenant, Set.of(TYPE_A), new Page(0, 20)).items()).isEmpty();
        assertThat(store.typesOf(otherTenant, Set.of(id))).isEmpty();

        assertThat(store.find(alice, id)).isPresent();
    }

    @Test
    void ontologyDocumentActiveIsEmptyInitiallyThenTracksVersions() {
        ObjectStorePort store = freshStore();
        Scope scope = Fixtures.defaultScope();
        OntologyDocumentStore documents = store.ontologyDocuments();

        assertThat(documents.active(scope)).isEmpty();

        OntologyDocument first = new OntologyDocument("first content", OntologyFormat.TURTLE);
        StoredOntologyDocument stored1 = documents.activate(scope, first);
        assertThat(stored1.version()).isEqualTo(1);
        assertThat(documents.active(scope)).contains(stored1);

        OntologyDocument second = new OntologyDocument("second content", OntologyFormat.TURTLE);
        StoredOntologyDocument stored2 = documents.activate(scope, second);
        assertThat(stored2.version()).isEqualTo(2);

        Optional<StoredOntologyDocument> active = documents.active(scope);
        assertThat(active).isPresent();
        assertThat(active.orElseThrow().version()).isEqualTo(2);
        assertThat(active.orElseThrow().document()).isEqualTo(second);
    }

    private static BusinessObject newObject(
        Scope scope, ObjectId id, TypeRef type, Map<PropertyRef, Value> properties) {
        return objectAt(scope, id, type, properties, Instant.now());
    }

    private static BusinessObject objectAt(
        Scope scope, ObjectId id, TypeRef type, Instant createdAt) {
        return objectAt(scope, id, type, Map.of(), createdAt);
    }

    private static BusinessObject objectAt(
        Scope scope,
        ObjectId id,
        TypeRef type,
        Map<PropertyRef, Value> properties,
        Instant createdAt) {
        String principalId = scope.principal().id();
        Audit audit = new Audit(createdAt, principalId, createdAt, principalId);
        return new BusinessObject(
            id, type, scope.tenantId(), 1, Optional.empty(), properties, audit, false);
    }

    private static OutboxEntry outboxEntry(ObjectId subject) {
        return new OutboxEntry(
            UUID.randomUUID(),
            OutboxEntry.KIND_OBJECT_CREATED,
            Map.of("objectId", subject.value().toString()),
            Instant.now());
    }
}
