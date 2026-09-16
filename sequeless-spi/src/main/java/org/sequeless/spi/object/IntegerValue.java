package org.sequeless.spi.object;

/**
 * An {@code INTEGER} or {@code LONG} property value.
 *
 * @param value the integral value
 */
public record IntegerValue(long value) implements Value {
}
