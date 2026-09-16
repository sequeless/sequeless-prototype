package org.sequeless.spi.object;

import java.util.Objects;

/**
 * Thrown by {@link ObjectStorePort#commit} when an {@link Update} or {@link Delete} targets an
 * {@link ObjectId} that does not exist, or that has already been soft-deleted. The whole {@link
 * ChangeSet} the mutation appeared in is rolled back, the same as for {@link
 * StaleObjectException}.
 *
 * <p>This is deliberately unchecked, for the same reason {@link StaleObjectException} is: the
 * caller is expected to catch it once, at the edge, to produce an HTTP 404 response.
 */
public final class ObjectNotFoundException extends RuntimeException {

    private final ObjectId objectId;

    /**
     * @param objectId the id of the missing or deleted object; must not be {@code null}
     * @throws NullPointerException if {@code objectId} is {@code null}
     */
    public ObjectNotFoundException(ObjectId objectId) {
        super("Object not found or deleted: " + Objects.requireNonNull(objectId, "objectId must not be null"));
        this.objectId = objectId;
    }

    /**
     * @return the id of the missing or deleted object
     */
    public ObjectId objectId() {
        return objectId;
    }
}
