package org.sequeless.testkit.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.sequeless.ontology.testfixtures.Fixtures.ASSIGNED_TO_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.BELONGS_TO_PROJECT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.DESCRIPTION_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.DUE_DATE_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.PERSON_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.PRIORITY_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.PROJECT_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.STATUS_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TASK_IRI;
import static org.sequeless.ontology.testfixtures.Fixtures.TITLE_IRI;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.BrowsePort;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.FacetBucket;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.Sort;
import org.sequeless.testkit.Fixtures;

/**
 * The mechanical form of the behavioural contract documented on {@link BrowsePort}'s
 * interface-level javadoc. Every {@link BrowsePort} implementation — adapter or test double — is
 * expected to satisfy every clause of that javadoc, and this class exercises each clause once,
 * against whatever {@link Environment} {@link #freshEnvironment()} supplies. It holds every test
 * of the query contract that predated the split into read, browse and index preparation ports,
 * re-expressed against {@link BrowsePort#browse}.
 *
 * <p>To use this contract, extend it from a test class in your own module and implement {@link
 * #freshEnvironment()} to return a brand-new, empty {@link ObjectStorePort} paired with a {@link
 * BrowsePort} that queries the same underlying storage:
 *
 * <pre>{@code
 * class MyAdapterContractTest extends BrowseContract {
 *     protected Environment freshEnvironment() {
 *         ObjectStorePort store = new MyAdapter();
 *         return new Environment(store, MyBrowseAdapter.over(store));
 *     }
 * }
 * }</pre>
 *
 * <p>Unlike {@link org.sequeless.testkit.object.ObjectStoreContract}, this contract is not
 * ontology-agnostic: exercising real filters, sorts, and facets needs a real {@link
 * Schema} built from real properties, so every test here seeds {@link
 * org.sequeless.spi.object.BusinessObject}s of the reference-domain {@code Task}, {@code
 * Project}, and {@code Person} types — via {@link Environment#store()}'s {@link
 * ObjectStorePort#commit} — and browses them through {@link Environment#browsePort()} against
 * {@link QueryFixtures#schema()}, a hand-built {@link Schema} mirroring {@code reference.ttl}.
 *
 * <p><b>What this contract deliberately does not check.</b> No assertion here depends on a
 * particular storage or indexing technology. Every assertion is about the behaviour {@link
 * BrowsePort}'s javadoc documents: null-safety, tenant scoping, soft-delete filtering, the union
 * semantics of {@link Query#types()}, the {@code (createdAt, id)} ascending default sort order,
 * page overrun, and the "conventional multi-select" facet-counting rule.
 *
 * <p><b>Timestamp precision.</b> Every seeded {@code createdAt} is a whole microsecond, because
 * {@link ObjectStorePort} lets adapters store {@link java.time.Instant}s at microsecond precision,
 * and {@link BrowsePort} orders by {@code createdAt} as stored.
 */
public abstract class BrowseContract {

    private static final TypeRef TASK = new TypeRef(TASK_IRI);
    private static final TypeRef PROJECT = new TypeRef(PROJECT_IRI);
    private static final TypeRef PERSON = new TypeRef(PERSON_IRI);

    private static final PropertyRef TITLE = new PropertyRef(TITLE_IRI);
    private static final PropertyRef STATUS = new PropertyRef(STATUS_IRI);
    private static final PropertyRef DESCRIPTION = new PropertyRef(DESCRIPTION_IRI);
    private static final PropertyRef PRIORITY = new PropertyRef(PRIORITY_IRI);
    private static final PropertyRef DUE_DATE = new PropertyRef(DUE_DATE_IRI);
    private static final PropertyRef ASSIGNED_TO = new PropertyRef(ASSIGNED_TO_IRI);
    private static final PropertyRef BELONGS_TO_PROJECT = new PropertyRef(BELONGS_TO_PROJECT_IRI);
    private static final PropertyRef NAME = new PropertyRef(QueryFixtures.NAME_IRI);
    private static final PropertyRef EMAIL = new PropertyRef(QueryFixtures.EMAIL_IRI);

