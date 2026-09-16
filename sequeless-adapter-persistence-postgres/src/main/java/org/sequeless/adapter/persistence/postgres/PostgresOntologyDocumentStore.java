package org.sequeless.adapter.persistence.postgres;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.StoredOntologyDocument;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;

/**
 * The PostgreSQL-backed {@link OntologyDocumentStore}, sharing the {@link JdbcClient} and {@link
 * PlatformTransactionManager} that {@link PostgresObjectStore} uses for {@code sq_object}, against
 * the {@code sq_ontology_document} table created by {@code V1__objects_ontology_outbox.sql}.
 */
final class PostgresOntologyDocumentStore implements OntologyDocumentStore {

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    PostgresOntologyDocumentStore(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<StoredOntologyDocument> active(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return jdbcClient
            .sql(
                "SELECT id, version, format, content, created_at FROM sq_ontology_document "
                    + "WHERE tenant_id = :tenantId AND active")
            .param("tenantId", scope.tenantId().value())
            .query(PostgresOntologyDocumentStore::mapRow)
            .optional();
    }

    @Override
    public StoredOntologyDocument activate(Scope scope, OntologyDocument document) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(document, "document must not be null");
        String tenantId = scope.tenantId().value();

        return transactionTemplate.execute(status -> {
            // An advisory lock scoped to the tenant serializes concurrent activate() calls for the
            // same tenant across the deactivate/read-max/insert sequence below; PostgreSQL's default
            // READ COMMITTED isolation does not otherwise prevent two concurrent transactions from
            // computing the same "next version" for the same tenant.
            jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtext(:tenantId))")
                .param("tenantId", tenantId)
                .query()
                .singleRow();

            jdbcClient.sql("UPDATE sq_ontology_document SET active = false WHERE tenant_id = :tenantId AND active")
                .param("tenantId", tenantId)
                .update();

            int nextVersion =
                jdbcClient
                    .sql(
                        "SELECT COALESCE(MAX(version), 0) + 1 FROM sq_ontology_document "
                            + "WHERE tenant_id = :tenantId")
                    .param("tenantId", tenantId)
                    .query(Integer.class)
                    .single();

            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

            jdbcClient
                .sql(
                    "INSERT INTO sq_ontology_document (id, tenant_id, version, format, content, active, created_at) "
                        + "VALUES (:id, :tenantId, :version, :format, :content, true, :createdAt)")
                .param("id", id)
                .param("tenantId", tenantId)
                .param("version", nextVersion)
                .param("format", document.format().name())
                .param("content", document.content())
                .param("createdAt", Timestamp.from(createdAt))
                .update();

            return new StoredOntologyDocument(id, nextVersion, document, createdAt);
        });
    }

    private static StoredOntologyDocument mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        UUID id = (UUID) rs.getObject("id");
        int version = rs.getInt("version");
        OntologyFormat format = OntologyFormat.valueOf(rs.getString("format"));
        String content = rs.getString("content");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        return new StoredOntologyDocument(id, version, new OntologyDocument(content, format), createdAt);
    }
}
