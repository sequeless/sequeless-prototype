package org.sequeless.app.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sequeless.filter.api.AndFilter;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.filter.api.FilterNode;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.store.spi.Criterion;
import org.sequeless.store.spi.Operator;

/**
 * Maps the prototype's {@link Criterion}s to the released filter tree. The operator names are the released filter's
 * canonical names; the REST aliases are translated before they reach the core, so they do not appear here.
 *
 * <p>{@link Operator#IS_NULL} is the released {@code is} operator with a JSON null value, and {@link
 * Operator#NOT_NULL} is {@code is not} with a JSON null value. The value is a {@link NullNode}, never a Java
 * {@code null}: the released filter validator reads a Java {@code null} value as a missing one and rejects it.
 */
final class CriterionMapper {

    private static final Map<Operator, String> OPERATORS = Map.of(
            Operator.EQ, "is",
            Operator.NE, "is not",
            Operator.GT, "is greater than",
            Operator.GTE, "is greater than or equal to",
            Operator.LT, "is less than",
            Operator.LTE, "is less than or equal to",
            Operator.CONTAINS, "contains",
            Operator.STARTS_WITH, "starts with",
            Operator.IN, "is in");

    private CriterionMapper() {}

    /**
     * @param criteria the prototype criteria, combined with AND; must not be {@code null}
     * @return empty when there are no criteria; the single filter when there is one; otherwise an {@link AndFilter}
     *     over them, in order
     */
    static Optional<FilterNode> toFilter(List<Criterion> criteria) {
        List<FilterNode> nodes = criteria.stream()
                .map(criterion -> (FilterNode) toFieldFilter(criterion))
                .toList();
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        if (nodes.size() == 1) {
            return Optional.of(nodes.get(0));
        }
        return Optional.of(new AndFilter(nodes));
    }

    /**
     * @param criterion one prototype criterion; must not be {@code null}
     * @return a field filter on the criterion's property IRI, which is the released path of a direct field
     * @throws IllegalArgumentException if a value-taking operator has no value
     */
    static FieldFilter toFieldFilter(Criterion criterion) {
        return switch (criterion.operator()) {
            case IS_NULL -> new FieldFilter(criterion.property(), "is", NullNode.getInstance());
            case NOT_NULL -> new FieldFilter(criterion.property(), "is not", NullNode.getInstance());
            case EQ, NE, GT, GTE, LT, LTE, CONTAINS, STARTS_WITH, IN -> valueFilter(criterion);
        };
    }

    private static FieldFilter valueFilter(Criterion criterion) {
        JsonNode value = criterion
                .value()
                .map(CriterionMapper::toJson)
                .orElseThrow(() -> new IllegalArgumentException("operator " + criterion.operator() + " on property "
                        + criterion.property() + " needs a value"));
        return new FieldFilter(criterion.property(), OPERATORS.get(criterion.operator()), value);
    }

    /**
     * The JSON form the released filter binds: text for strings, dates, date-times and references; numbers for
     * integers and decimals; booleans; and an array for a list. This is the form the released filter's value
     * conversion reads (its date-time parser takes an ISO offset date-time, which {@link java.time.Instant#toString}
     * produces).
     *
     * @param value a kernel value; must not be {@code null}
     * @return the JSON node for the value
     */
    static JsonNode toJson(Value value) {
        return switch (value) {
            case TextValue text -> TextNode.valueOf(text.value());
            case IntegerValue integer -> LongNode.valueOf(integer.value());
            case DecimalValue decimal -> DecimalNode.valueOf(decimal.value());
            case BoolValue bool -> BooleanNode.valueOf(bool.value());
            case DateValue date -> TextNode.valueOf(date.value().toString());
            case DateTimeValue dateTime -> TextNode.valueOf(dateTime.value().toString());
            // ObjectId's toString is the record form; the released binder reads the bare UUID text.
            case ReferenceValue reference ->
                TextNode.valueOf(reference.target().value().toString());
            case ListValue list -> {
                ArrayNode array = JsonNodeFactory.instance.arrayNode();
                list.values().forEach(element -> array.add(toJson(element)));
                yield array;
            }
        };
    }
}
