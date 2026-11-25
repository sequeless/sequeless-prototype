package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The Temporal workflow that durably drives one {@code ObjectCreated}/{@code ObjectUpdated}/{@code
 * ObjectDeleted} outbox entry's onChange evaluation to completion. Workflow id is {@code
 * entry.id().toString()} with {@code WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE}, so a redispatch of
 * the identical entry raises {@code WorkflowExecutionAlreadyStarted}, which {@link
 * TemporalAutomationPort#dispatch} treats as a successful no-op.
 *
 * <p><b>Ordering.</b> The derivation recompute is not part of this workflow. The outbox relay runs
 * the recompute before it dispatches the row (spec P5a), so {@code ex:ProjectLifecycle}'s {@code
 * autoClose} guard reads a materialised property that is already fresh when {@link
 * ChangeEventWorkflowImpl#run} calls {@link TriggerActivities#onChange}.
 */
@WorkflowInterface
public interface ChangeEventWorkflow {

    /**
     * Calls {@link TriggerActivities#onChange} for {@code entry}. The recompute has already run in
     * the relay before dispatch.
     *
     * @param entry the {@code ObjectCreated}/{@code ObjectUpdated}/{@code ObjectDeleted}-kind
     *     outbox entry describing the change
     * @param scope the scope to evaluate under
     */
    @WorkflowMethod
    void run(OutboxEntry entry, Scope scope);
}
