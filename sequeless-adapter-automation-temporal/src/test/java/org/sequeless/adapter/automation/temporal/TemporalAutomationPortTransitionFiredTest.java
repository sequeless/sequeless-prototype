package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.spi.automation.TransitionFiredEvent;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;

/**
 * Covers the {@code TransitionFired} branch of {@link TemporalAutomationPort#ingest}, which starts the
 * per-action workflows through {@link TemporalAutomationPort#startTransitionFiredActions}, against real
 * {@link TransitionActionWorkflowImpl} executions in an in-memory {@link TestWorkflowEnvironment}. The
 * port's own checks are tested here: a bad stamp throws before any workflow starts, a transition with no
 * actions starts nothing, and each action index gets its own workflow.
 *
 * <p>The executor is shared by every test in this class, and workflows from earlier tests are
 * fire-and-forget, so each count filters on the event's id rather than counting every recorded call.
 */
class TemporalAutomationPortTransitionFiredTest {

    private static final String TASK_QUEUE = "sequeless-transition-port-test";
    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final String MACHINE_IRI = "http://example.org/ProjectLifecycle";
    private static final String DRAFT_IRI = "http://example.org/Draft";
    private static final String ACTIVE_IRI = "http://example.org/Active";
    private static final String STATUS_IRI = "http://example.org/status";
    private static final String TRANSITION_NAME = "activate";

    private static RecordingActionExecutor executor;
    private static SwappableAutomationSource automationSource;
    private static TestWorkflowEnvironment env;
    private static TemporalAutomationPort port;

