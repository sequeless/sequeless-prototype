package org.sequeless.spi.object;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * The outbound port a relay polls to claim one unprocessed {@link OutboxEntry#KIND_ACTION_REQUEST}
 * row at a time, hand it to a handler, and mark it processed — all inside one short transaction,
 * so a slow action never blocks other rows and a crash mid-dispatch simply releases the row's lock
 * for the next poll.
 *
 * <p>Deliberately a sibling port to {@link ObjectStorePort}, not a method added to it: {@code
 * ObjectStorePort} has many implementers (the postgres adapter and several in-memory test doubles)
 * that have no use for an outbox-claiming method, exactly the reasoning that already keeps {@code
 * QueryPort} a sibling port rather than a method on {@code ObjectStorePort}. Exactly one
 * implementation is wired into the running application at a time, selected by configuration
 * property, the same way every other outbound port in this SPI is selected.
 *
 * <p>This port deliberately hands the handler a raw {@code tenantId} {@link String}, not a
 * pre-built {@link org.sequeless.spi.Scope}: it cannot know how the caller wants to construct a
 * {@link org.sequeless.spi.Principal} from the payload's {@code principalId} (display name?
 * roles? a "system" role for authz?) — that policy belongs to whichever relay calls this port, not
 * to this SPI.
 */
public interface OutboxPort {

    /**
     * Claims at most one unprocessed {@link OutboxEntry#KIND_ACTION_REQUEST} row (conceptually,
     * {@code SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1}, ordered by {@code occurredAt}), calls
     * {@code handler} with the row's own raw {@code tenantId} string and the reconstructed {@link
     * OutboxEntry}, then marks the row processed — all inside one short transaction. Returns {@link
     * Optional#empty()} if no unprocessed row is available; in that case {@code handler} is never
     * called.
     *
     * <p>If {@code handler} throws, the transaction rolls back — the row is left unprocessed and
     * its lock is released — and the exception propagates to the caller of this method.
     *
     * @param handler called at most once, with the claimed row's raw {@code tenantId} and the
     *     reconstructed {@link OutboxEntry}; must not be {@code null}
     * @param <T> the type {@code handler} returns
     * @return {@link Optional#empty()} if no unprocessed row was available (in which case {@code
     *     handler} is never called); otherwise {@code Optional.ofNullable(handler's return
     *     value)} — note a {@code handler} that legitimately returns {@code null} is therefore
     *     indistinguishable, from this method's return value alone, from "no row was available";
     *     a caller that needs to tell the two apart should have {@code handler} return a
     *     non-{@code null} sentinel
     * @throws NullPointerException if {@code handler} is {@code null}
     */
    <T> Optional<T> claimNextActionRequest(BiFunction<String, OutboxEntry, T> handler);
}
