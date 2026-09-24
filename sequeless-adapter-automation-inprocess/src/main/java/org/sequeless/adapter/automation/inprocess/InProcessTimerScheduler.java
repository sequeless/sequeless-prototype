package org.sequeless.adapter.automation.inprocess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The in-process substitute for a durable timer engine (a future Temporal adapter's {@code
 * TimerWorkflow}): keeps every pending {@link OutboxEntry#KIND_TIMER_SCHEDULED} entry in an
 * in-memory map keyed by its payload's {@code timerKey}, and fires {@link
 * TriggerEvaluator#onTimerElapsed} for whichever of them are due whenever {@link #sweep()} runs.
 *
 * <p><b>Not crash-safe</b> — a process restart forgets every pending timer, exactly like {@link
 * InProcessAutomationPort}'s own idempotency set. This is an accepted limitation of the in-process
 * adapter, which exists only for tests and local development; durability across restarts is a
 * durable engine's job.
 *
 * <p>The {@link Clock} is constructor-injected specifically so tests can substitute a mutable fake
 * and call {@link #sweep()} synchronously after advancing it — see {@code
 * AutomationContract#advanceTime}'s contract, which this class's test wiring exists to satisfy: no
 * sleeping, no polling, deterministic firing the instant a due timer's time has passed.
 */
final class InProcessTimerScheduler {

    private record ScheduledTimer(Instant dueAt, Scope scope, OutboxEntry entry) {}

    private final Clock clock;
    private final TriggerEvaluator triggerEvaluator;
    private final Map<String, ScheduledTimer> pending = new ConcurrentHashMap<>();

    /**
     * @param clock this scheduler's notion of "now"; {@link Clock#systemUTC()} in production, a
     *     mutable fake in tests; must not be {@code null}
     * @param triggerEvaluator the port {@link #sweep()} calls {@link
     *     TriggerEvaluator#onTimerElapsed} on for each due timer; must not be {@code null}
     */
    InProcessTimerScheduler(Clock clock, TriggerEvaluator triggerEvaluator) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.triggerEvaluator =
            Objects.requireNonNull(triggerEvaluator, "triggerEvaluator must not be null");
    }

    /**
     * Records a pending timer from a {@link OutboxEntry#KIND_TIMER_SCHEDULED} entry, due {@code
     * after} (parsed from the payload's ISO-8601 {@code after} field) from {@link #clock}'s current
     * instant. Re-scheduling the same {@code timerKey} (e.g. a redispatch of the identical entry)
     * simply overwrites the previous pending timer with an identical one — harmless, since both
     * describe the same due instant relative to when they were first scheduled... callers are
     * expected to have already de-duplicated by {@link OutboxEntry#id()} before calling this, which
     * {@link InProcessAutomationPort} does.
     *
     * @param scope the tenant/principal {@link #sweep()} should fire {@code onTimerElapsed} under
     *     for this timer; must not be {@code null}
     * @param entry the {@code TimerScheduled}-kind outbox entry; must not be {@code null}
     */
    void schedule(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        String timerKey = (String) entry.payload().get("timerKey");
        Duration after = Duration.parse((String) entry.payload().get("after"));
        pending.put(timerKey, new ScheduledTimer(clock.instant().plus(after), scope, entry));
    }

    /**
     * Removes the pending timer named by {@code cancelledEntry}'s payload {@code timerKey}, if any.
     * A cancel for a {@code timerKey} with no pending timer (already fired, already cancelled, or
     * never scheduled on this process) is a silent no-op.
     *
     * @param cancelledEntry the {@code TimerCancelled}-kind outbox entry; must not be {@code null}
     */
    void cancel(OutboxEntry cancelledEntry) {
        Objects.requireNonNull(cancelledEntry, "cancelledEntry must not be null");
        pending.remove((String) cancelledEntry.payload().get("timerKey"));
    }

    /**
     * Fires {@link TriggerEvaluator#onTimerElapsed} for, and removes, every pending timer whose
     * {@code dueAt} is not after {@link #clock}'s current instant. Called both by a background tick
     * (production) and directly, synchronously, by test code via {@code advanceTime} (see this
     * class's javadoc) — the same method serves both, since sweeping is idempotent and cheap.
     */
    void sweep() {
        Instant now = clock.instant();
        pending
            .entrySet()
            .removeIf(
                e -> {
                    ScheduledTimer timer = e.getValue();
                    if (!timer.dueAt().isAfter(now)) {
                        triggerEvaluator.onTimerElapsed(timer.scope(), timer.entry());
                        return true;
                    }
                    return false;
                });
    }
}
