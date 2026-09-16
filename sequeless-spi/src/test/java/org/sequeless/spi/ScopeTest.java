package org.sequeless.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ScopeTest {

    @Test
    void holdsTenantAndPrincipal() {
        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

        assertThat(scope.tenantId()).isEqualTo(TenantId.DEFAULT);
        assertThat(scope.principal()).isEqualTo(Principal.ANONYMOUS);
    }

    @Test
    void rejectsNullTenantId() {
        assertThatNullPointerException().isThrownBy(() -> new Scope(null, Principal.ANONYMOUS));
    }

    @Test
    void rejectsNullPrincipal() {
        assertThatNullPointerException().isThrownBy(() -> new Scope(TenantId.DEFAULT, null));
    }
}
