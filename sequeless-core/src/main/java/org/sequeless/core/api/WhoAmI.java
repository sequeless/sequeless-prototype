package org.sequeless.core.api;

import org.sequeless.core.AuthorizationException;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AuthorizationPort;

/**
 * Reports the caller's own tenant, principal, and authorization decision. This is the first use
 * case Sequeless defines, and Phase 0's end-to-end proof that a request reaches the {@link
 * AuthorizationPort} SPI through core rather than around it: every implementation must consult the
 * port for each call, never answer from cached or hardcoded state.
 *
 * @see org.sequeless.core.usecase.DefaultWhoAmI
 */
public interface WhoAmI {

    /**
     * Reports the tenant, principal, and authorization decision for {@code scope}.
     *
     * <p>This method has no {@code throws} clause because {@link AuthorizationException} is
     * unchecked, but a conforming implementation throws it whenever the underlying {@link
     * AuthorizationPort} denies the operation being reported on. Callers at the REST boundary are
     * expected to catch it there rather than have every signature up the call chain declare it.
     *
     * @param scope the tenant and principal to report on; must not be {@code null}
     * @return the result describing {@code scope}'s tenant, principal, and the port's decision
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws AuthorizationException if the authorization port denies the operation
     */
    WhoAmIResult whoAmI(Scope scope);
}
