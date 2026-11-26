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
import org.sequeless.core.api.UnknownSignalException;
import org.sequeless.core.ontology.SchemaAndBehaviour;
import org.sequeless.core.statemachine.PayloadValueCodec;
import org.sequeless.core.statemachine.StateMachineInterpreter;
import org.sequeless.core.statemachine.TransitionAvailability;
import org.sequeless.core.validation.TypeHierarchy;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.ExternalSignalTrigger;
import org.sequeless.ontology.behaviour.api.StateMachineDefinition;
import org.sequeless.ontology.behaviour.api.Transition;
import org.sequeless.ontology.behaviour.api.TriggerKind;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.store.api.AccessDecision;
import org.sequeless.store.api.Operation;
import org.sequeless.store.spi.AuthorizationPort;
import org.sequeless.store.spi.ObjectStorePort;

/**
 * Reference implementation of {@link TransitionService}.
 *
 * <p>Mirrors {@link DefaultBusinessObjectService#edit}'s structure closely — resolve the type,
 * authorize, read the existing object, build a candidate, commit a {@link ChangeSet} — but never
 * touches {@code ValidationPort}, {@code QueryPort}, or {@code DerivationPlanner}: a transition only
 * ever moves {@code state} and never re-coerces or re-validates the object's properties, since they
 * are left completely unchanged by every transition this class fires. Guard evaluation and "is this
 * transition available from the current state" are delegated entirely to {@link
 * StateMachineInterpreter#availableTransitions}, never re-implemented here, so the write path and a
 * later REST read path can never disagree about which transitions are available.
 *
 * <p>A fire writes one {@code TransitionFired} outbox row that carries the actions' data: the frozen
 * {@code self} snapshot, {@code typeIri}, the machine IRI and the Ontology version. The four {@link
 * Action} kinds are applied by the automation adapters from that row, never inline to the object
 * being transitioned, even a {@link SetPropertyAction} that targets that same object; see {@code
 * plan.md} §4's interpretation note.
 */
public final class DefaultTransitionService implements TransitionService {

    private final SchemaSource schemaSource;
    private final BehaviourSource behaviourSource;
    private final AutomationSource automationSource;
    private final ObjectStorePort objectStorePort;
    private final AuthorizationPort authorizationPort;
    private final ExpressionPort expressionPort;
    private final Clock clock;

