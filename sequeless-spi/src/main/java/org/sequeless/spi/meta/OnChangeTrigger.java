package org.sequeless.spi.meta;

import java.util.List;
import java.util.Objects;

/**
 * A {@link TriggerSpec} that fires a {@link Transition} when another object's data changes ({@code
 * sq:OnChange}), exactly as {@code autoClose} moves an {@code ex:Project} from {@code ex:Active} to
 * {@code ex:Closed} the instant its last open {@code ex:Task} closes. {@link #watchIris()} lists
 * {@code sq:watch} relationship IRIs declared as **forward** properties on the *changed* object's
 * type — {@code ex:Task}'s {@code ex:belongsToProject} points at the {@code ex:Project} to
 * re-evaluate — so candidate resolution is a single hop with no {@code owl:inverseOf} requirement:
 * core reads {@code ex:belongsToProject}'s current value(s) directly off the changed {@code
 * ex:Task}.
 *
 * <p>An empty {@link #watchIris()} is legal and means "watch self only" — the transition
 * re-evaluates when the object *it is declared on* changes, with no relationship hop at all. This
 * is distinct from {@code autoClose}'s case (which watches a different type's changes) but shares
 * the same evaluation path in {@code DefaultTriggerEvaluator.onChange}: the changed object is
 * always itself a candidate if its own type declares an {@code OnChange} transition departing its
 * current state, watch list or not.
 *
 * @param watchIris the IRIs of the forward relationship properties, declared on the type being
 *     watched, that point back to this transition's own type ({@code sq:watch}); must not be
 *     {@code null}; may be empty, meaning "watch self only"; returned as an unmodifiable copy so
 *     callers cannot mutate this trigger after construction
 */
public record OnChangeTrigger(List<String> watchIris) implements TriggerSpec {

    public OnChangeTrigger {
        Objects.requireNonNull(watchIris, "watchIris must not be null");
        watchIris = List.copyOf(watchIris);
    }

    @Override
    public TriggerKind kind() {
        return TriggerKind.ON_CHANGE;
    }
}
