package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void carriesAllFields() {
        PageResult<String> result = new PageResult<>(List.of("a"), 0, 20, 1);

        assertThat(result.items()).containsExactly("a");
        assertThat(result.number()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalItems()).isEqualTo(1);
    }

    @Test
    void totalPagesIsZeroWhenTotalItemsIsZero() {
        assertThat(new PageResult<>(List.of(), 0, 20, 0).totalPages()).isEqualTo(0);
    }

    @Test
    void totalPagesIsOneForASingleItem() {
        assertThat(new PageResult<>(List.of("a"), 0, 20, 1).totalPages()).isEqualTo(1);
    }

    @Test
    void totalPagesRoundsUpAtTheBoundary() {
        assertThat(new PageResult<>(List.of(), 0, 20, 41).totalPages()).isEqualTo(3);
        assertThat(new PageResult<>(List.of(), 0, 20, 42).totalPages()).isEqualTo(3);
    }

    @Test
    void itemsAreUnmodifiable() {
        PageResult<String> result = new PageResult<>(List.of("a"), 0, 20, 1);

        assertThatThrownBy(() -> result.items().add("b")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullItems() {
        assertThatNullPointerException().isThrownBy(() -> new PageResult<>(null, 0, 20, 0));
    }

    @Test
    void rejectsNegativeNumber() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), -1, 20, 0));
    }

    @Test
    void rejectsSizeLessThanOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), 0, 0, 0));
    }

    @Test
    void rejectsNegativeTotalItems() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), 0, 20, -1));
    }
}
