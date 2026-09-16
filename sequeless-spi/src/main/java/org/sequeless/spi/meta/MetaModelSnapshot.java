package org.sequeless.spi.meta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.ontology.OntologyReport;

/**
 * The whole ontology as the application sees it at a point in time: every {@link TypeDefinition},
 * the ontology's identity, its declared namespace prefixes, and the {@link OntologyReport} produced
 * while building it. This is what {@code OntologyPort.snapshot()} and {@code reload()} return, and
 * what a UI or authorization decision consults to answer "what types exist, and what do they look
 * like" without ever touching an RDF triple directly.
 *
 * <p>Types are also reachable by short name via {@link #typeByName(String)}: the local name after
 * an IRI's last {@code #}, or after its last {@code /} if there is no {@code #}. This is
 * deliberately <em>not</em> derived from {@link #prefixes()} — prefixes are a Turtle-export display
 * concern, and keying lookup off them would make {@link #typeByName(String)} depend on which
 * prefixes happen to be declared in a given document. Short names must be unique across a snapshot's
 * types; a collision is rejected at construction, naming both colliding IRIs, because a snapshot
 * with an ambiguous short name could silently resolve a lookup to the wrong type.
 *
 * @param ontologyIri the IRI of the {@code owl:Ontology} this snapshot was built from; must not be
 *     blank
 * @param versionIri the ontology's {@code owl:versionIRI}, if declared; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 * @param prefixes the namespace prefixes declared in the source document, keyed by prefix; must not
 *     be {@code null}; returned as an unmodifiable copy so callers cannot mutate a snapshot after
 *     construction
 * @param types every type in the ontology; must not be {@code null}; returned as an unmodifiable
 *     copy so callers cannot mutate a snapshot after construction; short names derived from {@link
 *     TypeDefinition#iri()} must be unique across this list
 * @param report the outcome of validating the ontology this snapshot was built from; must not be
 *     {@code null}
 */
public record MetaModelSnapshot(
    String ontologyIri,
    Optional<String> versionIri,
    Map<String, String> prefixes,
    List<TypeDefinition> types,
    OntologyReport report) {

    public MetaModelSnapshot {
        if (ontologyIri == null || ontologyIri.isBlank()) {
            throw new IllegalArgumentException("MetaModelSnapshot ontologyIri must not be blank");
        }
        Objects.requireNonNull(versionIri, "versionIri must not be null");
        Objects.requireNonNull(prefixes, "prefixes must not be null");
        prefixes = Map.copyOf(prefixes);
        Objects.requireNonNull(types, "types must not be null");
        types = List.copyOf(types);
        Objects.requireNonNull(report, "report must not be null");

        Map<String, String> shortNameToIri = new HashMap<>();
        for (TypeDefinition type : types) {
            String shortName = shortName(type.iri());
            String existingIri = shortNameToIri.putIfAbsent(shortName, type.iri());
            if (existingIri != null) {
                throw new IllegalArgumentException(
                    "Duplicate short name '" + shortName + "' for IRIs " + existingIri + " and "
                        + type.iri());
            }
        }
    }

    /**
     * @param iri the type's full IRI; must not be {@code null}
     * @return the matching {@link TypeDefinition}, or {@link Optional#empty()} if no type in this
     *     snapshot has that IRI
     * @throws NullPointerException if {@code iri} is {@code null}
     */
    public Optional<TypeDefinition> type(String iri) {
        Objects.requireNonNull(iri, "iri must not be null");
        return types.stream().filter(type -> type.iri().equals(iri)).findFirst();
    }

    /**
     * @param shortName the type's short name, per this class's short-name rule; must not be
     *     {@code null}
     * @return the matching {@link TypeDefinition}, or {@link Optional#empty()} if no type in this
     *     snapshot has that short name
     * @throws NullPointerException if {@code shortName} is {@code null}
     */
    public Optional<TypeDefinition> typeByName(String shortName) {
        Objects.requireNonNull(shortName, "shortName must not be null");
        return types.stream().filter(type -> shortName(type.iri()).equals(shortName)).findFirst();
    }

    /**
     * @return the short name of every type in this snapshot, in the same order as {@link #types()}
     */
    public List<String> names() {
        return types.stream().map(type -> shortName(type.iri())).toList();
    }

    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
    }
}
