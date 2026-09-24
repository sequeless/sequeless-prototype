package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The four Temporal activities {@link TimerWorkflowImpl}, {@link ChangeEventWorkflowImpl}, and
 * {@link SignalEventWorkflowImpl} call, one per {@link TriggerEvaluator}/{@link
 * DerivationRecomputer} inbound-port method — mirroring {@link ActionActivities}' one-interface,
 * thin-pass-through pattern exactly, so each method could later be given its own {@code
 * ActivityOptions} without touching the others. Today all four share the single {@code
 * ActivityOptions} {@link TemporalAutomationAutoConfiguration} builds from {@code
 * sequeless.automation.temporal.retry.*}, the same one {@link ActionActivities} shares.
 */
@ActivityInterface
public interface TriggerActivities {

    /** Runs one recompute pass. See {@link DerivationRecomputer#recompute}. */
    @ActivityMethod
    void recompute(Scope scope, OutboxEntry changeEvent);

    /** Evaluates on-change triggers. See {@link TriggerEvaluator#onChange}. */
    @ActivityMethod
    void onChange(Scope scope, OutboxEntry changeEvent);

    /** Evaluates an elapsed timer. See {@link TriggerEvaluator#onTimerElapsed}. */
    @ActivityMethod
    void onTimerElapsed(Scope scope, OutboxEntry timerScheduled);

    /** Evaluates a received signal. See {@link TriggerEvaluator#onSignal}. */
    @ActivityMethod
    void onSignal(Scope scope, OutboxEntry signalReceived);
}
