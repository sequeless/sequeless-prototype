package org.sequeless.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.authz.AccessDecision;

class AuthorizationExceptionTest {

    @Test
    void carriesTheDenyingDecisionAndUsesItsReasonAsTheMessage() {
        AccessDecision denial = AccessDecision.deny("no access");

        AuthorizationException exception = new AuthorizationException(denial);

        assertThat(exception.decision()).isEqualTo(denial);
        assertThat(exception.getMessage()).isEqualTo("no access");
    }

    @Test
    void rejectsNullDecision() {
        assertThatNullPointerException().isThrownBy(() -> new AuthorizationException(null));
    }

    @Test
    void rejectsAnAllowedDecision() {
        AccessDecision permit = AccessDecision.permit("ok");

        assertThatIllegalArgumentException().isThrownBy(() -> new AuthorizationException(permit));
    }
}
