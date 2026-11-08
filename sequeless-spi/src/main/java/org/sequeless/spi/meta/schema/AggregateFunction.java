package org.sequeless.spi.meta.schema;

/**
 * The aggregate a {@code org.sequeless.spi.meta.behaviour.RollupRule} computes over its source objects, mirroring {@code
 * sq:count}/{@code sq:sum}/{@code sq:min}/{@code sq:max}/{@code sq:avg} in the {@code sq:}
 * vocabulary. Also the function requested by {@link org.sequeless.spi.query.AggregateRequest},
 * since a {@code org.sequeless.spi.meta.behaviour.RollupRule} is resolved into exactly one such request per rule.
 *
 * <p>Whether {@code org.sequeless.spi.meta.behaviour.RollupRule#ofPropertyIri()} must be present or absent for a given constant
 * (present for {@link #SUM}, {@link #MIN}, {@link #MAX}, and {@link #AVG}; absent for {@link
 * #COUNT}) is not enforced by this enum or by {@code org.sequeless.spi.meta.behaviour.RollupRule} itself — see {@code org.sequeless.spi.meta.behaviour.RollupRule}'s
 * javadoc for where that coherence check lives.
 */
public enum AggregateFunction {

    /**
     * The number of source objects matching the rule, dense in the resulting {@link
     * org.sequeless.spi.query.AggregateResult}: every requested target id appears, {@code 0} when
     * it has no matching source rows. Never paired with {@code org.sequeless.spi.meta.behaviour.RollupRule#ofPropertyIri()}.
     */
    COUNT,

    /**
     * The sum of {@code org.sequeless.spi.meta.behaviour.RollupRule#ofPropertyIri()} across matching source objects. Absent from
     * the resulting {@link org.sequeless.spi.query.AggregateResult} for a target with no matching
     * source rows, rather than defaulting to zero, so "no sources" stays distinguishable from
     * "sources totalling zero".
     */
    SUM,

    /**
     * The minimum of {@code org.sequeless.spi.meta.behaviour.RollupRule#ofPropertyIri()} across matching source objects. Absent
     * from the resulting {@link org.sequeless.spi.query.AggregateResult} for a target with no
     * matching source rows.
     */
    MIN,

    /**
     * The maximum of {@code org.sequeless.spi.meta.behaviour.RollupRule#ofPropertyIri()} across matching source objects. Absent
     * from the resulting {@link org.sequeless.spi.query.AggregateResult} for a target with no
     * matching source rows.
     */
    MAX,

    /**
     * The average of {@code org.sequeless.spi.meta.behaviour.RollupRule#ofPropertyIri()} across matching source objects. Absent
     * from the resulting {@link org.sequeless.spi.query.AggregateResult} for a target with no
     * matching source rows.
     */
    AVG
}
