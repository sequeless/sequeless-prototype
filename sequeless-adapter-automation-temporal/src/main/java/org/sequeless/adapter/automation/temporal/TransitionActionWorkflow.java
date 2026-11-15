package org.sequeless.adapter.automation.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The per-action counterpart of {@link ActionWorkflow}, for a {@code TransitionFired} row. It durably
 * applies the one action at a given index of the row's transition, through {@link
 * ActionActivities#applyTransitionAction}. The port starts one execution per action index, with
 * workflow id {@code "<entryId>:<index>"}, so each index gets its own run and its own idempotency.
 *
 * <p>Nothing starts this workflow yet. The port's per-action entry point is not reached from {@code
 * dispatch} until the dispatch flip.
 */
@WorkflowInterface
public interface TransitionActionWorkflow {

    /**
     * Applies the action at {@code actionIndex} of the {@code TransitionFired} row {@code entry}, by
     * calling {@link ActionActivities#applyTransitionAction}.
     *
     * @param entry the {@code TransitionFired}-kind outbox entry the action originates from
     * @param actionIndex the action's 0-based position in the automation's list for the row's
     *     transition
     */
    @WorkflowMethod
    void execute(OutboxEntry entry, int actionIndex);
}
