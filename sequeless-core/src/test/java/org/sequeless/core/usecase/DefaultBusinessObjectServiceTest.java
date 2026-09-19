package org.sequeless.core.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.BrowseQuery;
import org.sequeless.core.api.InvalidQueryException;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.core.validation.ValidationException;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.meta.AggregateFunction;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.RollupRule;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.IntegerValue;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectNotFoundException;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.Criterion;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.Operator;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.Sort;
import org.sequeless.spi.validation.ValidationPort;
import org.sequeless.spi.validation.Violation;

/**
 * Unit tests for {@link DefaultBusinessObjectService}, built against a hand-written 2-level type
 * hierarchy (abstract {@code WorkItem} &gt; concrete {@code Task} and {@code Project}, plus
 * unrelated {@code Person} and abstract {@code AbstractThing}), the same idiom {@code
 * TypeHierarchyTest} and {@code DefaultMetaModelServiceTest} use: every port double is either a
 * plain lambda ({@link AuthorizationPort}, {@link ValidationPort}) or a small hand-written fake
 * ({@link FakeOntologyPort}, {@link FakeObjectStorePort}, {@link FakeQueryPort}) rather than a
 * mocking framework. Core must not depend on the testkit module at all, so {@code
 * sequeless-spi-testkit}'s {@code InMemoryObjectStorePort} is deliberately not reused here.
 */
class DefaultBusinessObjectServiceTest {

    private static final String NS = "https://sequeless.dev/ns/bo#";
    private static final String WORK_ITEM_IRI = NS + "WorkItem";
    private static final String TASK_IRI = NS + "Task";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String PERSON_IRI = NS + "Person";
    private static final String ABSTRACT_THING_IRI = NS + "AbstractThing";
    private static final String TITLE_IRI = NS + "title";
    private static final String HOURS_IRI = NS + "estimatedHours";
    private static final String ASSIGNED_TO_IRI = NS + "assignedTo";
    private static final String STATUS_IRI = NS + "status";
    private static final String TAGS_IRI = NS + "tags";
    private static final String BELONGS_TO_PROJECT_IRI = NS + "belongsToProject";
    private static final String OPEN_TASK_COUNT_IRI = NS + "openTaskCount";
    private static final String TOTAL_ESTIMATED_HOURS_IRI = NS + "totalEstimatedHours";

    private static final AttributeDefinition TITLE =
        new AttributeDefinition(
            TITLE_IRI, "title", Cardinality.range(1, 1), false, false, false, false, false,
            DisplayHints.none(), Optional.empty(), Datatype.STRING);
    private static final AttributeDefinition HOURS =
        new AttributeDefinition(
            HOURS_IRI, "estimatedHours", Cardinality.atMost(1), false, false, false, false, false,
            DisplayHints.none(), Optional.empty(), Datatype.DECIMAL);
    private static final RelationshipDefinition ASSIGNED_TO =
        new RelationshipDefinition(
            ASSIGNED_TO_IRI, "assignedTo", Cardinality.atMost(1), false, false, false, false, false,
            DisplayHints.none(), Optional.empty(), PERSON_IRI, Optional.empty(), false);
    /** Facet-enabled, indexed, scalar property — usable as a filter, sort, or facet key. */
    private static final AttributeDefinition STATUS =
        new AttributeDefinition(
            STATUS_IRI, "status", Cardinality.atMost(1), true, true, false, false, false,
            DisplayHints.none(), Optional.empty(), Datatype.STRING);
    /** Genuinely multi-valued property — must be rejected by filter/sort/facet resolution. */
    private static final AttributeDefinition TAGS =
        new AttributeDefinition(
            TAGS_IRI, "tags", Cardinality.optional(), false, false, false, false, false,
            DisplayHints.none(), Optional.empty(), Datatype.STRING);
    /**
     * {@code sq:Rollup}-derived: {@code count} of {@code Task} via {@code belongsToProject},
     * filtered to {@code status != "open"} — mirrors {@code QueryFixtures.openTaskCount()}'s exact
     * {@link RollupRule} shape.
     */
    private static final AttributeDefinition OPEN_TASK_COUNT =
        new AttributeDefinition(
            OPEN_TASK_COUNT_IRI, "openTaskCount", Cardinality.atMost(1), false, false, false, true,
            false, DisplayHints.none(),
            Optional.of(
                new RollupRule(
                    TASK_IRI, BELONGS_TO_PROJECT_IRI, AggregateFunction.COUNT, Optional.empty(),
                    List.of(
                        new Criterion(
                            STATUS_IRI, Operator.NE, Optional.of(Value.text("open")))))),
            Datatype.INTEGER);
    /**
     * {@code sq:Rollup}-derived: {@code sum} of {@code estimatedHours} over {@code Task} via
     * {@code belongsToProject}, no filter — mirrors {@code
     * QueryFixtures.totalEstimatedHours()}'s exact {@link RollupRule} shape.
     */
    private static final AttributeDefinition TOTAL_ESTIMATED_HOURS =
        new AttributeDefinition(
            TOTAL_ESTIMATED_HOURS_IRI, "totalEstimatedHours", Cardinality.atMost(1), false, false,
            false, true, false, DisplayHints.none(),
            Optional.of(
                new RollupRule(
                    TASK_IRI, BELONGS_TO_PROJECT_IRI, AggregateFunction.SUM,
                    Optional.of(HOURS_IRI), List.of())),
            Datatype.DECIMAL);

