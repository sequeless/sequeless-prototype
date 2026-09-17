package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.AdapterDescriptor;
import org.sequeless.spi.AdapterDescriptorProvider;
import org.sequeless.spi.SpiVersion;
import org.sequeless.spi.validation.ValidationPort;

/**
 * The real proof that {@code META-INF/services/org.sequeless.spi.AdapterDescriptorProvider}'s
 * second line is correct: loads providers through {@link ServiceLoader} itself, exactly as the
 * application does — see {@link JenaOntologyAdapterDescriptorProviderTest} for the sibling proof of
 * this file's first line.
 */
class ShaclValidationAdapterDescriptorProviderTest {

    @Test
    void isDiscoverableViaServiceLoaderAndDescribesItselfCorrectly() {
        List<AdapterDescriptorProvider> providers =
            ServiceLoader.load(AdapterDescriptorProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        AdapterDescriptor descriptor = providers.stream()
            .map(AdapterDescriptorProvider::descriptor)
            .filter(d -> d.name().equals("shacl"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No 'shacl' AdapterDescriptorProvider registered"));

        assertThat(descriptor.name()).isEqualTo("shacl");
        assertThat(descriptor.port()).isEqualTo(ValidationPort.class);
        assertThat(descriptor.property()).isEqualTo("sequeless.validation.adapter");
        assertThat(descriptor.spiVersionRange()).isEqualTo(">=0.1.0");
        assertThat(SpiVersion.isCompatibleWith(descriptor.spiVersionRange())).isTrue();
    }
}
