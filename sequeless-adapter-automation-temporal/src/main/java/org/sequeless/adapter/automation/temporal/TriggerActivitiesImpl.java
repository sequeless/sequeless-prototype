package org.sequeless.adapter.automation.temporal;

import java.util.Objects;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The real {@link TriggerActivities} implementation: four pure pass-throughs to the injected
 * {@link TriggerEvaluator}/{@link DerivationRecomputer}, exactly mirroring {@link
 * ActionActivitiesImpl}'s pattern. Any {@code StaleObjectException} {@link
 * DerivationRecomputer#recompute} lets propagate (after its own intra-call retry loop is
 * exhausted) simply propagates out of this activity method too, letting Temporal's configured
 * {@code RetryOptions} retry the activity invocation as a whole — the same "core retries first,
 * Temporal retries the activity as a whole on top" layering {@link ActionActivitiesImpl#executeWebhook}
 * already relies on.
 */
public final class TriggerActivitiesImpl implements TriggerActivities {

    private final TriggerEvaluator triggerEvaluator;
    private final DerivationRecomputer derivationRecomputer;

    /**
     * @param triggerEvaluator the port {@link #onChange}/{@link #onTimerElapsed}/{@link #onSignal}
     *     forward to; must not be {@code null}
     * @param derivationRecomputer the port {@link #recompute} forwards to; must not be {@code null}
     */
    public TriggerActivitiesImpl(TriggerEvaluator triggerEvaluator, DerivationRecomputer derivationRecomputer) {
        this.triggerEvaluator = Objects.requireNonNull(triggerEvaluator, "triggerEvaluator must not be null");
        this.derivationRecomputer =
            Objects.requireNonNull(derivationRecomputer, "derivationRecomputer must not be null");
    }

    @Override
    public void recompute(Scope scope, OutboxEntry changeEvent) {
        derivationRecomputer.recompute(scope, changeEvent);
    }

    @Override
    public void onChange(Scope scope, OutboxEntry changeEvent) {
        triggerEvaluator.onChange(scope, changeEvent);
    }

    @Override
    public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
        triggerEvaluator.onTimerElapsed(scope, timerScheduled);
    }

    @Override
    public void onSignal(Scope scope, OutboxEntry signalReceived) {
        triggerEvaluator.onSignal(scope, signalReceived);
    }
}
