package org.sequeless.adapter.automation.inprocess;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TimerScheduledEntry;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The in-process substitute for a durable timer engine (a future Temporal adapter's {@code
 * TimerWorkflow}): keeps every pending timer that a {@code TransitionFired} row starts in an
 * in-memory map keyed by {@link ResolvedTimer#timerKey()}, and fires {@link
 * TriggerEvaluator#onTimerElapsed} for whichever of them are due whenever {@link #sweep()} runs.
 * {@link InProcessAutomationPort} calls {@link #schedule} once for each timer the row starts, before
 * it applies the row's actions. There is no cancel: a timer runs to its due instant, and the
 * staleness check in {@link TriggerEvaluator#onTimerElapsed} is the only guard against firing after
 * the object has left the timer's state.
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

    /**
     * A pending timer: the {@link ResolvedTimer} it was scheduled from (which carries the timer key,
     * the state and the transition name), its due instant, and the object and scope the sweep needs
     * to rebuild the {@code TimerScheduled}-shaped entry at fire time.
     */
    private record ScheduledTimer(
            Instant dueAt, Scope scope, ObjectId objectId, String typeIri, int ontologyVersion, ResolvedTimer timer) {}

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
        this.triggerEvaluator = Objects.requireNonNull(triggerEvaluator, "triggerEvaluator must not be null");
    }

    /**
     * Records a pending timer that a {@code TransitionFired} row starts, due {@code timer.after()}
     * from {@link #clock}'s current instant. The timer is keyed by {@link ResolvedTimer#timerKey()}.
     *
     * <p>A {@code timerKey} that is already pending is left alone: the map uses {@code putIfAbsent},
     * so the original due instant survives. A {@code TransitionFired} row is redelivered when a retry
     * re-enters {@link InProcessAutomationPort#dispatchTransitionFired}, and this keeps such a retry
     * from moving the due time. A timer that {@link #sweep()} has already fired and removed is armed
     * again; the staleness check in {@link TriggerEvaluator#onTimerElapsed} makes that second firing
     * a no-op.
     *
     * @param scope the tenant and principal {@link #sweep()} fires {@code onTimerElapsed} under for
     *     this timer; must not be {@code null}
     * @param objectId the object the timer belongs to, from which {@link #sweep()} rebuilds the entry
     *     with {@link TimerScheduledEntry#of}; must not be {@code null}
     * @param typeIri the IRI of the object's type, for the same rebuilt entry; must not be {@code null}
     * @param ontologyVersion the ontology version the {@code TransitionFired} row is stamped with, for
     *     the same rebuilt entry
     * @param timer the timer the {@link TriggerEvaluator} resolved for that row; its key, duration,
     *     state and transition name are stored; must not be {@code null}
     */
    void schedule(Scope scope, ObjectId objectId, String typeIri, int ontologyVersion, ResolvedTimer timer) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(typeIri, "typeIri must not be null");
        Objects.requireNonNull(timer, "timer must not be null");
        pending.putIfAbsent(
                timer.timerKey(),
                new ScheduledTimer(
                        clock.instant().plus(timer.after()), scope, objectId, typeIri, ontologyVersion, timer));
    }

    /**
     * Fires {@link TriggerEvaluator#onTimerElapsed} for, and removes, every pending timer whose
     * {@code dueAt} is not after {@link #clock}'s current instant. The entry passed to {@code
     * onTimerElapsed} is a {@code TimerScheduled}-shaped entry rebuilt from the stored timer with
     * {@link TimerScheduledEntry#of}, and its {@code occurredAt} is the instant of this sweep. Called
     * both by a background tick (production) and directly, synchronously, by test code via {@code
     * advanceTime} (see this class's javadoc) — the same method serves both, since sweeping is
     * idempotent and cheap.
     */
    void sweep() {
        Instant now = clock.instant();
        pending.entrySet().removeIf(e -> {
            ScheduledTimer timer = e.getValue();
            if (!timer.dueAt().isAfter(now)) {
                OutboxEntry entry = TimerScheduledEntry.of(
                        timer.scope(), timer.objectId(), timer.typeIri(), timer.ontologyVersion(), timer.timer(), now);
                triggerEvaluator.onTimerElapsed(timer.scope(), entry);
                return true;
            }
            return false;
        });
    }
}
