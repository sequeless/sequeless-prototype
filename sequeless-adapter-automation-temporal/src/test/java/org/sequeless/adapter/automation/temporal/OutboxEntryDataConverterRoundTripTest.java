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
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * Proves Temporal's default {@link DataConverter} can serialize and deserialize the records this module
 * passes through it as workflow or activity inputs: a real {@link OutboxEntry} record (a {@link UUID}
 * field, an {@link Instant} field, and a nested {@code Map<String, Object>} payload), and the
 * {@link ObjectChangedEvent} and {@link TransitionFiredEvent} Store events, the first with its nested
 * {@link ObjectChangedEvent.Change} enum. All of them go through the same converter with no bespoke DTO
 * or custom Jackson module.
 *
 * <p>It fails first, with a clear message, if the default converter regresses (for example after a
 * Temporal SDK upgrade changes the default {@code ObjectMapper}'s record handling). Without it,
 * {@link TransitionActionWorkflowImpl} and {@link ActionActivities} would fail in a more confusing
 * way: a workflow that silently loses data, or a worker that never starts.
 */
class OutboxEntryDataConverterRoundTripTest {

    @Test
    void roundTripsOutboxEntryThroughDefaultDataConverter() {
        OutboxEntry original =
                new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, samplePayload(), Instant.now());

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

    @Test
    void roundTripsObjectChangedEventThroughDefaultDataConverter() {
        ObjectChangedEvent original = new ObjectChangedEvent(
                UUID.randomUUID(), ObjectChangedEvent.Change.UPDATED, samplePayload(), Instant.now());

        DataConverter converter = DataConverter.getDefaultInstance();
        Optional<Payload> encoded = converter.toPayload(original);
        assertThat(encoded).isPresent();

        ObjectChangedEvent roundTripped =
                converter.fromPayload(encoded.get(), ObjectChangedEvent.class, ObjectChangedEvent.class);

        assertThat(roundTripped).isEqualTo(original);
        assertThat(roundTripped.change()).isEqualTo(ObjectChangedEvent.Change.UPDATED);
        assertThat(roundTripped.payload()).isEqualTo(original.payload());
    }

    @Test
    void roundTripsTransitionFiredEventThroughDefaultDataConverter() {
        TransitionFiredEvent original = new TransitionFiredEvent(UUID.randomUUID(), samplePayload(), Instant.now());

        DataConverter converter = DataConverter.getDefaultInstance();
        Optional<Payload> encoded = converter.toPayload(original);
        assertThat(encoded).isPresent();

        TransitionFiredEvent roundTripped =
                converter.fromPayload(encoded.get(), TransitionFiredEvent.class, TransitionFiredEvent.class);

        assertThat(roundTripped).isEqualTo(original);
        assertThat(roundTripped.payload()).isEqualTo(original.payload());
    }

    /** A payload with the shapes a real row carries: strings, an integer stamp and a nested typed value. */
    private static Map<String, Object> samplePayload() {
        // Tags mirror PayloadValueCodec's scheme; written out here because this adapter cannot
        // depend on sequeless-core. PayloadValueCodec writes an integer as a Long, but Temporal's
        // default converter reads a small JSON integer back as Integer, so Integer 3 keeps the
        // whole-record equality in the tests. Readers must use Number.longValue(), as
        // PayloadValueCodec.fromPayload does.
        Map<String, Object> self = new LinkedHashMap<>();
        self.put("owner", Map.of("text", "user-2"));
        self.put("priority", Map.of("integer", 3));
        self.put("urgent", Map.of("bool", true));

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
        return payload;
    }
}
