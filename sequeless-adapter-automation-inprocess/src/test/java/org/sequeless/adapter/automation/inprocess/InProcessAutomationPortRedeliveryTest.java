package org.sequeless.adapter.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.testkit.automation.RecordingActionExecutor;

/**
 * Proves that a redelivered {@link ObjectChangedEvent} whose first {@code onChange} threw runs {@code
 * onChange} again, and that one whose {@code onChange} succeeded is not run a second time. The relay
 * redelivers a row whose handler threw, so {@link InProcessAutomationPort#ingest} must record an id only
 * after its {@code onChange} has returned normally.
 */
class InProcessAutomationPortRedeliveryTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));

    private final ChangeEvaluator evaluator = new ChangeEvaluator();
    private final InProcessAutomationPort port = new InProcessAutomationPort(
            new RecordingActionExecutor(),
            new InProcessAutomationProperties(),
            evaluator,
            new InProcessTimerScheduler(new MutableClock(Instant.parse("2026-01-01T00:00:00Z")), evaluator),
            tenant -> new Automation(new OntologyVersion(1), Map.of()));

    @Test
    void failedObjectChangedRedeliveryRunsOnChangeAgain() {
        ObjectChangedEvent event = created();
        evaluator.failNext(1);

        assertThatThrownBy(() -> port.ingest(SCOPE, event)).isInstanceOf(IllegalStateException.class);
        port.ingest(SCOPE, event);

        assertThat(evaluator.changeCalls).isEqualTo(2);
    }

    @Test
    void repeatedFailuresKeepRunningOnChangeUntilItSucceeds() {
        ObjectChangedEvent event = created();
        evaluator.failNext(2);

        assertThatThrownBy(() -> port.ingest(SCOPE, event)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> port.ingest(SCOPE, event)).isInstanceOf(IllegalStateException.class);
        port.ingest(SCOPE, event);

        assertThat(evaluator.changeCalls).isEqualTo(3);
    }

    @Test
    void objectChangedIsNotRunAgainOnceItHasSucceeded() {
        ObjectChangedEvent event = created();

        port.ingest(SCOPE, event);
        port.ingest(SCOPE, event);

        assertThat(evaluator.changeCalls).isEqualTo(1);
    }

    private static ObjectChangedEvent created() {
        return new ObjectChangedEvent(
                UUID.randomUUID(), ObjectChangedEvent.Change.CREATED, Map.of(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    /** Counts every {@code onChange} call, and throws for the next {@link #failNext} of them. */
    private static final class ChangeEvaluator implements TriggerEvaluator {

        private int changeCalls;
        private int failuresLeft;

        void failNext(int times) {
            failuresLeft = times;
        }

        @Override
        public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
            changeCalls++;
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new IllegalStateException("onChange failed for " + changeEvent.id());
            }
        }

        @Override
        public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {}

        @Override
        public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
            return List.of();
        }
    }
}
