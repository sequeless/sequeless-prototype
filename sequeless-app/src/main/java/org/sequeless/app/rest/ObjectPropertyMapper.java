package org.sequeless.app.rest;

import java.util.HashMap;
import java.util.Map;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.DateTimeValue;
import org.sequeless.spi.object.DateValue;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;

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
        return new BusinessObjectResponse(
                object.id().value(),
                shortName(object.type().iri()),
                object.type().iri(),
                object.version(),
                object.state().orElse(null),
                toProperties(requestType, object.properties()),
                new BusinessObjectResponse.AuditResponse(
                        object.audit().createdAt(),
                        object.audit().createdBy(),
                        object.audit().updatedAt(),
                        object.audit().updatedBy()));
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
    static Map<String, Object> toProperties(
            TypeDefinition requestType, Map<PropertyRef, Value> properties) {
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
     * org.sequeless.spi.meta.PropertyDefinition}.
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
            case ListValue list -> list.values().stream().map(ObjectPropertyMapper::toWireValue).toList();
        };
    }

    /**
     * Duplicates {@code MetaModelSnapshot}'s own short-name rule, for the same reason {@link
     * TypeResponseMapper#shortName} does: this maps a single, already-resolved IRI, not a whole
     * snapshot.
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
