package org.sequeless.testkit.object;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.StoredOntologyDocument;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.ontology.OntologyDocument;

/**
 * This testkit's own proof that {@link ObjectStoreContract} is not secretly biased toward
 * implementations backed by SQL, JSONB, or a Flyway-managed schema. This port holds every {@link
 * BusinessObject} in a plain map, guarded by a single monitor for the synchronized, all-or-nothing
 * {@link #commit} semantics the contract demands, and answers {@link #ontologyDocuments()} with an
 * equally trivial per-tenant version counter — genuine branching (the optimistic-lock check, the
 * soft-delete filtering, the atomic rollback of a whole {@link ChangeSet}), trivially implemented,
 * exactly like {@code FakeOntologyPort} plays for {@code OntologyContract} and {@code
 * DenyAllAuthorizationPort} plays for {@code AuthorizationContract}. If {@link ObjectStoreContract}
 * could only be passed by a relational adapter, it would not be a meaningful shared contract, and
 * this test's own contract subclass would fail.
 */
final class InMemoryObjectStorePort implements ObjectStorePort {

    private final Object lock = new Object();
    private final Map<ObjectId, BusinessObject> objects = new LinkedHashMap<>();
    private final List<OutboxEntry> outbox = new ArrayList<>();
    private final InMemoryOntologyDocumentStore ontologyDocuments = new InMemoryOntologyDocumentStore();

