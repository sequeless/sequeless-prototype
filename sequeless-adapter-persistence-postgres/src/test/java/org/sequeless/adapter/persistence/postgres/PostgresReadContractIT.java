package org.sequeless.adapter.persistence.postgres;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.sequeless.testkit.query.ReadContract;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresQueryStore}'s {@code ReadPort} implementation satisfies {@link ReadContract}
 * against a real PostgreSQL 18 instance started by Testcontainers, seeding fixtures through a
 * {@link PostgresObjectStore} over the same tables.
 *
 * <p>Unlike {@link PostgresQueryStoreContractIT}, this class does not prepare indexes: a read by id
 * never consults the index registries.
 */
@Testcontainers
class PostgresReadContractIT extends ReadContract {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = new SimpleDriverDataSource(
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
    protected Environment freshEnvironment() {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        return new Environment(
                new PostgresObjectStore(jdbcClient, transactionManager),
                new PostgresQueryStore(jdbcClient, transactionManager));
    }
}
