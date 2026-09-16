package org.sequeless.testkit.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link AuthorizationPort}'s
 * interface-level javadoc. Every {@link AuthorizationPort} implementation — adapter or test
 * double — is expected to satisfy every clause of that javadoc, and this class exercises each
 * clause once, against whatever port {@link #port()} supplies.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #port()} to return the implementation under test:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends AuthorizationContract {
 *     protected AuthorizationPort port() {
 *         return new MyAdapter();
 *     }
 * }
 * }</pre>
 *
 * <p><b>What this contract deliberately does not check.</b> No assertion here inspects {@link
 * AccessDecision#allowed()} against an expected {@code true} or {@code false} — every assertion is
 * about shape (non-null decisions, non-null reasons), null-safety (rejecting missing arguments),
 * and determinism (two identical calls agreeing with <em>each other</em>, not with some fixed
 * expectation). A permit-all port and a deny-all port must both be able to pass this contract
 * unmodified; a policy's actual allow/deny behaviour is the adapter's own concern and belongs in
 * that adapter's own test suite, never here. A contract that only a permit-all implementation
 * could pass would make "this adapter passes the shared contract" a worthless claim.
 *
 * <p><b>Caveat.</b> An adapter backed by genuinely time-varying external state (for example, a
 * cached credential or token whose validity expires between calls) may appear to violate the
 * determinism clause asserted here even though it is conforming. This testkit does not attempt to
 * exercise that case; such an adapter's own test suite must document the exception explicitly.
 */
public abstract class AuthorizationContract {

    /**
     * @return the {@link AuthorizationPort} implementation under test; invoked fresh for every
     *     {@code @Test} method, so implementors may return a new instance each time or a shared
     *     one, whichever suits the port under test
     */
    protected abstract AuthorizationPort port();

    @Test
    void decideReturnsNonNullDecisionForEveryOperation() {
        AuthorizationPort port = port();
        for (Operation operation : Operation.values()) {
            AccessDecision decision =
                port.decide(Fixtures.defaultScope(), operation, AuthorizationPort.EVERYTHING);
            assertThat(decision).as("decision for %s", operation).isNotNull();
            assertThat(decision.reason()).as("reason for %s", operation).isNotNull();
        }
    }

    @Test
    void decideRejectsNullScope() {
        AuthorizationPort port = port();
        assertThatNullPointerException()
            .isThrownBy(() -> port.decide(null, Operation.READ, AuthorizationPort.EVERYTHING));
    }

    @Test
    void decideRejectsNullOperation() {
        AuthorizationPort port = port();
        assertThatNullPointerException()
            .isThrownBy(
                () -> port.decide(Fixtures.defaultScope(), null, AuthorizationPort.EVERYTHING));
    }

    @Test
    void decideRejectsNullResource() {
        AuthorizationPort port = port();
        assertThatNullPointerException()
            .isThrownBy(() -> port.decide(Fixtures.defaultScope(), Operation.READ, null));
    }

    @Test
    void decideHandlesArbitraryOpaqueResource() {
        AuthorizationPort port = port();
        AccessDecision decision =
            port.decide(Fixtures.defaultScope(), Operation.READ, Fixtures.OPAQUE_RESOURCE);
        assertThat(decision).isNotNull();
        assertThat(decision.reason()).isNotNull();
    }

    @Test
    void decideIsDeterministicForIdenticalInputs() {
        AuthorizationPort port = port();
        AccessDecision first =
            port.decide(Fixtures.defaultScope(), Operation.READ, Fixtures.OPAQUE_RESOURCE);
        AccessDecision second =
            port.decide(Fixtures.defaultScope(), Operation.READ, Fixtures.OPAQUE_RESOURCE);
        assertThat(second.allowed()).isEqualTo(first.allowed());
    }
}
