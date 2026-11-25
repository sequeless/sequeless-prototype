package org.sequeless.spi.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.automation.ObjectChangedEvent.Change;

class StoreEventTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private static Map<String, Object> payload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", UUID.randomUUID().toString());
        payload.put("ontologyVersion", 3);
        return payload;
    }

    private static String label(StoreEvent event) {
        return switch (event) {
            case ObjectChangedEvent e -> "ObjectChanged";
            case TransitionFiredEvent e -> "TransitionFired";
        };
    }

    @Test
    void objectChangedEventRejectsNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectChangedEvent(null, Change.CREATED, payload(), NOW))
                .withMessage("id must not be null");
    }

    @Test
    void objectChangedEventRejectsNullChange() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectChangedEvent(UUID.randomUUID(), null, payload(), NOW))
                .withMessage("change must not be null");
    }

    @Test
    void objectChangedEventRejectsNullPayload() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectChangedEvent(UUID.randomUUID(), Change.CREATED, null, NOW))
                .withMessage("payload must not be null");
    }

    @Test
    void objectChangedEventRejectsNullOccurredAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ObjectChangedEvent(UUID.randomUUID(), Change.CREATED, payload(), null))
                .withMessage("occurredAt must not be null");
    }

    @Test
    void transitionFiredEventRejectsNullId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TransitionFiredEvent(null, payload(), NOW))
                .withMessage("id must not be null");
    }

    @Test
    void transitionFiredEventRejectsNullPayload() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TransitionFiredEvent(UUID.randomUUID(), null, NOW))
                .withMessage("payload must not be null");
    }

    @Test
    void transitionFiredEventRejectsNullOccurredAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TransitionFiredEvent(UUID.randomUUID(), payload(), null))
                .withMessage("occurredAt must not be null");
    }

    @Test
    void objectChangedEventCopiesPayloadFromSource() {
        Map<String, Object> source = payload();
        Map<String, Object> before = new HashMap<>(source);
        ObjectChangedEvent event = new ObjectChangedEvent(UUID.randomUUID(), Change.UPDATED, source, NOW);

        source.put("objectId", "changed");
        source.put("added", "later");

        assertThat(event.payload()).isEqualTo(before);
    }

    @Test
    void transitionFiredEventCopiesPayloadFromSource() {
        Map<String, Object> source = payload();
        Map<String, Object> before = new HashMap<>(source);
        TransitionFiredEvent event = new TransitionFiredEvent(UUID.randomUUID(), source, NOW);

        source.put("objectId", "changed");
        source.put("added", "later");

        assertThat(event.payload()).isEqualTo(before);
    }

    @Test
    void recordsBuiltFromTheSameFieldsAreEqual() {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = payload();

        assertThat(new ObjectChangedEvent(id, Change.CREATED, payload, NOW))
                .isEqualTo(new ObjectChangedEvent(id, Change.CREATED, payload, NOW));
        assertThat(new TransitionFiredEvent(id, payload, NOW)).isEqualTo(new TransitionFiredEvent(id, payload, NOW));
    }

    @Test
    void objectChangedEventsDifferWhenChangeDiffers() {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = payload();

        assertThat(new ObjectChangedEvent(id, Change.CREATED, payload, NOW))
                .isNotEqualTo(new ObjectChangedEvent(id, Change.DELETED, payload, NOW));
    }

    @Test
    void changeDeclaresExactlyTheThreeMutationConstants() {
        assertThat(Change.values()).containsExactly(Change.CREATED, Change.UPDATED, Change.DELETED);
    }

    @Test
    void storeEventIsSealedToTheTwoRecords() {
        assertThat(StoreEvent.class.isSealed()).isTrue();
        assertThat(StoreEvent.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(ObjectChangedEvent.class, TransitionFiredEvent.class);
    }

    @Test
    void exhaustiveSwitchReturnsTheLabelForEachRecord() {
        assertThat(label(new ObjectChangedEvent(UUID.randomUUID(), Change.CREATED, payload(), NOW)))
                .isEqualTo("ObjectChanged");
        assertThat(label(new TransitionFiredEvent(UUID.randomUUID(), payload(), NOW)))
                .isEqualTo("TransitionFired");
    }
}
