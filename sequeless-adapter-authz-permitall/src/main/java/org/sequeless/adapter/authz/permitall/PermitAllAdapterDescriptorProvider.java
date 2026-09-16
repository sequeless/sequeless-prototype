package org.sequeless.adapter.authz.permitall;

import org.sequeless.spi.AdapterDescriptor;
import org.sequeless.spi.AdapterDescriptorProvider;
import org.sequeless.spi.authz.AuthorizationPort;

/**
 * Publishes this module's {@link AdapterDescriptor} for discovery via {@link
 * java.util.ServiceLoader}, registered under {@code
 * META-INF/services/org.sequeless.spi.AdapterDescriptorProvider}. Has zero Spring imports, like
 * {@link PermitAllAuthorizationPort}: the descriptor must be discoverable before any Spring
 * context exists, since the application uses it to validate an adapter before deciding whether to
 * even start wiring Spring beans for it.
 *
 * <p>Requires the public no-arg constructor implicit here, since {@code ServiceLoader}
 * instantiates service implementations reflectively that way.
 */
public final class PermitAllAdapterDescriptorProvider implements AdapterDescriptorProvider {

    @Override
    public AdapterDescriptor descriptor() {
        return new AdapterDescriptor(
            "permit-all", AuthorizationPort.class, "sequeless.authz.adapter", ">=0.1.0");
    }
}
