package org.sequeless.core.automation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.core.api.TransitionService;
import org.sequeless.core.ontology.SchemaAndBehaviour;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.behaviour.api.ExternalSignalTrigger;
import org.sequeless.ontology.behaviour.api.OnChangeTrigger;
import org.sequeless.ontology.behaviour.api.StateMachineDefinition;
import org.sequeless.ontology.behaviour.api.Transition;
import org.sequeless.ontology.behaviour.api.TriggerKind;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.PropertyRef;

/**
 * Reference implementation of {@link TriggerEvaluator}, called back into by an automation adapter
 * (the in-process relay, or a Temporal activity) once a domain-lifecycle change, an elapsed timer,
 * or an external signal might make an {@code OnChange}/{@code Timer}/{@code ExternalSignal}-
 * triggered transition available. Conceptually parallel to {@link DefaultActionExecutor} — both are
 * inbound-port implementations an adapter calls, rather than outbound ports this class calls out
 * to — but this class never mutates an object directly: every actual state move goes through
 * {@link TransitionService#fireAutomated}, which owns guard evaluation, availability checking, and
 * the shared commit path, and which never throws for a stale hint. Every method here mirrors that
 * silent-no-op contract: a malformed payload missing a required key is the only thing allowed to
 * throw.
 */
public final class DefaultTriggerEvaluator implements TriggerEvaluator {

    private final SchemaSource schemaSource;
    private final BehaviourSource behaviourSource;
    private final ObjectStorePort objectStorePort;
    private final TransitionService transitionService;

    /**
     * @param schemaSource the port the schema half is read from; must not be {@code null}
     * @param behaviourSource the port the behaviour half is read from; must not be {@code null}
     * @param objectStorePort the port every candidate/target read goes through; must not be {@code
     *     null}
     * @param transitionService the port {@code fireAutomated} is called on; must not be {@code
     *     null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultTriggerEvaluator(
            SchemaSource schemaSource,
            BehaviourSource behaviourSource,
            ObjectStorePort objectStorePort,
            TransitionService transitionService) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.behaviourSource = Objects.requireNonNull(behaviourSource, "behaviourSource must not be null");
        this.objectStorePort = Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.transitionService = Objects.requireNonNull(transitionService, "transitionService must not be null");
    }

    @Override
    public void onChange(Scope scope, OutboxEntry changeEvent) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(changeEvent, "changeEvent must not be null");

        Map<String, Object> payload = changeEvent.payload();
        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String typeIri = (String) payload.get("typeIri");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        Behaviour behaviour = halves.behaviour();
        Optional<TypeDefinition> maybeChangedType = schema.type(typeIri);
        if (maybeChangedType.isEmpty()) {
            return;
        }
        TypeDefinition changedType = maybeChangedType.get();

        boolean deleted = OutboxEntry.KIND_OBJECT_DELETED.equals(changeEvent.kind());
        Map<PropertyRef, Value> changedProperties;
        if (deleted) {
            changedProperties = decodePropertiesSnapshot(payload);
        } else {
            Optional<BusinessObject> found = objectStorePort.find(scope, objectId);
            if (found.isEmpty()) {
                // Stale event: the object has since been deleted. No-op.
                return;
            }
            changedProperties = found.get().properties();
        }

        Set<ObjectId> candidates = new LinkedHashSet<>();
        if (!deleted) {
            // A deleted object transitioning is meaningless; only a live change makes the changed
            // object itself a candidate for its own OnChange transitions.
            candidates.add(objectId);
        }
        for (TypeDefinition candidateType : schema.types()) {
            Optional<StateMachineDefinition> maybeMachine = behaviour.stateMachine(candidateType.iri());
            if (maybeMachine.isEmpty()) {
                continue;
            }
            for (Transition transition : maybeMachine.get().transitions()) {
                if (!(transition.trigger() instanceof OnChangeTrigger onChangeTrigger)) {
                    continue;
                }
                for (String watchIri : onChangeTrigger.watchIris()) {
                    if (!isDeclaredOn(changedType, watchIri)) {
                        continue;
                    }
                    collectReferencedIds(changedProperties.get(new PropertyRef(watchIri)), candidates);
                }
            }
        }

        for (ObjectId candidateId : candidates) {
            fireFirstAvailableOnChange(scope, behaviour, candidateId);
        }
    }

    @Override
    public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(timerScheduled, "timerScheduled must not be null");

        Map<String, Object> payload = timerScheduled.payload();
        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String state = (String) payload.get("state");
        String transitionName = (String) payload.get("transitionName");

        Optional<BusinessObject> found = objectStorePort.find(scope, objectId);
        if (found.isEmpty()) {
            // The object is gone entirely: stale timer, no-op.
            return;
        }
        BusinessObject object = found.get();
        if (object.state().isEmpty() || !object.state().get().equals(state)) {
            // The object left the state the timer was waiting in: stale timer, no-op. This
            // staleness check, not TimerCancelled, is the real safety net.
            return;
        }
        transitionService.fireAutomated(scope, objectId, transitionName, TriggerKind.TIMER);
    }

    @Override
    public void onSignal(Scope scope, OutboxEntry signalReceived) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(signalReceived, "signalReceived must not be null");

        Map<String, Object> payload = signalReceived.payload();
        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String signalName = (String) payload.get("signalName");

        Optional<BusinessObject> found = objectStorePort.find(scope, objectId);
        if (found.isEmpty() || found.get().state().isEmpty()) {
            return;
        }
        BusinessObject object = found.get();

        Behaviour behaviour = behaviourSource.behaviour(scope.tenantId());
        Optional<StateMachineDefinition> maybeMachine =
                behaviour.stateMachine(object.type().iri());
        if (maybeMachine.isEmpty()) {
            return;
        }
        StateMachineDefinition machine = maybeMachine.get();
        String currentState = object.state().get();

        for (Transition transition : machine.transitions()) {
            if (transition.fromStateIri().equals(currentState)
                    && transition.trigger() instanceof ExternalSignalTrigger signalTrigger
                    && signalTrigger.signalName().equals(signalName)) {
                transitionService.fireAutomated(scope, objectId, transition.name(), TriggerKind.EXTERNAL_SIGNAL);
                return;
            }
        }
        // No transition on the object's current state matches this signal name: silent no-op.
    }

    /**
     * Reads {@code candidateId} fresh, and fires at most one {@code OnChange} transition departing
     * its current state, in declaration order, stopping at the first one {@link
     * TransitionService#fireAutomated} actually commits. A candidate that no longer exists, has no
     * current state, resolves to a type with no state machine, or has no {@code OnChange}
     * transition departing its current state is a silent no-op — exactly as stale a hint as any
     * other this class reacts to.
     */
    private void fireFirstAvailableOnChange(Scope scope, Behaviour behaviour, ObjectId candidateId) {
        Optional<BusinessObject> found = objectStorePort.find(scope, candidateId);
        if (found.isEmpty() || found.get().state().isEmpty()) {
            return;
        }
        BusinessObject candidate = found.get();
        Optional<StateMachineDefinition> maybeMachine =
                behaviour.stateMachine(candidate.type().iri());
        if (maybeMachine.isEmpty()) {
            return;
        }
        StateMachineDefinition machine = maybeMachine.get();
        String currentState = candidate.state().get();

        for (Transition transition : machine.transitions()) {
            if (!(transition.trigger() instanceof OnChangeTrigger)) {
                continue;
            }
            if (!transition.fromStateIri().equals(currentState)) {
                continue;
            }
            Optional<BusinessObject> result =
                    transitionService.fireAutomated(scope, candidateId, transition.name(), TriggerKind.ON_CHANGE);
            if (result.isPresent()) {
                return;
            }
        }
    }

