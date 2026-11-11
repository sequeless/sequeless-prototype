package org.sequeless.core.derivation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.core.validation.TypeHierarchy;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.AnyFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.filter.api.OrFilter;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.meta.schema.AttributeDefinition;
import org.sequeless.spi.meta.schema.Datatype;
import org.sequeless.spi.meta.schema.PropertyDefinition;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;

/**
 * Bridges the released filter tree of a rollup's {@code sq:filter} (filter-core's {@link FilterNode})
 * to the flat {@link Criterion} list the prototype's {@link org.sequeless.spi.query.AggregateRequest}
 * takes. The bridge accepts only AND-combined {@link FieldFilter} trees: an {@link AndFilter} is
 * flattened and each {@link FieldFilter} becomes one {@link Criterion}. Any other node is rejected.
 *
 * <p>The op names are the filter-core canonical names the released loader writes (sequeless-ontology
 * c9726a7, the {@code Operator} enum of its SnapshotMapper, spec 11 section 3.2), inverted. Any other
 * op not listed here is rejected. {@link FieldFilter#path()} is the full property IRI (spec 11 Q5),
 * looked up in the {@link Schema} from the source type and its subtypes.
 *
 * <p>The op and the no-value check come first. An unmapped op is rejected, and a value op other than
 * {@code is in} with no value is rejected, both before the property lookup. The lookup then runs for
 * every value op, {@code is in} included (its array check runs after the lookup). {@code is} and
 * {@code is not} with no value (a Java {@code null} or a {@code NullNode}) map to {@link
 * Operator#IS_NULL} and {@link Operator#NOT_NULL} with no lookup, so an undeclared property is accepted
 * for them. Both loaders also skip the property check for these two ops.
 *
 * <p>An {@link Operator#IN} criterion carries a {@link org.sequeless.kernel.values.ListValue} of the
 * element values, not a scalar. The cut-over PR must render it as a list.
 *
 * <p>Values convert by the property's {@link Datatype} as the {@link Schema} gives it. The Schema keeps
 * one datatype per attribute, the most specific range, while the released loader writes a value using
 * the first range IRI in sorted order. The two differ when a property has several ranges, declared or
 * entailed. Under the OWL reasoner (the prototype's default), an {@code xsd:integer} property also gets
 * the entailed range {@code xsd:decimal}, which sorts first, so the loader may write a {@code DecimalNode}
 * for it. The bridge then rejects any {@code DecimalNode} for INTEGER and LONG with an {@link
 * IllegalArgumentException}, including an integral one such as {@code 3.0}. The prototype's
 * {@code StateMachineMappingTest} and {@code mostSpecificXsdIri} comments say this was verified
 * empirically under OWL. It has NOT been reproduced in this bridge PR. The cut-over must verify it
 * before the bridge is wired into {@code DerivationPlanner.resolveRollup}. This javadoc makes no claim
 * that the bridge matches the loader.
 */
public final class FilterNodeToCriterion {

    private static final Map<String, Operator> VALUE_OPERATORS = Map.of(
            "is", Operator.EQ,
            "is not", Operator.NE,
            "is in", Operator.IN,
            "is less than", Operator.LT,
            "is less than or equal to", Operator.LTE,
            "is greater than", Operator.GT,
            "is greater than or equal to", Operator.GTE,
            "contains", Operator.CONTAINS,
            "starts with", Operator.STARTS_WITH);

    private static final Map<String, Operator> NO_VALUE_OPERATORS = Map.of(
            "is", Operator.IS_NULL,
            "is not", Operator.NOT_NULL);

    private FilterNodeToCriterion() {}

