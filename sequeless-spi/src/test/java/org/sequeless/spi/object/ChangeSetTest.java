package org.sequeless.spi.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.TenantId;

class ChangeSetTest {

    private static Mutation createMutation() {
        Instant now = Instant.parse("2026-09-16T10:00:00Z");
        Audit audit = new Audit(now, "alice", now, "alice");
        BusinessObject object = new BusinessObject(
            ObjectId.random(), new TypeRef("https://example.org/ns#Task"), TenantId.DEFAULT, 1,
            Optional.empty(), Map.of(), audit, false);
        return new Create(object);
    }

    @Test
    void carriesMutationsAndOutbox() {
        Mutation mutation = createMutation();
        OutboxEntry outboxEntry = new OutboxEntry(
            UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, Map.of("objectId", "x"),
            Instant.parse("2026-09-16T10:00:00Z"));

        ChangeSet changeSet = new ChangeSet(List.of(mutation), List.of(outboxEntry));

        assertThat(changeSet.mutations()).containsExactly(mutation);
        assertThat(changeSet.outbox()).containsExactly(outboxEntry);
    }

    @Test
    void rejectsEmptyMutations() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ChangeSet(List.of(), List.of()));
    }

    @Test
    void rejectsNullMutations() {
        assertThatNullPointerException().isThrownBy(() -> new ChangeSet(null, List.of()));
    }

    @Test
    void rejectsNullOutbox() {
        assertThatNullPointerException().isThrownBy(() -> new ChangeSet(List.of(createMutation()), null));
    }

    @Test
    void allowsEmptyOutbox() {
        ChangeSet changeSet = new ChangeSet(List.of(createMutation()), List.of());

        assertThat(changeSet.outbox()).isEmpty();
    }
}
