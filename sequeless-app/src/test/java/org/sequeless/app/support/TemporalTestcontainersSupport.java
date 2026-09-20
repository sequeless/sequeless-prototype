package org.sequeless.app.support;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers Temporal wiring for {@code @SpringBootTest}s that exercise {@code
 * sequeless.automation.adapter=temporal}, composed on top of {@link PostgresTestcontainersSupport}
 * (plain single-inheritance extension, not a JUnit extension/interface composition — matching that
 * class's own established convention exactly) so every subclass gets both containers for free.
 *
 * <p><b>Image, command, and healthcheck are deliberately copied verbatim from this repo's own
 * {@code docker-compose.yml}, not the originally-guessed {@code temporalio/auto-setup} +
 * log-line-wait design.</b> {@code docker-compose.yml}'s {@code temporal} service already runs and
 * validates {@code temporalio/temporal:1.9.1} via {@code server start-dev}, which is
 * self-contained (an embedded sqlite-backed server, no external database dependency unlike {@code
 * auto-setup}) and auto-registers the {@code default} namespace {@link
 * org.sequeless.adapter.automation.temporal.TemporalAutomationAutoConfiguration}'s {@code
 * WorkflowServiceStubs}/{@code WorkflowClient}/{@code WorkerFactory} need. The healthcheck command —
 * {@code temporal operator cluster health --address 127.0.0.1:7233} — is the same one {@code
 * docker-compose.yml} already proved reliable, reused here via {@link
 * Wait#forSuccessfulCommand(String)} instead of trusting a log line to appear at the right time.
 *
 * <p>Image tag is pinned to {@code 1.9.1}, matching {@code docker-compose.yml} exactly — never
 * {@code :latest}.
 *
 * <p>No {@code --db-filename}/volume mount here (unlike {@code docker-compose.yml}, which persists
 * state across {@code docker compose down}): a Testcontainers container is ephemeral for the
 * lifetime of one JVM fork, so there is nothing to persist across runs, and the server's own
 * default embedded-sqlite location inside the container is sufficient.
 *
 * <p>One container per JVM, started once — see {@link PostgresTestcontainersSupport}'s javadoc for
 * why {@code @Container} on a {@code static} field plus {@code @Testcontainers} (and never calling
 * {@code container.stop()} directly) is this codebase's established singleton-container pattern,
 * reused here for exactly the same reason.
 */
@Testcontainers
public abstract class TemporalTestcontainersSupport extends PostgresTestcontainersSupport {

    @Container
    protected static final GenericContainer<?> TEMPORAL =
        new GenericContainer<>("temporalio/temporal:1.9.1")
            .withCommand(
                "server", "start-dev", "--ip", "0.0.0.0", "--db-filename", "/home/temporal/temporal.db")
            .withExposedPorts(7233)
            .waitingFor(
                Wait.forSuccessfulCommand(
                        "temporal operator cluster health --address 127.0.0.1:7233")
                    .withStartupTimeout(Duration.ofSeconds(90)));

    @DynamicPropertySource
    static void temporalProperties(DynamicPropertyRegistry registry) {
        registry.add(
            "sequeless.automation.temporal.target",
            () -> TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(7233));
    }
}
