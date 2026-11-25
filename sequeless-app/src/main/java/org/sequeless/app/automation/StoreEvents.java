package org.sequeless.app.automation;

import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.StoreEvent;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * Maps an outbox row of a dispatchable kind to the Store event it records.
 *
 * <p>The three object-lifecycle kinds map to an {@link ObjectChangedEvent} with the matching {@link
 * ObjectChangedEvent.Change}, and {@link OutboxEntry#KIND_TRANSITION_FIRED} maps to a {@link
 * TransitionFiredEvent}. The event id is the row id, unchanged: the automation adapters derive their
 * idempotency keys and workflow ids from it, so a changed id would break redelivery.
 *
 * <p>The mapping lives in the host relay, not in the SPI, because the relay is the one place that
 * reads the persisted kind string (spec #20 point 4: "the relay stays in the host"). Keeping the
 * mapping here means {@link StoreEvent} does not depend on the outbox row type. A kind outside the
 * dispatchable set is rejected with {@link IllegalArgumentException}; the relay claims only those
 * kinds, so that branch is unreachable in practice, and a throw here counts as a failed dispatch.
 */
final class StoreEvents {

    private StoreEvents() {}

    /**
     * Returns the Store event that {@code entry} records.
     *
     * @param entry a claimed outbox row of a dispatchable kind; must not be {@code null}
     * @return the matching {@link ObjectChangedEvent} or {@link TransitionFiredEvent}, carrying the
     *     row's id, payload and {@code occurredAt}
     * @throws IllegalArgumentException if the row's kind is not a Store event kind
     */
    static StoreEvent from(OutboxEntry entry) {
        return switch (entry.kind()) {
            case OutboxEntry.KIND_OBJECT_CREATED ->
                new ObjectChangedEvent(
                        entry.id(), ObjectChangedEvent.Change.CREATED, entry.payload(), entry.occurredAt());
            case OutboxEntry.KIND_OBJECT_UPDATED ->
                new ObjectChangedEvent(
                        entry.id(), ObjectChangedEvent.Change.UPDATED, entry.payload(), entry.occurredAt());
            case OutboxEntry.KIND_OBJECT_DELETED ->
                new ObjectChangedEvent(
                        entry.id(), ObjectChangedEvent.Change.DELETED, entry.payload(), entry.occurredAt());
            case OutboxEntry.KIND_TRANSITION_FIRED ->
                new TransitionFiredEvent(entry.id(), entry.payload(), entry.occurredAt());
            default -> throw new IllegalArgumentException("not a Store event kind: " + entry.kind());
        };
    }
}
