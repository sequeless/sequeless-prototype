package org.sequeless.app.rest;

import org.sequeless.core.statemachine.TransitionAvailability;

/**
 * JSON response body for one element of {@code GET /objects/{type}/{id}/transitions}: whether a
 * single named transition is currently available from the object's current state, mirroring
 * {@link TransitionAvailability} exactly.
 *
 * @param name the transition's name
 * @param available whether this transition can be fired right now
 * @param reason why the transition is unavailable, or {@code null} when {@code available} is
 *     {@code true}
 */
public record TransitionResponse(String name, boolean available, String reason) {

    /**
     * @param availability the core's computed availability; must not be {@code null}
     * @return the {@link TransitionResponse} rendering of {@code availability}
     */
    static TransitionResponse from(TransitionAvailability availability) {
        return new TransitionResponse(
                availability.name(), availability.available(), availability.reason().orElse(null));
    }
}
