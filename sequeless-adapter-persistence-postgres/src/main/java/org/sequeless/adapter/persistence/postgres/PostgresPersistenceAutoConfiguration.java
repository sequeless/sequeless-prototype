package org.sequeless.adapter.persistence.postgres;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.sequeless.spi.object.ObjectStorePort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wires {@link PostgresObjectStore} into the Spring context when {@code
 * sequeless.persistence.adapter} is set to {@code postgres}, mirroring {@code
 * sequeless-adapter-ontology-jena}'s {@code JenaOntologyAutoConfiguration} and {@code
 * sequeless-adapter-authz-permitall}'s {@code PermitAllAuthorizationAutoConfiguration} exactly.
 *
 * <p>Deliberately has no {@code matchIfMissing}: when the property is unset, the condition must
 * evaluate to {@code false} and no {@link ObjectStorePort} bean must exist here at all, so the
 * application's port registry can detect the absent-property case itself and report it, rather than
 * this auto-configuration silently conjuring a default.
 *
 * <p>Deliberately has no {@code @ConditionalOnMissingBean}: if another adapter's auto-configuration
 * also produces an {@link ObjectStorePort} bean under the same property value, both beans must be
 * allowed to exist so the application's port registry — not Spring — is the thing that detects and
 * rejects the ambiguity.
 *
 * <h2>Migrations run adapter-side, never via Spring Boot's own Flyway auto-configuration</h2>
 *
 * <p>This module deliberately never depends on {@code spring-boot-starter-flyway}, and {@link
 * #objectStorePort(DataSource)} never lets Spring Boot's {@code FlywayAutoConfiguration} run against
 * the {@link DataSource} produced here. Boot's Flyway auto-configuration activates automatically for
 * <em>any</em> {@code DataSource} bean once the {@code Flyway} class and a {@code DataSource} bean
 * are both on the classpath, against one global {@code classpath:db/migration} default location. If
 * the application that assembles this adapter ever adds an unrelated schema of its own under that
 * default location, it must never become entangled with this module's own {@code
 * classpath:db/migration/sequeless/postgres} migrations. Instead, {@link PostgresMigrations#migrate}
 * runs this adapter's own {@code Flyway.configure()} call, scoped to its own location string, lazily
 * against {@link #sequelessPersistenceDataSource}'s first real connection (see {@link
 * PostgresMigratingDataSource}) rather than eagerly when either bean method here runs.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.persistence.adapter", havingValue = "postgres")
@EnableConfigurationProperties(PostgresPersistenceProperties.class)
public class PostgresPersistenceAutoConfiguration {

    /**
     * Builds this adapter's own {@link DataSource} from {@link
     * PostgresPersistenceProperties#getUrl()}/{@link PostgresPersistenceProperties#getUsername()}/
     * {@link PostgresPersistenceProperties#getPassword()}, rather than requiring a caller to have
     * already defined a {@code DataSource} bean.
     *
     * <p>Uses {@link HikariDataSource}'s no-arg constructor plus setters, not {@code new
     * HikariDataSource(HikariConfig)}: the latter starts the connection pool synchronously inside
     * the constructor, which would make this bean method fail fast against a database that is not
     * actually reachable yet. The no-arg-plus-setters form defers pool startup — and, wrapped in
     * {@link PostgresMigratingDataSource}, this adapter's Flyway migration — to the first real
     * {@link DataSource#getConnection()} call, which is what keeps this auto-configuration
     * constructible under {@code ApplicationContextRunner} without a real PostgreSQL listening.
     *
     * @param properties the bound {@code sequeless.persistence.*} configuration
     * @return a {@link DataSource} that migrates itself on first use
     */
    @Bean
    public DataSource sequelessPersistenceDataSource(PostgresPersistenceProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        return new PostgresMigratingDataSource(dataSource);
    }

    /**
     * @param sequelessPersistenceDataSource this adapter's own {@link DataSource}, built by {@link
     *     #sequelessPersistenceDataSource(PostgresPersistenceProperties)}
     * @return a new {@link PostgresObjectStore} over that {@link DataSource}
     */
    @Bean
    public ObjectStorePort objectStorePort(DataSource sequelessPersistenceDataSource) {
        JdbcClient jdbcClient = JdbcClient.create(sequelessPersistenceDataSource);
        PlatformTransactionManager transactionManager =
            new DataSourceTransactionManager(sequelessPersistenceDataSource);
        return new PostgresObjectStore(jdbcClient, transactionManager);
    }
}
