package org.sequeless.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class SpiVersionTest {

    @Test
    void versionStringMatchesTheDeclaredComponents() {
        assertThat(SpiVersion.VERSION).isEqualTo(SpiVersion.MAJOR + "." + SpiVersion.MINOR + "." + SpiVersion.PATCH);
    }

    @Test
    void isValidRangeAcceptsWellFormedRange() {
        assertThat(SpiVersion.isValidRange(">=0.1.0")).isTrue();
        assertThat(SpiVersion.isValidRange(">=12.34.56")).isTrue();
    }

    @Test
    void isValidRangeRejectsMalformedOrNullInput() {
        assertThat(SpiVersion.isValidRange(null)).isFalse();
        assertThat(SpiVersion.isValidRange("")).isFalse();
        assertThat(SpiVersion.isValidRange("0.1.0")).isFalse();
        assertThat(SpiVersion.isValidRange(">0.1.0")).isFalse();
        assertThat(SpiVersion.isValidRange(">=0.1")).isFalse();
        assertThat(SpiVersion.isValidRange(">=a.b.c")).isFalse();
        assertThat(SpiVersion.isValidRange("~>=0.1.0")).isFalse();
    }

    @Test
    void isCompatibleWithRejectsDifferentMajorVersionRegardlessOfDirection() {
        assertThat(SpiVersion.isCompatibleWith(">=1.0.0")).isFalse();
        // A hypothetical older major would also be incompatible under an exact-major-match policy.
        assertThat(SpiVersion.isCompatibleWith(">=" + (SpiVersion.MAJOR + 1) + ".0.0")).isFalse();
    }

    @Test
    void isCompatibleWithAcceptsLowerRequestedMinor() {
        assertThat(SpiVersion.isCompatibleWith(">=" + SpiVersion.MAJOR + "." + Math.max(0, SpiVersion.MINOR - 1) + ".999")).isTrue();
    }

    @Test
    void isCompatibleWithRejectsHigherRequestedMinor() {
        assertThat(SpiVersion.isCompatibleWith(">=" + SpiVersion.MAJOR + "." + (SpiVersion.MINOR + 1) + ".0")).isFalse();
    }

    @Test
    void isCompatibleWithSameMinorComparesPatch() {
        assertThat(SpiVersion.isCompatibleWith(">=" + SpiVersion.MAJOR + "." + SpiVersion.MINOR + "." + SpiVersion.PATCH))
            .as("equal patch is compatible")
            .isTrue();
        assertThat(SpiVersion.isCompatibleWith(">=" + SpiVersion.MAJOR + "." + SpiVersion.MINOR + "." + Math.max(0, SpiVersion.PATCH - 1)))
            .as("lower requested patch is compatible")
            .isTrue();
        assertThat(SpiVersion.isCompatibleWith(">=" + SpiVersion.MAJOR + "." + SpiVersion.MINOR + "." + (SpiVersion.PATCH + 1)))
            .as("higher requested patch is not yet satisfied")
            .isFalse();
    }

    @Test
    void isCompatibleWithRejectsNullRange() {
        assertThatNullPointerException().isThrownBy(() -> SpiVersion.isCompatibleWith(null));
    }

    @Test
    void isCompatibleWithThrowsOnMalformedRange() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SpiVersion.isCompatibleWith("0.1.0"))
            .withMessageContaining("0.1.0");
    }
}
