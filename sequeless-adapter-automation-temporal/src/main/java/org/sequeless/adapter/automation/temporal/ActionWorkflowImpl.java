package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.util.Map;
import java.util.Set;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The real {@link ActionWorkflow} implementation. Temporal instantiates this class itself,
 * reflectively, once per workflow execution — it therefore <b>must</b> have a public no-arg
 * constructor and cannot take {@link ActionActivities} or {@link ActivityOptions} via constructor
 * injection the way {@link ActionActivitiesImpl} takes an {@link
 * org.sequeless.spi.automation.ActionExecutor}.
 *
 * <p>Instead, the {@link ActivityOptions} carrying the configured {@code RetryOptions}/timeout
 * (built from {@code sequeless.automation.temporal.retry.*}) is published through {@link
 * #ACTIVITY_OPTIONS}, a {@code static volatile} field {@link TemporalAutomationAutoConfiguration}
 * assigns exactly once, before {@code WorkerFactory.start()} is ever called — so every subsequent
 * workflow execution (which Temporal always runs on a fresh instance of this class) reads the same
 * already-published value. This is the one piece of "configuration injection" a Temporal workflow
 * implementation can use, precisely because it happens once, at start-up, rather than per
 * execution; nothing about a workflow's own deterministic replay depends on when this field was
 * set, only on what it was set to when {@link #execute} runs.
 */
public final class ActionWorkflowImpl implements ActionWorkflow {

    /**
     * The {@link ActivityOptions} every workflow execution's activity stub is built with, published
     * once by {@link TemporalAutomationAutoConfiguration} before worker start. Must not be read
     * while still {@code null} — that would mean a worker was started without the auto-configuration
     * having run, which is itself a wiring bug worth failing loudly on (see {@link #execute}).
     */
    static volatile ActivityOptions ACTIVITY_OPTIONS;

    private static final String ACTION_KIND_SET_PROPERTY = "SetProperty";
    private static final String ACTION_KIND_CREATE_OBJECT = "CreateObject";
    private static final String ACTION_KIND_WEBHOOK = "Webhook";
    private static final String ACTION_KIND_LOG = "Log";

    @Override
    public void execute(OutboxEntry entry) {
        ActivityOptions activityOptions = ACTIVITY_OPTIONS;
        if (activityOptions == null) {
            throw new IllegalStateException(
                "ActionWorkflowImpl.ACTIVITY_OPTIONS was never set - "
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
            default -> throw new IllegalArgumentException(
                "Unrecognized ActionRequest payload actionKind: " + actionKind);
        }
    }

    /**
     * Reconstructs a {@link Scope} from {@code entry.payload()}'s own {@code tenantId}/{@code
     * principalId} fields, following the same convention {@code org.sequeless.testkit.Fixtures#scope}
     * uses: the principal's {@code displayName} is not carried in the payload, so the id is reused
     * for both.
     */
    private static Scope scopeFrom(OutboxEntry entry) {
        Map<String, Object> payload = entry.payload();
        String tenantId = (String) payload.get("tenantId");
        String principalId = (String) payload.get("principalId");
        return new Scope(new TenantId(tenantId), new Principal(principalId, principalId, Set.of()));
    }
}
