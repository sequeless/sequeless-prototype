package org.sequeless.spi.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ViolationTest {

    @Test
    void carriesPathAndMessage() {
        Violation violation = new Violation("https://example.org/ref#title", "must not be blank");

        assertThat(violation.path()).isEqualTo("https://example.org/ref#title");
        assertThat(violation.message()).isEqualTo("must not be blank");
    }

    @Test
    void allowsBlankPathForObjectLevelViolations() {
        Violation violation = new Violation("", "the object as a whole is invalid");

        assertThat(violation.path()).isEmpty();
    }

    @Test
    void rejectsNullPath() {
        assertThatNullPointerException().isThrownBy(() -> new Violation(null, "message"));
    }

    @Test
    void rejectsNullMessage() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Violation("path", null));
    }

    @Test
    void rejectsBlankMessage() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Violation("path", "   "));
    }
}
