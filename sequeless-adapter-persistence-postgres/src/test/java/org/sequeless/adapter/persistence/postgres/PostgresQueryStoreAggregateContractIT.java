package org.sequeless.adapter.persistence.postgres;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.sequeless.testkit.Fixtures;
import org.sequeless.testkit.query.AggregateContract;
import org.sequeless.testkit.query.QueryFixtures;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresQueryStore#aggregate} satisfies {@link AggregateContract} — the same
 * behavioural contract {@code QueryPort#aggregate}'s interface-level javadoc documents — against a
 * real PostgreSQL 18 instance started by Testcontainers, pairing it with a {@link
 * PostgresObjectStore} that seeds fixture data into the same tables {@link PostgresQueryStore}
 * reads from.
 *
 * <p>Follows {@link PostgresQueryStoreContractIT}'s exact pattern: one shared container, migrated
 * once in {@link #migrate()}, with {@code sq_object}/{@code sq_ontology_document}/{@code sq_outbox}
 * truncated after every test. {@link #freshEnvironment()} also calls {@code
 * ensureIndexes(Fixtures.defaultScope(), QueryFixtures.snapshot())} before returning, mirroring
 * the order every real caller uses (ontology activation always runs {@code ensureIndexes} before
 * any query), even though {@link PostgresQueryStore#aggregate} does not itself depend on the index
 * or searchable-property registries the way free-text {@code query} does.
 */
@Testcontainers
class PostgresQueryStoreAggregateContractIT extends AggregateContract {

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
    protected Environment freshEnvironment() {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        PostgresObjectStore store = new PostgresObjectStore(jdbcClient, transactionManager);
        PostgresQueryStore queryPort = new PostgresQueryStore(jdbcClient, transactionManager);
        queryPort.ensureIndexes(Fixtures.defaultScope(), QueryFixtures.snapshot());
        return new Environment(store, queryPort);
    }
}
