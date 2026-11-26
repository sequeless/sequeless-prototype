package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import org.sequeless.spi.automation.AutomationFailure;

/**
 * The Temporal activity a workflow calls to write its {@link AutomationFailure} record, after its own
 * activity failed and its retries were exhausted (spec 13, R2). Only {@link ChangeEventWorkflowImpl} and
 * {@link TransitionActionWorkflowImpl} call it.
 *
 * <p>The record activity uses the same retry policy as the others. If its own write keeps failing, the
 * workflow fails visibly rather than ending as if it had succeeded.
 */
@ActivityInterface
public interface AutomationFailureActivities {

    /**
     * Persists {@code failure} through the injected {@link org.sequeless.spi.automation.AutomationFailureRecorder}.
     *
     * @param failure the failure to record
     */
    @ActivityMethod
    void recordFailure(AutomationFailure failure);
}
