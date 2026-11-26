package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

/** Pins the Q1 mapping from the Command side's view to the released snapshot. */
class ObjectSnapshotsTest {

    private static final String TYPE = "https://sequeless.dev/ns/test#Thing";
    private static final String STATE = "https://sequeless.dev/ns/test#Open";
    private static final String NAME = "https://sequeless.dev/ns/test#name";

    @Test
    void carriesTheTypeIriTheStateIriAndThePropertiesKeyedByIri() {
        ObjectId id = new ObjectId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var view = BindingFakes.view(id, TYPE, 4, Optional.of(STATE), Map.of(NAME, Value.text("x")));

        ObjectSnapshot snapshot = ObjectSnapshots.from(view);

        assertThat(snapshot.typeIri()).isEqualTo(TYPE);
        assertThat(snapshot.state()).contains(STATE);
        assertThat(snapshot.properties()).containsExactly(Map.entry(NAME, Value.text("x")));
    }

    @Test
    void anObjectWithoutAStateHasAnEmptyState() {
        ObjectId id = new ObjectId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        var view = BindingFakes.view(id, TYPE, 1, Optional.empty(), Map.of());

        assertThat(ObjectSnapshots.from(view).state()).isEmpty();
    }
}
