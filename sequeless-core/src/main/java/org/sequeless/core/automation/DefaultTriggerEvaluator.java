package org.sequeless.core.automation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.core.api.TransitionService;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.meta.ExternalSignalTrigger;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.OnChangeTrigger;
import org.sequeless.spi.meta.StateMachineDefinition;
import org.sequeless.spi.meta.Transition;
import org.sequeless.spi.meta.TriggerKind;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyPort;

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

    private final OntologyPort ontologyPort;
    private final ObjectStorePort objectStorePort;
    private final TransitionService transitionService;

    /**
     * @param ontologyPort the port consulted for the type system; must not be {@code null}
     * @param objectStorePort the port every candidate/target read goes through; must not be {@code
     *     null}
     * @param transitionService the port {@code fireAutomated} is called on; must not be {@code
     *     null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultTriggerEvaluator(
        OntologyPort ontologyPort,
        ObjectStorePort objectStorePort,
        TransitionService transitionService) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
        this.objectStorePort =
            Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.transitionService =
            Objects.requireNonNull(transitionService, "transitionService must not be null");
    }

    @Override
    public void onChange(Scope scope, OutboxEntry changeEvent) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(changeEvent, "changeEvent must not be null");

        Map<String, Object> payload = changeEvent.payload();
        ObjectId objectId = ObjectId.parse((String) payload.get("objectId"));
        String typeIri = (String) payload.get("typeIri");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        Optional<TypeDefinition> maybeChangedType = snapshot.type(typeIri);
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
        for (TypeDefinition candidateType : snapshot.types()) {
            Optional<StateMachineDefinition> maybeMachine = candidateType.stateMachine();
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
            fireFirstAvailableOnChange(scope, snapshot, candidateId);
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

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        Optional<TypeDefinition> maybeType = snapshot.type(object.type().iri());
        if (maybeType.isEmpty() || maybeType.get().stateMachine().isEmpty()) {
            return;
        }
        StateMachineDefinition machine = maybeType.get().stateMachine().get();
        String currentState = object.state().get();

        for (Transition transition : machine.transitions()) {
            if (transition.fromStateIri().equals(currentState)
                && transition.trigger() instanceof ExternalSignalTrigger signalTrigger
                && signalTrigger.signalName().equals(signalName)) {
                transitionService.fireAutomated(
                    scope, objectId, transition.name(), TriggerKind.EXTERNAL_SIGNAL);
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
    private void fireFirstAvailableOnChange(
        Scope scope, MetaModelSnapshot snapshot, ObjectId candidateId) {
        Optional<BusinessObject> found = objectStorePort.find(scope, candidateId);
        if (found.isEmpty() || found.get().state().isEmpty()) {
            return;
        }
        BusinessObject candidate = found.get();
        Optional<TypeDefinition> maybeType = snapshot.type(candidate.type().iri());
        if (maybeType.isEmpty() || maybeType.get().stateMachine().isEmpty()) {
            return;
        }
        StateMachineDefinition machine = maybeType.get().stateMachine().get();
        String currentState = candidate.state().get();

        for (Transition transition : machine.transitions()) {
            if (!(transition.trigger() instanceof OnChangeTrigger)) {
                continue;
            }
            if (!transition.fromStateIri().equals(currentState)) {
                continue;
            }
            Optional<BusinessObject> result =
                transitionService.fireAutomated(
                    scope, candidateId, transition.name(), TriggerKind.ON_CHANGE);
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
        raw.forEach(
            (iri, tagged) -> result.put(new PropertyRef(iri), PayloadValueCodec.fromPayload(tagged)));
        return result;
    }
}
