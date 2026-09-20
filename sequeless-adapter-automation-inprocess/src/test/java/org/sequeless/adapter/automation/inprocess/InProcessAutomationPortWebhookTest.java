package org.sequeless.adapter.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.testkit.automation.RecordingActionExecutor;

/**
 * In-process-specific webhook delivery tests: not part of {@link
 * org.sequeless.testkit.automation.AutomationContract}, since HTTP retry behaviour genuinely
 * differs between automation adapters (this adapter's own small fixed loop vs. a future Temporal
 * activity's {@code RetryOptions}). Uses a tiny local {@link HttpServer} rather than
 * Testcontainers or a mocking library — this codebase uses neither.
 */
class InProcessAutomationPortWebhookTest {

    private static final Scope SCOPE =
        new Scope(new TenantId("tenant-1"), new Principal("user-1", "User One", java.util.Set.of()));

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
        server.createContext(
            "/hook",
            exchange -> {
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
        InProcessAutomationPort port = new InProcessAutomationPort(recorder, properties);

        OutboxEntry entry = webhookEntry();
        port.dispatch(SCOPE, entry);

        assertThat(requestCount.get()).isEqualTo(3);
        synchronized (lastRequestBody) {
            assertThat(lastRequestBody.toString()).isEqualTo("templated-body");
        }
    }

    @Test
    void givesUpAfterExactlyRetryAttemptsAndThrows() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
            "/hook",
            exchange -> {
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
        InProcessAutomationPort port = new InProcessAutomationPort(recorder, properties);

        OutboxEntry entry = webhookEntry();

        assertThatThrownBy(() -> port.dispatch(SCOPE, entry))
            .isInstanceOf(WebhookDeliveryException.class);
        assertThat(requestCount.get()).isEqualTo(3);
    }

    private static OutboxEntry webhookEntry() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("transitionName", "activate");
        payload.put("actionIndex", 0);
        payload.put("typeIri", "http://example.org/Project");
        payload.put("state", "Active");
        payload.put("self", Map.of());
        payload.put("actionKind", "Webhook");
        payload.put("url", "http://example.invalid/hook");
        payload.put("method", "POST");
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, Instant.now());
    }
}
