package org.sequeless.core.validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.validation.Violation;

/**
 * Turns raw, JSON-like input (a {@code Map<String, Object>} whose values are {@code String},
 * {@code Number}, {@code Boolean}, {@code List<Object>}, or {@code null}) into typed {@link Value}s
 * keyed by {@link PropertyRef}, per a {@link TypeDefinition}'s declared properties.
 *
 * <p><b>Property keys are accepted as either the full IRI or the short (local) name.</b> {@link
 * org.sequeless.spi.meta.MetaModelSnapshot#typeByName(String)} already accepts both forms for
 * <em>type</em> resolution; nothing in the SPI does the equivalent for <em>properties</em>
 * attributed to a {@link TypeDefinition}. Since the REST layer (out of scope here) hands this class
 * an already-short-named JSON body while documenting that full IRIs are also accepted on input,
 * this class does that resolution itself, reusing the same local-name rule as {@code
 * MetaModelSnapshot} (the substring after the last {@code #}, else after the last {@code /}, else
 * the whole IRI). Two properties of the <em>same</em> type genuinely colliding down to the same
 * local name is treated as a pre-existing ontology-authoring bug outside this class's job to catch
 * — it is not guarded against here, the same way {@code MetaModelSnapshot} only guards against
 * short-name collisions at the type level.
 *
 * <p>Coercion is all-or-nothing: if any key produces a {@link Violation}, {@link
 * CoercionResult#properties()} is empty and every violation found across every key is returned
 * together, never a partially-coerced result. A {@code null} raw value always means "the property
 * is absent" and never produces a violation, even for a required property — required-ness is
 * {@link StructuralValidator}'s job, not this class's.
 *
 * <p>A {@code readOnly} property supplied by the caller is always a violation, but the message
 * distinguishes two different reasons: a plain read-only property (e.g. {@code createdAt}) reports
 * "is read-only", while a property with a {@link PropertyDefinition#derivation()} present — one
 * whose value is computed, not stored, such as an {@code sq:Rollup} — reports "is a derived
 * property and cannot be set directly", since that is a more specific and more actionable message
 * for a caller who tried to PUT/POST one.
 *
 * <p>Stateless utility class: it has no dependencies to inject, so an instance would carry no
 * state, the same reason {@link org.sequeless.spi.meta.Datatype#fromXsd} and {@link Value}'s own
 * static factories are plain static methods rather than instantiated helpers.
 */
public final class ValueCoercer {

    private ValueCoercer() {}

    /**
     * @param type the resolved type the raw properties are coerced against; must not be {@code
     *     null}
     * @param rawProperties JSON-like input: keys are property short names or full IRIs; values are
     *     {@code String}, {@code Number}, {@code Boolean}, {@code List<Object>}, or {@code null}
     *     ({@code null} means "absent"); must not be {@code null}
     * @return a {@link CoercionResult}: either every key coerced to a {@link PropertyRef}/{@link
     *     Value} pair, or every {@link Violation} found, never a mix
     * @throws NullPointerException if either argument is {@code null}
     */
    public static CoercionResult coerce(TypeDefinition type, Map<String, Object> rawProperties) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(rawProperties, "rawProperties must not be null");

        Map<String, PropertyDefinition> byNameOrIri = new HashMap<>();
        for (PropertyDefinition property : type.properties()) {
            byNameOrIri.put(property.iri(), property);
            byNameOrIri.put(shortName(property.iri()), property);
        }

        Map<PropertyRef, Value> properties = new HashMap<>();
        List<Violation> violations = new ArrayList<>();

        for (Map.Entry<String, Object> entry : rawProperties.entrySet()) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();
            if (rawValue == null) {
                continue;
            }

            PropertyDefinition property = byNameOrIri.get(key);
            if (property == null) {
                violations.add(new Violation(key, "Unknown property '" + key + "'"));
                continue;
            }
            if (property.readOnly()) {
                String reason = property.derivation().isPresent()
                    ? "is a derived property and cannot be set directly"
                    : "is read-only";
                violations.add(
                    new Violation(
                        property.iri(),
                        "Property '" + shortName(property.iri()) + "' " + reason));
                continue;
            }

