package org.sequeless.adapter.persistence.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;

/**
 * The PostgreSQL-backed {@link ObjectStorePort}: {@code sq_object} for business objects, {@code
 * sq_outbox} for the transactional outbox, and (via {@link #ontologyDocuments()}) {@code
 * sq_ontology_document} for versioned ontology persistence — all created by this module's own
 * {@code V1__objects_ontology_outbox.sql} migration, run independently of Spring Boot's Flyway
 * autoconfiguration (see {@code plan.md} §3).
 *
 * <p>Reads use {@link JdbcClient}; {@link #commit} and {@link
 * PostgresOntologyDocumentStore#activate} each run inside one {@link TransactionTemplate}
 * transaction, satisfying the all-or-nothing guarantees {@link ObjectStorePort#commit} and {@link
 * OntologyDocumentStore#activate} document.
 */
public final class PostgresObjectStore implements ObjectStorePort {

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;
    private final PostgresOntologyDocumentStore ontologyDocuments;

    /**
     * @param jdbcClient the client to run reads and, inside {@link #commit}, writes through; must
     *     not be {@code null}
     * @param transactionManager the transaction manager {@link #commit} and {@link
     *     #ontologyDocuments()}'s {@code activate} wrap their work in; must not be {@code null}
     */
    public PostgresObjectStore(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.ontologyDocuments = new PostgresOntologyDocumentStore(jdbcClient, transactionManager);
    }

