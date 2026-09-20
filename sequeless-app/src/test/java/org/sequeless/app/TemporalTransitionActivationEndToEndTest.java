package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.rest.TransitionResponse;
import org.sequeless.app.support.TemporalTestcontainersSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Re-runs {@code TransitionsEndToEndTest}'s Draft -&gt; Active guard/success happy path, but under
 * {@code sequeless.automation.adapter=temporal} against a real Testcontainers-managed Temporal
 * server (see {@link TemporalTestcontainersSupport}), proving the same REST-visible behaviour
 * (guard-blocked availability, {@code expectedVersion} precondition enforcement, and the eventual
 * kickoff {@code Task} side effect) holds when the outbox relay hands actions off to a durable
 * Temporal workflow instead of running them in-process.
 *
 * <p>This class deliberately duplicates {@code TransitionsEndToEndTest}'s happy-path test method
 * and its small REST helper methods rather than factoring them into a shared base class.
 * {@code TemporalTestcontainersSupport} already extends {@code PostgresTestcontainersSupport}
 * (single inheritance), so an in-process-only test extending some new shared base that itself
 * extended {@code TemporalTestcontainersSupport} would pay for an unused Temporal container. The
 * duplication here is small and deliberate: clarity over coupling two unrelated test classes
 * through a shared base for one method.
 *
 * <p><b>Kickoff title is bracketed, not a clean sentence.</b> {@code ex:title} has unbounded
 * cardinality, so {@code self.title} inside the {@code 'Kickoff: ' + self.title} expression is a
 * JEXL-bound {@code ArrayList}, and JEXL's {@code +} concatenation falls back to {@code
 * Object#toString()} for the non-string operand, rendering as {@code "Kickoff: [<title>]"} —
 * square brackets included. See {@code TransitionsEndToEndTest}'s javadoc and finding F53 for the
 * full explanation; this test asserts the same bracketed literal, not the naively-expected clean
 * form.
 *
 * <p>Deliberately does not assert anything about the transition's {@code sq:Webhook} action: it
 * targets a hardcoded external URL ({@code https://example.org/hooks/project-activated}) with no
 * local interception mechanism today. In this sandbox that URL fails DNS resolution in
 * milliseconds, so it does not meaningfully slow down the relay or the Temporal workflow's retry
 * handling, but a test that wants to assert webhook delivery/retry behaviour under Temporal needs
 * a locally-reachable stand-in (e.g. WireMock or the hand-rolled {@code HttpServer} {@code
 * TemporalAutomationEndToEndTest} already uses for exactly that purpose).
 *
 * <p><b>The {@code properties} array below is deliberately NOT byte-for-byte identical to {@code
 * TemporalAutomationEndToEndTest}'s, despite that being the original intent (to let Spring's
 * test-context cache key-match and reuse one booted context across both classes).</b> That was
 * tried and reverted after it reproduced a genuine, deterministic failure: {@code
 * PostgresTestcontainersSupport}/{@code TemporalTestcontainersSupport}'s {@code @Container} static
 * fields are managed by JUnit 5's {@code @Testcontainers} extension with <em>per-test-class</em>
 * lifecycle semantics (started in that class's {@code beforeAll} if not running, stopped in that
 * class's {@code afterAll}) — not true JVM-wide singletons that outlive every class, despite this
 * codebase's own javadoc/finding-F56 claims to the contrary. When two {@code @SpringBootTest}
 * classes have byte-identical {@code properties} arrays, Spring's context cache reuses the same
 * {@code ApplicationContext} (and therefore the same already-configured {@code DataSource} pointing
 * at the first class's Postgres container's mapped port) for the second class — but
 * {@code @Testcontainers} independently stops that same container after the first class's tests
 * finish and starts a brand-new one (new container id, new mapped port) for the second class. The
 * cached Spring context never learns about the new port, so the second class's first REST call
 * fails with a real, reproducible {@code CannotCreateTransactionException} /
 * {@code Connection to host.docker.internal:&lt;old-port&gt; refused}, observed here specifically as
 * {@code TemporalAutomationEndToEndTest}'s {@code createProject} helper returning 500 instead of
 * 201 whenever it runs immediately after this class with matching properties. Verified by
 * reproducing it twice (once inside a full {@code mvn verify}, once in an isolated two-class
 * Surefire run) and by confirming the fix (one deliberately different retry property below) makes
 * both classes pass reliably back to back. Flagged as a real follow-up: the shared Testcontainers
 * support classes should adopt the standard Testcontainers "singleton container" pattern (start in
 * a static initializer, no {@code @Container}/{@code @Testcontainers}-managed stop) if per-class
 * Spring-context-cache reuse across Testcontainers-backed classes is ever wanted again.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
            "sequeless.persistence.adapter=postgres",
            "sequeless.automation.adapter=temporal",
            "sequeless.expression.adapter=jexl",
            "sequeless.automation.relay.poll-interval-ms=50",
            "sequeless.automation.temporal.retry.initial-interval=50ms",
            "sequeless.automation.temporal.retry.backoff-coefficient=1.0",
            // Deliberately 6, not 5 (TemporalAutomationEndToEndTest's value) -- the one
            // intentional difference from that class's properties array, see class javadoc: this
            // keeps the two classes' Spring test contexts from cache-matching, which avoids a real
            // Testcontainers-lifecycle-vs-context-cache bug this pairing otherwise reproduces.
            "sequeless.automation.temporal.retry.maximum-attempts=6"
        })
