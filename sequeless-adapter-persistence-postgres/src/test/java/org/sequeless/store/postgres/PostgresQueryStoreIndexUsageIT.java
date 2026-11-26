package org.sequeless.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.ontology.testfixtures.Fixtures.BELONGS_TO_PROJECT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.PROJECT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.STATUS_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TASK_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TITLE_IRI;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.store.spi.AggregateRequest;
import org.sequeless.store.spi.AggregateResult;
import org.sequeless.testkit.Fixtures;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresQueryStore#aggregate} compiles to a single {@code GROUP BY} over {@code sq_object}, with no
 * correlated per-target subplan, over a realistic volume of data. The expression-index usage that the prototype's
 * {@code prepare} test proved moved to the released {@code query-postgres-jsonb-adapter} (spec 17 P4: its
 * {@code PostgresIndexPreparationIT} runs the same EXPLAIN check).
 *
 * <p>Runs its own Testcontainers PostgreSQL instance, and forces the planner's hand with {@code SET LOCAL
 * enable_seqscan = OFF} only where a test needs it.
 */
@Testcontainers
class PostgresQueryStoreIndexUsageIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static final TypeRef TASK = new TypeRef(TASK_IRI);
    private static final TypeRef PROJECT = new TypeRef(PROJECT_IRI);
    private static final PropertyRef TITLE = new PropertyRef(TITLE_IRI);
    private static final PropertyRef STATUS = new PropertyRef(STATUS_IRI);
    private static final PropertyRef BELONGS_TO_PROJECT = new PropertyRef(BELONGS_TO_PROJECT_IRI);

    private static final int ROW_COUNT = 400;

    private DataSource dataSource;
    private JdbcClient jdbcClient;
    private TransactionTemplate transactionTemplate;
    private PostgresObjectStore store;
    private PostgresQueryStore queryPort;

    @BeforeEach
    void setUp() {
        dataSource = new SimpleDriverDataSource(
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
                .sql("TRUNCATE TABLE sq_object, sq_ontology_document, sq_outbox, sq_index_registry, "
                        + "sq_searchable_property")
                .update();
    }

    @Test
    void aggregateCompilesToSingleGroupByQuery() {
        Scope scope = Fixtures.defaultScope();
        String tenantId = scope.tenantId().value();

        int projectCount = 8;
        List<ObjectId> projectIds = seedProjects(scope, projectCount);
        seedTasksForProjects(scope, ROW_COUNT, projectIds);

        AggregateRequest request = new AggregateRequest(
                Set.of(TASK_IRI),
                BELONGS_TO_PROJECT_IRI,
                Set.copyOf(projectIds),
                AggregateFunction.COUNT,
                Optional.empty(),
                List.of());

        AggregateResult result = queryPort.aggregate(scope, Fixtures.schema(), request);
        assertThat(result.values()).hasSize(projectCount);

        // Reconstructs the exact SQL PostgresQueryStore.aggregate builds for this request: a single
        // GROUP BY over sq_object, no per-target correlated subquery.
        List<String> explainLines = transactionTemplate.execute(status -> jdbcClient
                .sql("EXPLAIN SELECT (props -> :via ->> 'ref')::uuid AS target_id, "
                        + "COUNT(*) AS agg_value FROM sq_object WHERE tenant_id = :tenantId "
                        + "AND type_iri IN (:sourceTypes) AND deleted_at IS NULL "
                        + "AND (props -> :via ->> 'ref')::uuid IN (:targetIds) GROUP BY 1")
                .param("via", BELONGS_TO_PROJECT_IRI)
                .param("tenantId", tenantId)
                .param("sourceTypes", List.of(TASK_IRI))
                .param("targetIds", projectIds.stream().map(ObjectId::value).toList())
                .query(String.class)
                .list());

        String plan = String.join("\n", explainLines);

        Matcher aggregateNodes = Pattern.compile("GroupAggregate|HashAggregate").matcher(plan);
        int aggregateNodeCount = 0;
        while (aggregateNodes.find()) {
            aggregateNodeCount++;
        }
        assertThat(aggregateNodeCount)
                .as("exactly one aggregation node, plan:%n%s", plan)
                .isEqualTo(1);
        assertThat(plan).as("plan:%n%s", plan).doesNotContain("SubPlan");
    }

    private List<ObjectId> seedProjects(Scope scope, int count) {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        String principalId = scope.principal().id();
        List<ObjectId> ids = new ArrayList<>(count);
        List<Mutation> mutations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant createdAt = base.plusSeconds(i);
            Map<PropertyRef, Value> properties = Map.of(TITLE, Value.text("Project " + i));
            Audit audit = new Audit(createdAt, principalId, createdAt, principalId);
            ObjectId id = ObjectId.random();
            ids.add(id);
            mutations.add(new Create(
                    new BusinessObject(id, PROJECT, scope.tenantId(), 1, Optional.empty(), properties, audit, false)));
        }
        store.commit(scope, new ChangeSet(mutations, List.of()));
        return ids;
    }

    private void seedTasksForProjects(Scope scope, int count, List<ObjectId> projectIds) {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        String principalId = scope.principal().id();
        List<Mutation> mutations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant createdAt = base.plusSeconds(i);
            String status = i % 2 == 0 ? "OPEN" : "CLOSED";
            ObjectId project = projectIds.get(i % projectIds.size());
            Map<PropertyRef, Value> properties = Map.of(
                    TITLE, Value.text("Task " + i),
                    STATUS, Value.text(status),
                    BELONGS_TO_PROJECT, Value.ref(project));
            Audit audit = new Audit(createdAt, principalId, createdAt, principalId);
            BusinessObject object = new BusinessObject(
                    ObjectId.random(), TASK, scope.tenantId(), 1, Optional.empty(), properties, audit, false);
            mutations.add(new Create(object));
        }
        int batchSize = 50;
        for (int start = 0; start < mutations.size(); start += batchSize) {
            List<Mutation> batch = mutations.subList(start, Math.min(start + batchSize, mutations.size()));
            store.commit(scope, new ChangeSet(batch, List.of()));
        }
    }
}
