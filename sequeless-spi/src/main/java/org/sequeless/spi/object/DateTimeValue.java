package org.sequeless.spi.object;

import java.time.Instant;
import java.util.Objects;

/**
 * A {@code dateTime} property value, stored and returned in UTC. {@link Instant} carries no zone
 * offset of its own, so no explicit UTC coercion happens here; callers are expected to have already
 * normalized any offset-bearing input to UTC before constructing this value.
 *
 * @param value the point in time, in UTC; must not be {@code null}
 */
public record DateTimeValue(Instant value) implements Value {

    public DateTimeValue {
        Objects.requireNonNull(value, "value must not be null");
    }
}
