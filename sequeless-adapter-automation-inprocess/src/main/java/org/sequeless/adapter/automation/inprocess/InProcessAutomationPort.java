package org.sequeless.adapter.automation.inprocess;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The test/dev {@link AutomationPort} adapter: {@link #dispatch} runs a {@code TransitionFired}
 * outbox entry's actions synchronously, to completion, on the calling (relay) thread, by invoking
 * the matching {@link ActionExecutor} method directly — no separate durable engine to hand off to,
 * unlike a future Temporal adapter.
 *
 * <p><b>This adapter owns idempotency itself, unlike a Temporal adapter.</b> {@link
 * AutomationPort#dispatch}'s contract requires that a second call for the same {@link
 * OutboxEntry#id()} must not invoke the underlying {@link ActionExecutor} method a second time.
 * A Temporal adapter gets this for free from workflow-id semantics ({@code
 * WorkflowExecutionAlreadyStarted}); this adapter has no such built-in mechanism, so it keeps an
 * in-memory, thread-safe set of every {@link OutboxEntry#id()} it has already dispatched and
 * short-circuits a repeat as a no-op. The exception is a {@code TransitionFired} row, which is keyed
 * by entry id and action index. This set grows unbounded for the process's lifetime and is
 * <b>not crash-safe</b> — a process restart forgets every id it ever saw. Both are accepted
 * limitations, not bugs: durability across restarts is Temporal's job, not this adapter's, which
 * exists only for tests and local development.
 *
 * <p><b>This class does not wire its own {@link ActionExecutor}.</b> The real implementation
 * ({@code org.sequeless.core.automation.DefaultActionExecutor}) lives in {@code sequeless-core},
 * which this adapter module cannot depend on (the {@code noAdapterDependsOnCore} architecture
 * rule). {@link InProcessAutomationAutoConfiguration} simply takes an already-registered {@link
 * ActionExecutor} bean as a method parameter; some later, application-level wiring task is
 * responsible for having put that bean on the context by the time this auto-configuration runs.
 *
 * <p>A {@code sq:Webhook} action's HTTP call uses the JDK's own {@link HttpClient} (no new
 * dependency) behind a small <b>fixed</b> retry loop — {@link
 * InProcessAutomationProperties#getRetryAttempts()} total attempts, each separated by {@link
 * InProcessAutomationProperties#getRetryDelay()} — deliberately dumb, in contrast to a future
 * Temporal activity's configurable, exponential-backoff {@code RetryOptions}. Only the HTTP call
 * itself is retried: {@link ActionExecutor#resolveWebhookAction}'s own template-resolution
 * failures (a malformed {@code ${expr}}) propagate immediately, on the first and only attempt, since
 * retrying
 * a deterministic template error can never succeed.
 *
 * <p><b>Kind routing.</b> {@link #dispatch} routes the three
 * object-lifecycle kinds ({@code ObjectCreated}/{@code ObjectUpdated}/{@code ObjectDeleted}) to
 * {@link TriggerEvaluator#onChange}. The application's outbox relay runs {@link
 * org.sequeless.spi.automation.DerivationRecomputer#recompute} for the same row before it calls this
 * method, so {@code
 * ex:ProjectLifecycle}'s {@code autoClose} guard reads a materialised property that is already
 * fresh. This port makes no recompute call of its own. {@code SignalReceived} goes to {@link
 * TriggerEvaluator#onSignal}. {@code TransitionFired} is applied per action: {@link #dispatch} routes
 * each such row to {@link #dispatchTransitionFired}, which starts the timers the row names through
 * {@link InProcessTimerScheduler} and then applies the row's actions in order from the automation of
 * the row's ontology version. This adapter has no timer kind of its own: timers are started from the
 * {@code TransitionFired} row, and an entry of any other kind not listed above is rejected as
 * unsupported.
 */
public final class InProcessAutomationPort implements AutomationPort {

    private final ActionExecutor actionExecutor;
    private final InProcessAutomationProperties properties;
    private final TriggerEvaluator triggerEvaluator;
    private final InProcessTimerScheduler timerScheduler;
    private final HttpClient httpClient;
    private final AutomationSource automationSource;
    private final Set<UUID> dispatched = ConcurrentHashMap.newKeySet();
    private final Set<ActionKey> dispatchedActions = ConcurrentHashMap.newKeySet();

    /**
     * Identifies one action of one {@code TransitionFired} row, for {@link #dispatchTransitionFired}'s
     * redelivery record. Kept separate from {@link #dispatched}, which is keyed by entry id alone.
     */
    private record ActionKey(UUID entryId, int actionIndex) {}

    /**
     * @param actionExecutor the {@link ActionExecutor} this port calls back into to actually apply
     *     each action; must not be {@code null}
     * @param properties this adapter's webhook retry configuration; must not be {@code null}
     * @param triggerEvaluator the port an object-lifecycle or signal entry is routed to;
     *     must not be {@code null}
     * @param timerScheduler the scheduler the timers a {@code TransitionFired} row starts are scheduled
     *     on; must not be {@code null}
     * @param automationSource the source a {@code TransitionFired} row's actions are read from; must
     *     not be {@code null}
     */
    public InProcessAutomationPort(
            ActionExecutor actionExecutor,
            InProcessAutomationProperties properties,
            TriggerEvaluator triggerEvaluator,
            InProcessTimerScheduler timerScheduler,
            AutomationSource automationSource) {
        this.actionExecutor = Objects.requireNonNull(actionExecutor, "actionExecutor must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.triggerEvaluator = Objects.requireNonNull(triggerEvaluator, "triggerEvaluator must not be null");
        this.timerScheduler = Objects.requireNonNull(timerScheduler, "timerScheduler must not be null");
        this.automationSource = Objects.requireNonNull(automationSource, "automationSource must not be null");
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void dispatch(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        if (OutboxEntry.KIND_TRANSITION_FIRED.equals(entry.kind())) {
            // dispatchTransitionFired records each action under (entry id, action index), so this row is
            // not gated by the entry-level dispatched set below.
            dispatchTransitionFired(scope, entry);
            return;
        }

        if (!dispatched.add(entry.id())) {
            return;
        }

        switch (entry.kind()) {
            case OutboxEntry.KIND_OBJECT_CREATED, OutboxEntry.KIND_OBJECT_UPDATED, OutboxEntry.KIND_OBJECT_DELETED ->
                triggerEvaluator.onChange(scope, entry);
            case OutboxEntry.KIND_SIGNAL_RECEIVED -> triggerEvaluator.onSignal(scope, entry);
            default -> throw new IllegalArgumentException("Unsupported OutboxEntry kind for dispatch: " + entry.kind());
        }
    }

    /**
     * Starts the timers, then applies the actions, of a {@code TransitionFired} row. The actions run
     * in the automation's order, from the automation of the ontology version the row was written
     * under. {@link #dispatch} calls this for every {@code TransitionFired} row.
     *
     * <p>The timers are started before the action loop. {@link TriggerEvaluator#timersStartedBy}
     * resolves the timers the row starts, and each one is passed to {@link
     * InProcessTimerScheduler#schedule}. A row with no actions still starts its timers, and an action
     * that throws does not stop them.
     *
     * <p>Redelivery is recorded per {@code (entry id, action index)}. A {@link LogAction} is recorded
     * once applied, and a {@link WebhookAction} once its delivery succeeds, so neither repeats on a
     * retry. A {@code SetProperty} or {@code CreateObject} action is never recorded: its write commits
     * with the claim transaction and rolls back with it, so it must run again on a retry.
     *
     * @throws IllegalStateException if the row has no {@code ontologyVersion} stamp, or the automation
     *     is at a different ontology version than the row, which is a stamp mismatch and is checked
     *     before any timer starts or action runs; the timer lookup also throws it when the active
     *     behaviour is at a different version than the row
     */
    void dispatchTransitionFired(Scope scope, OutboxEntry entry) {
        Map<String, Object> payload = entry.payload();
        Automation automation = automationSource.automation(scope.tenantId());
        if (!(payload.get("ontologyVersion") instanceof Number stamp)) {
            throw new IllegalStateException("TransitionFired row " + entry.id() + " has no ontologyVersion stamp");
        }
        int rowOntologyVersion = stamp.intValue();
        if (automation.ontologyVersion().value() != rowOntologyVersion) {
            throw new IllegalStateException("Automation is at ontology version "
                    + automation.ontologyVersion().value()
                    + " but TransitionFired row "
                    + entry.id()
                    + " was written at version "
                    + rowOntologyVersion);
        }

        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String typeIri = (String) payload.get("typeIri");
        for (ResolvedTimer timer : triggerEvaluator.timersStartedBy(scope, entry)) {
            timerScheduler.schedule(scope, objectId, typeIri, rowOntologyVersion, timer);
        }

        TransitionRef transition =
                new TransitionRef((String) payload.get("machineIri"), (String) payload.get("fromState"), (String)
                        payload.get("transitionName"));
        List<Action> actions = automation.actions(transition);
        for (int i = 0; i < actions.size(); i++) {
            ActionKey key = new ActionKey(entry.id(), i);
            if (dispatchedActions.contains(key)) {
                continue;
            }
            Action action = actions.get(i);
            if (action instanceof WebhookAction webhook) {
                deliver(actionExecutor.resolveWebhookAction(scope, entry, webhook, i));
                dispatchedActions.add(key);
            } else {
                actionExecutor.applyAction(scope, entry, action, i);
                if (action instanceof LogAction) {
                    dispatchedActions.add(key);
                }
            }
        }
    }

    /**
     * Issues {@code resolved}'s HTTP request, retrying up to {@link
     * InProcessAutomationProperties#getRetryAttempts()} times, with a fixed {@link
     * InProcessAutomationProperties#getRetryDelay()} pause between attempts, on either a
     * non-2xx status or an {@link java.io.IOException}.
     *
     * @throws WebhookDeliveryException if every attempt fails
     */
    private void deliver(ResolvedWebhookRequest resolved) {
        int totalAttempts = properties.getRetryAttempts();
        Duration delay = properties.getRetryDelay();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(resolved.url()))
                .method(
                        resolved.method(),
                        resolved.body().map(BodyPublishers::ofString).orElseGet(BodyPublishers::noBody));
        HttpRequest request = requestBuilder.build();

        Exception lastFailure = null;
        int lastStatusCode = -1;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                HttpResponse<Void> response = httpClient.send(request, BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
                lastStatusCode = response.statusCode();
                lastFailure = null;
            } catch (java.io.IOException e) {
                lastFailure = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WebhookDeliveryException("Webhook delivery to " + resolved.url() + " was interrupted", e);
            }

            if (attempt < totalAttempts) {
                sleep(delay);
            }
        }

        String message = lastFailure != null
                ? "Webhook delivery to " + resolved.url() + " failed after " + totalAttempts + " attempts"
                : "Webhook delivery to "
                        + resolved.url()
                        + " failed after "
                        + totalAttempts
                        + " attempts: last HTTP status was "
                        + lastStatusCode;
        throw new WebhookDeliveryException(message, lastFailure);
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebhookDeliveryException("Webhook delivery retry wait was interrupted", e);
        }
    }
}
