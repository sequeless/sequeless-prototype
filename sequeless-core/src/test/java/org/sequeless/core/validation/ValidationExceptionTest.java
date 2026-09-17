package org.sequeless.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.validation.Violation;

/** Unit tests for {@link ValidationException}. */
class ValidationExceptionTest {

    private static final Violation VIOLATION = new Violation("path", "message");

    @Test
    void constructorStoresSourceAndViolationsAndBuildsANonBlankMessage() {
        ValidationException exception =
            new ValidationException(ValidationException.Source.STRUCTURAL, List.of(VIOLATION));

        assertThat(exception.source()).isEqualTo(ValidationException.Source.STRUCTURAL);
        assertThat(exception.violations()).containsExactly(VIOLATION);
        assertThat(exception.getMessage()).isNotBlank();
        assertThat(exception.getMessage()).contains("STRUCTURAL");
    }

    @Test
    void violationsIsUnmodifiable() {
        ValidationException exception =
            new ValidationException(ValidationException.Source.SHACL, List.of(VIOLATION));

        assertThatExceptionOfType(UnsupportedOperationException.class)
            .isThrownBy(() -> exception.violations().add(VIOLATION));
    }

    @Test
    void emptyViolationsThrowsIllegalArgumentException() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () -> new ValidationException(ValidationException.Source.STRUCTURAL, List.of()));
    }

    @Test
    void constructorRejectsNullSource() {
        assertThatNullPointerException()
            .isThrownBy(() -> new ValidationException(null, List.of(VIOLATION)));
    }

    @Test
    void constructorRejectsNullViolations() {
        assertThatNullPointerException()
            .isThrownBy(() -> new ValidationException(ValidationException.Source.STRUCTURAL, null));
    }

    @Test
    void violationsIsDefensivelyCopied() {
        List<Violation> mutable = new ArrayList<>(List.of(VIOLATION));
        ValidationException exception =
            new ValidationException(ValidationException.Source.STRUCTURAL, mutable);

        mutable.add(new Violation("other", "other message"));

        assertThat(exception.violations()).containsExactly(VIOLATION);
    }
}
