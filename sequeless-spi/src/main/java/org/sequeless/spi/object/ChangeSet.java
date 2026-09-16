package org.sequeless.spi.object;

import java.util.List;
import java.util.Objects;

/**
 * A batch of {@link Mutation}s and {@link OutboxEntry} events to apply atomically in a single
 * {@link ObjectStorePort#commit} call: either every mutation and every outbox entry is persisted,
 * or none is.
 *
 * @param mutations the mutations to apply, in order; must not be {@code null}, must not be empty
 *     (a commit with nothing to commit is a caller bug, not a valid no-op); returned as an
 *     unmodifiable copy so callers cannot mutate this changeset after construction
 * @param outbox the outbox events to record alongside {@code mutations}; must not be {@code null};
 *     may be empty; returned as an unmodifiable copy so callers cannot mutate this changeset after
 *     construction
 */
public record ChangeSet(List<Mutation> mutations, List<OutboxEntry> outbox) {

    public ChangeSet {
        Objects.requireNonNull(mutations, "mutations must not be null");
        mutations = List.copyOf(mutations);
        if (mutations.isEmpty()) {
            throw new IllegalArgumentException("ChangeSet mutations must not be empty");
        }
        Objects.requireNonNull(outbox, "outbox must not be null");
        outbox = List.copyOf(outbox);
    }
}
