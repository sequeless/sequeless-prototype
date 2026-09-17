package org.sequeless.app.config;

import java.util.List;
import org.sequeless.app.port.PortDefinition;
import org.sequeless.app.port.PortRegistry;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.validation.ValidationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Declares every {@link PortDefinition} the application must fail-fast validate at startup, and
 * wires the {@link PortRegistry} that validates them.
 *
 * <p>Four port slots exist today: {@code sequeless.authz.adapter} selecting the {@link
 * AuthorizationPort} implementation, {@code sequeless.ontology.adapter} selecting the {@link
 * OntologyPort} implementation, {@code sequeless.persistence.adapter} selecting the {@link
 * ObjectStorePort} implementation, and {@code sequeless.validation.adapter} selecting the {@link
 * ValidationPort} implementation. Adding each one required no change to {@link PortRegistry}
 * itself — which was the point of making it iterate a list — and later phases add further entries
 * here the same way.
 */
@Configuration(proxyBeanMethods = false)
public class PortRegistryConfiguration {

    /**
     * @return every port slot this application exposes for adapter selection
     */
    @Bean
    public List<PortDefinition> portDefinitions() {
        return List.of(
            new PortDefinition("sequeless.authz.adapter", AuthorizationPort.class),
            new PortDefinition("sequeless.ontology.adapter", OntologyPort.class),
            new PortDefinition("sequeless.persistence.adapter", ObjectStorePort.class),
            new PortDefinition("sequeless.validation.adapter", ValidationPort.class));
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
