package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TypeRefTest {

    @Test
    void carriesIri() {
        assertThat(new TypeRef("https://example.org/ns#Task").iri()).isEqualTo("https://example.org/ns#Task");
    }

    @Test
    void rejectsNullIri() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TypeRef(null));
    }

    @Test
    void rejectsBlankIri() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TypeRef("   "));
    }
}
