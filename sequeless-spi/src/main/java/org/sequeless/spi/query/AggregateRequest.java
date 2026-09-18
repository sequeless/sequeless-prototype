package org.sequeless.spi.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.spi.meta.AggregateFunction;
import org.sequeless.spi.object.ObjectId;

/**
 * A single request to {@link QueryPort#aggregate}: compute one {@link AggregateFunction} over the
 * objects of {@code sourceTypes} that point back to each of {@code targetIds} via {@code viaIri},
 * for those source objects matching {@code criteria}. Resolved by the planner (core) from a single
 * {@code sq:Rollup} rule — {@code sourceTypes} is the rule's {@code sq:over} type expanded to its
 * concrete subtypes via the meta-model's type hierarchy, exactly as {@link Query#types()} already
 * is for a browse or query request — so the port itself never does hierarchy reasoning here either.
 *
 * @param sourceTypes the resolved set of concrete type IRIs to aggregate over; must not be {@code
 *     null}, must not be empty, mirroring {@link Query#types()}; returned as an unmodifiable copy
 *     so callers cannot mutate this request after construction
 * @param viaIri the IRI of the relationship property on {@code sourceTypes} that points back to the
 *     targets being aggregated for; must not be blank
 * @param targetIds the ids to compute an aggregate value for; must not be {@code null}; may be
 *     empty — this is a real, contract-tested case, not an error — in which case {@link
 *     QueryPort#aggregate} returns an {@link AggregateResult} with an empty {@link
 *     AggregateResult#values()}; returned as an unmodifiable copy so callers cannot mutate this
 *     request after construction
 * @param function the aggregate to compute; must not be {@code null}
 * @param ofPropertyIri the IRI of the source-side property to aggregate, required for every {@link
 *     AggregateFunction} other than {@link AggregateFunction#COUNT}; must not be {@code null} (the
 *     {@link Optional} wrapper itself, not just its contents) — this record does not itself enforce
 *     that coherence, mirroring {@link org.sequeless.spi.meta.RollupRule}'s own disclaimer
 * @param criteria the filter clauses narrowing which source objects are aggregated, combined with
 *     AND; must not be {@code null}; may be empty; returned as an unmodifiable copy so callers
 *     cannot mutate this request after construction
 */
public record AggregateRequest(
    Set<String> sourceTypes,
    String viaIri,
    Set<ObjectId> targetIds,
    AggregateFunction function,
    Optional<String> ofPropertyIri,
    List<Criterion> criteria) {

    public AggregateRequest {
        Objects.requireNonNull(sourceTypes, "sourceTypes must not be null");
        sourceTypes = Set.copyOf(sourceTypes);
        if (sourceTypes.isEmpty()) {
            throw new IllegalArgumentException("AggregateRequest sourceTypes must not be empty");
        }
        if (viaIri == null || viaIri.isBlank()) {
            throw new IllegalArgumentException("AggregateRequest viaIri must not be blank");
        }
        Objects.requireNonNull(targetIds, "targetIds must not be null");
        targetIds = Set.copyOf(targetIds);
        Objects.requireNonNull(function, "function must not be null");
        Objects.requireNonNull(ofPropertyIri, "ofPropertyIri must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        criteria = List.copyOf(criteria);
    }
}
