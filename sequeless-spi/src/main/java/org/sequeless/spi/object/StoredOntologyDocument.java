package org.sequeless.spi.object;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.sequeless.spi.ontology.OntologyDocument;

/**
 * A single versioned ontology document as persisted by an {@link OntologyDocumentStore}: its
 * identity, the version number {@link OntologyDocumentStore#activate} assigned it, the serialised
 * document itself, and when it was activated.
 *
 * @param id the identity of this stored document; must not be {@code null}
 * @param version the version number this document was activated at; {@link
 *     OntologyDocumentStore#activate} assigns versions starting at 1 and increasing by one per
 *     tenant, so any value less than 1 is rejected
 * @param document the serialised ontology this version holds; must not be {@code null}
 * @param createdAt when this version was activated; must not be {@code null}
 */
public record StoredOntologyDocument(UUID id, int version, OntologyDocument document, Instant createdAt) {

    public StoredOntologyDocument {
        Objects.requireNonNull(id, "id must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("StoredOntologyDocument version must be at least 1");
        }
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
