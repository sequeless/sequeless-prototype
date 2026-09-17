package org.sequeless.adapter.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sequeless.spi.Scope;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Value;
import org.sequeless.testkit.Fixtures;
import org.sequeless.testkit.query.QueryFixtures;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresQueryStore#ensureIndexes} does more than register a row in {@code
 * sq_index_registry}: the expression index it creates is actually chosen by PostgreSQL's planner
 * for a query filtering on that indexed property, over a realistic volume of data.
 *
 * <p>Runs its own Testcontainers PostgreSQL instance, isolated from {@link
 * PostgresQueryStoreContractIT}, since it truncates {@code sq_index_registry} and {@code
 * sq_searchable_property} between tests (unlike the contract test, which deliberately leaves those
 * populated) and forces the planner's hand with {@code SET LOCAL enable_seqscan = OFF} — settings
 * neither of those two adjustments should leak into the shared contract-test container.
 */
@Testcontainers
class PostgresQueryStoreIndexUsageIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final TypeRef TASK = new TypeRef(Fixtures.TASK_IRI);
    private static final PropertyRef TITLE = new PropertyRef(Fixtures.TITLE_IRI);
    private static final PropertyRef STATUS = new PropertyRef(Fixtures.STATUS_IRI);
    private static final PropertyRef DESCRIPTION = new PropertyRef(Fixtures.DESCRIPTION_IRI);
    private static final PropertyRef PRIORITY = new PropertyRef(Fixtures.PRIORITY_IRI);

    private static final int ROW_COUNT = 400;

    private DataSource dataSource;
    private JdbcClient jdbcClient;
    private TransactionTemplate transactionTemplate;
    private PostgresObjectStore store;
    private PostgresQueryStore queryPort;

    @BeforeEach
    void setUp() {
        dataSource =
            new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PostgresMigrations.migrate(dataSource);

        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        jdbcClient = JdbcClient.create(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        store = new PostgresObjectStore(jdbcClient, transactionManager);
        queryPort = new PostgresQueryStore(jdbcClient, transactionManager);
    }

    @AfterEach
    void tearDown() {
        jdbcClient
            .sql(
                "TRUNCATE TABLE sq_object, sq_ontology_document, sq_outbox, sq_index_registry, "
                    + "sq_searchable_property")
            .update();
    }

    /**
     * {@code ex:status} is the reference domain's one {@code sq:facet + sq:indexed} property (see
     * {@code Fixtures.STATUS_IRI}'s javadoc and {@code QueryFixtures.status()}), {@code
     * xsd:string}-typed. Seeds {@link #ROW_COUNT} {@code Task}s alternating {@code status} between
     * {@code "OPEN"} and {@code "CLOSED"} so a filter on it is selective, then proves an {@code
     * EXPLAIN} of that filter both names an Index Scan and names the specific index {@code
     * ensureIndexes} created and registered — read back from {@code sq_index_registry} rather than
     * recomputed via {@link PostgresQueryStore#indexNameFor}, so this test stays decoupled from
     * that hashing implementation detail.
     */
    @Test
    void filterOnIndexedPropertyUsesExpressionIndex() {
        Scope scope = Fixtures.defaultScope();
        String tenantId = scope.tenantId().value();

        queryPort.ensureIndexes(scope, QueryFixtures.snapshot());

        String indexName =
            jdbcClient
                .sql(
                    "SELECT index_name FROM sq_index_registry WHERE tenant_id = :tenantId "
                        + "AND property_iri = :propertyIri")
                .param("tenantId", tenantId)
                .param("propertyIri", Fixtures.STATUS_IRI)
                .query(String.class)
                .single();

        seedTasks(scope, ROW_COUNT);

        // castExpression's STRING-datatype cast is `props -> :propParam ->> 'text'`; the DDL-time
        // variant embeds the property IRI as a literal instead of a bind parameter (see
        // PostgresQueryStore.castExpressionLiteral), which is exactly what EXPLAIN needs here too.
        String jsonPathExpression =
            "props -> '" + Fixtures.STATUS_IRI.replace("'", "''") + "' ->> 'text'";

        List<String> explainLines =
            transactionTemplate.execute(status -> {
                jdbcClient.sql("SET LOCAL enable_seqscan = OFF").update();
                return jdbcClient
                    .sql(
                        "EXPLAIN SELECT id FROM sq_object WHERE tenant_id = :tenantId "
                            + "AND type_iri = :typeIri AND (" + jsonPathExpression + ") = :status")
                    .param("tenantId", tenantId)
                    .param("typeIri", Fixtures.TASK_IRI)
                    .param("status", "OPEN")
                    .query(String.class)
                    .list();
            });

        String plan = String.join("\n", explainLines);
        assertThat(plan).contains("Index Scan");
        assertThat(plan).contains(indexName);
    }

    private void seedTasks(Scope scope, int count) {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        String principalId = scope.principal().id();
        List<Mutation> mutations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant createdAt = base.plusSeconds(i);
            String status = i % 2 == 0 ? "OPEN" : "CLOSED";
            Map<PropertyRef, Value> properties =
                Map.of(
                    TITLE, Value.text("Task " + i),
                    DESCRIPTION, Value.text("Description of task " + i),
                    STATUS, Value.text(status),
                    PRIORITY, Value.integer(i % 10));
            Audit audit = new Audit(createdAt, principalId, createdAt, principalId);
            BusinessObject object =
                new BusinessObject(
                    ObjectId.random(), TASK, scope.tenantId(), 1, Optional.empty(), properties,
                    audit, false);
            mutations.add(new Create(object));
        }
        // A handful of large batches rather than one commit per row, and rather than one giant
        // single-transaction commit for all ROW_COUNT rows: keeps each PostgresObjectStore#commit
        // transaction a reasonable size while still seeding realistic volume quickly.
        int batchSize = 50;
        for (int start = 0; start < mutations.size(); start += batchSize) {
            List<Mutation> batch = mutations.subList(start, Math.min(start + batchSize, mutations.size()));
            store.commit(scope, new ChangeSet(batch, List.of()));
        }
    }
}
