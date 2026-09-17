package org.sequeless.adapter.persistence.postgres;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code sequeless.persistence.*} configuration namespace this adapter reads at startup:
 * which adapter to activate ({@link #getAdapter()}, consulted only by {@link
 * PostgresPersistenceAutoConfiguration}'s {@code @ConditionalOnProperty}, not by this class itself),
 * and the JDBC connection details ({@link #getUrl()}, {@link #getUsername()}, {@link
 * #getPassword()}) used to build this module's own {@link javax.sql.DataSource}.
 *
 * <p>This module builds its own {@code DataSource} from these properties rather than requiring a
 * caller to have already defined a {@code javax.sql.DataSource} bean, so {@link
 * PostgresPersistenceAutoConfiguration} is testable on its own via {@code
 * org.springframework.boot.test.context.runner.ApplicationContextRunner}, without pulling in the
 * application module that eventually assembles adapters together. If the application's own {@code
 * spring.datasource.*} configuration ends up supplying an equivalent {@code DataSource} bean by the
 * time the application wires this adapter in, reconciling the two configuration namespaces is that
 * later task's job, not this module's.
 */
@ConfigurationProperties("sequeless.persistence")
public class PostgresPersistenceProperties {

    /**
     * Which {@code ObjectStorePort} adapter to activate; must equal {@code "postgres"} for {@link
     * PostgresPersistenceAutoConfiguration} to wire this adapter's beans in at all. Read only by
     * that auto-configuration's {@code @ConditionalOnProperty}, never by this class.
     */
    private String adapter;

    /**
     * The JDBC URL of the PostgreSQL database this adapter's {@link javax.sql.DataSource} connects
     * to.
     */
    private String url;

    /**
     * The username used to authenticate the {@link javax.sql.DataSource}'s connections.
     */
    private String username;

    /**
     * The password used to authenticate the {@link javax.sql.DataSource}'s connections.
     */
    private String password;

    /**
     * @return which {@code ObjectStorePort} adapter to activate
     */
    public String getAdapter() {
        return adapter;
    }

    /**
     * @param adapter which {@code ObjectStorePort} adapter to activate
     */
    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    /**
     * @return the JDBC URL of the PostgreSQL database this adapter connects to
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param url the JDBC URL of the PostgreSQL database this adapter connects to
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @return the username used to authenticate the {@link javax.sql.DataSource}'s connections
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username used to authenticate the {@link javax.sql.DataSource}'s
     *     connections
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the password used to authenticate the {@link javax.sql.DataSource}'s connections
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password used to authenticate the {@link javax.sql.DataSource}'s
     *     connections
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
