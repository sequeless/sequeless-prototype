package org.sequeless.app.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.sequeless.automation.api.CreateObjectCommand;
import org.sequeless.automation.api.FireTransitionCommand;
import org.sequeless.automation.api.SetPropertyCommand;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.api.StoreCommandRejectedException;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.TriggerKind;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.store.core.AuthorizationException;
import org.sequeless.store.core.api.BusinessObjectService;
import org.sequeless.store.core.api.TransitionService;
import org.sequeless.store.core.api.TypeNotFoundException;
import org.sequeless.store.core.validation.ValidationException;

/**
 * The host's {@link StoreCommandPort}: every automation write goes through a Command use case, so validation,
 * authorization, provenance and the outbox write run as they do on the HTTP path (spec 17, section 10, Q3).
 *
 * <ul>
 *   <li>{@code setProperty} reads the object, then calls {@link BusinessObjectService#edit} with the stored,
 *       non-derived values and the one property replaced. A version conflict is a rejection, not a retry inside
 *       the binding.
 *   <li>{@code createObject} calls {@link BusinessObjectService#add} with the caller's id, so the derived id of a
 *       retried action finds the object it already created and does nothing.
 *   <li>{@code fireTransition} calls {@link TransitionService#fireAutomated}. An unavailable transition is
 *       {@code false}, a silent no-op.
 * </ul>
 *
 * <p>Any rejection by the Command side (denial, validation, version conflict, missing type) is thrown as
 * {@link StoreCommandRejectedException} with the cause attached. The claim then fails and is retried, and no part
 * of the action is applied.
 *
 * <p>Before a create or a set, the raw values are shaped to the declared cardinality by {@link ShapeCoercion}: a scalar
 * on a multi-valued property becomes a one-element list (spec 17, H7, decision b). The released executor emits scalars.
 *
 * <p>The {@link org.sequeless.automation.api.CommandProvenance} is checked by its record but is not written to the
 * object or its outbox row. The prototype's automation path did not write it either. Idempotency comes from the
 * derived id of a create and from the value comparison that the released executor makes before a set.
 */
final class BusinessObjectStoreCommandPort implements StoreCommandPort {

    private final ObjectTypes types;
    private final BusinessObjectService objects;
    private final TransitionService transitions;
    private final BehaviourSource behaviours;
    private final ShapeCoercion shape;

    /**
     * @param types the id-to-type lookup; must not be {@code null}
     * @param objects the Command use case for reads, writes and creates; must not be {@code null}
     * @param transitions the Command use case that fires automated transitions; must not be {@code null}
     * @param behaviours the behaviour the derived properties are read from; must not be {@code null}
     * @param shape shapes each raw value to its declared cardinality before the write; must not be {@code null}
     */
    BusinessObjectStoreCommandPort(
            ObjectTypes types,
            BusinessObjectService objects,
            TransitionService transitions,
            BehaviourSource behaviours,
            ShapeCoercion shape) {
        this.types = Objects.requireNonNull(types, "types must not be null");
        this.objects = Objects.requireNonNull(objects, "objects must not be null");
        this.transitions = Objects.requireNonNull(transitions, "transitions must not be null");
        this.behaviours = Objects.requireNonNull(behaviours, "behaviours must not be null");
        this.shape = Objects.requireNonNull(shape, "shape must not be null");
    }

    @Override
    public void setProperty(Scope scope, SetPropertyCommand command) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(command, "command must not be null");
        ObjectId id = command.objectId();
        String type = types.typeIriOf(scope, id)
                .orElseThrow(() -> new StoreCommandRejectedException(
                        "setProperty was rejected: the object is absent at command time"));

        ObjectView current = read(scope, type, id);
        Behaviour behaviour = behaviours.behaviour(scope.tenantId());
        Map<String, Object> raw = new LinkedHashMap<>();
        for (Map.Entry<PropertyRef, Value> entry : current.properties().entrySet()) {
            String iri = entry.getKey().iri();
            if (behaviour.derivation(iri).isEmpty()) {
                raw.put(iri, ValueWire.toRaw(entry.getValue()));
            }
        }
        raw.put(command.propertyIri(), ValueWire.toRaw(command.value()));

        try {
            objects.edit(scope, type, id, current.version(), shape.coerce(scope, type, raw));
        } catch (StaleObjectException conflict) {
            throw new StoreCommandRejectedException(
                    "setProperty was rejected: version conflict, the object is no longer at version "
                            + current.version(),
                    conflict);
        } catch (AuthorizationException | ValidationException | ObjectNotFoundException | TypeNotFoundException e) {
            throw rejected("setProperty", e);
        }
    }

    @Override
    public void createObject(Scope scope, CreateObjectCommand command) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(command, "command must not be null");
        if (types.typeIriOf(scope, command.objectId()).isPresent()) {
            // A retry of the same action: the object already exists under its derived id, so there is nothing to do.
            return;
        }
        try {
            objects.add(
                    scope,
                    command.typeIri(),
                    command.objectId(),
                    shape.coerce(scope, command.typeIri(), ValueWire.toRawProperties(command.properties())));
        } catch (AuthorizationException | ValidationException | TypeNotFoundException e) {
            throw rejected("createObject", e);
        }
    }

    @Override
    public boolean fireTransition(Scope scope, FireTransitionCommand command) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(command, "command must not be null");
        try {
            return transitions
                    .fireAutomated(scope, command.objectId(), command.transitionName(), triggerKind(command.trigger()))
                    .isPresent();
        } catch (StaleObjectException | AuthorizationException | ValidationException e) {
            throw rejected("fireTransition", e);
        }
    }

    private ObjectView read(Scope scope, String type, ObjectId id) {
        try {
            return objects.read(scope, type, id);
        } catch (AuthorizationException | ObjectNotFoundException | TypeNotFoundException e) {
            throw rejected("setProperty", e);
        }
    }

    private static TriggerKind triggerKind(FireTransitionCommand.Trigger trigger) {
        return switch (trigger) {
            case ON_CHANGE -> TriggerKind.ON_CHANGE;
            case TIMER -> TriggerKind.TIMER;
        };
    }

    private static StoreCommandRejectedException rejected(String operation, RuntimeException cause) {
        return new StoreCommandRejectedException(
                operation + " was rejected by the Store: " + cause.getMessage(), cause);
    }
}
