package org.sequeless.store.postgres;

import java.util.Objects;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.store.spi.AutomationFailure;
import org.sequeless.store.spi.AutomationFailureRecorder;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The Postgres {@link AutomationFailureRecorder}: writes one row to {@code sq_automation_failure} (V12).
 *
 * <p>The insert is {@code ON CONFLICT (workflow_id) DO NOTHING}, so recording the same workflow id twice
 * keeps the first row. A write failure is not caught here, so it propagates to the Temporal record
 * activity, which retries it under its own retry policy.
 */
public final class PostgresAutomationFailureRecorder implements AutomationFailureRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO sq_automation_failure
                (workflow_id, tenant_id, workflow_type, machine_iri, from_state, transition_name, cause)
            VALUES
                (:workflowId, :tenantId, :workflowType, :machineIri, :fromState, :transitionName, :cause)
            ON CONFLICT (workflow_id) DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    /** @param jdbcClient the client bound to the persistence {@code DataSource}; must not be {@code null} */
    public PostgresAutomationFailureRecorder(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    @Override
    public void record(AutomationFailure failure) {
        Objects.requireNonNull(failure, "failure must not be null");
        TransitionRef transition = failure.transition();
        jdbcClient
                .sql(INSERT_SQL)
                .param("workflowId", failure.workflowId())
                .param("tenantId", failure.scope().tenantId().value())
                .param("workflowType", failure.workflowType())
                .param("machineIri", transition == null ? null : transition.stateMachineIri())
                .param("fromState", transition == null ? null : transition.fromStateIri())
                .param("transitionName", transition == null ? null : transition.transitionName())
                .param("cause", failure.cause())
                .update();
    }
}
