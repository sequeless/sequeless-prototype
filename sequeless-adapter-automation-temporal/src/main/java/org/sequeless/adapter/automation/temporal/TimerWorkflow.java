package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The Temporal workflow that durably waits out one {@code TimerScheduled} outbox entry's {@code
 * after} duration, calling {@link TriggerActivities#onTimerElapsed} on natural expiry, or doing
 * nothing if a {@link #cancel} signal arrives first.
 *
 * <p><b>Workflow id is the payload's {@code timerKey}, deliberately NOT {@code entry.id()}</b> —
 * unlike {@link ActionWorkflow}/{@link ChangeEventWorkflow}/{@link SignalEventWorkflow}, all three
 * of which key on the outbox entry's own id. Keying on {@code timerKey} (stable across the
 * schedule/cancel pair — see {@code OutboxEntry#KIND_TIMER_SCHEDULED}'s javadoc for its {@code
 * objectId|stateIri|transitionName} shape) is what lets a later {@code TimerCancelled} dispatch
 * find and signal the exact right running workflow by recomputing the same key, and what makes
 * redispatching an identical {@code TimerScheduled} entry naturally idempotent: the second {@code
 * dispatch} call's attempt to start a workflow with the same id raises {@code
 * WorkflowExecutionAlreadyStarted}, exactly like {@link ActionWorkflow}'s own id-based idempotency.
 */
@WorkflowInterface
public interface TimerWorkflow {

    /**
     * Waits {@code entry.payload()}'s {@code after} duration (parsed from its ISO-8601 string) for
     * either natural expiry or a {@link #cancel} signal, calling {@link
     * TriggerActivities#onTimerElapsed} only on natural expiry.
     *
     * @param entry the {@code TimerScheduled}-kind outbox entry describing the pending timer
     * @param scope the scope to fire {@code onTimerElapsed} under
     */
    @WorkflowMethod
    void run(OutboxEntry entry, Scope scope);

    /**
     * Signals that the object left the state this timer was waiting out (or the timer transition
     * itself already fired via some other path), so {@link #run} should complete without calling
     * {@link TriggerActivities#onTimerElapsed}.
     */
    @SignalMethod
    void cancel();

    /**
     * @return {@code true} once {@link #run} has reached its {@code Workflow.await} call and is
     *     genuinely parked waiting for either the timer or a {@link #cancel} signal — used only by
     *     test code (see {@code TemporalAutomationPortContractTest#advanceTime}) to know it is safe
     *     to skip {@code TestWorkflowEnvironment} time forward without racing this workflow's own
     *     startup.
     */
    @QueryMethod
    boolean isAwaiting();
}
