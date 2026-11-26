package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.StoreEvent;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TransitionFiredEvent;

/**
 * Unit-tests {@link StoreEvents#from} (spec 17, section 3): each Store event kind maps to its released event with the
 * row's id, payload and {@code occurredAt} unchanged, and any other kind is rejected.
 */
class StoreEventsTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final Instant OCCURRED = Instant.parse("2026-01-02T03:04:05Z");
    private static final Map<String, Object> PAYLOAD = Map.of("objectId", "o-1", "version", 2L);

    @Test
    void anObjectCreatedRowMapsToACreatedChange() {
        StoreEvent event =
                StoreEvents.from(new StoreEventRow(ID, StoreEventRow.KIND_OBJECT_CREATED, PAYLOAD, OCCURRED));

        assertThat(event).isEqualTo(new ObjectChangedEvent(ID, ObjectChangedEvent.Change.CREATED, PAYLOAD, OCCURRED));
    }

    @Test
    void anObjectUpdatedRowMapsToAnUpdatedChange() {
        StoreEvent event =
                StoreEvents.from(new StoreEventRow(ID, StoreEventRow.KIND_OBJECT_UPDATED, PAYLOAD, OCCURRED));

        assertThat(event).isEqualTo(new ObjectChangedEvent(ID, ObjectChangedEvent.Change.UPDATED, PAYLOAD, OCCURRED));
    }

    @Test
    void anObjectDeletedRowMapsToADeletedChange() {
        StoreEvent event =
                StoreEvents.from(new StoreEventRow(ID, StoreEventRow.KIND_OBJECT_DELETED, PAYLOAD, OCCURRED));

        assertThat(event).isEqualTo(new ObjectChangedEvent(ID, ObjectChangedEvent.Change.DELETED, PAYLOAD, OCCURRED));
    }

    @Test
    void aTransitionFiredRowMapsToATransitionFiredEvent() {
        StoreEvent event =
                StoreEvents.from(new StoreEventRow(ID, StoreEventRow.KIND_TRANSITION_FIRED, PAYLOAD, OCCURRED));

        assertThat(event).isEqualTo(new TransitionFiredEvent(ID, PAYLOAD, OCCURRED));
    }

    @Test
    void rowOfAKindThatIsNotAStoreEventKindIsRejected() {
        StoreEventRow row = new StoreEventRow(ID, "ActionRequest", Map.of("objectId", "o-1"), OCCURRED);

        assertThatThrownBy(() -> StoreEvents.from(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a Store event kind: ActionRequest");
    }
}
