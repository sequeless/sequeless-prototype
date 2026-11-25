/**
 * Outbox relay for sequeless-app.
 *
 * <p>{@link org.sequeless.app.automation.OutboxRelay} is a thin {@code @Scheduled} poller that
 * claims one unprocessed outbox row at a time, of the dispatchable kinds (via {@link
 * org.sequeless.spi.outbox.OutboxPort}): {@code TransitionFired} and the three object-lifecycle
 * kinds. Timers have no kind of their own: the adapter starts them from the {@code TransitionFired}
 * row. The relay maps each row to the Store event it records, an {@code ObjectChangedEvent} or a
 * {@code TransitionFiredEvent}, and feeds that event to the ingress of whichever {@link
 * org.sequeless.spi.automation.AutomationPort} adapter is currently configured. It reconstructs a
 * {@link org.sequeless.kernel.identity.Scope} from the row's payload and passes it alongside the
 * event. This package depends only on {@code sequeless-spi} plus
 * standard Spring/JDK types — it never depends on {@code org.sequeless.core}, unlike {@link
 * org.sequeless.app.config} and {@link org.sequeless.app.rest}, because the relay only ever talks
 * to core-implemented behaviour indirectly, through SPI ports.
 */
package org.sequeless.app.automation;