    // Unsigned order: ID_A < ID_B < ID_C < ID_D. The signed UUID.compareTo order would be D, B, A, C.
    private static final ObjectId ID_A = ObjectId.parse("00000000-0000-4000-7fff-ffffffffffff");
    private static final ObjectId ID_B = ObjectId.parse("00000000-0000-4000-8000-000000000000");
    private static final ObjectId ID_C = ObjectId.parse("7fffffff-ffff-4fff-bfff-ffffffffffff");
    private static final ObjectId ID_D = ObjectId.parse("80000000-0000-4000-8000-000000000000");

    /**
     * An {@link ObjectStorePort} paired with the {@link BrowsePort} that queries the same
     * underlying storage — a conforming {@link BrowsePort} implementation only makes sense against
     * objects committed through a particular store, exactly as {@link
     * org.sequeless.testkit.validation.ValidationContract.Fixture} pairs a {@link
     * org.sequeless.spi.validation.ValidationPort} with the {@link Schema} it
     * validates against.
     *
     * @param store the store to seed fixture data into via {@link ObjectStorePort#commit}; must
     *     not be {@code null}
     * @param browsePort the port under test, expected to answer queries over whatever {@code
     *     store} holds; must not be {@code null}
     */
    public record Environment(ObjectStorePort store, BrowsePort browsePort) {

        public Environment {
            Objects.requireNonNull(store, "store must not be null");
            Objects.requireNonNull(browsePort, "browsePort must not be null");
        }
    }

    /**
     * @return a fresh {@link Environment} pairing a brand-new, empty {@link ObjectStorePort} with
     *     a {@link BrowsePort} that queries it; invoked fresh for every {@code @Test} method, so
     *     implementors may return new instances each time or shared ones, whichever suits the
     *     port under test — but the returned environment must not carry over state from a
     *     previous test
     */
    protected abstract Environment freshEnvironment();

