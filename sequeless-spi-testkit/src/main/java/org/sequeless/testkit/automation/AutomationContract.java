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
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.DerivationRecomputer;
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
 * #dispatchRoutesWebhookToResolveWebhook()} asserts only that {@link
 * org.sequeless.spi.automation.ActionExecutor#resolveWebhookAction} was invoked — it makes no assertion
 * about any HTTP call an adapter might go on to make with the resolved request, since HTTP retry
 * policy genuinely differs between adapters (the in-process adapter's fixed retry loop vs. a
 * Temporal activity's {@code RetryOptions}) and is tested separately, per adapter.
 */
public abstract class AutomationContract {

    /** The scope every test in this contract dispatches under; also reused by subclasses' own tests. */
    protected static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));

    private static final String MACHINE_IRI = "http://example.org/ProjectLifecycle";
    private static final String DRAFT_IRI = "http://example.org/Draft";
    private static final String ACTIVE_IRI = "http://example.org/Active";
    private static final String STATUS_IRI = "http://example.org/status";
    private static final String TRANSITION_NAME = "activate";

    /** The transition every {@code TransitionFired} row this contract dispatches names. */
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
     *     constructed with, so this contract can observe which method {@code dispatch} routed to
     */
    protected abstract RecordingActionExecutor recordingExecutor();

    /**
     * @return the same {@link RecordingTriggerEvaluator} spy {@link #port()}'s implementation was
     *     constructed with, so this contract can observe which {@link TriggerEvaluator} method
     *     {@code dispatch} routed an object-lifecycle, timer, or signal entry to
     */
    protected abstract RecordingTriggerEvaluator recordingTriggerEvaluator();

    /**
     * @return the same {@link RecordingDerivationRecomputer} spy {@link #port()}'s implementation
     *     was constructed with, so this contract can observe whether {@code dispatch} called {@link
     *     DerivationRecomputer#recompute}. An adapter whose {@code dispatch} does not recompute may
     *     return a spy that nothing is wired to; the contract then sees no calls
     */
    protected abstract RecordingDerivationRecomputer recordingDerivationRecomputer();

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
     * 1, holding {@code actionsByTransition} and nothing else. A {@code TransitionFired} row this contract
     * builds is stamped with version 1, so {@code dispatch} applies these actions. Every test that
     * dispatches such a row calls this first.
     *
     * @param actionsByTransition the actions each transition holds, in order; must not be {@code null}
     */
    protected abstract void serveAutomationWithActions(Map<TransitionRef, List<Action>> actionsByTransition);

    @Test
    void dispatchRoutesSetPropertyActionToApplyAction() {
        serveAutomationWithActions(Map.of(
                TRANSITION,
                List.<Action>of(new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner")))));

        port().dispatch(SCOPE, transitionFiredEntry());

        RecordingActionExecutor.Call call = onlyCallOf(RecordingActionExecutor.METHOD_APPLY_ACTION);
        assertThat(call.action()).isInstanceOf(SetPropertyAction.class);
    }

    @Test
    void dispatchRoutesCreateObjectActionToApplyAction() {
        serveAutomationWithActions(
                Map.of(TRANSITION, List.<Action>of(new CreateObjectAction("http://example.org/Task", List.of()))));

        port().dispatch(SCOPE, transitionFiredEntry());

        RecordingActionExecutor.Call call = onlyCallOf(RecordingActionExecutor.METHOD_APPLY_ACTION);
        assertThat(call.action()).isInstanceOf(CreateObjectAction.class);
    }

    @Test
    void dispatchRoutesWebhookToResolveWebhook() {
        serveAutomationWithActions(Map.of(
                TRANSITION,
                List.<Action>of(new WebhookAction("http://example.invalid/hook", "POST", Optional.empty()))));

        port().dispatch(SCOPE, transitionFiredEntry());

        RecordingActionExecutor.Call call = onlyCallOf(RecordingActionExecutor.METHOD_RESOLVE_WEBHOOK_ACTION);
        assertThat(call.action()).isInstanceOf(WebhookAction.class);
    }

    @Test
    void dispatchRoutesLogActionToApplyAction() {
        serveAutomationWithActions(Map.of(TRANSITION, List.<Action>of(new LogAction("hello"))));

        port().dispatch(SCOPE, transitionFiredEntry());

        RecordingActionExecutor.Call call = onlyCallOf(RecordingActionExecutor.METHOD_APPLY_ACTION);
        assertThat(call.action()).isInstanceOf(LogAction.class);
    }

    @Test
    void redispatchWithSameEntryIdIsIdempotent() {
        serveAutomationWithActions(Map.of(TRANSITION, List.<Action>of(new LogAction("hello"))));
        OutboxEntry entry = transitionFiredEntry();

        port().dispatch(SCOPE, entry);
        port().dispatch(SCOPE, entry);

        assertThat(recordingExecutor().countOf(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .isEqualTo(1);
    }

    @Test
    void dispatchRejectsNullScope() {
        OutboxEntry entry = transitionFiredEntry();
        assertThatNullPointerException().isThrownBy(() -> port().dispatch(null, entry));
    }

    @Test
    void dispatchRejectsNullEntry() {
        assertThatNullPointerException().isThrownBy(() -> port().dispatch(SCOPE, null));
    }

    /**
     * A {@code TransitionFired} row with no {@code ontologyVersion} stamp is rejected: {@code dispatch}
     * throws {@link IllegalStateException} and starts nothing for that row.
     */
    @Test
    void dispatchRejectsTransitionFiredWithoutOntologyVersionStamp() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("transitionName", "activate");
        payload.put("fromState", "Draft");
        payload.put("toState", "Active");
        OutboxEntry entry =
                new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.now());

        assertThatIllegalStateException().isThrownBy(() -> port().dispatch(SCOPE, entry));
    }

    @Test
    void dispatchRoutesObjectLifecycleEventToOnChange() {
        OutboxEntry entry = objectChangeEntry(OutboxEntry.KIND_OBJECT_UPDATED);

        port().dispatch(SCOPE, entry);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_CHANGE))
                .isEqualTo(1);
    }

    @Test
    void dispatchRoutesObjectDeletedToOnChangeToo() {
        OutboxEntry entry = objectDeletedEntry();

        port().dispatch(SCOPE, entry);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_CHANGE))
                .isEqualTo(1);
    }

    @Test
    void timerFiresOnlyAfterAdvanceTime() {
        Duration after = Duration.ofMinutes(30);
        UUID objectId = UUID.randomUUID();
        OutboxEntry entry =
                timerScheduledEntry(objectId, objectId + "|OnHold|expireHold", "OnHold", "expireHold", after);

        port().dispatch(SCOPE, entry);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(0);

        advanceTime(after);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(1);
    }

    @Test
    void timerCancelledBeforeExpiryPreventsFire() {
        Duration after = Duration.ofMinutes(30);
        UUID objectId = UUID.randomUUID();
        String timerKey = objectId + "|OnHold|expireHold";
        OutboxEntry scheduled = timerScheduledEntry(objectId, timerKey, "OnHold", "expireHold", after);
        OutboxEntry cancelled = timerCancelledEntry(objectId, timerKey);

        port().dispatch(SCOPE, scheduled);
        port().dispatch(SCOPE, cancelled);
        advanceTime(after);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(0);
    }

    @Test
    void dispatchRoutesSignalReceivedToOnSignal() {
        OutboxEntry entry = signalReceivedEntry("reopen");

        port().dispatch(SCOPE, entry);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_SIGNAL))
                .isEqualTo(1);
    }

    @Test
    void redispatchOfATimerScheduledEntryIsIdempotent() {
        Duration after = Duration.ofMinutes(30);
        UUID objectId = UUID.randomUUID();
        OutboxEntry entry =
                timerScheduledEntry(objectId, objectId + "|OnHold|expireHold", "OnHold", "expireHold", after);

        port().dispatch(SCOPE, entry);
        port().dispatch(SCOPE, entry);
        advanceTime(after);

        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
                .isEqualTo(1);
    }

    /**
     * Builds a valid {@code ObjectCreated}/{@code ObjectUpdated} {@link OutboxEntry} payload
     * matching the exact shape {@code DefaultBusinessObjectService}/{@code DefaultActionExecutor}'s
     * shared {@code outboxPayload} helper produces: {@code objectId}, {@code version}, {@code
     * typeIri} — no {@code tenantId}/{@code principalId}, unlike the automation-specific kinds.
     */
    protected static OutboxEntry objectChangeEntry(String kind) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("version", 1);
        payload.put("typeIri", "http://example.org/Task");
        return new OutboxEntry(UUID.randomUUID(), kind, payload, Instant.now());
    }

    /**
     * Builds a valid {@code ObjectDeleted} {@link OutboxEntry} payload: the same shape as {@link
     * #objectChangeEntry(String)} plus a {@code properties} snapshot map, since a soft-deleted
     * object can no longer be read back via {@code ObjectStorePort.find}. This contract's recording
     * doubles never decode payload contents beyond what {@code dispatch()} itself uses to route by
     * kind, so an empty snapshot is sufficient here — a real {@code DerivationRecomputer}/{@code
     * TriggerEvaluator} implementation would need real, IRI-keyed, {@code PayloadValueCodec}-encoded
     * entries, but this contract only proves routing, not decoding.
     */
    protected static OutboxEntry objectDeletedEntry() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("version", 1);
        payload.put("typeIri", "http://example.org/Task");
        payload.put("properties", Map.of());
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_DELETED, payload, Instant.now());
    }

    /**
     * Builds a valid {@code TimerScheduled} {@link OutboxEntry} payload matching the exact shape
     * {@code DefaultTransitionService} produces: {@code objectId}, {@code tenantId}, {@code
     * principalId}, {@code typeIri}, {@code state}, {@code transitionName}, {@code after}
     * (ISO-8601), and {@code timerKey}.
     */
    private static OutboxEntry timerScheduledEntry(
            UUID objectId, String timerKey, String state, String transitionName, Duration after) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", objectId.toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("typeIri", "http://example.org/Project");
        payload.put("state", state);
        payload.put("transitionName", transitionName);
        payload.put("after", after.toString());
        payload.put("timerKey", timerKey);
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TIMER_SCHEDULED, payload, Instant.now());
    }

    /**
     * Builds a valid {@code TimerCancelled} {@link OutboxEntry} payload: {@code objectId}, {@code
     * tenantId}, and {@code timerKey} — the latter MUST match the paired {@code TimerScheduled}
     * entry's {@code timerKey} exactly for a cancellation test to be meaningful.
     */
    private static OutboxEntry timerCancelledEntry(UUID objectId, String timerKey) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", objectId.toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("timerKey", timerKey);
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TIMER_CANCELLED, payload, Instant.now());
    }

    /**
     * Builds a valid {@code SignalReceived} {@link OutboxEntry} payload: {@code objectId}, {@code
     * tenantId}, {@code principalId}, {@code signalName}, and a {@code body} recorded for audit
     * only (never decoded by this contract's recording doubles).
     */
    private static OutboxEntry signalReceivedEntry(String signalName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("signalName", signalName);
        payload.put("body", Map.of());
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_SIGNAL_RECEIVED, payload, Instant.now());
    }

    /**
     * Builds a {@code TransitionFired} {@link OutboxEntry} for {@link #TRANSITION}, carrying the keys
     * {@code DefaultTransitionService} writes on one and stamped with ontology version 1.
     */
    private static OutboxEntry transitionFiredEntry() {
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
                1,
                "self",
                Map.of(),
                "typeIri",
                "http://example.org/Project",
                "machineIri",
                MACHINE_IRI);
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.now());
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
