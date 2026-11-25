package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.rest.TransitionResponse;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.outbox.OutboxEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application on a random port and proves the Phase 5 state-machine
 * REST surface end to end against a real Postgres container, with the in-process automation
 * adapter draining the outbox: {@code GET .../transitions} renders guard-blocked availability with
 * the exact reference-ontology guard message, {@code POST .../transitions/{name}} enforces the
 * same {@code expectedVersion} precondition convention {@code PUT} already uses, a failed guard
 * maps to 409, and a successful {@code activate} both moves {@code state} to {@code Active} and
 * (asynchronously, via {@code OutboxRelay}) creates the kickoff {@code Task} the transition's
 * {@code sq:CreateObject} action declares.
 *
 * <p><b>Kickoff title is bracketed, not a clean sentence — verified, not assumed.</b> {@code
 * ex:title} has unbounded cardinality, so {@code self.title} inside the {@code
 * 'Kickoff: ' + self.title} expression is a JEXL-bound {@code ArrayList} (see {@code
 * JexlExpressionPort}'s own javadoc on {@code ListValue} unwrapping), and JEXL's {@code +}
 * concatenation falls back to {@code Object#toString()} for the non-string operand. This was
 * confirmed empirically (a throwaway JEXL 3.6.2 script) to render as {@code "Kickoff: [<title>]"}
 * — square brackets included — not the naively-expected {@code "Kickoff: <title>"}. This is a
 * pre-existing core/reference-ontology behaviour from earlier phase-5 tasks, not something this
 * task's REST-layer scope changes; this test asserts the actual rendered literal.
 *
 * <p>The transition's {@code sq:Webhook} action targets a hardcoded external URL ({@code
 * https://example.org/hooks/project-activated}) that this sandbox cannot resolve. The activate
 * row's actions commit together, so that failure would roll back the kickoff Task too. The webhook
 * is therefore redirected to a local stand-in at the {@link ActionExecutor} seam:
 * {@link WebhookStandInConfiguration} registers a {@code @Primary} decorator that delegates to the
 * real executor and rewrites only the resolved webhook URL to a localhost {@code HttpServer}. The
 * production URL and the reference ontology are unchanged. This test asserts the kickoff {@code Task}.
 *
 * <p>{@code sequeless.automation.relay.poll-interval-ms=50} keeps the bounded-retry wait for the
 * async kickoff-Task side effect fast; the whole {@code properties} set differs from every other
 * {@code @SpringBootTest} class in this module (per {@code ObjectsEndToEndTest}'s own documented
 * test-context-cache gotcha), so this class gets its own Spring context rather than reusing
 * another class's already-stopped Testcontainers Postgres container.
 */
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {
            "sequeless.persistence.adapter=postgres",
            "sequeless.automation.adapter=inprocess",
            "sequeless.expression.adapter=jexl",
            "sequeless.automation.relay.poll-interval-ms=50"
        })
class TransitionsEndToEndTest extends PostgresTestcontainersSupport {

    private static final String GUARD_MESSAGE = "Project must have an owner before it can be activated";

    /**
     * Registers the test-only {@code @Primary} {@link ActionExecutor} that redirects the activate
     * webhook to a local stand-in. Spring Boot picks up this single nested {@code @TestConfiguration}
     * automatically. The decorator wraps the real {@code actionExecutor} bean by name, so the context
     * has no cycle: the real bean never depends on the decorator.
     */
    @TestConfiguration
    static class WebhookStandInConfiguration {

        @Bean(destroyMethod = "close")
        @Primary
        ActionExecutor webhookStandInActionExecutor(@Qualifier("actionExecutor") ActionExecutor delegate)
                throws IOException {
            return new WebhookStandInActionExecutor(delegate);
        }
    }

    /**
     * Delegates every {@link ActionExecutor} call to the real executor, except that {@link
     * #resolveWebhookAction} returns the same method and body with the URL replaced by this class's
     * localhost stand-in, which answers 204 to any request. The stand-in is started when the bean is
     * created and stopped when the context closes.
     */
    static final class WebhookStandInActionExecutor implements ActionExecutor, AutoCloseable {

        private final ActionExecutor delegate;
        private final HttpServer standIn;

        WebhookStandInActionExecutor(ActionExecutor delegate) throws IOException {
            this.delegate = delegate;
            this.standIn = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            standIn.createContext("/hook", WebhookStandInActionExecutor::acknowledge);
            standIn.start();
        }

        private static void acknowledge(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }

        @Override
        public void close() {
            standIn.stop(0);
        }

        @Override
        public void applyAction(Scope scope, OutboxEntry transitionFired, Action action, int actionIndex) {
            delegate.applyAction(scope, transitionFired, action, actionIndex);
        }

        @Override
        public ResolvedWebhookRequest resolveWebhookAction(
                Scope scope, OutboxEntry transitionFired, WebhookAction action, int actionIndex) {
            ResolvedWebhookRequest resolved =
                    delegate.resolveWebhookAction(scope, transitionFired, action, actionIndex);
            String standInUrl = "http://localhost:" + standIn.getAddress().getPort() + "/hook";
            return new ResolvedWebhookRequest(standInUrl, resolved.method(), resolved.body());
        }
    }

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void draftProjectHasOnlyActivateUnavailableWithGuardMessage() {
        BusinessObjectResponse project = createProject("No owner yet");

        List<TransitionResponse> transitions = fetchTransitions(project.id());

        assertThat(transitions).hasSize(1);
        TransitionResponse activate = transitions.get(0);
        assertThat(activate.name()).isEqualTo("activate");
        assertThat(activate.available()).isFalse();
        assertThat(activate.reason()).isEqualTo(GUARD_MESSAGE);
    }

    @Test
    void firingUnavailableTransitionIs409WithGuardMessage() {
        BusinessObjectResponse project = createProject("Still no owner");

        restTestClient
                .post()
                .uri("/api/objects/Project/" + project.id() + "/transitions/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedVersion", 1))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .value(type -> assertThat(type.toString()).endsWith("transition-not-available"))
                .jsonPath("$.detail")
                .isEqualTo(GUARD_MESSAGE)
                .jsonPath("$.transitionName")
                .isEqualTo("activate");
    }

    @Test
    void firingTransitionWithNoVersionAndNoIfMatchIs428() {
        BusinessObjectResponse project = createProject("No precondition supplied");

        restTestClient
                .post()
                .uri("/api/objects/Project/" + project.id() + "/transitions/activate")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PRECONDITION_REQUIRED)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void firingTransitionWithMismatchedBodyAndIfMatchIs400() {
        BusinessObjectResponse project = createProject("Mismatched precondition");

        restTestClient
                .post()
                .uri("/api/objects/Project/" + project.id() + "/transitions/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"2\"")
                .body(Map.of("expectedVersion", 1))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void activateSucceedsAfterOwnerSetAndEventuallyCreatesKickoffTask() {
        String projectTitle = "Launch rocket";
        BusinessObjectResponse project = createProject(projectTitle);
        BusinessObjectResponse owner = createPerson("Ada Lovelace");

        BusinessObjectResponse withOwner = restTestClient
                .put()
                .uri("/api/objects/Project/" + project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
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

        BusinessObjectResponse activated = restTestClient
                .post()
                .uri("/api/objects/Project/" + project.id() + "/transitions/activate")
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

        // The kickoff Task is one action of the activate TransitionFired row, applied by the
        // in-process automation adapter after the POST above returns. See this class's javadoc for
        // why the title is "Kickoff: [Launch rocket]", brackets included.
        awaitKickoffTask("Kickoff: [" + projectTitle + "]");
    }

    /**
     * REST-level proof that {@code POST /api/objects/{type}/{id}/signals/{name}} fires the transition
     * the signal names in the request: the reply is 202 with no body, and the Project is already
     * {@code Active} when the reply arrives, because the reopen commits before the reply is sent.
     * The automation adapter applies the transition's actions afterwards from its {@code
     * TransitionFired} row; this class does not test those, and {@link AutomationEndToEndTest} covers
     * the automation path. The 409 for a signal that no transition departing the current state
     * carries is {@link #reopenSignalOnActiveProjectIs409}.
     */
    @Test
    void reopenSignalOnClosedProjectIsAccepted() {
        String projectTitle = "Signal test project";
        BusinessObjectResponse project = createProject(projectTitle);
        BusinessObjectResponse owner = createPerson("Grace Hopper");

        restTestClient
                .put()
                .uri("/api/objects/Project/" + project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version",
                        1,
                        "properties",
                        Map.of(
                                "title", List.of(projectTitle),
                                "owner", owner.id().toString())))
                .exchange()
                .expectStatus()
                .isOk();

        fireTransition(project.id(), "activate", 2);
        fireTransition(project.id(), "close", 3);

        restTestClient
                .post()
                .uri("/api/objects/Project/" + project.id() + "/signals/reopen")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.ACCEPTED)
                .expectBody()
                .isEmpty();

        BusinessObjectResponse reopened = restTestClient
                .get()
                .uri("/api/objects/Project/" + project.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(BusinessObjectResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(reopened).isNotNull();
        assertThat(reopened.state()).isEqualTo("Active");
    }

    /**
     * A signal whose name the type declares, but that no transition departing the object's current
     * state carries, is a 409 {@code transition-not-available}, with {@code transitionName} set to the
     * signal name. The Project here is {@code Active}, and {@code reopen} departs only {@code Closed}.
     */
    @Test
    void reopenSignalOnActiveProjectIs409() {
        String projectTitle = "Reopen on an active project";
        BusinessObjectResponse project = createProject(projectTitle);
        BusinessObjectResponse owner = createPerson("Katherine Johnson");

        restTestClient
                .put()
                .uri("/api/objects/Project/" + project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "version",
                        1,
                        "properties",
                        Map.of(
                                "title", List.of(projectTitle),
                                "owner", owner.id().toString())))
                .exchange()
                .expectStatus()
                .isOk();

        fireTransition(project.id(), "activate", 2);

        restTestClient
                .post()
                .uri("/api/objects/Project/" + project.id() + "/signals/reopen")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .value(type -> assertThat(type.toString()).endsWith("transition-not-available"))
                .jsonPath("$.transitionName")
                .isEqualTo("reopen");
    }

    /**
     * A signal name no transition anywhere on the resolved type's state machine declares is a 400
     * with {@code type} suffix {@code unknown-signal} and {@code signalName} set — {@link
     * org.sequeless.app.rest.ApiExceptionAdviceTest} unit-tests the mapping directly; this proves it
     * wired end to end through the real controller and advice.
     */
    @Test
    void unknownSignalNameIs400() {
        BusinessObjectResponse project = createProject("Unknown signal project");

        restTestClient
                .post()
                .uri("/api/objects/Project/" + project.id() + "/signals/bogus")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type")
                .value(type -> assertThat(type.toString()).endsWith("unknown-signal"))
                .jsonPath("$.signalName")
                .isEqualTo("bogus");
    }

    private void fireTransition(UUID projectId, String name, long expectedVersion) {
        restTestClient
                .post()
                .uri("/api/objects/Project/" + projectId + "/transitions/" + name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expectedVersion", expectedVersion))
                .exchange()
                .expectStatus()
                .isOk();
    }

    private void awaitKickoffTask(String expectedTitle) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        boolean found = false;
        while (!found && Instant.now().isBefore(deadline)) {
            JsonNode page = fetchAsJson("/api/objects/Task?page=0&size=200");
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
        assertThat(found)
                .as("kickoff Task with title '%s' eventually appears", expectedTitle)
                .isTrue();
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
                .uri("/api/objects/Project")
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
                .uri("/api/objects/Person")
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
                .uri("/api/objects/Project/" + projectId + "/transitions")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<TransitionResponse>>() {})
                .returnResult()
                .getResponseBody();
    }

    private JsonNode fetchAsJson(String uri) {
        byte[] responseBody = restTestClient
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
