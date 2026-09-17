package org.sequeless.adapter.persistence.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Wraps a {@link DataSource} so {@link PostgresMigrations#migrate(DataSource)} runs exactly once,
 * lazily, the first time a connection is actually requested — not when this wrapper (or the {@link
 * org.sequeless.spi.object.ObjectStorePort} bean built on top of it) is constructed.
 *
 * <p>This is deliberate, not an oversight: {@link PostgresPersistenceAutoConfiguration}'s beans must
 * be constructible, by {@code ApplicationContextRunner}-driven tests with no real PostgreSQL
 * listening, without failing. Deferring the migration (and therefore the first real connection
 * attempt) until something actually calls {@link #getConnection()} keeps bean creation itself
 * side-effect-free, so a test can assert the beans exist without needing Testcontainers or Docker.
 */
final class PostgresMigratingDataSource extends DelegatingDataSource {

    private final AtomicBoolean migrated = new AtomicBoolean(false);

    PostgresMigratingDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        migrateIfNeeded();
        return super.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        migrateIfNeeded();
        return super.getConnection(username, password);
    }

    private void migrateIfNeeded() {
        if (migrated.compareAndSet(false, true)) {
            PostgresMigrations.migrate(getTargetDataSource());
        }
    }
}
