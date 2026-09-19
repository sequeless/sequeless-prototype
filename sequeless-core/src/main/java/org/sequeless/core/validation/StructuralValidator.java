package org.sequeless.core.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.validation.Violation;

/**
 * Checks a {@link ValueCoercer}-coerced property map against a {@link TypeDefinition}'s required
 * properties and cardinality, and against reference integrity: that every referenced object exists
 * and has the relationship's target type or a subtype of it.
 *
 * <p>{@link #validateForCreate} additionally rejects an abstract type outright, before checking
 * anything else, since an abstract type must never be instantiated. {@link #validateForUpdate}
 * never performs that check at all — an existing object of an abstract type could not have been
 * created in the first place, so re-deriving "was this type abstract at create time" logic on
 * every edit would be pointless; exposing two differently-named entry points makes the distinction
 * impossible to forget at a call site, unlike a boolean flag that could be passed wrong.
 *
 * <p>Stateless utility class (final, private constructor, static methods): it needs an {@link
 * ObjectStorePort} and a {@link Scope}/{@link MetaModelSnapshot} per call but no state across
 * calls, so a static method taking everything as a parameter is simpler than an instantiated class
 * a caller would have to construct and hold purely to call one method on it.
 */
public final class StructuralValidator {

    private StructuralValidator() {}

    /**
     * Validates {@code properties} for adding a new object of {@code type}. Rejects an abstract
     * {@code type} immediately, returning only the abstract-type violation without checking
     * anything else.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param snapshot the current meta-model, used to walk relationship target subtypes; must not
     *     be {@code null}
     * @param type the type {@code properties} is being validated against; must not be {@code null}
     * @param properties the coerced properties to validate, keyed by full IRI; must not be {@code
     *     null}
     * @param objectStorePort consulted once, in a single batch, to resolve every referenced
     *     object's type; must not be {@code null}
     * @return every violation found; empty if {@code properties} is structurally valid
     * @throws NullPointerException if any argument is {@code null}
     */
    public static List<Violation> validateForCreate(
        Scope scope,
        MetaModelSnapshot snapshot,
        TypeDefinition type,
        Map<PropertyRef, Value> properties,
        ObjectStorePort objectStorePort) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");

