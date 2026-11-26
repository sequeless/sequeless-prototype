package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The Temporal workflow that durably waits out one timer that a {@code TransitionFired} row starts,
 * calling {@link TriggerActivities#onTimerElapsed} on expiry. {@link TemporalAutomationPort} builds the
 * {@code TimerScheduled}-shaped outbox entry that {@link #run} takes, from the timer that {@link
 * org.sequeless.spi.automation.TriggerEvaluator#timersStartedBy} resolves for the row. This workflow
 * reads the entry's {@code after} duration and passes the entry on unchanged.
 *
 * <p><b>Workflow id is the timer's {@code timerKey}, deliberately NOT {@code entry.id()}</b> — unlike
 * {@link ChangeEventWorkflow}, which keys on the outbox entry's own id. The key is stable for an object, state and transition, so a second {@code TransitionFired} row
 * that starts the same timer recomputes the same id, and the second start raises {@code
 * WorkflowExecutionAlreadyStarted}, which the port treats as a no-op.
 *
 * <p>No timer is cancelled after P3, so the adapter no longer sends {@link #cancel}. The staleness check
 * in {@code onTimerElapsed} is the guard against a timer firing for an object that has left its state.
 * The {@link #cancel} signal stays for executions recorded before P3, whose histories can contain it.
 */
@WorkflowInterface
public interface TimerWorkflow {

    /**
     * Waits {@code entry.payload()}'s {@code after} duration (parsed from its ISO-8601 string), then calls
     * {@link TriggerActivities#onTimerElapsed}. A {@link #cancel} signal ends the wait early only in an
     * execution recorded before P3 (see {@link TimerWorkflowImpl}). If {@link TriggerActivities#onTimerElapsed}
     * fails after its retries, the failure is recorded under this workflow's id (the timer's key) and the
     * workflow ends normally (spec 13, R2).
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
     *     genuinely parked waiting for its timer (or, in an execution recorded before P3, for a {@link #cancel} signal) — used only by
     *     test code (see {@code TemporalAutomationPortContractTest#advanceTime}) to know it is safe
     *     to skip {@code TestWorkflowEnvironment} time forward without racing this workflow's own
     *     startup.
     */
    @QueryMethod
    boolean isAwaiting();
}
