package org.sequeless.testkit.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
    void dispatchRejectsNonActionRequestKind() {
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