    private static final TypeDefinition WORK_ITEM =
        new TypeDefinition(
            WORK_ITEM_IRI, "WorkItem", List.of(), List.of(), DisplayHints.none(), true,
            Optional.empty());
    private static final TypeDefinition TASK =
        new TypeDefinition(
            TASK_IRI, "Task", List.of(WORK_ITEM_IRI),
            List.of(TITLE, HOURS, ASSIGNED_TO, STATUS, TAGS), DisplayHints.none(), false,
            Optional.empty());
    private static final TypeDefinition PROJECT =
        new TypeDefinition(
            PROJECT_IRI, "Project", List.of(WORK_ITEM_IRI),
            List.of(OPEN_TASK_COUNT, TOTAL_ESTIMATED_HOURS), DisplayHints.none(), false,
            Optional.empty());
    private static final TypeDefinition PERSON =
        new TypeDefinition(
            PERSON_IRI, "Person", List.of(), List.of(), DisplayHints.none(), false,
            Optional.empty());
    private static final TypeDefinition ABSTRACT_THING =
        new TypeDefinition(
            ABSTRACT_THING_IRI, "AbstractThing", List.of(), List.of(), DisplayHints.none(), true,
            Optional.empty());

    private static final MetaModelSnapshot SNAPSHOT =
        new MetaModelSnapshot(
            NS.substring(0, NS.length() - 1),
            Optional.empty(),
            Map.of(),
            List.of(WORK_ITEM, TASK, PROJECT, PERSON, ABSTRACT_THING),
            new OntologyReport(true, List.of()));

    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Scope ALICE =
        new Scope(new TenantId("acme"), new Principal("alice", "Alice", Set.of("member")));
    private static final Scope BOB =
        new Scope(new TenantId("acme"), new Principal("bob", "Bob", Set.of("member")));

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static AuthorizationPort denyAll() {
        return (scope, operation, resource) -> AccessDecision.deny("nope");
    }

    private static DefaultBusinessObjectService service(
        FakeObjectStorePort store, ValidationPort validationPort, AuthorizationPort authorizationPort) {
        return service(store, validationPort, authorizationPort, new FakeQueryPort());
    }

    private static DefaultBusinessObjectService service(
        FakeObjectStorePort store,
        ValidationPort validationPort,
        AuthorizationPort authorizationPort,
        FakeQueryPort queryPort) {
        return new DefaultBusinessObjectService(
            new FakeOntologyPort(), store, validationPort, authorizationPort, queryPort, CLOCK);
    }

    private static ValidationPort noViolations() {
        return (scope, snapshot, object) -> List.of();
    }

    private static BrowseQuery browseQuery(Page page) {
        return new BrowseQuery(List.of(), Optional.empty(), List.of(), page, List.of());
    }

    // --- browse ---

    @Test
    void browseAuthorizesOnceAgainstResolvedTypeIriAndQueriesConcreteSubtypes() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        List<Operation> capturedOps = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort =
            (scope, operation, resource) -> {
                capturedOps.add(operation);
                capturedResources.add(resource);
                return AccessDecision.permit("ok");
            };
        DefaultBusinessObjectService service =
            service(store, noViolations(), authorizationPort, queryPort);

        QueryResult result = service.browse(ALICE, "WorkItem", browseQuery(new Page(0, 20)));

