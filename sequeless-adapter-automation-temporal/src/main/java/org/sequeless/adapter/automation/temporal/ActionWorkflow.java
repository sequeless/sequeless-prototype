package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The Temporal workflow that durably drives one {@code ActionRequest} outbox entry's action to
 * completion. {@link TemporalAutomationPort#dispatch} starts exactly one execution of this
 * workflow per {@link OutboxEntry#id()}, using that id (stringified) as the workflow id, which is
 * what gives {@link org.sequeless.spi.automation.AutomationPort#dispatch}'s idempotent-redispatch
 * requirement to Temporal's own workflow-id semantics for free: a second {@code start} call for an
 * already-started (or already-completed, within the workflow id reuse window) workflow id raises
 * {@code io.temporal.client.WorkflowExecutionAlreadyStarted}, which the caller treats as success.
 *
 * <p>{@code OutboxEntry} itself is the workflow input — no bespoke DTO — because it is a plain
 * record of JSON-compatible types, which Temporal's default Jackson-based {@code DataConverter}
 * round-trips natively (see {@code OutboxEntryDataConverterRoundTripTest}).
 */
@WorkflowInterface
public interface ActionWorkflow {

    /**
     * Reads {@code entry.payload()}'s {@code actionKind} and dispatches to exactly one of {@link
     * ActionActivities}' four methods, having first reconstructed a {@link org.sequeless.spi.Scope}
     * from the payload's own {@code tenantId}/{@code principalId} fields.
     *
     * @param entry the {@code ActionRequest}-kind outbox entry describing the action to run
     */
    @WorkflowMethod
    void execute(OutboxEntry entry);
}
