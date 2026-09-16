package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PropertyRefTest {

    @Test
    void carriesIri() {
        assertThat(new PropertyRef("https://example.org/ns#title").iri()).isEqualTo("https://example.org/ns#title");
    }

    @Test
    void rejectsNullIri() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PropertyRef(null));
    }

    @Test
    void rejectsBlankIri() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PropertyRef("   "));
    }
}