    /**
     * @return {@code true} if {@code propertyIri} appears in {@code type.properties()} — already
     *     the full, flattened set attributed to {@code type} including everything inherited from
     *     its supertypes, per {@code SnapshotMapper}'s attribution algorithm, so no separate
     *     supertype walk is needed here
     */
    private static boolean isDeclaredOn(TypeDefinition type, String propertyIri) {
        return type.properties().stream().anyMatch(property -> property.iri().equals(propertyIri));
    }

    /**
     * Collects the {@link ObjectId}s a relationship {@link Value} currently references into {@code
     * out}: a bare {@link ReferenceValue}, or every {@link ReferenceValue} element of a {@link
     * ListValue}. A {@code null} value (the property is absent on the changed object), a scalar
     * value, or a {@link ListValue} of non-references contributes no ids.
     */
    private static void collectReferencedIds(Value value, Set<ObjectId> out) {
        if (value instanceof ReferenceValue reference) {
            out.add(reference.target());
        } else if (value instanceof ListValue list) {
            for (Value element : list.values()) {
                if (element instanceof ReferenceValue reference) {
                    out.add(reference.target());
                }
            }
        }
    }

    /**
     * Decodes an {@code ObjectDeleted} payload's {@code properties} snapshot — a full-IRI-keyed,
     * {@link PayloadValueCodec}-encoded map captured at delete time precisely because {@link
     * ObjectStorePort#find} can never read a soft-deleted object back.
     */
    @SuppressWarnings("unchecked")
    private static Map<PropertyRef, Value> decodePropertiesSnapshot(Map<String, Object> payload) {
        Map<String, Object> raw = (Map<String, Object>) payload.get("properties");
        Map<PropertyRef, Value> result = new HashMap<>();
        raw.forEach((iri, tagged) -> result.put(new PropertyRef(iri), PayloadValueCodec.fromPayload(tagged)));
        return result;
    }
}
