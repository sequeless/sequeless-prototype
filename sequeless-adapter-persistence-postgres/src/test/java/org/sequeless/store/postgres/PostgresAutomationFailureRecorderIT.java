package org.sequeless.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.store.spi.AutomationFailure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the Postgres {@link PostgresAutomationFailureRecorder} (spec 13, R2) against a real PostgreSQL 18 started
 * by Testcontainers, with the whole command migration set applied, including V12 ({@code sq_automation_failure}).
 *
 * <p>Covers a change-event record, which has no transition, and a transition-action record, which keeps its three
 * transition columns. It also covers the idempotent insert: a second record for the same workflow id keeps the
 * first row.
 */
@Testcontainers
class PostgresAutomationFailureRecorderIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final TransitionRef TRANSITION =
            new TransitionRef("http://example.org/ProjectLifecycle", "http://example.org/Draft", "activate");

    private static DataSource dataSource;
    private static JdbcClient jdbcClient;
    private static PostgresAutomationFailureRecorder recorder;

    @BeforeAll
    static void migrateAndCreateRecorder() {
        dataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PostgresMigrations.migrate(dataSource);
        jdbcClient = JdbcClient.create(dataSource);
        recorder = new PostgresAutomationFailureRecorder(jdbcClient);
    }

    @BeforeEach
    void emptyTable() {
        jdbcClient.sql("DELETE FROM sq_automation_failure").update();
    }

    @Test
    void recordsAChangeEventFailureWithNullTransitionColumns() {
        recorder.record(new AutomationFailure(
                SCOPE, "change-event-1", "ChangeEventWorkflow", null, "java.lang.IllegalStateException: broke"));

        Stored stored = storedRow("change-event-1");
        assertThat(stored.tenantId()).isEqualTo("tenant-1");
        assertThat(stored.workflowType()).isEqualTo("ChangeEventWorkflow");
        assertThat(stored.machineIri()).isNull();
        assertThat(stored.fromState()).isNull();
        assertThat(stored.transitionName()).isNull();
        assertThat(stored.cause()).isEqualTo("java.lang.IllegalStateException: broke");
    }

    @Test
    void recordsATransitionActionFailureWithItsThreeTransitionColumns() {
        recorder.record(new AutomationFailure(
                SCOPE, "event-2:0", "TransitionActionWorkflow", TRANSITION, "TransitionFiredStampMissing: no stamp"));

        Stored stored = storedRow("event-2:0");
        assertThat(stored.workflowType()).isEqualTo("TransitionActionWorkflow");
        assertThat(stored.machineIri()).isEqualTo("http://example.org/ProjectLifecycle");
        assertThat(stored.fromState()).isEqualTo("http://example.org/Draft");
        assertThat(stored.transitionName()).isEqualTo("activate");
    }

    @Test
    void recordingTheSameWorkflowIdTwiceKeepsTheFirstRecord() {
        recorder.record(
                new AutomationFailure(SCOPE, "event-3:1", "TransitionActionWorkflow", TRANSITION, "first cause"));

        recorder.record(
                new AutomationFailure(SCOPE, "event-3:1", "TransitionActionWorkflow", TRANSITION, "second cause"));

        Long rows = jdbcClient
                .sql("SELECT count(*) FROM sq_automation_failure WHERE workflow_id = :id")
                .param("id", "event-3:1")
                .query(Long.class)
                .single();
        assertThat(rows).isEqualTo(1L);
        assertThat(storedRow("event-3:1").cause()).isEqualTo("first cause");
    }

    @Test
    void stampsFailedAtFromTheDatabaseWhenTheRecordIsWritten() {
        recorder.record(new AutomationFailure(SCOPE, "event-4", "ChangeEventWorkflow", null, "boom"));

        Boolean stamped = jdbcClient
                .sql("SELECT failed_at IS NOT NULL FROM sq_automation_failure WHERE workflow_id = :id")
                .param("id", "event-4")
                .query(Boolean.class)
                .single();
        assertThat(stamped).isTrue();
    }

    private static Stored storedRow(String workflowId) {
        return jdbcClient
                .sql("SELECT tenant_id, workflow_type, machine_iri, from_state, transition_name, cause"
                        + " FROM sq_automation_failure WHERE workflow_id = :id")
                .param("id", workflowId)
                .query((rs, rowNum) -> new Stored(
                        rs.getString("tenant_id"),
                        rs.getString("workflow_type"),
                        rs.getString("machine_iri"),
                        rs.getString("from_state"),
                        rs.getString("transition_name"),
                        rs.getString("cause")))
                .single();
    }

    private record Stored(
            String tenantId,
            String workflowType,
            String machineIri,
            String fromState,
            String transitionName,
            String cause) {}
}
