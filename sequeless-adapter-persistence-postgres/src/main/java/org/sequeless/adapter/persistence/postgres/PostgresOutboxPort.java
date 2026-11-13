package org.sequeless.adapter.persistence.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.spi.outbox.OutboxPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The PostgreSQL-backed {@link OutboxPort}: claims at most one unprocessed {@link OutboxEntry} row
 * whose {@code kind} is in the caller's requested set from {@code sq_outbox} per call ({@code
 * SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1}, ordered by {@code occurred_at}), hands it to the
 * caller's handler, and marks it processed — all inside one {@link TransactionTemplate}
 * transaction, so a handler exception rolls the whole thing back (the row stays unprocessed, its
 * lock released for the next poll) using {@link TransactionTemplate}'s default
 * rollback-on-{@code RuntimeException} behaviour, with no extra rollback-rule configuration
 * needed.
 *
 * <p>A failed dispatch is also counted, in a transaction of its own after that rollback: {@code
 * fail_count} goes up by one, and at {@link #DEAD_LETTER_THRESHOLD} failures the row is
 * dead-lettered ({@code dead_lettered_at} and {@code error} are stamped). The claim skips a
 * dead-lettered row, so the rows behind it keep flowing; it keeps {@code processed_at} NULL (DR-23).
 *
 * <p>The {@code kind IN (:kinds)} clause below relies on Spring's {@code NamedParameterJdbcTemplate}
 * (which {@link JdbcClient} wraps) automatically expanding a {@link Set} parameter into one bind
 * placeholder per element — there is no array-typed column or {@code ANY(?)} binding involved.
 * {@code kinds} is always a small, code-controlled constant (never user input), so this is not a
 * SQL-injection concern.
 *
 * <p>{@code entry.id()} on the reconstructed {@link OutboxEntry} always comes from the row's own
 * {@code id} column — never from the payload's own {@code "objectId"} entry, which is a different,
 * unrelated UUID identifying the transitioning object, not this outbox row.
 */
public final class PostgresOutboxPort implements OutboxPort {

    /**
     * The failed dispatches after which a row is dead-lettered (DR-23): the third failure stamps
     * {@code dead_lettered_at}, and the claim skips the row from then on.
     */
    private static final int DEAD_LETTER_THRESHOLD = 3;

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param jdbcClient the client every read and write here runs through; must not be {@code
     *     null}
     * @param transactionManager the transaction manager {@link #claimNext} wraps its work in; must
     *     not be {@code null}
     */
    public PostgresOutboxPort(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> Optional<T> claimNext(Set<String> kinds, BiFunction<String, OutboxEntry, T> handler) {
        Objects.requireNonNull(kinds, "kinds must not be null");
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("kinds must not be empty");
        }
        Objects.requireNonNull(handler, "handler must not be null");

        // Holds the id of the row being dispatched. It is set as soon as the row is read, before its payload
        // is decoded, so a row whose payload fails to decode is still counted. The id is set inside the claim
        // transaction. If the transaction then fails, the id tells the failure path which row to count.
        AtomicReference<UUID> dispatchingId = new AtomicReference<>();
        try {
            return transactionTemplate.execute(status -> {
                Optional<ClaimedRow> claimed = jdbcClient
                        .sql("SELECT id, tenant_id, kind, payload, occurred_at FROM sq_outbox "
                                + "WHERE kind IN (:kinds) AND processed_at IS NULL AND dead_lettered_at IS NULL "
                                + "ORDER BY occurred_at LIMIT 1 FOR UPDATE SKIP LOCKED")
                        .param("kinds", kinds)
                        .query((rs, rowNum) -> {
                            dispatchingId.set((UUID) rs.getObject("id"));
                            return mapRow(rs, rowNum);
                        })
                        .optional();

                if (claimed.isEmpty()) {
                    return Optional.empty();
                }

                ClaimedRow row = claimed.get();
                dispatchingId.set(row.entry().id());
                T result = handler.apply(row.tenantId(), row.entry());

                jdbcClient
                        .sql("UPDATE sq_outbox SET processed_at = :processedAt WHERE id = :id")
                        .param("processedAt", Timestamp.from(Instant.now()))
                        .param("id", row.entry().id())
                        .update();

                return Optional.ofNullable(result);
            });
        } catch (RuntimeException | Error failure) {
            // The claim transaction has rolled back, so the row's lock is released. Count the failure
            // in a transaction of its own. The failure still propagates to the caller.
            // A VirtualMachineError (out of memory and similar) is not tied to the row, so it is not counted.
            UUID failedId = dispatchingId.get();
            if (failedId != null && !(failure instanceof VirtualMachineError)) {
                recordFailure(failedId, failure);
            }
            throw failure;
        }
    }

    /**
     * Counts one failed dispatch of the row {@code id} in a transaction of its own, after the claim has
     * rolled back, and dead-letters the row once it reaches {@link #DEAD_LETTER_THRESHOLD}. A row that
     * another relay has processed since the rollback is left alone: it is neither counted nor
     * dead-lettered. A failure to record is attached to {@code failure} as suppressed rather than
     * replacing it, so the caller still sees the handler's exception. The row then stays claimable,
     * with its count unchanged.
     */
    private void recordFailure(UUID id, Throwable failure) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Optional<Integer> failCount = jdbcClient
                        .sql("UPDATE sq_outbox SET fail_count = fail_count + 1 WHERE id = :id "
                                + "AND processed_at IS NULL RETURNING fail_count")
                        .param("id", id)
                        .query(Integer.class)
                        .optional();
                if (failCount.isEmpty()) {
                    return; // processed by another relay since the rollback: nothing to count
                }
                if (failCount.get() >= DEAD_LETTER_THRESHOLD) {
                    jdbcClient
                            .sql("UPDATE sq_outbox SET dead_lettered_at = now(), error = :error WHERE id = :id")
                            .param("error", errorText(failure))
                            .param("id", id)
                            .update();
                }
            });
        } catch (RuntimeException | Error recordingFailure) {
            failure.addSuppressed(recordingFailure);
        }
    }

    /** The handler's exception message, or its class name when the exception has no message. */
    private static String errorText(Throwable failure) {
        return failure.getMessage() != null
                ? failure.getMessage()
                : failure.getClass().getName();
    }

    private record ClaimedRow(String tenantId, OutboxEntry entry) {}

    private static ClaimedRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String tenantId = rs.getString("tenant_id");
        String kind = rs.getString("kind");
        Map<String, Object> payload = OutboxPayloadJsonCodec.fromJson(rs.getString("payload"));
        Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();
        return new ClaimedRow(tenantId, new OutboxEntry(id, kind, payload, occurredAt));
    }
}
