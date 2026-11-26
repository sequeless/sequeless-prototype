package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.app.automation.BindingFakes.RecordingObjects;
import org.sequeless.app.automation.BindingFakes.RecordingTransitions;
import org.sequeless.automation.api.CommandProvenance;
import org.sequeless.automation.api.CreateObjectCommand;
import org.sequeless.automation.api.FireTransitionCommand;
import org.sequeless.automation.api.SetPropertyCommand;
import org.sequeless.automation.api.StoreCommandRejectedException;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.validation.ValidationException;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.TriggerKind;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.validation.Violation;

/**
 * Unit tests for {@link BusinessObjectStoreCommandPort}. Each write goes through the Command use case fake, and a
 * rejection by the Command side is a {@link StoreCommandRejectedException} with nothing written.
 */
class BusinessObjectStoreCommandPortTest {

    private static final String TYPE = "https://sequeless.dev/ns/test#Thing";
    private static final String NAME = "https://sequeless.dev/ns/test#name";
    private static final String COUNT = "https://sequeless.dev/ns/test#count";
    private static final String TOTAL = "https://sequeless.dev/ns/test#total";
    private static final String TAGS = "https://sequeless.dev/ns/test#tags";
    private static final ObjectId OBJECT = new ObjectId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final ObjectId NEW_OBJECT = new ObjectId(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
    private static final CommandProvenance PROVENANCE =
            new CommandProvenance(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), 0, 1);

    private final RecordingObjects objects = new RecordingObjects();
    private final RecordingTransitions transitions = new RecordingTransitions();

    /** NAME, COUNT and TOTAL are single-valued in the fixture schema; TAGS is unbounded. */
    private final BusinessObjectStoreCommandPort port = new BusinessObjectStoreCommandPort(
            new ObjectTypes(objects),
            objects,
            transitions,
            BindingFakes.behaviourWithDerived(TOTAL),
            new ShapeCoercion(BindingFakes.schemaDeclaring(
                    TYPE,
                    Map.of(
                            NAME, Cardinality.atMost(1),
                            COUNT, Cardinality.atMost(1),
                            TOTAL, Cardinality.atMost(1),
                            TAGS, new Cardinality(0, OptionalInt.empty())))));

    private void storeOneObject() {
        objects.views.put(
                OBJECT,
                BindingFakes.view(
                        OBJECT,
                        TYPE,
                        3,
                        Optional.empty(),
                        Map.of(NAME, Value.text("old"), COUNT, Value.integer(2), TOTAL, Value.integer(9))));
    }

    @Test
    void setPropertyReplacesOneValueKeepsTheStoredOnesAndDropsDerivedValues() {
        storeOneObject();

        port.setProperty(BindingFakes.SCOPE, new SetPropertyCommand(OBJECT, NAME, Value.text("new"), PROVENANCE));

        assertThat(objects.edited).hasSize(1);
        BindingFakes.RecordingObjects.Edited edit = objects.edited.get(0);
        assertThat(edit.id()).isEqualTo(OBJECT);
        assertThat(edit.expectedVersion()).isEqualTo(3L);
        assertThat(edit.properties()).isEqualTo(Map.of(NAME, "new", COUNT, 2L));
    }

    @Test
    void setPropertyOnAnAbsentObjectIsRejectedAndWritesNothing() {
        assertThatThrownBy(() -> port.setProperty(
                        BindingFakes.SCOPE, new SetPropertyCommand(OBJECT, NAME, Value.text("x"), PROVENANCE)))
                .isInstanceOf(StoreCommandRejectedException.class);
        assertThat(objects.edited).isEmpty();
    }

    @Test
    void aVersionConflictIsRejectedWithTheConflictAsCause() {
        storeOneObject();
        objects.editFailure = new StaleObjectException(OBJECT, 3);

        assertThatThrownBy(() -> port.setProperty(
                        BindingFakes.SCOPE, new SetPropertyCommand(OBJECT, NAME, Value.text("x"), PROVENANCE)))
                .isInstanceOf(StoreCommandRejectedException.class)
                .hasMessageContaining("version conflict")
                .hasCauseInstanceOf(StaleObjectException.class);
        assertThat(objects.edited).isEmpty();
    }

