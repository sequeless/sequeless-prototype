package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.temporal.activity.ActivityOptions;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.TimerScheduledEntry;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * Each workflow implementation that reads {@link TemporalActivityOptions#ACTIVITY_OPTIONS} throws an
 * {@link IllegalStateException} naming the holder when the holder is still {@code null}. These tests
 * call each implementation directly, outside any {@code TestWorkflowEnvironment}, because the check
 * runs before any activity stub is built. A reader that is missed by the holder move would otherwise
 * fail only at workflow run time.
 *
 * <p>Each test saves the holder and restores it in a {@code finally} block, because the other test
 * classes in this package set it in their {@code @BeforeAll} methods.
 */
class WorkflowHolderNullTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final OutboxEntry ENTRY = new OutboxEntry(
            UUID.randomUUID(),
            TimerScheduledEntry.KIND,
            Map.of("objectId", UUID.randomUUID().toString()),
            Instant.parse("2026-01-01T00:00:00Z"));
    private static final ObjectChangedEvent CHANGE = new ObjectChangedEvent(
            UUID.randomUUID(),
            ObjectChangedEvent.Change.CREATED,
            Map.of("objectId", UUID.randomUUID().toString()),
            Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void changeEventWorkflowRefusesANullHolder() {
        assertRefusesANullHolder(() -> new ChangeEventWorkflowImpl().run(CHANGE, SCOPE));
    }

    @Test
    void timerWorkflowRefusesANullHolder() {
        assertRefusesANullHolder(() -> new TimerWorkflowImpl().run(ENTRY, SCOPE));
    }

    @Test
    void transitionActionWorkflowRefusesANullHolder() {
        assertRefusesANullHolder(() -> new TransitionActionWorkflowImpl().execute(ENTRY, 0));
    }

    private static void assertRefusesANullHolder(Runnable call) {
        ActivityOptions saved = TemporalActivityOptions.ACTIVITY_OPTIONS;
        TemporalActivityOptions.ACTIVITY_OPTIONS = null;
        try {
            assertThatThrownBy(call::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TemporalActivityOptions.ACTIVITY_OPTIONS was never set");
        } finally {
            TemporalActivityOptions.ACTIVITY_OPTIONS = saved;
        }
    }
}
