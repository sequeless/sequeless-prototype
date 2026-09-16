package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEntryTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    private static Map<String, Object> payloadWithObjectId(String objectId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", objectId);
        payload.put("type", "Task");
        payload.put("version", 1);
        payload.put("principal", "alice");
        return payload;
    }

    @Test
    void carriesAllFields() {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = payloadWithObjectId(UUID.randomUUID().toString());

        OutboxEntry entry = new OutboxEntry(id, OutboxEntry.KIND_OBJECT_CREATED, payload, NOW);

        assertThat(entry.id()).isEqualTo(id);
        assertThat(entry.kind()).isEqualTo(OutboxEntry.KIND_OBJECT_CREATED);
        assertThat(entry.payload()).isEqualTo(payload);
        assertThat(entry.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsNullId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new OutboxEntry(null, OutboxEntry.KIND_OBJECT_CREATED, payloadWithObjectId("x"), NOW));
    }

    @Test
    void rejectsBlankKind() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OutboxEntry(UUID.randomUUID(), "  ", payloadWithObjectId("x"), NOW));
    }

    @Test
    void rejectsNullPayload() {
        assertThatNullPointerException()
            .isThrownBy(() -> new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, null, NOW));
    }

    @Test
    void rejectsMissingObjectIdKey() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "Task");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, payload, NOW))
            .withMessageContaining("objectId");
    }

    @Test
    void rejectsObjectIdThatIsNotAString() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, payload, NOW))
            .withMessageContaining("objectId");
    }

    @Test
    void rejectsNullInsideNestedMap() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("key", null);
        Map<String, Object> payload = payloadWithObjectId(UUID.randomUUID().toString());
        payload.put("nested", nested);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, payload, NOW));
    }

    @Test
    void rejectsNullInsideNestedList() {
        Map<String, Object> payload = payloadWithObjectId(UUID.randomUUID().toString());
        List<Object> list = new java.util.ArrayList<>();
        list.add("a");
        list.add(null);
        payload.put("list", list);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, payload, NOW));
    }

    @Test
    void acceptsADeeplyNestedValidPayload() {
        Map<String, Object> payload = payloadWithObjectId(UUID.randomUUID().toString());
        payload.put("deep", Map.of("items", List.of(Map.of("a", 1, "b", "text"), Map.of("c", true))));

        OutboxEntry entry = new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, payload, NOW);

        assertThat(entry.payload()).isEqualTo(payload);
    }

    @Test
    void rejectsNullOccurredAt() {
        assertThatNullPointerException()
            .isThrownBy(() -> new OutboxEntry(
                UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, payloadWithObjectId("x"), null));
    }

    @Test
    void payloadIsUnmodifiable() {
        OutboxEntry entry = new OutboxEntry(
            UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, payloadWithObjectId("x"), NOW);

        assertThatThrownBy(() -> entry.payload().put("k", "v")).isInstanceOf(UnsupportedOperationException.class);
    }
}
