package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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
 * Settles the Command/Query migration split of spec 13, P7 (spec 13 lines 121 and 128), against a
 * real PostgreSQL 18 started by Testcontainers. Each test empties the {@code public} schema first, so
 * it starts with no migration history.
 *
 * <p>The three starting states are: a fresh database; a database that ran the cut-over (the frozen
 * Command V1 to V4 set, with V2 recorded, copied from commit {@code 3328993}); and a database that ran
 * P2a's set (the frozen Command V1 to V5 set, copied from commit {@code fbb82c5}). The two frozen cases
 * migrate to the live set as it now stands. The frozen post-P7 set is the set as it stood at P7; later
 * migrations, starting with V7, may follow it, so the live set can be ahead of the frozen post-P7 set.
 * The pair from after P7 to after P2 (case f) starts at the frozen post-P7 set, migrates to the frozen
 * post-P2 set, and then to the live set. The pair from after P2 to after P3 (case g) starts at the frozen
 * post-P2 set, migrates to the frozen post-P3 set, and then to the live set. The pair from after P3 to after
 * P4 (case h) starts at the frozen post-P3 set, migrates to the frozen post-P4 set, and then to the live set.
 */
@Testcontainers
class PostgresMigrationsSettlingIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final String FROZEN_3328993 = "classpath:frozen-migrations/3328993";
    private static final String FROZEN_FBB82C5 = "classpath:frozen-migrations/fbb82c5";
    private static final String FROZEN_POST_P7 = "classpath:frozen-migrations/post-p7/command";
    private static final String FROZEN_POST_P2 = "classpath:frozen-migrations/post-p2/command";
    private static final String FROZEN_POST_P3 = "classpath:frozen-migrations/post-p3/command";
    private static final String FROZEN_POST_P4 = "classpath:frozen-migrations/post-p4/command";
    private static final String TENANT = "acme";
    private static final String TITLE_IRI = "https://sequeless.test/settling#title";

    /** The Command history of the final set, with no entry for V2, which the final set no longer has. */
    private static final List<String> FINAL_COMMAND_HISTORY = List.of(
            "1 SUCCESS",
            "3 SUCCESS",
            "4 SUCCESS",
            "5 SUCCESS",
            "6 SUCCESS",
            "7 SUCCESS",
            "8 SUCCESS",
            "9 SUCCESS",
            "10 SUCCESS",
            "11 SUCCESS",
            "12 SUCCESS");

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

    /**
     * Settling case (f): the pair from after P7 to after P2. A database at the frozen post-P7 set (V1 and V3
     * to V6, no V2) migrates to the frozen post-P2 set (V1 and V3 to V8), and then to the live set.
     */
    @Test
    void frozenPostP7LayoutMigratesThroughThePostP2Set() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(FROZEN_POST_P7)
                .load()
                .migrate();
        // A database at the post-P7 set has also run the Query set, which creates sq_searchable_property;
        // V6's trigger reads that table on every object write.
        PostgresMigrations.queryFlyway(dataSource).migrate();
        insertObject(UUID.randomUUID(), "first");
        insertObject(UUID.randomUUID(), "second");
        insertSearchableRegistration();
        Flyway.configure()
                .dataSource(dataSource)
                .locations(FROZEN_POST_P2)
                .load()
                .migrate();

        PostgresMigrations.migrate(dataSource);

        assertThat(versionsAndStates(PostgresMigrations.commandFlyway(dataSource)))
                .containsExactlyElementsOf(FINAL_COMMAND_HISTORY);
        assertSearchSchemaPresent();
        assertQueryHistory();
        assertRowCounts(2, 1);
    }

    /**
     * Settling case (g): the pair from after P2 to after P3. A database at the frozen post-P2 set (V1 and V3
     * to V8, no V2) and the Query set migrates to the frozen post-P3 set (V1 and V3 to V9), and then to the
     * live set.
     */
    @Test
    void frozenPostP2LayoutMigratesThroughThePostP3Set() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(FROZEN_POST_P2)
                .load()
                .migrate();
        // The post-P2 set has the search trigger, which reads sq_searchable_property on every object write.
        PostgresMigrations.queryFlyway(dataSource).migrate();
        insertObject(UUID.randomUUID(), "first");
        insertObject(UUID.randomUUID(), "second");
        insertSearchableRegistration();
        Flyway.configure()
                .dataSource(dataSource)
                .locations(FROZEN_POST_P3)
                .load()
                .migrate();

        PostgresMigrations.migrate(dataSource);

        assertThat(versionsAndStates(PostgresMigrations.commandFlyway(dataSource)))
                .containsExactlyElementsOf(FINAL_COMMAND_HISTORY);
        assertSearchSchemaPresent();
        assertQueryHistory();
        assertRowCounts(2, 1);
    }

    /**
     * Settling case (h): the pair from after P3 to after P4. A database at the frozen post-P3 set (V1 and V3
     * to V9, no V10 or V11) and the Query set migrates to the frozen post-P4 set (V1 and V3 to V11), and then
     * to the live set.
     */
    @Test
    void frozenPostP3LayoutMigratesThroughThePostP4Set() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(FROZEN_POST_P3)
                .load()
                .migrate();
        // The post-P3 set has the search trigger, which reads sq_searchable_property on every object write.
        PostgresMigrations.queryFlyway(dataSource).migrate();
        insertObject(UUID.randomUUID(), "first");
        insertObject(UUID.randomUUID(), "second");
        insertSearchableRegistration();
        // The post-P4 set ends with V10, CREATE INDEX CONCURRENTLY. With the transactional lock on, Flyway holds
        // a transaction open for the whole run and V10 waits on it forever, so the lock is off here, as in
        // PostgresMigrations.
        Flyway.configure()
                .dataSource(dataSource)
                .locations(FROZEN_POST_P4)
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .load()
                .migrate();

        PostgresMigrations.migrate(dataSource);

        assertThat(versionsAndStates(PostgresMigrations.commandFlyway(dataSource)))
                .containsExactlyElementsOf(FINAL_COMMAND_HISTORY);
        assertSearchSchemaPresent();
        assertQueryHistory();
        assertRowCounts(2, 1);
    }

    /** Settling case (a): a fresh database migrates to the final set. */
    @Test
    void freshDatabaseMigratesToTheFinalSet() {
        PostgresMigrations.migrate(dataSource);

        assertThat(versionsAndStates(PostgresMigrations.commandFlyway(dataSource)))
                .containsExactlyElementsOf(FINAL_COMMAND_HISTORY);
        assertSearchSchemaPresent();
        assertQueryHistory();

        insertObject(UUID.randomUUID(), "first");
        insertObject(UUID.randomUUID(), "second");
        insertSearchableRegistration();
        assertRowCounts(2, 1);
    }

    /** Settling case (b): a database that ran the cut-over, V1 to V4 with V2 recorded. */
    @Test
    void frozen3328993LayoutMigratesToTheLiveSet() {
        // target("4") on a set that ends at V4 applies nothing extra. It is harmless, and it stays.
        Flyway.configure()
                .dataSource(dataSource)
                .locations(FROZEN_3328993)
                .target("4")
                .load()
                .migrate();
        insertObject(UUID.randomUUID(), "first");
        insertObject(UUID.randomUUID(), "second");
        insertSearchableRegistration();

        PostgresMigrations.migrate(dataSource);

        assertOnlyVersionTwoIsMissing();
        assertThat(versionsAndStates(PostgresMigrations.commandFlyway(dataSource)))
                .containsExactly(
                        "1 SUCCESS",
                        "2 MISSING_SUCCESS",
                        "3 SUCCESS",
                        "4 SUCCESS",
                        "5 SUCCESS",
                        "6 SUCCESS",
                        "7 SUCCESS",
                        "8 SUCCESS",
                        "9 SUCCESS",
                        "10 SUCCESS",
                        "11 SUCCESS",
                        "12 SUCCESS");
        assertSearchSchemaPresent();
        assertQueryHistory();
        assertRowCounts(2, 1);
    }

    /** Settling case (c): a database that ran P2a's set, V1 to V5. */
    @Test
    void frozenFbb82c5LayoutMigratesToTheLiveSet() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(FROZEN_FBB82C5)
                .load()
                .migrate();
        insertObject(UUID.randomUUID(), "first");
        insertObject(UUID.randomUUID(), "second");
        insertSearchableRegistration();

        PostgresMigrations.migrate(dataSource);

        assertOnlyVersionTwoIsMissing();
        assertThat(versionsAndStates(PostgresMigrations.commandFlyway(dataSource)))
                .containsExactly(
                        "1 SUCCESS",
                        "2 MISSING_SUCCESS",
                        "3 SUCCESS",
                        "4 SUCCESS",
                        "5 SUCCESS",
                        "6 SUCCESS",
                        "7 SUCCESS",
                        "8 SUCCESS",
                        "9 SUCCESS",
                        "10 SUCCESS",
                        "11 SUCCESS",
                        "12 SUCCESS");
        assertSearchSchemaPresent();
        assertQueryHistory();
        assertRowCounts(2, 1);
    }

    /** Settling case (e): migrating an up-to-date database again changes neither history. */
    @Test
    void migratingAnUpToDateDatabaseAgainChangesNeitherHistory() {
        PostgresMigrations.migrate(dataSource);
        List<String> commandBefore = describedHistory(PostgresMigrations.commandFlyway(dataSource));
        List<String> queryBefore = describedHistory(PostgresMigrations.queryFlyway(dataSource));

        PostgresMigrations.migrate(dataSource);

        assertThat(describedHistory(PostgresMigrations.commandFlyway(dataSource)))
                .isEqualTo(commandBefore);
        assertThat(describedHistory(PostgresMigrations.queryFlyway(dataSource))).isEqualTo(queryBefore);
    }

    /** Settling case (d): the Query history is the baseline and its two migrations, and nothing else. */
    private static void assertQueryHistory() {
        assertThat(versionsAndStates(PostgresMigrations.queryFlyway(dataSource)))
                .containsExactly("0 BASELINE", "1 SUCCESS", "2 SUCCESS");
    }

    /** Settling cases (b) and (c): the only missing Command entry is V2, recorded as MISSING_SUCCESS. */
    private static void assertOnlyVersionTwoIsMissing() {
        List<String> missing = Arrays.stream(
                        PostgresMigrations.commandFlyway(dataSource).info().all())
                .filter(info -> info.getState() == MigrationState.MISSING_SUCCESS
                        || info.getState() == MigrationState.MISSING_FAILED)
                .map(PostgresMigrationsSettlingIT::describe)
                .toList();
        assertThat(missing).containsExactly("2 MISSING_SUCCESS");
    }

    private static void assertSearchSchemaPresent() {
        Long triggers = JdbcClient.create(dataSource)
                .sql("SELECT COUNT(*) FROM pg_trigger WHERE tgrelid = 'sq_object'::regclass "
                        + "AND tgname = 'sq_object_search_vector_trigger' AND NOT tgisinternal")
                .query(Long.class)
                .single();
        assertThat(triggers).as("search trigger on sq_object").isEqualTo(1L);

        Long ginIndexes = JdbcClient.create(dataSource)
                .sql(
                        "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sq_object' "
                                + "AND indexname = 'sq_object_search_vector_gin_idx' AND indexdef LIKE '%USING gin (search_vector)%'")
                .query(Long.class)
                .single();
        assertThat(ginIndexes).as("GIN index on search_vector").isEqualTo(1L);

        Long registryTables = JdbcClient.create(dataSource)
                .sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('sq_index_registry', 'sq_searchable_property')")
                .query(Long.class)
                .single();
        assertThat(registryTables).as("registry tables").isEqualTo(2L);
    }

    private static void assertRowCounts(long objects, long registrations) {
        Long objectRows = JdbcClient.create(dataSource)
                .sql("SELECT COUNT(*) FROM sq_object")
                .query(Long.class)
                .single();
        Long registrationRows = JdbcClient.create(dataSource)
                .sql("SELECT COUNT(*) FROM sq_searchable_property")
                .query(Long.class)
                .single();
        assertThat(objectRows).as("sq_object rows").isEqualTo(objects);
        assertThat(registrationRows).as("sq_searchable_property rows").isEqualTo(registrations);
    }

    private static List<String> versionsAndStates(Flyway flyway) {
        return Arrays.stream(flyway.info().all())
                .map(info -> info.getVersion() + " " + info.getState())
                .toList();
    }

    private static List<String> describedHistory(Flyway flyway) {
        return Arrays.stream(flyway.info().all())
                .map(info -> describe(info) + " rank " + info.getInstalledRank())
                .toList();
    }

    private static String describe(MigrationInfo info) {
        return info.getVersion() + " " + info.getState();
    }

    private static void insertObject(UUID id, String title) {
        JdbcClient.create(dataSource)
                .sql("INSERT INTO sq_object (id, tenant_id, type_iri, version, props, created_at, created_by, "
                        + "updated_at, updated_by) VALUES (:id, :tenantId, :typeIri, 1, CAST(:props AS jsonb), "
                        + "now(), 'settling', now(), 'settling')")
                .param("id", id)
                .param("tenantId", TENANT)
                .param("typeIri", "https://sequeless.test/settling#Document")
                .param("props", "{\"" + TITLE_IRI + "\": {\"text\": \"" + title + "\"}}")
                .update();
    }

    private static void insertSearchableRegistration() {
        JdbcClient.create(dataSource)
                .sql("INSERT INTO sq_searchable_property (tenant_id, property_iri) VALUES (:tenantId, :propertyIri)")
                .param("tenantId", TENANT)
                .param("propertyIri", TITLE_IRI)
                .update();
    }
}
