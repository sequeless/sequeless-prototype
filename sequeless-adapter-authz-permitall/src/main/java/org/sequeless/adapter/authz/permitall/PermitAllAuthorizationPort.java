package org.sequeless.adapter.authz.permitall;

import java.util.Objects;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;

/**
 * The default {@link AuthorizationPort} adapter (DR-11): permits every request unconditionally.
 * This exists so a freshly bootstrapped Sequeless application has a working authorization port
 * without an operator having to configure a real policy engine first — useful for local
 * development and as a documented, deliberate fallback, never as a production policy.
 *
 * <p>Has zero Spring imports and zero Spring annotations by design: this class must be loadable
 * and usable outside a Spring context, purely via {@code new PermitAllAuthorizationPort()} or
 * {@link java.util.ServiceLoader}. Only {@link PermitAllAuthorizationAutoConfiguration} in this
 * package is allowed to depend on Spring.
 */
public final class PermitAllAuthorizationPort implements AuthorizationPort {

    private static final String REASON = "permit-all adapter: all requests are allowed";

    @Override
    public AccessDecision decide(Scope scope, Operation operation, String resource) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(resource, "resource must not be null");
        return AccessDecision.permit(REASON);
    }
}
