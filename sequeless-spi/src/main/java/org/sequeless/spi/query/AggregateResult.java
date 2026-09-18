package org.sequeless.spi.query;

import java.util.Map;
import java.util.Objects;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.Value;

/**
 * The outcome of a single {@link QueryPort#aggregate} call: the computed value per target id, per
 * the density contract documented on {@link QueryPort#aggregate} — dense (every requested target
 * id present, {@link org.sequeless.spi.object.IntegerValue} {@code 0} when it has no matching
 * source rows) for {@link org.sequeless.spi.meta.AggregateFunction#COUNT}, and simply absent for a
 * target with no matching source rows for every other function, so "no sources" stays
 * distinguishable from "sources totalling zero".
 *
 * @param values the computed value per target id; must not be {@code null}; a key absent from this
 *     map means "no value for that target", per the density contract above, not zero; returned as
 *     an unmodifiable copy so callers cannot mutate this result after construction
 */
public record AggregateResult(Map<ObjectId, Value> values) {

    public AggregateResult {
        Objects.requireNonNull(values, "values must not be null");
        values = Map.copyOf(values);
    }
}
