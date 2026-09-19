package org.sequeless.testkit.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.object.OutboxEntry;

/**
 * A hand-rolled {@link ActionExecutor} test double (this codebase uses no Mockito) that records
 * every call it receives instead of doing anything else, so a contract test — or an adapter's own
 * test — can assert which method {@link org.sequeless.spi.automation.AutomationPort#dispatch}
 * actually invoked, and how many times.
 *
 * <p>Backed by a thread-safe list: a future Temporal activity implementation may call back into
 * this executor off the dispatching thread, so {@link #calls()} must remain safe to read and
 * write concurrently.
 */
public final class RecordingActionExecutor implements ActionExecutor {

    /** The method name recorded for a call to {@link ActionExecutor#applySetProperty}. */
    public static final String METHOD_APPLY_SET_PROPERTY = "applySetProperty";

    /** The method name recorded for a call to {@link ActionExecutor#applyCreateObject}. */
    public static final String METHOD_APPLY_CREATE_OBJECT = "applyCreateObject";

    /** The method name recorded for a call to {@link ActionExecutor#resolveWebhook}. */
    public static final String METHOD_RESOLVE_WEBHOOK = "resolveWebhook";

    /** The method name recorded for a call to {@link ActionExecutor#applyLog}. */
    public static final String METHOD_APPLY_LOG = "applyLog";

    /**
     * One recorded {@link ActionExecutor} call.
     *
     * @param method one of {@link #METHOD_APPLY_SET_PROPERTY}, {@link #METHOD_APPLY_CREATE_OBJECT},
     *     {@link #METHOD_RESOLVE_WEBHOOK}, or {@link #METHOD_APPLY_LOG}
     * @param entryId the {@link OutboxEntry#id()} the call was made with
     */
    public record Call(String method, UUID entryId) {}

    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());
    private final ResolvedWebhookRequest webhookResponse;

    /** Creates a recorder whose {@link #resolveWebhook} returns a default {@link ResolvedWebhookRequest}. */
    public RecordingActionExecutor() {
        this(new ResolvedWebhookRequest("http://example.invalid/hook", "POST", Optional.empty()));
    }

    /**
     * @param webhookResponse the {@link ResolvedWebhookRequest} every {@link #resolveWebhook} call
     *     returns; must not be {@code null}
     */
    public RecordingActionExecutor(ResolvedWebhookRequest webhookResponse) {
        this.webhookResponse = Objects.requireNonNull(webhookResponse, "webhookResponse must not be null");
    }

    @Override
    public void applySetProperty(Scope scope, OutboxEntry entry) {
        record(METHOD_APPLY_SET_PROPERTY, entry);
    }

    @Override
    public void applyCreateObject(Scope scope, OutboxEntry entry) {
        record(METHOD_APPLY_CREATE_OBJECT, entry);
    }

    @Override
    public ResolvedWebhookRequest resolveWebhook(Scope scope, OutboxEntry entry) {
        record(METHOD_RESOLVE_WEBHOOK, entry);
        return webhookResponse;
    }

    @Override
    public void applyLog(Scope scope, OutboxEntry entry) {
        record(METHOD_APPLY_LOG, entry);
    }

    /** @return every call recorded so far, in invocation order; a snapshot, not a live view */
    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    /**
     * @param method one of {@link #METHOD_APPLY_SET_PROPERTY}, {@link #METHOD_APPLY_CREATE_OBJECT},
     *     {@link #METHOD_RESOLVE_WEBHOOK}, or {@link #METHOD_APPLY_LOG}
     * @return how many recorded calls have {@link Call#method()} equal to {@code method}
     */
    public long countOf(String method) {
        return calls().stream().filter(call -> call.method().equals(method)).count();
    }

    private void record(String method, OutboxEntry entry) {
        calls.add(new Call(method, entry.id()));
    }
}
