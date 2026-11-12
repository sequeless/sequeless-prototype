package org.sequeless.spi.object;

import org.sequeless.kernel.identity.Scope;

/**
 * The outbound port that recomputes the full-text search data of every stored object of a tenant,
 * from that tenant's current searchable-property registry. The host calls it after {@link
 * org.sequeless.spi.query.IndexPreparationPort#prepare} reports that it registered a new searchable
 * property, so that the objects stored before the registration become searchable.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy.
 *
 * <ul>
 *   <li><b>Null handling.</b> {@code scope} must be non-{@code null}. A conforming implementation
 *       throws {@link NullPointerException} when it is {@code null}.
 *   <li><b>Tenant scoping.</b> Only the objects of {@code scope.tenantId()} are recomputed. Another
 *       tenant's objects are not touched.
 *   <li><b>Idempotent.</b> A repeat call recomputes the same values. It does not promise a no-op
 *       when nothing is searchable: the recomputation rewrites every object row of the tenant.
 * </ul>
 */
public interface SearchVectorBackfillPort {

    /**
     * Recomputes the full-text search data of every object of {@code scope.tenantId()} from the
     * tenant's current searchable-property registry. See the interface-level javadoc for the full
     * contract this method must satisfy.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @throws NullPointerException if {@code scope} is {@code null}
     */
    void backfill(Scope scope);
}
