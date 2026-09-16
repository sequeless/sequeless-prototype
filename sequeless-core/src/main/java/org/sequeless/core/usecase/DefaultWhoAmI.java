package org.sequeless.core.usecase;

import java.util.Objects;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.WhoAmI;
import org.sequeless.core.api.WhoAmIResult;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;

/**
 * Reference implementation of {@link WhoAmI}.
 *
 * <p>Consulting the injected {@link AuthorizationPort} is the entire point of this use case: it is
 * never optional, never cached, and never short-circuited. Every call asks the port to decide
 * {@link Operation#READ} against {@link AuthorizationPort#EVERYTHING} — {@code /whoami} reports on
 * the caller's own identity, not on any specific resource — and a denial is translated into an
 * {@link AuthorizationException} carrying the port's own decision rather than a generic failure.
 */
public final class DefaultWhoAmI implements WhoAmI {

    private final AuthorizationPort authorizationPort;

    /**
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @throws NullPointerException if {@code authorizationPort} is {@code null}
     */
    public DefaultWhoAmI(AuthorizationPort authorizationPort) {
        this.authorizationPort =
            Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
    }

    @Override
    public WhoAmIResult whoAmI(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        AccessDecision decision =
            authorizationPort.decide(scope, Operation.READ, AuthorizationPort.EVERYTHING);
        if (!decision.allowed()) {
            throw new AuthorizationException(decision);
        }
        return new WhoAmIResult(scope.tenantId(), scope.principal(), decision);
    }
}
