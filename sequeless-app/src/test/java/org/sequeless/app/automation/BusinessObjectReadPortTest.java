package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.app.automation.BindingFakes.RecordingObjects;
import org.sequeless.automation.api.ObjectReadDeniedException;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.core.AuthorizationException;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.authz.AccessDecision;

/** Unit tests for {@link BusinessObjectReadPort}: absent is empty, present is a snapshot, a denial is thrown. */
class BusinessObjectReadPortTest {

    private static final String TYPE = "https://sequeless.dev/ns/test#Thing";
    private static final String NAME = "https://sequeless.dev/ns/test#name";
    private static final ObjectId ID = new ObjectId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));

    private final RecordingObjects objects = new RecordingObjects();
    private final BusinessObjectReadPort port = new BusinessObjectReadPort(new ObjectTypes(objects), objects);

    @Test
    void anAbsentObjectIsAnEmptyResultAndIsNotRead() {
        Optional<ObjectSnapshot> found = port.find(BindingFakes.SCOPE, ID);

        assertThat(found).isEmpty();
    }

    @Test
    void aPresentObjectIsReturnedAsASnapshotOfItsTypeStateAndValues() {
        objects.views.put(ID, BindingFakes.view(ID, TYPE, 2, Optional.empty(), Map.of(NAME, Value.text("kept"))));

        ObjectSnapshot snapshot = port.find(BindingFakes.SCOPE, ID).orElseThrow();

        assertThat(snapshot.typeIri()).isEqualTo(TYPE);
        assertThat(snapshot.state()).isEmpty();
        assertThat(snapshot.properties()).containsEntry(NAME, Value.text("kept"));
    }

    @Test
    void anObjectDeletedBetweenTheLookupAndTheReadIsAnEmptyResult() {
        objects.views.put(ID, BindingFakes.view(ID, TYPE, 2, Optional.empty(), Map.of()));
        objects.readFailure = new ObjectNotFoundException(ID);

        assertThat(port.find(BindingFakes.SCOPE, ID)).isEmpty();
    }

    @Test
    void aDeniedReadThrowsAndDoesNotNameTheObjectInTheMessage() {
        objects.views.put(ID, BindingFakes.view(ID, TYPE, 2, Optional.empty(), Map.of()));
        objects.readFailure = new AuthorizationException(new AccessDecision(false, "denied"));

        assertThatThrownBy(() -> port.find(BindingFakes.SCOPE, ID))
                .isInstanceOf(ObjectReadDeniedException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(ID.toString()))
                .hasCauseInstanceOf(AuthorizationException.class);
    }
}
