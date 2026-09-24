package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.OutboxPort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresOutboxPort} claims one unprocessed {@code ActionRequest} row at a time,
 * skips other outbox kinds, marks a claimed row processed, never lets two concurrent claimers
 * take the same row (thanks to {@code SKIP LOCKED}), and leaves a row unprocessed when the
 * handler throws — against a real PostgreSQL 18 instance started by Testcontainers, mirroring
 * {@link PostgresObjectStoreContractIT}'s own container/migration/truncation pattern.
 *
 * <p>{@code sq_outbox} rows are inserted directly via SQL in these tests rather than through
 * {@link org.sequeless.spi.object.ObjectStorePort#commit}, since the point here is {@link
 * OutboxPort} alone, not the object store's own outbox-writing path (already covered by {@link
 * PostgresObjectStoreContractIT}/{@code ObjectStoreContract}).
 */
@Testcontainers
class PostgresOutboxPortIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource =
            new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PostgresMigrations.migrate(dataSource);
    }

    @AfterEach
    void truncateTables() {
        JdbcClient.create(dataSource).sql("TRUNCATE TABLE sq_object, sq_ontology_document, sq_outbox").update();
    }

    @Test
    void claimReturnsEmptyWhenNothingPending() {
        Optional<UUID> result = freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        assertThat(result).isEmpty();
    }

    @Test
    void claimSkipsNonActionRequestKinds() {
        insertOutboxRow(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, UUID.randomUUID(), Instant.now());

        Optional<UUID> result = freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());

        assertThat(result).isEmpty();
    }

    @Test
    void claimMarksRowProcessedSoASecondClaimReturnsADifferentRow() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Instant now = Instant.now();
        insertOutboxRow(firstId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), now);
        insertOutboxRow(secondId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), now.plusSeconds(1));

        Optional<UUID> first = freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        Optional<UUID> second = freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        Optional<UUID> third = freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());

        assertThat(first).contains(firstId);
        assertThat(second).contains(secondId);
        assertThat(third).isEmpty();
    }

    @Test
    void claimAcrossMultipleKindsReturnsBothInOccurredAtOrder() {
        UUID timerRowId = UUID.randomUUID();
        UUID actionRowId = UUID.randomUUID();
        Instant now = Instant.now();
        insertOutboxRow(timerRowId, OutboxEntry.KIND_TIMER_SCHEDULED, UUID.randomUUID(), now);
        insertOutboxRow(actionRowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), now.plusSeconds(1));

        Set<String> kinds = Set.of(OutboxEntry.KIND_TIMER_SCHEDULED, OutboxEntry.KIND_ACTION_REQUEST);
        Optional<UUID> first = freshPort().claimNext(kinds, (tenantId, entry) -> entry.id());
        Optional<UUID> second = freshPort().claimNext(kinds, (tenantId, entry) -> entry.id());
        Optional<UUID> third = freshPort().claimNext(kinds, (tenantId, entry) -> entry.id());

        assertThat(first).contains(timerRowId);
        assertThat(second).contains(actionRowId);
        assertThat(third).isEmpty();
    }

    @Test
    void claimHandsHandlerTheRowsRawTenantIdAndReconstructedEntry() {
        UUID rowId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        insertOutboxRow("tenant-xyz", rowId, OutboxEntry.KIND_ACTION_REQUEST, objectId, Instant.now());

        Optional<String> observedTenantId =
            freshPort()
                .claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), 
                    (tenantId, entry) -> {
                        assertThat(entry.id()).isEqualTo(rowId);
                        assertThat(entry.kind()).isEqualTo(OutboxEntry.KIND_ACTION_REQUEST);
                        assertThat(entry.payload()).containsEntry("objectId", objectId.toString());
                        return tenantId;
                    });

        assertThat(observedTenantId).contains("tenant-xyz");
    }

    @Test
    void concurrentClaimersNeverGetTheSameRowThanksToSkipLocked() throws InterruptedException {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);

        Thread firstClaimer =
            new Thread(
                () ->
                    freshPort()
                        .claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), 
                            (tenantId, entry) -> {
                                handlerEntered.countDown();
                                await(releaseHandler);
                                return entry.id();
                            }));
        firstClaimer.start();
        handlerEntered.await();

        // The row is locked (FOR UPDATE) by the first claimer's still-open transaction; SKIP
        // LOCKED means this second claim must return immediately, empty, rather than block.
        Optional<UUID> second = freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        assertThat(second).isEmpty();

        releaseHandler.countDown();
        firstClaimer.join();
    }

    @Test
    void handlerExceptionLeavesRowUnprocessedAndPropagates() {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        assertThatThrownBy(
                () ->
                    freshPort()
                        .claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), 
                            (tenantId, entry) -> {
                                throw new RuntimeException("boom");
                            }))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("boom");

        Boolean processed =
            JdbcClient.create(dataSource)
                .sql("SELECT processed_at IS NOT NULL FROM sq_outbox WHERE id = :id")
                .param("id", rowId)
                .query(Boolean.class)
                .single();
        assertThat(processed).isFalse();

        // The row must still be claimable afterwards: the failed transaction released its lock
        // and never stamped processed_at.
        Optional<UUID> retried = freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        assertThat(retried).contains(rowId);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static OutboxPort freshPort() {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        return new PostgresOutboxPort(JdbcClient.create(dataSource), transactionManager);
    }

    private static void insertOutboxRow(UUID id, String kind, UUID objectId, Instant occurredAt) {
        insertOutboxRow("tenant-1", id, kind, objectId, occurredAt);
    }

    private static void insertOutboxRow(
        String tenantId, UUID id, String kind, UUID objectId, Instant occurredAt) {
        JdbcClient.create(dataSource)
            .sql(
                "INSERT INTO sq_outbox (id, tenant_id, object_id, kind, payload, occurred_at, attempts) "
                    + "VALUES (:id, :tenantId, :objectId, :kind, CAST(:payload AS jsonb), :occurredAt, 0)")
            .param("id", id)
            .param("tenantId", tenantId)
            .param("objectId", objectId)
            .param("kind", kind)
            .param("payload", "{\"objectId\": \"" + objectId + "\"}")
            .param("occurredAt", Timestamp.from(occurredAt))
            .update();
    }
}
