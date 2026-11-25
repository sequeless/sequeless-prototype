package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.outbox.OutboxEntry;

/** Unit-tests {@link StoreEvents#from} for a row whose kind is not a Store event kind. */
class StoreEventsTest {

    @Test
    void rowOfAKindThatIsNotAStoreEventKindIsRejected() {
        OutboxEntry row = new OutboxEntry(UUID.randomUUID(), "ActionRequest", Map.of("objectId", "o-1"), Instant.now());

        assertThatThrownBy(() -> StoreEvents.from(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a Store event kind: ActionRequest");
    }
}
