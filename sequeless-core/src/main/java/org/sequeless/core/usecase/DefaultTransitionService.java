package org.sequeless.core.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.TransitionNotAvailableException;
import org.sequeless.core.api.TransitionService;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.core.statemachine.StateMachineInterpreter;
import org.sequeless.core.statemachine.TransitionAvailability;
import org.sequeless.core.validation.TypeHierarchy;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.meta.Action;
import org.sequeless.spi.meta.CreateObjectAction;
import org.sequeless.spi.meta.LogAction;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PropertyAssignment;
import org.sequeless.spi.meta.SetPropertyAction;
import org.sequeless.spi.meta.StateMachineDefinition;
import org.sequeless.spi.meta.Transition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.meta.WebhookAction;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectNotFoundException;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyPort;

/**
 * Reference implementation of {@link TransitionService}.
 *
 * <p>Mirrors {@link DefaultBusinessObjectService#edit}'s structure closely — resolve the type,
 * authorize, read the existing object, build a candidate, commit a {@link ChangeSet} — but never
 * touches {@code ValidationPort}, {@code QueryPort}, or {@code DerivationPlanner}: a transition
 * only ever moves {@code state} and never re-coerces or re-validates the object's properties, since
 * they are left completely unchanged by every transition this class fires. Guard evaluation and
 * "is this transition available from the current state" are delegated entirely to {@link
 * StateMachineInterpreter#availableTransitions}, never re-implemented here, so the write path and
 * a later REST read path can never disagree about which transitions are available.
 *
 * <p>All four {@link Action} kinds are dispatched uniformly as async {@code ActionRequest} outbox
 * rows — never applied inline to the object being transitioned, even a {@link SetPropertyAction}
 * that targets that same object — because the design treats every action as outbox-dispatched
 * without exception; see {@code plan.md} §4's interpretation note.
 */
public final class DefaultTransitionService implements TransitionService {

    private final OntologyPort ontologyPort;
    private final ObjectStorePort objectStorePort;
    private final AuthorizationPort authorizationPort;
    private final ExpressionPort expressionPort;
    private final Clock clock;

    /**
     * @param ontologyPort the port consulted for the type system; must not be {@code null}
     * @param objectStorePort the port every read and write ultimately goes through; must not be
     *     {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @param expressionPort the port every transition guard is evaluated through, via {@link
     *     StateMachineInterpreter}; must not be {@code null}
     * @param clock the clock every {@link Instant} this class produces is drawn from; must not be
     *     {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultTransitionService(
        OntologyPort ontologyPort,
        ObjectStorePort objectStorePort,
        AuthorizationPort authorizationPort,
        ExpressionPort expressionPort,
        Clock clock) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
        this.objectStorePort =
            Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.authorizationPort =
            Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
        this.expressionPort =
            Objects.requireNonNull(expressionPort, "expressionPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public BusinessObject fire(
        Scope scope, String type, ObjectId id, String transitionName, long expectedVersion) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(transitionName, "transitionName must not be null");

        MetaModelSnapshot snapshot = ontologyPort.snapshot(scope);
        TypeDefinition resolved = resolveType(snapshot, type);
        authorize(scope, Operation.TRANSITION, resolved.iri());

        BusinessObject existing =
            objectStorePort.find(scope, id).orElseThrow(() -> new ObjectNotFoundException(id));
        requireMatchingType(snapshot, existing, resolved, id);

        StateMachineDefinition machine =
            resolved
                .stateMachine()
                .orElseThrow(
                    () ->
                        new TransitionNotAvailableException(
                            transitionName,
                            "Type '" + resolved.iri() + "' has no state machine"));

        StateMachineInterpreter interpreter = new StateMachineInterpreter(expressionPort);
        List<TransitionAvailability> available =
            interpreter.availableTransitions(scope, snapshot, existing);
        Optional<TransitionAvailability> match =
            available.stream().filter(a -> a.name().equals(transitionName)).findFirst();
        if (match.isEmpty()) {
            throw new TransitionNotAvailableException(transitionName, notAvailableMessage(transitionName));
        }
        if (!match.get().available()) {
            throw new TransitionNotAvailableException(
                transitionName, match.get().reason().orElseThrow());
        }

        Transition transition =
            machine.transitions().stream()
                .filter(
                    t ->
                        t.name().equals(transitionName)
                            && existing.state().isPresent()
                            && Objects.equals(t.fromStateIri(), existing.state().get()))
                .findFirst()
                .orElseThrow(
                    () ->
                        new TransitionNotAvailableException(
                            transitionName, notAvailableMessage(transitionName)));

        Instant now = now();
        String by = scope.principal().id();
        Audit audit =
            new Audit(existing.audit().createdAt(), existing.audit().createdBy(), now, by);
        BusinessObject candidate =
            new BusinessObject(
                id,
                existing.type(),
                existing.tenant(),
                expectedVersion + 1,
                Optional.of(transition.toStateIri()),
                existing.properties(),
                audit,
                false);

        List<OutboxEntry> outbox = new ArrayList<>();
        outbox.add(transitionFiredEntry(scope, existing, transition, now));
        List<Action> actions = transition.actions();
        for (int i = 0; i < actions.size(); i++) {
            outbox.add(
                actionRequestEntry(scope, resolved, existing, transition, i, actions.get(i), now));
        }

        ChangeSet changeSet =
            new ChangeSet(List.<Mutation>of(new Update(candidate, expectedVersion)), outbox);
        CommitResult result = objectStorePort.commit(scope, changeSet);
        return result.objects().get(0);
    }

    private static String notAvailableMessage(String transitionName) {
        return "No transition named '" + transitionName
            + "' is available from the object's current state";
    }

    private OutboxEntry transitionFiredEntry(
        Scope scope, BusinessObject existing, Transition transition, Instant now) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", existing.id().value().toString());
        payload.put("tenantId", scope.tenantId().value());
        payload.put("principalId", scope.principal().id());
        payload.put("transitionName", transition.name());
        payload.put("fromState", transition.fromStateIri());
        payload.put("toState", transition.toStateIri());
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, now);
    }

    private OutboxEntry actionRequestEntry(
        Scope scope,
        TypeDefinition resolved,
        BusinessObject existing,
        Transition transition,
        int actionIndex,
        Action action,
        Instant now) {
        Map<String, Object> payload =
            actionRequestPayload(scope, resolved, existing, transition, actionIndex, action);
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_ACTION_REQUEST, payload, now);
    }

    /**
     * Builds one {@code ActionRequest} payload: the fields common to every action kind (including
     * {@code self}, a short-name-keyed snapshot of {@code existing}'s properties — see {@code
     * ExpressionContext#selfProperties()} for why short names, not IRIs, are used there), plus the
     * fields specific to {@code action}'s kind. Every optional field (a {@code SetProperty}'s
     * {@code value} vs {@code expression}, a {@code Webhook}'s {@code body}) is included only when
     * present — {@link OutboxEntry}'s constructor rejects a {@code null} anywhere in the payload,
     * so "one of two mutually exclusive keys" is expressed by omission, never a null placeholder.
     */
    private Map<String, Object> actionRequestPayload(
        Scope scope,
        TypeDefinition resolved,
        BusinessObject existing,
        Transition transition,
        int actionIndex,
        Action action) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", existing.id().value().toString());
        payload.put("tenantId", scope.tenantId().value());
        payload.put("principalId", scope.principal().id());
        payload.put("transitionName", transition.name());
        payload.put("actionIndex", actionIndex);
        payload.put("typeIri", resolved.iri());
        payload.put("state", transition.toStateIri());
        payload.put("self", selfPayload(existing));

