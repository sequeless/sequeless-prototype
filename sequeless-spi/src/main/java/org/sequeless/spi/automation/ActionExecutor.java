package org.sequeless.spi.automation;

import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The inbound port an automation adapter (the in-process relay, or a Temporal activity) calls back
 * into to apply one fired transition's action, using the exact same validated, audited code path
 * {@code sequeless-core} already uses for every other write. See this package's {@code
 * package-info.java} for why this port's dependency direction is the mirror image of every other
 * port in this SPI: {@code sequeless-core} implements this interface ({@code
 * org.sequeless.core.automation.DefaultActionExecutor}), and an adapter calls it, rather than the
 * other way around.
 *
 * <p>Both methods take one {@code TransitionFired}-kind {@link OutboxEntry} and one action of its
 * transition, by 0-based index. {@link #applyAction} applies the action, and {@link
 * #resolveWebhookAction} resolves a webhook action into the request the adapter delivers. The
 * adapter builds the action from the automation of the row's ontology version and passes it in.
 */
public interface ActionExecutor {

    /**
     * Applies one action that an adapter has already built from the automation of the {@code
     * TransitionFired} row's ontology version. The adapter passes the sealed {@link Action} itself.
     *
     * <p>{@code transitionFired} is the {@code TransitionFired}-kind {@link OutboxEntry}, which
     * carries {@code objectId}, {@code tenantId}, {@code principalId}, {@code transitionName},
     * {@code fromState}, {@code toState}, {@code ontologyVersion}, {@code self}, {@code typeIri} and
     * {@code machineIri}. {@code action} is the pre-built sealed {@link Action}. {@code actionIndex}
     * is the action's 0-based position in the list the automation returns.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException} until an
     * implementation overrides this method.
     *
     * @param scope the tenant and principal to apply the action on behalf of; must not be {@code
     *     null}
     * @param transitionFired the {@code TransitionFired}-kind outbox entry the action originates
     *     from; must not be {@code null}
     * @param action the pre-built sealed action to apply; must not be {@code null}
     * @param actionIndex the action's 0-based position in the list the automation returns
     * @throws IllegalArgumentException if {@code action} is a {@link WebhookAction}; deliver it through
     *     {@link #resolveWebhookAction} instead
     */
    default void applyAction(Scope scope, OutboxEntry transitionFired, Action action, int actionIndex) {
        throw new UnsupportedOperationException("applyAction is not implemented by this ActionExecutor");
    }

    /**
     * Resolves one webhook action that an adapter has already built from the automation of the
     * {@code TransitionFired} row's ontology version, into a fully-rendered {@link
     * ResolvedWebhookRequest}. The {@code url}, {@code method} and {@code body} are rendered against
     * the row's {@code self}, {@code toState} and {@code principalId}. This method performs no HTTP
     * I/O: the calling adapter delivers the returned request.
     *
     * <p>{@code transitionFired} and {@code actionIndex} are as described for {@link #applyAction}.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException} until an
     * implementation overrides this method.
     *
     * @param scope the tenant and principal to render the templates on behalf of; must not be {@code
     *     null}
     * @param transitionFired the {@code TransitionFired}-kind outbox entry the action originates
     *     from; must not be {@code null}
     * @param action the pre-built webhook action to resolve; must not be {@code null}
     * @param actionIndex the action's 0-based position in the list the automation returns
     * @return the fully-rendered request, ready for the caller to issue over HTTP
     */
    default ResolvedWebhookRequest resolveWebhookAction(
            Scope scope, OutboxEntry transitionFired, WebhookAction action, int actionIndex) {
        throw new UnsupportedOperationException("resolveWebhookAction is not implemented by this ActionExecutor");
    }
}
