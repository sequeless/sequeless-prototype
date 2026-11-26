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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.rest.BusinessObjectResponse;
import org.sequeless.app.support.TemporalTestcontainersSupport;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.api.TransitionFiredEvent;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.PropertyAssignment;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.spi.outbox.OutboxPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the real Spring Boot application against a real Postgres <em>and</em> a real Temporal
 * server (both Testcontainers-managed, see {@link TemporalTestcontainersSupport}) with {@code
 * sequeless.automation.adapter=temporal}, proving two properties {@code
 * TransitionsEndToEndTest} cannot (that class runs the in-process adapter): that a webhook
 * activity genuinely retries through Temporal's own {@code RetryOptions} until it succeeds, and
 * that {@link org.sequeless.automation.temporal.TemporalAutomationPort}'s explicit {@code
 * WorkflowIdReusePolicy.REJECT_DUPLICATE} (finding F40 from T10) actually prevents a duplicate
 * side effect when the same {@link TransitionFiredEvent} is ingested twice — the realistic shape of "the
 * relay crashed between committing an outbox row and finishing its ingest, then re-ingested the
 * same row after restart."
 *
 * <p><b>Deliberately hand-builds its own {@code TransitionFired} {@link OutboxEntry} rows rather
 * than running a real transition of {@code reference.ttl}.</b> {@link OutboxEntry#id()} is
 * caller-assigned, {@link ChangeSet} accepts any caller-built {@link OutboxEntry} list alongside a
 * non-empty {@link Mutation} list, and {@link ObjectStorePort#commit} persists both atomically for
 * real — so this test attaches a hand-built {@code TransitionFired} row to a legitimate {@link
 * Update} mutation (bumping an already-created {@code Project}'s version) and commits through the
 * real {@link ObjectStorePort} bean. The row's transitions ({@code webhookRetries} and {@code
 * createIdempotent}) are not in {@code reference.ttl}, so their actions come from a test-only
 * {@code @Primary} {@link AutomationSource} ({@link AutomationSourceTestConfiguration}) that this
 * class's tests set per test. This exercises the production path (a real {@code OutboxRelay} tick
 * &rarr; the real {@link AutomationPort} bean &rarr; a real Temporal workflow/activity &rarr; the
 * real {@code ActionExecutor} &rarr; a real HTTP call for the webhook test) without touching {@code
 * reference.ttl} or any production class. The row's payload mirrors the keys {@code
 * DefaultTransitionService} writes on a {@code TransitionFired} row, field for field.
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
    private static final int ONTOLOGY_VERSION = 1;
    private static final String MACHINE_IRI = "https://sequeless.dev/ns/test#TestLifecycle";
    private static final String FROM_STATE_IRI = "https://sequeless.dev/ns/test#Draft";
    private static final String TO_STATE_IRI = "https://sequeless.dev/ns/test#Active";

    /**
     * Registers the test-only {@code @Primary} {@link AutomationSource} this class's rows resolve their
     * actions from. The reference ontology declares neither {@code webhookRetries} nor {@code
     * createIdempotent}, so the Jena source the application would otherwise use serves no actions for
     * them. This is the same {@code @Primary} override shape {@code AutomationEndToEndTest} uses for its
     * {@link java.time.Clock}. Spring Boot picks up this single nested {@code @TestConfiguration}
     * automatically.
     */
    @TestConfiguration
    static class AutomationSourceTestConfiguration {

        @Bean
        @Primary
        ServedAutomationSource servedAutomationSource() {
            return new ServedAutomationSource();
        }
    }

    /**
     * An {@link AutomationSource} that serves whatever automation the test last handed to {@link
     * #serve(Automation)}. Every row this class commits is stamped with {@link #ONTOLOGY_VERSION}, so the
     * automation served must carry that version too.
     */
    static final class ServedAutomationSource implements AutomationSource {

        private final AtomicReference<Automation> served =
                new AtomicReference<>(new Automation(new OntologyVersion(ONTOLOGY_VERSION), Map.of()));

        void serve(Automation automation) {
            served.set(automation);
        }

        @Override
        public Automation automation(TenantId tenantId) {
            return served.get();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectStorePort objectStorePort;

    @Autowired
    private OutboxPort outboxPort;

    @Autowired
    private AutomationPort automationPort;

    @Autowired
    private ServedAutomationSource servedAutomationSource;

    private RestTestClient restTestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer webhookServer;

    @BeforeEach
    void setUp() {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
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
        webhookServer.createContext("/hook", exchange -> {
            int count = requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            int status = count < 4 ? 500 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        webhookServer.start();
        String webhookUrl = "http://localhost:" + webhookServer.getAddress().getPort() + "/hook";
        servedAutomationSource.serve(
                automation("webhookRetries", new WebhookAction(webhookUrl, "POST", Optional.of("delivered"))));

        BusinessObjectResponse project = createProject("Webhook retry project");
        BusinessObject existing =
                objectStorePort.find(SCOPE, new ObjectId(project.id())).orElseThrow();

        Update touch = touch(existing);
        OutboxEntry transitionFired = transitionFiredEntry(UUID.randomUUID(), existing, "webhookRetries");

        objectStorePort.commit(SCOPE, new ChangeSet(List.<Mutation>of(touch), List.of(transitionFired)));

        awaitRequestCount(requestCount, 4);
        assertThat(requestCount.get()).as("total webhook requests observed").isEqualTo(4);

        awaitOutboxDrained();
    }

    @Test
    void ingestingSameActionTwiceCreatesOnlyOneTask() {
        String uniqueTitle = "Idempotency Task " + UUID.randomUUID();
        servedAutomationSource.serve(automation(
                "createIdempotent",
                new CreateObjectAction(
                        TASK_TYPE_IRI,
                        List.of(new PropertyAssignment(
                                TITLE_PROPERTY_IRI, Optional.of(Value.text(uniqueTitle)), Optional.empty())))));

        BusinessObjectResponse project = createProject("Idempotency project");
        BusinessObject existing =
                objectStorePort.find(SCOPE, new ObjectId(project.id())).orElseThrow();

        Update touch = touch(existing);
        UUID entryId = UUID.randomUUID();
        OutboxEntry transitionFired = transitionFiredEntry(entryId, existing, "createIdempotent");

        objectStorePort.commit(SCOPE, new ChangeSet(List.<Mutation>of(touch), List.of(transitionFired)));

        // Simulates "the relay crashed between committing this row and finishing its ingest,
        // then re-ingested the identical row after restart": call ingest() directly, twice,
        // bypassing OutboxPort's own claim mechanism (already covered by T8's contract tests).
        TransitionFiredEvent fired =
                new TransitionFiredEvent(transitionFired.id(), transitionFired.payload(), transitionFired.occurredAt());
        automationPort.ingest(SCOPE, fired);
        automationPort.ingest(SCOPE, fired);

        awaitExactlyOneTaskTitled(uniqueTitle);
    }

    private Update touch(BusinessObject existing) {
        Instant now = Instant.now();
        Audit audit = new Audit(
                existing.audit().createdAt(),
                existing.audit().createdBy(),
                now,
                SCOPE.principal().id());
        BusinessObject candidate = new BusinessObject(
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

    /** Serves {@code action} as the only action of {@code transitionName} in this class's automation. */
    private static Automation automation(String transitionName, Action action) {
        TransitionRef transition = new TransitionRef(MACHINE_IRI, FROM_STATE_IRI, transitionName);
        return new Automation(new OntologyVersion(ONTOLOGY_VERSION), Map.of(transition, List.of(action)));
    }

    /** A {@code TransitionFired} row carrying the keys {@code DefaultTransitionService} writes on one. */
    private static OutboxEntry transitionFiredEntry(UUID id, BusinessObject existing, String transitionName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", existing.id().value().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("transitionName", transitionName);
        payload.put("fromState", FROM_STATE_IRI);
        payload.put("toState", TO_STATE_IRI);
        payload.put("ontologyVersion", ONTOLOGY_VERSION);
        payload.put("machineIri", MACHINE_IRI);
        payload.put("typeIri", existing.type().iri());
        payload.put("self", Map.of());
        return new OutboxEntry(id, OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.now());
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
                    outboxPort.claimNext(Set.of(OutboxEntry.KIND_TRANSITION_FIRED), (tenantId, entry) -> Boolean.TRUE);
            if (claimed.isPresent()) {
                sleep(Duration.ofMillis(100));
            }
        } while (claimed.isPresent() && Instant.now().isBefore(deadline));
        assertThat(claimed)
                .as("no unprocessed TransitionFired outbox rows remain")
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
        assertThat(matches)
                .as("exactly one Task titled '%s' exists", expectedTitle)
                .isEqualTo(1);

        // Give any redundant (would-be duplicate) ingest a moment to land, then confirm the
        // count never rises above one.
        sleep(Duration.ofSeconds(2));
        assertThat(countTasksTitled(expectedTitle))
                .as("no duplicate Task titled '%s' appeared", expectedTitle)
                .isEqualTo(1);
    }

    private int countTasksTitled(String expectedTitle) {
        JsonNode page = fetchAsJson("/api/objects/Task?page=0&size=200");
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
