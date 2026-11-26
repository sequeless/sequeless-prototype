package org.sequeless.app.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;

/**
 * Maps kernel {@link Value}s to the JSON-like raw form that {@code BusinessObjectService.add} and {@code edit}
 * coerce (see {@code ValueCoercer}): strings for text, dates, date-times and references; a {@code Long} or
 * {@code BigDecimal} for numbers; a {@code Boolean}; and a {@code List} for a list.
 *
 * <p>The switch has no {@code default}, so a new {@link Value} subtype fails to compile here.
 */
final class ValueWire {

    private ValueWire() {}

    /**
     * @param value the value to map; must not be {@code null}
     * @return the raw form of {@code value}
     */
    static Object toRaw(Value value) {
        Objects.requireNonNull(value, "value must not be null");
        return switch (value) {
            case TextValue text -> text.value();
            case IntegerValue integer -> integer.value();
            case DecimalValue decimal -> decimal.value();
            case BoolValue bool -> bool.value();
            case DateTimeValue dateTime -> dateTime.value().toString();
            case DateValue date -> date.value().toString();
            case ReferenceValue reference -> reference.target().value().toString();
            case ListValue list -> list.values().stream().map(ValueWire::toRaw).toList();
        };
    }

    /**
     * @param properties the property values, keyed by property IRI; must not be {@code null}
     * @return a new map of the same keys, each value mapped by {@link #toRaw}; insertion order is kept
     */
    static Map<String, Object> toRawProperties(Map<String, Value> properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        Map<String, Object> raw = new LinkedHashMap<>();
        properties.forEach((iri, value) -> raw.put(iri, toRaw(value)));
        return raw;
    }
}
