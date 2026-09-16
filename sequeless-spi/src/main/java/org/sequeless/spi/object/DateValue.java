package org.sequeless.spi.object;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A {@code date} property value, with no time-of-day or zone component.
 *
 * @param value the date; must not be {@code null}
 */
public record DateValue(LocalDate value) implements Value {

    public DateValue {
        Objects.requireNonNull(value, "value must not be null");
    }
}
