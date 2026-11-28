package org.sequeless.store.postgres;

import javax.sql.DataSource;

/**
 * The host's migration of the Postgres schema: the Command set and the Query set, in the order the search trigger
 * requires (spec 18, U11).
 *
 * <p>Command versions 1 to 6 run first. The Query set then runs through the released query-postgres-jsonb-adapter's own
 * migrate, on its own history table {@code flyway_schema_history_query}. Command version 7 onward runs last. The store's
 * {@link PostgresMigrations#migrate(DataSource, java.util.function.Consumer)} provides the step between versions 6 and 7.
 *
 * <p>The store's adapter carries the Command set alone, so the host is where the two sets are composed.
 */
public final class ComposedMigrations {

    private ComposedMigrations() {}

    /**
     * Migrates the Command set and the Query set, in the order the search trigger requires.
     *
     * @param dataSource the data source to migrate; must not be {@code null}
     */
    public static void migrate(DataSource dataSource) {
        PostgresMigrations.migrate(dataSource, org.sequeless.query.postgres.PostgresMigrations::migrate);
    }
}
