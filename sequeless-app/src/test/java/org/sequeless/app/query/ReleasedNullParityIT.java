package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.NullNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.query.api.BrowseQuery;
import org.sequeless.query.api.QueryService;
import org.sequeless.query.field.api.FieldScopeRequest;
import org.sequeless.query.field.api.TraversalConfig;
import org.sequeless.query.field.api.VersionPolicy;
import org.sequeless.query.field.spi.FieldSource;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Pins the released Postgres browse's NULL semantics over the same stored rows, against the id sets the prototype's own
 * query store produced when this test was written (spec 17 P2a, Navigator gate 2). Spec 17 P4 deleted that store's
 * browse, so each test now asserts the released side against the same explicit expectation the prototype side met.
 *
 * <p>The released side is {@link ReleasedBrowsePortAdapter},
 * which maps the criteria to released filter nodes and calls {@link QueryService#browse}, and so reaches the released
 * Postgres browse port. The negated form is also run straight through {@link QueryService#browse} with a {@code is
 * not} {@link NullNode} filter, so the released filter is compared without the host mapper in the way.
 *
 * <p>The rows are seeded as raw JSON in {@code sq_object}. The domain write path holds no JSON null, so an explicit
 * null can only be stored this way. The property under test is {@code status} on {@code Task}, a single-valued string.
 * An empty list is stored in the tagged list form {@code {"list": []}} on that same single-valued property, because
 * both readers decode the tagged form and a bare {@code []} is not decodable by either of them. Each test seeds its
 * own tenant, so the rows of one test do not reach another.
 *
 * <p>An explicit JSON null is different: neither reader decodes it (the tagged-value decoder throws on a bare null), so
 * a browse that returns that row fails with the same exception the prototype's reader raised. The test {@link
 * #explicitJsonNullFailsTheSameWayOnBothSides} pins that failure, not an id set, because no id set is produced for that
 * case.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "sequeless.ontology.reasoner=owl")
class ReleasedNullParityIT extends PostgresTestcontainersSupport {

    private static final String TASK = "https://sequeless.dev/ns/ref#Task";
    private static final String STATUS = "https://sequeless.dev/ns/ref#status";
    private static final String PRIORITY = "https://sequeless.dev/ns/ref#priority";

    @Autowired
    @Qualifier("sequelessPersistenceDataSource")
    private DataSource dataSource;

    @Autowired
    private QueryService queryService;

    @Autowired
    private FieldSource fieldSource;

    @Autowired
    private SchemaSource schemaSource;

