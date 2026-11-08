package org.sequeless.spi.meta.schema;

/**
 * The version of one ontology activation. The three halves of an activation (Schema, Behaviour and
 * Automation) carry the same version, so a caller can tell whether two reads came from the same
 * activation. An adapter assigns the value: from its document store when it has one, otherwise from
 * an in-memory counter. The administration port's javadoc states how versions move on import and
 * reload.
 *
 * @param value the version number; must be at least 1
 */
public record OntologyVersion(int value) {

    public OntologyVersion {
        if (value < 1) {
            throw new IllegalArgumentException("OntologyVersion value must be at least 1");
        }
    }
}
