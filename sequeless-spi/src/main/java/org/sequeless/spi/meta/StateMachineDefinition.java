package org.sequeless.spi.meta;

import java.util.List;
import java.util.Objects;

/**
 * A declarative {@code sq:StateMachine}: the states an object of {@link TypeDefinition#iri()} can
 * be in, the state it starts in when created, and the named transitions that move it between
 * states, exactly as {@code ex:ProjectLifecycle} moves an {@code ex:Project} through Draft →
 * Active → OnHold/Closed. The core interpreter (a later phase) resolves this into the set of
 * transitions available from an object's current {@link org.sequeless.spi.object.BusinessObject#state()},
 * evaluating each {@link Transition#guard()} through the {@code ExpressionPort}.
 *
 * <p>This record does not itself enforce state/transition coherence — for example, it does not
 * check that {@link #initialState()} is actually a member of {@link #states()}, or that {@link
 * Transition#fromStateIri()}/{@link Transition#toStateIri()} on every entry in {@link
 * #transitions()} reference a state IRI that appears in {@link #states()}. That coherence check is
 * the Jena mapper's job when parsing {@code sq:StateMachine} nodes, exactly as {@link RollupRule}
 * already disclaims function/property coherence for the rule it carries.
 *
 * @param iri the state machine's IRI; must not be blank
 * @param states every state this state machine defines ({@code sq:state}); must not be {@code
 *     null}; returned as an unmodifiable copy so callers cannot mutate this definition after
 *     construction
 * @param initialState the state a new object of the governed type starts in ({@code
 *     sq:initialState}); must not be {@code null}
 * @param transitions every transition this state machine defines ({@code sq:transition}); must not
 *     be {@code null}; returned as an unmodifiable copy so callers cannot mutate this definition
 *     after construction
 */
public record StateMachineDefinition(
    String iri, List<State> states, State initialState, List<Transition> transitions) {

    public StateMachineDefinition {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("StateMachineDefinition iri must not be blank");
        }
        Objects.requireNonNull(states, "states must not be null");
        states = List.copyOf(states);
        Objects.requireNonNull(initialState, "initialState must not be null");
        Objects.requireNonNull(transitions, "transitions must not be null");
        transitions = List.copyOf(transitions);
    }
}
