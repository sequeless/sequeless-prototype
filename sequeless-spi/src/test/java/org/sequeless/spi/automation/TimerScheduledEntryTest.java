package org.sequeless.spi.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.spi.outbox.OutboxEntry;

class TimerScheduledEntryTest {

    private static final Scope ALICE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    @Test
    void kindIsTheTimerScheduledString() {
        assertThat(TimerScheduledEntry.KIND).isEqualTo("TimerScheduled");
    }

    @Test
    void ofBuildsTheTimerScheduledPayloadKeysAndValues() {
        ObjectId objectId = ObjectId.random();
        ResolvedTimer timer = new ResolvedTimer(
                OutboxEntry.timerKey(objectId, "ex:Waiting", "expire"), Duration.ofHours(72), "ex:Waiting", "expire");

        OutboxEntry entry = TimerScheduledEntry.of(ALICE, objectId, "ex:Project", 1, timer, NOW);

        assertThat(entry.kind()).isEqualTo(TimerScheduledEntry.KIND);
        assertThat(entry.occurredAt()).isEqualTo(NOW);
        Map<String, Object> payload = entry.payload();
        assertThat(payload)
                .containsEntry("objectId", objectId.value().toString())
                .containsEntry("tenantId", "acme")
                .containsEntry("principalId", "alice")
                .containsEntry("typeIri", "ex:Project")
                .containsEntry("state", "ex:Waiting")
                .containsEntry("transitionName", "expire")
                .containsEntry("after", "PT72H")
                .containsEntry("timerKey", timer.timerKey())
                .containsEntry("ontologyVersion", 1);
        assertThat(payload).hasSize(9);
    }
}
