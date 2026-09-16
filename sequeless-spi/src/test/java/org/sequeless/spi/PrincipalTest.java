package org.sequeless.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrincipalTest {

    @Test
    void anonymousHasNoRoles() {
        assertThat(Principal.ANONYMOUS.id()).isEqualTo("anonymous");
        assertThat(Principal.ANONYMOUS.displayName()).isEqualTo("Anonymous");
        assertThat(Principal.ANONYMOUS.roles()).isEmpty();
    }

    @Test
    void defensiveCopyIsUnaffectedByLaterMutationOfCallerSet() {
        Set<String> callerRoles = new HashSet<>();
        callerRoles.add("editor");

        Principal principal = new Principal("u1", "User One", callerRoles);

        // Mutate the caller's own set AFTER construction.
        callerRoles.add("admin");
        callerRoles.remove("editor");

        assertThat(principal.roles())
            .as("Principal's roles must not reflect post-construction mutation of the caller's set")
            .containsExactly("editor");
    }

    @Test
    void rolesAreUnmodifiable() {
        Principal principal = new Principal("u1", "User One", Set.of("editor"));

        assertThatThrownBy(() -> principal.roles().add("admin"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullRoles() {
        assertThatNullPointerException().isThrownBy(() -> new Principal("u1", "User One", null));
    }

    @Test
    void rejectsNullOrBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Principal(null, "User One", Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Principal("  ", "User One", Set.of()));
    }

    @Test
    void rejectsNullOrBlankDisplayName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Principal("u1", null, Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Principal("u1", "  ", Set.of()));
    }

    @Test
    void rejectsNullElementWithinRoles() {
        Set<String> rolesWithNull = new HashSet<>();
        rolesWithNull.add(null);
        assertThatNullPointerException().isThrownBy(() -> new Principal("u1", "User One", rolesWithNull));
    }
}
