package org.sequeless.app.rest;

import java.util.HashMap;
import java.util.Map;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.query.ObjectView;

/**
 * Maps sequeless-core's {@code org.sequeless.spi.object} types onto {@link ObjectsController}'s
 * {@link BusinessObjectResponse} wire representation. Factored out of {@link ObjectsController}
 * itself, mirroring {@link TypeResponseMapper}'s factoring-out from {@link TypesController}.
 *
 * <p>Package-private: nothing outside {@code org.sequeless.app.rest} has a reason to map these
 * types directly.
 */
final class ObjectPropertyMapper {

    private ObjectPropertyMapper() {}

    /**
     * @param object the object to render; must not be {@code null}
     * @param requestType the resolved {@link TypeDefinition} for the current request's {@code
     *     type} path variable, used to resolve {@code object}'s property IRIs to short names; must
     *     not be {@code null}
     * @return the {@link BusinessObjectResponse} rendering of {@code object}
     */
    static BusinessObjectResponse toResponse(BusinessObject object, TypeDefinition requestType) {
        return toResponse(
                new ObjectView(
                        object.id(),
                        object.type(),
                        object.tenant(),
                        object.version(),
                        object.state(),
                        object.properties(),
                        object.audit(),
                        object.deleted()),
                requestType);
    }

    /**
     * @param view the Query side's view of the object to render; must not be {@code null}
     * @param requestType the resolved {@link TypeDefinition} for the current request's {@code
     *     type} path variable, used to resolve the object's property IRIs to short names; must not
     *     be {@code null}
     * @return the {@link BusinessObjectResponse} rendering of {@code view}
     */
    static BusinessObjectResponse toResponse(ObjectView view, TypeDefinition requestType) {
        return new BusinessObjectResponse(
                view.id().value(),
                shortName(view.type().iri()),
                view.type().iri(),
                view.version(),
                view.state().map(ObjectPropertyMapper::shortName).orElse(null),
                toProperties(requestType, view.properties()),
                new BusinessObjectResponse.AuditResponse(
                        view.audit().createdAt(),
                        view.audit().createdBy(),
                        view.audit().updatedAt(),
                        view.audit().updatedBy()));
    }

    /**
     * @param requestType the resolved {@link TypeDefinition} for the current request's {@code
     *     type} path variable; must not be {@code null}
     * @param properties the object's property values, keyed by property IRI; must not be {@code
     *     null}
     * @return {@code properties} keyed by short name — resolved against {@code
     *     requestType.properties()} where that IRI is declared there, falling back to the IRI's
     *     own local name (per {@link #shortName}) otherwise, e.g. for a subtype-only property
     *     encountered while browsing one of its abstract supertypes
     */
    static Map<String, Object> toProperties(TypeDefinition requestType, Map<PropertyRef, Value> properties) {
        Map<String, String> namesByIri = new HashMap<>();
        for (PropertyDefinition property : requestType.properties()) {
            namesByIri.put(property.iri(), shortName(property.iri()));
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<PropertyRef, Value> entry : properties.entrySet()) {
            String iri = entry.getKey().iri();
            String name = namesByIri.getOrDefault(iri, shortName(iri));
            result.put(name, toWireValue(entry.getValue()));
        }
        return result;
    }

    /**
     * Maps one {@link Value} onto its plain, JSON-serializable wire representation. The {@code
     * switch} is exhaustive over the sealed {@link Value} hierarchy with no {@code default}
     * branch, mirroring {@link TypeResponseMapper#toProperty}'s rationale for {@link
     * org.sequeless.ontology.schema.api.PropertyDefinition}.
     */
    private static Object toWireValue(Value value) {
        return switch (value) {
            case TextValue text -> text.value();
            case IntegerValue integer -> integer.value();
            case DecimalValue decimal -> decimal.value();
            case BoolValue bool -> bool.value();
            case DateTimeValue dateTime -> dateTime.value();
            case DateValue date -> date.value();
            case ReferenceValue reference -> reference.target().value();
            case ListValue list ->
                list.values().stream().map(ObjectPropertyMapper::toWireValue).toList();
        };
    }

    /**
     * Duplicates {@code Schema}'s own short-name rule, for the same reason {@link
     * TypeResponseMapper#shortName} does: this maps a single, already-resolved IRI, not a whole
     * schema.
     *
     * @param iri the IRI to derive a short name from; must not be {@code null}
     * @return the local name after the IRI's last {@code #}, or after its last {@code /} if there
     *     is no {@code #}, or the whole IRI if neither is present
     */
    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
    }
}
