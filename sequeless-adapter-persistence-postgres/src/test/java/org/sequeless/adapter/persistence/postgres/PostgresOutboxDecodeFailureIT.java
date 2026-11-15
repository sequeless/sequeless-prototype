package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.spi.outbox.OutboxPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Proves that a row whose payload {@link OutboxPayloadJsonCodec} cannot decode is counted as a failed
 * dispatch (DR-23): each claim that fails to decode it adds one to {@code fail_count}, the third one
 * dead-letters it, and the claim then skips it so the healthy row behind it is claimed and processed.
 * {@link PostgresOutboxPort} sets the row's id before it decodes the payload, so the failure path can
 * count the row. Against a real PostgreSQL 18 instance started by Testcontainers, mirroring {@link
 * PostgresOutboxPortIT}'s container, migration and truncation pattern.
 */
@Testcontainers
class PostgresOutboxDecodeFailureIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    /** A JSON array is valid {@code jsonb} but not a JSON object, so the codec's {@code Map} decode rejects it. */
    private static final String UNDECODABLE_PAYLOAD = "[1, 2, 3]";

    private static final Set<String> CLAIMABLE_KINDS =
            Set.of(OutboxEntry.KIND_ACTION_REQUEST, OutboxEntry.KIND_TIMER_SCHEDULED);

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

    private static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PostgresMigrations.migrate(dataSource);
    }

    @AfterEach
    void truncateTables() {
        JdbcClient.create(dataSource)
                .sql("TRUNCATE TABLE sq_object, sq_ontology_document, sq_outbox")
                .update();
    }

    @Test
    void eachClaimThatFailsToDecodeCountsOneFailedDispatch() {
        UUID badId = UUID.randomUUID();
        insertOutboxRow(badId, UNDECODABLE_PAYLOAD, BASE);

        for (int failure = 1; failure <= 3; failure++) {
            assertThatThrownBy(PostgresOutboxDecodeFailureIT::claim).isInstanceOf(MismatchedInputException.class);
            assertThat(failCountOf(badId)).isEqualTo(failure);
        }
    }

    @Test
    void undecodablePayloadIsDeadLetteredAtTheThirdFailedClaim() {
        UUID badId = UUID.randomUUID();
        insertOutboxRow(badId, UNDECODABLE_PAYLOAD, BASE);

        for (int failure = 0; failure < 3; failure++) {
            assertThatThrownBy(PostgresOutboxDecodeFailureIT::claim).isInstanceOf(MismatchedInputException.class);
        }

        assertThat(failCountOf(badId)).isEqualTo(3);
        assertThat(isDeadLettered(badId)).isTrue();
        assertThat(errorOf(badId)).isNotNull();
        assertThat(isProcessed(badId)).isFalse();
    }

    @Test
    void queueFlowsPastADeadLetteredUndecodableRow() {
        UUID badId = UUID.randomUUID();
        UUID healthyId = UUID.randomUUID();
        insertOutboxRow(badId, UNDECODABLE_PAYLOAD, BASE);
        insertOutboxRow(healthyId, healthyPayload(), BASE.plusSeconds(1));

        for (int failure = 0; failure < 3; failure++) {
            assertThatThrownBy(PostgresOutboxDecodeFailureIT::claim).isInstanceOf(MismatchedInputException.class);
        }

        Optional<UUID> next = claim();

        assertThat(next).contains(healthyId);
        assertThat(isProcessed(healthyId)).isTrue();
        assertThat(isProcessed(badId)).isFalse();
        assertThat(isDeadLettered(badId)).isTrue();
    }

    /** Claims the next row of the claimable kinds, with a handler that returns the row's id. */
    private static Optional<UUID> claim() {
        return freshPort().claimNext(CLAIMABLE_KINDS, (tenantId, entry) -> entry.id());
    }

    private static Boolean isProcessed(UUID id) {
        return outboxColumn("processed_at IS NOT NULL", id, Boolean.class);
    }

    private static Boolean isDeadLettered(UUID id) {
        return outboxColumn("dead_lettered_at IS NOT NULL", id, Boolean.class);
    }

    private static Integer failCountOf(UUID id) {
        return outboxColumn("fail_count", id, Integer.class);
    }

    private static String errorOf(UUID id) {
        return outboxColumn("error", id, String.class);
    }

    private static <V> V outboxColumn(String expression, UUID id, Class<V> type) {
        return JdbcClient.create(dataSource)
                .sql("SELECT " + expression + " FROM sq_outbox WHERE id = :id")
                .param("id", id)
                .query(type)
                .single();
    }

    private static OutboxPort freshPort() {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        return new PostgresOutboxPort(JdbcClient.create(dataSource), transactionManager);
    }

    private static String healthyPayload() {
        return "{\"objectId\": \"" + UUID.randomUUID() + "\"}";
    }

    /** Inserts one unprocessed {@code ActionRequest} row carrying {@code payload} as its JSON text. */
    private static void insertOutboxRow(UUID id, String payload, Instant occurredAt) {
        JdbcClient.create(dataSource)
                .sql("INSERT INTO sq_outbox (id, tenant_id, object_id, kind, payload, occurred_at, attempts) "
                        + "VALUES (:id, 'tenant-1', :objectId, :kind, CAST(:payload AS jsonb), :occurredAt, 0)")
                .param("id", id)
                .param("objectId", UUID.randomUUID())
                .param("kind", OutboxEntry.KIND_ACTION_REQUEST)
                .param("payload", payload)
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }
}
