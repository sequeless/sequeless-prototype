package org.sequeless.adapter.automation.temporal;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import java.util.Objects;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The default, durable {@link AutomationPort} adapter: {@link #dispatch} durably <b>starts</b> an
 * {@link ActionWorkflow} execution — it does not wait for the workflow to complete — using {@code
 * entry.id().toString()} as the workflow id, exactly and without any prefix or suffix, so a later
 * crash-recovery scenario that re-dispatches the identical outbox row produces the identical
 * workflow id.
 *
 * <p><b>Idempotency comes from Temporal's own workflow-id semantics, not from any state this class
 * keeps.</b> A second {@link #dispatch} call for an entry whose workflow has already been started
 * raises {@link WorkflowExecutionAlreadyStarted}, which this class catches and treats as a
 * successful no-op — unlike {@code org.sequeless.adapter.automation.inprocess.InProcessAutomationPort},
 * which has to track dispatched ids itself because it has no equivalent durable-engine mechanism to
 * lean on.
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
        if (!entry.kind().equals(OutboxEntry.KIND_ACTION_REQUEST)) {
            throw new IllegalArgumentException(
                "OutboxEntry kind must be '" + OutboxEntry.KIND_ACTION_REQUEST + "', was '" + entry.kind() + "'");
        }

        ActionWorkflow workflow =
            workflowClient.newWorkflowStub(
                ActionWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setWorkflowId(entry.id().toString())
                    .setTaskQueue(taskQueue)
                    .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                    .build());
        try {
            WorkflowClient.start(workflow::execute, entry);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Idempotent no-op: this entry's workflow already exists (started or completed).
        }
    }
}
