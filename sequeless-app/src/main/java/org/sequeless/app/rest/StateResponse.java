package org.sequeless.app.rest;

/**
 * JSON rendering of one {@code sq:State} within a {@link StateMachineResponse}.
 *
 * @param iri the state's IRI
 * @param name the state's short name
 * @param label the state's display label
 * @param displayOrder the state's position among its state machine's states
 */
public record StateResponse(String iri, String name, String label, int displayOrder) {}
