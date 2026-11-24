package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The real {@link TimerWorkflow} implementation. It reads {@link TemporalActivityOptions#ACTIVITY_OPTIONS},
 * which explains why.
 */
public final class TimerWorkflowImpl implements TimerWorkflow {

    private boolean cancelled = false;
    private boolean awaiting = false;

    @Override
    public void run(OutboxEntry entry, Scope scope) {
        ActivityOptions activityOptions = TemporalActivityOptions.ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException("TemporalActivityOptions.ACTIVITY_OPTIONS was never set - "
                    + "TemporalAutomationAutoConfiguration must assign it before starting the worker");
        }
        TriggerActivities activities = Workflow.newActivityStub(TriggerActivities.class, activityOptions);

        Duration after = Duration.parse((String) entry.payload().get("after"));

        awaiting = true;
        // Workflow.await(Duration, Supplier<Boolean>) returns true if the condition became true
        // before the timeout elapsed, false if the timeout elapsed first - see the SDK's own
        // WorkflowInternal#await implementation, verified via javap against temporal-sdk-1.39.0.jar.
        boolean cancelledBeforeExpiry = Workflow.await(after, () -> cancelled);
        if (!cancelledBeforeExpiry) {
            activities.onTimerElapsed(scope, entry);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public boolean isAwaiting() {
        return awaiting;
    }
}
