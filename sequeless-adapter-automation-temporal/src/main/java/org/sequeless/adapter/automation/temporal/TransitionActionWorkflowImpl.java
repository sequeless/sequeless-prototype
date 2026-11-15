package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.util.Map;
import java.util.Set;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The real {@link TransitionActionWorkflow} implementation. Temporal instantiates this class
 * reflectively, once per execution, so it keeps the implicit public no-arg constructor.
 *
 * <p>Its {@link ActivityOptions} are read from {@link ActionWorkflowImpl#ACTIVITY_OPTIONS}, the same
 * value {@link SignalEventWorkflowImpl} reads, so every activity this workflow calls shares the
 * retry policy {@link TemporalAutomationAutoConfiguration} builds.
 */
public final class TransitionActionWorkflowImpl implements TransitionActionWorkflow {

    @Override
    public void execute(OutboxEntry entry, int actionIndex) {
        ActivityOptions activityOptions = ActionWorkflowImpl.ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException("ActionWorkflowImpl.ACTIVITY_OPTIONS was never set - "
                    + "TemporalAutomationAutoConfiguration must assign it before starting the worker");
        }
        ActionActivities activities = Workflow.newActivityStub(ActionActivities.class, activityOptions);
        activities.applyTransitionAction(scopeFrom(entry), entry, actionIndex);
    }

    /**
     * Reconstructs a {@link Scope} from {@code entry.payload()}'s own {@code tenantId}/{@code
     * principalId} fields, following the same convention {@link ActionWorkflowImpl} uses: the
     * principal is rebuilt from its id alone.
     */
    private static Scope scopeFrom(OutboxEntry entry) {
        Map<String, Object> payload = entry.payload();
        String tenantId = (String) payload.get("tenantId");
        String principalId = (String) payload.get("principalId");
        return new Scope(new TenantId(tenantId), new Principal(principalId, Set.of()));
    }
}
