package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditTest {

    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    @Test
    void carriesAllFields() {
        Audit audit = new Audit(NOW, "alice", NOW, "bob");

        assertThat(audit.createdAt()).isEqualTo(NOW);
        assertThat(audit.createdBy()).isEqualTo("alice");
        assertThat(audit.updatedAt()).isEqualTo(NOW);
        assertThat(audit.updatedBy()).isEqualTo("bob");
    }

    @Test
    void rejectsNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() -> new Audit(null, "alice", NOW, "bob"));
    }

    @Test
    void rejectsBlankCreatedBy() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Audit(NOW, "  ", NOW, "bob"));
    }

    @Test
    void rejectsNullUpdatedAt() {
        assertThatNullPointerException().isThrownBy(() -> new Audit(NOW, "alice", null, "bob"));
    }

    @Test
    void rejectsBlankUpdatedBy() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Audit(NOW, "alice", NOW, "  "));
    }
}