        switch (action) {
            case SetPropertyAction setProperty -> {
                payload.put("actionKind", "SetProperty");
                payload.put("property", setProperty.propertyIri());
                setProperty.value().ifPresent(v -> payload.put("value", PayloadValueCodec.toPayload(v)));
                setProperty.expression().ifPresent(e -> payload.put("expression", e));
            }
            case CreateObjectAction createObject -> {
                payload.put("actionKind", "CreateObject");
                payload.put("createType", createObject.typeIri());
                payload.put("createProperties", createPropertiesPayload(createObject));
            }
            case WebhookAction webhook -> {
                payload.put("actionKind", "Webhook");
                payload.put("url", webhook.url());
                payload.put("method", webhook.method());
                webhook.body().ifPresent(b -> payload.put("body", b));
            }
            case LogAction log -> {
                payload.put("actionKind", "Log");
                payload.put("message", log.message());
            }
        }
        return payload;
    }

    private static Map<String, Object> createPropertiesPayload(CreateObjectAction action) {
        Map<String, Object> createProperties = new HashMap<>();
        for (PropertyAssignment assignment : action.properties()) {
            Map<String, Object> entry = new HashMap<>();
            assignment.value().ifPresent(v -> entry.put("value", PayloadValueCodec.toPayload(v)));
            assignment.expression().ifPresent(e -> entry.put("expression", e));
            createProperties.put(assignment.propertyIri(), entry);
        }
        return createProperties;
    }

    private static Map<String, Object> selfPayload(BusinessObject object) {
        Map<String, Object> self = new HashMap<>();
        for (Map.Entry<PropertyRef, Value> entry : object.properties().entrySet()) {
            self.put(shortName(entry.getKey().iri()), PayloadValueCodec.toPayload(entry.getValue()));
        }
        return self;
    }

    private TypeDefinition resolveType(MetaModelSnapshot snapshot, String nameOrIri) {
        return snapshot
            .typeByName(nameOrIri)
            .or(() -> snapshot.type(nameOrIri))
            .orElseThrow(() -> new TypeNotFoundException(nameOrIri));
    }

    private void requireMatchingType(
        MetaModelSnapshot snapshot, BusinessObject object, TypeDefinition resolved, ObjectId id) {
        if (!TypeHierarchy.isSubtypeOf(snapshot, object.type().iri(), resolved.iri())) {
            throw new ObjectNotFoundException(id);
        }
    }

    /**
     * Duplicated from {@code DefaultBusinessObjectService}'s identically-named private helper
     * rather than shared: this codebase's established convention for this specific short pure
     * function (the substring after an IRI's last {@code #}, else after its last {@code /}, else
     * the whole IRI) is to duplicate it at each use site rather than introduce a shared dependency
     * for one line of logic.
     */
    private static String shortName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        return slash >= 0 ? iri.substring(slash + 1) : iri;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private void authorize(Scope scope, Operation operation, String resource) {
        AccessDecision decision = authorizationPort.decide(scope, operation, resource);
        if (!decision.allowed()) {
            throw new AuthorizationException(decision);
        }
    }
}