    /**
     * @param schemaSource the port the schema half is read from; must not be {@code null}
     * @param behaviourSource the port the behaviour half is read from; must not be {@code null}
     * @param automationSource the port the automation half, the transition actions, is read from;
     *     must not be {@code null}
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
            SchemaSource schemaSource,
            BehaviourSource behaviourSource,
            AutomationSource automationSource,
            ObjectStorePort objectStorePort,
            AuthorizationPort authorizationPort,
            ExpressionPort expressionPort,
            Clock clock) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.behaviourSource = Objects.requireNonNull(behaviourSource, "behaviourSource must not be null");
        this.automationSource = Objects.requireNonNull(automationSource, "automationSource must not be null");
        this.objectStorePort = Objects.requireNonNull(objectStorePort, "objectStorePort must not be null");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
        this.expressionPort = Objects.requireNonNull(expressionPort, "expressionPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public BusinessObject fire(Scope scope, String type, ObjectId id, String transitionName, long expectedVersion) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(transitionName, "transitionName must not be null");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        Behaviour behaviour = halves.behaviour();
        TypeDefinition resolved = resolveType(schema, type);
        authorize(scope, Operation.TRANSITION, resolved.iri());

        BusinessObject existing = objectStorePort.find(scope, id).orElseThrow(() -> new ObjectNotFoundException(id));
        requireMatchingType(schema, existing, resolved, id);

        StateMachineDefinition machine = behaviour
                .stateMachine(resolved.iri())
                .orElseThrow(() -> new TransitionNotAvailableException(
                        transitionName, "Type '" + resolved.iri() + "' has no state machine"));

        StateMachineInterpreter interpreter = new StateMachineInterpreter(expressionPort);
        List<TransitionAvailability> available = interpreter.availableTransitions(scope, behaviour, existing);
        Optional<TransitionAvailability> match =
                available.stream().filter(a -> a.name().equals(transitionName)).findFirst();
        if (match.isEmpty()) {
            throw new TransitionNotAvailableException(transitionName, notAvailableMessage(transitionName));
        }
        if (!match.get().available()) {
            throw new TransitionNotAvailableException(
                    transitionName, match.get().reason().orElseThrow());
        }

        Transition transition = machine.transitions().stream()
                .filter(t -> t.name().equals(transitionName)
                        && existing.state().isPresent()
                        && Objects.equals(t.fromStateIri(), existing.state().get()))
                .findFirst()
                .orElseThrow(
                        () -> new TransitionNotAvailableException(transitionName, notAvailableMessage(transitionName)));

        if (transition.trigger().kind() != TriggerKind.USER_ACTION) {
            throw new TransitionNotAvailableException(
                    transitionName,
                    "Transition '" + transitionName + "' is not user-triggerable; its trigger is "
                            + transition.trigger().kind());
        }

        return commitTransition(scope, halves, resolved, existing, transition, expectedVersion, now());
    }

    /**
     * The automation counterpart to {@link #fire}, called by {@code DefaultTriggerEvaluator} (an
     * inbound port implemented in this same package, but not depended on here to avoid a cycle) once
     * a change event or an elapsed timer suggests {@code transitionName} might now be available on
     * {@code id}. Unlike {@link #fire}, a mismatch of any kind is a silent
     * no-op, never an exception: the event that prompted this call is, by construction, a hint that
     * may already be stale by the time it is acted on — the object may have been deleted, moved to a
     * different state by a race, or have had this exact transition disabled by an ontology edit —
     * and none of that is a caller error worth surfacing as a thrown exception the way a user's own
     * misdirected REST call is in {@link #fire}.
     *
     * <p>Every check {@link #fire} performs via {@link TransitionNotAvailableException} is performed
     * here too, plus one more: {@code transition.trigger().kind()} must equal {@code expected},
     * checked via the {@link TriggerKind} discriminator rather than an {@code instanceof} match on
     * {@link org.sequeless.ontology.behaviour.api.TriggerSpec}, exactly so a caller with only a {@link
     * TriggerKind} in hand (as every inbound automation port method has, from the outbox entry it is
     * dispatching) never needs to reconstruct or guess at the full trigger payload just to compare
     * kinds.
     *
     * @param scope the tenant and principal context the commit is recorded under; must not be {@code
     *     null}. Automation never authorizes against {@link
     *     org.sequeless.store.api.Operation#TRANSITION} the way {@link #fire} does — there is no
     *     external caller to authorize, only the object's own current, valid state
     * @param id the id of the object to transition; must not be {@code null}
     * @param transitionName the name of the transition to fire; must not be {@code null}
     * @param expected the {@link TriggerKind} the resolved transition's {@link
     *     Transition#trigger()} must match; must not be {@code null}
     * @return the object after the transition, or {@link Optional#empty()} if {@code id} no longer
     *     resolves to a live object, {@code transitionName} does not name a transition currently
     *     departing the object's state, that transition's guard evaluates {@code false}, or its
     *     trigger kind does not equal {@code expected}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalStateException if the automation half is at a different ontology version from
     *     the schema and behaviour halves; nothing is committed
     */
    @Override
    public Optional<BusinessObject> fireAutomated(
            Scope scope, ObjectId id, String transitionName, TriggerKind expected) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(transitionName, "transitionName must not be null");
        Objects.requireNonNull(expected, "expected must not be null");

        Optional<BusinessObject> maybeExisting = objectStorePort.find(scope, id);
        if (maybeExisting.isEmpty()) {
            return Optional.empty();
        }
        BusinessObject existing = maybeExisting.get();

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        Behaviour behaviour = halves.behaviour();
        Optional<TypeDefinition> maybeResolved = schema.type(existing.type().iri());
        if (maybeResolved.isEmpty()) {
            return Optional.empty();
        }
        TypeDefinition resolved = maybeResolved.get();
        Optional<StateMachineDefinition> maybeMachine = behaviour.stateMachine(resolved.iri());
        if (maybeMachine.isEmpty()) {
            return Optional.empty();
        }
        StateMachineDefinition machine = maybeMachine.get();

        StateMachineInterpreter interpreter = new StateMachineInterpreter(expressionPort);
        List<TransitionAvailability> available = interpreter.availableTransitions(scope, behaviour, existing);
        Optional<TransitionAvailability> match =
                available.stream().filter(a -> a.name().equals(transitionName)).findFirst();
        if (match.isEmpty() || !match.get().available()) {
            return Optional.empty();
        }

