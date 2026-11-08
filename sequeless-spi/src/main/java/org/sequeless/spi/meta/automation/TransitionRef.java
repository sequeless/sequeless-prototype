package org.sequeless.spi.meta.automation;

/**
 * Identifies one transition as the automation half files its actions under: the state machine it
 * belongs to, the state the object leaves, and the transition's name. The three parts together are
 * the key, because a request fires the first transition whose name and from-state both match.
 *
 * @param stateMachineIri the IRI of the state machine the transition belongs to; must not be blank
 * @param fromStateIri the IRI of the state the transition departs from; must not be blank
 * @param transitionName the transition's name; must not be blank
 */
public record TransitionRef(String stateMachineIri, String fromStateIri, String transitionName) {

    public TransitionRef {
        if (stateMachineIri == null || stateMachineIri.isBlank()) {
            throw new IllegalArgumentException("TransitionRef stateMachineIri must not be blank");
        }
        if (fromStateIri == null || fromStateIri.isBlank()) {
            throw new IllegalArgumentException("TransitionRef fromStateIri must not be blank");
        }
        if (transitionName == null || transitionName.isBlank()) {
            throw new IllegalArgumentException("TransitionRef transitionName must not be blank");
        }
    }
}
