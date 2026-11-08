package org.sequeless.spi.meta.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class OntologyVersionTest {

    @Test
    void acceptsTheFirstVersion() {
        assertThat(new OntologyVersion(1).value()).isEqualTo(1);
    }

    @Test
    void equalValuesAreEqual() {
        assertThat(new OntologyVersion(3)).isEqualTo(new OntologyVersion(3));
    }

    @Test
    void rejectsZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OntologyVersion(0));
    }

    @Test
    void rejectsNegativeValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OntologyVersion(-1));
    }
}
