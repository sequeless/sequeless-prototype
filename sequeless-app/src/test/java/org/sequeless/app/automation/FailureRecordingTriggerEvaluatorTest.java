package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.ResolvedTimer;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TransitionFiredEvent;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.store.spi.AutomationFailure;
import org.sequeless.store.spi.AutomationFailureRecorder;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for {@link FailureRecordingTriggerEvaluator#onChange}. Outside a Temporal activity every failure is its final
 * attempt, so the wrapper records and swallows it here. The IT for the workflow itself is {@code
 * ChangeEventFailureRecordIT}.
 */
class FailureRecordingTriggerEvaluatorTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));

    @Test
    void anOnChangeFailureOnItsFinalAttemptIsRecordedOnceUnderTheEventIdAndSwallowed() {
        UUID eventId = UUID.randomUUID();
        ObjectChangedEvent event =
                new ObjectChangedEvent(eventId, ObjectChangedEvent.Change.UPDATED, Map.of(), Instant.now());
        RecordingFailureRecorder recorder = new RecordingFailureRecorder();
        FailureRecordingTriggerEvaluator wrapper = new FailureRecordingTriggerEvaluator(
                new FailingOnChange(), new TemporalFailureRecorder(providerOf(recorder), 3));

        // Swallowed: the call returns normally, so the change-event workflow ends normally.
        wrapper.onChange(SCOPE, event);

        assertThat(recorder.records).hasSize(1);
        AutomationFailure record = recorder.records.get(0);
        assertThat(record.workflowId()).isEqualTo(eventId.toString());
        assertThat(record.workflowType()).isEqualTo("ChangeEventWorkflow");
        assertThat(record.transition()).isNull();
        assertThat(record.cause()).isEqualTo("java.lang.IllegalStateException: change evaluation broke");
    }

    @Test
    void aNonFinalOnChangeFailureIsRethrownAndRecordsNothing() {
        // A recorder that is never asked, because the attempt is not final: a maximum of zero or less means no
        // attempt is ever final, so the failure is rethrown for Temporal to retry.
        RecordingFailureRecorder recorder = new RecordingFailureRecorder();
        FailureRecordingTriggerEvaluator wrapper = new FailureRecordingTriggerEvaluator(
                new FailingOnChange(), new TemporalFailureRecorder(providerOf(recorder), 0));
        ObjectChangedEvent event =
                new ObjectChangedEvent(UUID.randomUUID(), ObjectChangedEvent.Change.CREATED, Map.of(), Instant.now());

        assertThatThrownBy(() -> wrapper.onChange(SCOPE, event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("change evaluation broke");
        assertThat(recorder.records).isEmpty();
    }

    /** The released evaluator stand-in: its onChange always fails, and the other methods do nothing. */
    private static final class FailingOnChange implements TriggerEvaluator {

        @Override
        public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
            throw new IllegalStateException("change evaluation broke");
        }

        @Override
        public void onTimerElapsed(Scope scope, StoreEventRow timerScheduled) {}

        @Override
        public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
            return List.of();
        }
    }

    /** Keeps every failure the wrapper hands the host recorder. */
    private static final class RecordingFailureRecorder implements AutomationFailureRecorder {

        final List<AutomationFailure> records = new ArrayList<>();

        @Override
        public void record(AutomationFailure failure) {
            records.add(failure);
        }
    }

    private static ObjectProvider<AutomationFailureRecorder> providerOf(AutomationFailureRecorder recorder) {
        return new ObjectProvider<>() {
            @Override
            public AutomationFailureRecorder getIfAvailable() {
                return recorder;
            }
        };
    }
}
