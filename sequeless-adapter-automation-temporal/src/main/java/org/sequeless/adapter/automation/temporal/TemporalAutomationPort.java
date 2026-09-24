package org.sequeless.adapter.automation.temporal;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import java.util.Objects;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The default, durable {@link AutomationPort} adapter. {@link #dispatch} routes by {@code
 * entry.kind()}: {@code ActionRequest} durably <b>starts</b> an {@link ActionWorkflow} execution
 * keyed by {@code entry.id()}; the three object-lifecycle kinds start a {@link ChangeEventWorkflow}
 * (also keyed by {@code entry.id()}) that runs recompute then {@code onChange}, in that order;
 * {@code TimerScheduled} starts a {@link TimerWorkflow} keyed by the payload's {@code timerKey};
 * {@code TimerCancelled} signals that same {@code timerKey}'s {@link TimerWorkflow}; {@code
 * SignalReceived} starts a {@link SignalEventWorkflow} keyed by {@code entry.id()}. Every case is
 * fire-and-forget — {@link #dispatch} never waits for a started workflow to complete. {@code
 * TransitionFired} (and any other unrecognised kind) is rejected with {@link
 * IllegalArgumentException}.
 *
 * <p><b>Idempotency comes from Temporal's own workflow-id semantics, not from any state this class
 * keeps.</b> A second {@link #dispatch} call for an entry (or, for timers, a {@code timerKey})
 * whose workflow has already been started raises {@link WorkflowExecutionAlreadyStarted}, which
 * this class catches and treats as a successful no-op — unlike {@code
 * org.sequeless.adapter.automation.inprocess.InProcessAutomationPort}, which has to track
 * dispatched ids itself because it has no equivalent durable-engine mechanism to lean on.
 *
 * <p><b>Recompute concurrency is {@code RETRY_ONLY} only, for this first cut.</b> See {@link
 * ChangeEventWorkflow}'s javadoc for why the plan's full {@code serialize}/{@code coalesce}
 * per-key-{@code RecomputeWorkflow}-via-{@code signalWithStart} design was not attempted, and what
 * would be needed to add it later.
 *
 * <p><b>{@code WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE} is set explicitly,
 * deliberately overriding Temporal's own default.</b> Verified empirically (a failing {@code
 * TestWorkflowEnvironment} contract test caught this): the SDK's default reuse policy is {@code
 * ALLOW_DUPLICATE}, under which starting a workflow whose previous run with the same id has already
 * <em>completed</em> silently starts a brand-new execution instead of raising {@link
 * WorkflowExecutionAlreadyStarted} — which would re-run the action a second time on redispatch,
 * exactly the bug {@link org.sequeless.spi.automation.AutomationPort#dispatch}'s idempotency clause
 * forbids. {@code REJECT_DUPLICATE} makes {@link WorkflowExecutionAlreadyStarted} unconditional for
 * any duplicate workflow id — running, completed, failed, or otherwise — which is what this class's
 * catch block actually needs to be correct.
 *
 * <p>This class does not wire its own {@code ActionExecutor}: that dependency is injected into
 * {@link ActionActivitiesImpl} instead, by {@link TemporalAutomationAutoConfiguration}, exactly one
 * layer further along the call chain than this class needs to know about.
 */
public final class TemporalAutomationPort implements AutomationPort {

    private final WorkflowClient workflowClient;
    private final String taskQueue;

    /**
     * @param workflowClient the Temporal client used to start {@link ActionWorkflow} executions;
     *     must not be {@code null}
     * @param taskQueue the task queue {@link ActionWorkflow} executions are started on, matching
     *     the queue name the worker(s) registered in {@link TemporalAutomationAutoConfiguration}
     *     poll; must not be {@code null}
     */
    public TemporalAutomationPort(WorkflowClient workflowClient, String taskQueue) {
        this.workflowClient = Objects.requireNonNull(workflowClient, "workflowClient must not be null");
        this.taskQueue = Objects.requireNonNull(taskQueue, "taskQueue must not be null");
    }

    @Override
    public void dispatch(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        switch (entry.kind()) {
            case OutboxEntry.KIND_ACTION_REQUEST -> startActionWorkflow(entry);
            case OutboxEntry.KIND_OBJECT_CREATED,
                OutboxEntry.KIND_OBJECT_UPDATED,
                OutboxEntry.KIND_OBJECT_DELETED -> startChangeEventWorkflow(scope, entry);
            case OutboxEntry.KIND_TIMER_SCHEDULED -> startTimerWorkflow(scope, entry);
            case OutboxEntry.KIND_TIMER_CANCELLED -> signalTimerCancel(entry);
            case OutboxEntry.KIND_SIGNAL_RECEIVED -> startSignalEventWorkflow(scope, entry);
            default -> throw new IllegalArgumentException(
                "Unsupported OutboxEntry kind for dispatch: " + entry.kind());
        }
    }

    private void startActionWorkflow(OutboxEntry entry) {
        ActionWorkflow workflow =
            workflowClient.newWorkflowStub(
                ActionWorkflow.class, workflowOptions(entry.id().toString()));
        try {
            WorkflowClient.start(workflow::execute, entry);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Idempotent no-op: this entry's workflow already exists (started or completed).
        }
    }

    private void startChangeEventWorkflow(Scope scope, OutboxEntry entry) {
        ChangeEventWorkflow workflow =
            workflowClient.newWorkflowStub(
                ChangeEventWorkflow.class, workflowOptions(entry.id().toString()));
        try {
            WorkflowClient.start(workflow::run, entry, scope);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Idempotent no-op: this entry's workflow already exists (started or completed).
        }
    }

    private void startSignalEventWorkflow(Scope scope, OutboxEntry entry) {
        SignalEventWorkflow workflow =
            workflowClient.newWorkflowStub(
                SignalEventWorkflow.class, workflowOptions(entry.id().toString()));
        try {
            WorkflowClient.start(workflow::run, entry, scope);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Idempotent no-op: this entry's workflow already exists (started or completed).
        }
    }

    /**
     * Starts a {@link TimerWorkflow} keyed by the payload's {@code timerKey} (NOT {@code
     * entry.id()} — see {@link TimerWorkflow}'s own javadoc for why). A redispatch of the identical
     * {@code TimerScheduled} entry raises {@link WorkflowExecutionAlreadyStarted} for the same
     * {@code timerKey}, which is treated as a successful no-op, making rescheduling naturally
     * idempotent.
     */
    private void startTimerWorkflow(Scope scope, OutboxEntry entry) {
        String timerKey = (String) entry.payload().get("timerKey");
        TimerWorkflow workflow =
            workflowClient.newWorkflowStub(TimerWorkflow.class, workflowOptions(timerKey));
        try {
            WorkflowClient.start(workflow::run, entry, scope);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Idempotent no-op: this timerKey's workflow already exists (started or completed).
        }
    }

    /**
     * Signals {@code cancel()} on the running {@link TimerWorkflow} named by the payload's {@code
     * timerKey}, swallowing {@link WorkflowNotFoundException}: the timer may already have fired,
     * already have been cancelled, or never have existed on this Temporal namespace, all of which
     * are legitimate races this method must tolerate silently rather than fail on.
     */
    private void signalTimerCancel(OutboxEntry entry) {
        String timerKey = (String) entry.payload().get("timerKey");
        TimerWorkflow workflow = workflowClient.newWorkflowStub(TimerWorkflow.class, timerKey);
        try {
            workflow.cancel();
        } catch (WorkflowNotFoundException e) {
            // Idempotent no-op: nothing to cancel.
        }
    }

    private WorkflowOptions workflowOptions(String workflowId) {
        return WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(taskQueue)
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
            .build();
    }
}
