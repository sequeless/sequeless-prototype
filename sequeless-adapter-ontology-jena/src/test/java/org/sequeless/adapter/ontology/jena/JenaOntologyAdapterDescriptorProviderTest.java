package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.AdapterDescriptor;
import org.sequeless.spi.AdapterDescriptorProvider;
import org.sequeless.spi.SpiVersion;
import org.sequeless.spi.ontology.OntologyPort;

/**
 * The real proof that {@code META-INF/services/org.sequeless.spi.AdapterDescriptorProvider}'s
 * filename and content are correct: this loads the provider through {@link ServiceLoader} itself,
 * exactly as the application does, rather than constructing {@link
 * JenaOntologyAdapterDescriptorProvider} directly. Instantiating it directly would prove nothing
 * about whether the service file actually wires the provider up for discovery.
 */
class JenaOntologyAdapterDescriptorProviderTest {

    @Test
    void isDiscoverableViaServiceLoaderAndDescribesItselfCorrectly() {
        List<AdapterDescriptorProvider> providers =
            ServiceLoader.load(AdapterDescriptorProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertThat(providers).hasSize(1);

        AdapterDescriptor descriptor = providers.get(0).descriptor();
        assertThat(descriptor.name()).isEqualTo("jena");
        assertThat(descriptor.port()).isEqualTo(OntologyPort.class);
        assertThat(descriptor.property()).isEqualTo("sequeless.ontology.adapter");
        assertThat(descriptor.spiVersionRange()).isEqualTo(">=0.1.0");
        assertThat(SpiVersion.isCompatibleWith(descriptor.spiVersionRange())).isTrue();
    }
}
