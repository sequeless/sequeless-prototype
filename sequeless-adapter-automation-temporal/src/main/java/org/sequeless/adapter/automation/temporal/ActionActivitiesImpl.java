package org.sequeless.adapter.automation.temporal;

import io.temporal.failure.ApplicationFailure;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The real {@link ActionActivities} implementation. {@link #applyTransitionAction} checks the row,
 * then applies one action through the injected {@link ActionExecutor}, or delivers a webhook action
 * over the JDK's own {@link HttpClient} (no new dependency, matching {@code InProcessAutomationPort}'s
 * choice).
 *
 * <p><b>No retry loop here, unlike the in-process adapter.</b> The webhook delivery throws a {@link
 * WebhookActivityException} on any non-2xx status or transport failure, letting the exception
 * propagate out of the activity method. Retrying is deliberately not this class's job: the
 * workflow's activity stub is built (see {@link TransitionActionWorkflowImpl}) with an {@code
 * ActivityOptions} carrying the {@code RetryOptions} {@link TemporalAutomationAutoConfiguration}
 * assembles from {@code sequeless.automation.temporal.retry.*}, so Temporal itself retries the
 * whole activity invocation — this is the entire point of using Temporal for this adapter, in
 * contrast to the in-process adapter's hand-rolled fixed-delay loop.
 */
public final class ActionActivitiesImpl implements ActionActivities {

    private final ActionExecutor actionExecutor;
    private final AutomationSource automationSource;
    private final HttpClient httpClient;

    /**
     * @param actionExecutor the {@link ActionExecutor} this activity implementation calls back
     *     into; must not be {@code null}
     * @param automationSource the {@link AutomationSource} {@link #applyTransitionAction} reads the
     *     automation of the row's ontology version from; must not be {@code null}
     */
    public ActionActivitiesImpl(ActionExecutor actionExecutor, AutomationSource automationSource) {
        this.actionExecutor = Objects.requireNonNull(actionExecutor, "actionExecutor must not be null");
        this.automationSource = Objects.requireNonNull(automationSource, "automationSource must not be null");
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Applies one action of a {@code TransitionFired} row, in this order on every attempt: reads the
     * automation first, so each attempt reads it exactly once; checks the row's {@code
     * ontologyVersion} stamp (missing or non-Number, then a mismatch against the automation); checks
     * the index against the automation's list for the row's transition; then applies the action. A
     * webhook action is resolved and delivered, and any other action is applied through {@link
     * ActionExecutor#applyAction}.
     *
     * <p>The three checks throw a non-retryable {@link ApplicationFailure}, so Temporal does not
     * retry the activity. The row was already processed by the relay before this runs, so no
     * dead-letter applies: a failure here is recorded as an {@link
     * org.sequeless.spi.automation.AutomationFailure} by {@link TransitionActionWorkflowImpl} once the
     * activity's retries are exhausted (spec 13, R2).
     *
     * @param scope the tenant and principal to apply the action on behalf of
     * @param transitionFired the {@code TransitionFired}-kind outbox entry the action originates from
     * @param actionIndex the action's 0-based position in the automation's list for the row's
     *     transition
     * @throws ApplicationFailure non-retryable, if the row has no {@code ontologyVersion} stamp, the
     *     stamp differs from the automation's version, or the index is outside the list
     */
    @Override
    public void applyTransitionAction(Scope scope, OutboxEntry transitionFired, int actionIndex) {
        Map<String, Object> payload = transitionFired.payload();
        Automation automation = automationSource.automation(scope.tenantId());
        if (!(payload.get("ontologyVersion") instanceof Number stamp)) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "TransitionFired row " + transitionFired.id() + " has no ontologyVersion stamp",
                    "TransitionFiredStampMissing");
        }
        int rowOntologyVersion = stamp.intValue();
        int activeOntologyVersion = automation.ontologyVersion().value();
        if (activeOntologyVersion != rowOntologyVersion) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Automation is at ontology version " + activeOntologyVersion
                            + " but TransitionFired row " + transitionFired.id()
                            + " was written at version " + rowOntologyVersion,
                    "TransitionFiredStampMismatch");
        }

        TransitionRef transition =
                new TransitionRef((String) payload.get("machineIri"), (String) payload.get("fromState"), (String)
                        payload.get("transitionName"));
        List<Action> actions = automation.actions(transition);
        if (actionIndex < 0 || actionIndex >= actions.size()) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "TransitionFired row " + transitionFired.id() + " has no action at index " + actionIndex,
                    "TransitionFiredIndexOutOfRange");
        }

        Action action = actions.get(actionIndex);
        if (action instanceof WebhookAction webhook) {
            deliver(actionExecutor.resolveWebhookAction(scope, transitionFired, webhook, actionIndex));
        } else {
            actionExecutor.applyAction(scope, transitionFired, action, actionIndex);
        }
    }

    /**
     * Issues {@code resolved}'s HTTP request exactly once, throwing on any transport failure or
     * non-2xx status; Temporal's {@code RetryOptions} is what causes this activity to be invoked
     * again, not this method.
     *
     * @throws WebhookActivityException if the request could not be delivered, or received a
     *     non-2xx status
     */
    private void deliver(ResolvedWebhookRequest resolved) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(resolved.url()))
                .method(
                        resolved.method(),
                        resolved.body().map(BodyPublishers::ofString).orElseGet(BodyPublishers::noBody))
                .build();

        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, BodyHandlers.discarding());
        } catch (IOException e) {
            throw new WebhookActivityException("Webhook delivery to " + resolved.url() + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebhookActivityException("Webhook delivery to " + resolved.url() + " was interrupted", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new WebhookActivityException(
                    "Webhook delivery to " + resolved.url() + " received non-2xx status " + response.statusCode(),
                    null);
        }
    }
}
