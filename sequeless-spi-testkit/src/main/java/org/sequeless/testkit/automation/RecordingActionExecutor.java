package org.sequeless.testkit.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.outbox.OutboxEntry;

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

    /** The method name recorded for a call to {@link ActionExecutor#applyAction}. */
    public static final String METHOD_APPLY_ACTION = "applyAction";

    /** The method name recorded for a call to {@link ActionExecutor#resolveWebhookAction}. */
    public static final String METHOD_RESOLVE_WEBHOOK_ACTION = "resolveWebhookAction";

    /**
     * One recorded {@link ActionExecutor} call.
     *
     * @param method one of {@link #METHOD_APPLY_ACTION} or {@link #METHOD_RESOLVE_WEBHOOK_ACTION}
     * @param entryId the {@link OutboxEntry#id()} of the {@code TransitionFired} row the call was made with
     * @param actionIndex the action index the call was made with
     * @param action the action the call was made with, so its type is the action's type
     */
    public record Call(String method, UUID entryId, int actionIndex, Action action) {}

    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());
    private final ResolvedWebhookRequest webhookResponse;

    /** Creates a recorder whose {@link #resolveWebhookAction} returns a default {@link ResolvedWebhookRequest}. */
    public RecordingActionExecutor() {
        this(new ResolvedWebhookRequest("http://example.invalid/hook", "POST", Optional.empty()));
    }

    /**
     * @param webhookResponse the {@link ResolvedWebhookRequest} every {@link #resolveWebhookAction} call
     *     returns; must not be {@code null}
     */
    public RecordingActionExecutor(ResolvedWebhookRequest webhookResponse) {
        this.webhookResponse = Objects.requireNonNull(webhookResponse, "webhookResponse must not be null");
    }

    @Override
    public void applyAction(Scope scope, OutboxEntry transitionFired, Action action, int actionIndex) {
        recordAction(METHOD_APPLY_ACTION, transitionFired, action, actionIndex);
    }

    @Override
    public ResolvedWebhookRequest resolveWebhookAction(
            Scope scope, OutboxEntry transitionFired, WebhookAction action, int actionIndex) {
        recordAction(METHOD_RESOLVE_WEBHOOK_ACTION, transitionFired, action, actionIndex);
        return webhookResponse;
    }

    /** @return every call recorded so far, in invocation order; a snapshot, not a live view */
    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    /**
     * @param method one of the {@code METHOD_*} constants above
     * @return how many recorded calls have {@link Call#method()} equal to {@code method}
     */
    public long countOf(String method) {
        return calls().stream().filter(call -> call.method().equals(method)).count();
    }

    private void recordAction(String method, OutboxEntry transitionFired, Action action, int actionIndex) {
        calls.add(new Call(method, transitionFired.id(), actionIndex, action));
    }
}
