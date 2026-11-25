package org.sequeless.spi.automation;

import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The outbound port a relay (the in-process poller, or a future Temporal-backed one) calls to
 * durably start — or, for a synchronous adapter, fully complete — the handling of one outbox row,
 * whatever its kind: an {@link OutboxEntry#KIND_TRANSITION_FIRED} row applies the fired
 * transition's actions and starts the timers of the state it enters, and the three object-lifecycle
 * kinds trigger a recompute and then trigger-evaluation pass. Exactly one implementation
 * is wired into the running application at a time, selected by configuration property, the same way
 * every other outbound port in this SPI is selected.
 *
 * <p>Unlike {@link ActionExecutor}, whose dependency direction {@code
 * org.sequeless.spi.automation.package-info} explains is inverted, {@code AutomationPort} is an
 * ordinary outbound port: {@code sequeless-core}/{@code sequeless-app}'s relay calls it, and an
 * automation adapter (the in-process adapter, or a Temporal adapter) implements it, switching on
 * {@code entry.kind()} and calling back into {@link ActionExecutor}, {@link
 * org.sequeless.spi.automation.TriggerEvaluator}, or {@link
 * org.sequeless.spi.automation.DerivationRecomputer} — whichever inbound port actually applies
 * that kind — to do the work.
 */
public interface AutomationPort {

    /**
     * Durably starts (or, for a synchronous adapter, completes) the handling of {@code entry},
     * dispatched by kind to the appropriate inbound port. A second call whose {@code entry.id()}
     * matches a previously dispatched entry must not cause the underlying inbound-port method to
     * be invoked a second time. The carve-out is {@code TransitionFired}, whose key is the entry id
     * and the action index together: the in-process adapter runs {@code SetProperty} and {@code
     * CreateObject} actions again on a repeat call.
     *
     * <p>A {@link OutboxEntry#KIND_TRANSITION_FIRED} entry applies the transition's actions.
     *
     * @param scope the tenant and principal to dispatch the action on behalf of; must not be
     *     {@code null}
     * @param entry the outbox entry describing the work to dispatch; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code entry.kind()} is not one of {@link
     *     OutboxEntry#KIND_TRANSITION_FIRED}, {@link OutboxEntry#KIND_OBJECT_CREATED}, {@link
     *     OutboxEntry#KIND_OBJECT_UPDATED}, or {@link OutboxEntry#KIND_OBJECT_DELETED}
     * @throws IllegalStateException if a {@code TransitionFired} entry's {@code ontologyVersion}
     *     stamp is missing, or does not match the ontology version of the automation or of the
     *     behaviour that the adapter reads to resolve the fired transition's timers
     */
    void dispatch(Scope scope, OutboxEntry entry);
}
