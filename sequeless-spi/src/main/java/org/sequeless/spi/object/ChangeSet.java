package org.sequeless.spi.object;

import java.util.List;
import java.util.Objects;

/**
 * A batch of {@link Mutation}s and {@link OutboxEntry} events to apply atomically in a single
 * {@link ObjectStorePort#commit} call: either every mutation and every outbox entry is persisted,
 * or none is.
 *
 * @param mutations the mutations to apply, in order; must not be {@code null}; may be empty only
 *     if {@code outbox} is not — a {@code SignalReceived}-only commit has no mutation to make but
 *     still has something to durably record — but a {@code ChangeSet} with both empty is a caller
 *     bug, not a valid no-op; returned as an unmodifiable copy so callers cannot mutate this
 *     changeset after construction
 * @param outbox the outbox events to record alongside {@code mutations}; must not be {@code null};
 *     may be empty; returned as an unmodifiable copy so callers cannot mutate this changeset after
 *     construction
 */
public record ChangeSet(List<Mutation> mutations, List<OutboxEntry> outbox) {

    public ChangeSet {
        Objects.requireNonNull(mutations, "mutations must not be null");
        mutations = List.copyOf(mutations);
        Objects.requireNonNull(outbox, "outbox must not be null");
        outbox = List.copyOf(outbox);
        if (mutations.isEmpty() && outbox.isEmpty()) {
            throw new IllegalArgumentException("ChangeSet mutations and outbox must not both be empty");
        }
    }
}
