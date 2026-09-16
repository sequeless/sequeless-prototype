package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PageTest {

    @Test
    void carriesNumberAndSize() {
        Page page = new Page(0, 20);

        assertThat(page.number()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void rejectsNegativeNumber() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Page(-1, 20));
    }

    @Test
    void rejectsSizeZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Page(0, 0));
    }

    @Test
    void rejectsSizeAbove500() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Page(0, 501));
    }

    @Test
    void acceptsSizeOne() {
        assertThat(new Page(0, 1).size()).isEqualTo(1);
    }

    @Test
    void acceptsSize500() {
        assertThat(new Page(0, 500).size()).isEqualTo(500);
    }
}
