package org.sequeless.core.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.WhoAmIResult;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;

/**
 * Unit tests for {@link DefaultWhoAmI}. {@link AuthorizationPort} is a functional interface, so
 * every port double here is a plain lambda — no mocking framework is needed, and each test's
 * stub makes its own behaviour obvious at the call site.
 *
 * <p>{@code sequeless-spi-testkit}'s {@code AuthorizationContract} is deliberately not used here:
 * it never asserts {@code allowed()} either way, so it cannot prove the "denial throws" behaviour
 * these tests exist to cover. Core also must not depend on the testkit module at all.
 */
class DefaultWhoAmITest {

    private static final Scope SCOPE =
        new Scope(new TenantId("acme"), new Principal("alice", "Alice", Set.of("member")));

    @Test
    void whoAmIEchoesScopeTenantAndPrincipalOnPermit() {
        AccessDecision permit = AccessDecision.permit("ok");
        DefaultWhoAmI whoAmI = new DefaultWhoAmI((scope, operation, resource) -> permit);

        WhoAmIResult result = whoAmI.whoAmI(SCOPE);

        assertThat(result.tenantId()).isEqualTo(SCOPE.tenantId());
        assertThat(result.principal()).isEqualTo(SCOPE.principal());
        assertThat(result.decision()).isEqualTo(permit);
    }

    @Test
    void whoAmIThrowsWithPortsOwnDecisionOnDeny() {
        AccessDecision denial = AccessDecision.deny("nope");
        DefaultWhoAmI whoAmI = new DefaultWhoAmI((scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> whoAmI.whoAmI(SCOPE))
            .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
    }

    @Test
    void whoAmIConsultsPortWithReadOperationAndEverythingResource() {
        List<Scope> capturedScopes = new ArrayList<>();
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        DefaultWhoAmI whoAmI =
            new DefaultWhoAmI(
                (scope, operation, resource) -> {
                    capturedScopes.add(scope);
                    capturedOperations.add(operation);
                    capturedResources.add(resource);
                    return AccessDecision.permit("ok");
                });

        whoAmI.whoAmI(SCOPE);

        assertThat(capturedScopes).containsExactly(SCOPE);
        assertThat(capturedOperations).containsExactly(Operation.READ);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void constructorRejectsNullPort() {
        assertThatNullPointerException().isThrownBy(() -> new DefaultWhoAmI(null));
    }

    @Test
    void whoAmIRejectsNullScope() {
        DefaultWhoAmI whoAmI = new DefaultWhoAmI((scope, operation, resource) -> AccessDecision.permit("ok"));

        assertThatNullPointerException().isThrownBy(() -> whoAmI.whoAmI(null));
    }
}
