package org.sequeless.spi.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;

/**
 * Unit-level tests for the {@link AuthorizationPort} interface shape itself. The full behavioural
 * contract every implementation must satisfy (null handling, never-throw, opaque resources,
 * determinism) is asserted mechanically against arbitrary implementations by {@code
 * sequeless-spi-testkit}'s {@code AuthorizationContract}, not repeated here.
 */
class AuthorizationPortTest {

    @Test
    void everythingSentinelIsTheAsteriskString() {
        assertThat(AuthorizationPort.EVERYTHING).isEqualTo("*");
    }

    @Test
    void isUsableAsASingleAbstractMethodInterface() {
        AuthorizationPort port = (scope, operation, resource) -> {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(operation, "operation must not be null");
            Objects.requireNonNull(resource, "resource must not be null");
            return AccessDecision.permit("lambda stub");
        };

        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
        AccessDecision decision = port.decide(scope, Operation.READ, AuthorizationPort.EVERYTHING);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("lambda stub");
    }

    @Test
    void aConformingImplementationRejectsNullArguments() {
        AuthorizationPort port = (scope, operation, resource) -> {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(operation, "operation must not be null");
            Objects.requireNonNull(resource, "resource must not be null");
            return AccessDecision.permit("unreachable");
        };
        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

        assertThatNullPointerException()
            .isThrownBy(() -> port.decide(null, Operation.READ, AuthorizationPort.EVERYTHING));
        assertThatNullPointerException()
            .isThrownBy(() -> port.decide(scope, null, AuthorizationPort.EVERYTHING));
        assertThatNullPointerException().isThrownBy(() -> port.decide(scope, Operation.READ, null));
    }
}
