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
import org.sequeless.spi.automation.ObjectChangedEvent;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.StoreEvent;
import org.sequeless.spi.automation.TimerScheduledEntry;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The default, durable {@link AutomationPort} adapter. {@link #ingest} matches on the sealed {@link
 * StoreEvent} type with an exhaustive switch and no {@code default}: an {@link ObjectChangedEvent} starts
 * a {@link ChangeEventWorkflow} (keyed by {@code event.id()}) that runs {@code onChange} only. A {@link
 * TransitionFiredEvent} is routed to {@code startTransitionFiredActions}, which starts one {@link
 * TimerWorkflow} for each timer the event starts, keyed by the timer's {@code timerKey}, and then one
 * {@link TransitionActionWorkflow} per action of the transition, keyed by {@code event.id()} and the
 * action index. Every case is fire-and-forget — {@link #ingest} never waits for a started workflow to
 * complete. Timers are started from the {@code TransitionFired} event, and no timer is cancelled.
 *
 * <p>The action workflows and their activities keep {@link OutboxEntry} as their input, so the {@code
 * TransitionFired} case rebuilds one from the event, with the event's id, payload and {@code occurredAt}
 * and kind {@link OutboxEntry#KIND_TRANSITION_FIRED}, before it starts them.
 *
 * <p><b>Idempotency comes from Temporal's own workflow-id semantics, not from any state this class
 * keeps.</b> A second {@link #ingest} call for an event (or, for a timer, its {@code timerKey}) whose
 * workflow has already been started raises {@link WorkflowExecutionAlreadyStarted}, which this class
 * catches and treats as a successful no-op — unlike {@code
 * org.sequeless.adapter.automation.inprocess.InProcessAutomationPort}, which has to track dispatched ids
 * itself because it has no equivalent durable-engine mechanism to lean on.
 *
 * <p><b>{@code WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE} is set explicitly,
 * deliberately overriding Temporal's own default.</b> Verified empirically (a failing {@code
 * TestWorkflowEnvironment} contract test caught this): the SDK's default reuse policy is {@code
 * ALLOW_DUPLICATE}, under which starting a workflow whose previous run with the same id has already
 * <em>completed</em> silently starts a brand-new execution instead of raising {@link
 * WorkflowExecutionAlreadyStarted} — which would re-run the action a second time on redispatch,
 * exactly the bug {@link org.sequeless.spi.automation.AutomationPort#ingest}'s idempotency clause
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
    public void ingest(Scope scope, StoreEvent event) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(event, "event must not be null");

        switch (event) {
            case ObjectChangedEvent changed -> startChangeEventWorkflow(scope, changed);
            case TransitionFiredEvent fired -> startTransitionFiredActions(scope, fired);
        }
    }

    private void startChangeEventWorkflow(Scope scope, ObjectChangedEvent event) {
        ChangeEventWorkflow workflow = workflowClient.newWorkflowStub(
                ChangeEventWorkflow.class, workflowOptions(event.id().toString()));
        try {
            WorkflowClient.start(workflow::run, event, scope);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Idempotent no-op: this event's workflow already exists (started or completed).
        }
    }

    /**
     * Starts the timers, then the actions, of a {@code TransitionFired} event. {@link #ingest} calls this
     * method for every {@link TransitionFiredEvent}.
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
    void startTransitionFiredActions(Scope scope, TransitionFiredEvent event) {
        Automation automation = automationSource.automation(scope.tenantId());
        if (!(event.payload().get("ontologyVersion") instanceof Number stamp)) {
            throw new IllegalStateException("TransitionFired row " + event.id() + " has no ontologyVersion stamp");
        }
        int rowOntologyVersion = stamp.intValue();
        int activeOntologyVersion = automation.ontologyVersion().value();
        if (activeOntologyVersion != rowOntologyVersion) {
            throw new IllegalStateException("Automation is at ontology version "
                    + activeOntologyVersion
                    + " but TransitionFired row "
                    + event.id()
                    + " was written at version "
                    + rowOntologyVersion);
        }

        ObjectId objectId = ObjectId.parse((String) event.payload().get("objectId"));
        String typeIri = (String) event.payload().get("typeIri");
        for (ResolvedTimer timer : triggerEvaluator.timersStartedBy(scope, event)) {
            OutboxEntry timerEntry =
                    TimerScheduledEntry.of(scope, objectId, typeIri, rowOntologyVersion, timer, event.occurredAt());
            TimerWorkflow timerWorkflow =
                    workflowClient.newWorkflowStub(TimerWorkflow.class, workflowOptions(timer.timerKey()));
            try {
                WorkflowClient.start(timerWorkflow::run, timerEntry, scope);
            } catch (WorkflowExecutionAlreadyStarted e) {
                // Idempotent no-op for this timer only: its timerKey's workflow already exists.
            }
        }

        TransitionRef transition = new TransitionRef(
                (String) event.payload().get("machineIri"),
                (String) event.payload().get("fromState"),
                (String) event.payload().get("transitionName"));
        List<Action> actions = automation.actions(transition);
        // The action workflows keep OutboxEntry as their input (OQ3(a)): rebuild the row, losslessly.
        OutboxEntry transitionFired =
                new OutboxEntry(event.id(), OutboxEntry.KIND_TRANSITION_FIRED, event.payload(), event.occurredAt());
        for (int i = 0; i < actions.size(); i++) {
            TransitionActionWorkflow workflow = workflowClient.newWorkflowStub(
                    TransitionActionWorkflow.class, workflowOptions(event.id() + ":" + i));
            try {
                WorkflowClient.start(workflow::execute, transitionFired, i);
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