    /**
     * @param node the filter tree to convert; must not be {@code null}
     * @param schema the type system the property IRIs and their datatypes are looked up in; must not
     *     be {@code null}
     * @param sourceTypeIri the rollup's source type ({@code sq:over}); the lookup starts here and
     *     then searches its subtypes; must not be {@code null}
     * @return one {@link Criterion} per {@link FieldFilter} leaf of {@code node}, in tree order; an
     *     empty list if the tree has no leaves
     * @throws IllegalArgumentException if {@code node} contains an {@link OrFilter} or an {@link
     *     AnyFilter}, an op the bridge does not map, or a value that does not fit its property (a
     *     missing or unknown property, or a JSON type that does not fit the datatype)
     * @throws NullPointerException if any argument is {@code null}
     */
    public static List<Criterion> toCriteria(FilterNode node, Schema schema, String sourceTypeIri) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(sourceTypeIri, "sourceTypeIri must not be null");
        List<Criterion> criteria = new ArrayList<>();
        collect(node, schema, sourceTypeIri, criteria);
        return List.copyOf(criteria);
    }

    private static void collect(FilterNode node, Schema schema, String sourceTypeIri, List<Criterion> criteria) {
        switch (node) {
            case AndFilter and -> and.operands().forEach(child -> collect(child, schema, sourceTypeIri, criteria));
            case FieldFilter field -> criteria.add(toCriterion(field, schema, sourceTypeIri));
            case OrFilter or ->
                throw new IllegalArgumentException(
                        "OrFilter is not supported by the criteria bridge; only AND-combined field filters are");
            case AnyFilter any ->
                throw new IllegalArgumentException(
                        "AnyFilter is not supported by the criteria bridge; only AND-combined field filters are");
        }
    }

    private static Criterion toCriterion(FieldFilter filter, Schema schema, String sourceTypeIri) {
        String property = filter.path();
        String op = filter.op();
        JsonNode value = filter.value();
        boolean noValue = value == null || value.isNull();
        if (noValue && NO_VALUE_OPERATORS.containsKey(op)) {
            return new Criterion(property, NO_VALUE_OPERATORS.get(op), Optional.empty());
        }
        Operator operator = VALUE_OPERATORS.get(op);
        if (operator == null) {
            throw new IllegalArgumentException("Unsupported filter op '" + op + "' on " + property);
        }
        if (noValue && operator != Operator.IN) {
            throw new IllegalArgumentException("Filter op '" + op + "' on " + property + " needs a value");
        }
        Optional<Datatype> datatype = valueKindOf(schema, sourceTypeIri, property);
        Value converted =
                operator == Operator.IN ? listOf(property, datatype, value) : valueOf(property, datatype, value);
        return new Criterion(property, operator, Optional.of(converted));
    }

    /**
     * The value kind of {@code property}: the datatype of an attribute, or empty for a relationship
     * (whose value is a {@link org.sequeless.kernel.values.ReferenceValue}). The search starts at the
     * source type and covers its subtypes; the property must be declared, and every declaration must
     * agree on the kind.
     */
    private static Optional<Datatype> valueKindOf(Schema schema, String sourceTypeIri, String property) {
        Set<TypeRef> closure =
                schema.type(sourceTypeIri).isEmpty() ? Set.of() : TypeHierarchy.typeAndSubtypes(schema, sourceTypeIri);
        Set<Optional<Datatype>> kinds = new LinkedHashSet<>();
        for (TypeRef type : closure) {
            for (PropertyDefinition declared :
                    schema.type(type.iri()).orElseThrow().properties()) {
                if (declared.iri().equals(property)) {
                    kinds.add(kindOf(declared));
                }
            }
        }
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Property " + property + " is not declared on " + sourceTypeIri + " or its subtypes");
        }
        if (kinds.size() > 1) {
            throw new IllegalArgumentException("Property " + property + " is declared with different value kinds on "
                    + sourceTypeIri + " or its subtypes");
        }
        return kinds.iterator().next();
    }

    private static Optional<Datatype> kindOf(PropertyDefinition property) {
        if (property instanceof AttributeDefinition attribute) {
            return Optional.of(attribute.datatype());
        }
        return Optional.empty();
    }

    private static Value listOf(String property, Optional<Datatype> datatype, JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("Filter op 'is in' on " + property + " needs a JSON array value");
        }
        List<Value> values = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            values.add(valueOf(property, datatype, element));
        }
        return Value.list(values);
    }

    private static Value valueOf(String property, Optional<Datatype> datatype, JsonNode node) {
        try {
            Optional<Value> value = datatype.isEmpty() ? reference(node) : scalar(datatype.get(), node);
            if (value.isPresent()) {
                return value.get();
            }
        } catch (DateTimeException | IllegalArgumentException e) {
            throw new IllegalArgumentException(mismatch(property, datatype, node), e);
        }
        throw new IllegalArgumentException(mismatch(property, datatype, node));
    }

    private static String mismatch(String property, Optional<Datatype> datatype, JsonNode node) {
        String expected = datatype.map(Datatype::name).orElse("a UUID text (reference)");
        return "Value " + node + " on " + property + " does not fit " + expected;
    }

    private static Optional<Value> reference(JsonNode node) {
        return node.isTextual() ? Optional.of(Value.ref(ObjectId.parse(node.textValue()))) : Optional.empty();
    }

    /**
     * Mirrors the released loader's JSON form (its {@code jsonOf}) and the prototype's
     * {@code coerceLiteral}: text for string-like datatypes and for dates (ISO-8601), an integral
     * number for integer and long, a number for decimal and double, a boolean for boolean.
     */
    private static Optional<Value> scalar(Datatype datatype, JsonNode node) {
        return switch (datatype) {
            case STRING, ANY_URI, TIME, DURATION ->
                node.isTextual() ? Optional.of(Value.text(node.textValue())) : Optional.empty();
            case INTEGER, LONG ->
                node.isIntegralNumber() && node.canConvertToLong()
                        ? Optional.of(Value.integer(node.longValue()))
                        : Optional.empty();
            case DECIMAL, DOUBLE ->
                node.isNumber() ? Optional.of(Value.decimal(node.decimalValue())) : Optional.empty();
            case BOOLEAN -> node.isBoolean() ? Optional.of(Value.bool(node.booleanValue())) : Optional.empty();
            case DATE ->
                node.isTextual() ? Optional.of(Value.date(LocalDate.parse(node.textValue()))) : Optional.empty();
            case DATE_TIME ->
                node.isTextual() ? Optional.of(Value.dateTime(Instant.parse(node.textValue()))) : Optional.empty();
        };
    }
}
