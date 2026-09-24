package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The Temporal workflow that durably drives one {@code ObjectCreated}/{@code ObjectUpdated}/{@code
 * ObjectDeleted} outbox entry's recompute-then-onChange pair to completion. Workflow id is {@code
 * entry.id().toString()} with {@code WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE}, exactly {@link
 * ActionWorkflow}'s own idempotency mechanism — a redispatch of the identical entry raises {@code
 * WorkflowExecutionAlreadyStarted}, which {@link TemporalAutomationPort#dispatch} treats as a
 * successful no-op.
 *
 * <p><b>Ordering.</b> {@link ChangeEventWorkflowImpl#run} calls {@link TriggerActivities#recompute}
 * THEN {@link TriggerActivities#onChange}, in that order, for the same reason {@code
 * InProcessAutomationPort} does: {@code ex:ProjectLifecycle}'s {@code autoClose} guard reads a
 * materialised property from stored props, which must already be fresh by the time {@code
 * onChange} evaluates it.
 *
 * <p><b>Recompute concurrency: RETRY_ONLY only, by design, for this first cut.</b> Unlike {@code
 * InProcessAutomationPort}'s configurable {@code serialize}/{@code coalesce} modes, this workflow
 * always calls {@link TriggerActivities#recompute} directly and unconditionally — relying on
 * {@code DefaultDerivationRecomputer}'s own intra-call {@code StaleObjectException} retry loop
 * (plus Temporal's own activity-level {@code RetryOptions} on top) as the only safety net, exactly
 * like the in-process adapter's {@code RETRY_ONLY} mode. See {@link TemporalAutomationPort}'s
 * class javadoc for why {@code serialize}/{@code coalesce} (a per-key {@code RecomputeWorkflow}
 * driven by {@code signalWithStart}) was not attempted this task: correlating "this specific
 * change event's own coalesced recompute has finished" with signal-with-start's fire-and-forget
 * semantics would need an additional callback round-trip (a signal back from the per-key workflow,
 * or a side-channel activity), which is more new surface than this task's budget allows to get
 * right with confidence. This is a recorded, explicit scope reduction, not an oversight.
 */
@WorkflowInterface
public interface ChangeEventWorkflow {

    /**
     * Calls {@link TriggerActivities#recompute} then {@link TriggerActivities#onChange}, in that
     * order, for {@code entry}.
     *
     * @param entry the {@code ObjectCreated}/{@code ObjectUpdated}/{@code ObjectDeleted}-kind
     *     outbox entry describing the change
     * @param scope the scope to recompute and evaluate under
     */
    @WorkflowMethod
    void run(OutboxEntry entry, Scope scope);
}
