package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sequeless.app.query.QueryFixtures.PROJECT_IRI;
import static org.sequeless.app.query.QueryFixtures.SCHEMA;
import static org.sequeless.app.query.QueryFixtures.SCOPE;
import static org.sequeless.app.query.QueryFixtures.TAGS_IRI;
import static org.sequeless.app.query.QueryFixtures.TASK_IRI;
import static org.sequeless.app.query.QueryFixtures.TENANT;
import static org.sequeless.app.query.QueryFixtures.TITLE_IRI;
import static org.sequeless.app.query.QueryFixtures.WORK_ITEM_IRI;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.BrowseResult;
import org.sequeless.query.api.FacetBucket;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.QueryServiceFactory;
import org.sequeless.query.api.TypeRef;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;
import org.sequeless.query.schema.SchemaFieldSource;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.Sort;
import org.sequeless.store.spi.Criterion;
import org.sequeless.store.spi.Operator;

class ReleasedBrowsePortAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-26T08:00:00Z");

    private static FieldScope taskScope() {
        return QueryFixtures.scope(
                TASK_IRI,
                Set.of(TASK_IRI),
                List.of(QueryFixtures.facet(TITLE_IRI, true), QueryFixtures.facet(TAGS_IRI, false)));
    }

    @Test
    void theQueryRootIriIsTheReleasedRootAndTheBrowseIsMapped() {
        FieldScope scope = taskScope();
        QueryFixtures.FakeFieldSource fieldSource = new QueryFixtures.FakeFieldSource(scope);
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();
        ReleasedBrowsePortAdapter adapter = new ReleasedBrowsePortAdapter(queryService, fieldSource);
        Query query = QueryFixtures.query(
                TASK_IRI,
                Set.of(TASK_IRI),
                List.of(new Criterion(TITLE_IRI, Operator.EQ, Optional.of(Value.text("x")))),
                List.of(new Sort(TITLE_IRI, Direction.DESC)),
                List.of(TITLE_IRI));

        adapter.browse(SCOPE, SCHEMA, query);

        assertThat(queryService.requests).hasSize(1);
        assertThat(queryService.requests.get(0).root().iri()).isEqualTo(TASK_IRI);
        assertThat(queryService.requests.get(0).tenantId()).isEqualTo(TENANT);
        org.sequeless.query.api.BrowseQuery browse = queryService.browses.get(0);
        assertThat(browse.filter()).contains(new FieldFilter(TITLE_IRI, "is", CriterionMapper.toJson(Value.text("x"))));
        assertThat(browse.sorts())
                .containsExactly(new org.sequeless.query.api.Sort(
                        scope.facets().get(0).path(), org.sequeless.query.api.Direction.DESC));
        assertThat(browse.facets()).containsExactly(scope.facets().get(0).path());
        assertThat(browse.page()).isEqualTo(new org.sequeless.query.api.Page(0, 20));
    }

    @Test
    void anAbstractRootSendsItsOwnIriNotASubtype() {
        FieldScope scope = QueryFixtures.scope(WORK_ITEM_IRI, Set.of(TASK_IRI, PROJECT_IRI), List.of());
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();
        ReleasedBrowsePortAdapter adapter =
                new ReleasedBrowsePortAdapter(queryService, new QueryFixtures.FakeFieldSource(scope));

        adapter.browse(
                SCOPE,
                SCHEMA,
                QueryFixtures.query(WORK_ITEM_IRI, Set.of(TASK_IRI, PROJECT_IRI), List.of(), List.of(), List.of()));

        assertThat(queryService.requests.get(0).root().iri()).isEqualTo(WORK_ITEM_IRI);
    }

    @Test
    void aConcreteTypeMismatchFailsLoudly() {
        FieldScope scope = QueryFixtures.scope(WORK_ITEM_IRI, Set.of(TASK_IRI), List.of());
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();
        ReleasedBrowsePortAdapter adapter =
                new ReleasedBrowsePortAdapter(queryService, new QueryFixtures.FakeFieldSource(scope));

        assertThatThrownBy(() -> adapter.browse(
                        SCOPE,
                        SCHEMA,
                        QueryFixtures.query(
                                WORK_ITEM_IRI, Set.of(TASK_IRI, PROJECT_IRI), List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("differ");
        assertThat(queryService.browses).isEmpty();
    }

    @Test
    void itemsAndFacetsComeBackAsPrototypeValues() {
        FieldScope scope = taskScope();
        QueryFixtures.FakeQueryService queryService = new QueryFixtures.FakeQueryService();
        ObjectId id = new ObjectId(UUID.fromString("5b6f0c2e-7d1a-4c4e-9a55-0f7a2e1d9c10"));
        ObjectView item = new ObjectView(
                id,
                new TypeRef(TASK_IRI),
                TENANT,
                1L,
                Optional.empty(),
                Map.of(new PropertyRef(TITLE_IRI), Value.text("Report")),
                new Audit(NOW, "alice", NOW, "alice"),
                false);
        FieldPath titlePath = scope.facets().get(0).path();
        queryService.browseResult =
                new BrowseResult(List.of(item), 1L, Map.of(titlePath, List.of(new FacetBucket("OPEN", 1L))));
        ReleasedBrowsePortAdapter adapter =
                new ReleasedBrowsePortAdapter(queryService, new QueryFixtures.FakeFieldSource(scope));

        QueryResult result = adapter.browse(
                SCOPE,
                SCHEMA,
                QueryFixtures.query(TASK_IRI, Set.of(TASK_IRI), List.of(), List.of(), List.of(TITLE_IRI)));

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(id);
        assertThat(result.items().get(0).type()).isEqualTo(new org.sequeless.spi.object.TypeRef(TASK_IRI));
        assertThat(result.facets()).containsOnlyKeys(TITLE_IRI);
        assertThat(result.facets().get(TITLE_IRI)).containsExactly(new org.sequeless.spi.query.FacetBucket("OPEN", 1L));
    }

    @Test
    void includeDeletedHasNoReleasedEquivalentAndIsRejected() {
        ReleasedBrowsePortAdapter adapter = new ReleasedBrowsePortAdapter(
                new QueryFixtures.FakeQueryService(), new QueryFixtures.FakeFieldSource(taskScope()));
        Query withDeleted = new Query(
                Set.of(TASK_IRI),
                List.of(),
                Optional.empty(),
                List.of(),
                new org.sequeless.spi.object.Page(0, 20),
                List.of(),
                true,
                TASK_IRI);

        assertThatThrownBy(() -> adapter.browse(SCOPE, SCHEMA, withDeleted))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("soft-deleted");
    }

    @Test
    void aSortOnANonSortablePropertySurfacesTheReleasedInvalidQueryException() {
        // The real released service over the same scope: its own sortable check rejects the sort, and the adapter
        // does not catch or translate that rejection.
        FieldScope scope = taskScope();
        org.sequeless.query.api.QueryService released = QueryServiceFactory.create(
                new QueryFixtures.FakeFieldSource(scope),
                (fieldScope, browseQuery) -> {
                    throw new AssertionError("the released browse port must not be reached");
                },
                (tenant, read) -> Optional.empty(),
                (tenant, version, rootFields) -> {
                    throw new AssertionError("index preparation is not used here");
                });
        ReleasedBrowsePortAdapter adapter =
                new ReleasedBrowsePortAdapter(released, new QueryFixtures.FakeFieldSource(scope));
        Query query = QueryFixtures.query(
                TASK_IRI, Set.of(TASK_IRI), List.of(), List.of(new Sort(TAGS_IRI, Direction.ASC)), List.of());

        assertThatThrownBy(() -> adapter.browse(SCOPE, SCHEMA, query))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("sortable");
    }

    @Test
    void theRootClassRefIsTheReleasedSchemaAdaptersForTheSameType() {
        TypeDefinition task = new TypeDefinition(TASK_IRI, "Task", List.of(), List.of(), DisplayHints.none(), false);
        Schema schema = new Schema(
                QueryFixtures.VERSION,
                QueryFixtures.NS.substring(0, QueryFixtures.NS.length() - 1),
                Optional.empty(),
                Map.of(),
                List.of(task));
        ClassRef released = new SchemaFieldSource(tenant -> schema)
                .rootClasses(TENANT).stream()
                        .filter(ref -> ref.iri().equals(TASK_IRI))
                        .findFirst()
                        .orElseThrow();

        assertThat(ReleasedBrowsePortAdapter.rootClassRef(TASK_IRI, schema)).isEqualTo(released);
    }
}
