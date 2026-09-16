package org.sequeless.spi.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AccessDecisionTest {

    @Test
    void permitProducesAnAllowedDecisionCarryingTheReason() {
        AccessDecision decision = AccessDecision.permit("because policy says so");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("because policy says so");
    }

    @Test
    void denyProducesADisallowedDecisionCarryingTheReason() {
        AccessDecision decision = AccessDecision.deny("insufficient role");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("insufficient role");
    }

    @Test
    void blankReasonIsAllowed() {
        assertThat(AccessDecision.permit("").reason()).isEmpty();
        assertThat(new AccessDecision(false, "  ").reason()).isEqualTo("  ");
    }

    @Test
    void rejectsNullReason() {
        assertThatNullPointerException().isThrownBy(() -> new AccessDecision(true, null));
        assertThatNullPointerException().isThrownBy(() -> AccessDecision.permit(null));
        assertThatNullPointerException().isThrownBy(() -> AccessDecision.deny(null));
    }
}
