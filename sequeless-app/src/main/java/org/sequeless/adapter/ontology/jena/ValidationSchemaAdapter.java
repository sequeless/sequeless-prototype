package org.sequeless.adapter.ontology.jena;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.ontology.api.ValidationSchema;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;

/**
 * Presents a released {@link Schema} as the {@link ValidationSchema} that the released SHACL port reads.
 *
 * <p>The released {@code SchemaValidationSchema} does the same job, but it is package-private in
 * {@code ontology-jena-adapter}, so this class reproduces its three methods over the public API of the
 * schema: {@link Schema#type(String)}, {@link TypeDefinition#superTypes()}, {@link
 * TypeDefinition#properties()}, {@link org.sequeless.ontology.schema.api.PropertyDefinition#iri()},
 * {@link AttributeDefinition#datatype()} and {@link Datatype#xsdIri()}. The rules are the ones of that
 * class at sequeless-ontology {@code c9726a7}: {@code SchemaValidationSchema.java} lines 34 to 37 for
 * {@link #hasType(String)}, 50 to 67 for {@link #typeAndSupertypes(String)}, and 76 to 86 for {@link
 * #xsdDatatypeOf(String, String)}.
 */
final class ValidationSchemaAdapter implements ValidationSchema {

    private final Schema schema;

    /**
     * @param schema the type system to expose; must not be {@code null}
     * @throws NullPointerException if {@code schema} is {@code null}
     */
    ValidationSchemaAdapter(Schema schema) {
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
    }

    @Override
    public boolean hasType(String typeIri) {
        return schema.type(typeIri).isPresent();
    }

    /**
     * Walks the supertypes transitively, breadth first, starting from {@code typeIri}. A supertype the
     * schema does not declare stays in the closure but is not expanded.
     *
     * @param typeIri a declared type IRI
     * @return {@code typeIri} and every supertype reachable from it, as an unmodifiable set that starts
     *     with {@code typeIri}
     */
    @Override
    public Set<String> typeAndSupertypes(String typeIri) {
        Set<String> closure = new LinkedHashSet<>();
        closure.add(typeIri);
        Deque<String> pending = new ArrayDeque<>(List.of(typeIri));
        while (!pending.isEmpty()) {
            Optional<TypeDefinition> current = schema.type(pending.poll());
            if (current.isEmpty()) {
                continue;
            }
            for (String superType : current.get().superTypes()) {
                if (closure.add(superType)) {
                    pending.add(superType);
                }
            }
        }
        return Collections.unmodifiableSet(closure);
    }

    /**
     * The XSD IRI of the attribute that {@code typeIri} declares for {@code propertyIri}. When the type
     * declares the property more than once, the last declaration wins. The XSD string IRI is returned
     * when the property is not an attribute declared on the type, including when the type is not
     * declared.
     *
     * @param typeIri a declared type IRI
     * @param propertyIri the property IRI
     * @return the XSD datatype IRI of the attribute, or the XSD string IRI
     */
    @Override
    public String xsdDatatypeOf(String typeIri, String propertyIri) {
        return schema.type(typeIri)
                .flatMap(type -> type.properties().stream()
                        .filter(property -> property.iri().equals(propertyIri))
                        .reduce((first, second) -> second))
                .filter(AttributeDefinition.class::isInstance)
                .map(AttributeDefinition.class::cast)
                .map(attribute -> attribute.datatype().xsdIri())
                .orElse(Datatype.STRING.xsdIri());
    }
}
