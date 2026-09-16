package org.sequeless.spi.validation;

import java.util.Objects;

/**
 * A single validation failure produced by a {@link ValidationPort}: which property it applies to,
 * and a human-readable explanation.
 *
 * @param path the IRI of the property the violation applies to, or {@code ""} for a violation that
 *     applies to the object as a whole rather than to any single property; must not be {@code null}
 * @param message a human-readable explanation of the violation; must not be blank
 */
public record Violation(String path, String message) {

    public Violation {
        Objects.requireNonNull(path, "path must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Violation message must not be blank");
        }
    }
}
