package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.spi.automation.ObjectChangedEvent;

/**
 * The Temporal workflow that durably drives one {@link ObjectChangedEvent} (an object created, updated
 * or deleted) to completion through {@link TriggerActivities#onChange}. Workflow id is {@code
 * event.id().toString()} with {@code WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE}, so a re-ingest of
 * the identical event raises {@code WorkflowExecutionAlreadyStarted}, which {@link
 * TemporalAutomationPort#ingest} treats as a successful no-op.
 *
 * <p><b>Ordering.</b> The derivation recompute is not part of this workflow. The outbox relay runs
 * the recompute before it ingests the row (spec P5a), so {@code ex:ProjectLifecycle}'s {@code
 * autoClose} guard reads a materialised property that is already fresh when {@link
 * ChangeEventWorkflowImpl#run} calls {@link TriggerActivities#onChange}.
 */
@WorkflowInterface
public interface ChangeEventWorkflow {

    /**
     * Calls {@link TriggerActivities#onChange} for {@code event}. The recompute has already run in
     * the relay before ingest.
     *
     * @param event the object-lifecycle Store event describing the change
     * @param scope the scope to evaluate under
     */
    @WorkflowMethod
    void run(ObjectChangedEvent event, Scope scope);
}
