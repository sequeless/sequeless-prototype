package org.sequeless.core.statemachine;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether a single named {@link org.sequeless.spi.meta.Transition} is currently available from an
 * object's current state, as computed by {@link
 * StateMachineInterpreter#availableTransitions}: {@code available} is {@code true} whenever the
 * transition departs from the object's current state and either has no {@code
 * org.sequeless.spi.meta.Transition#guard()} or its guard evaluated to {@code true}.
 *
 * @param name the transition's name ({@link org.sequeless.spi.meta.Transition#name()})
 * @param available whether this transition can be fired right now
 * @param reason why the transition is unavailable — either its {@code
 *     org.sequeless.spi.meta.Transition#guardMessage()} or the generic fallback text — present only
 *     when {@code available} is {@code false}; must not be {@code null} (the {@link Optional}
 *     wrapper itself, not just its contents)
 */
public record TransitionAvailability(String name, boolean available, Optional<String> reason) {

    public TransitionAvailability {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
