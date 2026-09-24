package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/** The real {@link SignalEventWorkflow} implementation. See {@link TimerWorkflowImpl}'s javadoc. */
public final class SignalEventWorkflowImpl implements SignalEventWorkflow {

    @Override
    public void run(OutboxEntry entry, Scope scope) {
        ActivityOptions activityOptions = ActionWorkflowImpl.ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException(
                "ActionWorkflowImpl.ACTIVITY_OPTIONS was never set - "
                    + "TemporalAutomationAutoConfiguration must assign it before starting the worker");
        }
        TriggerActivities activities = Workflow.newActivityStub(TriggerActivities.class, activityOptions);
        activities.onSignal(scope, entry);
    }
}
