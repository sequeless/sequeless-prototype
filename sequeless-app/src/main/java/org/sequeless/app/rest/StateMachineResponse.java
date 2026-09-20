package org.sequeless.app.rest;

import java.util.List;
import org.sequeless.spi.meta.StateMachineDefinition;

/**
 * JSON rendering of a type's {@code sq:StateMachine} ({@link StateMachineDefinition}), nested in
 * {@link TypeDetailResponse}.
 *
 * <p>{@code states} arrives from {@link StateMachineDefinition#states()} already sorted by {@code
 * (displayOrder, iri)} (the Jena mapper's own contract, mirroring {@link
 * TypeDetailResponse#properties()}'s equivalent ordering guarantee) — {@link
 * TypeResponseMapper#toStateMachineResponse} must not re-sort it.
 *
 * @param iri the state machine's IRI
 * @param states every state this state machine defines, in display order
 * @param initialState the short name of the state a new object of the governed type starts in
 * @param transitions every transition this state machine defines
 */
public record StateMachineResponse(
        String iri,
        List<StateResponse> states,
        String initialState,
        List<TransitionSummaryResponse> transitions) {}
