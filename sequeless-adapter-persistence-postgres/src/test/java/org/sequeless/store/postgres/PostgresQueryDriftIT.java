package org.sequeless.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The host's drift check for the store's copied Query table (spec 18, G1).
 *
 * <p>The store's integration tests have no Query set. They create {@code sq_searchable_property} with a copy of the Query
 * set's V1 DDL, at the hook between Command versions 6 and 7. This test runs the host's composed migrate instead, which puts
 * the real Query set, from the released query-postgres-jsonb-adapter, at that same hook. It then checks that the real table has
 * the shape the copy gives it: the columns {@code tenant_id} and {@code property_iri}, both {@code text NOT NULL}, with the
 * primary key on both. If the Query set changes that table and the copy is not updated, this test fails.
 *
 * <p>The order is fixed by the Query set itself. Its V2 creates an index on {@code sq_object}, which Command V1 creates, so the
 * Query set cannot run first on an empty database. Command V6 reads {@code sq_searchable_property}, which the Query set creates.
 */
@Testcontainers
class PostgresQueryDriftIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static DataSource dataSource;

    @BeforeAll
    static void createDataSource() {
        dataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @BeforeEach
    void emptyDatabase() {
        JdbcClient.create(dataSource).sql("DROP SCHEMA public CASCADE").update();
        JdbcClient.create(dataSource).sql("CREATE SCHEMA public").update();
    }

    @Test
    void theRealQuerySetAtTheHookGivesTheSearchableTableTheCopiedShape() {
        ComposedMigrations.migrate(dataSource);

        List<String> commandStates = Arrays.stream(
                        PostgresMigrations.commandFlyway(dataSource).info().all())
                .map(PostgresQueryDriftIT::describe)
                .toList();
        assertThat(commandStates).contains("12 SUCCESS");
        assertThat(commandStates).noneMatch(state -> state.endsWith(MigrationState.FAILED.name()));

        assertThat(searchablePropertyColumns())
                .containsExactly("tenant_id text NOT NULL", "property_iri text NOT NULL");
        assertThat(searchablePropertyPrimaryKey()).isEqualTo("tenant_id,property_iri");
    }

    private static String describe(MigrationInfo info) {
        return info.getVersion() + " " + info.getState();
    }

    private static List<String> searchablePropertyColumns() {
        return JdbcClient.create(dataSource)
                .sql(
                        "SELECT column_name || ' ' || data_type || CASE WHEN is_nullable = 'NO' THEN ' NOT NULL' ELSE '' END "
                                + "FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'sq_searchable_property' "
                                + "ORDER BY ordinal_position")
                .query(String.class)
                .list();
    }

    private static String searchablePropertyPrimaryKey() {
        return JdbcClient.create(dataSource)
                .sql("SELECT string_agg(a.attname, ',' ORDER BY array_position(i.indkey::int2[], a.attnum)) "
                        + "FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                        + "WHERE i.indrelid = 'sq_searchable_property'::regclass AND i.indisprimary")
                .query(String.class)
                .single();
    }
}
