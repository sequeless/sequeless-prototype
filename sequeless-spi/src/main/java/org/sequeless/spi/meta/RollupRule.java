package org.sequeless.spi.meta;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.spi.query.Criterion;

/**
 * A declarative {@code sq:Rollup}: derive a property's value by aggregating the objects of another
 * type that relate back to it, exactly as {@code ex:Project.openTaskCount} is computed from its
 * {@code ex:Task}s. The planner (core) resolves a {@code RollupRule} into an {@link
 * org.sequeless.spi.query.AggregateRequest} — expanding {@link #sourceTypeIri()} to its concrete
 * subtypes via the meta-model's type hierarchy, exactly as it already does for {@code browse} —
 * and issues one request per distinct rule per page, never one per object.
 *
 * <p>This record does not itself enforce {@link #function()}/{@link #ofPropertyIri()} coherence —
 * for example, it does not check that {@link #ofPropertyIri()} is present for {@link
 * AggregateFunction#SUM}, {@link AggregateFunction#MIN}, {@link AggregateFunction#MAX}, or {@link
 * AggregateFunction#AVG}, or that it is absent for {@link AggregateFunction#COUNT}. That coherence
 * check is the Jena mapper's job when parsing {@code sq:Rollup} nodes, exactly as {@link Criterion}
 * already disclaims operator/value coherence for the criteria carried here.
 *
 * @param sourceTypeIri the IRI of the related type to aggregate over ({@code sq:over}); must not be
 *     blank
 * @param viaIri the IRI of the relationship property on {@code sourceTypeIri} that points back to
 *     the type this rule derives a property for ({@code sq:via}); must not be blank
 * @param function the aggregate to compute ({@code sq:function}); must not be {@code null}
 * @param ofPropertyIri the IRI of the source-side property to aggregate ({@code sq:of}), required
 *     for every {@link AggregateFunction} other than {@link AggregateFunction#COUNT}, absent for
 *     {@link AggregateFunction#COUNT}, per this record's disclaimer above; must not be {@code null}
 *     (the {@link Optional} wrapper itself, not just its contents)
 * @param criteria the filter clauses narrowing which source objects are aggregated ({@code
 *     sq:filter}), already resolved to SPI {@link Criterion}s; combined with AND, exactly as {@link
 *     org.sequeless.spi.query.Query#criteria()} is; must not be {@code null}; may be empty;
 *     returned as an unmodifiable copy so callers cannot mutate this rule after construction
 */
public record RollupRule(
    String sourceTypeIri,
    String viaIri,
    AggregateFunction function,
    Optional<String> ofPropertyIri,
    List<Criterion> criteria)
    implements DerivationRule {

    public RollupRule {
        if (sourceTypeIri == null || sourceTypeIri.isBlank()) {
            throw new IllegalArgumentException("RollupRule sourceTypeIri must not be blank");
        }
        if (viaIri == null || viaIri.isBlank()) {
            throw new IllegalArgumentException("RollupRule viaIri must not be blank");
        }
        Objects.requireNonNull(function, "function must not be null");
        Objects.requireNonNull(ofPropertyIri, "ofPropertyIri must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        criteria = List.copyOf(criteria);
    }
}
