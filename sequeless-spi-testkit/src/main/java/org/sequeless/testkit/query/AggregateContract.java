package org.sequeless.testkit.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.AggregateFunction;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link QueryPort#aggregate}'s
 * javadoc. Every {@link QueryPort} implementation — adapter or test double — is expected to
 * satisfy every clause of that javadoc, and this class exercises each clause once, against
 * whatever {@link Environment} {@link #freshEnvironment()} supplies.
 *
 * <p>This is deliberately not a subclass of {@link QueryContract}: {@link #aggregate} needs its
 * own source data shaped around a {@code Project}/{@code Task} rollup rather than the flat
 * filter/sort/facet fixtures {@link QueryContract} seeds, so it declares its own {@link
 * Environment} of the same shape.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #freshEnvironment()} to return a brand-new, empty {@link ObjectStorePort} paired with a {@link
 * QueryPort} that aggregates over the same underlying storage:
 *
 * <pre>{@code
 * class MyAdapterAggregateContractTest extends AggregateContract {
 *     protected Environment freshEnvironment() {
 *         ObjectStorePort store = new MyAdapter();
 *         return new Environment(store, MyQueryAdapter.over(store));
 *     }
 * }
 * }</pre>
 *
 * <p>Every test seeds real {@code Task} and {@code Project} objects (the reference-domain
 * hierarchy {@link Fixtures} declares IRI constants for) via {@link Environment#store()}'s {@link
 * ObjectStorePort#commit}, then calls {@link Environment#queryPort()}'s {@link
 * QueryPort#aggregate} against {@link QueryFixtures#snapshot()}, mirroring the exact rollup shapes
 * {@code ex:openTaskCount} and {@code ex:totalEstimatedHours} declare.
 */
public abstract class AggregateContract {

    private static final TypeRef TASK = new TypeRef(Fixtures.TASK_IRI);
    private static final TypeRef PROJECT = new TypeRef(Fixtures.PROJECT_IRI);

    private static final PropertyRef TITLE = new PropertyRef(Fixtures.TITLE_IRI);
    private static final PropertyRef STATUS = new PropertyRef(Fixtures.STATUS_IRI);
    private static final PropertyRef ESTIMATED_HOURS = new PropertyRef(Fixtures.ESTIMATED_HOURS_IRI);
    private static final PropertyRef BELONGS_TO_PROJECT =
        new PropertyRef(Fixtures.BELONGS_TO_PROJECT_IRI);

    /**
     * An {@link ObjectStorePort} paired with the {@link QueryPort} that aggregates over the same
     * underlying storage — a conforming {@link QueryPort} implementation only makes sense against
     * objects committed through a particular store, exactly as {@link QueryContract.Environment}
     * pairs the two for filtered/sorted/faceted queries.
     *
     * @param store the store to seed fixture data into via {@link ObjectStorePort#commit}; must
     *     not be {@code null}
     * @param queryPort the port under test, expected to answer aggregates over whatever {@code
     *     store} holds; must not be {@code null}
     */
    public record Environment(ObjectStorePort store, QueryPort queryPort) {

        public Environment {
            Objects.requireNonNull(store, "store must not be null");
            Objects.requireNonNull(queryPort, "queryPort must not be null");
        }
    }

    /**
     * @return a fresh {@link Environment} pairing a brand-new, empty {@link ObjectStorePort} with
     *     a {@link QueryPort} that aggregates over it; invoked fresh for every {@code @Test}
     *     method, so implementors may return new instances each time or shared ones, whichever
     *     suits the port under test — but the returned environment must not carry over state from
     *     a previous test
     */
    protected abstract Environment freshEnvironment();

    @Test
    void countIsDenseAndZeroFilled() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId projectA = seedProject(env, scope, base, "Project A");
        ObjectId projectB = seedProject(env, scope, base.plusSeconds(1), "Project B");

        seedTask(env, scope, base.plusSeconds(2), "OPEN", null, projectA);
        seedTask(env, scope, base.plusSeconds(3), "OPEN", null, projectA);
        seedTask(env, scope, base.plusSeconds(4), "done", null, projectA);

        AggregateRequest request =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(projectA, projectB),
                AggregateFunction.COUNT,
                Optional.empty(),
                List.of(
                    new Criterion(
                        Fixtures.STATUS_IRI, Operator.NE, Optional.of(Value.text("done")))));

        AggregateResult result = env.queryPort().aggregate(scope, QueryFixtures.snapshot(), request);

        assertThat(result.values())
            .containsEntry(projectA, Value.integer(2))
            .containsEntry(projectB, Value.integer(0));
    }

    @Test
    void sumOmitsTargetsWithNoMatchingSource() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId projectA = seedProject(env, scope, base, "Project A");
        ObjectId projectB = seedProject(env, scope, base.plusSeconds(1), "Project B");

        seedTask(env, scope, base.plusSeconds(2), "OPEN", new BigDecimal("3"), projectA);
        seedTask(env, scope, base.plusSeconds(3), "OPEN", new BigDecimal("4.5"), projectA);

        AggregateRequest request =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(projectA, projectB),
                AggregateFunction.SUM,
                Optional.of(Fixtures.ESTIMATED_HOURS_IRI),
                List.of());

        AggregateResult result = env.queryPort().aggregate(scope, QueryFixtures.snapshot(), request);

        assertThat(result.values()).containsKey(projectA);
        assertThat(decimalOf(result, projectA)).isEqualByComparingTo(new BigDecimal("7.5"));
        assertThat(result.values()).doesNotContainKey(projectB);
    }

    @Test
    void minAndMaxOverSourceProperty() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId projectA = seedProject(env, scope, base, "Project A");

        seedTask(env, scope, base.plusSeconds(1), "OPEN", new BigDecimal("3"), projectA);
        seedTask(env, scope, base.plusSeconds(2), "OPEN", new BigDecimal("4.5"), projectA);
        seedTask(env, scope, base.plusSeconds(3), "OPEN", new BigDecimal("1"), projectA);

        AggregateRequest minRequest =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(projectA),
                AggregateFunction.MIN,
                Optional.of(Fixtures.ESTIMATED_HOURS_IRI),
                List.of());
        AggregateResult minResult =
            env.queryPort().aggregate(scope, QueryFixtures.snapshot(), minRequest);
        assertThat(decimalOf(minResult, projectA)).isEqualByComparingTo(new BigDecimal("1"));

        AggregateRequest maxRequest =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(projectA),
                AggregateFunction.MAX,
                Optional.of(Fixtures.ESTIMATED_HOURS_IRI),
                List.of());
        AggregateResult maxResult =
            env.queryPort().aggregate(scope, QueryFixtures.snapshot(), maxRequest);
        assertThat(decimalOf(maxResult, projectA)).isEqualByComparingTo(new BigDecimal("4.5"));
    }

    @Test
    void avgOverSourceProperty() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId projectA = seedProject(env, scope, base, "Project A");

        seedTask(env, scope, base.plusSeconds(1), "OPEN", new BigDecimal("2"), projectA);
        seedTask(env, scope, base.plusSeconds(2), "OPEN", new BigDecimal("4"), projectA);

        AggregateRequest request =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(projectA),
                AggregateFunction.AVG,
                Optional.of(Fixtures.ESTIMATED_HOURS_IRI),
                List.of());

        AggregateResult result = env.queryPort().aggregate(scope, QueryFixtures.snapshot(), request);

        // Allow for reasonable scale/rounding differences between adapters (e.g. Postgres' avg()
        // over numeric returns a wider scale than a hand-rolled BigDecimal division would) — assert
        // numerically, not by exact DecimalValue equality.
        assertThat(decimalOf(result, projectA)).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void criteriaNarrowSourceRows() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId projectA = seedProject(env, scope, base, "Project A");

        seedTask(env, scope, base.plusSeconds(1), "OPEN", null, projectA);
        seedTask(env, scope, base.plusSeconds(2), "OPEN", null, projectA);
        seedTask(env, scope, base.plusSeconds(3), "CLOSED", null, projectA);

        AggregateRequest request =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(projectA),
                AggregateFunction.COUNT,
                Optional.empty(),
                List.of(
                    new Criterion(
                        Fixtures.STATUS_IRI, Operator.EQ, Optional.of(Value.text("OPEN")))));

        AggregateResult result = env.queryPort().aggregate(scope, QueryFixtures.snapshot(), request);

        assertThat(result.values()).containsEntry(projectA, Value.integer(2));
    }

    @Test
    void tenantScopingExcludesOtherTenants() {
        Environment env = freshEnvironment();
        Scope defaultScope = Fixtures.defaultScope();
        Scope otherTenantScope =
            new Scope(new TenantId("other-tenant"), Fixtures.defaultScope().principal());
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId projectA = seedProject(env, defaultScope, base, "Project A");
        seedTask(env, defaultScope, base.plusSeconds(1), "OPEN", null, projectA);

        // Seeded under a different tenant, referencing the same target id — must not leak into the
        // default tenant's aggregate.
        seedTask(env, otherTenantScope, base.plusSeconds(2), "OPEN", null, projectA);

        AggregateRequest request =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(projectA),
                AggregateFunction.COUNT,
                Optional.empty(),
                List.of());

        AggregateResult result =
            env.queryPort().aggregate(defaultScope, QueryFixtures.snapshot(), request);

        assertThat(result.values()).containsEntry(projectA, Value.integer(1));
    }

    @Test
    void softDeletedSourceExcluded() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId projectA = seedProject(env, scope, base, "Project A");
        ObjectId task = seedTask(env, scope, base.plusSeconds(1), "OPEN", null, projectA);

        env.store()
            .commit(
                scope,
                new ChangeSet(List.of(new Delete(task, 1, base.plusSeconds(2), "tester")), List.of()));

        AggregateRequest request =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(projectA),
                AggregateFunction.COUNT,
                Optional.empty(),
                List.of());

        AggregateResult result = env.queryPort().aggregate(scope, QueryFixtures.snapshot(), request);

        assertThat(result.values()).containsEntry(projectA, Value.integer(0));
    }

    @Test
    void emptyTargetIdsYieldsEmptyMap() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();

        AggregateRequest request =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(),
                AggregateFunction.COUNT,
                Optional.empty(),
                List.of());

        AggregateResult result = env.queryPort().aggregate(scope, QueryFixtures.snapshot(), request);

        assertThat(result).isEqualTo(new AggregateResult(Map.of()));
    }

    @Test
    void aggregateRejectsNullArguments() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        MetaModelSnapshot snapshot = QueryFixtures.snapshot();
        AggregateRequest request =
            new AggregateRequest(
                Set.of(Fixtures.TASK_IRI),
                Fixtures.BELONGS_TO_PROJECT_IRI,
                Set.of(),
                AggregateFunction.COUNT,
                Optional.empty(),
                List.of());

        assertThatNullPointerException()
            .isThrownBy(() -> env.queryPort().aggregate(null, snapshot, request));
        assertThatNullPointerException()
            .isThrownBy(() -> env.queryPort().aggregate(scope, null, request));
        assertThatNullPointerException()
            .isThrownBy(() -> env.queryPort().aggregate(scope, snapshot, null));
    }

    // -- Assertion helpers ---------------------------------------------------------------------

    private static BigDecimal decimalOf(AggregateResult result, ObjectId target) {
        Value value = result.values().get(target);
        assertThat(value).as("value for %s", target).isNotNull();
        return switch (value) {
            case org.sequeless.spi.object.DecimalValue decimal -> decimal.value();
            case org.sequeless.spi.object.IntegerValue integer -> BigDecimal.valueOf(integer.value());
            default -> throw new AssertionError("Expected a numeric Value but got " + value);
        };
    }

    // -- Fixture-seeding helpers -------------------------------------------------------------

    private static ObjectId seedProject(Environment env, Scope scope, Instant createdAt, String title) {
        Map<PropertyRef, Value> properties = Map.of(TITLE, Value.text(title));
        return seed(env, scope, PROJECT, createdAt, properties);
    }

    private static ObjectId seedTask(
        Environment env,
        Scope scope,
        Instant createdAt,
        String status,
        BigDecimal estimatedHours,
        ObjectId belongsToProject) {
        Map<PropertyRef, Value> properties = new HashMap<>();
        properties.put(TITLE, Value.text("Task at " + createdAt));
        properties.put(STATUS, Value.text(status));
        if (estimatedHours != null) {
            properties.put(ESTIMATED_HOURS, Value.decimal(estimatedHours));
        }
        if (belongsToProject != null) {
            properties.put(BELONGS_TO_PROJECT, Value.ref(belongsToProject));
        }
        return seed(env, scope, TASK, createdAt, Map.copyOf(properties));
    }

    private static ObjectId seed(
        Environment env, Scope scope, TypeRef type, Instant createdAt, Map<PropertyRef, Value> properties) {
        ObjectId id = ObjectId.random();
        String principalId = scope.principal().id();
        Audit audit = new Audit(createdAt, principalId, createdAt, principalId);
        BusinessObject object =
            new BusinessObject(id, type, scope.tenantId(), 1, Optional.empty(), properties, audit, false);
        env.store().commit(scope, new ChangeSet(List.of(new Create(object)), List.of()));
        return id;
    }
}