    private JdbcClient jdbc;
    private Scope scope;
    private Schema schema;
    private ReleasedBrowsePortAdapter released;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        scope = new Scope(new TenantId("parity-" + UUID.randomUUID()), new Principal("parity", Set.of("member")));
        schema = schemaSource.schema(scope.tenantId());
        released = new ReleasedBrowsePortAdapter(queryService, fieldSource);
    }

    // --- the four stored shapes, on one tenant ---------------------------------------------------

    @Test
    void isNullAndTheEmptyListAgreeOnAMissingKeyAndAnEmptyList() {
        ObjectId missing = insert("{\"" + PRIORITY + "\": {\"integer\": 1}}");
        ObjectId present = insert("{\"" + PRIORITY + "\": {\"integer\": 3}, \"" + STATUS + "\": {\"text\": \"OPEN\"}}");
        ObjectId emptyList = insert("{\"" + PRIORITY + "\": {\"integer\": 4}, \"" + STATUS + "\": {\"list\": []}}");

        Set<ObjectId> fromReleased = ids(released.browse(scope, schema, query(isNull())));

        assertThat(fromReleased).containsExactlyInAnyOrder(missing, emptyList).doesNotContain(present);
    }

    @Test
    void notNullAgreesOnAPresentValue() {
        insert("{\"" + PRIORITY + "\": {\"integer\": 1}}");
        ObjectId present = insert("{\"" + PRIORITY + "\": {\"integer\": 3}, \"" + STATUS + "\": {\"text\": \"OPEN\"}}");
        insert("{\"" + PRIORITY + "\": {\"integer\": 4}, \"" + STATUS + "\": {\"list\": []}}");

        Set<ObjectId> fromReleased = ids(released.browse(scope, schema, query(notNull())));

        assertThat(fromReleased).containsExactly(present);
    }

    @Test
    void theNegatedNotNullFilterOnTheReleasedQueryServiceAgreesWithNotNull() {
        insert("{\"" + PRIORITY + "\": {\"integer\": 1}}");
        ObjectId present = insert("{\"" + PRIORITY + "\": {\"integer\": 3}, \"" + STATUS + "\": {\"text\": \"OPEN\"}}");
        insert("{\"" + PRIORITY + "\": {\"integer\": 4}, \"" + STATUS + "\": {\"list\": []}}");

        Set<ObjectId> fromReleasedNegated = releasedIds(new FieldFilter(STATUS, "is not", NullNode.getInstance()));

        assertThat(fromReleasedNegated).containsExactly(present);
    }

    @Test
    void aCombinedFilterWithAComparisonAgrees() {
        ObjectId missing = insert("{\"" + PRIORITY + "\": {\"integer\": 1}}");
        insert("{\"" + PRIORITY + "\": {\"integer\": 3}, \"" + STATUS + "\": {\"text\": \"OPEN\"}}");
        ObjectId emptyList = insert("{\"" + PRIORITY + "\": {\"integer\": 4}, \"" + STATUS + "\": {\"list\": []}}");
        Criterion greaterThanOne = new Criterion(PRIORITY, Operator.GT, Optional.of(Value.integer(1)));

        Set<ObjectId> fromReleased = ids(released.browse(scope, schema, query(isNull(), greaterThanOne)));

        assertThat(fromReleased).containsExactly(emptyList).doesNotContain(missing);
    }

    // --- the explicit JSON null ------------------------------------------------------------------

    @Test
    void explicitJsonNullFailsTheSameWayOnBothSides() {
        insert("{\"" + PRIORITY + "\": {\"integer\": 1}}");
        insert("{\"" + PRIORITY + "\": {\"integer\": 2}, \"" + STATUS + "\": null}");
        insert("{\"" + PRIORITY + "\": {\"integer\": 3}, \"" + STATUS + "\": {\"text\": \"OPEN\"}}");

        String fromReleased = outcome(() -> released.browse(scope, schema, query(isNull())));

        String expectedFailure = "failed java.lang.IllegalArgumentException: Unrecognized tagged value: null";
        assertThat(fromReleased).isEqualTo(expectedFailure);
    }

    // --- helpers ---------------------------------------------------------------------------------

    private ObjectId insert(String props) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO sq_object (id, tenant_id, type_iri, version, props, created_at, created_by, "
                        + "updated_at, updated_by) VALUES (:id, :tenantId, :typeIri, 1, CAST(:props AS jsonb), "
                        + "now(), 'parity', now(), 'parity')")
                .param("id", id)
                .param("tenantId", scope.tenantId().value())
                .param("typeIri", TASK)
                .param("props", props)
                .update();
        return new ObjectId(id);
    }

    private static Criterion isNull() {
        return new Criterion(STATUS, Operator.IS_NULL, Optional.empty());
    }

    private static Criterion notNull() {
        return new Criterion(STATUS, Operator.NOT_NULL, Optional.empty());
    }

    private Query query(Criterion... criteria) {
        return new Query(
                Set.of(TASK), List.of(criteria), Optional.empty(), List.of(), new Page(0, 100), List.of(), false, TASK);
    }

    /** The released QueryService browse with one filter node, rooted at Task, with no host mapping. */
    private Set<ObjectId> releasedIds(FieldFilter filter) {
        FieldScopeRequest request = new FieldScopeRequest(
                scope.tenantId(),
                ReleasedBrowsePortAdapter.rootClassRef(TASK, schema),
                new VersionPolicy.Union(),
                TraversalConfig.defaults(),
                Optional.empty(),
                Optional.empty());
        BrowseQuery browse = new BrowseQuery(
                Optional.of(filter), Optional.empty(), List.of(), new org.sequeless.query.api.Page(0, 100), List.of());
        return queryService.browse(request, browse).items().stream()
                .map(item -> new ObjectId(item.id().value()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<ObjectId> ids(QueryResult result) {
        return result.items().stream().map(ObjectView::id).collect(Collectors.toUnmodifiableSet());
    }

    private static String outcome(Supplier<QueryResult> browse) {
        try {
            List<String> ids = ids(browse.get()).stream()
                    .map(id -> id.value().toString())
                    .sorted()
                    .toList();
            return "ids " + ids;
        } catch (RuntimeException failure) {
            return "failed " + failure.getClass().getName() + ": " + failure.getMessage();
        }
    }
}
