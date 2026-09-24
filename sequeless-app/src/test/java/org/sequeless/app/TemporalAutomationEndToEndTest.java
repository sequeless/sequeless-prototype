package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.support.TemporalTestcontainersSupport;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.OutboxPort;
import org.sequeless.spi.object.Update;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application against a real Postgres <em>and</em> a real Temporal
 * server (both Testcontainers-managed, see {@link TemporalTestcontainersSupport}) with {@code
 * sequeless.automation.adapter=temporal}, proving two properties {@code
 * TransitionsEndToEndTest} cannot (that class runs the in-process adapter): that a webhook
 * activity genuinely retries through Temporal's own {@code RetryOptions} until it succeeds, and
 * that {@link org.sequeless.adapter.automation.temporal.TemporalAutomationPort}'s explicit {@code
 * WorkflowIdReusePolicy.REJECT_DUPLICATE} (finding F40 from T10) actually prevents a duplicate
 * side effect when the same {@link OutboxEntry} is dispatched twice — the realistic shape of "the
 * relay crashed between committing an outbox row and finishing its dispatch, then redispatched the
 * same row after restart."
 *
 * <p><b>Deliberately hand-builds its own {@code ActionRequest} {@link OutboxEntry} payloads rather
 * than going through {@code sq:CreateObject}/{@code sq:Webhook} actions declared in {@code
 * reference.ttl}.</b> {@link OutboxEntry#id()} is caller-assigned, {@link ChangeSet} accepts any
 * caller-built {@link OutboxEntry} list alongside a non-empty {@link Mutation} list, and {@link
 * ObjectStorePort#commit} persists both atomically for real — so this test attaches a
 * hand-built, {@code Webhook}- or {@code CreateObject}-kind {@code ActionRequest} row to a
 * legitimate {@link Update} mutation (bumping an already-created {@code Project}'s version) and
 * commits through the real {@link ObjectStorePort} bean. This exercises the exact production path
 * (a real {@code OutboxRelay} tick &rarr; the real {@link AutomationPort} bean &rarr; a real
 * Temporal workflow/activity &rarr; the real {@code ActionExecutor} &rarr; a real HTTP call for
 * the webhook test) without touching {@code reference.ttl} or its {@code sq:ProjectLifecycle}
 * transitions at all — see {@code DefaultTransitionService}'s exact payload shape (finding F23)
 * that this class's payload-building helpers mirror field for field.
 *
 * <p>Retry properties are overridden to keep this test fast: 50ms initial interval, 1.0 backoff
 * coefficient (no exponential growth), 5 max attempts.
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
            "sequeless.automation.temporal.retry.maximum-attempts=5"
        })
class TemporalAutomationEndToEndTest extends TemporalTestcontainersSupport {

    private static final Scope SCOPE = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
    private static final String TASK_TYPE_IRI = "https://sequeless.dev/ns/ref#Task";
    private static final String TITLE_PROPERTY_IRI = "https://sequeless.dev/ns/ref#title";

    @LocalServerPort private int port;

    @Autowired private ObjectStorePort objectStorePort;
    @Autowired private OutboxPort outboxPort;
    @Autowired private AutomationPort automationPort;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer webhookServer;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void stopWebhookServer() {
        if (webhookServer != null) {
            webhookServer.stop(0);
        }
    }

    @Test
    void webhookRetriesThreeTimesThenSucceedsAndMarksOutboxRowProcessed() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        webhookServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        webhookServer.createContext(
            "/hook",
            exchange -> {
                int count = requestCount.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                int status = count < 4 ? 500 : 200;
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
        webhookServer.start();
        String webhookUrl = "http://localhost:" + webhookServer.getAddress().getPort() + "/hook";

        BusinessObjectResponse project = createProject("Webhook retry project");
        BusinessObject existing =
            objectStorePort.find(SCOPE, new ObjectId(project.id())).orElseThrow();

        Update touch = touch(existing);
        OutboxEntry actionRequest =
            webhookActionRequestEntry(existing, "webhookRetries", webhookUrl);

        objectStorePort.commit(
            SCOPE, new ChangeSet(List.<Mutation>of(touch), List.of(actionRequest)));

        awaitRequestCount(requestCount, 4);
        assertThat(requestCount.get()).as("total webhook requests observed").isEqualTo(4);

        awaitOutboxDrained();
    }

    @Test
    void dispatchingSameActionTwiceCreatesOnlyOneTask() {
        String uniqueTitle = "Idempotency Task " + UUID.randomUUID();

        BusinessObjectResponse project = createProject("Idempotency project");
        BusinessObject existing =
            objectStorePort.find(SCOPE, new ObjectId(project.id())).orElseThrow();

        Update touch = touch(existing);
        UUID entryId = UUID.randomUUID();
        OutboxEntry actionRequest = createObjectActionRequestEntry(entryId, existing, uniqueTitle);

        objectStorePort.commit(
            SCOPE, new ChangeSet(List.<Mutation>of(touch), List.of(actionRequest)));

        // Simulates "the relay crashed between committing this row and finishing its dispatch,
        // then redispatched the identical row after restart": call dispatch() directly, twice,
        // bypassing OutboxPort's own claim mechanism (already covered by T8's contract tests).
        automationPort.dispatch(SCOPE, actionRequest);
        automationPort.dispatch(SCOPE, actionRequest);

        awaitExactlyOneTaskTitled(uniqueTitle);
    }

    private Update touch(BusinessObject existing) {
        Instant now = Instant.now();
        Audit audit =
            new Audit(
                existing.audit().createdAt(),
                existing.audit().createdBy(),
                now,
                SCOPE.principal().id());
        BusinessObject candidate =
            new BusinessObject(
                existing.id(),
                existing.type(),
                existing.tenant(),
                existing.version() + 1,
                existing.state(),
                existing.properties(),
                audit,
                false);
        return new Update(candidate, existing.version());
    }

    private static OutboxEntry webhookActionRequestEntry(
        BusinessObject existing, String transitionName, String url) {
        Map<String, Object> payload = commonPayload(existing, transitionName);
        payload.put("actionKind", "Webhook");
        payload.put("url", url);
        payload.put("method", "POST");
        payload.put("body", "delivered");
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, Instant.now());
    }

    private static OutboxEntry createObjectActionRequestEntry(
        UUID entryId, BusinessObject existing, String title) {
        Map<String, Object> payload = commonPayload(existing, "createIdempotent");
        payload.put("actionKind", "CreateObject");
        payload.put("createType", TASK_TYPE_IRI);
        Map<String, Object> titleAssignment = Map.of("value", Map.of("text", title));
        payload.put("createProperties", Map.of(TITLE_PROPERTY_IRI, titleAssignment));
        return new OutboxEntry(entryId, OutboxEntry.KIND_ACTION_REQUEST, payload, Instant.now());
    }

    private static Map<String, Object> commonPayload(BusinessObject existing, String transitionName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", existing.id().value().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("transitionName", transitionName);
        payload.put("actionIndex", 0);
        payload.put("typeIri", existing.type().iri());
        payload.put("state", existing.state().orElse("Active"));
        payload.put("self", Map.of());
        return payload;
    }

    private void awaitRequestCount(AtomicInteger requestCount, int expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (requestCount.get() < expected && Instant.now().isBefore(deadline)) {
            sleep(Duration.ofMillis(100));
        }
    }

    private void awaitOutboxDrained() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        Optional<Boolean> claimed;
        do {
            claimed =
                outboxPort.claimNext(
                    Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> Boolean.TRUE);
            if (claimed.isPresent()) {
                sleep(Duration.ofMillis(100));
            }
        } while (claimed.isPresent() && Instant.now().isBefore(deadline));
        assertThat(claimed)
            .as("no unprocessed ActionRequest outbox rows remain")
            .isEmpty();
    }

    private void awaitExactlyOneTaskTitled(String expectedTitle) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        int matches = 0;
        while (Instant.now().isBefore(deadline)) {
            matches = countTasksTitled(expectedTitle);
            if (matches >= 1) {
                break;
            }
            sleep(Duration.ofMillis(200));
        }
        assertThat(matches).as("exactly one Task titled '%s' exists", expectedTitle).isEqualTo(1);

        // Give any redundant (would-be duplicate) dispatch a moment to land, then confirm the
        // count never rises above one.
        sleep(Duration.ofSeconds(2));
        assertThat(countTasksTitled(expectedTitle))
            .as("no duplicate Task titled '%s' appeared", expectedTitle)
            .isEqualTo(1);
    }

    private int countTasksTitled(String expectedTitle) {
        JsonNode page = fetchAsJson("/objects/Task?page=0&size=200");
        int count = 0;
        for (JsonNode item : page.get("items")) {
            JsonNode title = item.get("properties").get("title");
            if (title != null && titleMatches(title, expectedTitle)) {
                count++;
            }
        }
        return count;
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
