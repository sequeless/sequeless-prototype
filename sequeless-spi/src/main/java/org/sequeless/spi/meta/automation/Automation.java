package org.sequeless.spi.meta.automation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.sequeless.spi.meta.schema.OntologyVersion;

/**
 * The Automation half of one ontology activation: the actions that run when a transition fires.
 * Only transitions that have actions have an entry. The triggers that make a transition fire stay
 * on the behaviour half.
 *
 * @param ontologyVersion the version of the activation this half was read from; must not be
 *     {@code null}
 * @param transitionActions the actions of each transition, in order, keyed by {@link TransitionRef};
 *     must not be {@code null}; returned as an unmodifiable copy, with every action list copied too,
 *     so callers cannot mutate this automation after construction
 */
public record Automation(OntologyVersion ontologyVersion, Map<TransitionRef, List<Action>> transitionActions) {

    public Automation {
        Objects.requireNonNull(ontologyVersion, "ontologyVersion must not be null");
        Objects.requireNonNull(transitionActions, "transitionActions must not be null");
        transitionActions = transitionActions.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    /**
     * @param transition the transition whose actions to return; must not be {@code null}
     * @return the actions that run when {@code transition} fires, in order; an empty list when that
     *     transition has none
     * @throws NullPointerException if {@code transition} is {@code null}
     */
    public List<Action> actions(TransitionRef transition) {
        Objects.requireNonNull(transition, "transition must not be null");
        return transitionActions.getOrDefault(transition, List.of());
    }
}
