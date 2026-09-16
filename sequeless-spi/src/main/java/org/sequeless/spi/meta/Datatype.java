package org.sequeless.spi.meta;

import java.util.Objects;
import java.util.Optional;

/**
 * The scalar value types an {@link AttributeDefinition} can hold, each backed by the XSD datatype
 * IRI the Jena adapter's {@code SnapshotMapper} reads off an {@code rdfs:range} axiom. This is a
 * closed set matching the datatypes the phase-1 reference ontology and OWL → snapshot mapping
 * require; an unrecognised {@code rdfs:range} IRI falls back to {@link #STRING} plus a {@link
 * org.sequeless.spi.ontology.Severity#WARNING} issue rather than failing the whole mapping.
 */
public enum Datatype {

    /** {@code xsd:string}. */
    STRING("http://www.w3.org/2001/XMLSchema#string"),

    /** {@code xsd:boolean}. */
    BOOLEAN("http://www.w3.org/2001/XMLSchema#boolean"),

    /** {@code xsd:integer}. */
    INTEGER("http://www.w3.org/2001/XMLSchema#integer"),

    /** {@code xsd:long}. */
    LONG("http://www.w3.org/2001/XMLSchema#long"),

    /** {@code xsd:decimal}. */
    DECIMAL("http://www.w3.org/2001/XMLSchema#decimal"),

    /** {@code xsd:double}. */
    DOUBLE("http://www.w3.org/2001/XMLSchema#double"),

    /** {@code xsd:date}. */
    DATE("http://www.w3.org/2001/XMLSchema#date"),

    /** {@code xsd:time}. */
    TIME("http://www.w3.org/2001/XMLSchema#time"),

    /** {@code xsd:dateTime}. */
    DATE_TIME("http://www.w3.org/2001/XMLSchema#dateTime"),

    /** {@code xsd:duration}. */
    DURATION("http://www.w3.org/2001/XMLSchema#duration"),

    /** {@code xsd:anyURI}. */
    ANY_URI("http://www.w3.org/2001/XMLSchema#anyURI");

    private final String xsdIri;

    Datatype(String xsdIri) {
        this.xsdIri = xsdIri;
    }

    /**
     * @return the full XSD datatype IRI this constant maps to, e.g. {@code
     *     "http://www.w3.org/2001/XMLSchema#string"}
     */
    public String xsdIri() {
        return xsdIri;
    }

    /**
     * Looks up the {@code Datatype} whose {@link #xsdIri()} equals {@code iri}. Used by the Jena
     * adapter's {@code SnapshotMapper} to translate an {@code rdfs:range} IRI into a {@code
     * Datatype}; an empty result signals the caller should fall back to {@link #STRING} and record
     * a warning, rather than fail the mapping outright.
     *
     * @param iri the candidate XSD datatype IRI; must not be {@code null}
     * @return the matching {@code Datatype}, or {@link Optional#empty()} if {@code iri} does not
     *     match any known XSD datatype IRI
     * @throws NullPointerException if {@code iri} is {@code null}
     */
    public static Optional<Datatype> fromXsd(String iri) {
        Objects.requireNonNull(iri, "iri must not be null");
        for (Datatype datatype : values()) {
            if (datatype.xsdIri.equals(iri)) {
                return Optional.of(datatype);
            }
        }
        return Optional.empty();
    }
}
