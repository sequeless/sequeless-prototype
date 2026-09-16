package org.sequeless.app.config;

import java.util.List;
import org.sequeless.app.port.PortDefinition;
import org.sequeless.app.port.PortRegistry;
import org.sequeless.spi.authz.AuthorizationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Declares every {@link PortDefinition} the application must fail-fast validate at startup, and
 * wires the {@link PortRegistry} that validates them.
 *
 * <p>Phase 0 has exactly one port slot: {@code sequeless.authz.adapter} selecting the {@link
 * AuthorizationPort} implementation. Later phases add more entries to {@link #portDefinitions()}
 * without needing to change {@link PortRegistry} itself.
 */
@Configuration(proxyBeanMethods = false)
public class PortRegistryConfiguration {

    /**
     * @return every port slot this application exposes for adapter selection
     */
    @Bean
    public List<PortDefinition> portDefinitions() {
        return List.of(new PortDefinition("sequeless.authz.adapter", AuthorizationPort.class));
    }

    /**
     * @param environment source of each port definition's configured property value
     * @param portDefinitions every port slot to validate; see {@link #portDefinitions()}
     * @return the registry that validates {@code portDefinitions} once all singletons exist
     */
    @Bean
    public PortRegistry portRegistry(Environment environment, List<PortDefinition> portDefinitions) {
        return new PortRegistry(environment, portDefinitions);
    }
}
