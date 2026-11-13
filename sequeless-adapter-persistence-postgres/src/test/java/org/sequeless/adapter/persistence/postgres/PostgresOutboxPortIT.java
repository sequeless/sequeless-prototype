package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.spi.outbox.OutboxPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresOutboxPort} claims one unprocessed {@code ActionRequest} row at a time,
 * skips other outbox kinds, marks a claimed row processed, never lets two concurrent claimers
 * take the same row (thanks to {@code SKIP LOCKED}), leaves a row unprocessed when the handler
 * throws, and dead-letters a row after its third failed dispatch (DR-23) — against a real
 * PostgreSQL 18 instance started by Testcontainers, mirroring
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
    void claimReturnsEmptyWhenNothingPending() {
        Optional<UUID> result =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        assertThat(result).isEmpty();
    }

    @Test
    void claimSkipsNonActionRequestKinds() {
        insertOutboxRow(UUID.randomUUID(), OutboxEntry.KIND_OBJECT_CREATED, UUID.randomUUID(), Instant.now());

        Optional<UUID> result =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());

        assertThat(result).isEmpty();
    }

    @Test
    void claimMarksRowProcessedSoASecondClaimReturnsADifferentRow() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Instant now = Instant.now();
        insertOutboxRow(firstId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), now);
        insertOutboxRow(secondId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), now.plusSeconds(1));

        Optional<UUID> first =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        Optional<UUID> second =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        Optional<UUID> third =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());

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

        Optional<String> observedTenantId = freshPort()
                .claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> {
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
                new Thread(() -> freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> {
                    handlerEntered.countDown();
                    await(releaseHandler);
                    return entry.id();
                }));
        firstClaimer.start();
        handlerEntered.await();

        // The row is locked (FOR UPDATE) by the first claimer's still-open transaction; SKIP
        // LOCKED means this second claim must return immediately, empty, rather than block.
        Optional<UUID> second =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        assertThat(second).isEmpty();

        releaseHandler.countDown();
        firstClaimer.join();
    }

    @Test
    void handlerExceptionLeavesRowUnprocessedAndPropagates() {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        assertThatThrownBy(() -> freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> {
                    throw new RuntimeException("boom");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        Boolean processed = JdbcClient.create(dataSource)
                .sql("SELECT processed_at IS NOT NULL FROM sq_outbox WHERE id = :id")
                .param("id", rowId)
                .query(Boolean.class)
                .single();
        assertThat(processed).isFalse();

        // The row must still be claimable afterwards: the failed transaction released its lock
        // and never stamped processed_at.
        Optional<UUID> retried =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        assertThat(retried).contains(rowId);
    }

    @Test
    void rowWhoseHandlerAlwaysThrowsIsDeadLetteredAfterThreeFailedDispatches() {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        for (int failure = 0; failure < 3; failure++) {
            assertThatThrownBy(PostgresOutboxPortIT::claimThatThrows).hasMessage("boom");
        }

        AtomicInteger handlerCalls = new AtomicInteger();
        Optional<UUID> claimedAfterThree = freshPort()
                .claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> {
                    handlerCalls.incrementAndGet();
                    return entry.id();
                });

        assertThat(claimedAfterThree).isEmpty();
        assertThat(handlerCalls).hasValue(0);
        assertThat(failCountOf(rowId)).isEqualTo(3);
        assertThat(isDeadLettered(rowId)).isTrue();
        assertThat(errorOf(rowId)).isEqualTo("boom");
        assertThat(isProcessed(rowId)).isFalse();
    }

    @Test
    void rowBehindADeadLetteredRowIsStillClaimedAndProcessed() {
        UUID deadLetteredId = UUID.randomUUID();
        UUID behindId = UUID.randomUUID();
        Instant now = Instant.now();
        insertOutboxRow(deadLetteredId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), now);
        insertOutboxRow(behindId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), now.plusSeconds(1));

        for (int failure = 0; failure < 3; failure++) {
            assertThatThrownBy(PostgresOutboxPortIT::claimThatThrows).hasMessage("boom");
        }
        assertThat(isDeadLettered(deadLetteredId)).isTrue();

        Optional<UUID> next =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());

        assertThat(next).contains(behindId);
        assertThat(isProcessed(behindId)).isTrue();
        assertThat(isProcessed(deadLetteredId)).isFalse();
    }

    @Test
    void rowThatFailsTwiceAndThenSucceedsIsProcessedAndNotDeadLettered() {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        for (int failure = 0; failure < 2; failure++) {
            assertThatThrownBy(PostgresOutboxPortIT::claimThatThrows).hasMessage("boom");
        }

        Optional<UUID> third =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());

        assertThat(third).contains(rowId);
        assertThat(failCountOf(rowId)).isEqualTo(2);
        assertThat(isDeadLettered(rowId)).isFalse();
        assertThat(isProcessed(rowId)).isTrue();
    }

    @Test
    void failureToCountADispatchIsSuppressedAndTheHandlerExceptionStillPropagates() {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        refuseFailCountWrites();
        Throwable thrown;
        try {
            thrown = catchThrowable(PostgresOutboxPortIT::claimThatThrows);
        } finally {
            allowFailCountWrites();
        }

        assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("boom");
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0])
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fail_count write refused");
        assertThat(failCountOf(rowId)).isZero();
        assertThat(isDeadLettered(rowId)).isFalse();
        assertThat(errorOf(rowId)).isNull();
        assertThat(isProcessed(rowId)).isFalse();
    }

    @Test
    void nonVirtualMachineErrorIsCountedLikeARuntimeExceptionAndDeadLettersTheRow() {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        for (int failure = 0; failure < 3; failure++) {
            assertThatThrownBy(() -> claimThatThrowsError(new LinkageError("boom")))
                    .isInstanceOf(LinkageError.class)
                    .hasMessage("boom");
        }

        assertThat(failCountOf(rowId)).isEqualTo(3);
        assertThat(isDeadLettered(rowId)).isTrue();
        assertThat(errorOf(rowId)).isEqualTo("boom");
        assertThat(isProcessed(rowId)).isFalse();
    }

    @Test
    void virtualMachineErrorIsNotCountedSoTheRowStaysClaimable() {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        for (int failure = 0; failure < 3; failure++) {
            assertThatThrownBy(() -> claimThatThrowsError(new OutOfMemoryError("boom")))
                    .isInstanceOf(OutOfMemoryError.class)
                    .hasMessage("boom");
        }

        assertThat(failCountOf(rowId)).isZero();
        assertThat(isDeadLettered(rowId)).isFalse();
        assertThat(errorOf(rowId)).isNull();
        assertThat(isProcessed(rowId)).isFalse();

        Optional<UUID> retried =
                freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> entry.id());
        assertThat(retried).contains(rowId);
    }

    @Test
    void failureWithNoMessageIsDeadLetteredWithItsClassNameAsTheError() {
        UUID rowId = UUID.randomUUID();
        insertOutboxRow(rowId, OutboxEntry.KIND_ACTION_REQUEST, UUID.randomUUID(), Instant.now());

        for (int failure = 0; failure < 3; failure++) {
            assertThatThrownBy(PostgresOutboxPortIT::claimThatThrowsWithoutMessage)
                    .isInstanceOf(IllegalStateException.class)
                    .extracting(Throwable::getMessage)
                    .isNull();
        }

        assertThat(failCountOf(rowId)).isEqualTo(3);
        assertThat(isDeadLettered(rowId)).isTrue();
        assertThat(errorOf(rowId)).isEqualTo("java.lang.IllegalStateException");
        assertThat(isProcessed(rowId)).isFalse();
    }

    private static void claimThatThrows() {
        freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> {
            throw new RuntimeException("boom");
        });
    }

    private static void claimThatThrowsError(Error error) {
        freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> {
            throw error;
        });
    }

    private static void claimThatThrowsWithoutMessage() {
        freshPort().claimNext(Set.of(OutboxEntry.KIND_ACTION_REQUEST), (tenantId, entry) -> {
            throw new IllegalStateException();
        });
    }

    /** Makes every write to fail_count throw, as a database that rejects the failure count would. */
    private static void refuseFailCountWrites() {
        JdbcClient.create(dataSource)
                .sql("CREATE OR REPLACE FUNCTION refuse_fail_count() RETURNS trigger LANGUAGE plpgsql AS $$ "
                        + "BEGIN RAISE EXCEPTION 'fail_count write refused'; END; $$")
                .update();
        JdbcClient.create(dataSource)
                .sql("CREATE TRIGGER refuse_fail_count BEFORE UPDATE OF fail_count ON sq_outbox "
                        + "FOR EACH ROW EXECUTE FUNCTION refuse_fail_count()")
                .update();
    }

    private static void allowFailCountWrites() {
        JdbcClient.create(dataSource)
                .sql("DROP TRIGGER IF EXISTS refuse_fail_count ON sq_outbox")
                .update();
        JdbcClient.create(dataSource)
                .sql("DROP FUNCTION IF EXISTS refuse_fail_count()")
                .update();
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
        // list() rather than single(): single() rejects a null value, and error is legitimately null.
        return JdbcClient.create(dataSource)
                .sql("SELECT " + expression + " FROM sq_outbox WHERE id = :id")
                .param("id", id)
                .query(type)
                .list()
                .getFirst();
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

    private static void insertOutboxRow(String tenantId, UUID id, String kind, UUID objectId, Instant occurredAt) {
        JdbcClient.create(dataSource)
                .sql("INSERT INTO sq_outbox (id, tenant_id, object_id, kind, payload, occurred_at, attempts) "
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