        if (type.isAbstract()) {
            return List.of(
                new Violation(
                    "", "Type '" + type.iri() + "' is abstract and cannot be instantiated"));
        }
        return validateCommon(scope, snapshot, type, properties, objectStorePort);
    }

    /**
     * Validates {@code properties} for replacing an existing object of {@code type}'s properties.
     * Performs the same checks as {@link #validateForCreate} minus the abstract-type rejection.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param snapshot the current meta-model, used to walk relationship target subtypes; must not
     *     be {@code null}
     * @param type the type {@code properties} is being validated against; must not be {@code null}
     * @param properties the coerced properties to validate, keyed by full IRI; must not be {@code
     *     null}
     * @param objectStorePort consulted once, in a single batch, to resolve every referenced
     *     object's type; must not be {@code null}
     * @return every violation found; empty if {@code properties} is structurally valid
     * @throws NullPointerException if any argument is {@code null}
     */
    public static List<Violation> validateForUpdate(
        Scope scope,
        MetaModelSnapshot snapshot,
        TypeDefinition type,
        Map<PropertyRef, Value> properties,
        ObjectStorePort objectStorePort) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");

        return validateCommon(scope, snapshot, type, properties, objectStorePort);
    }

    private static List<Violation> validateCommon(
        Scope scope,
        MetaModelSnapshot snapshot,
        TypeDefinition type,
        Map<PropertyRef, Value> properties,
        ObjectStorePort objectStorePort) {
        Map<String, PropertyDefinition> byIri = new HashMap<>();
        for (PropertyDefinition property : type.properties()) {
            byIri.put(property.iri(), property);
        }

        assertNoDerivedPropertyPresent(type, properties, byIri);

        List<Violation> violations = new ArrayList<>();
        violations.addAll(validateRequired(type, properties));
        violations.addAll(validateMaxCardinality(properties, byIri));
        violations.addAll(validateReferences(scope, snapshot, properties, byIri, objectStorePort));
        return violations;
    }

    /**
     * Defensive canary, not the primary enforcement mechanism: asserts that no key in the
     * already-coerced {@code properties} map corresponds to a {@code derivation()}-present property
     * on {@code type}. {@link ValueCoercer#coerce} already rejects a caller-supplied derived
     * property outright — coercion is all-or-nothing, so a derived-property violation empties
     * {@code CoercionResult.properties()} and short-circuits {@code
     * DefaultBusinessObjectService.coerceOrThrow} before this method is ever reached, on both the
     * add and edit paths. This should therefore be unreachable in normal operation; it exists only
     * to fail loudly, rather than silently persist a derived value, if some future change ever
     * bypasses {@link ValueCoercer}.
     *
     * @throws IllegalStateException if {@code properties} contains a derived property's IRI, naming
     *     it
     */
    private static void assertNoDerivedPropertyPresent(
        TypeDefinition type, Map<PropertyRef, Value> properties, Map<String, PropertyDefinition> byIri) {
        for (PropertyRef ref : properties.keySet()) {
            PropertyDefinition property = byIri.get(ref.iri());
            if (property != null && property.derivation().isPresent()) {
                throw new IllegalStateException(
                    "Derived property '" + property.iri() + "' reached StructuralValidator for type '"
                        + type.iri() + "' — this should be unreachable; ValueCoercer must reject it "
                        + "before this point");
            }
        }
    }

    private static List<Violation> validateRequired(
        TypeDefinition type, Map<PropertyRef, Value> properties) {
        List<Violation> violations = new ArrayList<>();
        for (PropertyDefinition property : type.properties()) {
            if (property.cardinality().min() >= 1
                && !properties.containsKey(new PropertyRef(property.iri()))) {
                violations.add(
                    new Violation(
                        property.iri(),
                        "Property '" + shortName(property.iri()) + "' is required"));
            }
        }
        return violations;
    }

    private static List<Violation> validateMaxCardinality(
        Map<PropertyRef, Value> properties, Map<String, PropertyDefinition> byIri) {
        List<Violation> violations = new ArrayList<>();
        for (Map.Entry<PropertyRef, Value> entry : properties.entrySet()) {
            PropertyDefinition property = byIri.get(entry.getKey().iri());
            if (property == null) {
                continue;
            }
            OptionalInt max = property.cardinality().max();
            if (max.isEmpty()) {
                continue;
            }
            int actualCount =
                entry.getValue() instanceof ListValue listValue ? listValue.values().size() : 1;
            if (actualCount > max.getAsInt()) {
                violations.add(
                    new Violation(
                        property.iri(),
                        "expected at most " + max.getAsInt() + " value(s), got " + actualCount));
            }
        }
        return violations;
    }

    private static List<Violation> validateReferences(
        Scope scope,
        MetaModelSnapshot snapshot,
        Map<PropertyRef, Value> properties,
        Map<String, PropertyDefinition> byIri,
        ObjectStorePort objectStorePort) {
        Map<RelationshipDefinition, List<ObjectId>> targetsByProperty = new LinkedHashMap<>();
        Set<ObjectId> allTargetIds = new LinkedHashSet<>();
        for (Map.Entry<PropertyRef, Value> entry : properties.entrySet()) {
            PropertyDefinition property = byIri.get(entry.getKey().iri());
            if (!(property instanceof RelationshipDefinition relationship)) {
                continue;
            }
            List<ObjectId> targets = new ArrayList<>();
            collectReferenceIds(entry.getValue(), targets);
            if (targets.isEmpty()) {
                continue;
            }
            targetsByProperty.put(relationship, targets);
            allTargetIds.addAll(targets);
        }

        if (allTargetIds.isEmpty()) {
            return List.of();
        }

        Map<ObjectId, TypeRef> resolvedTypes = objectStorePort.typesOf(scope, allTargetIds);

        List<Violation> violations = new ArrayList<>();
        for (Map.Entry<RelationshipDefinition, List<ObjectId>> entry :
            targetsByProperty.entrySet()) {
            RelationshipDefinition relationship = entry.getKey();
            for (ObjectId id : entry.getValue()) {
                TypeRef resolvedType = resolvedTypes.get(id);
                if (resolvedType == null) {
                    violations.add(
                        new Violation(
                            relationship.iri(),
                            "Referenced object " + id.value() + " does not exist"));
                    continue;
                }
                if (!TypeHierarchy.isSubtypeOf(
                    snapshot, resolvedType.iri(), relationship.targetTypeIri())) {
                    violations.add(
                        new Violation(
                            relationship.iri(),
                            "Referenced object " + id.value() + " has type " + resolvedType.iri()
                                + ", expected " + relationship.targetTypeIri() + " or a subtype"));
                }
            }
        }
        return violations;
    }

    private static void collectReferenceIds(Value value, List<ObjectId> out) {
        if (value instanceof ReferenceValue reference) {
            out.add(reference.target());
        } else if (value instanceof ListValue listValue) {
            for (Value element : listValue.values()) {
                if (element instanceof ReferenceValue reference) {
                    out.add(reference.target());
                }
            }
        }
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
