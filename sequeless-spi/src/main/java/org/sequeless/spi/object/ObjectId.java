package org.sequeless.spi.object;

import java.util.Objects;
import java.util.UUID;

/**
 * The identity of a {@link BusinessObject}: an opaque, tenant-scoped UUID. Identity is deliberately
 * a UUID rather than an ontology-derived key — objects of any type share the same id space, so a
 * {@link ReferenceValue} never needs to carry its target's type alongside the id.
 *
 * @param value the underlying UUID; must not be {@code null}
 */
public record ObjectId(UUID value) {

    public ObjectId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * @return a freshly generated, random {@code ObjectId}
     */
    public static ObjectId random() {
        return new ObjectId(UUID.randomUUID());
    }

    /**
     * @param value the canonical UUID string to parse; must not be {@code null}
     * @return the {@code ObjectId} wrapping the parsed UUID
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is not a valid UUID string, propagated
     *     from {@link UUID#fromString(String)}
     */
    public static ObjectId parse(String value) {
        Objects.requireNonNull(value, "value must not be null");
        return new ObjectId(UUID.fromString(value));
    }
}
