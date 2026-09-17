package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sequeless.app.port.PortBindingException;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Proves the Application phase's other end-to-end acceptance criterion: a misconfigured {@code
 * sequeless.authz.adapter} fails application startup with a message naming both the offending
 * property and the available adapters, rather than booting into a broken state.
 *
 * <p>Deliberately does <b>not</b> use {@code @SpringBootTest}: that annotation has no clean way to
 * assert on a startup failure — it just fails the test itself with a context-loading error rather
 * than handing the exception back for inspection. Driving {@link SpringApplicationBuilder}
 * directly, with no web server at all ({@link WebApplicationType#NONE}), lets the failure surface
 * as a plain exception this test can catch and walk.
 *
 * <p>The assertion below targets {@link PortBindingException#getMessage()}, found by walking the
 * {@code getCause()} chain — never the {@code ***APPLICATION FAILED TO START***} banner. That
 * banner is only ever printed to the log by Spring Boot's {@code LoggingFailureAnalysisReporter}
 * and is not attached to the thrown exception in any assertable form, so matching against it would
 * be brittle.
 *
 * <p>The override is passed as a command-line argument to {@code run(...)}, not via {@code
 * SpringApplicationBuilder.properties(...)}: {@code properties(...)} adds a {@code
 * defaultProperties} source, which is the <em>lowest</em>-precedence property source in the
 * environment, so it would be silently overridden by {@code application.yaml}'s own {@code
 * sequeless.authz.adapter=permit-all} default and the application would start successfully instead
 * of failing. Command-line arguments are the highest-precedence source, so they genuinely win.
 *
 * <p>Extends {@link PostgresTestcontainersSupport} and passes its container's connection details
 * as command-line arguments alongside the {@code sequeless.authz.adapter} override: {@code
 * sequeless.persistence.adapter=postgres} is baked into {@code application.yaml}, and the Jena
 * {@code OntologyPort} bean eagerly loads its active document from that store during bean
 * construction — in the singleton-creation pass that runs <em>before</em> {@code PortRegistry}'s own
 * checks. Without a reachable Postgres, that eager load would fail first and this test would never
 * reach the {@link PortBindingException} it means to assert on. {@code @DynamicPropertySource} does
 * not apply here — it only works with the Spring test context framework's own context caching, not
 * a manually-driven {@code SpringApplication.run}.
 */
class PortBindingStartupFailureTest extends PostgresTestcontainersSupport {

    @Test
    void unknownAdapterNameFailsFastNamingThePropertyAndAvailableAdapters() {
        assertThatThrownBy(
                () ->
                    new SpringApplicationBuilder(SequelessApplication.class)
                        .web(WebApplicationType.NONE)
                        .run(
                            "--sequeless.authz.adapter=missing",
                            "--sequeless.persistence.url=" + POSTGRES.getJdbcUrl(),
                            "--sequeless.persistence.username=" + POSTGRES.getUsername(),
                            "--sequeless.persistence.password=" + POSTGRES.getPassword()))
            .satisfies(
                thrown -> {
                    PortBindingException cause = findCause(thrown, PortBindingException.class);
                    assertThat(cause).isNotNull();
                    assertThat(cause.getMessage())
                        .contains("sequeless.authz.adapter")
                        .contains("permit-all");
                });
    }

    private static <T extends Throwable> T findCause(Throwable root, Class<T> type) {
        Throwable current = root;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
