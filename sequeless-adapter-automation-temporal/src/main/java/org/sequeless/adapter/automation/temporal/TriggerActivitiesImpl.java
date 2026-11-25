package org.sequeless.adapter.automation.temporal;

import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The real {@link TriggerActivities} implementation: two pure pass-throughs to the injected {@link
 * TriggerEvaluator}. The derivation recompute runs in the outbox relay before ingest, not here.
 */
public final class TriggerActivitiesImpl implements TriggerActivities {

    private final TriggerEvaluator triggerEvaluator;

    /**
     * @param triggerEvaluator the port {@link #onChange}/{@link #onTimerElapsed} forward to; must
     *     not be {@code null}
     */
    public TriggerActivitiesImpl(TriggerEvaluator triggerEvaluator) {
        this.triggerEvaluator = Objects.requireNonNull(triggerEvaluator, "triggerEvaluator must not be null");
    }

    @Override
    public void onChange(Scope scope, ObjectChangedEvent changeEvent) {
        triggerEvaluator.onChange(scope, changeEvent);
    }

    @Override
    public void onTimerElapsed(Scope scope, OutboxEntry timerScheduled) {
        triggerEvaluator.onTimerElapsed(scope, timerScheduled);
    }
}
