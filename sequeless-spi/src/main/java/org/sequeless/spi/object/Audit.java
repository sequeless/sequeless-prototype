package org.sequeless.spi.object;

import java.time.Instant;
import java.util.Objects;

/**
 * Who created and last modified a {@link BusinessObject}, and when. {@code createdAt}/{@code
 * createdBy} never change after a {@code Create}; {@code updatedAt}/{@code updatedBy} are set on
 * every subsequent {@code Update} or {@code Delete}.
 *
 * @param createdAt when the object was created; must not be {@code null}
 * @param createdBy the principal that created the object; must not be blank
 * @param updatedAt when the object was last modified; must not be {@code null}
 * @param updatedBy the principal that last modified the object; must not be blank
 */
public record Audit(Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {

    public Audit {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("Audit createdBy must not be blank");
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedBy == null || updatedBy.isBlank()) {
            throw new IllegalArgumentException("Audit updatedBy must not be blank");
        }
    }
}
