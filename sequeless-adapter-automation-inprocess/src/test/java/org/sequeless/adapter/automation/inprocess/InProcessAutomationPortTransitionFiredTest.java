package org.sequeless.adapter.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
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
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;

/**
 * Covers the applied {@code TransitionFired} path through {@link InProcessAutomationPort}:
 * {@link InProcessAutomationPort#ingest} hands each such event to {@code applyTransitionFired}, which
 * starts the event's timers and then applies its actions. A {@code Log} action is applied once and not
 * again on redelivery, a {@code SetProperty} action is applied again on every delivery, a {@code
 * Webhook} action is recorded only once its delivery succeeds,
 * and a row that is unstamped or written at another ontology version is refused rather than skipped.
 * A row starts its timer even with no actions or when an action throws, and a refused row starts none.
 */
class InProcessAutomationPortTransitionFiredTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final String MACHINE_IRI = "http://example.org/ProjectLifecycle";
    private static final String DRAFT_IRI = "http://example.org/Draft";
    private static final String ACTIVE_IRI = "http://example.org/Active";
    private static final String STATUS_IRI = "http://example.org/status";
    private static final String TRANSITION_NAME = "activate";
    private static final String TIMER_TRANSITION_NAME = "expireHold";
    private static final Duration TIMER_AFTER = Duration.ofMinutes(30);
    private static final Instant CLOCK_START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void ingestTransitionFiredAppliesALogOnceAndRefusesAMismatchedOntologyVersion() {
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
        TransitionFiredEvent entry = transitionFired(1);

        port.ingest(SCOPE, entry);
        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);

        port.ingest(SCOPE, entry);
        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);

        assertThatThrownBy(() -> port.ingest(SCOPE, transitionFired(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version 2");
        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);
    }

    @Test
    void ingestTransitionFiredAppliesAStoreActionOnEveryDeliveryBecauseItIsNotRecorded() {
        Automation automation = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                        List.<Action>of(
                                new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner")))));
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        InProcessAutomationPort port = portFor(recorder, automation, new InProcessAutomationProperties());
        TransitionFiredEvent entry = transitionFired(1);

        port.ingest(SCOPE, entry);
        port.ingest(SCOPE, entry);

        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(2);
    }

    @Test
    void ingestTransitionFiredRetriesAWebhookThatFailedOnceAndRecordsItOnlyOnDelivery() throws IOException {
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
            TransitionFiredEvent entry = transitionFired(1);

            assertThatThrownBy(() -> port.ingest(SCOPE, entry)).isInstanceOf(WebhookDeliveryException.class);
            port.ingest(SCOPE, entry);
            port.ingest(SCOPE, entry);

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
    void ingestAppliesTheSingleActionOfAStampedTransitionFiredRowOnce() {
        Automation automation = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                        List.<Action>of(new LogAction("project activated"))));
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        InProcessAutomationPort port = portFor(recorder, automation, new InProcessAutomationProperties());

        port.ingest(SCOPE, transitionFired(1));

        assertThat(recorder.countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);
    }

    @Test
    void ingestTransitionFiredRefusesARowWithNoOntologyVersionStampAndNamesTheEvent() {
        Automation automation = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                        List.<Action>of(new LogAction("project activated"))));
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        InProcessAutomationPort port = portFor(recorder, automation, new InProcessAutomationProperties());
        TransitionFiredEvent unstamped = unstampedTransitionFired();

        assertThatThrownBy(() -> port.ingest(SCOPE, unstamped))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(unstamped.id().toString());
        assertThat(recorder.calls()).isEmpty();
    }

    @Test
    void ingestTransitionFiredRefusesANonNumberStampAndNamesTheEvent() {
        Automation automation = new Automation(
                new OntologyVersion(1),
                Map.of(
                        new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                        List.<Action>of(new LogAction("project activated"))));
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        InProcessAutomationPort port = portFor(recorder, automation, new InProcessAutomationProperties());
        TransitionFiredEvent textStamped = transitionFiredWithStamp("1");

        assertThatThrownBy(() -> port.ingest(SCOPE, textStamped))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(textStamped.id().toString());
        assertThat(recorder.calls()).isEmpty();
    }

    /**
     * A {@code TransitionFired} row with no actions still starts its timer: the timer fires once the
     * clock passes its duration, and no action runs.
     */
    @Test
    void transitionFiredWithNoActionsStartsItsTimerAndTheTimerFiresWhenDue() {
        MutableClock clock = new MutableClock(CLOCK_START);
        RecordingActionExecutor recorder = new RecordingActionExecutor();
        RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
        triggerEvaluator.setTimersStartedBy(List.of(expireHoldTimer()));
        InProcessTimerScheduler timerScheduler = new InProcessTimerScheduler(clock, triggerEvaluator);
        InProcessAutomationPort port = new InProcessAutomationPort(
                recorder,
                new InProcessAutomationProperties(),
                triggerEvaluator,
                timerScheduler,
                tenant -> new Automation(new OntologyVersion(1), Map.of()));

        port.ingest(SCOPE, transitionFired(1));
        assertThat(recorder.calls()).isEmpty();

        clock.advance(TIMER_AFTER);
        timerScheduler.sweep();

        assertThat(triggerEvaluator.countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(1);
    }

    /**
     * A {@code TransitionFired} row whose action throws still starts its timer, because the timers start
     * before the action loop. The webhook delivery fails here, so {@code ingest} throws.
     */
    @Test
    void transitionFiredWhoseActionThrowsStillStartsItsTimer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/hook";
            MutableClock clock = new MutableClock(CLOCK_START);
            RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
            triggerEvaluator.setTimersStartedBy(List.of(expireHoldTimer()));
            InProcessTimerScheduler timerScheduler = new InProcessTimerScheduler(clock, triggerEvaluator);
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
            InProcessAutomationPort port = new InProcessAutomationPort(
                    recorder, properties, triggerEvaluator, timerScheduler, tenant -> automation);

            assertThatThrownBy(() -> port.ingest(SCOPE, transitionFired(1)))
                    .isInstanceOf(WebhookDeliveryException.class);

            clock.advance(TIMER_AFTER);
            timerScheduler.sweep();

            assertThat(triggerEvaluator.countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                    .isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    /**
     * A {@code TransitionFired} row redelivered after the clock has moved on keeps its timer's first due
     * instant. The timer is due 30 minutes after the first ingest. The redelivery, 10 minutes later,
     * must not move the due instant to 40 minutes, so the timer fires at 30 minutes and not after.
     */
    @Test
    void reingestAfterTheClockAdvancesFiresTheTimerAtItsFirstDueInstant() {
        MutableClock clock = new MutableClock(CLOCK_START);
        RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
        triggerEvaluator.setTimersStartedBy(List.of(expireHoldTimer()));
        InProcessTimerScheduler timerScheduler = new InProcessTimerScheduler(clock, triggerEvaluator);
        InProcessAutomationPort port = new InProcessAutomationPort(
                new RecordingActionExecutor(),
                new InProcessAutomationProperties(),
                triggerEvaluator,
                timerScheduler,
                tenant -> new Automation(new OntologyVersion(1), Map.of()));
        TransitionFiredEvent entry = transitionFired(1);

        port.ingest(SCOPE, entry);
        clock.advance(Duration.ofMinutes(10));
        port.ingest(SCOPE, entry);
        clock.advance(Duration.ofMinutes(10));
        timerScheduler.sweep();
        assertThat(triggerEvaluator.countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(0);

        clock.advance(Duration.ofMinutes(10));
        timerScheduler.sweep();
        assertThat(triggerEvaluator.countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(1);
    }

    /**
     * A {@code TransitionFired} row whose stamp does not match the automation is refused before any timer
     * starts, so the timer the row would have started never fires.
     */
    @Test
    void transitionFiredWithAMismatchedStampThrowsAndStartsNoTimer() {
        MutableClock clock = new MutableClock(CLOCK_START);
        RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
        triggerEvaluator.setTimersStartedBy(List.of(expireHoldTimer()));
        InProcessTimerScheduler timerScheduler = new InProcessTimerScheduler(clock, triggerEvaluator);
        InProcessAutomationPort port = new InProcessAutomationPort(
                new RecordingActionExecutor(),
                new InProcessAutomationProperties(),
                triggerEvaluator,
                timerScheduler,
                tenant -> new Automation(new OntologyVersion(1), Map.of()));

        assertThatThrownBy(() -> port.ingest(SCOPE, transitionFired(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version 2");

        clock.advance(TIMER_AFTER);
        timerScheduler.sweep();
        assertThat(triggerEvaluator.countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(0);
    }

    /**
     * The {@code expireHold} timer a row moving the object into {@link #ACTIVE_IRI} starts, due after
     * {@link #TIMER_AFTER}.
     */
    private static ResolvedTimer expireHoldTimer() {
        String timerKey = OutboxEntry.timerKey(ObjectId.random(), ACTIVE_IRI, TIMER_TRANSITION_NAME);
        return new ResolvedTimer(timerKey, TIMER_AFTER, ACTIVE_IRI, TIMER_TRANSITION_NAME);
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

    /** The row {@link #transitionFired(int)} builds, with {@code stamp} in place of its {@code ontologyVersion}. */
    private static TransitionFiredEvent transitionFiredWithStamp(Object stamp) {
        Map<String, Object> payload = new HashMap<>(transitionFired(1).payload());
        payload.put("ontologyVersion", stamp);
        return new TransitionFiredEvent(UUID.randomUUID(), payload, Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** The row {@link #transitionFired(int)} builds, minus the {@code ontologyVersion} stamp. */
    private static TransitionFiredEvent unstampedTransitionFired() {
        Map<String, Object> payload = new HashMap<>(transitionFired(1).payload());
        payload.remove("ontologyVersion");
        return new TransitionFiredEvent(UUID.randomUUID(), payload, Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** A {@code TransitionFired} row carrying the keys {@code DefaultTransitionService} writes on one. */
    private static TransitionFiredEvent transitionFired(int ontologyVersion) {
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
        return new TransitionFiredEvent(UUID.randomUUID(), payload, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
