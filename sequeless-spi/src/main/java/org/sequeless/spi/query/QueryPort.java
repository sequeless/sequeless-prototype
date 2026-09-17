package org.sequeless.spi.query;

import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;

/**
 * The outbound port that runs filtered, sorted, paged, faceted, and free-text queries over stored
 * {@link org.sequeless.spi.object.BusinessObject}s, and prepares whatever index structures an
 * implementation needs in order to answer them efficiently. Exactly one implementation is wired
 * into the running application at a time, selected by configuration property (see the app's
 * {@code PortRegistry}); use cases never choose between implementations themselves.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: a future {@code sequeless-spi-testkit} {@code QueryContract} asserts every clause of
 * it mechanically against any port passed to it, and any adapter's own test suite is expected to
 * extend that contract. An implementation that violates a clause here is not a valid adapter,
 * regardless of what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to every method on this interface must be non-{@code
 *       null}. A conforming implementation throws {@link NullPointerException} when any argument
 *       is {@code null}.
 *   <li><b>Tenant scoping.</b> {@link #query} is scoped to {@code scope.tenantId()}, exactly like
 *       {@link org.sequeless.spi.object.ObjectStorePort#browse(Scope, java.util.Set,
 *       org.sequeless.spi.object.Page)}: an object created under one tenant is invisible to a
 *       query made with a {@link Scope} whose {@code tenantId} differs, as if it did not exist.
 *   <li><b>Soft-delete filtering.</b> Soft-deleted objects are excluded from {@link #query}'s
 *       results unless {@code query.includeDeleted()} is {@code true}.
 *   <li><b>{@code query.types()} is a union, not an intersection.</b> Exactly like {@link
 *       org.sequeless.spi.object.ObjectStorePort#browse}'s {@code Set<TypeRef>} parameter, matching
 *       objects are those whose type is any one of the IRIs in {@link Query#types()}, not objects
 *       matching every listed type at once.
 *   <li><b>Default sort order.</b> When {@code query.sorts()} is empty, results are ordered
 *       ascending by {@code createdAt} then by {@code id} — the same {@code (createdAt, id)}
 *       ascending default order that {@link org.sequeless.spi.object.ObjectStorePort#browse}
 *       documents and implements. This is a precise, testable contract: a future {@code
 *       QueryContract}'s pagination tests assert against exactly this tie-broken ordering.
 *   <li><b>Facet counting is "conventional multi-select."</b> For a facet property {@code P} in
 *       {@code query.facetProperties()}, the bucket counts returned for {@code P} are computed
 *       against every criterion in {@code query.criteria()} <em>except</em> any criterion whose
 *       {@code property} is {@code P} itself. This means selecting one bucket of a facet (by
 *       adding a matching {@link Criterion} for that property) does not zero out the counts of
 *       that facet's sibling buckets — only the counts of other facets are narrowed by the
 *       selection.
 *   <li><b>{@link #ensureIndexes} is idempotent.</b> It is safe to call repeatedly with an
 *       equivalent {@link MetaModelSnapshot} — it will be called on every ontology activation in a
 *       later phase task, not just once — and a conforming implementation must not fail, error, or
 *       accumulate duplicate index structures on a repeat call.
 *   <li><b>{@link #ensureIndexes} is a no-op signal for a snapshot with no indexing needs.</b> A
 *       {@link MetaModelSnapshot} whose types declare no {@code sq:indexed} and no {@code
 *       sq:searchable} properties must not cause {@link #ensureIndexes} to fail; it simply has
 *       nothing to prepare.
 * </ul>
 */
public interface QueryPort {

    /**
     * Runs a single filtered, sorted, paged, faceted, and optionally free-text query. See the
     * interface-level javadoc for the full contract this method must satisfy, including tenant
     * scoping, soft-delete filtering, the union semantics of {@link Query#types()}, the default
     * sort order used when {@link Query#sorts()} is empty, and the "conventional multi-select"
     * facet counting rule.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param snapshot the type system {@code query} is interpreted against; must not be {@code
     *     null}
     * @param query the filter, sort, paging, and facet request to run; must not be {@code null}
     * @return a non-null result containing the matching page of objects, the total match count,
     *     and the computed facet buckets
     * @throws NullPointerException if any argument is {@code null}
     */
    QueryResult query(Scope scope, MetaModelSnapshot snapshot, Query query);

    /**
     * Prepares whatever index structures this implementation needs in order to answer {@link
     * #query} efficiently for the types described in {@code snapshot} — for example, one index per
     * {@code sq:indexed} property and a full-text index over every {@code sq:searchable} property.
     * See the interface-level javadoc for the full contract this method must satisfy, including
     * idempotence and its no-op behaviour for a snapshot with no indexing needs.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param snapshot the type system to prepare indexes for; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    void ensureIndexes(Scope scope, MetaModelSnapshot snapshot);
}
