package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The real {@link TimerWorkflow} implementation. It reads {@link TemporalActivityOptions#ACTIVITY_OPTIONS},
 * which explains why.
 *
 * <p>P3 changed the body of {@link #run}, under the change id {@link #TIMER_BODY_CHANGE_ID}. An execution
 * recorded before the change has no marker for that id, so {@link Workflow#getVersion} returns {@link
 * Workflow#DEFAULT_VERSION} when it replays, and {@link #run} takes the original await, which still honours
 * a {@link #cancel} signal. A new execution records the marker and waits on its timer alone.
 */
public final class TimerWorkflowImpl implements TimerWorkflow {

    /** The change id that guards the P3 change to the body of {@link #run}. */
    private static final String TIMER_BODY_CHANGE_ID = "timer-body-p3";

    /** The version a new execution records under {@link #TIMER_BODY_CHANGE_ID}. */
    private static final int TIMER_BODY_VERSION = 1;

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
        if (Workflow.getVersion(TIMER_BODY_CHANGE_ID, Workflow.DEFAULT_VERSION, TIMER_BODY_VERSION)
                == Workflow.DEFAULT_VERSION) {
            // Recorded before the change: keep the await, so a recorded cancel signal still ends the wait.
            // Workflow.await(Duration, Supplier<Boolean>) returns true if the condition became true
            // before the timeout elapsed, false if the timeout elapsed first - see the SDK's own
            // WorkflowInternal#await implementation, verified via javap against temporal-sdk-1.39.0.jar.
            boolean cancelledBeforeExpiry = Workflow.await(after, () -> cancelled);
            if (!cancelledBeforeExpiry) {
                activities.onTimerElapsed(scope, entry);
            }
        } else {
            Workflow.sleep(after);
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
