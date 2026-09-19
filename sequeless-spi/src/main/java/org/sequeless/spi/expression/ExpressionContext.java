package org.sequeless.spi.expression;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.object.Value;

/**
 * The request-scoped state an {@link ExpressionPort} call is evaluated against: a single object's
 * own properties, its current state (if its type has a state machine), and the id of the
 * principal on whose behalf the expression is being evaluated — exactly what a {@code sq:guard},
 * a {@code sq:SetProperty}/{@code sq:CreateObject} value expression, or a {@code sq:Webhook}/{@code
 * sq:Log} template needs to reference as {@code self}, {@code self.state}, and {@code principal}.
 *
 * <p>{@link #selfProperties()} is keyed by the property's short (local) name — {@code "owner"},
 * {@code "title"} — not its IRI. That is what lets a JEXL script write {@code self.owner}: JEXL
 * resolves a bean-style {@code .property} or {@code [key]} access on any {@code self} bound to a
 * {@link java.util.Map}, and it has no notion of an IRI-qualified property name. Shortening a
 * property's IRI down to this local name is the caller's job — a later core-interpreter task that
 * builds a fresh {@code ExpressionContext} immediately before each {@link ExpressionPort} call —
 * not this record's. This is deliberately a different, narrower representation than the
 * IRI-keyed {@code self} map described in the outbox {@code ActionRequest} payload format
 * (plan.md §3): that map is built once, frozen at transition-fire time, and carries enough
 * information to survive a Temporal activity retry without re-reading mutable object state: an
 * {@code ExpressionContext} carries no such durability concern and is rebuilt fresh, by short
 * name, right before every evaluation.
 *
 * @param selfProperties the evaluated object's own property values, keyed by property short name
 *     (not IRI); must not be {@code null}; no value in the map may be {@code null}; returned as an
 *     unmodifiable copy so callers cannot mutate this context after construction
 * @param selfState the evaluated object's current state ({@code self.state}), present only if its
 *     type has a state machine; must not be {@code null} (the {@link Optional} wrapper itself, not
 *     just its contents)
 * @param principalId the id of the principal the expression is evaluated on behalf of ({@code
 *     principal}); must not be {@code null}
 */
public record ExpressionContext(
    Map<String, Value> selfProperties, Optional<String> selfState, String principalId) {

    public ExpressionContext {
        Objects.requireNonNull(selfProperties, "selfProperties must not be null");
        selfProperties = Map.copyOf(selfProperties);
        Objects.requireNonNull(selfState, "selfState must not be null");
        Objects.requireNonNull(principalId, "principalId must not be null");
    }
}
