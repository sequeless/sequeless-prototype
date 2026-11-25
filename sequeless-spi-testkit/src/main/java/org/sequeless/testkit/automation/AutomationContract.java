package org.sequeless.testkit.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The mechanical form of the behavioural contract documented on {@link AutomationPort}'s
 * interface-level javadoc. Every {@link AutomationPort} implementation — adapter or test double —
 * is expected to satisfy every clause of that javadoc, and this class exercises each clause once,
 * against whatever port {@link #port()} supplies and whatever spy {@link #recordingExecutor()}
 * supplies (the same {@link RecordingActionExecutor} instance the port under test was wired with).
 *
 * <p>To use this contract, extend it from a test class in your own module:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends AutomationContract {
 *     private final RecordingActionExecutor recorder = new RecordingActionExecutor();
 *     private final AutomationPort port = new MyAdapter(recorder);
 *
 *     protected AutomationPort port() {
 *         return port;
 *     }
 *
 *     protected RecordingActionExecutor recordingExecutor() {
 *         return recorder;
 *     }
 * }
 * }</pre>
 *
 * <p><b>What this contract deliberately does not check.</b> {@link
 * #ingestRoutesWebhookToResolveWebhook()} asserts only that {@link
 * org.sequeless.spi.automation.ActionExecutor#resolveWebhookAction} was invoked — it makes no assertion
 * about any HTTP call an adapter might go on to make with the resolved request, since HTTP retry
 * policy genuinely differs between adapters (the in-process adapter's fixed retry loop vs. a
 * Temporal activity's {@code RetryOptions}) and is tested separately, per adapter.
 */
public abstract class AutomationContract {

    /** The scope every test in this contract ingests under; also reused by subclasses' own tests. */
    protected static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));

    private static final String MACHINE_IRI = "http://example.org/ProjectLifecycle";
    private static final String DRAFT_IRI = "http://example.org/Draft";
    private static final String ACTIVE_IRI = "http://example.org/Active";
    private static final String STATUS_IRI = "http://example.org/status";
    private static final String TRANSITION_NAME = "activate";

    /** The timer transition that waits in {@link #ACTIVE_IRI}; the timer the timer contracts start. */
    private static final String TIMER_TRANSITION_NAME = "expireHold";

    /** The transition every {@code TransitionFired} event this contract ingests names. */
    private static final TransitionRef TRANSITION = new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME);

    /**
     * @return the {@link AutomationPort} implementation under test, wired with {@link
     *     #recordingExecutor()}'s spy; invoked fresh for every {@code @Test} method, so
     *     implementors may return a new instance each time or a shared one, whichever suits the
     *     port under test
     */
    protected abstract AutomationPort port();

    /**
     * @return the same {@link RecordingActionExecutor} spy {@link #port()}'s implementation was
     *     constructed with, so this contract can observe which method {@code ingest} routed to
     */
    protected abstract RecordingActionExecutor recordingExecutor();

    /**
     * @return the same {@link RecordingTriggerEvaluator} spy {@link #port()}'s implementation was
     *     constructed with, so this contract can observe which {@link TriggerEvaluator} method
     *     {@code ingest} routed an object-lifecycle event or a timer entry to
     */
    protected abstract RecordingTriggerEvaluator recordingTriggerEvaluator();

    /**
     * Advances this port's notion of "now" by {@code by} and, before returning, synchronously
     * sweeps and fires every due timer as if that much wall-clock time had actually elapsed — an
     * ACTION each concrete adapter performs differently (the in-process adapter advances a mutable
     * {@link java.time.Clock} and sweeps its scheduler; the Temporal adapter skips {@code
     * TestWorkflowEnvironment}'s time), never a plain getter.
     *
     * <p>This method must be synchronous in the strongest sense: by the time it returns, every
     * {@link TriggerEvaluator#onTimerElapsed} call that a timer due by the new "now" would trigger
     * must already have happened. That determinism — no sleeping, no polling, no {@code
     * Awaitility} — is what lets {@link #timerFiresOnlyAfterAdvanceTime()} and its siblings assert
     * directly on {@link #recordingTriggerEvaluator()} immediately after calling this method.
     *
     * @param by the amount of time to advance by; must not be {@code null}
     */
    protected abstract void advanceTime(Duration by);

    /**
     * Makes the automation source this port reads from serve one {@link Automation} at ontology version
     * 1, holding {@code actionsByTransition} and nothing else. A {@code TransitionFired} event this contract
     * builds is stamped with version 1, so {@code ingest} applies these actions. Every test that
     * ingests such an event calls this first.
     *
     * @param actionsByTransition the actions each transition holds, in order; must not be {@code null}
     */
    protected abstract void serveAutomationWithActions(Map<TransitionRef, List<Action>> actionsByTransition);

    @Test
    void ingestRoutesSetPropertyActionToApplyAction() {
        serveAutomationWithActions(Map.of(
                TRANSITION,
                List.<Action>of(new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner")))));

        port().ingest(SCOPE, transitionFiredEvent());

        RecordingActionExecutor.Call call = onlyCallOf(RecordingActionExecutor.METHOD_APPLY_ACTION);
        assertThat(call.action()).isInstanceOf(SetPropertyAction.class);
    }

    @Test
    void ingestRoutesCreateObjectActionToApplyAction() {
        serveAutomationWithActions(
                Map.of(TRANSITION, List.<Action>of(new CreateObjectAction("http://example.org/Task", List.of()))));

        port().ingest(SCOPE, transitionFiredEvent());

        RecordingActionExecutor.Call call = onlyCallOf(RecordingActionExecutor.METHOD_APPLY_ACTION);
        assertThat(call.action()).isInstanceOf(CreateObjectAction.class);
    }

    @Test
    void ingestRoutesWebhookToResolveWebhook() {
        serveAutomationWithActions(Map.of(
                TRANSITION,
                List.<Action>of(new WebhookAction("http://example.invalid/hook", "POST", Optional.empty()))));

        port().ingest(SCOPE, transitionFiredEvent());

        RecordingActionExecutor.Call call = onlyCallOf(RecordingActionExecutor.METHOD_RESOLVE_WEBHOOK_ACTION);
        assertThat(call.action()).isInstanceOf(WebhookAction.class);
    }

    @Test
    void ingestRoutesLogActionToApplyAction() {
        serveAutomationWithActions(Map.of(TRANSITION, List.<Action>of(new LogAction("hello"))));

        port().ingest(SCOPE, transitionFiredEvent());

        RecordingActionExecutor.Call call = onlyCallOf(RecordingActionExecutor.METHOD_APPLY_ACTION);
        assertThat(call.action()).isInstanceOf(LogAction.class);
    }

    @Test
    void reingestWithSameEventIdIsIdempotent() {
        serveAutomationWithActions(Map.of(TRANSITION, List.<Action>of(new LogAction("hello"))));
        TransitionFiredEvent event = transitionFiredEvent();

        port().ingest(SCOPE, event);
        port().ingest(SCOPE, event);

        assertThat(recordingExecutor().countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);
    }

    @Test
    void ingestRejectsNullScope() {
        TransitionFiredEvent event = transitionFiredEvent();
        assertThatNullPointerException().isThrownBy(() -> port().ingest(null, event));
    }

    @Test
    void ingestRejectsNullEntry() {
        assertThatNullPointerException().isThrownBy(() -> port().ingest(SCOPE, null));
    }

    /**
     * A {@code TransitionFired} event with no {@code ontologyVersion} stamp is rejected: {@code ingest}
     * throws {@link IllegalStateException} and starts nothing for that event.
     */
    @Test
    void ingestRejectsTransitionFiredWithoutOntologyVersionStamp() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("transitionName", "activate");
        payload.put("fromState", "Draft");
        payload.put("toState", "Active");
        TransitionFiredEvent event = new TransitionFiredEvent(UUID.randomUUID(), payload, Instant.now());

        assertThatIllegalStateException().isThrownBy(() -> port().ingest(SCOPE, event));
        assertThat(recordingExecutor().calls()).isEmpty();
    }

    @Test
    void ingestRoutesObjectChangedToOnChange() {
        ObjectChangedEvent event = objectChangeEvent(ObjectChangedEvent.Change.UPDATED);

        port().ingest(SCOPE, event);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_CHANGE))
                .isEqualTo(1);
    }

    @Test
    void ingestRoutesObjectDeletedToOnChangeToo() {
        ObjectChangedEvent event = objectDeletedEvent();

        port().ingest(SCOPE, event);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_CHANGE))
                .isEqualTo(1);
    }

    /**
     * A {@code TransitionFired} event starts its timer: the timer fires only once {@link #advanceTime} has
     * moved the clock past the timer's duration, not on ingest.
     */
    @Test
    void timerFiresOnlyAfterAdvanceTime() {
        Duration after = Duration.ofMinutes(30);
        UUID objectId = UUID.randomUUID();
        serveAutomationWithActions(Map.of());
        recordingTriggerEvaluator().setTimersStartedBy(List.of(expireHoldTimer(objectId, after)));

        port().ingest(SCOPE, transitionFiredEvent(objectId));

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(0);

        advanceTime(after);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(1);
    }

    /** Ingesting the same {@code TransitionFired} event twice starts its timer once, so it fires once. */
    @Test
    void reingestOfATransitionFiredEventIsIdempotentForItsTimer() {
        Duration after = Duration.ofMinutes(30);
        UUID objectId = UUID.randomUUID();
        serveAutomationWithActions(Map.of());
        recordingTriggerEvaluator().setTimersStartedBy(List.of(expireHoldTimer(objectId, after)));
        TransitionFiredEvent event = transitionFiredEvent(objectId);

        port().ingest(SCOPE, event);
        port().ingest(SCOPE, event);
        advanceTime(after);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(1);
    }

    /**
     * A {@code TransitionFired} event redelivered after the clock has moved on keeps the due instant of its
     * first delivery. The timer is due 30 minutes after the first ingest, so the second ingest, made
     * 10 minutes later, must not push the firing to 40 minutes.
     */
    @Test
    void reingestAfterTheClockAdvancesKeepsTheFirstDueInstant() {
        Duration after = Duration.ofMinutes(30);
        UUID objectId = UUID.randomUUID();
        serveAutomationWithActions(Map.of());
        recordingTriggerEvaluator().setTimersStartedBy(List.of(expireHoldTimer(objectId, after)));
        TransitionFiredEvent event = transitionFiredEvent(objectId);

        port().ingest(SCOPE, event);
        advanceTime(Duration.ofMinutes(10));
        port().ingest(SCOPE, event);
        advanceTime(Duration.ofMinutes(20));

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(1);
    }

    /**
     * Builds an {@link ObjectChangedEvent} for {@code change} whose payload has the shape {@code
     * DefaultBusinessObjectService}/{@code DefaultActionExecutor}'s shared {@code outboxPayload} helper
     * produces: {@code objectId}, {@code version}, {@code typeIri} — no {@code tenantId}/{@code
     * principalId}, unlike a {@code TransitionFired} event.
     */
    protected static ObjectChangedEvent objectChangeEvent(ObjectChangedEvent.Change change) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("version", 1);
        payload.put("typeIri", "http://example.org/Task");
        return new ObjectChangedEvent(UUID.randomUUID(), change, payload, Instant.now());
    }

    /**
     * Builds a {@link ObjectChangedEvent} with {@link ObjectChangedEvent.Change#DELETED}: the same shape as
     * {@link #objectChangeEvent(ObjectChangedEvent.Change)} plus a {@code properties} snapshot map, since a
     * soft-deleted object can no longer be read back via {@code ObjectStorePort.find}. This contract's
     * recording doubles never decode payload contents, and {@code ingest} routes by the sealed event type
     * alone, so an empty snapshot is sufficient here — a real {@code DerivationRecomputer}/{@code
     * TriggerEvaluator} implementation would need real, IRI-keyed, {@code PayloadValueCodec}-encoded
     * entries, but this contract only proves routing, not decoding.
     */
    protected static ObjectChangedEvent objectDeletedEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("version", 1);
        payload.put("typeIri", "http://example.org/Task");
        payload.put("properties", Map.of());
        return new ObjectChangedEvent(UUID.randomUUID(), ObjectChangedEvent.Change.DELETED, payload, Instant.now());
    }

    /**
     * Builds the {@link ResolvedTimer} that a {@code TransitionFired} event moving {@code objectId} into
     * {@link #ACTIVE_IRI} starts: the {@code expireHold} timer, waiting in {@link #ACTIVE_IRI} for {@code
     * after}. The timer key is built by {@link OutboxEntry#timerKey}, as an adapter builds it.
     */
    private static ResolvedTimer expireHoldTimer(UUID objectId, Duration after) {
        String timerKey = OutboxEntry.timerKey(new ObjectId(objectId), ACTIVE_IRI, TIMER_TRANSITION_NAME);
        return new ResolvedTimer(timerKey, after, ACTIVE_IRI, TIMER_TRANSITION_NAME);
    }

    /**
     * Builds a {@code TransitionFired} {@link TransitionFiredEvent} for a fresh object; see {@link
     * #transitionFiredEvent(UUID)}.
     */
    private static TransitionFiredEvent transitionFiredEvent() {
        return transitionFiredEvent(UUID.randomUUID());
    }

    /**
     * Builds a {@code TransitionFired} {@link TransitionFiredEvent} for {@link #TRANSITION} on {@code objectId},
     * carrying the keys {@code DefaultTransitionService} writes on one and stamped with ontology version 1.
     */
    private static TransitionFiredEvent transitionFiredEvent(UUID objectId) {
        Map<String, Object> payload = Map.of(
                "objectId",
                objectId.toString(),
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
                1,
                "self",
                Map.of(),
                "typeIri",
                "http://example.org/Project",
                "machineIri",
                MACHINE_IRI);
        return new TransitionFiredEvent(UUID.randomUUID(), payload, Instant.now());
    }

    /**
     * Asserts that exactly one call was recorded and that it has {@code method}, then returns it.
     *
     * @param method one of the {@code METHOD_*} constants of {@link RecordingActionExecutor}
     */
    private RecordingActionExecutor.Call onlyCallOf(String method) {
        List<RecordingActionExecutor.Call> calls = recordingExecutor().calls();
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).method()).isEqualTo(method);
        return calls.get(0);
    }
}
