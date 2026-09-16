package org.sequeless.spi.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DisplayHintsTest {

    @Test
    void noneSortsLastHasNoGroupAndIsNotHidden() {
        DisplayHints hints = DisplayHints.none();

        assertThat(hints.order()).isEqualTo(Integer.MAX_VALUE);
        assertThat(hints.group()).isEmpty();
        assertThat(hints.hidden()).isFalse();
    }

    @Test
    void constructsWithExplicitValues() {
        DisplayHints hints = new DisplayHints(1, Optional.of("General"), true);

        assertThat(hints.order()).isEqualTo(1);
        assertThat(hints.group()).contains("General");
        assertThat(hints.hidden()).isTrue();
    }

    @Test
    void rejectsNullGroupWrapper() {
        assertThatNullPointerException().isThrownBy(() -> new DisplayHints(0, null, false));
    }
}
