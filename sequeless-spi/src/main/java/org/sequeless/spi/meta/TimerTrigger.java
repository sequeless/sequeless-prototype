package org.sequeless.spi.meta;

import java.time.Duration;
import java.util.Objects;

/**
 * A {@link TriggerSpec} that fires a {@link Transition} once {@link #after()} has elapsed since the
 * object entered {@link Transition#fromStateIri()} ({@code sq:Timer}, {@code sq:after} as an
 * ISO-8601 duration), exactly as {@code expireHold} moves an {@code ex:Project} from {@code
 * ex:OnHold} to {@code ex:Closed} 72 hours (encoded {@code "PT72H"}) after it entered {@code
 * ex:OnHold} — a duration that is ontology data, editable without a rebuild, not a compiled-in
 * constant. Every successful transition into {@link Transition#fromStateIri()} schedules a
 * corresponding {@code TimerScheduled} outbox entry, and every transition departing that state
 * (including this one firing) writes a matching {@code TimerCancelled} entry, so a timer that races
 * a state change never fires twice or against the wrong state — see {@code OutboxEntry}'s {@code
 * KIND_TIMER_SCHEDULED}/{@code KIND_TIMER_CANCELLED} constants.
 *
 * @param after the duration to wait, measured from the moment the object entered {@link
 *     Transition#fromStateIri()} ({@code sq:after}); must not be {@code null}; must be positive —
 *     zero or negative would mean "immediately" or "in the past", neither of which a durable timer
 *     workflow can represent
 */
public record TimerTrigger(Duration after) implements TriggerSpec {

    public TimerTrigger {
        Objects.requireNonNull(after, "after must not be null");
        if (after.isZero() || after.isNegative()) {
            throw new IllegalArgumentException("TimerTrigger after must be positive");
        }
    }

    @Override
    public TriggerKind kind() {
        return TriggerKind.TIMER;
    }
}