    @Test
    void aStoreSideValidationRejectionOnASetPropertyWritesNothing() {
        storeOneObject();
        objects.editFailure = new ValidationException(
                ValidationException.Source.STRUCTURAL, List.of(new Violation(NAME, "must not be blank")));

        assertThatThrownBy(() -> port.setProperty(
                        BindingFakes.SCOPE, new SetPropertyCommand(OBJECT, NAME, Value.text(" "), PROVENANCE)))
                .isInstanceOf(StoreCommandRejectedException.class)
                .hasCauseInstanceOf(ValidationException.class);
        assertThat(objects.edited).isEmpty();
    }

    @Test
    void aDeniedSetPropertyIsRejected() {
        storeOneObject();
        objects.editFailure = new AuthorizationException(new AccessDecision(false, "denied"));

        assertThatThrownBy(() -> port.setProperty(
                        BindingFakes.SCOPE, new SetPropertyCommand(OBJECT, NAME, Value.text("x"), PROVENANCE)))
                .isInstanceOf(StoreCommandRejectedException.class)
                .hasCauseInstanceOf(AuthorizationException.class);
    }

    @Test
    void createObjectAddsTheObjectUnderTheCallersId() {
        port.createObject(
                BindingFakes.SCOPE,
                new CreateObjectCommand(NEW_OBJECT, TYPE, Map.of(NAME, Value.text("made")), PROVENANCE));

        assertThat(objects.added).hasSize(1);
        BindingFakes.RecordingObjects.Added add = objects.added.get(0);
        assertThat(add.id()).isEqualTo(NEW_OBJECT);
        assertThat(add.type()).isEqualTo(TYPE);
        assertThat(add.properties()).isEqualTo(Map.of(NAME, "made"));
    }

    @Test
    void aScalarOnAMultiValuedPropertyReachesTheCommandSideAsAOneElementList() {
        port.createObject(
                BindingFakes.SCOPE,
                new CreateObjectCommand(NEW_OBJECT, TYPE, Map.of(TAGS, Value.text("one")), PROVENANCE));

        assertThat(objects.added).hasSize(1);
        assertThat(objects.added.get(0).properties()).isEqualTo(Map.of(TAGS, List.of("one")));
    }

    @Test
    void aRepeatedCreateUnderAnExistingIdIsANoOp() {
        objects.views.put(
                NEW_OBJECT,
                BindingFakes.view(NEW_OBJECT, TYPE, 1, Optional.empty(), Map.of(NAME, Value.text("already"))));

        port.createObject(
                BindingFakes.SCOPE,
                new CreateObjectCommand(NEW_OBJECT, TYPE, Map.of(NAME, Value.text("again")), PROVENANCE));

        assertThat(objects.added).isEmpty();
    }

    @Test
    void aStoreSideValidationRejectionOnACreateWritesNothing() {
        objects.addFailure = new ValidationException(
                ValidationException.Source.SHACL, List.of(new Violation(NAME, "shape violated")));

        assertThatThrownBy(() -> port.createObject(
                        BindingFakes.SCOPE,
                        new CreateObjectCommand(NEW_OBJECT, TYPE, Map.of(NAME, Value.text("bad")), PROVENANCE)))
                .isInstanceOf(StoreCommandRejectedException.class)
                .hasCauseInstanceOf(ValidationException.class);
        assertThat(objects.added).isEmpty();
    }

    @Test
    void fireTransitionReturnsTrueWhenTheTransitionCommits() {
        transitions.result = Optional.of(BindingFakes.businessObject(OBJECT, TYPE));

        boolean fired = port.fireTransition(
                BindingFakes.SCOPE, new FireTransitionCommand(OBJECT, "expire", FireTransitionCommand.Trigger.TIMER));

        assertThat(fired).isTrue();
        assertThat(transitions.kinds).containsExactly(TriggerKind.TIMER);
    }

    @Test
    void fireTransitionReturnsFalseForAnUnavailableTransitionAndMapsOnChange() {
        boolean fired = port.fireTransition(
                BindingFakes.SCOPE,
                new FireTransitionCommand(OBJECT, "expire", FireTransitionCommand.Trigger.ON_CHANGE));

        assertThat(fired).isFalse();
        assertThat(transitions.kinds).containsExactly(TriggerKind.ON_CHANGE);
    }

    @Test
    void aStaleFireTransitionIsRejected() {
        transitions.failure = new StaleObjectException(OBJECT, 1);

        assertThatThrownBy(() -> port.fireTransition(
                        BindingFakes.SCOPE,
                        new FireTransitionCommand(OBJECT, "expire", FireTransitionCommand.Trigger.ON_CHANGE)))
                .isInstanceOf(StoreCommandRejectedException.class)
                .hasCauseInstanceOf(StaleObjectException.class);
    }
}
