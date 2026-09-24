package org.sequeless.adapter.automation.inprocess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A minimal controllable {@link Clock} for tests: holds a mutable {@link Instant}, advanced only
 * by an explicit call to {@link #advance(java.time.Duration)}, never by real wall-clock time. This
 * is what lets {@link InProcessAutomationPortContractTest#advanceTime} move {@link
 * InProcessTimerScheduler}'s notion of "now" forward deterministically, with no sleeping or
 * polling. Package-private: only this adapter module's own tests need it.
 */
final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    MutableClock(Instant initial) {
        this(initial, ZoneId.of("UTC"));
    }

    private MutableClock(Instant initial, ZoneId zone) {
        this.now = new AtomicReference<>(initial);
        this.zone = zone;
    }

    /** Moves this clock's current instant forward by {@code by}. */
    void advance(java.time.Duration by) {
        now.updateAndGet(instant -> instant.plus(by));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now.get(), zone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
