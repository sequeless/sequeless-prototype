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
 * Proves migration V5 (spec 13, P2a) on a database at V4 against a real PostgreSQL 18 started by
 * Testcontainers. Pre-existing unprocessed {@code TransitionFired} rows are marked processed and are
 * never claimed. The rows of every other kind keep {@code processed_at} NULL. The claim index covers
 * the eight claimable kinds, {@code TransitionFired} included.
 *
 * <p>Each test empties the {@code public} schema first, so it starts from a database with no migration
 * history. Every test migrates to exactly V5 with {@code migrateTo("5")}, so no migration after V5 is
 * applied. The pre-existing-rows test first calls {@code migrateTo("4")}, which stops at V4 so the seeded
 * rows can be written before V5 runs.
 */
@Testcontainers
class PostgresOutboxV5MigrationIT {

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
    void emptyDatabase() {
        JdbcClient.create(dataSource).sql("DROP SCHEMA public CASCADE").update();
        JdbcClient.create(dataSource).sql("CREATE SCHEMA public").update();
    }

    @Test
    void preExistingTransitionFiredRowsAreMarkedProcessedAndOtherKindsStayClaimable() {
        PostgresMigrations.migrateTo(dataSource, "4");
        UUID firedOne = insertRow("TransitionFired", BASE, null);
        UUID firedTwo = insertRow("TransitionFired", BASE.plusSeconds(1), null);
        UUID firedDone = insertRow("TransitionFired", BASE.plusSeconds(2), BASE.plusSeconds(20));
        UUID actionRequest = insertRow("ActionRequest", BASE.plusSeconds(3), null);
        UUID timerScheduled = insertRow("TimerScheduled", BASE.plusSeconds(4), null);
        UUID timerCancelled = insertRow("TimerCancelled", BASE.plusSeconds(5), null);
        UUID signalReceived = insertRow("SignalReceived", BASE.plusSeconds(6), null);
        UUID objectCreated = insertRow("ObjectCreated", BASE.plusSeconds(7), null);

        PostgresMigrations.migrateTo(dataSource, "5");

        assertThat(processedAt(firedOne)).isNotNull();
        assertThat(processedAt(firedTwo)).isNotNull();
        assertThat(processedAt(firedDone)).isEqualTo(BASE.plusSeconds(20));
        assertThat(processedAt(actionRequest)).isNull();
        assertThat(processedAt(timerScheduled)).isNull();
        assertThat(processedAt(timerCancelled)).isNull();
        assertThat(processedAt(signalReceived)).isNull();
        assertThat(processedAt(objectCreated)).isNull();

        assertThat(claimAll())
                .containsExactly(actionRequest, timerScheduled, timerCancelled, signalReceived, objectCreated);
    }

    @Test
    void transitionFiredRowWrittenAfterV5IsClaimed() {
        PostgresMigrations.migrateTo(dataSource, "5");
        UUID fired = insertRow("TransitionFired", BASE, null);

        assertThat(claimAll()).containsExactly(fired);
    }

    @Test
    void claimIndexCoversTheEightClaimableKindsIncludingTransitionFired() {
        PostgresMigrations.migrateTo(dataSource, "5");

        List<String> outboxIndexes = JdbcClient.create(dataSource)
                .sql("SELECT indexname FROM pg_indexes WHERE tablename = 'sq_outbox'"
                        + " AND indexname LIKE 'sq_outbox_unprocessed%'")
                .query(String.class)
                .list();
        assertThat(outboxIndexes).containsExactly("sq_outbox_unprocessed_dispatchable_idx");

        String definition = JdbcClient.create(dataSource)
                .sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'sq_outbox_unprocessed_dispatchable_idx'")
                .query(String.class)
                .single();
        for (String kind : CLAIMABLE_KINDS) {
            assertThat(definition).as("claim index covers %s", kind).contains("'" + kind + "'::text");
        }
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
}
