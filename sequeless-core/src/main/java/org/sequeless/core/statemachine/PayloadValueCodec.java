package org.sequeless.core.statemachine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.Value;

/**
 * Converts a {@link Value} to and from the tagged, JSON-compatible shape an {@link
 * OutboxEntry#payload()} entry is built from: a single-key {@code Map<String, Object>} whose key
 * names the {@link Value} variant ({@code text}, {@code integer}, {@code decimal}, {@code bool},
 * {@code dateTime}, {@code date}, {@code ref}, or {@code list}), mirroring the tag scheme {@code
 * sequeless-adapter-persistence-postgres}'s {@code ValueJsonCodec} uses for {@code
 * sq_object.props} — so a future JSON-persistence task for the outbox can reuse this exact scheme
 * without inventing a second one. This class is a fresh implementation, not a reuse of that
 * adapter's codec: {@code sequeless-core} cannot depend on an adapter module, and this codec
 * targets plain {@code Map<String, Object>}/{@code List<Object>} rather than Jackson's {@code
 * JsonNode}.
 *
 * <p>{@link #toPayload(Value)} is used by {@code DefaultTransitionService} to build each {@code
 * ActionRequest} {@link OutboxEntry}'s {@code self} map and kind-specific {@code value} fields;
 * {@link #fromPayload(Object)} is the symmetric decode a later {@code
 * org.sequeless.core.automation.ActionExecutor} implementation uses to read them back.
 *
 * <p><b>Native types, not JSON text.</b> Unlike {@code ValueJsonCodec}, this codec never touches
 * real JSON text in this phase — the outbox is still a purely in-memory {@code Map<String,
 * Object>} until a later postgres-persistence task serializes it — so {@link IntegerValue} and
 * {@link DecimalValue} round-trip as native {@link Long}/{@link BigDecimal} (an {@link
 * OutboxEntry} payload value need only be JSON-<em>compatible</em>, i.e. a {@link Number}, not
 * actual JSON text), preserving exact decimal scale rather than risking the precision loss a real
 * JSON-text round trip can introduce (see {@link DecimalValue}'s own javadoc). {@link
 * DateTimeValue}, {@link DateValue}, and {@link ReferenceValue} have no {@link Number}/{@link
 * Boolean}/{@link String} representation other than their canonical string form, so — exactly as
 * {@code ValueJsonCodec} does — they are always encoded as a {@link String} ({@link
 * Instant#toString()}, {@link LocalDate#toString()}, and the target {@link ObjectId}'s UUID
 * string, respectively). A future task that serializes this payload to real JSON text must either
 * reuse this exact tag scheme as-is (native numbers stay JSON numbers) or explicitly decide to
 * stringify {@code decimal}/{@code integer} too; that decision is out of scope here.
 */
public final class PayloadValueCodec {

    private static final String TAG_TEXT = "text";
    private static final String TAG_INTEGER = "integer";
    private static final String TAG_DECIMAL = "decimal";
    private static final String TAG_BOOL = "bool";
    private static final String TAG_DATE_TIME = "dateTime";
    private static final String TAG_DATE = "date";
    private static final String TAG_REF = "ref";
    private static final String TAG_LIST = "list";

    private PayloadValueCodec() {}

    /**
     * @param value the value to encode; must not be {@code null}
     * @return a single-key {@code Map<String, Object>} (or, for {@link ListValue}, a single-key map
     *     whose value is a {@code List<Object>} of such maps) — always JSON-compatible per {@link
     *     OutboxEntry}'s own requirement
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static Object toPayload(Value value) {
        Objects.requireNonNull(value, "value must not be null");
        return switch (value) {
            case TextValue text -> tag(TAG_TEXT, text.value());
            case IntegerValue integer -> tag(TAG_INTEGER, integer.value());
            case DecimalValue decimal -> tag(TAG_DECIMAL, decimal.value());
            case BoolValue bool -> tag(TAG_BOOL, bool.value());
            case DateTimeValue dateTime -> tag(TAG_DATE_TIME, dateTime.value().toString());
            case DateValue date -> tag(TAG_DATE, date.value().toString());
            case ReferenceValue ref -> tag(TAG_REF, ref.target().value().toString());
            case ListValue list ->
                tag(TAG_LIST, list.values().stream().map(PayloadValueCodec::toPayload).toList());
        };
    }

    /**
     * @param payload a single-key tagged map previously produced by {@link #toPayload(Value)};
     *     must not be {@code null}
     * @return the decoded {@link Value}
     * @throws NullPointerException if {@code payload} is {@code null}
     * @throws IllegalArgumentException if {@code payload} is not a single-key map with a
     *     recognized tag key
     */
    public static Value fromPayload(Object payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (!(payload instanceof Map<?, ?> map) || map.size() != 1) {
            throw new IllegalArgumentException(
                "Expected a single-key tagged value map, got: " + payload);
        }
        Map.Entry<?, ?> entry = map.entrySet().iterator().next();
        Object rawTag = entry.getKey();
        Object raw = entry.getValue();
        if (!(rawTag instanceof String tag)) {
            throw new IllegalArgumentException("Tagged value map key must be a String, got: " + rawTag);
        }
        return switch (tag) {
            case TAG_TEXT -> Value.text((String) raw);
            case TAG_INTEGER -> Value.integer(((Number) raw).longValue());
            case TAG_DECIMAL ->
                Value.decimal(raw instanceof BigDecimal decimal ? decimal : new BigDecimal(raw.toString()));
            case TAG_BOOL -> Value.bool((Boolean) raw);
            case TAG_DATE_TIME -> Value.dateTime(Instant.parse((String) raw));
            case TAG_DATE -> Value.date(LocalDate.parse((String) raw));
            case TAG_REF -> Value.ref(ObjectId.parse((String) raw));
            case TAG_LIST ->
                Value.list(((List<?>) raw).stream().map(PayloadValueCodec::fromPayload).toList());
            default -> throw new IllegalArgumentException("Unrecognized tagged value key: " + tag);
        };
    }

    private static Map<String, Object> tag(String tagName, Object value) {
        return Map.of(tagName, value);
    }
}
