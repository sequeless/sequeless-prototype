package org.sequeless.adapter.automation.temporal;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import java.util.List;
import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TimerScheduledEntry;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The default, durable {@link AutomationPort} adapter. {@link #dispatch} routes by {@code
 * entry.kind()}: the three object-lifecycle kinds start a {@link ChangeEventWorkflow} (also keyed by
 * {@code entry.id()}) that runs recompute then {@code onChange}, in that order; {@code SignalReceived}
 * starts a {@link SignalEventWorkflow} keyed by {@code entry.id()}. {@code TransitionFired} is routed to
 * {@code startTransitionFiredActions}, which starts one {@link TimerWorkflow} for each timer the row
 * starts, keyed by the timer's {@code timerKey}, and then one {@link TransitionActionWorkflow} per action
 * of the transition, keyed by {@code entry.id()} and the action index. Every case is fire-and-forget —
 * {@link #dispatch} never waits for a started workflow to complete. Any other unrecognised kind is
 * rejected with {@link IllegalArgumentException}, including {@code TimerScheduled} and {@code
 * TimerCancelled}: timers are started from the {@code TransitionFired} row, and no timer is cancelled.
 *
 * <p><b>Idempotency comes from Temporal's own workflow-id semantics, not from any state this class
 * keeps.</b> A second {@link #dispatch} call for an entry (or, for a timer, its {@code timerKey}) whose
 * workflow has already been started raises {@link WorkflowExecutionAlreadyStarted}, which this class
 * catches and treats as a successful no-op — unlike {@code
 * org.sequeless.adapter.automation.inprocess.InProcessAutomationPort}, which has to track dispatched ids
 * itself because it has no equivalent durable-engine mechanism to lean on.
 *
 * <p><b>Recompute concurrency has no serialize or coalesce mode, for this first cut.</b> See {@link
 * ChangeEventWorkflow}'s javadoc for why the plan's full {@code serialize}/{@code coalesce}
 * per-key-{@code RecomputeWorkflow}-via-{@code signalWithStart} design was not attempted, and what
 * would be needed to add it later.
 *
 * <p><b>{@code WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE} is set explicitly,
 * deliberately overriding Temporal's own default.</b> Verified empirically (a failing {@code
 * TestWorkflowEnvironment} contract test caught this): the SDK's default reuse policy is {@code
 * ALLOW_DUPLICATE}, under which starting a workflow whose previous run with the same id has already
 * <em>completed</em> silently starts a brand-new execution instead of raising {@link
 * WorkflowExecutionAlreadyStarted} — which would re-run the action a second time on redispatch,
 * exactly the bug {@link org.sequeless.spi.automation.AutomationPort#dispatch}'s idempotency clause
 * forbids. {@code REJECT_DUPLICATE} makes {@link WorkflowExecutionAlreadyStarted} unconditional for
 * any duplicate workflow id — running, completed, failed, or otherwise — which is what this class's
 * catch block actually needs to be correct.
 *
 * <p>This class does not wire its own {@code ActionExecutor}: that dependency is injected into
 * {@link ActionActivitiesImpl} instead, by {@link TemporalAutomationAutoConfiguration}, exactly one
 * layer further along the call chain than this class needs to know about.
 */
public final class TemporalAutomationPort implements AutomationPort {

    private final WorkflowClient workflowClient;
    private final String taskQueue;
    private final AutomationSource automationSource;
    private final TriggerEvaluator triggerEvaluator;

    /**
     * @param workflowClient the Temporal client used to start {@link TimerWorkflow} and {@link
     *     TransitionActionWorkflow} executions; must not be {@code null}
     * @param taskQueue the task queue {@link TimerWorkflow} and {@link TransitionActionWorkflow}
     *     executions are started on, matching the queue name the worker(s) registered in {@link
     *     TemporalAutomationAutoConfiguration} poll; must not be {@code null}
     * @param automationSource the {@link AutomationSource} the per-action path reads the automation
     *     of a {@code TransitionFired} row's ontology version from; must not be {@code null}
     * @param triggerEvaluator the {@link TriggerEvaluator} whose {@link TriggerEvaluator#timersStartedBy}
     *     resolves the timers a {@code TransitionFired} row starts; must not be {@code null}
     */
    public TemporalAutomationPort(
            WorkflowClient workflowClient,
            String taskQueue,
            AutomationSource automationSource,
            TriggerEvaluator triggerEvaluator) {
        this.workflowClient = Objects.requireNonNull(workflowClient, "workflowClient must not be null");
        this.taskQueue = Objects.requireNonNull(taskQueue, "taskQueue must not be null");
        this.automationSource = Objects.requireNonNull(automationSource, "automationSource must not be null");
        this.triggerEvaluator = Objects.requireNonNull(triggerEvaluator, "triggerEvaluator must not be null");
    }

    @Override
    public void dispatch(Scope scope, OutboxEntry entry) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        switch (entry.kind()) {
            case OutboxEntry.KIND_OBJECT_CREATED, OutboxEntry.KIND_OBJECT_UPDATED, OutboxEntry.KIND_OBJECT_DELETED ->
                startChangeEventWorkflow(scope, entry);
            case OutboxEntry.KIND_SIGNAL_RECEIVED -> startSignalEventWorkflow(scope, entry);
            case OutboxEntry.KIND_TRANSITION_FIRED -> startTransitionFiredActions(scope, entry);
            default -> throw new IllegalArgumentException("Unsupported OutboxEntry kind for dispatch: " + entry.kind());
        }
    }

    private void startChangeEventWorkflow(Scope scope, OutboxEntry entry) {
        ChangeEventWorkflow workflow = workflowClient.newWorkflowStub(
                ChangeEventWorkflow.class, workflowOptions(entry.id().toString()));
        try {
            WorkflowClient.start(workflow::run, entry, scope);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Idempotent no-op: this entry's workflow already exists (started or completed).
        }
    }

    private void startSignalEventWorkflow(Scope scope, OutboxEntry entry) {
        SignalEventWorkflow workflow = workflowClient.newWorkflowStub(
                SignalEventWorkflow.class, workflowOptions(entry.id().toString()));
        try {
            WorkflowClient.start(workflow::run, entry, scope);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Idempotent no-op: this entry's workflow already exists (started or completed).
        }
    }

    /**
     * Starts the timers, then the actions, of a {@code TransitionFired} row. {@link #dispatch} calls this
     * method for every {@code TransitionFired} row.
     *
     * <p>The automation is read first. The row's {@code ontologyVersion} stamp is then checked, missing or
     * non-Number first and a mismatch second, and both checks run before any workflow starts. The row's
     * timers are then resolved through {@link TriggerEvaluator#timersStartedBy}, and one {@link
     * TimerWorkflow} starts for each, keyed by its {@code timerKey}, before the action loop. Each action of
     * the transition then starts one {@link TransitionActionWorkflow}, in the automation's order, with
     * workflow id {@code entryId + ":" + index}.
     *
     * <p>A transition with no actions starts no action workflow and throws nothing, and the row's timers
     * still start.
     *
     * <p>Idempotency is per timer and per index. A timer or index whose workflow already exists is skipped,
     * and the loop continues, so an earlier one that was already started does not stop a later one from
     * starting.
     *
     * @throws IllegalStateException if the row has no {@code ontologyVersion} stamp, if the automation is at
     *     a different ontology version from the row's stamp, or if the behaviour that {@link
     *     TriggerEvaluator#timersStartedBy} reads is at a different version from the row's stamp, which the
     *     lookup throws
     */
    void startTransitionFiredActions(Scope scope, OutboxEntry entry) {
        Automation automation = automationSource.automation(scope.tenantId());
        if (!(entry.payload().get("ontologyVersion") instanceof Number stamp)) {
            throw new IllegalStateException("TransitionFired row " + entry.id() + " has no ontologyVersion stamp");
        }
        int rowOntologyVersion = stamp.intValue();
        int activeOntologyVersion = automation.ontologyVersion().value();
        if (activeOntologyVersion != rowOntologyVersion) {
            throw new IllegalStateException("Automation is at ontology version "
                    + activeOntologyVersion
                    + " but TransitionFired row "
                    + entry.id()
                    + " was written at version "
                    + rowOntologyVersion);
        }

        ObjectId objectId = ObjectId.parse((String) entry.payload().get("objectId"));
        String typeIri = (String) entry.payload().get("typeIri");
        for (ResolvedTimer timer : triggerEvaluator.timersStartedBy(scope, entry)) {
            OutboxEntry timerEntry =
                    TimerScheduledEntry.of(scope, objectId, typeIri, rowOntologyVersion, timer, entry.occurredAt());
            TimerWorkflow timerWorkflow =
                    workflowClient.newWorkflowStub(TimerWorkflow.class, workflowOptions(timer.timerKey()));
            try {
                WorkflowClient.start(timerWorkflow::run, timerEntry, scope);
            } catch (WorkflowExecutionAlreadyStarted e) {
                // Idempotent no-op for this timer only: its timerKey's workflow already exists.
            }
        }

        TransitionRef transition = new TransitionRef(
                (String) entry.payload().get("machineIri"),
                (String) entry.payload().get("fromState"),
                (String) entry.payload().get("transitionName"));
        List<Action> actions = automation.actions(transition);
        for (int i = 0; i < actions.size(); i++) {
            TransitionActionWorkflow workflow = workflowClient.newWorkflowStub(
                    TransitionActionWorkflow.class, workflowOptions(entry.id() + ":" + i));
            try {
                WorkflowClient.start(workflow::execute, entry, i);
            } catch (WorkflowExecutionAlreadyStarted e) {
                // Idempotent no-op for this index only: its workflow already exists. The loop continues,
                // so a later index whose workflow has not started yet still starts.
            }
        }
    }

    private WorkflowOptions workflowOptions(String workflowId) {
        return WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .build();
    }
}
