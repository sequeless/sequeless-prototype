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
 * The real {@link ActionWorkflow} implementation. Temporal instantiates this class itself,
 * reflectively, once per workflow execution — it therefore <b>must</b> have a public no-arg
 * constructor and cannot take {@link ActionActivities} or {@link ActivityOptions} via constructor
 * injection the way {@link ActionActivitiesImpl} takes an {@link
 * org.sequeless.spi.automation.ActionExecutor}.
 *
 * <p>The {@link ActivityOptions} this workflow builds its activity stub with are read from {@link
 * TemporalActivityOptions#ACTIVITY_OPTIONS}, which explains why a workflow cannot take them through a
 * constructor.
 */
public final class ActionWorkflowImpl implements ActionWorkflow {

    private static final String ACTION_KIND_SET_PROPERTY = "SetProperty";
    private static final String ACTION_KIND_CREATE_OBJECT = "CreateObject";
    private static final String ACTION_KIND_WEBHOOK = "Webhook";
    private static final String ACTION_KIND_LOG = "Log";

    @Override
    public void execute(OutboxEntry entry) {
        ActivityOptions activityOptions = TemporalActivityOptions.ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException("TemporalActivityOptions.ACTIVITY_OPTIONS was never set - "
                    + "TemporalAutomationAutoConfiguration must assign it before starting the worker");
        }
        ActionActivities activities = Workflow.newActivityStub(ActionActivities.class, activityOptions);

        Scope scope = scopeFrom(entry);
        String actionKind = (String) entry.payload().get("actionKind");
        switch (actionKind) {
            case ACTION_KIND_SET_PROPERTY -> activities.applySetProperty(scope, entry);
            case ACTION_KIND_CREATE_OBJECT -> activities.applyCreateObject(scope, entry);
            case ACTION_KIND_LOG -> activities.applyLog(scope, entry);
            case ACTION_KIND_WEBHOOK -> activities.executeWebhook(scope, entry);
            default ->
                throw new IllegalArgumentException("Unrecognized ActionRequest payload actionKind: " + actionKind);
        }
    }

    /**
     * Reconstructs a {@link Scope} from {@code entry.payload()}'s own {@code tenantId}/{@code
     * principalId} fields, following the same convention {@code org.sequeless.testkit.Fixtures#scope}
     * uses: the principal is rebuilt from its id alone.
     */
    private static Scope scopeFrom(OutboxEntry entry) {
        Map<String, Object> payload = entry.payload();
        String tenantId = (String) payload.get("tenantId");
        String principalId = (String) payload.get("principalId");
        return new Scope(new TenantId(tenantId), new Principal(principalId, Set.of()));
    }
}