    @Test
    void browseRejectsNullArguments() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Schema schema = QueryFixtures.schema();
        Query query = queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 20));

        assertThatNullPointerException().isThrownBy(() -> env.browsePort().browse(null, schema, query));
        assertThatNullPointerException().isThrownBy(() -> env.browsePort().browse(scope, null, query));
        assertThatNullPointerException().isThrownBy(() -> env.browsePort().browse(scope, schema, null));

        Set<String> withNull = new HashSet<>();
        withNull.add(null);
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new Query(withNull, List.of(), Optional.empty(), List.of(), new Page(0, 20), List.of(), false));
    }

    @Test
    void equalityFilterMatchesOnlyExactValue() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId open1 = seedTask(env, scope, base, "Open task one", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        ObjectId open2 = seedTask(
                env, scope, base.plusSeconds(1), "Open task two", "OPEN", 2, LocalDate.of(2026, 2, 2), null, null);
        seedTask(env, scope, base.plusSeconds(2), "Closed task", "CLOSED", 3, LocalDate.of(2026, 2, 3), null, null);

        Query query = queryOf(
                Set.of(TASK_IRI),
                List.of(new Criterion(STATUS_IRI, Operator.EQ, Optional.of(Value.text("OPEN")))),
                new Page(0, 20));

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        assertThat(result.items()).extracting(ObjectView::id).containsExactlyInAnyOrder(open1, open2);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void referenceFilterMatchesByTargetId() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId alice = seedPerson(env, scope, base, "Alice Smith", "alice@example.com");
        ObjectId bob = seedPerson(env, scope, base.plusSeconds(1), "Bob Jones", "bob@example.com");

        ObjectId taskForAlice = seedTask(
                env, scope, base.plusSeconds(2), "Alice's task", "OPEN", 1, LocalDate.of(2026, 2, 1), alice, null);
        seedTask(env, scope, base.plusSeconds(3), "Bob's task", "OPEN", 2, LocalDate.of(2026, 2, 2), bob, null);

        Query query = queryOf(
                Set.of(TASK_IRI),
                List.of(new Criterion(ASSIGNED_TO_IRI, Operator.EQ, Optional.of(Value.ref(alice)))),
                new Page(0, 20));

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(taskForAlice);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void dateRangeFilterUsesGteAndLte() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seedTask(env, scope, base, "Too early", "OPEN", 1, LocalDate.of(2026, 1, 1), null, null);
        ObjectId inWindow = seedTask(
                env, scope, base.plusSeconds(1), "In window", "OPEN", 2, LocalDate.of(2026, 2, 15), null, null);
        seedTask(env, scope, base.plusSeconds(2), "Too late", "OPEN", 3, LocalDate.of(2026, 3, 1), null, null);

        Query query = queryOf(
                Set.of(TASK_IRI),
                List.of(
                        new Criterion(DUE_DATE_IRI, Operator.GTE, Optional.of(Value.date(LocalDate.of(2026, 2, 1)))),
                        new Criterion(DUE_DATE_IRI, Operator.LTE, Optional.of(Value.date(LocalDate.of(2026, 2, 28))))),
                new Page(0, 20));

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(inWindow);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void numericSortOrdersAscendingAndDescending() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId low = seedTask(env, scope, base, "Low priority", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        ObjectId mid = seedTask(
                env, scope, base.plusSeconds(1), "Mid priority", "OPEN", 5, LocalDate.of(2026, 2, 2), null, null);
        ObjectId high = seedTask(
                env, scope, base.plusSeconds(2), "High priority", "OPEN", 9, LocalDate.of(2026, 2, 3), null, null);

        Schema schema = QueryFixtures.schema();

        Query ascending =
                queryOf(Set.of(TASK_IRI), List.of(), List.of(new Sort(PRIORITY_IRI, Direction.ASC)), new Page(0, 20));
        QueryResult ascResult = env.browsePort().browse(scope, schema, ascending);
        assertThat(ascResult.items()).extracting(ObjectView::id).containsExactly(low, mid, high);

        Query descending =
                queryOf(Set.of(TASK_IRI), List.of(), List.of(new Sort(PRIORITY_IRI, Direction.DESC)), new Page(0, 20));
        QueryResult descResult = env.browsePort().browse(scope, schema, descending);
        assertThat(descResult.items()).extracting(ObjectView::id).containsExactly(high, mid, low);
    }

    @Test
    void facetCountsGroupByScalarPropertyReflectMultiSelectExclusion() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId openLow = seedTask(env, scope, base, "Open low", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        seedTask(env, scope, base.plusSeconds(1), "Open high", "OPEN", 2, LocalDate.of(2026, 2, 2), null, null);
        seedTask(env, scope, base.plusSeconds(2), "Closed low", "CLOSED", 1, LocalDate.of(2026, 2, 3), null, null);
        seedTask(env, scope, base.plusSeconds(3), "Closed high", "CLOSED", 2, LocalDate.of(2026, 2, 4), null, null);

        // Both a criterion on the facet property itself (status = OPEN) and an unrelated one
        // (priority = 1) are applied. Per BrowsePort's "conventional multi-select" rule, the
        // status facet's bucket counts must be computed against every criterion EXCEPT the one
        // on status itself — so they should reflect both OPEN and CLOSED among the
        // priority-filtered subset, not just the single OPEN value the result set is narrowed to.
        Query query = new Query(
                Set.of(TASK_IRI),
                List.of(
                        new Criterion(STATUS_IRI, Operator.EQ, Optional.of(Value.text("OPEN"))),
                        new Criterion(PRIORITY_IRI, Operator.EQ, Optional.of(Value.integer(1)))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of(STATUS_IRI),
                false);

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(openLow);
        assertThat(result.total()).isEqualTo(1);

        List<FacetBucket> statusBuckets = result.facets().get(STATUS_IRI);
        assertThat(statusBuckets).containsExactlyInAnyOrder(new FacetBucket("OPEN", 1), new FacetBucket("CLOSED", 1));
    }

    @Test
    void facetCountsOnReferencePropertyResolveDisplayLabel() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId alice = seedPerson(env, scope, base, "Alice Smith", "alice@example.com");
        ObjectId bob = seedPerson(env, scope, base.plusSeconds(1), "Bob Jones", "bob@example.com");

        seedTask(env, scope, base.plusSeconds(2), "Alice task one", "OPEN", 1, LocalDate.of(2026, 2, 1), alice, null);
        seedTask(env, scope, base.plusSeconds(3), "Alice task two", "OPEN", 2, LocalDate.of(2026, 2, 2), alice, null);
        seedTask(env, scope, base.plusSeconds(4), "Bob task", "OPEN", 3, LocalDate.of(2026, 2, 3), bob, null);

        Query query = queryOf(Set.of(TASK_IRI), List.of(), List.of(), new Page(0, 20), List.of(ASSIGNED_TO_IRI));

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        List<FacetBucket> assignedToBuckets = result.facets().get(ASSIGNED_TO_IRI);
        assertThat(assignedToBuckets)
                .extracting(FacetBucket::value)
                .containsExactlyInAnyOrder("Alice Smith", "Bob Jones");
        assertThat(assignedToBuckets)
                .filteredOn(bucket -> bucket.value().equals("Alice Smith"))
                .extracting(FacetBucket::count)
                .containsExactly(2L);
        assertThat(assignedToBuckets)
                .filteredOn(bucket -> bucket.value().equals("Bob Jones"))
                .extracting(FacetBucket::count)
                .containsExactly(1L);
    }

    @Test
    void facetTiesOrderByCodePoint() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seedTask(env, scope, base, "Lower case", "b", 1, LocalDate.of(2026, 2, 1), null, null);
        seedTask(env, scope, base.plusSeconds(1), "Upper case", "B", 2, LocalDate.of(2026, 2, 2), null, null);

        QueryResult result = env.browsePort()
                .browse(
                        scope,
                        QueryFixtures.schema(),
                        queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 20), List.of(STATUS_IRI)));

        // Equal counts: U+0042 (B) sorts before U+0062 (b) in code point order.
        assertThat(result.facets().get(STATUS_IRI)).containsExactly(new FacetBucket("B", 1), new FacetBucket("b", 1));
    }

    @Test
    void scalarFacetDropsNullKeyBucket() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seedTask(env, scope, base, "Lower case", "b", 1, LocalDate.of(2026, 2, 1), null, null);
        seedTask(env, scope, base.plusSeconds(1), "Upper case", "B", 2, LocalDate.of(2026, 2, 2), null, null);
        // No status: the facet value is absent, so this object's bucket key would be null.
        seed(env, scope, TASK, base.plusSeconds(2), Map.of(TITLE, Value.text("No status"), PRIORITY, Value.integer(3)));

        QueryResult result = env.browsePort()
                .browse(
                        scope,
                        QueryFixtures.schema(),
                        queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 20), List.of(STATUS_IRI)));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.facets().get(STATUS_IRI)).containsExactly(new FacetBucket("B", 1), new FacetBucket("b", 1));
    }

    @Test
    void facetOrdersSupplementaryAfterBmpWhenCountsTie() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        // Code point order: U+00E9 < U+FFFD < U+1F600 (the emoji, a surrogate pair in UTF-16).
        // U+00E9 and U+1F600 alone sort the same in UTF-16 order, so U+FFFD is what pins the change:
        // UTF-16 code units put the surrogate pair (0xD83D) before U+FFFD (0xFFFD).
        // Seeded out of expected order, so the result cannot be the insertion order.
        seedTask(env, scope, base, "Emoji", "😀", 3, LocalDate.of(2026, 2, 3), null, null);
        seedTask(env, scope, base.plusSeconds(1), "Accented", "é", 1, LocalDate.of(2026, 2, 1), null, null);
        seedTask(env, scope, base.plusSeconds(2), "Replacement", "�", 2, LocalDate.of(2026, 2, 2), null, null);

        QueryResult result = env.browsePort()
                .browse(
                        scope,
                        QueryFixtures.schema(),
                        queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 20), List.of(STATUS_IRI)));

        assertThat(result.facets().get(STATUS_IRI))
                .containsExactly(new FacetBucket("é", 1), new FacetBucket("�", 1), new FacetBucket("😀", 1));
    }

    @Test
    void referenceFacetDropsNullLabelBucket() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId alice = seedPerson(env, scope, base, "Alice Smith", "alice@example.com");
        ObjectId bob = seedPerson(env, scope, base.plusSeconds(1), "Bob Jones", "bob@example.com");
        // No name: the display label is absent, so this target's bucket key would be null.
        ObjectId nameless =
                seed(env, scope, PERSON, base.plusSeconds(2), Map.of(EMAIL, Value.text("nobody@example.com")));

        seedTask(env, scope, base.plusSeconds(3), "Alice task", "OPEN", 1, LocalDate.of(2026, 2, 1), alice, null);
        seedTask(env, scope, base.plusSeconds(4), "Bob task one", "OPEN", 2, LocalDate.of(2026, 2, 2), bob, null);
        seedTask(env, scope, base.plusSeconds(5), "Bob task two", "OPEN", 3, LocalDate.of(2026, 2, 3), bob, null);
        seedTask(env, scope, base.plusSeconds(6), "Nameless task", "OPEN", 4, LocalDate.of(2026, 2, 4), nameless, null);

        QueryResult result = env.browsePort()
                .browse(
                        scope,
                        QueryFixtures.schema(),
                        queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 20), List.of(ASSIGNED_TO_IRI)));

        // Bob Jones (2) sorts before Alice Smith (1) by count, although "Alice Smith" < "Bob Jones" by value.
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.facets().get(ASSIGNED_TO_IRI))
                .containsExactly(new FacetBucket("Bob Jones", 2), new FacetBucket("Alice Smith", 1));
    }

    @Test
    void textSearchRanksBestMatchFirst() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId bestMatch = seedTaskWithDescription(
                env,
                scope,
                base,
                "Quarterly budget review",
                "Review the quarterly budget numbers",
                "OPEN",
                1,
                LocalDate.of(2026, 2, 1),
                null,
                null);
        seedTaskWithDescription(
                env,
                scope,
                base.plusSeconds(1),
                "Unrelated task",
                "Something else entirely",
                "OPEN",
                2,
                LocalDate.of(2026, 2, 2),
                null,
                null);
        seedTaskWithDescription(
                env,
                scope,
                base.plusSeconds(2),
                "Another task",
                "Nothing to do with the topic",
                "OPEN",
                3,
                LocalDate.of(2026, 2, 3),
                null,
                null);

        Query query = new Query(
                Set.of(TASK_IRI),
                List.of(),
                Optional.of("quarterly budget"),
                List.of(),
                new Page(0, 20),
                List.of(),
                false);

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        assertThat(result.items()).isNotEmpty();
        assertThat(result.items().get(0).id()).isEqualTo(bestMatch);
    }

    @Test
    void workItemQueryReturnsBothTaskAndProjectSubtypes() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId task = seedTask(env, scope, base, "A task", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        ObjectId project = seedProject(env, scope, base.plusSeconds(1), "A project", "ACTIVE");

        Query query = queryOf(Set.of(TASK_IRI, PROJECT_IRI), List.of(), new Page(0, 20));

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        assertThat(result.items()).extracting(ObjectView::id).containsExactlyInAnyOrder(task, project);
        assertThat(result.items())
                .extracting(item -> item.type().iri())
                .containsExactlyInAnyOrder(TASK_IRI, PROJECT_IRI);
    }

    @Test
    void paginationRespectsPageAndSize() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        List<ObjectId> expectedOrder = List.of(
                seedTask(env, scope, base, "Task 1", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null),
                seedTask(env, scope, base.plusSeconds(1), "Task 2", "OPEN", 2, LocalDate.of(2026, 2, 2), null, null),
                seedTask(env, scope, base.plusSeconds(2), "Task 3", "OPEN", 3, LocalDate.of(2026, 2, 3), null, null),
                seedTask(env, scope, base.plusSeconds(3), "Task 4", "OPEN", 4, LocalDate.of(2026, 2, 4), null, null),
                seedTask(env, scope, base.plusSeconds(4), "Task 5", "OPEN", 5, LocalDate.of(2026, 2, 5), null, null));

        Schema schema = QueryFixtures.schema();

        // No explicit sort: relies on BrowsePort's documented default order, ascending
        // (createdAt, id), so page contents are deterministic.
        QueryResult page0 =
                env.browsePort().browse(scope, schema, queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 2)));
        assertThat(page0.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(0, 2));
        assertThat(page0.total()).isEqualTo(5);

        QueryResult page1 =
                env.browsePort().browse(scope, schema, queryOf(Set.of(TASK_IRI), List.of(), new Page(1, 2)));
        assertThat(page1.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(2, 4));
        assertThat(page1.total()).isEqualTo(5);

        QueryResult page2 =
                env.browsePort().browse(scope, schema, queryOf(Set.of(TASK_IRI), List.of(), new Page(2, 2)));
        assertThat(page2.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(4, 5));
        assertThat(page2.total()).isEqualTo(5);

        QueryResult overrun =
                env.browsePort().browse(scope, schema, queryOf(Set.of(TASK_IRI), List.of(), new Page(5, 2)));
        assertThat(overrun.items()).isEmpty();
        assertThat(overrun.total()).isEqualTo(5);
    }

    @Test
    void includeDeletedDefaultsToExcluded() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId id = seedTask(env, scope, base, "Soon deleted", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        env.store().commit(scope, new ChangeSet(List.of(new Delete(id, 1, base.plusSeconds(1), "tester")), List.of()));

        Query query =
                new Query(Set.of(TASK_IRI), List.of(), Optional.empty(), List.of(), new Page(0, 20), List.of(), false);

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        assertThat(result.items()).extracting(ObjectView::id).doesNotContain(id);
        assertThat(result.total()).isZero();
    }

    @Test
    void paginationOverUnionOfTypesIsOrderedByCreatedAtThenId() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        List<ObjectId> expectedOrder = List.of(
                seedTask(env, scope, base, "Task 1", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null),
                seedProject(env, scope, base.plusSeconds(1), "Project 1", "ACTIVE"),
                seedTask(env, scope, base.plusSeconds(2), "Task 2", "OPEN", 2, LocalDate.of(2026, 2, 2), null, null),
                seedProject(env, scope, base.plusSeconds(3), "Project 2", "ACTIVE"),
                seedTask(env, scope, base.plusSeconds(4), "Task 3", "OPEN", 3, LocalDate.of(2026, 2, 3), null, null));

        Schema schema = QueryFixtures.schema();
        Set<String> types = Set.of(TASK_IRI, PROJECT_IRI);

        QueryResult page0 = env.browsePort().browse(scope, schema, queryOf(types, List.of(), new Page(0, 2)));
        assertThat(page0.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(0, 2));
        assertThat(page0.total()).isEqualTo(5);

        QueryResult page2 = env.browsePort().browse(scope, schema, queryOf(types, List.of(), new Page(2, 2)));
        assertThat(page2.items()).extracting(ObjectView::id).containsExactlyElementsOf(expectedOrder.subList(4, 5));
        assertThat(page2.total()).isEqualTo(5);

        QueryResult overrun = env.browsePort().browse(scope, schema, queryOf(types, List.of(), new Page(3, 2)));
        assertThat(overrun.items()).isEmpty();
        assertThat(overrun.total()).isEqualTo(5);
    }

    @Test
    void includeDeletedWhenRequestedIncludesSoftDeleted() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        ObjectId id = seedTask(env, scope, base, "Soon deleted", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);
        env.store().commit(scope, new ChangeSet(List.of(new Delete(id, 1, base.plusSeconds(1), "tester")), List.of()));

        Query query =
                new Query(Set.of(TASK_IRI), List.of(), Optional.empty(), List.of(), new Page(0, 20), List.of(), true);

        QueryResult result = env.browsePort().browse(scope, QueryFixtures.schema(), query);

        assertThat(result.items()).extracting(ObjectView::id).containsExactly(id);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void browseIsTenantScoped() {
        Environment env = freshEnvironment();
        Scope alice = Fixtures.scope("alice");
        Scope otherTenant = new Scope(new TenantId("other-tenant"), Principal.ANONYMOUS);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seedTask(env, alice, base, "Alice's task", "OPEN", 1, LocalDate.of(2026, 2, 1), null, null);

        Schema schema = QueryFixtures.schema();
        Query query = queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 20));

        QueryResult otherResult = env.browsePort().browse(otherTenant, schema, query);
        assertThat(otherResult.items()).isEmpty();
        assertThat(otherResult.total()).isZero();

        QueryResult otherFacets = env.browsePort()
                .browse(
                        otherTenant,
                        schema,
                        queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 20), List.of(STATUS_IRI)));
        assertThat(otherFacets.facets().get(STATUS_IRI)).isEmpty();

        QueryResult aliceResult = env.browsePort().browse(alice, schema, query);
        assertThat(aliceResult.total()).isEqualTo(1);
    }

    @Test
    void defaultOrderBreaksCreatedAtTiesByUnsignedId() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seedTaskWithId(env, scope, ID_D, base, "Tied task", 1);
        seedTaskWithId(env, scope, ID_C, base, "Tied task", 1);
        seedTaskWithId(env, scope, ID_B, base, "Tied task", 1);
        seedTaskWithId(env, scope, ID_A, base, "Tied task", 1);

        assertThat(browseIds(env, scope, queryOf(Set.of(TASK_IRI), List.of(), new Page(0, 4))))
                .isEqualTo(List.of(ID_A, ID_B, ID_C, ID_D));
        assertThat(browseIds(env, scope, queryOf(Set.of(TASK_IRI), List.of(), new Page(1, 2))))
                .isEqualTo(List.of(ID_C, ID_D));
    }

    @Test
    void explicitSortBreaksTiesByUnsignedIdInEitherDirection() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seedTaskWithId(env, scope, ID_D, base, "Tied task", 5);
        seedTaskWithId(env, scope, ID_C, base.plusSeconds(1), "Tied task", 5);

        for (Direction direction : Direction.values()) {
            Query query =
                    queryOf(Set.of(TASK_IRI), List.of(), List.of(new Sort(PRIORITY_IRI, direction)), new Page(0, 20));
            assertThat(browseIds(env, scope, query)).isEqualTo(List.of(ID_C, ID_D));
        }
    }

    @Test
    void textSearchBreaksRankTiesByCreatedAtThenUnsignedId() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seedTaskWithId(env, scope, ID_A, base.plusSeconds(1), "Tied task", 1);
        seedTaskWithId(env, scope, ID_D, base, "Tied task", 1);
        seedTaskWithId(env, scope, ID_C, base, "Tied task", 1);

        Query query = new Query(
                Set.of(TASK_IRI), List.of(), Optional.of("tied"), List.of(), new Page(0, 20), List.of(), false);

        assertThat(browseIds(env, scope, query)).isEqualTo(List.of(ID_C, ID_D, ID_A));
    }

    @Test
    void referenceSortOrdersByUnsignedTargetId() {
        Environment env = freshEnvironment();
        Scope scope = Fixtures.defaultScope();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        seed(env, scope, ID_C, PERSON, base, Map.of(NAME, Value.text("Person C"), EMAIL, Value.text("c@example.com")));
        seed(env, scope, ID_D, PERSON, base, Map.of(NAME, Value.text("Person D"), EMAIL, Value.text("d@example.com")));

        ObjectId forD =
                seedTask(env, scope, base.plusSeconds(1), "For D", "OPEN", 1, LocalDate.of(2026, 2, 1), ID_D, null);
        ObjectId forC =
                seedTask(env, scope, base.plusSeconds(2), "For C", "OPEN", 2, LocalDate.of(2026, 2, 2), ID_C, null);

        Query ascending = queryOf(
                Set.of(TASK_IRI), List.of(), List.of(new Sort(ASSIGNED_TO_IRI, Direction.ASC)), new Page(0, 20));
        assertThat(browseIds(env, scope, ascending)).isEqualTo(List.of(forC, forD));

        Query descending = queryOf(
                Set.of(TASK_IRI), List.of(), List.of(new Sort(ASSIGNED_TO_IRI, Direction.DESC)), new Page(0, 20));
        assertThat(browseIds(env, scope, descending)).isEqualTo(List.of(forD, forC));
    }

    // -- Fixture-seeding helpers -------------------------------------------------------------

    private static Query queryOf(Set<String> types, List<Criterion> criteria, Page page) {
        return queryOf(types, criteria, List.of(), page, List.of());
    }

    private static Query queryOf(Set<String> types, List<Criterion> criteria, List<Sort> sorts, Page page) {
        return queryOf(types, criteria, sorts, page, List.of());
    }

    private static Query queryOf(Set<String> types, List<Criterion> criteria, Page page, List<String> facetProperties) {
        return queryOf(types, criteria, List.of(), page, facetProperties);
    }

    private static Query queryOf(
            Set<String> types, List<Criterion> criteria, List<Sort> sorts, Page page, List<String> facetProperties) {
        return new Query(types, criteria, Optional.empty(), sorts, page, facetProperties, false);
    }

    private static ObjectId seedPerson(Environment env, Scope scope, Instant createdAt, String name, String email) {
        Map<PropertyRef, Value> properties = Map.of(NAME, Value.text(name), EMAIL, Value.text(email));
        return seed(env, scope, PERSON, createdAt, properties);
    }

    private static ObjectId seedProject(Environment env, Scope scope, Instant createdAt, String title, String status) {
        Map<PropertyRef, Value> properties = Map.of(TITLE, Value.text(title), STATUS, Value.text(status));
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
                env,
                scope,
                createdAt,
                title,
                "Description of " + title,
                status,
                priority,
                dueDate,
                assignedTo,
                belongsToProject);
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

    private static ObjectId seedTaskWithId(
            Environment env, Scope scope, ObjectId id, Instant createdAt, String title, long priority) {
        return seed(
                env,
                scope,
                id,
                TASK,
                createdAt,
                Map.of(
                        TITLE, Value.text(title),
                        DESCRIPTION, Value.text("Description of " + title),
                        STATUS, Value.text("OPEN"),
                        PRIORITY, Value.integer(priority),
                        DUE_DATE, Value.date(LocalDate.of(2026, 2, 1))));
    }

    private static List<ObjectId> browseIds(Environment env, Scope scope, Query query) {
        return env.browsePort().browse(scope, QueryFixtures.schema(), query).items().stream()
                .map(ObjectView::id)
                .toList();
    }

    private static ObjectId seed(
            Environment env, Scope scope, TypeRef type, Instant createdAt, Map<PropertyRef, Value> properties) {
        return seed(env, scope, ObjectId.random(), type, createdAt, properties);
    }

    private static ObjectId seed(
            Environment env,
            Scope scope,
            ObjectId id,
            TypeRef type,
            Instant createdAt,
            Map<PropertyRef, Value> properties) {
        String principalId = scope.principal().id();
        Audit audit = new Audit(createdAt, principalId, createdAt, principalId);
        BusinessObject object =
                new BusinessObject(id, type, scope.tenantId(), 1, Optional.empty(), properties, audit, false);
        env.store().commit(scope, new ChangeSet(List.of(new Create(object)), List.of()));
        return id;
    }
}
