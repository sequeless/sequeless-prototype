package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeleteTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    @Test
    void carriesAllFields() {
        ObjectId id = ObjectId.random();
        Delete delete = new Delete(id, 2, NOW, "alice");

        assertThat(delete.id()).isEqualTo(id);
        assertThat(delete.expectedVersion()).isEqualTo(2);
        assertThat(delete.at()).isEqualTo(NOW);
        assertThat(delete.by()).isEqualTo("alice");
    }

    @Test
    void rejectsNullId() {
        assertThatNullPointerException().isThrownBy(() -> new Delete(null, 1, NOW, "alice"));
    }

    @Test
    void rejectsExpectedVersionLessThanOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Delete(ObjectId.random(), 0, NOW, "alice"));
    }

    @Test
    void rejectsNullAt() {
        assertThatNullPointerException().isThrownBy(() -> new Delete(ObjectId.random(), 1, null, "alice"));
    }

    @Test
    void rejectsBlankBy() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Delete(ObjectId.random(), 1, NOW, "  "));
    }
}
