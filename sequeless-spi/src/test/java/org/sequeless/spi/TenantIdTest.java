package org.sequeless.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void defaultTenantValueIsExactlyDefault() {
        assertThat(TenantId.DEFAULT.value()).isEqualTo("default");
    }

    @Test
    void rejectsNullValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TenantId(null));
    }

    @Test
    void rejectsBlankValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TenantId(""));
        assertThatIllegalArgumentException().isThrownBy(() -> new TenantId("   "));
    }

    @Test
    void equalValuesAreEqualRecords() {
        assertThat(new TenantId("acme")).isEqualTo(new TenantId("acme"));
    }
}
