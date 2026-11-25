package org.sequeless.spi.automation;

import java.time.Duration;
import java.util.Objects;

/**
 * A timer transition that a {@code TransitionFired} row starts, as resolved by {@link
 * TriggerEvaluator#timersStartedBy}: the timer a move into a state begins waiting in. An automation
 * adapter starts one durable timer per resolved value, keyed by {@link #timerKey()}.
 *
 * @param timerKey the stable key naming this timer, built by {@link
 *     org.sequeless.spi.outbox.OutboxEntry#timerKey}; must not be blank
 * @param after how long the timer waits from the moment it is started; must not be {@code null}
 * @param stateIri the state the timer waits in (the timer transition's {@code fromStateIri}); must
 *     not be blank
 * @param transitionName the name of the timer transition; must not be blank
 */
public record ResolvedTimer(String timerKey, Duration after, String stateIri, String transitionName) {

    public ResolvedTimer {
        if (timerKey == null || timerKey.isBlank()) {
            throw new IllegalArgumentException("ResolvedTimer timerKey must not be blank");
        }
        Objects.requireNonNull(after, "after must not be null");
        if (stateIri == null || stateIri.isBlank()) {
            throw new IllegalArgumentException("ResolvedTimer stateIri must not be blank");
        }
        if (transitionName == null || transitionName.isBlank()) {
            throw new IllegalArgumentException("ResolvedTimer transitionName must not be blank");
        }
    }
}
