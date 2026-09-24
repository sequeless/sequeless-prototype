package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The real {@link ChangeEventWorkflow} implementation. See {@link TimerWorkflowImpl}'s javadoc for
 * why this reads {@link ActionWorkflowImpl#ACTIVITY_OPTIONS} rather than taking its own
 * constructor-injected copy.
 */
public final class ChangeEventWorkflowImpl implements ChangeEventWorkflow {

    @Override
    public void run(OutboxEntry entry, Scope scope) {
        ActivityOptions activityOptions = ActionWorkflowImpl.ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException(
                "ActionWorkflowImpl.ACTIVITY_OPTIONS was never set - "
                    + "TemporalAutomationAutoConfiguration must assign it before starting the worker");
        }
        TriggerActivities activities = Workflow.newActivityStub(TriggerActivities.class, activityOptions);
        activities.recompute(scope, entry);
        activities.onChange(scope, entry);
    }
}
