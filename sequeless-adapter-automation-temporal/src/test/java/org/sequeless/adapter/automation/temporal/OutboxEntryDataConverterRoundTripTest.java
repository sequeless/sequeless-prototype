package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.api.common.v1.Payload;
import io.temporal.common.converter.DataConverter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * Proves Temporal's default {@link DataConverter} can serialize and deserialize a real {@link
 * OutboxEntry} record (a {@link UUID} field, an {@link Instant} field, and a nested {@code
 * Map<String, Object>} payload) with no bespoke DTO or custom Jackson module, before any
 * workflow/activity code in this module is written against {@code OutboxEntry} directly.
 *
 * <p>This is deliberately the first thing this module's test suite checks: if it ever regresses
 * (e.g. after a Temporal SDK upgrade changes the default {@code ObjectMapper}'s record handling),
 * {@link TransitionActionWorkflowImpl}/{@link ActionActivities} would fail in a much more confusing way — a
 * workflow that silently loses data, or a worker that never starts — so this test fails fast and
 * explains exactly what broke instead.
 */
class OutboxEntryDataConverterRoundTripTest {

    @Test
    void roundTripsOutboxEntryThroughDefaultDataConverter() {
        Map<String, Object> self = new LinkedHashMap<>();
        self.put("owner", "user-2");
        self.put("priority", 3);
        self.put("urgent", true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("tenantId", "tenant-1");
        payload.put("principalId", "user-1");
        payload.put("transitionName", "activate");
        payload.put("fromState", "http://example.org/Project#Draft");
        payload.put("toState", "http://example.org/Project#Active");
        payload.put("ontologyVersion", 1);
        payload.put("self", self);
        payload.put("typeIri", "http://example.org/Project");
        payload.put("machineIri", "http://example.org/ProjectMachine");

        OutboxEntry original =
                new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.now());

        DataConverter converter = DataConverter.getDefaultInstance();
        Optional<Payload> encoded = converter.toPayload(original);
        assertThat(encoded).isPresent();

        OutboxEntry roundTripped = converter.fromPayload(encoded.get(), OutboxEntry.class, OutboxEntry.class);

        assertThat(roundTripped).isEqualTo(original);
        assertThat(roundTripped.id()).isEqualTo(original.id());
        assertThat(roundTripped.kind()).isEqualTo(original.kind());
        assertThat(roundTripped.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(roundTripped.payload()).isEqualTo(original.payload());
    }
}
