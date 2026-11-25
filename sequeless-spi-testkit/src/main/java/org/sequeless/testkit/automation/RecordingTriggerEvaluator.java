package org.sequeless.testkit.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * A hand-rolled {@link TriggerEvaluator} test double (this codebase uses no Mockito), mirroring
 * {@link RecordingActionExecutor}'s exact idiom: it records every call it receives instead of
 * doing anything else, so a contract test — or an adapter's own test — can assert which method
 * {@link org.sequeless.spi.automation.AutomationPort#dispatch} actually invoked, and how many
 * times.
 *
 * <p>Each recorded {@link Call} additionally carries {@link Call#sequenceNanos()}, a {@link
 * System#nanoTime()} reading taken at record time. This exists purely so {@link
 * AutomationContract} can prove relative call order between this double and a separately
 * constructed {@link RecordingDerivationRecomputer} — for example, that a single change event's
 * recompute call happened before its onChange call — without the two doubles needing to share any
 * constructor-injected state; comparing two {@code sequenceNanos()} readings taken on the same JVM
 * is sufficient for a relative before/after assertion within one test.
 *
 * <p>Backed by a thread-safe list: a future Temporal activity implementation may call back into
 * this evaluator off the dispatching thread, so {@link #calls()} must remain safe to read and
 * write concurrently.
 */
public final class RecordingTriggerEvaluator implements TriggerEvaluator {

    /** The method name recorded for a call to {@link TriggerEvaluator#onChange}. */
    public static final String METHOD_ON_CHANGE = "onChange";

    /** The method name recorded for a call to {@link TriggerEvaluator#onTimerElapsed}. */
    public static final String METHOD_ON_TIMER_ELAPSED = "onTimerElapsed";

    /** The method name recorded for a call to {@link TriggerEvaluator#onSignal}. */
    public static final String METHOD_ON_SIGNAL = "onSignal";

    /** The method name recorded for a call to {@link TriggerEvaluator#timersStartedBy}. */
    public static final String METHOD_TIMERS_STARTED_BY = "timersStartedBy";

    /**
     * One recorded {@link TriggerEvaluator} call.
     *
     * @param method one of {@link #METHOD_ON_CHANGE}, {@link #METHOD_ON_TIMER_ELAPSED}, {@link
     *     #METHOD_ON_SIGNAL}, or {@link #METHOD_TIMERS_STARTED_BY}
     * @param entryId the {@link OutboxEntry#id()} the call was made with
     * @param sequenceNanos a {@link System#nanoTime()} reading taken when the call was recorded,
     *     usable only for relative ordering within a single JVM run, never as a wall-clock value
     */
    public record Call(String method, UUID entryId, long sequenceNanos) {}

    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    /** The timers {@link #timersStartedBy} returns; empty until a test sets them with {@link #setTimersStartedBy}. */
    private volatile List<ResolvedTimer> timers = List.of();

    @Override
    public void onChange(Scope scope, OutboxEntry changeEvent) {
        record(METHOD_ON_CHANGE, changeEvent);
    }

    @Override
    public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
        record(METHOD_ON_TIMER_ELAPSED, timerScheduled);
    }

    @Override
    public void onSignal(Scope scope, OutboxEntry signalReceived) {
        record(METHOD_ON_SIGNAL, signalReceived);
    }

    /**
     * Records the call under {@link #METHOD_TIMERS_STARTED_BY}, then returns the timers most recently
     * set with {@link #setTimersStartedBy}, or none if no test has set any.
     */
    @Override
    public List<ResolvedTimer> timersStartedBy(Scope scope, OutboxEntry transitionFired) {
        record(METHOD_TIMERS_STARTED_BY, transitionFired);
        return timers;
    }

    /**
     * Sets the timers that every later {@link #timersStartedBy} call returns, replacing any set before.
     * A contract test calls this before dispatching a {@code TransitionFired} row, so that the row
     * starts the timers the test needs. Until it is called, the double resolves no timers.
     *
     * @param timers the timers to return, in the order they should be started; must not be {@code null}
     */
    public void setTimersStartedBy(List<ResolvedTimer> timers) {
        this.timers = List.copyOf(Objects.requireNonNull(timers, "timers must not be null"));
    }

    /** @return every call recorded so far, in invocation order; a snapshot, not a live view */
    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    /**
     * @param method one of {@link #METHOD_ON_CHANGE}, {@link #METHOD_ON_TIMER_ELAPSED}, or {@link
     *     #METHOD_ON_SIGNAL}
     * @return how many recorded calls have {@link Call#method()} equal to {@code method}
     */
    public long countOf(String method) {
        return calls().stream().filter(call -> call.method().equals(method)).count();
    }

    private void record(String method, OutboxEntry entry) {
        calls.add(new Call(method, entry.id(), System.nanoTime()));
    }
}
