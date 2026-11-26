package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.TemporalTestcontainersSupport;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.PropertyAssignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Spec 17, H7 decision (b): the Store's validation applies to automation's writes. An automated create whose payload
 * has the wrong datatype for a declared property is refused by the Store, and the host records the failure. A scalar for
 * a multi-valued property is accepted, because the host coerces it to a one-element list before the write.
 *
 * <p>The bound {@code ActionExecutor} is the one the Temporal failure recorder wraps. Applying an action outside an
 * activity is its final attempt, so a final failure is recorded and swallowed, as the prototype's
 * {@code TransitionActionWorkflow} recorded it. The refusal is the Store's {@code StoreCommandRejectedException}.
 *
 * <p>Run this class on its own. It starts Postgres and Temporal containers.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "sequeless.persistence.adapter=postgres",
            "sequeless.automation.adapter=temporal",
            "sequeless.expression.adapter=jexl",
            "sequeless.automation.temporal.retry.initial-interval=50ms",
            "sequeless.automation.temporal.retry.backoff-coefficient=1.0",
            "sequeless.automation.temporal.retry.maximum-attempts=3"
        })
class AutomatedWriteShapeRejectionIT extends TemporalTestcontainersSupport {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-shape"), new Principal("user-shape", Set.of()));
    private static final String TASK_IRI = "https://sequeless.dev/ns/ref#Task";
    private static final String PRIORITY_IRI = "https://sequeless.dev/ns/ref#priority";
    private static final String TITLE_IRI = "https://sequeless.dev/ns/ref#title";

    @Autowired
    private ActionExecutor actionExecutor;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void aCreateWithAWrongDatatypeIsRefusedByTheStoreAndTheHostRecordsTheFailure() {
        StoreEventRow row = transitionFired();
        Action action =
                createTask(new PropertyAssignment(PRIORITY_IRI, Optional.of(Value.text("urgent")), Optional.empty()));

        // The final failure is recorded and then swallowed: the call returns normally.
        actionExecutor.applyAction(SCOPE, row, action, 0);

        List<FailureRow> records = recordsFor(row.id() + ":0");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).workflowType()).isEqualTo("TransitionActionWorkflow");
        assertThat(records.get(0).cause())
                .contains("StoreCommandRejectedException")
                .contains("Validation failed (STRUCTURAL)")
                .contains(PRIORITY_IRI)
                .contains("expected an integer");
    }

    @Test
    void aScalarTitleIsAcceptedBecauseTheHostCoercesItToAOneElementList() {
        StoreEventRow row = transitionFired();
        Action action =
                createTask(new PropertyAssignment(TITLE_IRI, Optional.of(Value.text("one title")), Optional.empty()));

        actionExecutor.applyAction(SCOPE, row, action, 0);

        assertThat(recordsFor(row.id() + ":0")).isEmpty();
    }

    private static Action createTask(PropertyAssignment assignment) {
        return new CreateObjectAction(TASK_IRI, List.of(assignment));
    }

    /** A TransitionFired row carrying its provenance stamp, so the released core accepts it. */
    private static StoreEventRow transitionFired() {
        Map<String, Object> payload = Map.of(
                "objectId",
                UUID.randomUUID().toString(),
                "tenantId",
                SCOPE.tenantId().value(),
                "principalId",
                SCOPE.principal().id(),
                "machineIri",
                "https://sequeless.dev/ns/ref#WorkItemLifecycle",
                "fromState",
                "https://sequeless.dev/ns/ref#Open",
                "transitionName",
                "activate",
                "toState",
                "https://sequeless.dev/ns/ref#Active",
                "ontologyVersion",
                1);
        return new StoreEventRow(UUID.randomUUID(), StoreEventRow.KIND_TRANSITION_FIRED, payload, Instant.now());
    }

    private List<FailureRow> recordsFor(String workflowId) {
        return jdbcClient
                .sql("SELECT workflow_type, cause FROM sq_automation_failure WHERE workflow_id = :id")
                .param("id", workflowId)
                .query((rs, rowNum) -> new FailureRow(rs.getString("workflow_type"), rs.getString("cause")))
                .list();
    }

    private record FailureRow(String workflowType, String cause) {}
}