            coerceProperty(property, rawValue, properties, violations);
        }

        if (!violations.isEmpty()) {
            return CoercionResult.failure(violations);
        }
        return CoercionResult.success(properties);
    }

    private static void coerceProperty(
        PropertyDefinition property,
        Object rawValue,
        Map<PropertyRef, Value> properties,
        List<Violation> violations) {
        Cardinality cardinality = property.cardinality();
        OptionalInt max = cardinality.max();
        boolean expectsScalar = max.isPresent() && max.getAsInt() == 1;

        if (expectsScalar) {
            if (rawValue instanceof List<?>) {
                violations.add(
                    new Violation(property.iri(), "expected a single value, got a list"));
                return;
            }
            ScalarCoercion coerced = coerceScalar(property, rawValue);
            if (coerced.errorReason() != null) {
                violations.add(new Violation(property.iri(), coerced.errorReason()));
                return;
            }
            properties.put(new PropertyRef(property.iri()), coerced.value());
            return;
        }

        if (!(rawValue instanceof List<?> rawList)) {
            violations.add(new Violation(property.iri(), "expected a list, got a single value"));
            return;
        }

        List<Value> elements = new ArrayList<>();
        List<Violation> elementViolations = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            Object element = rawList.get(i);
            if (element instanceof List<?>) {
                elementViolations.add(
                    new Violation(
                        property.iri(), "element " + i + ": expected a scalar value, got a list"));
                continue;
            }
            ScalarCoercion coerced = coerceScalar(property, element);
            if (coerced.errorReason() != null) {
                elementViolations.add(
                    new Violation(property.iri(), "element " + i + ": " + coerced.errorReason()));
                continue;
            }
            elements.add(coerced.value());
        }

        if (!elementViolations.isEmpty()) {
            violations.addAll(elementViolations);
            return;
        }
        properties.put(new PropertyRef(property.iri()), Value.list(elements));
    }

    /**
     * Coerces a single raw scalar to a {@link Value} per {@code property}'s datatype (or, for a
     * {@link RelationshipDefinition}, per the UUID/{@code ReferenceValue} rule). Returns a {@link
     * ScalarCoercion} carrying exactly one of a {@link Value} or a violation reason string, never
     * both and never neither.
     */
    /**
     * Coerces a single raw scalar value against {@code property}'s datatype, independent of {@code
     * property}'s own declared cardinality.
     *
     * <p>This exists because the public {@link #coerce(TypeDefinition, Map)} entry point branches
     * on the property's own cardinality and rejects a {@code List} input outright for a
     * scalar-cardinality property — but query filter coercion needs exactly that combination: an
     * {@code Operator#IN} filter must coerce N candidate values against a single-cardinality
     * property, one at a time. {@link #coerceScalar} already does per-datatype coercion
     * cardinality-independently, so this method is a small public wrapper around it, not a
     * duplicate of its logic.
     *
     * @param property the property {@code rawValue} is coerced against; must not be {@code null}
     * @param rawValue the raw scalar value to coerce — a {@code String}, {@code Number}, or {@code
     *     Boolean}; must not be {@code null}
     * @return a {@link ScalarResult} carrying exactly one of a coerced {@link Value} or a violation
     *     reason string, never both and never neither
     * @throws NullPointerException if either argument is {@code null}
     */
    public static ScalarResult coerceScalarValue(PropertyDefinition property, Object rawValue) {
        Objects.requireNonNull(property, "property must not be null");
        Objects.requireNonNull(rawValue, "rawValue must not be null");
        ScalarCoercion result = coerceScalar(property, rawValue);
        return new ScalarResult(result.value(), result.errorReason());
    }

    /**
     * The outcome of {@link #coerceScalarValue}: exactly one of {@link #value()} or {@link
     * #errorReason()} is non-{@code null}, never both, never neither.
     */
    public record ScalarResult(Value value, String errorReason) {

        /**
         * @return {@code true} if {@link #value()} is present (and {@link #errorReason()} is
         *     {@code null})
         */
        public boolean isSuccess() {
            return errorReason == null;
        }
    }

    private static ScalarCoercion coerceScalar(PropertyDefinition property, Object rawValue) {
        if (property instanceof RelationshipDefinition) {
            if (!(rawValue instanceof String text)) {
                return ScalarCoercion.failure("expected a UUID string");
            }
            try {
                return ScalarCoercion.success(Value.ref(ObjectId.parse(text)));
            } catch (IllegalArgumentException e) {
                return ScalarCoercion.failure("expected a UUID string");
            }
        }

        AttributeDefinition attribute = (AttributeDefinition) property;
        return switch (attribute.datatype()) {
            case STRING, ANY_URI, TIME, DURATION -> {
                if (!(rawValue instanceof String text)) {
                    yield ScalarCoercion.failure("expected a string");
                }
                yield ScalarCoercion.success(Value.text(text));
            }
            case INTEGER, LONG -> coerceIntegral(rawValue);
            case DECIMAL, DOUBLE -> coerceDecimal(rawValue);
            case BOOLEAN -> {
                if (!(rawValue instanceof Boolean bool)) {
                    yield ScalarCoercion.failure("expected a boolean");
                }
                yield ScalarCoercion.success(Value.bool(bool));
            }
            case DATE -> {
                if (!(rawValue instanceof String text)) {
                    yield ScalarCoercion.failure("expected a date string");
                }
                try {
                    yield ScalarCoercion.success(Value.date(LocalDate.parse(text)));
                } catch (DateTimeParseException e) {
                    yield ScalarCoercion.failure("expected an ISO-8601 date string");
                }
            }
            case DATE_TIME -> {
                if (!(rawValue instanceof String text)) {
                    yield ScalarCoercion.failure("expected a dateTime string");
                }
                try {
                    yield ScalarCoercion.success(Value.dateTime(Instant.parse(text)));
                } catch (DateTimeParseException e) {
                    yield ScalarCoercion.failure(
                        "expected an ISO-8601 dateTime string with an offset");
                }
            }
        };
    }

    private static ScalarCoercion coerceIntegral(Object rawValue) {
        if (!(rawValue instanceof Number number)) {
            return ScalarCoercion.failure("expected an integer");
        }
        if (number instanceof Integer || number instanceof Long || number instanceof Short) {
            return ScalarCoercion.success(Value.integer(number.longValue()));
        }
        if (number instanceof BigDecimal decimal) {
            if (decimal.stripTrailingZeros().scale() > 0) {
                return ScalarCoercion.failure("expected an integer");
            }
            return ScalarCoercion.success(Value.integer(decimal.longValueExact()));
        }
        double asDouble = number.doubleValue();
        if (asDouble != Math.rint(asDouble)) {
            return ScalarCoercion.failure("expected an integer");
        }
        return ScalarCoercion.success(Value.integer(number.longValue()));
    }

    private static ScalarCoercion coerceDecimal(Object rawValue) {
        if (rawValue instanceof BigDecimal decimal) {
            return ScalarCoercion.success(Value.decimal(decimal));
        }
        if (rawValue instanceof Number number) {
            return ScalarCoercion.success(Value.decimal(new BigDecimal(number.toString())));
        }
        if (rawValue instanceof String text) {
            try {
                return ScalarCoercion.success(Value.decimal(new BigDecimal(text)));
            } catch (NumberFormatException e) {
                return ScalarCoercion.failure("expected a decimal number");
            }
        }
        return ScalarCoercion.failure("expected a decimal number");
    }

    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
    }

    /**
     * The outcome of coercing one scalar raw value: exactly one of {@link #value()} or {@link
     * #errorReason()} is non-{@code null}, never both, never neither.
     */
    private record ScalarCoercion(Value value, String errorReason) {

        static ScalarCoercion success(Value value) {
            return new ScalarCoercion(value, null);
        }

        static ScalarCoercion failure(String errorReason) {
            return new ScalarCoercion(null, errorReason);
        }
    }
}