    @Override
    public Optional<BusinessObject> find(Scope scope, ObjectId id) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(id, "id must not be null");
        return jdbcClient
            .sql(
                "SELECT id, tenant_id, type_iri, version, state, props, created_at, created_by, "
                    + "updated_at, updated_by, deleted_at FROM sq_object "
                    + "WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL")
            .param("id", id.value())
            .param("tenantId", scope.tenantId().value())
            .query(PostgresObjectStore::mapRow)
            .optional();
    }

    @Override
    public PageResult<BusinessObject> browse(Scope scope, Set<TypeRef> types, Page page) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(types, "types must not be null");
        for (TypeRef type : types) {
            Objects.requireNonNull(type, "types must not contain null");
        }
        Objects.requireNonNull(page, "page must not be null");

        if (types.isEmpty()) {
            return new PageResult<>(List.of(), page.number(), page.size(), 0);
        }

        List<String> typeIris = types.stream().map(TypeRef::iri).toList();
        String tenantId = scope.tenantId().value();

        long totalItems =
            jdbcClient
                .sql(
                    "SELECT COUNT(*) FROM sq_object WHERE tenant_id = :tenantId "
                        + "AND type_iri IN (:types) AND deleted_at IS NULL")
                .param("tenantId", tenantId)
                .param("types", typeIris)
                .query(Long.class)
                .single();

        List<BusinessObject> items =
            jdbcClient
                .sql(
                    "SELECT id, tenant_id, type_iri, version, state, props, created_at, created_by, "
                        + "updated_at, updated_by, deleted_at FROM sq_object "
                        + "WHERE tenant_id = :tenantId AND type_iri IN (:types) AND deleted_at IS NULL "
                        + "ORDER BY created_at, id LIMIT :limit OFFSET :offset")
                .param("tenantId", tenantId)
                .param("types", typeIris)
                .param("limit", page.size())
                .param("offset", page.number() * page.size())
                .query(PostgresObjectStore::mapRow)
                .list();

        return new PageResult<>(items, page.number(), page.size(), totalItems);
    }

    @Override
    public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(ids, "ids must not be null");
        for (ObjectId id : ids) {
            Objects.requireNonNull(id, "ids must not contain null");
        }
        if (ids.isEmpty()) {
            return Map.of();
        }

        List<UUID> rawIds = ids.stream().map(ObjectId::value).toList();
        List<Map<String, Object>> rows =
            jdbcClient
                .sql(
                    "SELECT id, type_iri FROM sq_object WHERE tenant_id = :tenantId "
                        + "AND id IN (:ids) AND deleted_at IS NULL")
                .param("tenantId", scope.tenantId().value())
                .param("ids", rawIds)
                .query()
                .listOfRows();

        Map<ObjectId, TypeRef> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(
                new ObjectId((UUID) row.get("id")), new TypeRef((String) row.get("type_iri")));
        }
        return result;
    }

    @Override
    public CommitResult commit(Scope scope, ChangeSet changeSet) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(changeSet, "changeSet must not be null");
        String tenantId = scope.tenantId().value();

        return transactionTemplate.execute(status -> {
            // Validate every mutation before applying any of them, so a StaleObjectException or
            // ObjectNotFoundException never leaves a partially-built statement sequence behind and
            // mirrors InMemoryObjectStorePort's two-pass discipline (F10).
            for (Mutation mutation : changeSet.mutations()) {
                validate(tenantId, mutation);
            }

            List<BusinessObject> affected = new ArrayList<>();
            for (Mutation mutation : changeSet.mutations()) {
                affected.add(apply(scope, tenantId, mutation));
            }

            List<UUID> outboxIds = new ArrayList<>();
            for (OutboxEntry entry : changeSet.outbox()) {
                insertOutboxEntry(tenantId, entry);
                outboxIds.add(entry.id());
            }

            return new CommitResult(affected, outboxIds);
        });
    }

    @Override
    public OntologyDocumentStore ontologyDocuments() {
        return ontologyDocuments;
    }

    /**
     * Locks and checks the current row for an {@link Update} or {@link Delete}, throwing {@link
     * ObjectNotFoundException} or {@link StaleObjectException} as {@link ObjectStorePort#commit}'s
     * javadoc requires. A {@link Create} has nothing to validate up front.
     */
    private void validate(String tenantId, Mutation mutation) {
        switch (mutation) {
            case Create create -> {
                // Nothing to validate: a Create always succeeds regardless of what, if anything,
                // is already stored at that id.
            }
            case Update update -> requireCurrentVersion(tenantId, update.object().id(), update.expectedVersion());
            case Delete delete -> requireCurrentVersion(tenantId, delete.id(), delete.expectedVersion());
        }
    }

    private void requireCurrentVersion(String tenantId, ObjectId id, long expectedVersion) {
        Optional<Map<String, Object>> row =
            jdbcClient
                .sql(
                    "SELECT version, deleted_at FROM sq_object WHERE id = :id AND tenant_id = :tenantId "
                        + "FOR UPDATE")
                .param("id", id.value())
                .param("tenantId", tenantId)
                .query()
                .listOfRows()
                .stream()
                .findFirst();

        if (row.isEmpty() || row.get().get("deleted_at") != null) {
            throw new ObjectNotFoundException(id);
        }
        long currentVersion = ((Number) row.get().get("version")).longValue();
        if (currentVersion != expectedVersion) {
            throw new StaleObjectException(id, expectedVersion);
        }
    }

    private BusinessObject apply(Scope scope, String tenantId, Mutation mutation) {
        return switch (mutation) {
            case Create create -> {
                BusinessObject object = create.object();
                Audit audit = object.audit();
                jdbcClient
                    .sql(
                        "INSERT INTO sq_object (id, tenant_id, type_iri, version, state, props, "
                            + "created_at, created_by, updated_at, updated_by, deleted_at) "
                            + "VALUES (:id, :tenantId, :typeIri, 1, :state, CAST(:props AS jsonb), "
                            + ":createdAt, :createdBy, :updatedAt, :updatedBy, NULL)")
                    .param("id", object.id().value())
                    .param("tenantId", tenantId)
                    .param("typeIri", object.type().iri())
                    .param("state", object.state().orElse(null))
                    .param("props", ValueJsonCodec.toJson(object.properties()))
                    .param("createdAt", Timestamp.from(audit.createdAt()))
                    .param("createdBy", audit.createdBy())
                    .param("updatedAt", Timestamp.from(audit.updatedAt()))
                    .param("updatedBy", audit.updatedBy())
                    .update();
                yield new BusinessObject(
                    object.id(), object.type(), scope.tenantId(), 1, object.state(),
                    object.properties(), audit, false);
            }
            case Update update -> {
                BusinessObject object = update.object();
                long newVersion = update.expectedVersion() + 1;
                Audit audit = object.audit();
                jdbcClient
                    .sql(
                        "UPDATE sq_object SET version = :newVersion, state = :state, "
                            + "props = CAST(:props AS jsonb), updated_at = :updatedAt, "
                            + "updated_by = :updatedBy WHERE id = :id AND tenant_id = :tenantId "
                            + "AND version = :expectedVersion")
                    .param("newVersion", newVersion)
                    .param("state", object.state().orElse(null))
                    .param("props", ValueJsonCodec.toJson(object.properties()))
                    .param("updatedAt", Timestamp.from(audit.updatedAt()))
                    .param("updatedBy", audit.updatedBy())
                    .param("id", object.id().value())
                    .param("tenantId", tenantId)
                    .param("expectedVersion", update.expectedVersion())
                    .update();
                yield new BusinessObject(
                    object.id(), object.type(), scope.tenantId(), newVersion, object.state(),
                    object.properties(), audit, false);
            }
            case Delete delete -> {
                BusinessObject previous =
                    find(scope, delete.id())
                        .orElseThrow(() -> new ObjectNotFoundException(delete.id()));
                long newVersion = delete.expectedVersion() + 1;
                jdbcClient
                    .sql(
                        "UPDATE sq_object SET deleted_at = :deletedAt, updated_at = :updatedAt, "
                            + "updated_by = :updatedBy, version = :newVersion "
                            + "WHERE id = :id AND tenant_id = :tenantId")
                    .param("deletedAt", Timestamp.from(delete.at()))
                    .param("updatedAt", Timestamp.from(delete.at()))
                    .param("updatedBy", delete.by())
                    .param("newVersion", newVersion)
                    .param("id", delete.id().value())
                    .param("tenantId", tenantId)
                    .update();
                Audit audit =
                    new Audit(previous.audit().createdAt(), previous.audit().createdBy(), delete.at(), delete.by());
                yield new BusinessObject(
                    previous.id(), previous.type(), scope.tenantId(), newVersion, previous.state(),
                    previous.properties(), audit, true);
            }
        };
    }

    private void insertOutboxEntry(String tenantId, OutboxEntry entry) {
        UUID objectId = UUID.fromString((String) entry.payload().get("objectId"));
        jdbcClient
            .sql(
                "INSERT INTO sq_outbox (id, tenant_id, object_id, kind, payload, occurred_at, attempts) "
                    + "VALUES (:id, :tenantId, :objectId, :kind, CAST(:payload AS jsonb), :occurredAt, 0)")
            .param("id", entry.id())
            .param("tenantId", tenantId)
            .param("objectId", objectId)
            .param("kind", entry.kind())
            .param("payload", OutboxPayloadJsonCodec.toJson(entry.payload()))
            .param("occurredAt", Timestamp.from(entry.occurredAt()))
            .update();
    }

    private static BusinessObject mapRow(ResultSet rs, int rowNum) throws SQLException {
        ObjectId id = new ObjectId((UUID) rs.getObject("id"));
        TenantId tenant = new TenantId(rs.getString("tenant_id"));
        TypeRef type = new TypeRef(rs.getString("type_iri"));
        long version = rs.getLong("version");
        Optional<String> state = Optional.ofNullable(rs.getString("state"));
        Map<PropertyRef, Value> properties = ValueJsonCodec.fromJson(rs.getString("props"));
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        String createdBy = rs.getString("created_by");
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        String updatedBy = rs.getString("updated_by");
        boolean deleted = rs.getTimestamp("deleted_at") != null;
        Audit audit = new Audit(createdAt, createdBy, updatedAt, updatedBy);
        return new BusinessObject(id, type, tenant, version, state, properties, audit, deleted);
    }
}
