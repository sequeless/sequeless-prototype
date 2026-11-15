package org.sequeless.adapter.automation.temporal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * An {@link ActionExecutor} test double for the per-action path. It records each {@link
 * #applyAction} and {@link #resolveWebhookAction} call with the action index and the action it was
 * given, so a test can check that the action at each index is the one applied.
 *
 * <p>Its four ActionRequest methods throw {@link UnsupportedOperationException}: the per-action
 * workflow never reaches them.
 */
final class RecordingTransitionExecutor implements ActionExecutor {

    /** The method name recorded for a call to {@link ActionExecutor#applyAction}. */
    static final String METHOD_APPLY_ACTION = "applyAction";

    /** The method name recorded for a call to {@link ActionExecutor#resolveWebhookAction}. */
    static final String METHOD_RESOLVE_WEBHOOK_ACTION = "resolveWebhookAction";

    /**
     * One recorded call.
     *
     * @param method {@link #METHOD_APPLY_ACTION} or {@link #METHOD_RESOLVE_WEBHOOK_ACTION}
     * @param actionIndex the action index the call was made with
     * @param entryId the {@link OutboxEntry#id()} of the {@code TransitionFired} row
     * @param action the action the call was made with
     */
    record Call(String method, int actionIndex, UUID entryId, Action action) {}

    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());
    private volatile String webhookUrl;

    /**
     * Sets the URL every {@link #resolveWebhookAction} call resolves to. Kept across {@link #reset()}.
     *
     * @param webhookUrl the URL to resolve to; must not be {@code null}
     */
    void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /** @return a snapshot of every recorded call, in invocation order, unmodifiable */
    List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    /** Clears the recorded calls. The webhook URL is kept. */
    void reset() {
        calls.clear();
    }

    @Override
    public void applyAction(Scope scope, OutboxEntry transitionFired, Action action, int actionIndex) {
        calls.add(new Call(METHOD_APPLY_ACTION, actionIndex, transitionFired.id(), action));
    }

    @Override
    public ResolvedWebhookRequest resolveWebhookAction(
            Scope scope, OutboxEntry transitionFired, WebhookAction action, int actionIndex) {
        calls.add(new Call(METHOD_RESOLVE_WEBHOOK_ACTION, actionIndex, transitionFired.id(), action));
        return new ResolvedWebhookRequest(webhookUrl, action.method(), Optional.empty());
    }

    @Override
    public void applySetProperty(Scope scope, OutboxEntry entry) {
        throw new UnsupportedOperationException("not used by the per-action workflow");
    }

    @Override
    public void applyCreateObject(Scope scope, OutboxEntry entry) {
        throw new UnsupportedOperationException("not used by the per-action workflow");
    }

    @Override
    public ResolvedWebhookRequest resolveWebhook(Scope scope, OutboxEntry entry) {
        throw new UnsupportedOperationException("not used by the per-action workflow");
    }

    @Override
    public void applyLog(Scope scope, OutboxEntry entry) {
        throw new UnsupportedOperationException("not used by the per-action workflow");
    }
}
