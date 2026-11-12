package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.ontology.testfixtures.Fixtures.DESCRIPTION_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.WORK_ITEM_IRI;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.testkit.Fixtures;
import org.sequeless.testkit.query.QueryFixtures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresObjectStore#backfill} on its own, against a real PostgreSQL 18 started by
 * Testcontainers: a row written before the search column existed has no search data until the
 * backfill runs, after which a search for its text matches it. The second {@code prepare} for the same
 * schema reports nothing newly registered.
 *
 * <p>{@code prepare} and {@code backfill} are called in separate steps on purpose, to test the
 * backfill alone. The transaction that joins the two in production is proved by the app module's
 * {@code IndexPreparationTransactionProofTest}.
 *
 * <p>Each test empties the {@code public} schema first, so it starts with no migration history.
 */
@Testcontainers
class PostgresSearchVectorBackfillIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

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
    void backfillRecomputesTheSearchVectorOfARowWrittenBeforeTheSearchColumnExisted() {
        // Command V1 to V5: no search_vector column and no search trigger yet.
        PostgresMigrations.migrateTo(dataSource, "5");
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        PostgresObjectStore store = new PostgresObjectStore(jdbcClient, transactionManager);
        PostgresQueryStore queryStore = new PostgresQueryStore(jdbcClient, transactionManager);
        Scope scope = Fixtures.defaultScope();
        Schema schema = QueryFixtures.schema();
        ObjectId id = new ObjectId(UUID.randomUUID());
        store.commit(
                scope,
                new ChangeSet(List.of(new Create(workItem(scope, id, "quarterly ledger reconciliation"))), List.of()));

        PostgresMigrations.migrate(dataSource);
        assertThat(matchesSearch(id, "ledger")).as("before the backfill").isFalse();

        assertThat(queryStore.prepare(scope, schema.ontologyVersion(), schema))
                .as("first prepare registers the searchable properties")
                .isTrue();
        store.backfill(scope);
        assertThat(matchesSearch(id, "ledger")).as("after the backfill").isTrue();

        assertThat(queryStore.prepare(scope, schema.ontologyVersion(), schema))
                .as("a repeat prepare registers nothing new")
                .isFalse();
    }

    /** Whether {@code id}'s stored {@code search_vector} matches a search for {@code term}. */
    private static boolean matchesSearch(ObjectId id, String term) {
        Long matches = JdbcClient.create(dataSource)
                .sql("SELECT COUNT(*) FROM sq_object WHERE id = :id "
                        + "AND search_vector @@ plainto_tsquery('simple', :term)")
                .param("id", id.value())
                .param("term", term)
                .query(Long.class)
                .single();
        return matches == 1L;
    }

    private static BusinessObject workItem(Scope scope, ObjectId id, String description) {
        return new BusinessObject(
                id,
                new TypeRef(WORK_ITEM_IRI),
                scope.tenantId(),
                1,
                Optional.empty(),
                Map.of(new PropertyRef(DESCRIPTION_IRI), Value.text(description)),
                new Audit(NOW, "carol", NOW, "carol"),
                false);
    }
}
