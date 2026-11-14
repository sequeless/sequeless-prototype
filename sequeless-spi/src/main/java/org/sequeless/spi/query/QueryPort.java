package org.sequeless.spi.query;

import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.Schema;

/**
 * The outbound port that computes aggregates over stored {@link org.sequeless.spi.object.BusinessObject}s,
 * for the {@code sq:Rollup}-derived properties of an ontology. It holds only aggregate: single-object reads
 * are {@link ReadPort}'s, filtered, sorted, paged, faceted and free-text browsing is {@link BrowsePort}'s, and
 * index preparation is {@link IndexPreparationPort}'s. Exactly one implementation is wired into the running
 * application at a time, selected by configuration property (see the app's {@code PortRegistry}); use cases
 * never choose between implementations themselves.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not advisory:
 * {@code sequeless-spi-testkit}'s {@code AggregateContract} asserts every clause of it mechanically against
 * any port passed to it, and any adapter's own test suite is expected to extend that contract. An
 * implementation that violates a clause here is not a valid adapter, regardless of what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to every method on this interface must be non-{@code null}. A
 *       conforming implementation throws {@link NullPointerException} when any argument is {@code null}.
 *   <li><b>{@link #aggregate} is scoped and filtered like {@link BrowsePort#browse}.</b> It is scoped to
 *       {@code scope.tenantId()}; source objects are matched against the union of {@code
 *       request.sourceTypes()}, exactly as {@link Query#types()} is a union; and a soft-deleted source object is
 *       excluded from the aggregate, exactly as it is excluded from {@link BrowsePort#browse}'s results (there is
 *       no {@code includeDeleted} escape hatch for aggregation).
 *   <li><b>{@link #aggregate}'s result density depends on {@code request.function()}.</b> This is the most
 *       load-bearing, easiest-to-get-wrong part of this contract. For {@link AggregateFunction#COUNT}, the
 *       returned {@link AggregateResult#values()} is <em>dense</em>: every id in {@code request.targetIds()} is
 *       present as a key, with an {@link org.sequeless.kernel.values.IntegerValue} of {@code 0} when that target
 *       has no matching source rows. For {@link AggregateFunction#SUM}, {@link AggregateFunction#MIN}, {@link
 *       AggregateFunction#MAX}, and {@link AggregateFunction#AVG}, a target with no matching source rows is
 *       simply <em>absent</em> from {@link AggregateResult#values()} — never present with a zero or null-like
 *       value — so that "no sources" stays distinguishable from "sources totalling zero". An empty {@code
 *       request.targetIds()} is not an error: it simply yields an {@link AggregateResult} whose {@link
 *       AggregateResult#values()} is empty.
 * </ul>
 */
public interface QueryPort {

    /**
     * Computes a single {@link AggregateFunction} over the source objects of {@code request.sourceTypes()} that
     * point back to each of {@code request.targetIds()} via {@code request.viaIri()}, restricted to source objects
     * matching {@code request.criteria()}. Backs a {@code sq:Rollup}-derived property: the planner (core) resolves
     * one such rule into one {@code AggregateRequest} and calls this method once per rule per page, never once per
     * object.
     *
     * <p>See the interface-level javadoc for the full contract this method must satisfy, in particular tenant
     * scoping and soft-deleted-source exclusion (identical to {@link BrowsePort#browse}'s), and — most
     * importantly — the result-density contract: {@link AggregateFunction#COUNT} is dense (every requested target
     * id present, {@code 0} when it has no matching source rows), while {@link AggregateFunction#SUM}, {@link
     * AggregateFunction#MIN}, {@link AggregateFunction#MAX}, and {@link AggregateFunction#AVG} simply omit a target
     * id from the result when no source row matches it. An empty {@code request.targetIds()} is a valid,
     * contract-tested request, not an error, and yields an {@link AggregateResult} with empty {@link
     * AggregateResult#values()}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code null}
     * @param schema the type system {@code request} is interpreted against; must not be {@code null}
     * @param request the source types, relationship, targets, function, optional aggregated property, and filter
     *     criteria to compute; must not be {@code null}
     * @return a non-null result containing the computed value per target id, per the density contract above
     * @throws NullPointerException if any argument is {@code null}
     */
    AggregateResult aggregate(Scope scope, Schema schema, AggregateRequest request);
}
