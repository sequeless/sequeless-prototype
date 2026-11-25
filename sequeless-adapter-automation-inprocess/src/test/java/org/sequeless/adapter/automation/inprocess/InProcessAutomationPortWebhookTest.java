package org.sequeless.adapter.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;

/**
 * In-process-specific webhook delivery tests: not part of {@link
 * org.sequeless.testkit.automation.AutomationContract}, since HTTP retry behaviour genuinely
 * differs between automation adapters (this adapter's own small fixed loop vs. a future Temporal
 * activity's {@code RetryOptions}). Uses a tiny local {@link HttpServer} rather than
 * Testcontainers or a mocking library — this codebase uses neither.
 */
class InProcessAutomationPortWebhookTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", java.util.Set.of()));
    private static final String MACHINE_IRI = "http://example.org/ProjectLifecycle";
    private static final String DRAFT_IRI = "http://example.org/Draft";
    private static final String ACTIVE_IRI = "http://example.org/Active";
    private static final String TRANSITION_NAME = "activate";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesAndSucceedsOnThirdAttempt() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        StringBuilder lastRequestBody = new StringBuilder();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            int count = requestCount.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            synchronized (lastRequestBody) {
                lastRequestBody.setLength(0);
                lastRequestBody.append(new String(body, java.nio.charset.StandardCharsets.UTF_8));
            }
            int status = count < 3 ? 500 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:" + server.getAddress().getPort() + "/hook";
        ResolvedWebhookRequest resolved = new ResolvedWebhookRequest(url, "POST", Optional.of("templated-body"));
        RecordingActionExecutor recorder = new RecordingActionExecutor(resolved);

        InProcessAutomationProperties properties = new InProcessAutomationProperties();
        properties.setRetryAttempts(3);
        properties.setRetryDelay(Duration.ofMillis(10));
        InProcessAutomationPort port = newPort(recorder, properties, url);

        TransitionFiredEvent entry = webhookEvent();
        port.ingest(SCOPE, entry);

        assertThat(requestCount.get()).isEqualTo(3);
        synchronized (lastRequestBody) {
            assertThat(lastRequestBody.toString()).isEqualTo("templated-body");
        }
    }

    @Test
    void givesUpAfterExactlyRetryAttemptsAndThrows() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:" + server.getAddress().getPort() + "/hook";
        ResolvedWebhookRequest resolved = new ResolvedWebhookRequest(url, "POST", Optional.empty());
        RecordingActionExecutor recorder = new RecordingActionExecutor(resolved);

        InProcessAutomationProperties properties = new InProcessAutomationProperties();
        properties.setRetryAttempts(3);
        properties.setRetryDelay(Duration.ofMillis(10));
        InProcessAutomationPort port = newPort(recorder, properties, url);

        TransitionFiredEvent entry = webhookEvent();

        assertThatThrownBy(() -> port.ingest(SCOPE, entry)).isInstanceOf(WebhookDeliveryException.class);
        assertThat(requestCount.get()).isEqualTo(3);
    }

    /**
     * Builds the port over an automation whose single transition holds one {@link WebhookAction} aimed
     * at {@code url}, the test's local HTTP server. The recorder's own resolved request carries the same
     * URL, so the delivery goes to that server.
     */
    private static InProcessAutomationPort newPort(
            RecordingActionExecutor recorder, InProcessAutomationProperties properties, String url) {
        RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
        Automation automation = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                        List.<Action>of(new WebhookAction(url, "POST", Optional.empty()))));
        return new InProcessAutomationPort(
                recorder,
                properties,
                triggerEvaluator,
                new InProcessTimerScheduler(java.time.Clock.systemUTC(), triggerEvaluator),
                tenant -> automation);
    }

    /** A {@code TransitionFired} row carrying the keys {@code DefaultTransitionService} writes on one. */
    private static TransitionFiredEvent webhookEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("transitionName", TRANSITION_NAME);
        payload.put("fromState", DRAFT_IRI);
        payload.put("toState", ACTIVE_IRI);
        payload.put("ontologyVersion", 1);
        payload.put("self", Map.of());
        payload.put("typeIri", "http://example.org/Project");
        payload.put("machineIri", MACHINE_IRI);
        return new TransitionFiredEvent(UUID.randomUUID(), payload, Instant.now());
    }
}
