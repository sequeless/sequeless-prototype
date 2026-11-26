package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.util.Map;
import java.util.Set;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.spi.automation.AutomationFailure;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The real {@link TransitionActionWorkflow} implementation. Temporal instantiates this class
 * reflectively, once per execution, so it keeps the implicit public no-arg constructor.
 *
 * <p>Its {@link ActivityOptions} are read from {@link TemporalActivityOptions#ACTIVITY_OPTIONS}, the same
 * value {@link TimerWorkflowImpl} reads, so every activity this workflow calls shares the
 * retry policy {@link TemporalAutomationAutoConfiguration} builds.
 *
 * <p><b>Failure path (spec 13, R2).</b> Once {@link ActionActivities#applyTransitionAction} exhausts its
 * retries, or fails without one (a refused stamp or index), the {@link ActivityFailure} is caught here. The
 * workflow writes one {@link AutomationFailure} record, named by this execution's id ({@code eventId:index}),
 * with the row's transition, then ends normally. A cancellation is not recorded and propagates. A failure of
 * the record write propagates too, so the execution shows as failed.
 */
public final class TransitionActionWorkflowImpl implements TransitionActionWorkflow {

    /** The change id that guards the failure record of a transition action (spec 13, R2). */
    private static final String FAILURE_RECORD_CHANGE_ID = "transition-action-failure-record";

    /** The version a new execution records under {@link #FAILURE_RECORD_CHANGE_ID}. */
    private static final int FAILURE_RECORD_VERSION = 1;

    /** The workflow type written on a failure record. Equals the interface's simple name. */
    static final String WORKFLOW_TYPE = "TransitionActionWorkflow";

    @Override
    public void execute(OutboxEntry entry, int actionIndex) {
        ActivityOptions activityOptions = TemporalActivityOptions.ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException("TemporalActivityOptions.ACTIVITY_OPTIONS was never set - "
                    + "TemporalAutomationAutoConfiguration must assign it before starting the worker");
        }
        Scope scope = scopeFrom(entry);
        ActionActivities activities = Workflow.newActivityStub(ActionActivities.class, activityOptions);
        // Read before the activity, on every path, so the marker is written on the first execution. An execution
        // recorded before R2 has no marker, so it keeps the old behaviour: the failure propagates.
        boolean recordsFailure =
                Workflow.getVersion(FAILURE_RECORD_CHANGE_ID, Workflow.DEFAULT_VERSION, FAILURE_RECORD_VERSION)
                        == FAILURE_RECORD_VERSION;
        try {
            activities.applyTransitionAction(scope, entry, actionIndex);
        } catch (ActivityFailure failure) {
            if (!recordsFailure || ActivityFailureCauses.isCancellation(failure)) {
                throw failure;
            }
            AutomationFailureActivities failures =
                    Workflow.newActivityStub(AutomationFailureActivities.class, activityOptions);
            failures.recordFailure(new AutomationFailure(
                    scope,
                    Workflow.getInfo().getWorkflowId(),
                    WORKFLOW_TYPE,
                    transitionFrom(entry),
                    ActivityFailureCauses.describe(failure)));
        }
    }

    /**
     * Reconstructs a {@link Scope} from {@code entry.payload()}'s own {@code tenantId}/{@code
     * principalId} fields, following the same convention {@code
     * org.sequeless.testkit.Fixtures#scope} uses: the principal is rebuilt from its id alone.
     */
    private static Scope scopeFrom(OutboxEntry entry) {
        Map<String, Object> payload = entry.payload();
        String tenantId = (String) payload.get("tenantId");
        String principalId = (String) payload.get("principalId");
        return new Scope(new TenantId(tenantId), new Principal(principalId, Set.of()));
    }

    /** The transition the {@code TransitionFired} row records, read from the same payload keys the port uses. */
    private static TransitionRef transitionFrom(OutboxEntry entry) {
        Map<String, Object> payload = entry.payload();
        return new TransitionRef((String) payload.get("machineIri"), (String) payload.get("fromState"), (String)
                payload.get("transitionName"));
    }
}
