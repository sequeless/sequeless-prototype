package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;

class StoredOntologyDocumentTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");
    private static final OntologyDocument DOCUMENT =
        new OntologyDocument("@prefix ex: <https://example.org/> .", OntologyFormat.TURTLE);

    @Test
    void carriesAllFields() {
        UUID id = UUID.randomUUID();

        StoredOntologyDocument stored = new StoredOntologyDocument(id, 1, DOCUMENT, NOW);

        assertThat(stored.id()).isEqualTo(id);
        assertThat(stored.version()).isEqualTo(1);
        assertThat(stored.document()).isEqualTo(DOCUMENT);
        assertThat(stored.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsNullId() {
        assertThatNullPointerException().isThrownBy(() -> new StoredOntologyDocument(null, 1, DOCUMENT, NOW));
    }

    @Test
    void rejectsVersionLessThanOne() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new StoredOntologyDocument(UUID.randomUUID(), 0, DOCUMENT, NOW));
    }

    @Test
    void rejectsNullDocument() {
        assertThatNullPointerException().isThrownBy(() -> new StoredOntologyDocument(UUID.randomUUID(), 1, null, NOW));
    }

    @Test
    void rejectsNullCreatedAt() {
        assertThatNullPointerException()
            .isThrownBy(() -> new StoredOntologyDocument(UUID.randomUUID(), 1, DOCUMENT, null));
    }
}
