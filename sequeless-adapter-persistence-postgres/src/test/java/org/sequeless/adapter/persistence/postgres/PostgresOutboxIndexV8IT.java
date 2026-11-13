package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves migration V8 (spec 13, step 4; DR-23) on the latest schema, against a real PostgreSQL 18
 * started by Testcontainers. The claim index {@code sq_outbox_unprocessed_dispatchable_idx} keeps its
 * {@code processed_at IS NULL} predicate and adds {@code dead_lettered_at IS NULL}, so a dead-lettered
 * row is not in the index that every claim walks.
 *
 * <p>Each test empties the {@code public} schema first, so it starts with no migration history, and
 * then migrates to the latest schema with {@link PostgresMigrations#migrate}.
 */
@Testcontainers
class PostgresOutboxIndexV8IT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static DataSource dataSource;

    @BeforeAll
    static void createDataSource() {
        dataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @BeforeEach
    void migrateEmptyDatabase() {
        JdbcClient.create(dataSource).sql("DROP SCHEMA public CASCADE").update();
        JdbcClient.create(dataSource).sql("CREATE SCHEMA public").update();
        PostgresMigrations.migrate(dataSource);
    }

    @Test
    void claimIndexPredicateExcludesDeadLetteredRows() {
        String indexdef = JdbcClient.create(dataSource)
                .sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'sq_outbox_unprocessed_dispatchable_idx'")
                .query(String.class)
                .single();

        assertThat(indexdef).contains("processed_at IS NULL").contains("dead_lettered_at IS NULL");
    }
}
