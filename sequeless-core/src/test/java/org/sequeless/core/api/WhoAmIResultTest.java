package org.sequeless.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Principal;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;

class WhoAmIResultTest {

    private static final TenantId TENANT = TenantId.DEFAULT;
    private static final Principal PRINCIPAL = new Principal("alice", "Alice", Set.of());
    private static final AccessDecision DECISION = AccessDecision.permit("ok");

    @Test
    void exposesItsComponents() {
        WhoAmIResult result = new WhoAmIResult(TENANT, PRINCIPAL, DECISION);

        assertThat(result.tenantId()).isEqualTo(TENANT);
        assertThat(result.principal()).isEqualTo(PRINCIPAL);
        assertThat(result.decision()).isEqualTo(DECISION);
    }

    @Test
    void rejectsNullTenantId() {
        assertThatNullPointerException()
            .isThrownBy(() -> new WhoAmIResult(null, PRINCIPAL, DECISION));
    }

    @Test
    void rejectsNullPrincipal() {
        assertThatNullPointerException()
            .isThrownBy(() -> new WhoAmIResult(TENANT, null, DECISION));
    }

    @Test
    void rejectsNullDecision() {
        assertThatNullPointerException()
            .isThrownBy(() -> new WhoAmIResult(TENANT, PRINCIPAL, null));
    }
}
