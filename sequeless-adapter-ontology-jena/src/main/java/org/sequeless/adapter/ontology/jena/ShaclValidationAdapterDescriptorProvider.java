package org.sequeless.adapter.ontology.jena;

import org.sequeless.spi.AdapterDescriptor;
import org.sequeless.spi.AdapterDescriptorProvider;
import org.sequeless.spi.validation.ValidationPort;

/**
 * Publishes {@link ShaclValidationPort}'s {@link AdapterDescriptor} for discovery via {@link
 * java.util.ServiceLoader}, registered under {@code
 * META-INF/services/org.sequeless.spi.AdapterDescriptorProvider} alongside {@link
 * JenaOntologyAdapterDescriptorProvider} — see that class's javadoc for why this has zero Spring
 * imports and a public no-arg constructor.
 */
public final class ShaclValidationAdapterDescriptorProvider implements AdapterDescriptorProvider {

    @Override
    public AdapterDescriptor descriptor() {
        return new AdapterDescriptor("shacl", ValidationPort.class, "sequeless.validation.adapter", ">=0.1.0");
    }
}
