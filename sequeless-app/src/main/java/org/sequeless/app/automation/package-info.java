/**
 * Outbox relay for sequeless-app.
 *
 * <p>{@link org.sequeless.app.automation.OutboxRelay} is a thin {@code @Scheduled} poller that
 * claims one unprocessed {@code ActionRequest} outbox row at a time (via {@link
 * org.sequeless.spi.object.OutboxPort}), reconstructs a {@link org.sequeless.spi.Scope} from the
 * row's payload, and hands both to whichever {@link org.sequeless.spi.automation.AutomationPort}
 * adapter is currently configured. This package depends only on {@code sequeless-spi} plus
 * standard Spring/JDK types — it never depends on {@code org.sequeless.core}, unlike {@link
 * org.sequeless.app.config} and {@link org.sequeless.app.rest}, because the relay only ever talks
 * to core-implemented behaviour indirectly, through SPI ports.
 */
package org.sequeless.app.automation;
