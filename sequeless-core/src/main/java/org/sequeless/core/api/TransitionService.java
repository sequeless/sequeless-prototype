package org.sequeless.core.api;

import java.util.Map;
import java.util.Optional;
import org.sequeless.core.AuthorizationException;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.ontology.behaviour.api.ExternalSignalTrigger;
import org.sequeless.ontology.behaviour.api.Transition;
import org.sequeless.ontology.behaviour.api.TriggerKind;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.StaleObjectException;

/**
 * Moves an existing business object through its type's {@code sq:StateMachine} by firing one of
 * its named transitions, parallel to {@link BusinessObjectService}'s BREAD operations but scoped
 * to state-machine-governed objects only.
 *
 * <p>{@code type} parameters accept either a type's short name or its full IRI, resolved against
 * the current {@link org.sequeless.ontology.schema.api.Schema}, the same way {@link
 * BusinessObjectService}'s own {@code type} parameters do.
 *
 * @see org.sequeless.core.usecase.DefaultTransitionService
 */
public interface TransitionService {

    /**
     * Fires the named transition on an existing object of {@code type} (or a subtype of it).
     *
     * <p>Authorizes {@link Operation#TRANSITION} against the resolved type's IRI, reads the
     * existing object, validates that {@code transitionName} names a transition defined on the
     * resolved type's state machine that departs from the object's current state and whose guard
     * (if any) currently passes — via {@link
     * org.sequeless.core.statemachine.StateMachineInterpreter#availableTransitions} — then, in one
     * {@link org.sequeless.spi.object.ChangeSet}, commits an {@link
     * org.sequeless.spi.object.Update} moving {@code state} to the transition's target state (every
     * other property is left exactly as it was) and one {@code TransitionFired} outbox event that
     * carries the data of the actions the transition defines. No action is ever applied inline to
     * the transitioning object itself; every action, including a {@code SetProperty} that targets
     * the same object, is applied asynchronously by the automation adapters from that outbox event.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param id the id of the object to transition; must not be {@code null}
     * @param transitionName the name of the transition to fire ({@link
     *     org.sequeless.ontology.behaviour.api.Transition#name()}); must not be {@code null}
     * @param expectedVersion the version the caller expects the stored object to currently be at
     * @return the object after the transition, at version {@code expectedVersion + 1}, with {@code
     *     state} moved to the transition's target state
     * @throws NullPointerException if any reference-typed argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     * @throws TransitionNotAvailableException if the resolved type has no state machine, {@code
     *     transitionName} does not name a transition departing from the object's current state, or
     *     that transition's guard currently evaluates to {@code false}
     * @throws StaleObjectException if the stored object is not currently at {@code expectedVersion}
     */
    BusinessObject fire(Scope scope, String type, ObjectId id, String transitionName, long expectedVersion);

    /**
     * Fires {@code transitionName} on {@code id} in response to an automation event — a change to
     * another object or an elapsed timer — rather than a user's own REST request. Unlike {@link
     * #fire}, every mismatch is a silent {@link Optional#empty()}, never an
     * exception: the event that prompted this call is, by construction, only a hint that {@code
     * transitionName} might now be available, and it may already be stale by the time it is acted
     * on. No {@link Operation#TRANSITION} authorization check is performed — there is no external
     * caller to authorize, only the object's own current, valid state.
     *
     * @param scope the tenant and principal context the commit (if any) is recorded under; must not
     *     be {@code null}
     * @param id the id of the object to transition; must not be {@code null}
     * @param transitionName the name of the transition to fire ({@link Transition#name()}); must not
     *     be {@code null}
     * @param expected the {@link TriggerKind} the resolved transition's {@link Transition#trigger()}
     *     must match; must not be {@code null}
     * @return the object after the transition, or {@link Optional#empty()} if {@code id} no longer
     *     resolves to a live object of a type with a state machine, {@code transitionName} does not
     *     name a transition currently departing the object's state, that transition's guard
     *     evaluates {@code false}, or its trigger kind does not equal {@code expected}
     * @throws NullPointerException if any argument is {@code null}
     */
    Optional<BusinessObject> fireAutomated(Scope scope, ObjectId id, String transitionName, TriggerKind expected);

    /**
     * Fires the transition that {@code signalName} names on {@code id}, for {@code POST
     * /objects/{type}/{id}/signals/{name}}, synchronously: the transition is applied before this
     * method returns. The transition is the first, in transition order, that departs the object's
     * current state with an {@link ExternalSignalTrigger} of that name. Its guard is evaluated the
     * way {@link #fire} evaluates one, and it is committed in one {@link
     * org.sequeless.spi.object.ChangeSet}, the same way a user {@link #fire} commits: an {@link
     * org.sequeless.spi.object.Update} and one {@code TransitionFired} outbox event, whose actions
     * and timers the automation adapters apply afterwards.
     *
     * <p>Authorizes {@link Operation#TRANSITION} against the resolved type's IRI, reads the existing
     * object, and validates that {@code signalName} matches an {@link
     * ExternalSignalTrigger#signalName()} declared by <em>some</em> transition anywhere on the
     * resolved type's state machine. That check is type-wide, not scoped to the object's current
     * state: a name the type declares, but that no transition departs the current state with, is
     * a {@link TransitionNotAvailableException}, not an unknown name, because the client named a
     * real signal.
     *
     * <p>On a {@link TransitionNotAvailableException}, {@code transitionName} is the signal name when
     * no transition from the current state carries the signal name (including a state-less object).
     * When the matched transition's guard fails, {@code transitionName} is that transition's name and
     * the reason is the guard's message, or a default naming the transition when the guard gives none.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param id the id of the object the signal targets; must not be {@code null}
     * @param signalName the signal name to fire ({@link ExternalSignalTrigger#signalName()}); must
     *     not be {@code null}
     * @param body the request body; must not be {@code null}; validated but not recorded, and not
     *     bound into any guard's {@code ExpressionContext}
     * @throws NullPointerException if any argument is {@code null}
     * @throws TypeNotFoundException if no type in the current schema matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     * @throws UnknownSignalException if no transition on the resolved type's state machine declares
     *     {@code signalName}
     * @throws TransitionNotAvailableException if no transition departing the object's current state
     *     has a matching signal name (a state-less object has none), or the first such transition's
     *     guard currently evaluates to {@code false}; the guard's reason is carried in the exception
     * @throws StaleObjectException if the stored object changed between the read and the commit
     */
    void signal(Scope scope, String type, ObjectId id, String signalName, Map<String, Object> body);
}
