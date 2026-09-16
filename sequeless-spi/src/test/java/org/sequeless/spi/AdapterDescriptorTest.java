package org.sequeless.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AdapterDescriptorTest {

    @Test
    void constructsWithValidComponents() {
        AdapterDescriptor descriptor =
            new AdapterDescriptor("permit-all", AdapterDescriptorProvider.class, "sequeless.authz.adapter", ">=0.1.0");

        assertThat(descriptor.name()).isEqualTo("permit-all");
        assertThat(descriptor.port()).isEqualTo(AdapterDescriptorProvider.class);
        assertThat(descriptor.property()).isEqualTo("sequeless.authz.adapter");
        assertThat(descriptor.spiVersionRange()).isEqualTo(">=0.1.0");
    }

    @Test
    void rejectsNullOrBlankName() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AdapterDescriptor(null, AdapterDescriptorProvider.class, "prop", ">=0.1.0"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AdapterDescriptor("  ", AdapterDescriptorProvider.class, "prop", ">=0.1.0"));
    }

    @Test
    void rejectsNullPort() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AdapterDescriptor("permit-all", null, "prop", ">=0.1.0"));
    }

    @Test
    void rejectsNullOrBlankProperty() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AdapterDescriptor("permit-all", AdapterDescriptorProvider.class, null, ">=0.1.0"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AdapterDescriptor("permit-all", AdapterDescriptorProvider.class, "  ", ">=0.1.0"));
    }

    @Test
    void rejectsMalformedSpiVersionRange() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AdapterDescriptor("permit-all", AdapterDescriptorProvider.class, "prop", "0.1.0"))
            .withMessageContaining("0.1.0");
    }

    @Test
    void rejectsNullSpiVersionRange() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AdapterDescriptor("permit-all", AdapterDescriptorProvider.class, "prop", null));
    }
}
