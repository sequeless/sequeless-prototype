package org.sequeless.app.automation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;

/**
 * Shapes the raw property values of an automated write to the shape the ontology declares (spec 17, H7, decision b).
 *
 * <p>The released executor emits a single value for every property. The Store takes a JSON list for a property the
 * ontology declares multi-valued (its cardinality maximum is absent or greater than one), and refuses a scalar for it.
 * So a scalar for such a property is wrapped in a one-element list before the write.
 *
 * <p>The declared cardinality is read from the schema of the tenant, never from a property name. A list is left as it
 * is. A single-valued property is left as it is. A property the schema does not declare is left as it is, so the Store's
 * own validation decides, as it does for every other write. A type the schema does not declare is left as it is too.
 */
final class ShapeCoercion {

    private final SchemaSource schemaSource;

    /**
     * @param schemaSource the schema the declared cardinalities are read from; must not be {@code null}
     */
    ShapeCoercion(SchemaSource schemaSource) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
    }

    /**
     * @param scope the tenant whose schema is read; must not be {@code null}
     * @param typeIri the IRI of the type the write targets; must not be {@code null}
     * @param rawProperties the raw property values, keyed by property IRI; must not be {@code null}
     * @return a new map with the same keys in the same order, where each scalar on a multi-valued declared property is
     *     a one-element list
     */
    Map<String, Object> coerce(Scope scope, String typeIri, Map<String, Object> rawProperties) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        Objects.requireNonNull(rawProperties, "rawProperties must not be null");

        Map<String, PropertyDefinition> declared = new HashMap<>();
        schemaSource
                .schema(scope.tenantId())
                .type(typeIri)
                .map(TypeDefinition::properties)
                .ifPresent(properties -> properties.forEach(property -> declared.put(property.iri(), property)));

        Map<String, Object> shaped = new LinkedHashMap<>();
        rawProperties.forEach((iri, raw) -> {
            boolean wrap = isMultiValued(declared.get(iri)) && !(raw instanceof List<?>);
            shaped.put(iri, wrap ? List.of(raw) : raw);
        });
        return shaped;
    }

    /** Whether the declared cardinality allows more than one value: no maximum, or a maximum above one. */
    private static boolean isMultiValued(PropertyDefinition property) {
        if (property == null) {
            return false;
        }
        return property.cardinality().max().isEmpty()
                || property.cardinality().max().getAsInt() > 1;
    }
}
