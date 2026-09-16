package org.sequeless.adapter.authz.permitall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.testkit.Fixtures;

/**
 * The permit-all semantics {@link org.sequeless.testkit.authz.AuthorizationContract} deliberately
 * does not assert: every operation against every resource is allowed, and the reason given is a
 * fixed, stable literal. The shared contract only checks shape and determinism, never the actual
 * allow/deny outcome, so this adapter's own policy behaviour must be proven here.
 */
class PermitAllAuthorizationPortTest {

    private static final String EXPECTED_REASON = "permit-all adapter: all requests are allowed";

    private final AuthorizationPort port = new PermitAllAuthorizationPort();

    @Test
    void permitsEveryOperationAgainstEveryResource() {
        for (Operation operation : Operation.values()) {
            for (String resource :
                new String[] {
                    AuthorizationPort.EVERYTHING, Fixtures.OPAQUE_RESOURCE, "arbitrary-other-resource"
                }) {
                AccessDecision decision = port.decide(Fixtures.defaultScope(), operation, resource);
                assertThat(decision.allowed())
                    .as("allowed() for %s against %s", operation, resource)
                    .isTrue();
            }
        }
    }

    @Test
    void reasonIsTheFixedLiteral() {
        AccessDecision decision =
            port.decide(Fixtures.defaultScope(), Operation.READ, AuthorizationPort.EVERYTHING);
        assertThat(decision.reason()).isEqualTo(EXPECTED_REASON);
    }

    @Test
    void repeatCallsReturnTheSameReason() {
        AccessDecision first =
            port.decide(Fixtures.defaultScope(), Operation.READ, Fixtures.OPAQUE_RESOURCE);
        AccessDecision second =
            port.decide(Fixtures.defaultScope(), Operation.READ, Fixtures.OPAQUE_RESOURCE);
        assertThat(first.reason()).isEqualTo(EXPECTED_REASON);
        assertThat(second.reason()).isEqualTo(EXPECTED_REASON);
    }
}
