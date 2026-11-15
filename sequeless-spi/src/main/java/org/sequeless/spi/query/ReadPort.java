package org.sequeless.spi.query;

import java.util.Optional;
import org.sequeless.kernel.identity.Scope;

/**
 * The outbound port that reads a single stored object by id, returned as an {@link ObjectView}. Exactly one
 * implementation is wired into the running application at a time, selected by configuration
 * property (see the app's {@code PortRegistry}); use cases never choose between implementations
 * themselves.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: {@code sequeless-spi-testkit}'s {@code ReadContract} asserts every clause of it
 * mechanically against any port passed to it, and any adapter's own test suite is expected to
 * extend that contract. An implementation that violates a clause here is not a valid adapter,
 * regardless of what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to {@link #read} must be non-{@code null}. A
 *       conforming implementation throws {@link NullPointerException} when any argument is {@code
 *       null}. It must never substitute a default: missing input is a programming error in the
 *       caller, not a fact about what is stored.
 *   <li><b>Tenant scoping.</b> {@link #read} is scoped to {@code scope.tenantId()}. An object created
 *       under one tenant is invisible to a read made with a {@link Scope} whose {@code tenantId}
 *       differs, as if it did not exist.
 *   <li><b>Not found is empty, never a throw.</b> {@link #read} returns {@link Optional#empty()}
 *       for an id that does not exist, for an object that has been soft-deleted, and for an object
 *       that belongs to a different tenant than {@code scope.tenantId()}. These three cases are
 *       indistinguishable to the caller by design, so that the caller can answer each with the same
 *       not-found outcome.
 * </ul>
 */
public interface ReadPort {

    /**
     * Looks up a single object by id. See the interface-level javadoc for the full contract this
     * method must satisfy, including the three cases that all return an empty result.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param request the id of the object to read; must not be {@code null}
     * @return the object's view, or {@link Optional#empty()} if it does not exist, has been
     *     soft-deleted, or belongs to a different tenant than {@code scope.tenantId()}
     * @throws NullPointerException if either argument is {@code null}
     */
    Optional<ObjectView> read(Scope scope, ReadRequest request);
}
