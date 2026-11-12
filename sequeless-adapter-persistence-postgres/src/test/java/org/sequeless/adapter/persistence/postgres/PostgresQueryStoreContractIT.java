package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.testkit.Fixtures;
import org.sequeless.testkit.query.BrowseContract;
import org.sequeless.testkit.query.QueryFixtures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresQueryStore} satisfies {@link BrowseContract} — the behavioural contract
 * {@code BrowsePort}'s interface-level javadoc documents — against a real PostgreSQL 18 instance
 * started by Testcontainers, pairing it with a {@link PostgresObjectStore} that seeds fixture data
 * into the same tables the query store reads from. It also proves the null-argument contract of
 * {@code IndexPreparationPort#prepare}, which has no testkit contract of its own.
 *
 * <p>{@link #freshEnvironment()} builds a fresh {@link JdbcClient}/{@link
 * PlatformTransactionManager} pair from the shared container {@link DataSource} on every call, and
 * constructs both {@link PostgresObjectStore} and {@link PostgresQueryStore} from that same pair —
 * the whole point of {@link BrowseContract.Environment} is that the store and the browse port see
 * the same underlying data.
 *
 * <p>{@link #freshEnvironment()} also calls {@code prepare} before returning, mirroring the order
 * every real caller uses: ontology activation always prepares indexes before any browse runs
 * (plan.md "PostgreSQL adapter design"). This matters for correctness, not just performance, for
 * this adapter: {@code browse}'s free-text search matches against the persisted {@code
 * search_vector} column, which {@code prepare} registers a property into {@code
 * sq_searchable_property} and backfills for. A property never becomes searchable, and {@code
 * search_vector} never gets populated by the insert trigger, until {@code prepare} has run at least
 * once for the tenant. Without this call, {@code BrowseContract}'s {@code
 * textSearchRanksBestMatchFirst} test fails: every row's {@code search_vector} stays an empty
 * {@code tsvector} and {@code plainto_tsquery} matches nothing.
 *
 * <p>Unlike {@link PostgresObjectStoreContractIT}, {@link #truncateTables()} deliberately leaves
 * {@code sq_index_registry} and {@code sq_searchable_property} untouched between tests — only
 * {@code sq_object}, {@code sq_ontology_document}, and {@code sq_outbox} are truncated, matching
 * what {@link PostgresObjectStoreContractIT} truncates. Since every test's {@code
 * freshEnvironment()} calls {@code prepare} against the same {@code QueryFixtures#schema()},
 * leaving the registries populated across tests exercises {@code prepare}'s idempotent "skip if
 * already registered" path on every test after the first.
 */
@Testcontainers
class PostgresQueryStoreContractIT extends BrowseContract {

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

    @Override
    protected Environment freshEnvironment() {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        PostgresObjectStore store = new PostgresObjectStore(jdbcClient, transactionManager);
        PostgresQueryStore browsePort = new PostgresQueryStore(jdbcClient, transactionManager);
        Schema schema = QueryFixtures.schema();
        browsePort.prepare(Fixtures.defaultScope(), schema.ontologyVersion(), schema);
        return new Environment(store, browsePort);
    }

    @Test
    void prepareRejectsNullArguments() {
        PostgresQueryStore queryStore =
                new PostgresQueryStore(JdbcClient.create(dataSource), new DataSourceTransactionManager(dataSource));
        Scope scope = Fixtures.defaultScope();
        Schema schema = QueryFixtures.schema();
        OntologyVersion version = schema.ontologyVersion();

        assertThatNullPointerException().isThrownBy(() -> queryStore.prepare(null, version, schema));
        assertThatNullPointerException().isThrownBy(() -> queryStore.prepare(scope, null, schema));
        assertThatNullPointerException().isThrownBy(() -> queryStore.prepare(scope, version, null));
    }
}
