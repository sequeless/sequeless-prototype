package org.sequeless.adapter.persistence.postgres;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.sequeless.testkit.query.AggregateContract;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
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
 * <p>Follows the pattern of the released query contract ITs: one shared container, migrated once in
 * {@link #migrate()}, with {@code sq_object}/{@code sq_ontology_document}/{@code sq_outbox} truncated after every
 * test. {@link PostgresQueryStore#aggregate} does not depend on the index or searchable-property registries, so
 * {@link #freshEnvironment()} does not prepare indexes (spec 17 P4 removed {@code prepare} from that class).
 */
@Testcontainers
class PostgresQueryStoreAggregateContractIT extends AggregateContract {

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
        PostgresObjectStore store = new PostgresObjectStore(jdbcClient, transactionManager);
        PostgresQueryStore queryPort = new PostgresQueryStore(jdbcClient, transactionManager);
        return new Environment(store, queryPort);
    }
}