class TemporalTransitionActivationEndToEndTest extends TemporalTestcontainersSupport {

    private static final String GUARD_MESSAGE =
            "Project must have an owner before it can be activated";

    @LocalServerPort private int port;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void activateSucceedsAfterOwnerSetAndEventuallyCreatesKickoffTaskUnderTemporal() {
        // Unique suffix: Testcontainers Postgres persists across test classes within one JVM fork,
        // so a fixed literal title risks a false-positive match against another test class's rows
        // in the same database (finding F59).
        String projectTitle = "Launch rocket " + UUID.randomUUID();
        BusinessObjectResponse project = createProject(projectTitle);
        BusinessObjectResponse owner = createPerson("Ada Lovelace");

        List<TransitionResponse> transitionsBeforeOwner = fetchTransitions(project.id());
        assertThat(transitionsBeforeOwner).hasSize(1);
        TransitionResponse activateBeforeOwner = transitionsBeforeOwner.get(0);
        assertThat(activateBeforeOwner.name()).isEqualTo("activate");
        assertThat(activateBeforeOwner.available()).isFalse();
        assertThat(activateBeforeOwner.reason()).isEqualTo(GUARD_MESSAGE);

        BusinessObjectResponse withOwner =
                restTestClient
                        .put()
                        .uri("/objects/Project/" + project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                Map.of(
                                        "version",
                                        1,
                                        "properties",
                                        Map.of(
                                                "title", List.of(projectTitle),
                                                "owner", owner.id().toString())))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(BusinessObjectResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(withOwner).isNotNull();
        assertThat(withOwner.version()).isEqualTo(2L);

        List<TransitionResponse> transitions = fetchTransitions(project.id());
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).available()).as("activate now available").isTrue();
        assertThat(transitions.get(0).reason()).isNull();

        BusinessObjectResponse activated =
                restTestClient
                        .post()
                        .uri("/objects/Project/" + project.id() + "/transitions/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("expectedVersion", 2))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectHeader()
                        .valueEquals("ETag", "\"3\"")
                        .expectBody(BusinessObjectResponse.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(activated).isNotNull();
        assertThat(activated.state()).isEqualTo("Active");
        assertThat(activated.version()).isEqualTo(3L);

        // The kickoff Task is created asynchronously: the live OutboxRelay drains the
        // ActionRequest row this transition wrote and hands it to the real Temporal-backed
        // AutomationPort, which starts an ActionWorkflow that runs the CreateObject activity —
        // not synchronously by the POST above. See this class's javadoc for why the title is
        // "Kickoff: [<title>]", brackets included.
        awaitKickoffTask("Kickoff: [" + projectTitle + "]");
    }

    private void awaitKickoffTask(String expectedTitle) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        boolean found = false;
        while (!found && Instant.now().isBefore(deadline)) {
            JsonNode page = fetchAsJson("/objects/Task?page=0&size=200");
            for (JsonNode item : page.get("items")) {
                JsonNode title = item.get("properties").get("title");
                if (title != null && titleMatches(title, expectedTitle)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                sleep(Duration.ofMillis(100));
            }
        }
        assertThat(found).as("kickoff Task with title '%s' eventually appears", expectedTitle).isTrue();
    }

    private static boolean titleMatches(JsonNode title, String expectedTitle) {
        if (title.isArray()) {
            for (JsonNode element : title) {
                if (element.asText().equals(expectedTitle)) {
                    return true;
                }
            }
            return false;
        }
        return title.asText().equals(expectedTitle);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private BusinessObjectResponse createProject(String title) {
        return restTestClient
                .post()
                .uri("/objects/Project")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("properties", Map.of("title", List.of(title))))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private BusinessObjectResponse createPerson(String name) {
        return restTestClient
                .post()
                .uri("/objects/Person")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("properties", Map.of("name", name)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private List<TransitionResponse> fetchTransitions(UUID projectId) {
        return restTestClient
                .get()
                .uri("/objects/Project/" + projectId + "/transitions")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<TransitionResponse>>() {})
                .returnResult()
                .getResponseBody();
    }

    private JsonNode fetchAsJson(String uri) {
        byte[] responseBody =
                restTestClient
                        .get()
                        .uri(uri)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
