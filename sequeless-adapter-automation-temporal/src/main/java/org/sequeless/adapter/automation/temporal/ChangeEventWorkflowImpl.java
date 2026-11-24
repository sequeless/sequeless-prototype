package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The real {@link ChangeEventWorkflow} implementation. It reads {@link
 * TemporalActivityOptions#ACTIVITY_OPTIONS}, which explains why.
 */
public final class ChangeEventWorkflowImpl implements ChangeEventWorkflow {

    @Override
    public void run(OutboxEntry entry, Scope scope) {
        ActivityOptions activityOptions = TemporalActivityOptions.ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException("TemporalActivityOptions.ACTIVITY_OPTIONS was never set - "
                    + "TemporalAutomationAutoConfiguration must assign it before starting the worker");
        }
        TriggerActivities activities = Workflow.newActivityStub(TriggerActivities.class, activityOptions);
        activities.recompute(scope, entry);
        activities.onChange(scope, entry);
    }
}
