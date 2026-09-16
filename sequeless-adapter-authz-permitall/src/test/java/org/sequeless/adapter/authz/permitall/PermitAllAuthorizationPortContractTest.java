package org.sequeless.adapter.authz.permitall;

import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.testkit.authz.AuthorizationContract;

/**
 * Proves {@link PermitAllAuthorizationPort} satisfies the shared {@link AuthorizationPort}
 * behavioural contract (null-handling, never-throwing, opaque resources, determinism) — the
 * mechanical requirements every adapter must meet, independently of its actual allow/deny policy.
 */
class PermitAllAuthorizationPortContractTest extends AuthorizationContract {

    @Override
    protected AuthorizationPort port() {
        return new PermitAllAuthorizationPort();
    }
}
