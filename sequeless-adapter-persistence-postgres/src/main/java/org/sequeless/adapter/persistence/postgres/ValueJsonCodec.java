package org.sequeless.adapter.persistence.postgres;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.spi.object.BoolValue;
import org.sequeless.spi.object.DateTimeValue;
import org.sequeless.spi.object.DateValue;
import org.sequeless.spi.object.DecimalValue;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Encodes and decodes {@code sq_object.props} between {@code Map<PropertyRef, Value>} and the
 * tagged JSONB shape documented in {@code plan.md} §2: a top-level JSON object keyed by full
 * property IRI, each value a single-entry object whose key names the {@link Value} variant
 * ({@code text}, {@code integer}, {@code decimal}, {@code bool}, {@code dateTime}, {@code date},
 * {@code ref}, or {@code list}).
 *
 * <p>This class imports exclusively from {@code tools.jackson.databind} (Jackson 3, the line Boot
 * 4.1.1 and this whole codebase use), never {@code com.fasterxml.jackson.databind} (Jackson 2):
 * mixing the two package trees would silently create a second, uncoordinated {@code ObjectMapper}
 * family and can surface as {@code NoClassDefFoundError} at runtime.
 */
final class ValueJsonCodec {

    private static final String TAG_TEXT = "text";
    private static final String TAG_INTEGER = "integer";
    private static final String TAG_DECIMAL = "decimal";
    private static final String TAG_BOOL = "bool";
    private static final String TAG_DATE_TIME = "dateTime";
    private static final String TAG_DATE = "date";
    private static final String TAG_REF = "ref";
    private static final String TAG_LIST = "list";

    // USE_BIG_DECIMAL_FOR_FLOATS is required: without it, a JSON floating-point number like
    // 12.50 is parsed into a DoubleNode and JsonNode#decimalValue() derives a BigDecimal from the
    // double, which silently drops scale (12.50 -> 12.5). With this enabled, decimal(s) parse
    // directly from their JSON literal into a scale-preserving BigDecimalNode.
    private static final JsonMapper MAPPER =
        JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private ValueJsonCodec() {}

    /**
     * @param properties the properties to encode; must not be {@code null}
     * @return the tagged JSON text to store in {@code sq_object.props}
     */
    static String toJson(Map<PropertyRef, Value> properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        ObjectNode root = MAPPER.createObjectNode();
        properties.forEach((property, value) -> root.set(property.iri(), encode(value)));
        return MAPPER.writeValueAsString(root);
    }

    /**
     * @param json the tagged JSON text read back from {@code sq_object.props}; must not be {@code
     *     null}
     * @return the decoded properties, keyed by {@link PropertyRef}
     */
    static Map<PropertyRef, Value> fromJson(String json) {
        Objects.requireNonNull(json, "json must not be null");
        JsonNode root = MAPPER.readTree(json);
        Map<PropertyRef, Value> result = new LinkedHashMap<>();
        root.properties()
            .forEach(entry -> result.put(new PropertyRef(entry.getKey()), decode(entry.getValue())));
        return result;
    }

    private static JsonNode encode(Value value) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (value) {
            case TextValue text -> node.put(TAG_TEXT, text.value());
            case IntegerValue integer -> node.put(TAG_INTEGER, integer.value());
            case DecimalValue decimal -> node.put(TAG_DECIMAL, decimal.value());
            case BoolValue bool -> node.put(TAG_BOOL, bool.value());
            case DateTimeValue dateTime -> node.put(TAG_DATE_TIME, dateTime.value().toString());
            case DateValue date -> node.put(TAG_DATE, date.value().toString());
            case ReferenceValue ref -> node.put(TAG_REF, ref.target().value().toString());
            case ListValue list -> {
                ArrayNode array = node.putArray(TAG_LIST);
                for (Value element : list.values()) {
                    if (element instanceof ListValue) {
                        throw new IllegalArgumentException("ListValue must not contain nested ListValue");
                    }
                    array.add(encode(element));
                }
            }
        }
        return node;
    }

    private static Value decode(JsonNode node) {
        if (node.has(TAG_TEXT)) {
            return Value.text(node.get(TAG_TEXT).asString());
        }
        if (node.has(TAG_INTEGER)) {
            return Value.integer(node.get(TAG_INTEGER).longValue());
        }
        if (node.has(TAG_DECIMAL)) {
            return Value.decimal(node.get(TAG_DECIMAL).decimalValue());
        }
        if (node.has(TAG_BOOL)) {
            return Value.bool(node.get(TAG_BOOL).booleanValue());
        }
        if (node.has(TAG_DATE_TIME)) {
            return Value.dateTime(Instant.parse(node.get(TAG_DATE_TIME).asString()));
        }
        if (node.has(TAG_DATE)) {
            return Value.date(LocalDate.parse(node.get(TAG_DATE).asString()));
        }
        if (node.has(TAG_REF)) {
            return Value.ref(ObjectId.parse(node.get(TAG_REF).asString()));
        }
        if (node.has(TAG_LIST)) {
            List<Value> values = new java.util.ArrayList<>();
            for (JsonNode element : node.get(TAG_LIST)) {
                values.add(decode(element));
            }
            return Value.list(values);
        }
        throw new IllegalArgumentException("Unrecognized tagged value: " + node);
    }
}
