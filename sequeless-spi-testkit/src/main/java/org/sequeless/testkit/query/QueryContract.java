package org.sequeless.testkit.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.FacetBucket;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.Sort;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link QueryPort}'s
 * interface-level javadoc. Every {@link QueryPort} implementation — adapter or test double — is
 * expected to satisfy every clause of that javadoc, and this class exercises each clause once,
 * against whatever {@link Environment} {@link #freshEnvironment()} supplies.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #freshEnvironment()} to return a brand-new, empty {@link ObjectStorePort} paired with a {@link
 * QueryPort} that queries the same underlying storage:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends QueryContract {
 *     protected Environment freshEnvironment() {
 *         ObjectStorePort store = new MyAdapter();
 *         return new Environment(store, MyQueryAdapter.over(store));
 *     }
 * }
 * }</pre>
 *
 * <p>Unlike {@link org.sequeless.testkit.object.ObjectStoreContract}, this contract is not
 * ontology-agnostic: exercising real filters, sorts, and facets needs a real {@link
 * MetaModelSnapshot} built from real properties, so every test here seeds {@link
 * org.sequeless.spi.object.BusinessObject}s of the reference-domain {@code Task}, {@code
 * Project}, and {@code Person} types — via {@link Environment#store()}'s {@link
 * ObjectStorePort#commit} — and queries them through {@link Environment#queryPort()} against
 * {@link QueryFixtures#snapshot()}, a hand-built {@link MetaModelSnapshot} mirroring {@code
 * reference.ttl}.
 *
 * <p><b>What this contract deliberately does not check.</b> No assertion here depends on a
 * particular storage or indexing technology. Every assertion is about the behaviour {@link
 * QueryPort}'s javadoc documents: null-safety, tenant scoping (inherited from {@link
 * ObjectStorePort}'s own contract and not re-asserted here), soft-delete filtering, the union
 * semantics of {@link Query#types()}, the {@code (createdAt, id)} ascending default sort order,
 * and the "conventional multi-select" facet-counting rule.
 */
public abstract class QueryContract {

    private static final TypeRef TASK = new TypeRef(Fixtures.TASK_IRI);
    private static final TypeRef PROJECT = new TypeRef(Fixtures.PROJECT_IRI);
    private static final TypeRef PERSON = new TypeRef(Fixtures.PERSON_IRI);

    private static final PropertyRef TITLE = new PropertyRef(Fixtures.TITLE_IRI);
    private static final PropertyRef STATUS = new PropertyRef(Fixtures.STATUS_IRI);
    private static final PropertyRef DESCRIPTION = new PropertyRef(Fixtures.DESCRIPTION_IRI);
    private static final PropertyRef PRIORITY = new PropertyRef(Fixtures.PRIORITY_IRI);
    private static final PropertyRef DUE_DATE = new PropertyRef(Fixtures.DUE_DATE_IRI);
    private static final PropertyRef ASSIGNED_TO = new PropertyRef(Fixtures.ASSIGNED_TO_IRI);
    private static final PropertyRef BELONGS_TO_PROJECT =
        new PropertyRef(Fixtures.BELONGS_TO_PROJECT_IRI);
    private static final PropertyRef NAME = new PropertyRef(Fixtures.NAME_IRI);
    private static final PropertyRef EMAIL = new PropertyRef(Fixtures.EMAIL_IRI);

    /**
     * An {@link ObjectStorePort} paired with the {@link QueryPort} that queries the same
     * underlying storage — a conforming {@link QueryPort} implementation only makes sense against
     * objects committed through a particular store, exactly as {@link
     * org.sequeless.testkit.validation.ValidationContract.Fixture} pairs a {@link
     * org.sequeless.spi.validation.ValidationPort} with the {@link MetaModelSnapshot} it
     * validates against.
     *
     * @param store the store to seed fixture data into via {@link ObjectStorePort#commit}; must
     *     not be {@code null}
     * @param queryPort the port under test, expected to answer queries over whatever {@code
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
     *     a {@link QueryPort} that queries it; invoked fresh for every {@code @Test} method, so
     *     implementors may return new instances each time or shared ones, whichever suits the
     *     port under test — but the returned environment must not carry over state from a
     *     previous test
     */
    protected abstract Environment freshEnvironment();

    @Test
    void queryRejectsNullArguments() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        MetaModelSnapshot snapshot = QueryFixtures.snapshot();
        Query query = queryOf(Set.of(Fixtures.TASK_IRI), List.of(), new Page(0, 20));

        assertThatNullPointerException()
            .isThrownBy(() -> env.queryPort().query(null, snapshot, query));
        assertThatNullPointerException()
            .isThrownBy(() -> env.queryPort().query(scope, null, query));
        assertThatNullPointerException()
            .isThrownBy(() -> env.queryPort().query(scope, snapshot, null));
    }

    @Test
    void ensureIndexesRejectsNullArguments() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        MetaModelSnapshot snapshot = QueryFixtures.snapshot();

        assertThatNullPointerException()
            .isThrownBy(() -> env.queryPort().ensureIndexes(null, snapshot));
        assertThatNullPointerException()
            .isThrownBy(() -> env.queryPort().ensureIndexes(scope, null));
    }

    @Test
    void equalityFilterMatchesOnlyExactValue() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId open1 =
            seedTask(env, scope, base, "Open task one", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        ObjectId open2 =
            seedTask(
                env, scope, base.plusSeconds(1), "Open task two", "OPEN", 2, LocalDate.of(2026, 2, 2),
                null, null);
        seedTask(
            env, scope, base.plusSeconds(2), "Closed task", "CLOSED", 3, LocalDate.of(2026, 2, 3), null,
            null);

        Query query =
            queryOf(
                Set.of(Fixtures.TASK_IRI),
                List.of(new Criterion(Fixtures.STATUS_IRI, Operator.EQ, Optional.of(Value.text("OPEN")))),
                new Page(0, 20));

        QueryResult result = env.queryPort().query(scope, QueryFixtures.snapshot(), query);

        assertThat(result.items()).extracting(BusinessObject::id).containsExactlyInAnyOrder(open1, open2);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void referenceFilterMatchesByTargetId() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId alice = seedPerson(env, scope, base, "Alice Smith", "alice@example.com");
        ObjectId bob = seedPerson(env, scope, base.plusSeconds(1), "Bob Jones", "bob@example.com");

        ObjectId taskForAlice =
            seedTask(
                env, scope, base.plusSeconds(2), "Alice's task", "OPEN", 1, LocalDate.of(2026, 2, 1),
                alice, null);
        seedTask(
            env, scope, base.plusSeconds(3), "Bob's task", "OPEN", 2, LocalDate.of(2026, 2, 2), bob,
            null);

        Query query =
            queryOf(
                Set.of(Fixtures.TASK_IRI),
                List.of(new Criterion(Fixtures.ASSIGNED_TO_IRI, Operator.EQ, Optional.of(Value.ref(alice)))),
                new Page(0, 20));

        QueryResult result = env.queryPort().query(scope, QueryFixtures.snapshot(), query);

        assertThat(result.items()).extracting(BusinessObject::id).containsExactly(taskForAlice);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void dateRangeFilterUsesGteAndLte() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seedTask(env, scope, base, "Too early", "OPEN", 1, LocalDate.of(2026, 1, 1), null, null);
        ObjectId inWindow =
            seedTask(
                env, scope, base.plusSeconds(1), "In window", "OPEN", 2, LocalDate.of(2026, 2, 15), null,
                null);
        seedTask(env, scope, base.plusSeconds(2), "Too late", "OPEN", 3, LocalDate.of(2026, 3, 1), null, null);

        Query query =
            queryOf(
                Set.of(Fixtures.TASK_IRI),
                List.of(
                    new Criterion(
                        Fixtures.DUE_DATE_IRI, Operator.GTE,
                        Optional.of(Value.date(LocalDate.of(2026, 2, 1)))),
                    new Criterion(
                        Fixtures.DUE_DATE_IRI, Operator.LTE,
                        Optional.of(Value.date(LocalDate.of(2026, 2, 28))))),
                new Page(0, 20));

        QueryResult result = env.queryPort().query(scope, QueryFixtures.snapshot(), query);

        assertThat(result.items()).extracting(BusinessObject::id).containsExactly(inWindow);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void numericSortOrdersAscendingAndDescending() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId low =
            seedTask(env, scope, base, "Low priority", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        ObjectId mid =
            seedTask(
                env, scope, base.plusSeconds(1), "Mid priority", "OPEN", 5, LocalDate.of(2026, 2, 2), null,
                null);
        ObjectId high =
            seedTask(
                env, scope, base.plusSeconds(2), "High priority", "OPEN", 9, LocalDate.of(2026, 2, 3), null,
                null);

        MetaModelSnapshot snapshot = QueryFixtures.snapshot();

        Query ascending =
            queryOf(
                Set.of(Fixtures.TASK_IRI), List.of(), List.of(new Sort(Fixtures.PRIORITY_IRI, Direction.ASC)),
                new Page(0, 20));
        QueryResult ascResult = env.queryPort().query(scope, snapshot, ascending);
        assertThat(ascResult.items()).extracting(BusinessObject::id).containsExactly(low, mid, high);

        Query descending =
            queryOf(
                Set.of(Fixtures.TASK_IRI), List.of(), List.of(new Sort(Fixtures.PRIORITY_IRI, Direction.DESC)),
                new Page(0, 20));
        QueryResult descResult = env.queryPort().query(scope, snapshot, descending);
        assertThat(descResult.items()).extracting(BusinessObject::id).containsExactly(high, mid, low);
    }

    @Test
    void facetCountsGroupByScalarPropertyReflectMultiSelectExclusion() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId openLow =
            seedTask(env, scope, base, "Open low", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        seedTask(env, scope, base.plusSeconds(1), "Open high", "OPEN", 2, LocalDate.of(2026, 2, 2), null, null);
        seedTask(
            env, scope, base.plusSeconds(2), "Closed low", "CLOSED", 1, LocalDate.of(2026, 2, 3), null,
            null);
        seedTask(
            env, scope, base.plusSeconds(3), "Closed high", "CLOSED", 2, LocalDate.of(2026, 2, 4), null,
            null);

        // Both a criterion on the facet property itself (status = OPEN) and an unrelated one
        // (priority = 1) are applied. Per QueryPort's "conventional multi-select" rule, the
        // status facet's bucket counts must be computed against every criterion EXCEPT the one
        // on status itself — so they should reflect both OPEN and CLOSED among the
        // priority-filtered subset, not just the single OPEN value the result set is narrowed to.
        Query query =
            new Query(
                Set.of(Fixtures.TASK_IRI),
                List.of(
                    new Criterion(Fixtures.STATUS_IRI, Operator.EQ, Optional.of(Value.text("OPEN"))),
                    new Criterion(Fixtures.PRIORITY_IRI, Operator.EQ, Optional.of(Value.integer(1)))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of(Fixtures.STATUS_IRI),
                false);

        QueryResult result = env.queryPort().query(scope, QueryFixtures.snapshot(), query);

        assertThat(result.items()).extracting(BusinessObject::id).containsExactly(openLow);
        assertThat(result.total()).isEqualTo(1);

        List<FacetBucket> statusBuckets = result.facets().get(Fixtures.STATUS_IRI);
        assertThat(statusBuckets)
            .containsExactlyInAnyOrder(new FacetBucket("OPEN", 1), new FacetBucket("CLOSED", 1));
    }

    @Test
    void facetCountsOnReferencePropertyResolveDisplayLabel() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId alice = seedPerson(env, scope, base, "Alice Smith", "alice@example.com");
        ObjectId bob = seedPerson(env, scope, base.plusSeconds(1), "Bob Jones", "bob@example.com");

        seedTask(
            env, scope, base.plusSeconds(2), "Alice task one", "OPEN", 1, LocalDate.of(2026, 2, 1), alice,
            null);
        seedTask(
            env, scope, base.plusSeconds(3), "Alice task two", "OPEN", 2, LocalDate.of(2026, 2, 2), alice,
            null);
        seedTask(
            env, scope, base.plusSeconds(4), "Bob task", "OPEN", 3, LocalDate.of(2026, 2, 3), bob, null);

        Query query =
            queryOf(
                Set.of(Fixtures.TASK_IRI), List.of(), List.of(), new Page(0, 20),
                List.of(Fixtures.ASSIGNED_TO_IRI));

        QueryResult result = env.queryPort().query(scope, QueryFixtures.snapshot(), query);

        List<FacetBucket> assignedToBuckets = result.facets().get(Fixtures.ASSIGNED_TO_IRI);
        assertThat(assignedToBuckets).extracting(FacetBucket::value).containsExactlyInAnyOrder(
            "Alice Smith", "Bob Jones");
        assertThat(assignedToBuckets)
            .filteredOn(bucket -> bucket.value().equals("Alice Smith"))
            .extracting(FacetBucket::count)
            .containsExactly(2L);
    }

    @Test
    void textSearchRanksBestMatchFirst() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId bestMatch =
            seedTaskWithDescription(
                env, scope, base, "Quarterly budget review", "Review the quarterly budget numbers",
                "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        seedTaskWithDescription(
            env, scope, base.plusSeconds(1), "Unrelated task", "Something else entirely", "OPEN", 2,
            LocalDate.of(2026, 2, 2), null, null);
        seedTaskWithDescription(
            env, scope, base.plusSeconds(2), "Another task", "Nothing to do with the topic", "OPEN", 3,
            LocalDate.of(2026, 2, 3), null, null);

        Query query =
            new Query(
                Set.of(Fixtures.TASK_IRI),
                List.of(),
                Optional.of("quarterly budget"),
                List.of(),
                new Page(0, 20),
                List.of(),
                false);

        QueryResult result = env.queryPort().query(scope, QueryFixtures.snapshot(), query);

        assertThat(result.items()).isNotEmpty();
        assertThat(result.items().get(0).id()).isEqualTo(bestMatch);
    }

    @Test
    void workItemQueryReturnsBothTaskAndProjectSubtypes() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId task =
            seedTask(env, scope, base, "A task", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        ObjectId project = seedProject(env, scope, base.plusSeconds(1), "A project", "ACTIVE");

        Query query =
            queryOf(Set.of(Fixtures.TASK_IRI, Fixtures.PROJECT_IRI), List.of(), new Page(0, 20));

        QueryResult result = env.queryPort().query(scope, QueryFixtures.snapshot(), query);

        assertThat(result.items()).extracting(BusinessObject::id).containsExactlyInAnyOrder(task, project);
        assertThat(result.items())
            .extracting(item -> item.type().iri())
            .containsExactlyInAnyOrder(Fixtures.TASK_IRI, Fixtures.PROJECT_IRI);
    }

    @Test
    void paginationRespectsPageAndSize() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        List<ObjectId> expectedOrder =
            List.of(
                seedTask(env, scope, base, "Task 1", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null),
                seedTask(
                    env, scope, base.plusSeconds(1), "Task 2", "OPEN", 2, LocalDate.of(2026, 2, 2), null,
                    null),
                seedTask(
                    env, scope, base.plusSeconds(2), "Task 3", "OPEN", 3, LocalDate.of(2026, 2, 3), null,
                    null),
                seedTask(
                    env, scope, base.plusSeconds(3), "Task 4", "OPEN", 4, LocalDate.of(2026, 2, 4), null,
                    null),
                seedTask(
                    env, scope, base.plusSeconds(4), "Task 5", "OPEN", 5, LocalDate.of(2026, 2, 5), null,
                    null));

        MetaModelSnapshot snapshot = QueryFixtures.snapshot();

        // No explicit sort: relies on QueryPort's documented default order, ascending
        // (createdAt, id), so page contents are deterministic.
        QueryResult page0 =
            env.queryPort().query(scope, snapshot, queryOf(Set.of(Fixtures.TASK_IRI), List.of(), new Page(0, 2)));
        assertThat(page0.items())
            .extracting(BusinessObject::id)
            .containsExactlyElementsOf(expectedOrder.subList(0, 2));
        assertThat(page0.total()).isEqualTo(5);

        QueryResult page1 =
            env.queryPort().query(scope, snapshot, queryOf(Set.of(Fixtures.TASK_IRI), List.of(), new Page(1, 2)));
        assertThat(page1.items())
            .extracting(BusinessObject::id)
            .containsExactlyElementsOf(expectedOrder.subList(2, 4));
        assertThat(page1.total()).isEqualTo(5);

        QueryResult page2 =
            env.queryPort().query(scope, snapshot, queryOf(Set.of(Fixtures.TASK_IRI), List.of(), new Page(2, 2)));
        assertThat(page2.items())
            .extracting(BusinessObject::id)
            .containsExactlyElementsOf(expectedOrder.subList(4, 5));
        assertThat(page2.total()).isEqualTo(5);

        QueryResult overrun =
            env.queryPort().query(scope, snapshot, queryOf(Set.of(Fixtures.TASK_IRI), List.of(), new Page(5, 2)));
        assertThat(overrun.items()).isEmpty();
        assertThat(overrun.total()).isEqualTo(5);
    }

    @Test
    void includeDeletedDefaultsToExcluded() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId id =
            seedTask(env, scope, base, "Soon deleted", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        env.store()
            .commit(scope, new ChangeSet(List.of(new Delete(id, 1, base.plusSeconds(1), "tester")), List.of()));

        Query query =
            new Query(
                Set.of(Fixtures.TASK_IRI), List.of(), Optional.empty(), List.of(), new Page(0, 20),
                List.of(), false);

        QueryResult result = env.queryPort().query(scope, QueryFixtures.snapshot(), query);

        assertThat(result.items()).extracting(BusinessObject::id).doesNotContain(id);
    }

    // -- Fixture-seeding helpers -------------------------------------------------------------

    private static Query queryOf(Set<String> types, List<Criterion> criteria, Page page) {
        return queryOf(types, criteria, List.of(), page, List.of());
    }

    private static Query queryOf(
        Set<String> types, List<Criterion> criteria, List<Sort> sorts, Page page) {
        return queryOf(types, criteria, sorts, page, List.of());
    }

    private static Query queryOf(
        Set<String> types, List<Criterion> criteria, Page page, List<String> facetProperties) {
        return queryOf(types, criteria, List.of(), page, facetProperties);
    }

    private static Query queryOf(
        Set<String> types,
        List<Criterion> criteria,
        List<Sort> sorts,
        Page page,
        List<String> facetProperties) {
        return new Query(types, criteria, Optional.empty(), sorts, page, facetProperties, false);
    }

    private static ObjectId seedPerson(
        Environment env, Scope scope, Instant createdAt, String name, String email) {
        Map<PropertyRef, Value> properties =
            Map.of(NAME, Value.text(name), EMAIL, Value.text(email));
        return seed(env, scope, PERSON, createdAt, properties);
    }

    private static ObjectId seedProject(
        Environment env, Scope scope, Instant createdAt, String title, String status) {
        Map<PropertyRef, Value> properties =
            Map.of(TITLE, Value.text(title), STATUS, Value.text(status));
        return seed(env, scope, PROJECT, createdAt, properties);
    }

    private static ObjectId seedTask(
        Environment env,
        Scope scope,
        Instant createdAt,
        String title,
        String status,
        long priority,
        LocalDate dueDate,
        ObjectId assignedTo,
        ObjectId belongsToProject) {
        return seedTaskWithDescription(
            env, scope, createdAt, title, "Description of " + title, status, priority, dueDate,
            assignedTo, belongsToProject);
    }

    private static ObjectId seedTaskWithDescription(
        Environment env,
        Scope scope,
        Instant createdAt,
        String title,
        String description,
        String status,
        long priority,
        LocalDate dueDate,
        ObjectId assignedTo,
        ObjectId belongsToProject) {
        Map<PropertyRef, Value> properties = new HashMap<>();
        properties.put(TITLE, Value.text(title));
        properties.put(DESCRIPTION, Value.text(description));
        properties.put(STATUS, Value.text(status));
        properties.put(PRIORITY, Value.integer(priority));
        properties.put(DUE_DATE, Value.date(dueDate));
        if (assignedTo != null) {
            properties.put(ASSIGNED_TO, Value.ref(assignedTo));
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
