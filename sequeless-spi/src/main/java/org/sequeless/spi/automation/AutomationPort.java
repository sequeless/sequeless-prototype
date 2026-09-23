package org.sequeless.spi.automation;

import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The outbound port a relay (the in-process poller, or a future Temporal-backed one) calls to
 * durably start — or, for a synchronous adapter, fully complete — the handling of one outbox row,
 * whatever its kind: an {@link OutboxEntry#KIND_ACTION_REQUEST} applies a fired transition's
 * action, the three object-lifecycle kinds trigger a recompute and then trigger-evaluation pass,
 * and the three automation kinds introduced alongside {@link org.sequeless.spi.meta.TriggerSpec}
 * drive a timer or a signal. Exactly one implementation is wired into the running application at a
 * time, selected by configuration property, the same way every other outbound port in this SPI
 * is selected.
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
     * be invoked a second time.
     *
     * @param scope the tenant and principal to dispatch the action on behalf of; must not be
     *     {@code null}
     * @param entry the outbox entry describing the work to dispatch; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code entry.kind()} is not one of {@link
     *     OutboxEntry#KIND_ACTION_REQUEST}, {@link OutboxEntry#KIND_OBJECT_CREATED}, {@link
     *     OutboxEntry#KIND_OBJECT_UPDATED}, {@link OutboxEntry#KIND_OBJECT_DELETED}, {@link
     *     OutboxEntry#KIND_TIMER_SCHEDULED}, {@link OutboxEntry#KIND_TIMER_CANCELLED}, or {@link
     *     OutboxEntry#KIND_SIGNAL_RECEIVED} — {@link OutboxEntry#KIND_TRANSITION_FIRED} is
     *     audit-only and is never dispatched here
     */
    void dispatch(Scope scope, OutboxEntry entry);
}
