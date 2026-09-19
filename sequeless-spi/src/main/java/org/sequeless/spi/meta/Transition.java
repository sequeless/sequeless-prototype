package org.sequeless.spi.meta;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single named transition within a {@link StateMachineDefinition} ({@code sq:Transition}),
 * exactly as {@code activate} moves an {@code ex:Project} from {@code ex:Draft} to {@code
 * ex:Active}. The core interpreter evaluates {@link #guard()} (when present) through the {@code
 * ExpressionPort} to decide whether this transition is available from an object's current state,
 * and — once fired — dispatches {@link #actions()} as outbox-recorded {@code ActionRequest}s, in
 * order.
 *
 * <p>This record does not itself enforce that {@link #fromStateIri()}/{@link #toStateIri()}
 * reference states that actually exist in the owning {@link StateMachineDefinition#states()}; per
 * that record's own disclaimer, that coherence check belongs to the Jena mapper.
 *
 * @param name the transition's id, used as both the REST path segment and the trigger name ({@code
 *     sq:name}); must not be blank
 * @param fromStateIri the IRI of the state this transition departs from ({@code sq:from}); must not
 *     be blank
 * @param toStateIri the IRI of the state this transition arrives at ({@code sq:to}); must not be
 *     blank
 * @param guard optional JEXL source evaluated against the {@code ExpressionContext} to decide
 *     whether this transition is currently available ({@code sq:guard}); absent means always
 *     available; must not be {@code null} (the {@link Optional} wrapper itself, not just its
 *     contents)
 * @param guardMessage optional reason text shown when {@link #guard()} evaluates false ({@code
 *     sq:guardMessage}); must not be {@code null} (the {@link Optional} wrapper itself, not just
 *     its contents)
 * @param actions the actions dispatched, in order, when this transition fires ({@code sq:action});
 *     must not be {@code null}; returned as an unmodifiable copy so callers cannot mutate this
 *     transition after construction
 */
public record Transition(
    String name,
    String fromStateIri,
    String toStateIri,
    Optional<String> guard,
    Optional<String> guardMessage,
    List<Action> actions) {

    public Transition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Transition name must not be blank");
        }
        if (fromStateIri == null || fromStateIri.isBlank()) {
            throw new IllegalArgumentException("Transition fromStateIri must not be blank");
        }
        if (toStateIri == null || toStateIri.isBlank()) {
            throw new IllegalArgumentException("Transition toStateIri must not be blank");
        }
        Objects.requireNonNull(guard, "guard must not be null");
        Objects.requireNonNull(guardMessage, "guardMessage must not be null");
        Objects.requireNonNull(actions, "actions must not be null");
        actions = List.copyOf(actions);
    }
}
