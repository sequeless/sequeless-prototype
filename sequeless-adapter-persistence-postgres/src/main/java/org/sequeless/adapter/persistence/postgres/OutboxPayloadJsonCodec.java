package org.sequeless.adapter.persistence.postgres;

import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/**
 * Encodes an {@link org.sequeless.spi.object.OutboxEntry#payload()} to JSON text for {@code
 * sq_outbox.payload}, and decodes it back. Unlike {@link ValueJsonCodec}, no tagging is needed
 * here: {@code OutboxEntry}'s constructor already guarantees every value in the payload, at any
 * depth, is a plain {@code String}, {@code Number}, {@code Boolean}, {@code List<?>}, or {@code
 * Map<?, ?>}, which Jackson serialises directly without ambiguity, and deserialises back into the
 * same shape ({@code readValue} against a raw {@code Map} target yields {@code String}/{@code
 * Number}/{@code Boolean}/{@code List}/{@code Map} nodes, matching exactly what {@code
 * org.sequeless.core.statemachine.PayloadValueCodec}'s tagged maps were built from).
 */
final class OutboxPayloadJsonCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private OutboxPayloadJsonCodec() {}

    /**
     * @param payload the JSON-compatible payload to encode; must not be {@code null}
     * @return the JSON text to store in {@code sq_outbox.payload}
     */
    static String toJson(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        return MAPPER.writeValueAsString(payload);
    }

    /**
     * @param json previously-encoded {@code sq_outbox.payload} JSON text; must not be {@code null}
     * @return the decoded payload map, in the same shape {@link #toJson} was given
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> fromJson(String json) {
        Objects.requireNonNull(json, "json must not be null");
        return (Map<String, Object>) MAPPER.readValue(json, Map.class);
    }
}
