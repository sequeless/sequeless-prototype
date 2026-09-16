package org.sequeless.spi.object;

import java.time.Instant;
import java.util.Objects;

/**
 * A {@link Mutation} that soft-deletes an object, subject to optimistic locking. The stored object
 * must currently be at {@code expectedVersion}, or {@code commit} throws {@link
 * StaleObjectException} and rolls back the whole {@link ChangeSet}. On success the object's
 * deletion timestamp is set to {@code at}, its audit trail is updated to {@code by}, and its
 * version is bumped to {@code expectedVersion + 1}.
 *
 * @param id the object to delete; must not be {@code null}
 * @param expectedVersion the version the stored object is expected to currently be at; must be at
 *     least 1
 * @param at when the deletion occurs; must not be {@code null}
 * @param by the principal performing the deletion; must not be blank
 */
public record Delete(ObjectId id, long expectedVersion, Instant at, String by) implements Mutation {

    public Delete {
        Objects.requireNonNull(id, "id must not be null");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("Delete expectedVersion must be at least 1");
        }
        Objects.requireNonNull(at, "at must not be null");
        if (by == null || by.isBlank()) {
            throw new IllegalArgumentException("Delete by must not be blank");
        }
    }
}
