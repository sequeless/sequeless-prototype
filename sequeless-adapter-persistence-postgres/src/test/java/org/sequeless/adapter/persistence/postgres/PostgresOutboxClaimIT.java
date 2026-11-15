package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the claim of {@link PostgresOutboxPort} on the latest schema, against a real PostgreSQL 18
 * started by Testcontainers. Each test migrates the empty database to the latest schema with {@link
 * PostgresMigrations#migrate}, so the claim query runs with every column it reads, including the
 * {@code dead_lettered_at} that V7 adds. A processed row and a dead-lettered row are never claimed.
 * An unprocessed {@code TransitionFired} row is claimed, because V5 made that kind claimable.
 *
 * <p>Each test empties the {@code public} schema first, so it starts with no migration history.
 */
@Testcontainers
class PostgresOutboxClaimIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final Set<String> CLAIMABLE_KINDS = Set.of(
            "ActionRequest",
            "ObjectCreated",
            "ObjectUpdated",
            "ObjectDeleted",
            "TransitionFired",
            "TimerScheduled",
            "TimerCancelled",
            "SignalReceived");

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

    private static DataSource dataSource;

    @BeforeAll
    static void createDataSource() {
        dataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @BeforeEach
    void migrateEmptyDatabase() {
        JdbcClient.create(dataSource).sql("DROP SCHEMA public CASCADE").update();
        JdbcClient.create(dataSource).sql("CREATE SCHEMA public").update();
        PostgresMigrations.migrate(dataSource);
    }

    @Test
    void claimAllReturnsOnlyTheUnprocessedRowThatIsNotDeadLettered() {
        insertRow("TransitionFired", BASE, BASE.plusSeconds(20));
        UUID unprocessed = insertRow("ActionRequest", BASE.plusSeconds(1), null);
        UUID deadLettered = insertRow("TimerScheduled", BASE.plusSeconds(2), null);
        deadLetter(deadLettered);

        assertThat(claimAll()).containsExactly(unprocessed);
        assertThat(processedAt(unprocessed)).isNotNull();
        assertThat(processedAt(deadLettered)).isNull();
    }

    @Test
    void transitionFiredRowWrittenAfterV5IsClaimed() {
        UUID fired = insertRow("TransitionFired", BASE, null);

        assertThat(claimAll()).containsExactly(fired);
    }

    /** Claims every row of the claimable kinds, in order, until none is left. */
    private static List<UUID> claimAll() {
        PostgresOutboxPort port =
                new PostgresOutboxPort(JdbcClient.create(dataSource), new DataSourceTransactionManager(dataSource));
        List<UUID> claimed = new ArrayList<>();
        Optional<UUID> next = port.claimNext(CLAIMABLE_KINDS, (tenantId, entry) -> entry.id());
        while (next.isPresent()) {
            claimed.add(next.get());
            next = port.claimNext(CLAIMABLE_KINDS, (tenantId, entry) -> entry.id());
        }
        return claimed;
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

    /** Inserts one outbox row of {@code kind}, marking it processed at {@code processedAt} when non-null. */
    private static UUID insertRow(String kind, Instant occurredAt, Instant processedAt) {
        UUID id = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        JdbcClient.create(dataSource)
                .sql("INSERT INTO sq_outbox (id, tenant_id, object_id, kind, payload, occurred_at, attempts) "
                        + "VALUES (:id, 'tenant-1', :objectId, :kind, CAST(:payload AS jsonb), :occurredAt, 0)")
                .param("id", id)
                .param("objectId", objectId)
                .param("kind", kind)
                .param("payload", "{\"objectId\": \"" + objectId + "\"}")
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
        if (processedAt != null) {
            JdbcClient.create(dataSource)
                    .sql("UPDATE sq_outbox SET processed_at = :processedAt WHERE id = :id")
                    .param("processedAt", Timestamp.from(processedAt))
                    .param("id", id)
                    .update();
        }
        return id;
    }

    /** Stamps the row as dead-lettered after three failed dispatches, as the failure path does. */
    private static void deadLetter(UUID id) {
        JdbcClient.create(dataSource)
                .sql("UPDATE sq_outbox SET fail_count = 3, dead_lettered_at = now(), error = 'boom' WHERE id = :id")
                .param("id", id)
                .update();
    }
}
