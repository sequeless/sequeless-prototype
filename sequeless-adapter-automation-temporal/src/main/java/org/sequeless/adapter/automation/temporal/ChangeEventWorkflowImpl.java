package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.automation.AutomationFailure;
import org.sequeless.spi.automation.ObjectChangedEvent;

/**
 * The real {@link ChangeEventWorkflow} implementation. It reads {@link
 * TemporalActivityOptions#ACTIVITY_OPTIONS}, which explains why.
 *
 * <p><b>Failure path (spec 13, R2).</b> Temporal's retry policy retries {@link TriggerActivities#onChange}.
 * Once the retries are exhausted, the {@link ActivityFailure} is caught here, and the workflow writes one
 * {@link AutomationFailure} record through {@link AutomationFailureActivities#recordFailure}, then ends
 * normally. Before this, the failure reached no one: the workflow failed silently and the outbox row was
 * already marked processed. A cancellation is not recorded, and it propagates. A failure of the record
 * write itself propagates too, so the execution shows as failed.
 */
public final class ChangeEventWorkflowImpl implements ChangeEventWorkflow {

    /** The workflow type written on a failure record. Equals the interface's simple name. */
    static final String WORKFLOW_TYPE = "ChangeEventWorkflow";

    @Override
    public void run(ObjectChangedEvent event, Scope scope) {
        ActivityOptions activityOptions = TemporalActivityOptions.ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException("TemporalActivityOptions.ACTIVITY_OPTIONS was never set - "
                    + "TemporalAutomationAutoConfiguration must assign it before starting the worker");
        }
        TriggerActivities activities = Workflow.newActivityStub(TriggerActivities.class, activityOptions);
        try {
            activities.onChange(scope, event);
        } catch (ActivityFailure failure) {
            if (ActivityFailureCauses.isCancellation(failure)) {
                throw failure;
            }
            AutomationFailureActivities failures =
                    Workflow.newActivityStub(AutomationFailureActivities.class, activityOptions);
            failures.recordFailure(new AutomationFailure(
                    scope,
                    Workflow.getInfo().getWorkflowId(),
                    WORKFLOW_TYPE,
                    null,
                    ActivityFailureCauses.describe(failure)));
        }
    }
}
