package org.sequeless.adapter.ontology.jena;

import org.sequeless.spi.AdapterDescriptor;
import org.sequeless.spi.AdapterDescriptorProvider;
import org.sequeless.spi.ontology.OntologyPort;

/**
 * Publishes this module's {@link AdapterDescriptor} for discovery via {@link
 * java.util.ServiceLoader}, registered under {@code
 * META-INF/services/org.sequeless.spi.AdapterDescriptorProvider}. Has zero Spring imports, like
 * {@link JenaOntologyPort}: the descriptor must be discoverable before any Spring context exists,
 * since the application uses it to validate an adapter before deciding whether to even start wiring
 * Spring beans for it.
 *
 * <p>Requires the public no-arg constructor implicit here, since {@code ServiceLoader} instantiates
 * service implementations reflectively that way.
 */
public final class JenaOntologyAdapterDescriptorProvider implements AdapterDescriptorProvider {

    @Override
    public AdapterDescriptor descriptor() {
        return new AdapterDescriptor("jena", OntologyPort.class, "sequeless.ontology.adapter", ">=0.1.0");
    }
}
