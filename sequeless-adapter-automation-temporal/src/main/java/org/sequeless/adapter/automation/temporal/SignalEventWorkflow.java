package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The Temporal workflow that durably drives one {@code SignalReceived} outbox entry's {@link
 * TriggerActivities#onSignal} call to completion. Workflow id is {@code entry.id().toString()}
 * with {@code WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE}, exactly {@link ActionWorkflow}'s and
 * {@link ChangeEventWorkflow}'s own idempotency mechanism.
 */
@WorkflowInterface
public interface SignalEventWorkflow {

    /**
     * Calls {@link TriggerActivities#onSignal} for {@code entry}.
     *
     * @param entry the {@code SignalReceived}-kind outbox entry describing the signal
     * @param scope the scope to evaluate under
     */
    @WorkflowMethod
    void run(OutboxEntry entry, Scope scope);
}