    @Override
    public Optional<BusinessObject> find(Scope scope, ObjectId id) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(id, "id must not be null");
        synchronized (lock) {
            BusinessObject object = objects.get(id);
            if (object == null || object.deleted() || !object.tenant().equals(scope.tenantId())) {
                return Optional.empty();
            }
            return Optional.of(object);
        }
    }

    @Override
    public PageResult<BusinessObject> browse(Scope scope, Set<TypeRef> types, Page page) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(types, "types must not be null");
        for (TypeRef type : types) {
            Objects.requireNonNull(type, "types must not contain null");
        }
        Objects.requireNonNull(page, "page must not be null");
        synchronized (lock) {
            List<BusinessObject> matching =
                objects.values().stream()
                    .filter(object -> !object.deleted())
                    .filter(object -> object.tenant().equals(scope.tenantId()))
                    .filter(object -> types.contains(object.type()))
                    .sorted(
                        Comparator.comparing((BusinessObject object) -> object.audit().createdAt())
                            .thenComparing(object -> object.id().value()))
                    .toList();
            long totalItems = matching.size();
            int fromIndex = Math.min(page.number() * page.size(), matching.size());
            int toIndex = Math.min(fromIndex + page.size(), matching.size());
            return new PageResult<>(matching.subList(fromIndex, toIndex), page.number(), page.size(), totalItems);
        }
    }

    @Override
    public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(ids, "ids must not be null");
        for (ObjectId id : ids) {
            Objects.requireNonNull(id, "ids must not contain null");
        }
        synchronized (lock) {
            Map<ObjectId, TypeRef> result = new LinkedHashMap<>();
            for (ObjectId id : ids) {
                BusinessObject object = objects.get(id);
                if (object != null && !object.deleted() && object.tenant().equals(scope.tenantId())) {
                    result.put(id, object.type());
                }
            }
            return result;
        }
    }

    @Override
    public CommitResult commit(Scope scope, ChangeSet changeSet) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(changeSet, "changeSet must not be null");
        synchronized (lock) {
            // Validate every mutation before applying any of them, so a StaleObjectException or
            // ObjectNotFoundException thrown partway through never leaves a partially-applied
            // changeset behind.
            for (Mutation mutation : changeSet.mutations()) {
                validate(scope, mutation);
            }
            List<BusinessObject> affected = new ArrayList<>();
            for (Mutation mutation : changeSet.mutations()) {
                affected.add(apply(scope, mutation));
            }
            List<UUID> outboxIds = new ArrayList<>();
            for (OutboxEntry entry : changeSet.outbox()) {
                outbox.add(entry);
                outboxIds.add(entry.id());
            }
            return new CommitResult(affected, outboxIds);
        }
    }

    @Override
    public OntologyDocumentStore ontologyDocuments() {
        return ontologyDocuments;
    }

    /**
     * @return the ids of every outbox row committed so far, in commit order; the test-only escape
     *     hatch {@link org.sequeless.testkit.object.ObjectStoreContract#persistedOutboxIds(Scope)}
     *     is overridden with, since this fake has no independent query surface to check against
     */
    List<UUID> outboxIds() {
        synchronized (lock) {
            return outbox.stream().map(OutboxEntry::id).toList();
        }
    }

    private void validate(Scope scope, Mutation mutation) {
        switch (mutation) {
            case Create create -> {
                // Nothing to validate up front: a Create always succeeds regardless of what, if
                // anything, is already stored at that id.
            }
            case Update update -> requireCurrentVersion(scope, update.object().id(), update.expectedVersion());
            case Delete delete -> requireCurrentVersion(scope, delete.id(), delete.expectedVersion());
        }
    }

    private void requireCurrentVersion(Scope scope, ObjectId id, long expectedVersion) {
        BusinessObject current = objects.get(id);
        if (current == null || current.deleted() || !current.tenant().equals(scope.tenantId())) {
            throw new ObjectNotFoundException(id);
        }
        if (current.version() != expectedVersion) {
            throw new StaleObjectException(id, expectedVersion);
        }
    }

    private BusinessObject apply(Scope scope, Mutation mutation) {
        return switch (mutation) {
            case Create create -> {
                BusinessObject stored =
                    new BusinessObject(
                        create.object().id(),
                        create.object().type(),
                        scope.tenantId(),
                        1,
                        create.object().state(),
                        create.object().properties(),
                        create.object().audit(),
                        false);
                objects.put(stored.id(), stored);
                yield stored;
            }
            case Update update -> {
                BusinessObject stored =
                    new BusinessObject(
                        update.object().id(),
                        update.object().type(),
                        scope.tenantId(),
                        update.expectedVersion() + 1,
                        update.object().state(),
                        update.object().properties(),
                        update.object().audit(),
                        false);
                objects.put(stored.id(), stored);
                yield stored;
            }
            case Delete delete -> {
                BusinessObject previous = objects.get(delete.id());
                BusinessObject stored =
                    new BusinessObject(
                        previous.id(),
                        previous.type(),
                        scope.tenantId(),
                        delete.expectedVersion() + 1,
                        previous.state(),
                        previous.properties(),
                        new Audit(
                            previous.audit().createdAt(), previous.audit().createdBy(), delete.at(), delete.by()),
                        true);
                objects.put(stored.id(), stored);
                yield stored;
            }
        };
    }

    /**
     * A per-tenant version counter with no persistence at all: {@link #activate} assigns the next
     * integer for the tenant and overwrites whatever was previously active, which is all {@link
     * OntologyDocumentStore}'s contract requires — there is no separate "deactivate" step to forget
     * because only the currently-active document is ever kept.
     */
    private static final class InMemoryOntologyDocumentStore implements OntologyDocumentStore {

        private final Object lock = new Object();
        private final Map<TenantId, StoredOntologyDocument> active = new LinkedHashMap<>();
        private final Map<TenantId, Integer> nextVersion = new LinkedHashMap<>();

        @Override
        public Optional<StoredOntologyDocument> active(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            synchronized (lock) {
                return Optional.ofNullable(active.get(scope.tenantId()));
            }
        }

        @Override
        public StoredOntologyDocument activate(Scope scope, OntologyDocument document) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(document, "document must not be null");
            synchronized (lock) {
                int version = nextVersion.merge(scope.tenantId(), 1, Integer::sum);
                StoredOntologyDocument stored =
                    new StoredOntologyDocument(UUID.randomUUID(), version, document, Instant.now());
                active.put(scope.tenantId(), stored);
                return stored;
            }
        }
    }
}
