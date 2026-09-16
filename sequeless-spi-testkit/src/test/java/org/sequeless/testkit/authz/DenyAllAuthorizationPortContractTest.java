package org.sequeless.testkit.authz;

import java.util.Objects;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;

/**
 * This testkit's own proof that {@link AuthorizationContract} is not biased toward permit-all
 * implementations. A deny-all port — one that rejects every request outright — is just as
 * conforming a {@link AuthorizationPort} as a permit-all one: the interface-level contract governs
 * null-handling, never-throwing, opaque resources, and determinism, never the actual allow/deny
 * outcome. If {@link AuthorizationContract} could only be passed by a permit-all implementation,
 * it would not be a meaningful shared contract, and this test would fail.
 */
class DenyAllAuthorizationPortContractTest extends AuthorizationContract {

    @Override
    protected AuthorizationPort port() {
        return new DenyAllAuthorizationPort();
    }

    /** Denies every request, but otherwise fully conforms to {@link AuthorizationPort}. */
    private static final class DenyAllAuthorizationPort implements AuthorizationPort {

        @Override
        public AccessDecision decide(Scope scope, Operation operation, String resource) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(operation, "operation must not be null");
            Objects.requireNonNull(resource, "resource must not be null");
            return AccessDecision.deny("deny-all test port");
        }
    }
}
