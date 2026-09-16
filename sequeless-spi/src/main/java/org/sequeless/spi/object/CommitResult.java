package org.sequeless.spi.object;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of a successful {@link ObjectStorePort#commit}: every affected object at its
 * post-commit version, and the ids of every {@link OutboxEntry} that was persisted alongside them.
 *
 * @param objects the affected objects, reflecting post-commit versions (1 for each {@link Create},
 *     {@code expectedVersion + 1} for each {@link Update} or {@link Delete}); must not be {@code
 *     null}; returned as an unmodifiable copy so callers cannot mutate this result after
 *     construction
 * @param outboxIds the ids of the persisted outbox rows, in the same order as the committed {@link
 *     ChangeSet}'s {@code outbox()}; must not be {@code null}; returned as an unmodifiable copy so
 *     callers cannot mutate this result after construction
 */
public record CommitResult(List<BusinessObject> objects, List<UUID> outboxIds) {

    public CommitResult {
        Objects.requireNonNull(objects, "objects must not be null");
        objects = List.copyOf(objects);
        Objects.requireNonNull(outboxIds, "outboxIds must not be null");
        outboxIds = List.copyOf(outboxIds);
    }
}
