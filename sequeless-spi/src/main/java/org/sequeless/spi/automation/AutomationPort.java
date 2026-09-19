package org.sequeless.spi.automation;

import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The outbound port a relay (the in-process poller, or a future Temporal-backed one) calls to
 * durably start — or, for a synchronous adapter, fully complete — one {@code ActionRequest}
 * outbox row's action. Exactly one implementation is wired into the running application at a
 * time, selected by configuration property, the same way every other outbound port in this SPI
 * is selected.
 *
 * <p>Unlike {@link ActionExecutor}, whose dependency direction {@code
 * org.sequeless.spi.automation.package-info} explains is inverted, {@code AutomationPort} is an
 * ordinary outbound port: {@code sequeless-core}/{@code sequeless-app}'s relay calls it, and an
 * automation adapter (the in-process adapter, or a Temporal adapter) implements it, calling back
 * into {@link ActionExecutor} to actually apply the action.
 */
public interface AutomationPort {

    /**
     * Durably starts (or, for a synchronous adapter, completes) the action described by {@code
     * entry}. A second call whose {@code entry.id()} matches a previously dispatched entry must
     * not cause the underlying {@link ActionExecutor} method to be invoked a second time.
     *
     * @param scope the tenant and principal to dispatch the action on behalf of; must not be
     *     {@code null}
     * @param entry the {@code ActionRequest}-kind outbox entry describing the action to dispatch;
     *     must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code entry.kind()} is not {@link
     *     OutboxEntry#KIND_ACTION_REQUEST}
     */
    void dispatch(Scope scope, OutboxEntry entry);
}
