package org.sequeless.spi.object;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * The outbound port a relay polls to claim one unprocessed outbox row of a caller-chosen kind at a
 * time, hand it to a handler, and mark it processed — all inside one short transaction, so a slow
 * action never blocks other rows and a crash mid-dispatch simply releases the row's lock for the
 * next poll. Originally shaped around {@link OutboxEntry#KIND_ACTION_REQUEST} alone, this port now
 * claims across whatever set of kinds the caller names — {@code OutboxRelay} passes the full
 * dispatchable set ({@link OutboxEntry#KIND_ACTION_REQUEST}, the three object-lifecycle kinds, and
 * the three automation kinds introduced alongside {@link org.sequeless.spi.meta.TriggerSpec}) —
 * because a single relay now fans one polling loop out to several inbound ports rather than always
 * calling {@code ActionExecutor}. {@link OutboxEntry#KIND_TRANSITION_FIRED} stays audit-only and
 * must never appear in the requested {@code kinds}, since nothing ever claims it.
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
     * Claims at most one unprocessed row whose {@code kind} is in {@code kinds} (conceptually,
     * {@code SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1 WHERE kind = ANY(?)}, ordered by {@code
     * occurredAt}), calls {@code handler} with the row's own raw {@code tenantId} string and the
     * reconstructed {@link OutboxEntry}, then marks the row processed — all inside one short
     * transaction. Returns {@link Optional#empty()} if no unprocessed row of a requested kind is
     * available; in that case {@code handler} is never called.
     *
     * <p>If {@code handler} throws, the transaction rolls back — the row is left unprocessed and
     * its lock is released — and the exception propagates to the caller of this method.
     *
     * @param kinds the {@code OutboxEntry.kind()} values this call is willing to claim; must not be
     *     {@code null} and must not be empty — an empty set would silently claim nothing forever,
     *     which is never what a caller actually wants, so it is rejected eagerly here rather than
     *     left as a confusing always-empty poll loop
     * @param handler called at most once, with the claimed row's raw {@code tenantId} and the
     *     reconstructed {@link OutboxEntry}; must not be {@code null}
     * @param <T> the type {@code handler} returns
     * @return {@link Optional#empty()} if no unprocessed row was available (in which case {@code
     *     handler} is never called); otherwise {@code Optional.ofNullable(handler's return
     *     value)} — note a {@code handler} that legitimately returns {@code null} is therefore
     *     indistinguishable, from this method's return value alone, from "no row was available";
     *     a caller that needs to tell the two apart should have {@code handler} return a
     *     non-{@code null} sentinel
     * @throws NullPointerException if {@code kinds} or {@code handler} is {@code null}
     * @throws IllegalArgumentException if {@code kinds} is empty
     */
    <T> Optional<T> claimNext(Set<String> kinds, BiFunction<String, OutboxEntry, T> handler);
}
