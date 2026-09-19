package org.sequeless.spi.meta;

/**
 * A single state within a {@link StateMachineDefinition} ({@code sq:State}), exactly as {@code
 * ex:Draft}/{@code ex:Active}/{@code ex:OnHold}/{@code ex:Closed} are the states of {@code
 * ex:ProjectLifecycle}. Individuals reuse the existing {@code sq:label}/{@code sq:displayOrder}
 * annotation terms for display, the same way {@link PropertyDefinition} does.
 *
 * @param iri the state's IRI; must not be blank
 * @param label the state's display label ({@code sq:label}); must not be blank
 * @param displayOrder the state's position among its state machine's states ({@code
 *     sq:displayOrder})
 */
public record State(String iri, String label, int displayOrder) {

    public State {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("State iri must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("State label must not be blank");
        }
    }
}
