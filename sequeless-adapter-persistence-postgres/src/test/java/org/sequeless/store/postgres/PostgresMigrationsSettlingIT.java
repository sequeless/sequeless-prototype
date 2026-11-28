package org.sequeless.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
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
 * The host's settling cases for the composed migration (spec 13, P7; spec 18, U11 and G4), run by {@link ComposedMigrations}
 * against a real PostgreSQL 18 started by Testcontainers. Each test empties the {@code public} schema first, so it starts with
 * no migration history.
 *
 * <p>The host keeps the cases that need the Query set: the two frozen layouts that the cut-over produced (the frozen Command
 * V1 to V4 set, copied from commit {@code 3328993}, and P2a's set, copied from commit {@code fbb82c5}), the Query history, and the
 * bare-database cases (a default write works, a command-only write fails, and the default Query history matches the released
 * Query migrate). The command-only pairs (after P7, P2 and P3) are the store's, in the store repository, because their frozen
 * inputs were removed from this repository in the cut-over. The pair cases that started from the frozen post-P7, post-P2 and
 * post-P3 Command sets are therefore not run here.
 */
@Testcontainers
class PostgresMigrationsSettlingIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final String FROZEN_3328993 = "classpath:frozen-migrations/3328993";
    private static final String FROZEN_FBB82C5 = "classpath:frozen-migrations/fbb82c5";
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

    /** A fresh database migrates to the final set, and the Query history is the baseline and its two migrations. */
    @Test
    void freshDatabaseMigratesToTheFinalSet() {
        ComposedMigrations.migrate(dataSource);

        assertThat(versionsAndStates(PostgresMigrations.commandFlyway(dataSource)))
                .containsExactlyElementsOf(FINAL_COMMAND_HISTORY);
        assertSearchSchemaPresent();
        assertQueryHistory();

        insertObject(UUID.randomUUID(), "first");
        insertObject(UUID.randomUUID(), "second");
        insertSearchableRegistration();
        assertRowCounts(2, 1);
    }

    /** A database that ran the cut-over (V1 to V4, with V2 recorded) migrates to the live set. */
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

        ComposedMigrations.migrate(dataSource);

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

    /** A database that ran P2a's set (V1 to V5) migrates to the live set. */
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

        ComposedMigrations.migrate(dataSource);

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

    /** Migrating an up-to-date database again changes neither history. */
    @Test
    void migratingAnUpToDateDatabaseAgainChangesNeitherHistory() {
        ComposedMigrations.migrate(dataSource);
        List<String> commandBefore = describedHistory(PostgresMigrations.commandFlyway(dataSource));
        List<String> queryBefore = describedHistory(queryFlyway(dataSource));

        ComposedMigrations.migrate(dataSource);

        assertThat(describedHistory(PostgresMigrations.commandFlyway(dataSource)))
                .isEqualTo(commandBefore);
        assertThat(describedHistory(queryFlyway(dataSource))).isEqualTo(queryBefore);
    }

    /**
     * Bare database, composed migrate: the Query set is applied before any Command write, so a row write right after
     * migrate succeeds and the search trigger fires.
     */
    @Test
    void composedMigrateWritesAnObjectOnABareDatabase() {
        ComposedMigrations.migrate(dataSource);

        insertObject(UUID.randomUUID(), "bare");

        assertRowCounts(1, 0);
        assertSearchSchemaPresent();
        assertQueryHistory();
    }

    /**
     * Bare database, command-only migrate (a no-op hook): the Query set is not applied, so the search trigger reads a
     * table that does not exist and an object write fails. This is the negative check for the composed migrate.
     */
    @Test
    void commandOnlyMigrateFailsAnObjectWriteBecauseNoQuerySetExists() {
        PostgresMigrations.migrate(dataSource, ignored -> {});

        // Spring wraps the driver error, so its message is the SQL text. The missing relation is in the cause.
        assertThatThrownBy(() -> insertObject(UUID.randomUUID(), "bare"))
                .hasStackTraceContaining("relation \"sq_searchable_property\" does not exist");
    }

    /**
     * The composed migrate's Query history is the store's default migrate's Query history: the same versions, descriptions,
     * states and checksums. The composed migrate runs the released query-postgres-jsonb-adapter's migrate. The store's default
     * carries its own copy of that configuration (location, history table and baseline), so this checks the two copies agree.
     */
    @Test
    void composedQuerySetHistoryMatchesTheStoreDefaultMigrate() {
        ComposedMigrations.migrate(dataSource);
        List<String> composed = queryHistoryFingerprint();

        emptyDatabase();
        PostgresMigrations.migrate(dataSource);

        assertThat(queryHistoryFingerprint()).isEqualTo(composed);
    }

    private static List<String> queryHistoryFingerprint() {
        return Arrays.stream(queryFlyway(dataSource).info().all())
                .map(info -> info.getVersion() + " " + info.getDescription() + " " + info.getState() + " "
                        + info.getChecksum())
                .toList();
    }

    /** The Query history is the baseline and its two migrations, and nothing else. */
    private static void assertQueryHistory() {
        assertThat(versionsAndStates(queryFlyway(dataSource))).containsExactly("0 BASELINE", "1 SUCCESS", "2 SUCCESS");
    }

    /**
     * The Query instance for history assertions, configured as the released adapter configures it. The test builds its
     * own copy, so it can read the history without the migration's own accessor.
     */
    private static Flyway queryFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/sequeless/postgres-query")
                .table("flyway_schema_history_query")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    /** The only missing Command entry is V2, recorded as MISSING_SUCCESS. */
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
