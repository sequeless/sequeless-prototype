package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.automation.AutomationFailure;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The real {@link TimerWorkflow} implementation. It reads {@link TemporalActivityOptions#ACTIVITY_OPTIONS},
 * which explains why.
 *
 * <p>P3 changed the body of {@link #run}, under the change id {@link #TIMER_BODY_CHANGE_ID}. An execution
 * recorded before the change has no marker for that id, so {@link Workflow#getVersion} returns {@link
 * Workflow#DEFAULT_VERSION} when it replays, and {@link #run} takes the original await, which still honours
 * a {@link #cancel} signal. A new execution records the marker and waits on its timer alone.
 *
 * <p><b>Failure path (spec 13, R2).</b> When {@link TriggerActivities#onTimerElapsed} exhausts its retries,
 * the {@link ActivityFailure} is caught here, and the workflow writes one {@link AutomationFailure} record,
 * keyed by its own workflow id (the timer's {@code timerKey}), then ends normally. It is guarded by {@link
 * #TIMER_FAILURE_RECORD_CHANGE_ID}. An execution recorded before that change has no marker, so it keeps the
 * old behaviour: the failure propagates and the execution fails. A cancellation is not recorded, and it
 * propagates.
 */
public final class TimerWorkflowImpl implements TimerWorkflow {

    /** The change id that guards the P3 change to the body of {@link #run}. */
    private static final String TIMER_BODY_CHANGE_ID = "timer-body-p3";

    /** The version a new execution records under {@link #TIMER_BODY_CHANGE_ID}. */
    private static final int TIMER_BODY_VERSION = 1;

    /** The change id that guards the failure record of an elapsed timer (spec 13, R2). */
    private static final String TIMER_FAILURE_RECORD_CHANGE_ID = "timer-failure-record";

    /** The version a new execution records under {@link #TIMER_FAILURE_RECORD_CHANGE_ID}. */
    private static final int TIMER_FAILURE_RECORD_VERSION = 1;

    /** The workflow type written on a failure record. Equals the interface's simple name. */
    static final String WORKFLOW_TYPE = "TimerWorkflow";

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

        // Read on every path, before the wait, so the marker is written on the first execution and a replay
        // reads the same value at the same point.
        boolean recordsFailure = Workflow.getVersion(
                        TIMER_FAILURE_RECORD_CHANGE_ID, Workflow.DEFAULT_VERSION, TIMER_FAILURE_RECORD_VERSION)
                == TIMER_FAILURE_RECORD_VERSION;

        awaiting = true;
        if (Workflow.getVersion(TIMER_BODY_CHANGE_ID, Workflow.DEFAULT_VERSION, TIMER_BODY_VERSION)
                == Workflow.DEFAULT_VERSION) {
            // Recorded before the change: keep the await, so a recorded cancel signal still ends the wait.
            // Workflow.await(Duration, Supplier<Boolean>) returns true if the condition became true
            // before the timeout elapsed, false if the timeout elapsed first - see the SDK's own
            // WorkflowInternal#await implementation, verified via javap against temporal-sdk-1.39.0.jar.
            boolean cancelledBeforeExpiry = Workflow.await(after, () -> cancelled);
            if (!cancelledBeforeExpiry) {
                onTimerElapsed(activities, activityOptions, scope, entry, recordsFailure);
            }
        } else {
            Workflow.sleep(after);
            onTimerElapsed(activities, activityOptions, scope, entry, recordsFailure);
        }
    }

    /**
     * Calls {@link TriggerActivities#onTimerElapsed}. When {@code recordsFailure} is {@code false}, an
     * execution recorded before the R2 failure record, a failure propagates as it did before R2. When it is
     * {@code true}, a failure that is not a cancellation is recorded, and the workflow then ends normally.
     */
    private static void onTimerElapsed(
            TriggerActivities activities,
            ActivityOptions activityOptions,
            Scope scope,
            OutboxEntry entry,
            boolean recordsFailure) {
        try {
            activities.onTimerElapsed(scope, entry);
        } catch (ActivityFailure failure) {
            if (!recordsFailure || ActivityFailureCauses.isCancellation(failure)) {
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

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public boolean isAwaiting() {
        return awaiting;
    }
}
