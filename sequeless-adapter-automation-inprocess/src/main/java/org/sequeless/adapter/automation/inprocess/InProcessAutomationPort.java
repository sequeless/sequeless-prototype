package org.sequeless.adapter.automation.inprocess;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The test/dev {@link AutomationPort} adapter: {@link #dispatch} runs an {@code ActionRequest}
 * outbox entry's action synchronously, to completion, on the calling (relay) thread, by invoking
 * the matching {@link ActionExecutor} method directly — no separate durable engine to hand off to,
 * unlike a future Temporal adapter.
 *
 * <p><b>This adapter owns idempotency itself, unlike a Temporal adapter.</b> {@link
 * AutomationPort#dispatch}'s contract requires that a second call for the same {@link
 * OutboxEntry#id()} must not invoke the underlying {@link ActionExecutor} method a second time.
 * A Temporal adapter gets this for free from workflow-id semantics ({@code
 * WorkflowExecutionAlreadyStarted}); this adapter has no such built-in mechanism, so it keeps an
 * in-memory, thread-safe set of every {@link OutboxEntry#id()} it has already dispatched and
 * short-circuits a repeat as a no-op. This set grows unbounded for the process's lifetime and is
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
 * itself is retried: {@link ActionExecutor#resolveWebhook}'s own template-resolution failures (a
 * malformed {@code ${expr}}) propagate immediately, on the first and only attempt, since retrying
 * a deterministic template error can never succeed.
 */
public final class InProcessAutomationPort implements AutomationPort {

    private static final String ACTION_KIND_SET_PROPERTY = "SetProperty";
    private static final String ACTION_KIND_CREATE_OBJECT = "CreateObject";
    private static final String ACTION_KIND_WEBHOOK = "Webhook";
    private static final String ACTION_KIND_LOG = "Log";

    private final ActionExecutor actionExecutor;
    private final InProcessAutomationProperties properties;
    private final HttpClient httpClient;
    private final Set<UUID> dispatched = ConcurrentHashMap.newKeySet();

    /**
     * @param actionExecutor the {@link ActionExecutor} this port calls back into to actually apply
     *     each action; must not be {@code null}
     * @param properties this adapter's webhook retry configuration; must not be {@code null}
     */
    public InProcessAutomationPort(ActionExecutor actionExecutor, InProcessAutomationProperties properties) {
        this.actionExecutor = Objects.requireNonNull(actionExecutor, "actionExecutor must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void dispatch(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        if (!entry.kind().equals(OutboxEntry.KIND_ACTION_REQUEST)) {
            throw new IllegalArgumentException(
                "OutboxEntry kind must be '" + OutboxEntry.KIND_ACTION_REQUEST + "', was '" + entry.kind() + "'");
        }

        if (!dispatched.add(entry.id())) {
            return;
        }

        String actionKind = (String) entry.payload().get("actionKind");
        switch (actionKind) {
            case ACTION_KIND_SET_PROPERTY -> actionExecutor.applySetProperty(scope, entry);
            case ACTION_KIND_CREATE_OBJECT -> actionExecutor.applyCreateObject(scope, entry);
            case ACTION_KIND_LOG -> actionExecutor.applyLog(scope, entry);
            case ACTION_KIND_WEBHOOK -> {
                ResolvedWebhookRequest resolved = actionExecutor.resolveWebhook(scope, entry);
                deliver(resolved);
            }
            default -> throw new IllegalArgumentException(
                "Unrecognized ActionRequest payload actionKind: " + actionKind);
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

        HttpRequest.Builder requestBuilder =
            HttpRequest.newBuilder(URI.create(resolved.url()))
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
                throw new WebhookDeliveryException(
                    "Webhook delivery to " + resolved.url() + " was interrupted", e);
            }

            if (attempt < totalAttempts) {
                sleep(delay);
            }
        }

        String message =
            lastFailure != null
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
