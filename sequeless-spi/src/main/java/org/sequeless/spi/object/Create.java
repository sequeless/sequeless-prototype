package org.sequeless.spi.object;

import java.util.Objects;

/**
 * A {@link Mutation} that creates a new object. The object is expected to be stored at version 1
 * regardless of what {@link BusinessObject#version()} on {@code object} happens to be — {@code
 * commit} always assigns the initial version itself.
 *
 * @param object the object to create; must not be {@code null}
 */
public record Create(BusinessObject object) implements Mutation {

    public Create {
        Objects.requireNonNull(object, "object must not be null");
    }
}
