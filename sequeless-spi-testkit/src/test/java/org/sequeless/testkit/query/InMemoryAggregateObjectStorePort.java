package org.sequeless.testkit.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.sequeless.spi.Scope;
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
 * A minimal in-memory {@link ObjectStorePort} purely so {@link AggregateContract} — abstract, and
 * otherwise unreachable in this module — has a concrete pairing to run against before T7's real
 * Postgres implementation extends it. Deliberately not the {@code InMemoryObjectStorePort} in
 * {@code org.sequeless.testkit.object} (package-private there, and this module has no {@code
 * ObjectStoreContract} obligation to prove here) — this is a smaller, purpose-built double that
 * only needs to support {@link #commit} faithfully enough to seed {@link AggregateContract}'s
 * fixtures and soft-delete a source object.
 *
 * <p>{@link InMemoryAggregateQueryPort} reads directly from this store's {@link #snapshot(Scope,
 * Set)}, so the two are always used as a pair, exactly as a real adapter's {@code QueryPort}
 * queries the same underlying storage its {@code ObjectStorePort} writes to.
 */
final class InMemoryAggregateObjectStorePort implements ObjectStorePort {

    private final Object lock = new Object();
    private final Map<ObjectId, BusinessObject> objects = new LinkedHashMap<>();

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
        throw new UnsupportedOperationException(
            "browse is not exercised by AggregateContract; this double only supports commit and "
                + "aggregation reads");
    }

    @Override
    public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
        throw new UnsupportedOperationException(
            "typesOf is not exercised by AggregateContract; this double only supports commit and "
                + "aggregation reads");
    }

    @Override
    public CommitResult commit(Scope scope, ChangeSet changeSet) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(changeSet, "changeSet must not be null");
        synchronized (lock) {
            for (Mutation mutation : changeSet.mutations()) {
                validate(scope, mutation);
            }
            List<BusinessObject> affected = new ArrayList<>();
            for (Mutation mutation : changeSet.mutations()) {
                affected.add(apply(scope, mutation));
            }
            List<UUID> outboxIds = new ArrayList<>();
            for (OutboxEntry entry : changeSet.outbox()) {
                outboxIds.add(entry.id());
            }
            return new CommitResult(affected, outboxIds);
        }
    }

    @Override
    public OntologyDocumentStore ontologyDocuments() {
        return new OntologyDocumentStore() {
            @Override
            public Optional<StoredOntologyDocument> active(Scope scope) {
                return Optional.empty();
            }

            @Override
            public StoredOntologyDocument activate(Scope scope, OntologyDocument document) {
                throw new UnsupportedOperationException(
                    "AggregateContract never activates an ontology through the store");
            }
        };
    }

    /**
     * @param scope the tenant to read for; only objects with a matching {@link
     *     org.sequeless.spi.TenantId} are returned
     * @param typeIris the type IRIs to include, matched as a union exactly like {@link
     *     ObjectStorePort#browse}'s {@code types}
     * @return every non-deleted object of {@code scope.tenantId()} whose type is in {@code
     *     typeIris}
     */
    List<BusinessObject> snapshot(Scope scope, Set<String> typeIris) {
        synchronized (lock) {
            List<BusinessObject> matching = new ArrayList<>();
            for (BusinessObject object : objects.values()) {
                if (!object.deleted()
                    && object.tenant().equals(scope.tenantId())
                    && typeIris.contains(object.type().iri())) {
                    matching.add(object);
                }
            }
            return matching;
        }
    }

    private void validate(Scope scope, Mutation mutation) {
        switch (mutation) {
            case Create create -> {
                // A Create always succeeds regardless of what, if anything, is already stored.
            }
            case Update update -> requireCurrentVersion(update.object().id(), update.expectedVersion());
            case Delete delete -> requireCurrentVersion(delete.id(), delete.expectedVersion());
        }
    }

    private void requireCurrentVersion(ObjectId id, long expectedVersion) {
        BusinessObject current = objects.get(id);
        if (current == null || current.deleted()) {
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
                        previous.tenant(),
                        delete.expectedVersion() + 1,
                        previous.state(),
                        previous.properties(),
                        new Audit(
                            previous.audit().createdAt(), previous.audit().createdBy(), delete.at(),
                            delete.by()),
                        true);
                objects.put(stored.id(), stored);
                yield stored;
            }
        };
    }
}