        assertThat(capturedOps).containsExactly(Operation.BROWSE);
        assertThat(capturedResources).containsExactly(WORK_ITEM_IRI);
        assertThat(queryPort.queries).hasSize(1);
        Query captured = queryPort.queries.get(0);
        assertThat(captured.types()).containsExactlyInAnyOrder(TASK_IRI, PROJECT_IRI);
        assertThat(captured.criteria()).isEmpty();
        assertThat(captured.sorts()).isEmpty();
        assertThat(captured.facetProperties()).isEmpty();
        assertThat(captured.includeDeleted()).isFalse();
        // No longer isSameAs: browse now always passes the query result through the
        // DerivationPlanner, which returns a fresh QueryResult (equal in content, since there are
        // no derived properties to compute on an empty items list here) rather than the queryPort's
        // own instance.
        assertThat(result).isEqualTo(queryPort.response);
    }

    @Test
    void browseThrowsTypeNotFoundForUnknownTypeBeforeAnyPortCall() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);

        assertThatExceptionOfType(TypeNotFoundException.class)
            .isThrownBy(
                () -> service.browse(ALICE, "NoSuchType", browseQuery(new Page(0, 20))));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseThrowsAuthorizationExceptionOnDenyAndNeverCallsQuery() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), denyAll(), queryPort);

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.browse(ALICE, "WorkItem", browseQuery(new Page(0, 20))));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithUnknownFilterPropertyThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        BrowseQuery query =
            new BrowseQuery(
                List.of(new BrowseQuery.Filter("noSuchProperty", "eq", Optional.of("x"))),
                Optional.empty(), List.of(), new Page(0, 20), List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
            .isThrownBy(() -> service.browse(ALICE, "Task", query))
            .satisfies(
                exception -> assertThat(exception.violations())
                    .anySatisfy(
                        violation -> assertThat(violation.path()).isEqualTo("noSuchProperty")));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithOperatorNotApplicableToDatatypeThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        // STATUS is Datatype.STRING; operatorApplicable only allows GT for INTEGER, LONG, DECIMAL,
        // DOUBLE, DATE, and DATE_TIME attributes, so GT against a string property is rejected.
        BrowseQuery query =
            new BrowseQuery(
                List.of(new BrowseQuery.Filter("status", "gt", Optional.of("open"))),
                Optional.empty(), List.of(), new Page(0, 20), List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
            .isThrownBy(() -> service.browse(ALICE, "Task", query))
            .satisfies(
                exception -> assertThat(exception.violations())
                    .anySatisfy(
                        violation -> {
                            assertThat(violation.path()).isEqualTo(STATUS_IRI);
                            assertThat(violation.message()).contains("gt").contains("status");
                        }));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithNonFacetPropertyRequestedAsFacetThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        BrowseQuery query =
            new BrowseQuery(
                List.of(), Optional.empty(), List.of(), new Page(0, 20), List.of("title"));

        assertThatExceptionOfType(InvalidQueryException.class)
            .isThrownBy(() -> service.browse(ALICE, "Task", query))
            .satisfies(
                exception -> assertThat(exception.violations())
                    .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(TITLE_IRI)));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithNonSortablePropertyThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        BrowseQuery query =
            new BrowseQuery(
                List.of(), Optional.empty(), List.of(new BrowseQuery.SortKey("tags", "asc")),
                new Page(0, 20), List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
            .isThrownBy(() -> service.browse(ALICE, "Task", query))
            .satisfies(
                exception -> assertThat(exception.violations())
                    .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(TAGS_IRI)));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithUnknownOperatorTokenThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        BrowseQuery query =
            new BrowseQuery(
                List.of(new BrowseQuery.Filter("title", "bogus", Optional.of("x"))),
                Optional.empty(), List.of(), new Page(0, 20), List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
            .isThrownBy(() -> service.browse(ALICE, "Task", query))
            .satisfies(
                exception -> assertThat(exception.violations())
                    .anySatisfy(
                        violation -> {
                            assertThat(violation.path()).isEqualTo("title");
                            assertThat(violation.message()).contains("bogus");
                        }));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithUnknownSortDirectionThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        BrowseQuery query =
            new BrowseQuery(
                List.of(), Optional.empty(), List.of(new BrowseQuery.SortKey("title", "bogus")),
                new Page(0, 20), List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
            .isThrownBy(() -> service.browse(ALICE, "Task", query))
            .satisfies(
                exception -> assertThat(exception.violations())
                    .anySatisfy(
                        violation -> {
                            assertThat(violation.path()).isEqualTo("title");
                            assertThat(violation.message()).contains("bogus");
                        }));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseOfConcreteTypeResolvesToItsOwnIriOnly() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);

        service.browse(ALICE, "Task", browseQuery(new Page(0, 20)));

        assertThat(queryPort.queries).hasSize(1);
        assertThat(queryPort.queries.get(0).types()).containsExactly(TASK_IRI);
    }

    @Test
    void browseBuildsCriteriaSortsAndFacetsOnQuery() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        BrowseQuery query =
            new BrowseQuery(
                List.of(new BrowseQuery.Filter("title", "eq", Optional.of("Write plan"))),
                Optional.empty(),
                List.of(new BrowseQuery.SortKey("estimatedHours", "asc")),
                new Page(0, 20),
                List.of("status"));

        service.browse(ALICE, "Task", query);

        assertThat(queryPort.queries).hasSize(1);
        Query captured = queryPort.queries.get(0);
        assertThat(captured.types()).containsExactly(TASK_IRI);
        assertThat(captured.criteria())
            .containsExactly(new Criterion(TITLE_IRI, Operator.EQ, Optional.of(Value.text("Write plan"))));
        assertThat(captured.sorts()).containsExactly(new Sort(HOURS_IRI, Direction.ASC));
        assertThat(captured.facetProperties()).containsExactly(STATUS_IRI);
    }

    @Test
    void browseWithInFilterProducesListValueCriterion() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        BrowseQuery query =
            new BrowseQuery(
                List.of(new BrowseQuery.Filter("status", "in", Optional.of("a,b,c"))),
                Optional.empty(), List.of(), new Page(0, 20), List.of());

        service.browse(ALICE, "Task", query);

        assertThat(queryPort.queries).hasSize(1);
        Criterion criterion = queryPort.queries.get(0).criteria().get(0);
        assertThat(criterion.property()).isEqualTo(STATUS_IRI);
        assertThat(criterion.operator()).isEqualTo(Operator.IN);
        assertThat(criterion.value()).isPresent();
        assertThat(criterion.value().get()).isInstanceOf(ListValue.class);
        ListValue listValue = (ListValue) criterion.value().get();
        assertThat(listValue.values())
            .containsExactly(Value.text("a"), Value.text("b"), Value.text("c"));
    }

    @Test
    void browseWithIsNullFilterProducesEmptyValueCriterion() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);
        BrowseQuery query =
            new BrowseQuery(
                List.of(new BrowseQuery.Filter("title", "isnull", Optional.empty())),
                Optional.empty(), List.of(), new Page(0, 20), List.of());

        service.browse(ALICE, "Task", query);

        assertThat(queryPort.queries).hasSize(1);
        Criterion criterion = queryPort.queries.get(0).criteria().get(0);
        assertThat(criterion.property()).isEqualTo(TITLE_IRI);
        assertThat(criterion.operator()).isEqualTo(Operator.IS_NULL);
        assertThat(criterion.value()).isEmpty();
    }

    @Test
    void browseComputesRollupsWithExactlyTwoAggregateCallsForFiftyProjects() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        List<BusinessObject> projects = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            projects.add(
                new BusinessObject(
                    ObjectId.random(), new TypeRef(PROJECT_IRI), ALICE.tenantId(), 1,
                    Optional.empty(), Map.of(), new Audit(NOW, "alice", NOW, "alice"), false));
        }
        queryPort.response = new QueryResult(projects, 50, Map.of());
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);

        QueryResult result = service.browse(ALICE, "Project", browseQuery(new Page(0, 50)));

        // Exactly one COUNT request and one SUM request — never one per object (50) and never
        // one per property-per-object (100).
        assertThat(queryPort.aggregateRequests).hasSize(2);
        AggregateRequest countRequest =
            queryPort.aggregateRequests.stream()
                .filter(request -> request.function() == AggregateFunction.COUNT)
                .findFirst()
                .orElseThrow();
        AggregateRequest sumRequest =
            queryPort.aggregateRequests.stream()
                .filter(request -> request.function() == AggregateFunction.SUM)
                .findFirst()
                .orElseThrow();
        assertThat(countRequest.sourceTypes()).containsExactly(TASK_IRI);
        assertThat(countRequest.viaIri()).isEqualTo(BELONGS_TO_PROJECT_IRI);
        assertThat(countRequest.targetIds()).hasSize(50);
        assertThat(sumRequest.sourceTypes()).containsExactly(TASK_IRI);
        assertThat(sumRequest.viaIri()).isEqualTo(BELONGS_TO_PROJECT_IRI);
        assertThat(sumRequest.ofPropertyIri()).contains(HOURS_IRI);
        assertThat(sumRequest.targetIds()).hasSize(50);

        assertThat(result.items()).hasSize(50);
        assertThat(result.items())
            .allSatisfy(
                object ->
                    assertThat(object.properties()).containsKey(new PropertyRef(OPEN_TASK_COUNT_IRI)));
    }

    // --- read ---

    @Test
    void readReturnsObjectOnExactTypeMatch() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject task = seedTask(store, "alice", NOW, 1);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        BusinessObject result = service.read(ALICE, "Task", task.id());

        assertThat(result).isEqualTo(task);
    }

    @Test
    void readReturnsObjectWhenStoredTypeIsSubtypeOfRequestedType() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject task = seedTask(store, "alice", NOW, 1);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        BusinessObject result = service.read(ALICE, "WorkItem", task.id());

        assertThat(result).isEqualTo(task);
    }

    @Test
    void readThrowsObjectNotFoundForUnrelatedStoredType() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        BusinessObject person =
            new BusinessObject(
                ObjectId.random(), new TypeRef(PERSON_IRI), ALICE.tenantId(), 1, Optional.empty(),
                Map.of(), new Audit(NOW, "alice", NOW, "alice"), false);
        store.seed(person);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);

        assertThatExceptionOfType(ObjectNotFoundException.class)
            .isThrownBy(() -> service.read(ALICE, "Task", person.id()));
        // The type-mismatch 404 must short-circuit before derivation ever runs an aggregate.
        assertThat(queryPort.aggregateRequests).isEmpty();
    }

    @Test
    void readComputesRollupsWithExactlyTwoAggregateCallsSameAsBrowse() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        BusinessObject project =
            new BusinessObject(
                ObjectId.random(), new TypeRef(PROJECT_IRI), ALICE.tenantId(), 1, Optional.empty(),
                Map.of(), new Audit(NOW, "alice", NOW, "alice"), false);
        store.seed(project);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll(), queryPort);

        BusinessObject result = service.read(ALICE, "Project", project.id());

        assertThat(queryPort.aggregateRequests).hasSize(2);
        assertThat(result.properties()).containsKey(new PropertyRef(OPEN_TASK_COUNT_IRI));
    }

    @Test
    void readThrowsObjectNotFoundForMissingId() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
            .isThrownBy(() -> service.read(ALICE, "Task", ObjectId.random()));
    }

    @Test
    void readThrowsAuthorizationExceptionOnDenyBeforeFind() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject task = seedTask(store, "alice", NOW, 1);
        DefaultBusinessObjectService service = service(store, noViolations(), denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.read(ALICE, "Task", task.id()));
        assertThat(store.findCalls).isZero();
    }

    // --- add ---

    @Test
    void addHappyPathCommitsCreateAndOutboxEntry() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        BusinessObject result = service.add(ALICE, "Task", Map.of("title", "Write plan"));

        assertThat(store.commits).hasSize(1);
        ChangeSet changeSet = store.commits.get(0);
        assertThat(changeSet.mutations()).hasSize(1);
        Create create = (Create) changeSet.mutations().get(0);
        assertThat(create.object().version()).isEqualTo(1);
        assertThat(create.object().state()).isEmpty();
        assertThat(create.object().audit().createdAt()).isEqualTo(NOW);
        assertThat(create.object().audit().updatedAt()).isEqualTo(NOW);
        assertThat(changeSet.outbox()).hasSize(1);
        OutboxEntry outbox = changeSet.outbox().get(0);
        assertThat(outbox.kind()).isEqualTo(OutboxEntry.KIND_OBJECT_CREATED);
        assertThat(outbox.payload().get("version")).isEqualTo(1L);
        assertThat(result.version()).isEqualTo(1);
    }

    @Test
    void addWithBadPropertyThrowsStructuralValidationExceptionWithoutCommitOrValidate() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> service.add(ALICE, "Task", Map.of("title", 42)))
            .satisfies(
                exception -> assertThat(exception.source())
                    .isEqualTo(ValidationException.Source.STRUCTURAL));
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void addWithDerivedPropertyThrowsValidationExceptionNamingItAsDerived() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, validationPort, permitAll(), queryPort);

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> service.add(ALICE, "Project", Map.of("openTaskCount", 3)))
            .satisfies(
                exception -> {
                    assertThat(exception.source())
                        .isEqualTo(ValidationException.Source.STRUCTURAL);
                    assertThat(exception.violations())
                        .anySatisfy(
                            violation -> {
                                assertThat(violation.path()).isEqualTo(OPEN_TASK_COUNT_IRI);
                                assertThat(violation.message())
                                    .contains("is a derived property and cannot be set directly");
                            });
                });
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
        assertThat(queryPort.aggregateRequests).isEmpty();
    }

    @Test
    void addWithMissingRequiredTitleThrowsStructuralValidationExceptionWithoutValidate() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> service.add(ALICE, "Task", Map.of()))
            .satisfies(
                exception -> assertThat(exception.source())
                    .isEqualTo(ValidationException.Source.STRUCTURAL));
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void addWithShaclViolationThrowsShaclValidationExceptionWithoutCommit() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        Violation violation = new Violation(TITLE_IRI, "shacl says no");
        FakeValidationPort validationPort = new FakeValidationPort(List.of(violation));
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> service.add(ALICE, "Task", Map.of("title", "Write plan")))
            .satisfies(
                exception -> assertThat(exception.source())
                    .isEqualTo(ValidationException.Source.SHACL));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void addingAbstractTypeThrowsStructuralValidationExceptionWithAbstractViolation() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> service.add(ALICE, "AbstractThing", Map.of()))
            .satisfies(
                exception -> {
                    assertThat(exception.source()).isEqualTo(ValidationException.Source.STRUCTURAL);
                    assertThat(exception.violations()).hasSize(1);
                });
    }

    @Test
    void addThrowsAuthorizationExceptionOnDenyBeforeCommit() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultBusinessObjectService service = service(store, noViolations(), denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.add(ALICE, "Task", Map.of("title", "Write plan")));
        assertThat(store.commitCalls).isZero();
    }

    // --- edit ---

    @Test
    void editHappyPathPreservesCreatedAuditAndCommitsUpdate() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        Instant earlier = NOW.minusSeconds(3600);
        BusinessObject existing = seedTask(store, "alice", earlier, 1);
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        BusinessObject result =
            service.edit(BOB, "Task", existing.id(), 1, Map.of("title", "Updated"));

        assertThat(store.commits).hasSize(1);
        ChangeSet changeSet = store.commits.get(0);
        Update update = (Update) changeSet.mutations().get(0);
        assertThat(update.expectedVersion()).isEqualTo(1);
        assertThat(update.object().audit().createdAt()).isEqualTo(earlier);
        assertThat(update.object().audit().createdBy()).isEqualTo("alice");
        assertThat(update.object().audit().updatedAt()).isEqualTo(NOW);
        assertThat(update.object().audit().updatedBy()).isEqualTo("bob");
        OutboxEntry outbox = changeSet.outbox().get(0);
        assertThat(outbox.kind()).isEqualTo(OutboxEntry.KIND_OBJECT_UPDATED);
        assertThat(outbox.payload().get("version")).isEqualTo(2L);
        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    void editNonexistentIdThrowsObjectNotFoundWithoutCommit() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
            .isThrownBy(
                () -> service.edit(BOB, "Task", ObjectId.random(), 1, Map.of("title", "x")));
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void editWithMismatchedStoredTypeThrowsObjectNotFound() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject person =
            new BusinessObject(
                ObjectId.random(), new TypeRef(PERSON_IRI), ALICE.tenantId(), 1, Optional.empty(),
                Map.of(), new Audit(NOW, "alice", NOW, "alice"), false);
        store.seed(person);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
            .isThrownBy(
                () -> service.edit(BOB, "Task", person.id(), 1, Map.of("title", "x")));
    }

    @Test
    void editWithMissingRequiredTitleThrowsStructuralValidationExceptionWithoutCommit() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 1);
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(() -> service.edit(BOB, "Task", existing.id(), 1, Map.of()))
            .satisfies(
                exception -> assertThat(exception.source())
                    .isEqualTo(ValidationException.Source.STRUCTURAL));
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void editWithDerivedPropertyThrowsValidationExceptionNamingItAsDerived() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedProject(store, "alice", NOW, 1);
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultBusinessObjectService service = service(store, validationPort, permitAll(), queryPort);

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(
                () ->
                    service.edit(
                        BOB, "Project", existing.id(), 1, Map.of("totalEstimatedHours", 40)))
            .satisfies(
                exception -> {
                    assertThat(exception.source())
                        .isEqualTo(ValidationException.Source.STRUCTURAL);
                    assertThat(exception.violations())
                        .anySatisfy(
                            violation -> {
                                assertThat(violation.path()).isEqualTo(TOTAL_ESTIMATED_HOURS_IRI);
                                assertThat(violation.message())
                                    .contains("is a derived property and cannot be set directly");
                            });
                });
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
        assertThat(queryPort.aggregateRequests).isEmpty();
    }

    @Test
    void editWithShaclViolationThrowsShaclValidationExceptionWithoutCommit() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 1);
        FakeValidationPort validationPort =
            new FakeValidationPort(List.of(new Violation(TITLE_IRI, "shacl says no")));
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
            .isThrownBy(
                () -> service.edit(BOB, "Task", existing.id(), 1, Map.of("title", "Updated")))
            .satisfies(
                exception -> assertThat(exception.source())
                    .isEqualTo(ValidationException.Source.SHACL));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void editThrowsAuthorizationExceptionOnDenyBeforeFind() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 1);
        DefaultBusinessObjectService service = service(store, noViolations(), denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(
                () -> service.edit(BOB, "Task", existing.id(), 1, Map.of("title", "Updated")));
        assertThat(store.findCalls).isZero();
    }

    @Test
    void editPropagatesStaleObjectException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 3);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(StaleObjectException.class)
            .isThrownBy(
                () -> service.edit(BOB, "Task", existing.id(), 1, Map.of("title", "Updated")));
    }

    // --- delete ---

    @Test
    void deleteWithExpectedVersionCommitsDeleteAndOutboxEntry() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 3);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        service.delete(ALICE, "Task", existing.id(), OptionalLong.of(3));

        assertThat(store.commits).hasSize(1);
        ChangeSet changeSet = store.commits.get(0);
        Delete delete = (Delete) changeSet.mutations().get(0);
        assertThat(delete.expectedVersion()).isEqualTo(3);
        OutboxEntry outbox = changeSet.outbox().get(0);
        assertThat(outbox.kind()).isEqualTo(OutboxEntry.KIND_OBJECT_DELETED);
        assertThat(outbox.payload().get("version")).isEqualTo(4L);
    }

    @Test
    void deleteWithoutExpectedVersionUsesCurrentlyStoredVersion() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 3);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        service.delete(ALICE, "Task", existing.id(), OptionalLong.empty());

        ChangeSet changeSet = store.commits.get(0);
        Delete delete = (Delete) changeSet.mutations().get(0);
        assertThat(delete.expectedVersion()).isEqualTo(3);
    }

    @Test
    void deleteOfNonexistentIdThrowsObjectNotFoundBeforeCommit() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
            .isThrownBy(
                () -> service.delete(ALICE, "Task", ObjectId.random(), OptionalLong.empty()));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void deleteWithMismatchedStoredTypeThrowsObjectNotFound() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject person =
            new BusinessObject(
                ObjectId.random(), new TypeRef(PERSON_IRI), ALICE.tenantId(), 1, Optional.empty(),
                Map.of(), new Audit(NOW, "alice", NOW, "alice"), false);
        store.seed(person);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
            .isThrownBy(
                () -> service.delete(ALICE, "Task", person.id(), OptionalLong.empty()));
    }

    @Test
    void deleteThrowsAuthorizationExceptionOnDenyBeforeFind() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 3);
        DefaultBusinessObjectService service = service(store, noViolations(), denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(
                () -> service.delete(ALICE, "Task", existing.id(), OptionalLong.empty()));
        assertThat(store.findCalls).isZero();
    }

    // --- constructor ---

    @Test
    void constructorRejectsNullArguments() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    null, store, noViolations(), permitAll(), queryPort, CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), null, noViolations(), permitAll(), queryPort, CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), store, null, permitAll(), queryPort, CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), store, noViolations(), null, queryPort, CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), store, noViolations(), permitAll(), null, CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), store, noViolations(), permitAll(), queryPort, null));
    }

    @Test
    void sevenArgConstructorRejectsNullDerivationPlanner() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), store, noViolations(), permitAll(), queryPort, CLOCK,
                    null));
    }

    // --- null arguments per method ---

    @Test
    void browseRejectsNullArguments() {
        DefaultBusinessObjectService service =
            service(new FakeObjectStorePort(), noViolations(), permitAll());
        BrowseQuery query = browseQuery(new Page(0, 20));
        assertThatNullPointerException().isThrownBy(() -> service.browse(null, "Task", query));
        assertThatNullPointerException().isThrownBy(() -> service.browse(ALICE, null, query));
        assertThatNullPointerException().isThrownBy(() -> service.browse(ALICE, "Task", null));
    }

    @Test
    void readRejectsNullArguments() {
        DefaultBusinessObjectService service =
            service(new FakeObjectStorePort(), noViolations(), permitAll());
        assertThatNullPointerException()
            .isThrownBy(() -> service.read(null, "Task", ObjectId.random()));
        assertThatNullPointerException()
            .isThrownBy(() -> service.read(ALICE, null, ObjectId.random()));
        assertThatNullPointerException().isThrownBy(() -> service.read(ALICE, "Task", null));
    }

    @Test
    void addRejectsNullArguments() {
        DefaultBusinessObjectService service =
            service(new FakeObjectStorePort(), noViolations(), permitAll());
        assertThatNullPointerException().isThrownBy(() -> service.add(null, "Task", Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.add(ALICE, null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.add(ALICE, "Task", null));
    }

    @Test
    void editRejectsNullArguments() {
        DefaultBusinessObjectService service =
            service(new FakeObjectStorePort(), noViolations(), permitAll());
        ObjectId id = ObjectId.random();
        assertThatNullPointerException()
            .isThrownBy(() -> service.edit(null, "Task", id, 1, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.edit(ALICE, null, id, 1, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.edit(ALICE, "Task", null, 1, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.edit(ALICE, "Task", id, 1, null));
    }

    @Test
    void deleteRejectsNullArguments() {
        DefaultBusinessObjectService service =
            service(new FakeObjectStorePort(), noViolations(), permitAll());
        ObjectId id = ObjectId.random();
        assertThatNullPointerException()
            .isThrownBy(() -> service.delete(null, "Task", id, OptionalLong.empty()));
        assertThatNullPointerException()
            .isThrownBy(() -> service.delete(ALICE, null, id, OptionalLong.empty()));
        assertThatNullPointerException()
            .isThrownBy(() -> service.delete(ALICE, "Task", null, OptionalLong.empty()));
        assertThatNullPointerException()
            .isThrownBy(() -> service.delete(ALICE, "Task", id, null));
    }

    private static BusinessObject seedTask(
        FakeObjectStorePort store, String createdBy, Instant createdAt, long version) {
        BusinessObject task =
            new BusinessObject(
                ObjectId.random(), new TypeRef(TASK_IRI), new TenantId("acme"), version,
                Optional.empty(), Map.of(new PropertyRef(TITLE_IRI), new TextValue("Write plan")),
                new Audit(createdAt, createdBy, createdAt, createdBy), false);
        store.seed(task);
        return task;
    }

    private static BusinessObject seedProject(
        FakeObjectStorePort store, String createdBy, Instant createdAt, long version) {
        BusinessObject project =
            new BusinessObject(
                ObjectId.random(), new TypeRef(PROJECT_IRI), new TenantId("acme"), version,
                Optional.empty(), Map.of(),
                new Audit(createdAt, createdBy, createdAt, createdBy), false);
        store.seed(project);
        return project;
    }

    /**
     * Hand-written {@link QueryPort} double: captures every {@link Query} passed to {@link
     * #query}, returning a configurable (default empty) {@link QueryResult}. {@link #ensureIndexes}
     * is not exercised by these tests (that's {@code DefaultOntologyAdministrationTest}'s job), so
     * it throws, matching how every other fake in this file leaves an unexercised method
     * unimplemented.
     */
    private static final class FakeQueryPort implements QueryPort {

        private final List<Query> queries = new ArrayList<>();
        private final List<AggregateRequest> aggregateRequests = new ArrayList<>();
        private QueryResult response = new QueryResult(List.of(), 0, Map.of());

        @Override
        public QueryResult query(Scope scope, MetaModelSnapshot snapshot, Query query) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            Objects.requireNonNull(query, "query must not be null");
            queries.add(query);
            return response;
        }

        @Override
        public void ensureIndexes(Scope scope, MetaModelSnapshot snapshot) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        /**
         * Real counting implementation, not a stub: {@code DerivationPlanner} bounding tests
         * assert exactly how many times, and with what shape, this is called for a page of
         * objects. Density-aware per {@link QueryPort#aggregate}'s contract: {@link
         * AggregateFunction#COUNT} returns every requested target id mapped to a canned {@code 0},
         * every other function returns an empty map (as if nothing matched) — simplest canned
         * response that still respects the density contract.
         */
        @Override
        public AggregateResult aggregate(Scope scope, MetaModelSnapshot snapshot, AggregateRequest request) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            Objects.requireNonNull(request, "request must not be null");
            aggregateRequests.add(request);
            if (request.function() == AggregateFunction.COUNT) {
                Map<ObjectId, Value> values = new HashMap<>();
                for (ObjectId id : request.targetIds()) {
                    values.put(id, new IntegerValue(0));
                }
                return new AggregateResult(values);
            }
            return new AggregateResult(Map.of());
        }
    }

    /** Hand-written {@link OntologyPort} double: only {@code snapshot} is exercised. */
    private static final class FakeOntologyPort implements OntologyPort {

        @Override
        public MetaModelSnapshot snapshot(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            return SNAPSHOT;
        }

        @Override
        public MetaModelSnapshot reload(Scope scope) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public OntologyDocument export(Scope scope, OntologyFormat format) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }
    }

    /**
     * Hand-written {@link ValidationPort} double returning a fixed, configured list of violations
     * and counting invocations, so tests can assert it was never reached when a structural failure
     * should have short-circuited before it.
     */
    private static final class FakeValidationPort implements ValidationPort {

        private final List<Violation> violations;
        private int calls;

        FakeValidationPort(List<Violation> violations) {
            this.violations = violations;
        }

        @Override
        public List<Violation> validate(Scope scope, MetaModelSnapshot snapshot, BusinessObject object) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            Objects.requireNonNull(object, "object must not be null");
            calls++;
            return violations;
        }
    }

    /**
     * Hand-written {@link ObjectStorePort} double backed by an in-memory map, with a captured list
     * of committed {@link ChangeSet}s so tests can assert exactly what was committed. Deliberately
     * not {@code sequeless-spi-testkit}'s {@code InMemoryObjectStorePort}: that lives in that
     * module's test sources and is not on {@code sequeless-core}'s test classpath, and core must
     * not gain a test dependency on testkit just to reuse it.
     */
    private static final class FakeObjectStorePort implements ObjectStorePort {

        private final Map<ObjectId, BusinessObject> store = new HashMap<>();
        private final List<ChangeSet> commits = new ArrayList<>();
        private int findCalls;
        private int browseCalls;
        private int typesOfCalls;
        private int commitCalls;
        private Set<TypeRef> lastBrowseTypes;

        void seed(BusinessObject object) {
            store.put(object.id(), object);
        }

        @Override
        public Optional<BusinessObject> find(Scope scope, ObjectId id) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(id, "id must not be null");
            findCalls++;
            BusinessObject found = store.get(id);
            if (found == null || found.deleted()) {
                return Optional.empty();
            }
            return Optional.of(found);
        }

        @Override
        public PageResult<BusinessObject> browse(Scope scope, Set<TypeRef> types, Page page) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(types, "types must not be null");
            Objects.requireNonNull(page, "page must not be null");
            browseCalls++;
            lastBrowseTypes = types;
            List<BusinessObject> items =
                store.values().stream()
                    .filter(object -> !object.deleted() && types.contains(object.type()))
                    .sorted(
                        Comparator.comparing((BusinessObject object) -> object.audit().createdAt())
                            .thenComparing(object -> object.id().value()))
                    .toList();
            return new PageResult<>(items, page.number(), page.size(), items.size());
        }

        @Override
        public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(ids, "ids must not be null");
            typesOfCalls++;
            Map<ObjectId, TypeRef> result = new HashMap<>();
            for (ObjectId id : ids) {
                BusinessObject found = store.get(id);
                if (found != null && !found.deleted()) {
                    result.put(id, found.type());
                }
            }
            return result;
        }

        @Override
        public CommitResult commit(Scope scope, ChangeSet changeSet) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(changeSet, "changeSet must not be null");
            commitCalls++;
            commits.add(changeSet);

            List<BusinessObject> resultObjects = new ArrayList<>();
            for (Mutation mutation : changeSet.mutations()) {
                if (mutation instanceof Create create) {
                    store.put(create.object().id(), create.object());
                    resultObjects.add(create.object());
                } else if (mutation instanceof Update update) {
                    BusinessObject current = store.get(update.object().id());
                    if (current == null || current.deleted()) {
                        throw new ObjectNotFoundException(update.object().id());
                    }
                    if (current.version() != update.expectedVersion()) {
                        throw new StaleObjectException(update.object().id(), update.expectedVersion());
                    }
                    store.put(update.object().id(), update.object());
                    resultObjects.add(update.object());
                } else if (mutation instanceof Delete delete) {
                    BusinessObject current = store.get(delete.id());
                    if (current == null || current.deleted()) {
                        throw new ObjectNotFoundException(delete.id());
                    }
                    if (current.version() != delete.expectedVersion()) {
                        throw new StaleObjectException(delete.id(), delete.expectedVersion());
                    }
                    BusinessObject deleted =
                        new BusinessObject(
                            current.id(), current.type(), current.tenant(),
                            delete.expectedVersion() + 1, current.state(), current.properties(),
                            new Audit(
                                current.audit().createdAt(), current.audit().createdBy(), delete.at(),
                                delete.by()),
                            true);
                    store.put(delete.id(), deleted);
                    resultObjects.add(deleted);
                }
            }

            List<UUID> outboxIds = new ArrayList<>();
            for (OutboxEntry ignored : changeSet.outbox()) {
                outboxIds.add(UUID.randomUUID());
            }
            return new CommitResult(resultObjects, outboxIds);
        }

        @Override
        public OntologyDocumentStore ontologyDocuments() {
            throw new UnsupportedOperationException("not exercised by these tests");
        }
    }
}
