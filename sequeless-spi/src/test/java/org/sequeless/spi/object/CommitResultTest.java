package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.TenantId;

class CommitResultTest {

    private static BusinessObject sampleObject() {
        Instant now = Instant.parse("2026-09-16T10:00:00Z");
        Audit audit = new Audit(now, "alice", now, "alice");
        return new BusinessObject(
            ObjectId.random(), new TypeRef("https://example.org/ns#Task"), TenantId.DEFAULT, 1,
            Optional.empty(), Map.of(), audit, false);
    }

    @Test
    void carriesObjectsAndOutboxIds() {
        BusinessObject object = sampleObject();
        UUID outboxId = UUID.randomUUID();

        CommitResult result = new CommitResult(List.of(object), List.of(outboxId));

        assertThat(result.objects()).containsExactly(object);
        assertThat(result.outboxIds()).containsExactly(outboxId);
    }

    @Test
    void rejectsNullObjects() {
        assertThatNullPointerException().isThrownBy(() -> new CommitResult(null, List.of()));
    }

    @Test
    void rejectsNullOutboxIds() {
        assertThatNullPointerException().isThrownBy(() -> new CommitResult(List.of(sampleObject()), null));
    }
}
