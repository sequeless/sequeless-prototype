package org.sequeless.spi.query;

import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.schema.api.Schema;

/**
 * The outbound port that runs filtered, sorted, paged, faceted, and free-text browse queries over
 * stored objects, returning each matching object as an {@link ObjectView}. Exactly one implementation is wired into
 * the running application at a time, selected by configuration property (see the app's {@code
 * PortRegistry}); use cases never choose between implementations themselves.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: {@code sequeless-spi-testkit}'s {@code BrowseContract} asserts every clause of it
 * mechanically against any port passed to it, and any adapter's own test suite is expected to
 * extend that contract. An implementation that violates a clause here is not a valid adapter,
 * regardless of what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to every method on this interface must be non-{@code
 *       null}. A conforming implementation throws {@link NullPointerException} when any argument
 *       is {@code null}.
 *   <li><b>Tenant scoping.</b> {@link #browse} is scoped to {@code scope.tenantId()}: an object
 *       created under one tenant is invisible to a query made with a {@link Scope} whose {@code
 *       tenantId} differs, as if it did not exist.
 *   <li><b>Soft-delete filtering.</b> Soft-deleted objects are excluded from {@link #browse}'s
 *       results unless {@code query.includeDeleted()} is {@code true}.
 *   <li><b>{@code query.types()} is a union, not an intersection.</b> Matching objects are those
 *       whose type is any one of the IRIs in {@link Query#types()}, not objects matching every
 *       listed type at once.
 *   <li><b>Default sort order.</b> When {@code query.sorts()} is empty, results are ordered
 *       ascending by {@code createdAt} then by {@code id}: the same {@code (createdAt, id)}
 *       ascending order the object store uses for its own listings. This is a precise, testable
 *       contract: a {@code BrowseContract}'s pagination tests assert against exactly this
 *       tie-broken ordering.
 *   <li><b>Page overrun is empty, never a throw.</b> A {@link Query#page()} whose number is past the
 *       last page returns a {@link QueryResult} with empty {@link QueryResult#items()} and the
 *       correct {@link QueryResult#total()}.
 *   <li><b>Facet counting is "conventional multi-select."</b> For a facet property {@code P} in
 *       {@code query.facetProperties()}, the bucket counts returned for {@code P} are computed
 *       against every criterion in {@code query.criteria()} <em>except</em> any criterion whose
 *       {@code property} is {@code P} itself. This means selecting one bucket of a facet (by
 *       adding a matching {@link Criterion} for that property) does not zero out the counts of
 *       that facet's sibling buckets — only the counts of other facets are narrowed by the
 *       selection.
 * </ul>
 */
public interface BrowsePort {

    /**
     * Runs a single filtered, sorted, paged, faceted, and optionally free-text query. See the
     * interface-level javadoc for the full contract this method must satisfy, including tenant
     * scoping, soft-delete filtering, the union semantics of {@link Query#types()}, the default
     * sort order used when {@link Query#sorts()} is empty, page-overrun behaviour, and the
     * "conventional multi-select" facet counting rule.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param schema the type system {@code query} is interpreted against; must not be {@code
     *     null}
     * @param query the filter, sort, paging, and facet request to run; must not be {@code null}
     * @return a non-null result containing the matching page of objects, the total match count,
     *     and the computed facet buckets
     * @throws NullPointerException if any argument is {@code null}
     */
    QueryResult browse(Scope scope, Schema schema, Query query);
}
