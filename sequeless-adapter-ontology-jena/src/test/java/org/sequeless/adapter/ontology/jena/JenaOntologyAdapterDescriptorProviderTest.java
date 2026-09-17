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
 *
 * <p>Since T8, this module also registers {@link ShaclValidationAdapterDescriptorProvider} in the
 * same service file — see {@code ShaclValidationAdapterDescriptorProviderTest} for that one's own
 * proof; this test asserts on the {@code "jena"} descriptor specifically, tolerating the second
 * provider rather than asserting an exact list size of one.
 */
class JenaOntologyAdapterDescriptorProviderTest {

    @Test
    void isDiscoverableViaServiceLoaderAndDescribesItselfCorrectly() {
        List<AdapterDescriptorProvider> providers =
            ServiceLoader.load(AdapterDescriptorProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        AdapterDescriptor descriptor = providers.stream()
            .map(AdapterDescriptorProvider::descriptor)
            .filter(d -> d.name().equals("jena"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No 'jena' AdapterDescriptorProvider registered"));

        assertThat(descriptor.name()).isEqualTo("jena");
        assertThat(descriptor.port()).isEqualTo(OntologyPort.class);
        assertThat(descriptor.property()).isEqualTo("sequeless.ontology.adapter");
        assertThat(descriptor.spiVersionRange()).isEqualTo(">=0.1.0");
        assertThat(SpiVersion.isCompatibleWith(descriptor.spiVersionRange())).isTrue();
    }
}
