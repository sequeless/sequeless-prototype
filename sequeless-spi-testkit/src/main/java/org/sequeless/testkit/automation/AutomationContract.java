package org.sequeless.testkit.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.object.OutboxEntry;

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
 * org.sequeless.spi.automation.ActionExecutor#resolveWebhook} was invoked — it makes no assertion
 * about any HTTP call an adapter might go on to make with the resolved request, since HTTP retry
 * policy genuinely differs between adapters (the in-process adapter's fixed retry loop vs. a
 * Temporal activity's {@code RetryOptions}) and is tested separately, per adapter.
 */
public abstract class AutomationContract {

    private static final Scope SCOPE =
        new Scope(new TenantId("tenant-1"), new Principal("user-1", "User One", Set.of()));

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
     *     was constructed with, so this contract can observe whether {@code dispatch} routed an
     *     object-lifecycle entry to {@link DerivationRecomputer#recompute}
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

    @Test
    void dispatchRoutesSetPropertyToApplySetProperty() {
        OutboxEntry entry =
            actionRequestEntry(
                "SetProperty",
                Map.of("property", "http://example.org/owner", "expression", "'user-2'"));

        port().dispatch(SCOPE, entry);

        assertThat(recordingExecutor().countOf(RecordingActionExecutor.METHOD_APPLY_SET_PROPERTY))
            .isEqualTo(1);
    }

    @Test
    void dispatchRoutesCreateObjectToApplyCreateObject() {
        OutboxEntry entry =
            actionRequestEntry(
                "CreateObject",
                Map.of("createType", "http://example.org/Task", "createProperties", Map.of()));

        port().dispatch(SCOPE, entry);

        assertThat(recordingExecutor().countOf(RecordingActionExecutor.METHOD_APPLY_CREATE_OBJECT))
            .isEqualTo(1);
    }

    @Test
    void dispatchRoutesWebhookToResolveWebhook() {
        OutboxEntry entry =
            actionRequestEntry(
                "Webhook", Map.of("url", "http://example.invalid/hook", "method", "POST"));

        port().dispatch(SCOPE, entry);

        assertThat(recordingExecutor().countOf(RecordingActionExecutor.METHOD_RESOLVE_WEBHOOK))
            .isEqualTo(1);
    }

    @Test
    void dispatchRoutesLogToApplyLog() {
        OutboxEntry entry = actionRequestEntry("Log", Map.of("message", "hello"));

        port().dispatch(SCOPE, entry);

        assertThat(recordingExecutor().countOf(RecordingActionExecutor.METHOD_APPLY_LOG))
            .isEqualTo(1);
    }

    @Test
    void redispatchWithSameEntryIdIsIdempotent() {
        OutboxEntry entry = actionRequestEntry("Log", Map.of("message", "hello"));

        port().dispatch(SCOPE, entry);
        port().dispatch(SCOPE, entry);

        assertThat(recordingExecutor().countOf(RecordingActionExecutor.METHOD_APPLY_LOG))
            .isEqualTo(1);
    }

    @Test
    void dispatchRejectsNullScope() {
        OutboxEntry entry = actionRequestEntry("Log", Map.of("message", "hello"));
        assertThatNullPointerException().isThrownBy(() -> port().dispatch(null, entry));
    }

    @Test
    void dispatchRejectsNullEntry() {
        assertThatNullPointerException().isThrownBy(() -> port().dispatch(SCOPE, null));
    }

    @Test
    void dispatchStillRejectsTransitionFiredKind() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("transitionName", "activate");
        payload.put("fromState", "Draft");
        payload.put("toState", "Active");
        OutboxEntry entry =
            new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.now());

        assertThatIllegalArgumentException().isThrownBy(() -> port().dispatch(SCOPE, entry));
    }

    @Test
    void dispatchRoutesObjectLifecycleEventToRecomputeThenOnChange() {
        OutboxEntry entry = objectChangeEntry(OutboxEntry.KIND_OBJECT_UPDATED);

        port().dispatch(SCOPE, entry);

        assertThat(recordingDerivationRecomputer().calls()).hasSize(1);
        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_CHANGE))
            .isEqualTo(1);
        assertRecomputeRanBeforeOnChange();
    }

    @Test
    void dispatchRoutesObjectDeletedToRecomputeThenOnChangeToo() {
        OutboxEntry entry = objectDeletedEntry();

        port().dispatch(SCOPE, entry);

        assertThat(recordingDerivationRecomputer().calls()).hasSize(1);
        assertThat(recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_CHANGE))
            .isEqualTo(1);
        assertRecomputeRanBeforeOnChange();
    }

    @Test
    void timerFiresOnlyAfterAdvanceTime() {
        Duration after = Duration.ofMinutes(30);
        UUID objectId = UUID.randomUUID();
        OutboxEntry entry =
            timerScheduledEntry(objectId, objectId + "|OnHold|expireHold", "OnHold", "expireHold", after);

        port().dispatch(SCOPE, entry);

        assertThat(
                recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
            .isEqualTo(0);

        advanceTime(after);

        assertThat(
                recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
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

        assertThat(
                recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
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

        assertThat(
                recordingTriggerEvaluator().countOf(RecordingTriggerEvaluator.METHOD_ON_TIMER_ELAPSED))
            .isEqualTo(1);
    }

    /**
     * Asserts that, for the single change event most recently dispatched, {@link
     * #recordingDerivationRecomputer()}'s one recorded call happened strictly before {@link
     * #recordingTriggerEvaluator()}'s first recorded {@link RecordingTriggerEvaluator#METHOD_ON_CHANGE}
     * call, by comparing each {@code Call}'s {@code sequenceNanos()} — see {@link
     * RecordingTriggerEvaluator}'s javadoc for why that field, rather than any state shared between
     * the two doubles, is what makes this comparison possible.
     */
    private void assertRecomputeRanBeforeOnChange() {
        long recomputeAt = recordingDerivationRecomputer().calls().get(0).sequenceNanos();
        long onChangeAt =
            recordingTriggerEvaluator().calls().stream()
                .filter(call -> call.method().equals(RecordingTriggerEvaluator.METHOD_ON_CHANGE))
                .findFirst()
                .orElseThrow()
                .sequenceNanos();
        assertThat(recomputeAt).isLessThan(onChangeAt);
    }

    /**
     * Builds a valid {@code ObjectCreated}/{@code ObjectUpdated} {@link OutboxEntry} payload
     * matching the exact shape {@code DefaultBusinessObjectService}/{@code DefaultActionExecutor}'s
     * shared {@code outboxPayload} helper produces: {@code objectId}, {@code version}, {@code
     * typeIri} — no {@code tenantId}/{@code principalId}, unlike the automation-specific kinds.
     */
    private static OutboxEntry objectChangeEntry(String kind) {
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
    private static OutboxEntry objectDeletedEntry() {
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
     * Builds a valid {@code ActionRequest} {@link OutboxEntry} payload matching the exact shape
     * {@code org.sequeless.core.usecase.DefaultTransitionService} produces: the fields common to
     * every action kind, plus whatever {@code extra} kind-specific fields the caller supplies.
     */
    private static OutboxEntry actionRequestEntry(String actionKind, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", SCOPE.tenantId().value());
        payload.put("principalId", SCOPE.principal().id());
        payload.put("transitionName", "activate");
        payload.put("actionIndex", 0);
        payload.put("typeIri", "http://example.org/Project");
        payload.put("state", "Active");
        payload.put("self", Map.of());
        payload.put("actionKind", actionKind);
        payload.putAll(extra);
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, Instant.now());
    }
}
