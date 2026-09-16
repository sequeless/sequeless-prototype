package org.sequeless.spi.object;

import java.util.Objects;

/**
 * Thrown by {@link ObjectStorePort#commit} when an {@link Update} or {@link Delete}'s {@code
 * expectedVersion} does not match the object's currently stored version.
 * The whole {@link ChangeSet} the mutation appeared in is rolled back, including any mutations
 * that appeared earlier in the list — a conforming adapter never partially applies a changeset.
 *
 * <p>This is deliberately unchecked, the same way {@code
 * org.sequeless.spi.ontology.OntologyException} is: a stale version is a condition every
 * intermediate signature up the call chain should not have to declare, and the caller (typically a
 * core use case, ultimately the REST boundary) is expected to catch it once, at the edge, to
 * produce an HTTP 409 response.
 */
public final class StaleObjectException extends RuntimeException {

    private final ObjectId objectId;
    private final long expectedVersion;

    /**
     * @param objectId the id of the object whose version did not match; must not be {@code null}
     * @param expectedVersion the version the caller expected the object to be at
     * @throws NullPointerException if {@code objectId} is {@code null}
     */
    public StaleObjectException(ObjectId objectId, long expectedVersion) {
        super("Stale version for " + Objects.requireNonNull(objectId, "objectId must not be null")
            + ": expected " + expectedVersion);
        this.objectId = objectId;
        this.expectedVersion = expectedVersion;
    }

    /**
     * @return the id of the object whose version did not match
     */
    public ObjectId objectId() {
        return objectId;
    }

    /**
     * @return the version the caller expected the object to be at
     */
    public long expectedVersion() {
        return expectedVersion;
    }
}
