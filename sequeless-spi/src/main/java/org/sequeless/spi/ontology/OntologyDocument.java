package org.sequeless.spi.ontology;

import java.util.Objects;

/**
 * A serialised ontology, as read from or written to an external source: the raw text and the
 * format it is written in. {@link OntologyPort#export} produces one of these; {@link
 * OntologyPort#importDocument} consumes one. This type does not interpret {@code content} beyond
 * carrying it — parsing and validation are the adapter's responsibility.
 *
 * @param content the serialised ontology text; must not be blank
 * @param format the serialisation {@code content} is written in; must not be {@code null}
 */
public record OntologyDocument(String content, OntologyFormat format) {

    public OntologyDocument {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("OntologyDocument content must not be blank");
        }
        Objects.requireNonNull(format, "format must not be null");
    }
}
