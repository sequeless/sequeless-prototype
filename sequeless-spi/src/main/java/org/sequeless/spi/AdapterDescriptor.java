package org.sequeless.spi;

import java.util.Objects;

/**
 * Self-description an adapter publishes so the application can discover and validate it before
 * wiring it in, without the SPI or the application needing to know the adapter's concrete
 * implementation class ahead of time.
 *
 * <p>Adapters are selected at runtime by a configuration property naming which implementation of a
 * given port to activate (see the app's {@code PortRegistry}). Before trusting the configured
 * choice, the registry needs three things about each candidate adapter: what it calls itself (
 * {@link #name()}), which port it implements ({@link #port()}), which property selects it ({@link
 * #property()}), and which range of this SPI's versions it was built against ({@link
 * #spiVersionRange()}) — so a mismatch between an adapter and the running SPI fails fast at startup
 * with a clear message, rather than surfacing later as a mysterious {@code NoSuchMethodError}.
 *
 * @param name the adapter's own name, used in the configuration property's value and in error
 *     messages listing available adapters; must not be blank
 * @param port the port interface this adapter implements; must not be {@code null}
 * @param property the configuration property whose value selects this adapter for its port; must
 *     not be blank
 * @param spiVersionRange the range of SPI versions (see {@link SpiVersion}) this adapter was built
 *     against, of the form {@code ">=MAJOR.MINOR.PATCH"}; must be well-formed per {@link
 *     SpiVersion#isValidRange(String)}
 */
public record AdapterDescriptor(String name, Class<?> port, String property, String spiVersionRange) {

    public AdapterDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AdapterDescriptor name must not be blank");
        }
        Objects.requireNonNull(port, "port must not be null");
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("AdapterDescriptor property must not be blank");
        }
        if (!SpiVersion.isValidRange(spiVersionRange)) {
            throw new IllegalArgumentException(
                "Malformed SPI version range (expected '>=MAJOR.MINOR.PATCH'): " + spiVersionRange);
        }
    }
}