    @BeforeAll
    static void startEnvironment() {
        // Published before env.start(), as TemporalAutomationAutoConfiguration does.
        TemporalActivityOptions.ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofMillis(10))
                        .setBackoffCoefficient(1.0)
                        .build())
                .build();

        executor = new RecordingActionExecutor();
        automationSource = new SwappableAutomationSource();

        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TransitionActionWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ActionActivitiesImpl(executor, automationSource));
        env.start();

        // The rows in this class start no timer, so the recording double resolves none.
        port = new TemporalAutomationPort(
                env.getWorkflowClient(), TASK_QUEUE, automationSource, new RecordingTriggerEvaluator());
    }

    @AfterAll
    static void stopEnvironment() {
        env.close();
    }

    @BeforeEach
    void resetAutomationSource() {
        SwappableAutomationSource.reset();
    }

    @Test
    void refusesEachBadStampBeforeStartingAnyWorkflow() {
        SwappableAutomationSource.current = twoActionAutomation(1);

        for (TransitionFiredEvent event : List.of(transitionFired(null), transitionFired("1"), transitionFired(2))) {
            assertThatThrownBy(() -> port.ingest(SCOPE, event))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(event.id().toString());
            assertNoWorkflowStarted(event);
        }
    }

    @Test
    void startsNothingAndThrowsForAnAutomationWithNoActionsForTheTransition() {
        SwappableAutomationSource.current = new Automation(new OntologyVersion(1), Map.of());
        TransitionFiredEvent event = transitionFired(1);

        assertThatCode(() -> port.ingest(SCOPE, event)).doesNotThrowAnyException();
        assertNoWorkflowStarted(event);
    }

    @Test
    void startsOneWorkflowPerActionAndEachAppliesItsAction() {
        SwappableAutomationSource.current = twoActionAutomation(1);
        TransitionFiredEvent event = transitionFired(1);

        port.ingest(SCOPE, event);
        awaitClosed(event, 0);
        awaitClosed(event, 1);

        assertThat(applyActionCount(event)).isEqualTo(2);
    }

    @Test
    void startsTheRemainingIndexesWhenAnEarlierIndexIsAlreadyStarted() {
        SwappableAutomationSource.current = twoActionAutomation(1);
        TransitionFiredEvent event = transitionFired(1);
        // Index 0 is started first, as a partial earlier dispatch would have left it, using the same id,
        // task queue and reuse policy the port uses. The port's own options builder is private.
        TransitionActionWorkflow first = env.getWorkflowClient()
                .newWorkflowStub(
                        TransitionActionWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(event.id() + ":0")
                                .setTaskQueue(TASK_QUEUE)
                                .setWorkflowIdReusePolicy(
                                        WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                                .build());
        WorkflowClient.start(first::execute, rebuiltRow(event), 0);

        port.ingest(SCOPE, event);
        awaitClosed(event, 0);
        awaitClosed(event, 1);

        assertThat(applyActionCount(event)).isEqualTo(2);
    }

    @Test
    void redispatchStartsNoIndexAgain() {
        SwappableAutomationSource.current = twoActionAutomation(1);
        TransitionFiredEvent event = transitionFired(1);
        port.ingest(SCOPE, event);
        awaitClosed(event, 0);
        awaitClosed(event, 1);
        List<String> runIdsBefore = runIds(event, 2);
        long appliedBefore = applyActionCount(event);

        port.ingest(SCOPE, event);

        // The run-id check is the one that fails if the reuse policy regresses. A duplicate run
        // would not have executed yet when this runs, so the apply count alone proves little.
        assertThat(runIds(event, 2)).isEqualTo(runIdsBefore);
        assertThat(applyActionCount(event)).isEqualTo(appliedBefore);
    }

    /** Asserts that no workflow exists for {@code event}'s first index, which a started event would have. */
    private static void assertNoWorkflowStarted(TransitionFiredEvent event) {
        assertThatThrownBy(() -> env.getWorkflowClient()
                        .newUntypedWorkflowStub(event.id() + ":0")
                        .describe())
                .isInstanceOf(WorkflowNotFoundException.class);
    }

    /** Blocks, bounded, until the workflow for {@code event} at {@code index} has closed. */
    private static void awaitClosed(TransitionFiredEvent event, int index) {
        WorkflowStub stub = env.getWorkflowClient().newUntypedWorkflowStub(event.id() + ":" + index);
        try {
            stub.getResult(5, TimeUnit.SECONDS, Void.class);
        } catch (TimeoutException e) {
            throw new AssertionError("Workflow " + event.id() + ":" + index + " did not close within 5 seconds", e);
        }
    }

    /** The run id of the latest run of each of {@code event}'s first {@code count} index workflows. */
    private static List<String> runIds(TransitionFiredEvent event, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> env.getWorkflowClient()
                        .newUntypedWorkflowStub(event.id() + ":" + index)
                        .describe()
                        .getExecution()
                        .getRunId())
                .toList();
    }

    /** How many {@code applyAction} calls the shared executor recorded for {@code event}. */
    private static long applyActionCount(TransitionFiredEvent event) {
        return executor.calls().stream()
                .filter(call -> call.method().equals(RecordingActionExecutor.METHOD_APPLY_ACTION))
                .filter(call -> call.entryId().equals(event.id()))
                .count();
    }

    /** An automation at {@code ontologyVersion} with two actions on the test transition, in order. */
    private static Automation twoActionAutomation(int ontologyVersion) {
        return automationAt(
                ontologyVersion,
                new LogAction("project activated"),
                new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner")));
    }

    /** An automation at {@code ontologyVersion} whose transition has the given actions, in order. */
    private static Automation automationAt(int ontologyVersion, Action... actions) {
        return new Automation(
                new OntologyVersion(ontologyVersion),
                Map.of(new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME), List.of(actions)));
    }

    /** The {@link OutboxEntry} the action workflows take as input, rebuilt from {@code event} as the port does. */
    private static OutboxEntry rebuiltRow(TransitionFiredEvent event) {
        return new OutboxEntry(event.id(), OutboxEntry.KIND_TRANSITION_FIRED, event.payload(), event.occurredAt());
    }

    /**
     * A {@code TransitionFired} event carrying the keys {@code DefaultTransitionService} writes on a row. A
     * {@code null} stamp omits the {@code ontologyVersion} key, because a null payload value is rejected.
     */
    private static TransitionFiredEvent transitionFired(Object ontologyVersion) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "objectId",
                UUID.randomUUID().toString(),
                "tenantId",
                SCOPE.tenantId().value(),
                "principalId",
                SCOPE.principal().id(),
                "transitionName",
                TRANSITION_NAME,
                "fromState",
                DRAFT_IRI,
                "toState",
                ACTIVE_IRI,
                "self",
                Map.of(),
                "typeIri",
                "http://example.org/Project",
                "machineIri",
                MACHINE_IRI));
        if (ontologyVersion != null) {
            payload.put("ontologyVersion", ontologyVersion);
        }
        return new TransitionFiredEvent(UUID.randomUUID(), payload, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
