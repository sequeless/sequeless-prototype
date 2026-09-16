package org.sequeless.spi.object;

import java.util.Objects;

/**
 * A property value that points at another {@link BusinessObject}. This record carries only the
 * target's id; resolving whether the target exists, is deleted, or has the expected type is the
 * caller's responsibility, typically via {@link ObjectStorePort#typesOf}.
 *
 * @param target the referenced object's id; must not be {@code null}
 */
public record ReferenceValue(ObjectId target) implements Value {

    public ReferenceValue {
        Objects.requireNonNull(target, "target must not be null");
    }
}
