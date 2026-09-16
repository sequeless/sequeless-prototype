package org.sequeless.app.port;

import java.util.Objects;

/**
 * Describes one port slot the application must fail-fast validate at startup: a configuration
 * property whose value selects an adapter implementing a given port interface.
 *
 * <p>{@link PortRegistry} is constructed with a {@code List<PortDefinition>} — one per port the
 * application exposes for adapter selection. Phase 0 declares exactly one, for {@code
 * sequeless.authz.adapter} / {@link org.sequeless.spi.authz.AuthorizationPort}, in {@code
 * org.sequeless.app.config.PortRegistryConfiguration}; later phases add more without changing
 * {@link PortRegistry} itself.
 *
 * @param property the configuration property whose value names the adapter to use for {@link
 *     #portType()}; must not be blank
 * @param portType the port interface the named adapter must implement; must not be {@code null}
 */
public record PortDefinition(String property, Class<?> portType) {

    public PortDefinition {
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("PortDefinition property must not be blank");
        }
        Objects.requireNonNull(portType, "portType must not be null");
    }
}
