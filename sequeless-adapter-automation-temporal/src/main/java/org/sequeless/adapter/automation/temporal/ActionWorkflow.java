package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The Temporal workflow for the pre-flip {@code ActionRequest} drain path. It is registered on the
 * worker and never started: {@link TemporalAutomationPort#dispatch} no longer routes {@code
 * ActionRequest} rows to it. The per-action path for {@code TransitionFired} rows is {@link
 * TransitionActionWorkflow}. P2e deletes this workflow.
 *
 * <p>{@code OutboxEntry} itself is the workflow input — no bespoke DTO — because it is a plain
 * record of JSON-compatible types, which Temporal's default Jackson-based {@code DataConverter}
 * round-trips natively (see {@code OutboxEntryDataConverterRoundTripTest}).
 */
@WorkflowInterface
public interface ActionWorkflow {

    /**
     * Reads {@code entry.payload()}'s {@code actionKind} and dispatches to exactly one of the four
     * ActionRequest activity methods of {@link ActionActivities}; {@code applyTransitionAction} is the
     * fifth. Before dispatching, it reconstructs a {@link org.sequeless.kernel.identity.Scope} from the
     * payload's own {@code tenantId}/{@code principalId} fields.
     *
     * @param entry the {@code ActionRequest}-kind outbox entry describing the action to run
     */
    @WorkflowMethod
    void execute(OutboxEntry entry);
}