        Optional<Transition> maybeTransition = machine.transitions().stream()
                .filter(t -> t.name().equals(transitionName)
                        && existing.state().isPresent()
                        && Objects.equals(t.fromStateIri(), existing.state().get()))
                .findFirst();
        if (maybeTransition.isEmpty()) {
            return Optional.empty();
        }
        Transition transition = maybeTransition.get();
        if (transition.trigger().kind() != expected) {
            return Optional.empty();
        }

        return Optional.of(commitTransition(scope, halves, resolved, existing, transition, existing.version(), now()));
    }

    /**
     * Fires the transition that {@code signalName} names on {@code id}, synchronously, in the request.
     * The transition is the first, in the machine's transition order, that departs the object's
     * current state with an {@link ExternalSignalTrigger} of that name. Its guard is evaluated through
     * {@link StateMachineInterpreter#availableTransitions}, as {@link #fire} evaluates one, and the
     * transition is committed by {@link #commitTransition} against {@code existing.version()}: one
     * {@link ChangeSet} carrying the object's {@link Update} and one {@code TransitionFired} row, so
     * the transition's actions and timers come from that row exactly as they do for a user fire.
     *
     * <p>A name no transition declares on the object's type is an {@link UnknownSignalException}. A
     * declared name with no match from the current state (a state-less object included), or whose
     * first matching transition's guard evaluates {@code false}, is a {@link
     * TransitionNotAvailableException}; the guard's reason is carried in the latter case. This method
     * never calls {@link #fireAutomated}. The request {@code body} is validated for null but not
     * recorded.
     */
    @Override
    public void signal(Scope scope, String type, ObjectId id, String signalName, Map<String, Object> body) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(signalName, "signalName must not be null");
        Objects.requireNonNull(body, "body must not be null");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        Behaviour behaviour = halves.behaviour();
        TypeDefinition resolved = resolveType(schema, type);
        authorize(scope, Operation.TRANSITION, resolved.iri());

        BusinessObject existing = objectStorePort.find(scope, id).orElseThrow(() -> new ObjectNotFoundException(id));
        requireMatchingType(schema, existing, resolved, id);

        StateMachineDefinition machine = behaviour
                .stateMachine(resolved.iri())
                .orElseThrow(() -> new UnknownSignalException(resolved.iri(), signalName));
        boolean declared = machine.transitions().stream()
                .anyMatch(t -> t.trigger() instanceof ExternalSignalTrigger signal
                        && signal.signalName().equals(signalName));
        if (!declared) {
            throw new UnknownSignalException(resolved.iri(), signalName);
        }

        Transition transition = signalTransition(machine, existing, signalName)
                .orElseThrow(
                        () -> new TransitionNotAvailableException(signalName, signalNotAvailableMessage(signalName)));
        StateMachineInterpreter interpreter = new StateMachineInterpreter(expressionPort);
        TransitionAvailability availability = interpreter.availableTransitions(scope, behaviour, existing).stream()
                .filter(a -> a.name().equals(transition.name()))
                .findFirst()
                .orElseThrow(
                        () -> new TransitionNotAvailableException(signalName, signalNotAvailableMessage(signalName)));
        if (!availability.available()) {
            throw new TransitionNotAvailableException(
                    transition.name(), availability.reason().orElseThrow());
        }

        commitTransition(scope, halves, resolved, existing, transition, existing.version(), now());
    }

    /**
     * The commit path shared by {@link #fire}, {@link #fireAutomated} and {@link #signal}: builds the
     * moved-state {@code candidate} and the one {@code TransitionFired} entry that carries the actions'
     * data, and commits both as one {@link ChangeSet} — so the callers can never disagree about what a
     * successful transition commits.
     *
     * <p>Reads {@link Automation} from its {@link AutomationSource} first. If its ontology version
     * differs from the version of {@code halves}, this throws {@link IllegalStateException} before
     * anything is committed.
     *
     * @throws IllegalStateException if the automation half is at a different ontology version from
     *     {@code halves}
     */
    private BusinessObject commitTransition(
            Scope scope,
            SchemaAndBehaviour halves,
            TypeDefinition resolved,
            BusinessObject existing,
            Transition transition,
            long expectedVersion,
            Instant now) {
        Automation automation = automationSource.automation(scope.tenantId());
        if (!automation.ontologyVersion().equals(halves.schema().ontologyVersion())) {
            throw new IllegalStateException("Automation is at ontology version "
                    + automation.ontologyVersion().value() + " but schema and behaviour are at "
                    + halves.schema().ontologyVersion().value() + "; the ontology changed during the transition");
        }
        StateMachineDefinition machine =
                halves.behaviour().stateMachine(resolved.iri()).orElseThrow();

        String by = scope.principal().id();
        Audit audit = new Audit(existing.audit().createdAt(), existing.audit().createdBy(), now, by);
        BusinessObject candidate = new BusinessObject(
                existing.id(),
                existing.type(),
                existing.tenant(),
                expectedVersion + 1,
                Optional.of(transition.toStateIri()),
                existing.properties(),
                audit,
                false);

        int ontologyVersion = halves.schema().ontologyVersion().value();
        List<OutboxEntry> outbox = new ArrayList<>();
        outbox.add(transitionFiredEntry(scope, existing, resolved, machine, transition, ontologyVersion, now));

        ChangeSet changeSet = new ChangeSet(List.<Mutation>of(new Update(candidate, expectedVersion)), outbox);
        CommitResult result = objectStorePort.commit(scope, changeSet);
        return result.objects().get(0);
    }

    private static String notAvailableMessage(String transitionName) {
        return "No transition named '" + transitionName + "' is available from the object's current state";
    }

    private static String signalNotAvailableMessage(String signalName) {
        return "No transition for signal '" + signalName + "' is available from the object's current state";
    }

    /**
     * The first transition, in the machine's transition order, that departs {@code object}'s current
     * state with an {@link ExternalSignalTrigger} named {@code signalName}. Empty for a state-less
     * object, which has no current state to depart.
     */
    private static Optional<Transition> signalTransition(
            StateMachineDefinition machine, BusinessObject object, String signalName) {
        if (object.state().isEmpty()) {
            return Optional.empty();
        }
        String currentState = object.state().get();
        return machine.transitions().stream()
                .filter(t -> Objects.equals(t.fromStateIri(), currentState)
                        && t.trigger() instanceof ExternalSignalTrigger signal
                        && signal.signalName().equals(signalName))
                .findFirst();
    }

    private OutboxEntry transitionFiredEntry(
            Scope scope,
            BusinessObject existing,
            TypeDefinition resolved,
            StateMachineDefinition machine,
            Transition transition,
            int ontologyVersion,
            Instant now) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", existing.id().value().toString());
        payload.put("tenantId", scope.tenantId().value());
        payload.put("principalId", scope.principal().id());
        payload.put("transitionName", transition.name());
        payload.put("fromState", transition.fromStateIri());
        payload.put("toState", transition.toStateIri());
        payload.put("ontologyVersion", ontologyVersion);
        payload.put("self", selfPayload(existing));
        payload.put("typeIri", resolved.iri());
        payload.put("machineIri", machine.iri());
        return new OutboxEntry(UUID.randomUUID(), OutboxEntry.KIND_TRANSITION_FIRED, payload, now);
    }

    private static Map<String, Object> selfPayload(BusinessObject object) {
        Map<String, Object> self = new HashMap<>();
        for (Map.Entry<PropertyRef, Value> entry : object.properties().entrySet()) {
            self.put(shortName(entry.getKey().iri()), PayloadValueCodec.toPayload(entry.getValue()));
        }
        return self;
    }

    private TypeDefinition resolveType(Schema schema, String nameOrIri) {
        return schema.typeByName(nameOrIri)
                .or(() -> schema.type(nameOrIri))
                .orElseThrow(() -> new TypeNotFoundException(nameOrIri));
    }

    private void requireMatchingType(Schema schema, BusinessObject object, TypeDefinition resolved, ObjectId id) {
        if (!TypeHierarchy.isSubtypeOf(schema, object.type().iri(), resolved.iri())) {
            throw new ObjectNotFoundException(id);
        }
    }

    /**
     * Duplicated from {@code DefaultBusinessObjectService}'s identically-named private helper rather
     * than shared: this codebase's established convention for this specific short pure function (the
     * substring after an IRI's last {@code #}, else after its last {@code /}, else the whole IRI) is
     * to duplicate it at each use site rather than introduce a shared dependency for one line of
     * logic.
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
