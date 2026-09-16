package org.sequeless.spi.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class OntologyDocumentTest {

    @Test
    void carriesContentAndFormat() {
        OntologyDocument document = new OntologyDocument("@prefix ex: <https://example.org/> .", OntologyFormat.TURTLE);

        assertThat(document.content()).isEqualTo("@prefix ex: <https://example.org/> .");
        assertThat(document.format()).isEqualTo(OntologyFormat.TURTLE);
    }

    @Test
    void rejectsNullContent() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OntologyDocument(null, OntologyFormat.TURTLE));
    }

    @Test
    void rejectsBlankContent() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OntologyDocument("   ", OntologyFormat.TURTLE));
    }

    @Test
    void rejectsNullFormat() {
        assertThatNullPointerException().isThrownBy(() -> new OntologyDocument("content", null));
    }
}
