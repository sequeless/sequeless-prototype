package org.sequeless.app.rest;

/**
 * JSON rendering of one {@code sq:Transition} within a {@link StateMachineResponse}: the
 * type-level descriptor of a transition, not its instance-specific availability (see {@link
 * TransitionResponse} for that — {@code hasGuard} deliberately is not an evaluated {@code
 * available}, since availability is per-object, not per-type).
 *
 * @param name the transition's name
 * @param from the short name of the state this transition departs from
 * @param to the short name of the state this transition arrives at
 * @param hasGuard whether this transition declares a guard ({@code sq:guard}); does not reflect
 *     whether the guard currently passes for any particular object
 */
public record TransitionSummaryResponse(String name, String from, String to, boolean hasGuard) {}
