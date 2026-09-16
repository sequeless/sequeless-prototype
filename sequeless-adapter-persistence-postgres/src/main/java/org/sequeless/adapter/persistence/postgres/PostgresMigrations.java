package org.sequeless.adapter.persistence.postgres;

import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * Runs this module's own Flyway migration against a {@link DataSource}, independently of Spring
 * Boot's Flyway autoconfiguration. This is the one place that names the migration location
 * ({@code classpath:db/migration/sequeless/postgres}), shared by the auto-configuration wired in
 * a later task and by this module's own {@code PostgresObjectStoreContractIT}, so the location
 * string is never duplicated between the two.
 */
public final class PostgresMigrations {

    private static final String LOCATION = "classpath:db/migration/sequeless/postgres";

    private PostgresMigrations() {}

    /**
     * @param dataSource the data source to migrate; must not be {@code null}
     */
    public static void migrate(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Flyway.configure().dataSource(dataSource).locations(LOCATION).load().migrate();
    }
}
