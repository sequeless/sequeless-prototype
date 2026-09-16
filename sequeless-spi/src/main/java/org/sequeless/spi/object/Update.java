package org.sequeless.spi.object;

import java.util.Objects;

/**
 * A {@link Mutation} that replaces an existing object's properties, subject to optimistic
 * locking. The stored object must currently be at {@code expectedVersion}, or {@code commit}
 * throws {@link StaleObjectException} and rolls back the whole {@link ChangeSet}. On success the
 * object is stored at {@code expectedVersion + 1}.
 *
 * @param object the object's new state; must not be {@code null}
 * @param expectedVersion the version the stored object is expected to currently be at; must be at
 *     least 1
 */
public record Update(BusinessObject object, long expectedVersion) implements Mutation {

    public Update {
        Objects.requireNonNull(object, "object must not be null");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("Update expectedVersion must be at least 1");
        }
    }
}
