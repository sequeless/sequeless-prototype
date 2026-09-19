package org.sequeless.core.api;

import org.sequeless.core.AuthorizationException;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectNotFoundException;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.StaleObjectException;

/**
 * Moves an existing business object through its type's {@code sq:StateMachine} by firing one of
 * its named transitions, parallel to {@link BusinessObjectService}'s BREAD operations but scoped
 * to state-machine-governed objects only.
 *
 * <p>{@code type} parameters accept either a type's short name or its full IRI, resolved against
 * the current {@link org.sequeless.spi.meta.MetaModelSnapshot}, the same way {@link
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
     * other property is left exactly as it was), a {@code TransitionFired} outbox event, and one
     * {@code ActionRequest} outbox event per action the transition defines, in order. No action is
     * ever applied inline to the transitioning object itself; every action, including a {@code
     * SetProperty} that targets the same object, is dispatched uniformly as an async outbox row.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param type the type's short name or full IRI; must not be {@code null}
     * @param id the id of the object to transition; must not be {@code null}
     * @param transitionName the name of the transition to fire ({@link
     *     org.sequeless.spi.meta.Transition#name()}); must not be {@code null}
     * @param expectedVersion the version the caller expects the stored object to currently be at
     * @return the object after the transition, at version {@code expectedVersion + 1}, with {@code
     *     state} moved to the transition's target state
     * @throws NullPointerException if any reference-typed argument is {@code null}
     * @throws TypeNotFoundException if no type in the current snapshot matches {@code type}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws ObjectNotFoundException if no matching, non-deleted object of {@code type} or a
     *     subtype of it exists with {@code id}
     * @throws TransitionNotAvailableException if the resolved type has no state machine, {@code
     *     transitionName} does not name a transition departing from the object's current state, or
     *     that transition's guard currently evaluates to {@code false}
     * @throws StaleObjectException if the stored object is not currently at {@code expectedVersion}
     */
    BusinessObject fire(
        Scope scope, String type, ObjectId id, String transitionName, long expectedVersion);
}
