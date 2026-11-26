package org.sequeless.adapter.automation.inprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * Proves that one failing timer cannot stop {@link InProcessTimerScheduler#sweep()} or the other due
 * timers, and that a failed timer stays pending until a later sweep fires it.
 */
class InProcessTimerSchedulerTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final String TYPE_IRI = "https://example.org/ns#Project";
    private static final String STATE_IRI = "https://example.org/ns#Active";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final TimerEvaluator evaluator = new TimerEvaluator();
    private final InProcessTimerScheduler scheduler = new InProcessTimerScheduler(clock, evaluator);

    @Test
    void aThrowingTimerDoesNotStopTheSweepOrTheOtherDueTimers() {
        schedule("a");
        schedule("b");
        evaluator.failNext("a", 1);
        clock.advance(Duration.ofSeconds(2));

        assertThatCode(scheduler::sweep).doesNotThrowAnyException();

        assertThat(evaluator.attempts).containsExactlyInAnyOrder("a", "b");
        assertThat(evaluator.completions).containsExactly("b");
    }

    @Test
    void aFailedTimerStaysPendingAndTheNextSweepRetriesIt() {
        schedule("a");
        evaluator.failNext("a", 1);
        clock.advance(Duration.ofSeconds(2));

        scheduler.sweep();
        scheduler.sweep();

        assertThat(evaluator.attempts).containsExactly("a", "a");
        assertThat(evaluator.completions).containsExactly("a");
    }

    @Test
    void aTimerThatElapsedIsNotRunAgain() {
        schedule("a");
        clock.advance(Duration.ofSeconds(2));

        scheduler.sweep();
        scheduler.sweep();

        assertThat(evaluator.attempts).containsExactly("a");
    }

    @Test
    void aSweepReturnsNormallyWhenEveryDueTimerFails() {
        schedule("a");
        schedule("b");
        evaluator.failNext("a", 5);
        evaluator.failNext("b", 5);
        clock.advance(Duration.ofSeconds(2));

        assertThatCode(scheduler::sweep).doesNotThrowAnyException();
        assertThatCode(scheduler::sweep).doesNotThrowAnyException();

        assertThat(evaluator.attempts).containsExactlyInAnyOrder("a", "a", "b", "b");
        assertThat(evaluator.completions).isEmpty();
    }

    @Test
    void aTimerThatIsNotYetDueIsNotAttempted() {
        schedule("a");

        scheduler.sweep();

        assertThat(evaluator.attempts).isEmpty();
    }

    private void schedule(String timerKey) {
        scheduler.schedule(
                SCOPE,
                ObjectId.random(),
                TYPE_IRI,
                1,
                new ResolvedTimer(timerKey, Duration.ofSeconds(1), STATE_IRI, "expire"));
    }

    /**
     * Records each attempted and each completed {@code onTimerElapsed}, by timer key, and throws for the
     * next {@link #failNext} attempts of a given key.
     */
    private static final class TimerEvaluator implements TriggerEvaluator {

        private final List<String> attempts = new ArrayList<>();
        private final List<String> completions = new ArrayList<>();
        private final Map<String, Integer> failuresLeft = new HashMap<>();

        void failNext(String timerKey, int times) {
            failuresLeft.put(timerKey, times);
        }

        @Override
        public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
            String timerKey = (String) timerScheduled.payload().get("timerKey");
            attempts.add(timerKey);
            if (failuresLeft.getOrDefault(timerKey, 0) > 0) {
                failuresLeft.merge(timerKey, -1, Integer::sum);
                throw new IllegalStateException("onTimerElapsed failed for " + timerKey);
            }
            completions.add(timerKey);
        }

        @Override
        public void onChange(Scope scope, ObjectChangedEvent changeEvent) {}

        @Override
        public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
            return List.of();
        }
    }
}
