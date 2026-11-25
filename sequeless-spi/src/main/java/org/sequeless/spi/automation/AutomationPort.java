package org.sequeless.spi.automation;

import org.sequeless.kernel.identity.Scope;

/**
 * The outbound port a relay (the in-process poller, or a future Temporal-backed one) calls to
 * durably start — or, for a synchronous adapter, fully complete — the handling of one {@link
 * StoreEvent}: an object created, updated or deleted, or a transition fired. The relay reads each
 * outbox row, maps it to its event type, and calls {@link #ingest}. The adapter matches on that
 * sealed type: an {@link ObjectChangedEvent} triggers trigger evaluation through {@link
 * org.sequeless.spi.automation.TriggerEvaluator}, and a {@link TransitionFiredEvent} applies the
 * fired transition's actions through {@link ActionExecutor} and starts the timers of the state it
 * enters. Exactly one implementation is wired into the running application at a time, selected by
 * configuration property, the same way every other outbound port in this SPI is selected.
 *
 * <p>Unlike {@link ActionExecutor}, whose dependency direction {@code
 * org.sequeless.spi.automation.package-info} explains is inverted, {@code AutomationPort} is an
 * ordinary outbound port: {@code sequeless-core}/{@code sequeless-app}'s relay calls it, and an
 * automation adapter (the in-process adapter, or a Temporal adapter) implements it and calls back
 * into {@link ActionExecutor} or {@link org.sequeless.spi.automation.TriggerEvaluator} to do the
 * work.
 */
public interface AutomationPort {

    /**
     * Durably starts (or, for a synchronous adapter, completes) the handling of {@code event}, routed
     * by its sealed type. A second call whose {@code event.id()} matches a previously ingested event
     * must not cause the underlying inbound-port method to be invoked a second time. The carve-out is
     * {@code TransitionFired}, whose key is the event id and the action index together: the in-process
     * adapter runs {@code SetProperty} and {@code CreateObject} actions again on a repeat call.
     *
     * <p>A {@link TransitionFiredEvent} applies the transition's actions and starts its timers.
     *
     * @param scope the tenant and principal to ingest the event on behalf of; must not be {@code null}
     * @param event the Store event to ingest; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalStateException if a {@link TransitionFiredEvent}'s {@code ontologyVersion} stamp
     *     is missing, or does not match the ontology version of the automation or of the behaviour
     *     that the adapter reads to resolve the fired transition's timers
     */
    void ingest(Scope scope, StoreEvent event);
}
