package org.sequeless.app.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers Postgres wiring for every {@code @SpringBootTest} in this module.
 *
 * <p>{@code sequeless.persistence.adapter=postgres} is baked into {@code application.yaml}, so
 * every context this module boots — including tests that have nothing to do with objects or
 * validation — now constructs a real {@code DataSource} via {@code
 * PostgresPersistenceAutoConfiguration}, and the Jena {@code OntologyPort} adapter (when a store is
 * present) eagerly loads its active document from that store during bean construction (see T7/T11's
 * investigation notes). There is no way to opt a context out of this while keeping the
 * application's real, shipped configuration under test — so every {@code @SpringBootTest} class in
 * this module extends this class instead of configuring its own container.
 *
 * <p>One container per JVM, started once (Testcontainers' own singleton-container pattern, no
 * {@code static} field re-declared per subclass): {@code @Container} on a {@code static} field plus
 * {@code @Testcontainers} starts it before the first test in this class and Testcontainers' Ryuk
 * resource reaper stops it after the whole JVM exits — this deliberately never calls {@code
 * container.stop()} itself, so the same container is reused across every subclass in the same JVM
 * fork rather than restarted per test class.
 *
 * <p>{@code @DynamicPropertySource} — not {@code @ServiceConnection} — because {@code
 * PostgresPersistenceAutoConfiguration} builds its own {@code DataSource} from {@code
 * sequeless.persistence.*}, a namespace {@code @ServiceConnection} does not know about (it only
 * targets {@code spring.datasource.*}/Boot's own autoconfiguration); see plan.md's T11 investigation
 * notes.
 *
 * <p>Manually-driven {@code SpringApplicationBuilder} tests (which do not go through the Spring
 * test context framework, so {@code @DynamicPropertySource} does not apply to them) must inject
 * these same three {@code sequeless.persistence.*} properties themselves, using {@link
 * #POSTGRES}'s {@code getJdbcUrl()}/{@code getUsername()}/{@code getPassword()} directly — see
 * {@code InconsistentOntologyStartupTest}/{@code PortBindingStartupFailureTest} for that pattern.
 */
@Testcontainers
public abstract class PostgresTestcontainersSupport {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("sequeless.persistence.url", POSTGRES::getJdbcUrl);
        registry.add("sequeless.persistence.username", POSTGRES::getUsername);
        registry.add("sequeless.persistence.password", POSTGRES::getPassword);
    }
}
