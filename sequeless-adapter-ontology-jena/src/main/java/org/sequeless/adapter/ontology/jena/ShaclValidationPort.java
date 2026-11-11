package org.sequeless.adapter.ontology.jena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.api.ShapeViolation;
import org.sequeless.ontology.api.ValidatableObject;
import org.sequeless.ontology.jena.JenaOntologyPort;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.validation.ValidationPort;
import org.sequeless.spi.validation.Violation;

/**
 * The Command-side {@link ValidationPort}. It holds no SHACL logic: it maps the prototype's
 * {@link Schema} and {@link BusinessObject} to the released ontology types, delegates the evaluation
 * to the released {@link org.sequeless.ontology.jena.ShaclValidationPort}, and maps each {@link
 * ShapeViolation} back to a {@link Violation}.
 *
 * <p>The released port reads the shapes from the live ontology of the given {@link JenaOntologyPort},
 * so {@link ShaclValidationAutoConfiguration} must pass the same port that backs the ontology ports.
 */
public final class ShaclValidationPort implements ValidationPort {

    private final org.sequeless.ontology.jena.ShaclValidationPort delegate;

    /**
     * @param ontologyPort the released Jena adapter whose live ontology supplies the {@code sh:} shapes;
     *     must not be {@code null}
     * @throws NullPointerException if {@code ontologyPort} is {@code null}
     */
    public ShaclValidationPort(JenaOntologyPort ontologyPort) {
        this.delegate = new org.sequeless.ontology.jena.ShaclValidationPort(
                Objects.requireNonNull(ontologyPort, "ontologyPort must not be null"));
    }

    @Override
    public List<Violation> validate(Scope scope, Schema schema, BusinessObject object) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(object, "object must not be null");

        List<ShapeViolation> shapeViolations =
                delegate.validate(scope, new ValidationSchemaAdapter(schema), toValidatable(object));
        return shapeViolations.stream()
                .map(violation -> new Violation(violation.propertyIri(), violation.message()))
                .toList();
    }

    /**
     * @param object the prototype object to validate; must not be {@code null}
     * @return the released form of {@code object}: its id, its type IRI, and each property keyed by the
     *     property IRI, with the values unchanged
     */
    private static ValidatableObject toValidatable(BusinessObject object) {
        Map<String, Value> propertiesByIri = new LinkedHashMap<>();
        for (Map.Entry<PropertyRef, Value> property : object.properties().entrySet()) {
            propertiesByIri.put(property.getKey().iri(), property.getValue());
        }
        return new ValidatableObject(object.id(), object.type().iri(), propertiesByIri);
    }
}
