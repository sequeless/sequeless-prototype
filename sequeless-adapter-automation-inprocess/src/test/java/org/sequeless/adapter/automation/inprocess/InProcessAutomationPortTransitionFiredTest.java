package org.sequeless.adapter.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;

/**
 * Covers the applied {@code TransitionFired} path through {@link InProcessAutomationPort}:
 * {@link InProcessAutomationPort#dispatch} hands each such row to {@link
 * InProcessAutomationPort#dispatchTransitionFired}, which applies the row's actions. A {@code Log}
 * action is applied once and not again on redelivery, a {@code SetProperty} action is applied again on
 * every delivery, a {@code Webhook} action is recorded only once its delivery succeeds, and a row that
 * is unstamped or written at another ontology version is refused rather than skipped.
 */
class InProcessAutomationPortTransitionFiredTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final String MACHINE_IRI = "http://example.org/ProjectLifecycle";
    private static final String DRAFT_IRI = "http://example.org/Draft";
    private static final String ACTIVE_IRI = "http://example.org/Active";
    private static final String STATUS_IRI = "http://example.org/status";
    private static final String TRANSITION_NAME = "activate";

    @Test
    void dispatchTransitionFiredAppliesALogOnceAndRefusesAMismatchedOntologyVersion() {
        Automation automation = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                        List.<Action>of(new LogAction("project activated"))));
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
        InProcessAutomationPort port = new InProcessAutomationPort(
                recorder,
                new InProcessAutomationProperties(),
                triggerEvaluator,
                new InProcessTimerScheduler(Clock.systemUTC(), triggerEvaluator),
                tenant -> automation);
        OutboxEntry entry = transitionFired(1);

        port.dispatchTransitionFired(SCOPE, entry);
        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);

        port.dispatchTransitionFired(SCOPE, entry);
        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);

        assertThatThrownBy(() -> port.dispatchTransitionFired(SCOPE, transitionFired(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version 2");
        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);
    }

    @Test
    void dispatchTransitionFiredAppliesAStoreActionOnEveryDeliveryBecauseItIsNotRecorded() {
        Automation automation = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                        List.<Action>of(
                                new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner")))));
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        InProcessAutomationPort port = portFor(recorder, automation, new InProcessAutomationProperties());
        OutboxEntry entry = transitionFired(1);

        port.dispatchTransitionFired(SCOPE, entry);
        port.dispatchTransitionFired(SCOPE, entry);

        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(2);
    }

    @Test
    void dispatchTransitionFiredRetriesAWebhookThatFailedOnceAndRecordsItOnlyOnDelivery() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            int status = requestCount.incrementAndGet() == 1 ? 500 : 200;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/hook";
            Automation automation = new Automation(
                    new OntologyVersion(1),
                    Map.of(
                            new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                            List.<Action>of(
                                    new WebhookAction("https://example.invalid/hook", "POST", Optional.empty()))));
            RecordingActionExecutor recorder =
                    new RecordingActionExecutor(new ResolvedWebhookRequest(url, "POST", Optional.empty()));
            InProcessAutomationProperties properties = new InProcessAutomationProperties();
            properties.setRetryAttempts(1);
            InProcessAutomationPort port = portFor(recorder, automation, properties);
            OutboxEntry entry = transitionFired(1);

            assertThatThrownBy(() -> port.dispatchTransitionFired(SCOPE, entry))
                    .isInstanceOf(WebhookDeliveryException.class);
            port.dispatchTransitionFired(SCOPE, entry);
            port.dispatchTransitionFired(SCOPE, entry);

            // The failed first delivery was not recorded, so the second call delivered again; the
            // third call found the action recorded and neither resolved nor delivered it.
            assertThat(requestCount.get()).isEqualTo(2);
            assertThat(recorder.countOf(RecordingActionExecutor.METHOD_RESOLVE_WEBHOOK_ACTION))
                    .isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dispatchAppliesTheSingleActionOfAStampedTransitionFiredRowOnce() {
        Automation automation = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                        List.<Action>of(new LogAction("project activated"))));
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        InProcessAutomationPort port = portFor(recorder, automation, new InProcessAutomationProperties());

        port.dispatch(SCOPE, transitionFired(1));

        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);
    }

    @Test
    void dispatchTransitionFiredRefusesARowWithNoOntologyVersionStampAndNamesTheRow() {
        Automation automation = new Automation(new OntologyVersion(1), Map.of());
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        InProcessAutomationPort port = portFor(recorder, automation, new InProcessAutomationProperties());
        OutboxEntry unstamped = unstampedTransitionFired();

        assertThatThrownBy(() -> port.dispatchTransitionFired(SCOPE, unstamped))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(unstamped.id().toString());
        assertThat(recorder.calls()).isEmpty();
    }

    private static InProcessAutomationPort portFor(
            RecordingActionExecutor recorder, Automation automation, InProcessAutomationProperties properties) {
        RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
        return new InProcessAutomationPort(
                recorder,
                properties,
                triggerEvaluator,
                new InProcessTimerScheduler(Clock.systemUTC(), triggerEvaluator),
                tenant -> automation);
    }

    /** The row {@link #transitionFired(int)} builds, minus the {@code ontologyVersion} stamp. */
    private static OutboxEntry unstampedTransitionFired() {
        Map<String, Object> payload = new HashMap<>(transitionFired(1).payload());
        payload.remove("ontologyVersion");
        return new OutboxEntry(
                UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** A {@code TransitionFired} row carrying the keys {@code DefaultTransitionService} writes on one. */
    private static OutboxEntry transitionFired(int ontologyVersion) {
        Map<String, Object> payload = Map.of(
                "objectId",
                UUID.randomUUID().toString(),
                "tenantId",
                SCOPE.tenantId().value(),
                "principalId",
                SCOPE.principal().id(),
                "transitionName",
                TRANSITION_NAME,
                "fromState",
                DRAFT_IRI,
                "toState",
                ACTIVE_IRI,
                "ontologyVersion",
                ontologyVersion,
                "self",
                Map.of(),
                "typeIri",
                "http://example.org/Project",
                "machineIri",
                MACHINE_IRI);
        return new OutboxEntry(
                UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
