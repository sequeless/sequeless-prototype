package org.sequeless.app.automation;

import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.StoreEvent;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TransitionFiredEvent;

/**
 * Maps a claimed outbox row, as the released {@link StoreEventRow}, to the Store event it records.
 *
 * <p>Replaces the prototype's spi.automation Store event copies, deleted in spec 17 P4. The released types are in
 * sequeless/sequeless-automation at 5159284 ({@code org.sequeless.automation.api}).
 *
 * <p>The three object-lifecycle kinds map to an {@link ObjectChangedEvent} with the matching change, and
 * {@link StoreEventRow#KIND_TRANSITION_FIRED} maps to a {@link TransitionFiredEvent}. The event id is the row id,
 * unchanged: the released adapters derive their idempotency keys and workflow ids from it, so a changed id would
 * break redelivery.
 *
 * <p>The mapping lives in the host relay, because the relay is the one place that reads the persisted kind string.
 * A kind outside the Store event kinds is rejected with {@link IllegalArgumentException}. The relay claims only those
 * kinds, so that branch is unreachable in practice.
 */
final class StoreEvents {

    private StoreEvents() {}

    /**
     * @param row a claimed outbox row of a dispatchable kind, as {@link StoreEventRow}; must not be {@code null}
     * @return the matching {@link ObjectChangedEvent} or {@link TransitionFiredEvent}, carrying the row's id, payload
     *     and {@code occurredAt}
     * @throws IllegalArgumentException if the row's kind is not a Store event kind
     */
    static StoreEvent from(StoreEventRow row) {
        return switch (row.kind()) {
            case StoreEventRow.KIND_OBJECT_CREATED ->
                new ObjectChangedEvent(row.id(), ObjectChangedEvent.Change.CREATED, row.payload(), row.occurredAt());
            case StoreEventRow.KIND_OBJECT_UPDATED ->
                new ObjectChangedEvent(row.id(), ObjectChangedEvent.Change.UPDATED, row.payload(), row.occurredAt());
            case StoreEventRow.KIND_OBJECT_DELETED ->
                new ObjectChangedEvent(row.id(), ObjectChangedEvent.Change.DELETED, row.payload(), row.occurredAt());
            case StoreEventRow.KIND_TRANSITION_FIRED ->
                new TransitionFiredEvent(row.id(), row.payload(), row.occurredAt());
            default -> throw new IllegalArgumentException("not a Store event kind: " + row.kind());
        };
    }
}
