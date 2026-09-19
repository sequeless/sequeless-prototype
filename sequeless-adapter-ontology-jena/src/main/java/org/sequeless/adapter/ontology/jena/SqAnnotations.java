package org.sequeless.adapter.ontology.jena;

import java.util.Optional;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

/**
 * The single lookup pattern behind every {@code sq:} annotation read in this adapter: get the
 * (at most one) statement for a given subject and property, and fall back to a default when it is
 * absent. Every one of the {@code sq:} terms in {@link SqVocabulary}, plus {@code rdfs:label}
 * resolution in {@link SnapshotMapper}, goes through one of these three methods rather than
 * repeating {@code subject.getProperty(...)} ad hoc at each call site.
 *
 * <p>{@code sq:} annotations are functional by convention (the vocabulary document specifies at
 * most one value per subject); {@link Resource#getProperty(Property)} already returns at most one
 * {@link Statement} for a given property, which is exactly the shape these methods need — if a
 * document asserts more than one value, Jena's own "some statement" selection applies and no
 * ordering guarantee is made, which is an acceptable phase-1 simplification since none of the
 * reference fixtures do this.
 *
 * <p>Parameter type is the plain Jena {@link Resource}, not {@code OntObject}: {@code
 * SnapshotMapper}'s state-machine parsing (phase 5) reads these annotations off nodes obtained via
 * {@code Model#listResourcesWithProperty}/{@code Resource#listProperties} (a {@code sq:state}'s
 * target individual, a {@code sq:Transition}/action node, ...), which are plain {@link Resource}s,
 * not the richer {@code OntObject} the phase-1/4 callers (a type or property) happen to pass —
 * {@code Resource} is the common supertype both shapes share.
 *
 * <p>Package-private: only {@link SnapshotMapper} reads {@code sq:} annotations.
 */
final class SqAnnotations {

    private SqAnnotations() {}

    /**
     * @param subject the subject to read the annotation from; must not be {@code null}
     * @param property the annotation property to look up; must not be {@code null}
     * @param defaultValue the value to return when {@code subject} has no statement for {@code
     *     property}
     * @return the boolean value of {@code subject}'s statement for {@code property}, or {@code
     *     defaultValue} if none exists
     */
    static boolean bool(Resource subject, Property property, boolean defaultValue) {
        Statement statement = subject.getProperty(property);
        return statement == null ? defaultValue : statement.getBoolean();
    }

    /**
     * @param subject the subject to read the annotation from; must not be {@code null}
     * @param property the annotation property to look up; must not be {@code null}
     * @param defaultValue the value to return when {@code subject} has no statement for {@code
     *     property}
     * @return the integer value of {@code subject}'s statement for {@code property}, or {@code
     *     defaultValue} if none exists
     */
    static int intValue(Resource subject, Property property, int defaultValue) {
        Statement statement = subject.getProperty(property);
        return statement == null ? defaultValue : statement.getInt();
    }

    /**
     * @param subject the subject to read the annotation from; must not be {@code null}
     * @param property the annotation property to look up; must not be {@code null}
     * @return the string (literal lexical form) value of {@code subject}'s statement for {@code
     *     property}, or {@link Optional#empty()} if none exists
     */
    static Optional<String> string(Resource subject, Property property) {
        Statement statement = subject.getProperty(property);
        return statement == null ? Optional.empty() : Optional.of(statement.getString());
    }
}
