package org.sequeless.adapter.persistence.postgres;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.testkit.object.ObjectStoreContract;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresObjectStore} satisfies {@link ObjectStoreContract} — the same behavioural
 * contract {@code InMemoryObjectStorePortContractTest} proves for the in-memory testkit double —
 * against a real PostgreSQL 18 instance started by Testcontainers.
 *
 * <p>{@link #freshStore()} returns a new {@link PostgresObjectStore} wired to the container's
 * {@link DataSource} on every call, but the underlying tables are truncated between tests (in
 * {@link #truncateTables()}) rather than migrated from scratch each time, since {@code TRUNCATE}
 * is far cheaper than a repeated {@code Flyway.clean()}/{@code migrate()} cycle and the migration
 * only needs to run once per container in {@link #migrate()}.
 */
@Testcontainers
class PostgresObjectStoreContractIT extends ObjectStoreContract {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource =
            new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PostgresMigrations.migrate(dataSource);
    }

    @AfterEach
    void truncateTables() {
        JdbcClient.create(dataSource)
            .sql("TRUNCATE TABLE sq_object, sq_ontology_document, sq_outbox")
            .update();
    }

    @Override
    protected ObjectStorePort freshStore() {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        return new PostgresObjectStore(JdbcClient.create(dataSource), transactionManager);
    }

    @Override
    protected List<UUID> persistedOutboxIds(Scope scope) {
        return JdbcClient.create(dataSource)
            .sql("SELECT id FROM sq_outbox WHERE tenant_id = :tenantId")
            .param("tenantId", scope.tenantId().value())
            .query(UUID.class)
            .list();
    }
}
