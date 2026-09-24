package org.sequeless.app.rest;

import java.util.List;

/**
 * JSON rendering of one {@code sq:Transition} within a {@link StateMachineResponse}: the
 * type-level descriptor of a transition, not its instance-specific availability (see {@link
 * TransitionResponse} for that — {@code hasGuard} deliberately is not an evaluated {@code
 * available}, since availability is per-object, not per-type).
 *
 * <p>{@code trigger}/{@code after}/{@code signalName}/{@code watch} render {@code
 * org.sequeless.spi.meta.Transition#trigger()} (phase 6). {@code
 * org.sequeless.app.rest.TypeResponseMapper#toStateMachineResponse} currently only ever maps
 * transitions whose {@code trigger().kind()} is {@code USER_ACTION} into a {@link
 * StateMachineResponse} — {@code GET /types/{nameOrIri}}'s state-machine descriptor lists only
 * user-actionable transitions, mirroring {@code GET .../transitions}' own {@code USER_ACTION}-only
 * filtering — so {@code after}/{@code signalName}/{@code watch} are always {@code null}/empty on
 * that specific response today. These fields exist for wire-format completeness against {@code
 * docs/architecture/sq-vocabulary.md}'s documented trigger shape, for a future caller that renders
 * every transition regardless of trigger kind.
 *
 * @param name the transition's name
 * @param from the short name of the state this transition departs from
 * @param to the short name of the state this transition arrives at
 * @param hasGuard whether this transition declares a guard ({@code sq:guard}); does not reflect
 *     whether the guard currently passes for any particular object
 * @param trigger the discriminator naming which of the four trigger kinds this transition uses
 *     ({@code org.sequeless.spi.meta.TriggerKind#name()}: {@code "USER_ACTION"}, {@code
 *     "ON_CHANGE"}, {@code "TIMER"}, or {@code "EXTERNAL_SIGNAL"})
 * @param after the ISO-8601 duration a {@code TIMER}-triggered transition waits ({@code sq:after}),
 *     or {@code null} for every other trigger kind
 * @param signalName the signal name an {@code EXTERNAL_SIGNAL}-triggered transition matches
 *     ({@code sq:signalName}), or {@code null} for every other trigger kind
 * @param watch the short names of the forward relationship properties an {@code ON_CHANGE}-triggered
 *     transition watches ({@code sq:watch}); empty (not {@code null}) for every other trigger kind,
 *     and also empty for a "watch self only" {@code ON_CHANGE} trigger with no {@code sq:watch}
 *     values
 */
public record TransitionSummaryResponse(
        String name,
        String from,
        String to,
        boolean hasGuard,
        String trigger,
        String after,
        String signalName,
        List<String> watch) {}
