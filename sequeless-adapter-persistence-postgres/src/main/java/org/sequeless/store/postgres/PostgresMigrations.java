package org.sequeless.store.postgres;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/**
 * Runs this module's own Flyway migrations against a {@link DataSource}, independently of Spring
 * Boot's Flyway autoconfiguration. This is the one place that names the migration locations, shared
 * by the auto-configuration and by this module's own integration tests, so no location string is
 * duplicated.
 *
 * <p>There are two migration sets, each with its own history table:
 *
 * <ul>
 *   <li>the Command set, {@code classpath:db/migration/sequeless/postgres}, in Flyway's default
 *       history table: the objects, ontology documents, outbox, and the search column with its
 *       trigger;
 *   <li>the Query set, {@code classpath:db/migration/sequeless/postgres-query}, in {@code
 *       flyway_schema_history_query}: the index registries and the GIN index.
 * </ul>
 *
 * <p>{@link #migrate} applies the Command set up to version {@code 6}, then the Query set, then the
 * rest of the Command set. Command version 6 creates a trigger that reads {@code
 * sq_searchable_property}, which the Query set creates, so the Query set must exist before any later
 * Command migration that writes {@code sq_object}. {@link #migrate(DataSource, Consumer)} takes the
 * step between Command versions 6 and 7 as a hook; {@link #migrate} supplies the Query set as that
 * hook.
 */
public final class PostgresMigrations {

    /*
     * These three constants must match the released query-postgres-jsonb-adapter's migration set: its
     * location, its history table, and its baseline. They are shared by convention only, so a change in
     * that adapter must be mirrored here. The released adapter's migrate(DataSource) uses the same values.
     */
    private static final String COMMAND_LOCATION = "classpath:db/migration/sequeless/postgres";
    private static final String QUERY_LOCATION = "classpath:db/migration/sequeless/postgres-query";
    private static final String QUERY_HISTORY_TABLE = "flyway_schema_history_query";

    /** The last Command version applied before the Query set: it creates the search trigger. */
    private static final String COMMAND_VERSION_BEFORE_QUERY = "6";

    private PostgresMigrations() {}

    /**
     * Migrates the Command set and the Query set, in the order the search trigger requires.
     *
     * @param dataSource the data source to migrate; must not be {@code null}
     */
    public static void migrate(DataSource dataSource) {
        migrate(dataSource, PostgresMigrations::migrateQuerySet);
    }

    /**
     * Migrates the Command set with {@code afterCommandV6} run between Command versions 6 and 7. The hook
     * is the only place the Query set can be applied, so a no-op hook gives the Command set alone. A
     * caller that writes {@code sq_object} rows must run the Query set in the hook before it writes.
     *
     * @param dataSource the data source to migrate; must not be {@code null}
     * @param afterCommandV6 the step to run once Command version 6 is applied; must not be {@code null}
     */
    public static void migrate(DataSource dataSource, Consumer<DataSource> afterCommandV6) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Objects.requireNonNull(afterCommandV6, "afterCommandV6 must not be null");
        commandConfiguration(dataSource)
                .target(COMMAND_VERSION_BEFORE_QUERY)
                .load()
                .migrate();
        afterCommandV6.accept(dataSource);
        commandConfiguration(dataSource).load().migrate();
    }

    private static void migrateQuerySet(DataSource dataSource) {
        queryConfiguration(dataSource).load().migrate();
    }

    /**
     * Migrates the Command set up to and including {@code version} only, so a test can stop at an
     * earlier schema, write rows into it, and then run the later migrations. The Query set is not
     * touched.
     *
     * @param dataSource the data source to migrate; must not be {@code null}
     * @param version the last Command version to apply, for example {@code "4"}; must not be {@code
     *     null}
     */
    static void migrateTo(DataSource dataSource, String version) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Objects.requireNonNull(version, "version must not be null");
        commandConfiguration(dataSource).target(version).load().migrate();
    }

    /**
     * The Command instance, unbounded, for tests that read its history with {@code info()}.
     *
     * @param dataSource the data source; must not be {@code null}
     * @return a configured Flyway instance for the Command set
     */
    static Flyway commandFlyway(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        return commandConfiguration(dataSource).load();
    }

    /*
     * Validation ignores missing and future entries. A database that applied Command V2 before P7
     * keeps that row, which Flyway reports as MISSING_SUCCESS because V2 is no longer in this set.
     *
     * The transactional advisory lock is off. With it on, Flyway holds an open transaction for the whole
     * run, and CREATE INDEX CONCURRENTLY (Command V10) waits on that transaction and never finishes. The
     * lock is then a session-level advisory lock, which still serialises concurrent migrations.
     */
    private static FluentConfiguration commandConfiguration(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(COMMAND_LOCATION)
                .ignoreMigrationPatterns("*:missing", "*:future")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"));
    }

    private static FluentConfiguration queryConfiguration(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(QUERY_LOCATION)
                .table(QUERY_HISTORY_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion("0");
    }
}
