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
 * event. The relay depends only on {@code sequeless-spi}, the released automation API and standard
 * Spring/JDK types. It never depends on {@code org.sequeless.core}.
 *
 * <p>The host's bindings of the released {@code ObjectReadPort} and {@code StoreCommandPort}
 * ({@link org.sequeless.app.automation.BusinessObjectReadPort}, {@link
 * org.sequeless.app.automation.BusinessObjectStoreCommandPort}) are the exception. They call the Command
 * use cases in {@code sequeless-core}, so that validation, authorization, provenance and the outbox write
 * run as on the HTTP path (spec 17, section 10, Q3). The rule that allows this is in {@code
 * ArchitectureTest}.
 */
package org.sequeless.app.automation;
