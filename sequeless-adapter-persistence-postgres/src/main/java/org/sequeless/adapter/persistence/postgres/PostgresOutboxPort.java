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
import java.util.function.BiFunction;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.OutboxPort;

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

        return transactionTemplate.execute(status -> {
            Optional<ClaimedRow> claimed =
                jdbcClient
                    .sql(
                        "SELECT id, tenant_id, kind, payload, occurred_at FROM sq_outbox "
                            + "WHERE kind IN (:kinds) AND processed_at IS NULL "
                            + "ORDER BY occurred_at LIMIT 1 FOR UPDATE SKIP LOCKED")
                    .param("kinds", kinds)
                    .query(PostgresOutboxPort::mapRow)
                    .optional();

            if (claimed.isEmpty()) {
                return Optional.empty();
            }

            ClaimedRow row = claimed.get();
            T result = handler.apply(row.tenantId(), row.entry());

            jdbcClient
                .sql("UPDATE sq_outbox SET processed_at = :processedAt WHERE id = :id")
                .param("processedAt", Timestamp.from(Instant.now()))
                .param("id", row.entry().id())
                .update();

            return Optional.ofNullable(result);
        });
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
