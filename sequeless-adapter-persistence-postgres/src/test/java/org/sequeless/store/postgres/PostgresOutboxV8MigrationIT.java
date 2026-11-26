package org.sequeless.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves migration V8 (spec 13, P2d) on a database at V7 against a real PostgreSQL 18 started by
 * Testcontainers. Every unprocessed or dead-lettered {@code ActionRequest} row is marked processed, and
 * so is a {@code TransitionFired} row whose payload has no {@code self} key. The {@code ActionRequest}
 * row that is already processed keeps its {@code processed_at}, and a {@code TransitionFired} row with
 * {@code self} stays unprocessed.
 *
 * <p>The test empties the {@code public} schema first, so it starts from a database with no migration
 * history. It migrates to exactly V7 with {@code migrateTo("7")}, writes the rows, and then migrates to
 * exactly V8 with {@code migrateTo("8")}.
 */
@Testcontainers
class PostgresOutboxV8MigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

    private static DataSource dataSource;

    @BeforeAll
    static void createDataSource() {
        dataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @BeforeEach
    void emptyDatabase() {
        JdbcClient.create(dataSource).sql("DROP SCHEMA public CASCADE").update();
        JdbcClient.create(dataSource).sql("CREATE SCHEMA public").update();
    }

    @Test
    void drainedActionRequestsAndSelfLessTransitionFiredRowsAreMarkedProcessedAndNothingElseIs() {
        PostgresMigrations.migrateTo(dataSource, "7");
        UUID actionRequestUnprocessed = insertRow("ActionRequest", "{}", BASE);
        UUID actionRequestDeadLettered = insertRow("ActionRequest", "{}", BASE.plusSeconds(1));
        markDeadLettered(actionRequestDeadLettered);
        UUID actionRequestProcessed = insertRow("ActionRequest", "{}", BASE.plusSeconds(2));
        markProcessed(actionRequestProcessed, BASE.plusSeconds(20));
        UUID transitionFiredWithSelf = insertRow("TransitionFired", "{\"self\": {}}", BASE.plusSeconds(3));
        UUID transitionFiredWithoutSelf = insertRow("TransitionFired", "{\"objectId\": \"abc\"}", BASE.plusSeconds(4));

        PostgresMigrations.migrateTo(dataSource, "8");

        assertThat(processedAt(actionRequestUnprocessed)).isNotNull();
        assertThat(processedAt(actionRequestDeadLettered)).isNotNull();
        assertThat(processedAt(actionRequestProcessed)).isEqualTo(BASE.plusSeconds(20));
        assertThat(processedAt(transitionFiredWithSelf)).isNull();
        assertThat(processedAt(transitionFiredWithoutSelf)).isNotNull();
    }

    /** The row's {@code processed_at}, or {@code null} while the row is unprocessed. */
    private static Instant processedAt(UUID id) {
        // list(), not single(): single() rejects a NULL column value, and NULL is the state under test.
        List<Timestamp> values = JdbcClient.create(dataSource)
                .sql("SELECT processed_at FROM sq_outbox WHERE id = :id")
                .param("id", id)
                .query(Timestamp.class)
                .list();
        Timestamp processedAt = values.get(0);
        return processedAt == null ? null : processedAt.toInstant();
    }

    /** Inserts one unprocessed outbox row of {@code kind} with the given JSON payload. */
    private static UUID insertRow(String kind, String payload, Instant occurredAt) {
        UUID id = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        JdbcClient.create(dataSource)
                .sql("INSERT INTO sq_outbox (id, tenant_id, object_id, kind, payload, occurred_at, attempts) "
                        + "VALUES (:id, 'tenant-1', :objectId, :kind, CAST(:payload AS jsonb), :occurredAt, 0)")
                .param("id", id)
                .param("objectId", objectId)
                .param("kind", kind)
                .param("payload", payload)
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
        return id;
    }

    /** Marks the row processed at {@code processedAt}. */
    private static void markProcessed(UUID id, Instant processedAt) {
        JdbcClient.create(dataSource)
                .sql("UPDATE sq_outbox SET processed_at = :processedAt WHERE id = :id")
                .param("processedAt", Timestamp.from(processedAt))
                .param("id", id)
                .update();
    }

    /**
     * Dead-letters the row as {@code PostgresOutboxPort} does after three failed dispatches: {@code
     * dead_lettered_at} and {@code error} are set, and {@code processed_at} stays NULL.
     */
    private static void markDeadLettered(UUID id) {
        JdbcClient.create(dataSource)
                .sql("UPDATE sq_outbox SET fail_count = 3, dead_lettered_at = now(), error = 'dispatch failed' "
                        + "WHERE id = :id")
                .param("id", id)
                .update();
    }
}
