package org.sequeless.app.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.node.TextNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.ontology.spi.OntologyDocumentStore;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
import org.sequeless.spi.object.Mutation;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.spi.query.BrowsePort;
import org.sequeless.spi.query.Direction;
import org.sequeless.spi.query.ObjectView;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.query.ReadPort;
import org.sequeless.spi.query.Sort;
import org.sequeless.store.api.AccessDecision;
import org.sequeless.store.api.Operation;
import org.sequeless.store.core.AuthorizationException;
import org.sequeless.store.core.api.BrowseQuery;
import org.sequeless.store.core.api.InvalidQueryException;
import org.sequeless.store.core.api.TypeNotFoundException;
import org.sequeless.store.core.derivation.DerivationPlanner;
import org.sequeless.store.core.derivation.DerivationPluginRegistry;
import org.sequeless.store.spi.AggregateRequest;
import org.sequeless.store.spi.AggregateResult;
import org.sequeless.store.spi.AuthorizationPort;
import org.sequeless.store.spi.Criterion;
import org.sequeless.store.spi.ObjectStorePort;
import org.sequeless.store.spi.Operator;
import org.sequeless.store.spi.QueryPort;

/**
 * Unit tests for {@link DefaultObjectQueryService}: the browse and read use cases, which moved out of core in spec 18
 * U6. The fixture is the one the core command tests use (abstract {@code WorkItem} over concrete {@code Task} and
 * {@code Project}, plus unrelated {@code Person} and abstract {@code AbstractThing}), so both halves describe one
 * schema. Each port double is a plain lambda or a hand-written fake. Core keeps its own copy of the fixture, because
 * the two test source sets cannot share code.
 */
class DefaultObjectQueryServiceTest {

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

    private static final AttributeDefinition TITLE = new AttributeDefinition(
            TITLE_IRI,
            "title",
            Cardinality.range(1, 1),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            Datatype.STRING);
    private static final AttributeDefinition HOURS = new AttributeDefinition(
            HOURS_IRI,
            "estimatedHours",
            Cardinality.atMost(1),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            Datatype.DECIMAL);
    private static final RelationshipDefinition ASSIGNED_TO = new RelationshipDefinition(
            ASSIGNED_TO_IRI,
            "assignedTo",
            Cardinality.atMost(1),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            PERSON_IRI,
            Optional.empty(),
            false);
    /** Facet-enabled, indexed, scalar property — usable as a filter, sort, or facet key. */
    private static final AttributeDefinition STATUS = new AttributeDefinition(
            STATUS_IRI,
            "status",
            Cardinality.atMost(1),
            true,
            true,
            false,
            false,
            false,
            DisplayHints.none(),
            Datatype.STRING);
    /** Genuinely multi-valued property — must be rejected by filter/sort/facet resolution. */
    private static final AttributeDefinition TAGS = new AttributeDefinition(
            TAGS_IRI,
            "tags",
            Cardinality.optional(),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            Datatype.STRING);
    /**
     * {@code sq:Rollup}-derived: {@code count} of {@code Task} via {@code belongsToProject},
     * filtered to {@code status != "open"}. The rule lives in {@link #BEHAVIOUR}, not on the
     * attribute. It has the shape of the rule in {@code QueryFixtures.behaviour()} (different filter
     * value; materialised).
     */
    private static final AttributeDefinition OPEN_TASK_COUNT = new AttributeDefinition(
            OPEN_TASK_COUNT_IRI,
            "openTaskCount",
            Cardinality.atMost(1),
            false,
            false,
            false,
            true,
            false,
            DisplayHints.none(),
            Datatype.INTEGER);
    /**
     * {@code sq:Rollup}-derived: {@code sum} of {@code estimatedHours} over {@code Task} via
     * {@code belongsToProject}, no filter. The rule lives in {@link #BEHAVIOUR}, not on the
     * attribute; it mirrors the rule {@code QueryFixtures.behaviour()} carries for {@code
     * totalEstimatedHours}.
     */
    private static final AttributeDefinition TOTAL_ESTIMATED_HOURS = new AttributeDefinition(
            TOTAL_ESTIMATED_HOURS_IRI,
            "totalEstimatedHours",
            Cardinality.atMost(1),
            false,
            false,
            false,
            true,
            false,
            DisplayHints.none(),
            Datatype.DECIMAL);

    private static final RollupRule OPEN_TASK_COUNT_RULE = new RollupRule(
            TASK_IRI,
            BELONGS_TO_PROJECT_IRI,
            AggregateFunction.COUNT,
            Optional.empty(),
            Optional.of(new FieldFilter(STATUS_IRI, "is not", TextNode.valueOf("open"))),
            true);
    private static final RollupRule TOTAL_ESTIMATED_HOURS_RULE = new RollupRule(
            TASK_IRI, BELONGS_TO_PROJECT_IRI, AggregateFunction.SUM, Optional.of(HOURS_IRI), Optional.empty(), false);

    /** The behaviour: the two rollups, keyed by the IRI of the property each derives. */
    private static final Behaviour BEHAVIOUR = new Behaviour(
            new OntologyVersion(1),
            Map.of(),
            Map.of(OPEN_TASK_COUNT_IRI, OPEN_TASK_COUNT_RULE, TOTAL_ESTIMATED_HOURS_IRI, TOTAL_ESTIMATED_HOURS_RULE));

    private static final TypeDefinition WORK_ITEM =
            new TypeDefinition(WORK_ITEM_IRI, "WorkItem", List.of(), List.of(), DisplayHints.none(), true);
    private static final TypeDefinition TASK = new TypeDefinition(
            TASK_IRI,
            "Task",
            List.of(WORK_ITEM_IRI),
            List.of(TITLE, HOURS, ASSIGNED_TO, STATUS, TAGS),
            DisplayHints.none(),
            false);
    private static final TypeDefinition PROJECT = new TypeDefinition(
            PROJECT_IRI,
            "Project",
            List.of(WORK_ITEM_IRI),
            List.of(OPEN_TASK_COUNT, TOTAL_ESTIMATED_HOURS),
            DisplayHints.none(),
            false);
    private static final TypeDefinition PERSON =
            new TypeDefinition(PERSON_IRI, "Person", List.of(), List.of(), DisplayHints.none(), false);
    private static final TypeDefinition ABSTRACT_THING =
            new TypeDefinition(ABSTRACT_THING_IRI, "AbstractThing", List.of(), List.of(), DisplayHints.none(), true);

    private static final Schema SCHEMA = new Schema(
            new OntologyVersion(1),
            NS.substring(0, NS.length() - 1),
            Optional.empty(),
            Map.of(),
            List.of(WORK_ITEM, TASK, PROJECT, PERSON, ABSTRACT_THING));

    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Scope ALICE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));
    private static final Scope BOB = new Scope(new TenantId("acme"), new Principal("bob", Set.of("member")));

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static AuthorizationPort denyAll() {
        return (scope, operation, resource) -> AccessDecision.deny("nope");
    }

    private static DefaultObjectQueryService service(
            FakeObjectStorePort store, AuthorizationPort authorizationPort, FakeQueryPort queryPort) {
        return new DefaultObjectQueryService(
                new FakeOntology(),
                new FakeOntology(),
                authorizationPort,
                readOver(store),
                queryPort,
                new DerivationPlanner(queryPort, DerivationPluginRegistry.fromServiceLoader()));
    }

    private static DefaultObjectQueryService service(FakeObjectStorePort store, AuthorizationPort authorizationPort) {
        return service(store, authorizationPort, new FakeQueryPort());
    }

    /**
     * A {@link ReadPort} over {@code store}, so {@link DefaultObjectQueryService#read} sees the
     * same objects the command side commits to it.
     */
    private static ReadPort readOver(FakeObjectStorePort store) {
        return (scope, request) -> store.find(scope, request.id()).map(ObjectViewMapper::toView);
    }

    private static BrowseQuery browseQuery(Page page) {
        return new BrowseQuery(List.of(), Optional.empty(), List.of(), page, List.of());
    }

    @Test
    void constructorRejectsNullArguments() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        ReadPort readPort = readOver(store);
        FakeQueryPort queryPort = new FakeQueryPort();
        DerivationPlanner planner = new DerivationPlanner(queryPort, DerivationPluginRegistry.fromServiceLoader());
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultObjectQueryService(
                        null, new FakeOntology(), permitAll(), readPort, queryPort, planner));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultObjectQueryService(
                        new FakeOntology(), null, permitAll(), readPort, queryPort, planner));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultObjectQueryService(
                        new FakeOntology(), new FakeOntology(), null, readPort, queryPort, planner));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultObjectQueryService(
                        new FakeOntology(), new FakeOntology(), permitAll(), null, queryPort, planner));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultObjectQueryService(
                        new FakeOntology(), new FakeOntology(), permitAll(), readPort, null, planner));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultObjectQueryService(
                        new FakeOntology(), new FakeOntology(), permitAll(), readPort, queryPort, null));
    }

    @Test
    void browseAuthorizesOnceAgainstResolvedTypeIriAndQueriesConcreteSubtypes() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        List<Operation> capturedOps = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort = (scope, operation, resource) -> {
            capturedOps.add(operation);
            capturedResources.add(resource);
            return AccessDecision.permit("ok");
        };
        DefaultObjectQueryService service = service(store, authorizationPort, queryPort);

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
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);

        assertThatExceptionOfType(TypeNotFoundException.class)
                .isThrownBy(() -> service.browse(ALICE, "NoSuchType", browseQuery(new Page(0, 20))));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseThrowsAuthorizationExceptionOnDenyAndNeverCallsQuery() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, denyAll(), queryPort);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.browse(ALICE, "WorkItem", browseQuery(new Page(0, 20))));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithUnknownFilterPropertyThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(new BrowseQuery.Filter("noSuchProperty", "eq", Optional.of("x"))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Task", query))
                .satisfies(exception -> assertThat(exception.violations())
                        .anySatisfy(violation -> assertThat(violation.path()).isEqualTo("noSuchProperty")));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithOperatorNotApplicableToDatatypeThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        // STATUS is Datatype.STRING; operatorApplicable only allows GT for INTEGER, LONG, DECIMAL,
        // DOUBLE, DATE, and DATE_TIME attributes, so GT against a string property is rejected.
        BrowseQuery query = new BrowseQuery(
                List.of(new BrowseQuery.Filter("status", "gt", Optional.of("open"))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Task", query))
                .satisfies(exception -> assertThat(exception.violations()).anySatisfy(violation -> {
                    assertThat(violation.path()).isEqualTo(STATUS_IRI);
                    assertThat(violation.message()).contains("gt").contains("status");
                }));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithNonFacetPropertyRequestedAsFacetThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(List.of(), Optional.empty(), List.of(), new Page(0, 20), List.of("title"));

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Task", query))
                .satisfies(exception -> assertThat(exception.violations())
                        .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(TITLE_IRI)));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithNonSortablePropertyThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(),
                Optional.empty(),
                List.of(new BrowseQuery.SortKey("tags", "asc")),
                new Page(0, 20),
                List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Task", query))
                .satisfies(exception -> assertThat(exception.violations())
                        .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(TAGS_IRI)));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithNonMaterialisedDerivedPropertyFilterThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(new BrowseQuery.Filter("totalEstimatedHours", "eq", Optional.of("40"))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Project", query))
                .satisfies(exception -> assertThat(exception.violations())
                        .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(TOTAL_ESTIMATED_HOURS_IRI)));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithNonMaterialisedDerivedPropertySortThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(),
                Optional.empty(),
                List.of(new BrowseQuery.SortKey("totalEstimatedHours", "asc")),
                new Page(0, 20),
                List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Project", query))
                .satisfies(exception -> assertThat(exception.violations())
                        .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(TOTAL_ESTIMATED_HOURS_IRI)));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithNonMaterialisedDerivedPropertyFacetThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(), Optional.empty(), List.of(), new Page(0, 20), List.of("totalEstimatedHours"));

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Project", query))
                .satisfies(exception -> assertThat(exception.violations())
                        .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(TOTAL_ESTIMATED_HOURS_IRI)));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithMaterialisedDerivedPropertyFilterAndSortReachesQueryPort() {
        // openTaskCount is not itself facet-enabled (see its AttributeDefinition fixture above), so
        // this only exercises filter + sort — buildFacets's own "not a facet property" check is
        // unrelated to the materialised-rejection this test targets.
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(new BrowseQuery.Filter("openTaskCount", "eq", Optional.of("0"))),
                Optional.empty(),
                List.of(new BrowseQuery.SortKey("openTaskCount", "asc")),
                new Page(0, 20),
                List.of());

        service.browse(ALICE, "Project", query);

        assertThat(queryPort.queries).hasSize(1);
    }

    @Test
    void browseWithUnknownOperatorTokenThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(new BrowseQuery.Filter("title", "bogus", Optional.of("x"))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Task", query))
                .satisfies(exception -> assertThat(exception.violations()).anySatisfy(violation -> {
                    assertThat(violation.path()).isEqualTo("title");
                    assertThat(violation.message()).contains("bogus");
                }));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseWithUnknownSortDirectionThrowsInvalidQueryException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(),
                Optional.empty(),
                List.of(new BrowseQuery.SortKey("title", "bogus")),
                new Page(0, 20),
                List.of());

        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> service.browse(ALICE, "Task", query))
                .satisfies(exception -> assertThat(exception.violations()).anySatisfy(violation -> {
                    assertThat(violation.path()).isEqualTo("title");
                    assertThat(violation.message()).contains("bogus");
                }));
        assertThat(queryPort.queries).isEmpty();
    }

    @Test
    void browseOfConcreteTypeResolvesToItsOwnIriOnly() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);

        service.browse(ALICE, "Task", browseQuery(new Page(0, 20)));

        assertThat(queryPort.queries).hasSize(1);
        assertThat(queryPort.queries.get(0).types()).containsExactly(TASK_IRI);
    }

    @Test
    void browseSetsRootIriToTheResolvedTypeAndConcreteTypesToItsSubtypes() {
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(new FakeObjectStorePort(), permitAll(), queryPort);

        service.browse(ALICE, "Task", browseQuery(new Page(0, 20)));
        service.browse(ALICE, "WorkItem", browseQuery(new Page(0, 20)));

        assertThat(queryPort.queries).hasSize(2);
        Query subtype = queryPort.queries.get(0);
        assertThat(subtype.rootIri()).isEqualTo(TASK_IRI);
        assertThat(subtype.types()).containsExactly(TASK_IRI);
        Query abstractRoot = queryPort.queries.get(1);
        assertThat(abstractRoot.rootIri()).isEqualTo(WORK_ITEM_IRI);
        assertThat(abstractRoot.types()).containsExactlyInAnyOrder(TASK_IRI, PROJECT_IRI);
    }

    @Test
    void browseBuildsCriteriaSortsAndFacetsOnQuery() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
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
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(new BrowseQuery.Filter("status", "in", Optional.of("a,b,c"))),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of());

        service.browse(ALICE, "Task", query);

        assertThat(queryPort.queries).hasSize(1);
        Criterion criterion = queryPort.queries.get(0).criteria().get(0);
        assertThat(criterion.property()).isEqualTo(STATUS_IRI);
        assertThat(criterion.operator()).isEqualTo(Operator.IN);
        assertThat(criterion.value()).isPresent();
        assertThat(criterion.value().get()).isInstanceOf(ListValue.class);
        ListValue listValue = (ListValue) criterion.value().get();
        assertThat(listValue.values()).containsExactly(Value.text("a"), Value.text("b"), Value.text("c"));
    }

    @Test
    void browseWithIsNullFilterProducesEmptyValueCriterion() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);
        BrowseQuery query = new BrowseQuery(
                List.of(new BrowseQuery.Filter("title", "isnull", Optional.empty())),
                Optional.empty(),
                List.of(),
                new Page(0, 20),
                List.of());

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
        List<ObjectView> projects = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            projects.add(new ObjectView(
                    ObjectId.random(),
                    new TypeRef(PROJECT_IRI),
                    ALICE.tenantId(),
                    1,
                    Optional.empty(),
                    Map.of(),
                    new Audit(NOW, "alice", NOW, "alice"),
                    false));
        }
        queryPort.response = new QueryResult(projects, 50, Map.of());
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);

        QueryResult result = service.browse(ALICE, "Project", browseQuery(new Page(0, 50)));

        // Exactly one COUNT request and one SUM request — never one per object (50) and never
        // one per property-per-object (100).
        assertThat(queryPort.aggregateRequests).hasSize(2);
        AggregateRequest countRequest = queryPort.aggregateRequests.stream()
                .filter(request -> request.function() == AggregateFunction.COUNT)
                .findFirst()
                .orElseThrow();
        AggregateRequest sumRequest = queryPort.aggregateRequests.stream()
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
                        object -> assertThat(object.properties()).containsKey(new PropertyRef(OPEN_TASK_COUNT_IRI)));
    }

    @Test
    void readReturnsObjectOnExactTypeMatch() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject task = seedTask(store, "alice", NOW, 1);
        DefaultObjectQueryService service = service(store, permitAll());

        ObjectView result = service.read(ALICE, "Task", task.id());

        assertThat(result).isEqualTo(ObjectViewMapper.toView(task));
    }

    @Test
    void readReturnsObjectWhenStoredTypeIsSubtypeOfRequestedType() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject task = seedTask(store, "alice", NOW, 1);
        DefaultObjectQueryService service = service(store, permitAll());

        ObjectView result = service.read(ALICE, "WorkItem", task.id());

        assertThat(result).isEqualTo(ObjectViewMapper.toView(task));
    }

    @Test
    void readThrowsObjectNotFoundForUnrelatedStoredType() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        BusinessObject person = new BusinessObject(
                ObjectId.random(),
                new TypeRef(PERSON_IRI),
                ALICE.tenantId(),
                1,
                Optional.empty(),
                Map.of(),
                new Audit(NOW, "alice", NOW, "alice"),
                false);
        store.seed(person);
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);

        assertThatExceptionOfType(ObjectNotFoundException.class)
                .isThrownBy(() -> service.read(ALICE, "Task", person.id()));
        // The type-mismatch 404 must short-circuit before derivation ever runs an aggregate.
        assertThat(queryPort.aggregateRequests).isEmpty();
    }

    @Test
    void readComputesRollupsWithExactlyTwoAggregateCallsSameAsBrowse() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeQueryPort queryPort = new FakeQueryPort();
        BusinessObject project = new BusinessObject(
                ObjectId.random(),
                new TypeRef(PROJECT_IRI),
                ALICE.tenantId(),
                1,
                Optional.empty(),
                Map.of(),
                new Audit(NOW, "alice", NOW, "alice"),
                false);
        store.seed(project);
        DefaultObjectQueryService service = service(store, permitAll(), queryPort);

        ObjectView result = service.read(ALICE, "Project", project.id());

        assertThat(queryPort.aggregateRequests).hasSize(2);
        assertThat(result.properties()).containsKey(new PropertyRef(OPEN_TASK_COUNT_IRI));
    }

    /**
     * T5 extracted {@code DerivationPlanner.computeRule} out of {@code apply}'s inline {@code
     * switch} over {@link RollupRule}/{@code PluginRule}, so {@code
     * org.sequeless.store.core.derivation.DefaultDerivationRecomputer} could share the exact same
     * aggregate resolution. This test proves that extraction is bit-identical: calling {@code
     * computeRule} directly with the same {@link RollupRule} and target id that {@link
     * #readComputesRollupsWithExactlyTwoAggregateCallsSameAsBrowse} exercises via {@code read}
     * produces the very same {@link AggregateRequest} shape and the very same computed value.
     */
    @Test
    void computeRuleProducesTheSameValueAndRequestShapeAsReadsInlineDispatchDid() {
        FakeQueryPort queryPort = new FakeQueryPort();
        DerivationPlanner planner = new DerivationPlanner(queryPort, DerivationPluginRegistry.fromServiceLoader());
        ObjectId projectId = ObjectId.random();
        BusinessObject project = new BusinessObject(
                projectId,
                new TypeRef(PROJECT_IRI),
                ALICE.tenantId(),
                1,
                Optional.empty(),
                Map.of(),
                new Audit(NOW, "alice", NOW, "alice"),
                false);
        RollupRule rule = (RollupRule) BEHAVIOUR.derivation(OPEN_TASK_COUNT_IRI).orElseThrow();

        Map<ObjectId, Value> values = planner.computeRule(ALICE, SCHEMA, rule, Set.of(projectId), List.of(project));

        assertThat(queryPort.aggregateRequests).hasSize(1);
        AggregateRequest request = queryPort.aggregateRequests.get(0);
        assertThat(request.sourceTypes()).containsExactly(TASK_IRI);
        assertThat(request.viaIri()).isEqualTo(BELONGS_TO_PROJECT_IRI);
        assertThat(request.function()).isEqualTo(AggregateFunction.COUNT);
        assertThat(request.targetIds()).containsExactly(projectId);
        // FakeQueryPort's aggregate() returns a canned 0 for COUNT, per its own javadoc.
        assertThat(values.get(projectId)).isEqualTo(new IntegerValue(0));
    }

    @Test
    void readThrowsObjectNotFoundForMissingId() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultObjectQueryService service = service(store, permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
                .isThrownBy(() -> service.read(ALICE, "Task", ObjectId.random()));
    }

    @Test
    void readThrowsAuthorizationExceptionOnDenyBeforeFind() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject task = seedTask(store, "alice", NOW, 1);
        DefaultObjectQueryService service = service(store, denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.read(ALICE, "Task", task.id()));
        assertThat(store.findCalls).isZero();
    }

    @Test
    void browseRejectsNullArguments() {
        DefaultObjectQueryService service = service(new FakeObjectStorePort(), permitAll());
        BrowseQuery query = browseQuery(new Page(0, 20));
        assertThatNullPointerException().isThrownBy(() -> service.browse(null, "Task", query));
        assertThatNullPointerException().isThrownBy(() -> service.browse(ALICE, null, query));
        assertThatNullPointerException().isThrownBy(() -> service.browse(ALICE, "Task", null));
    }

    @Test
    void readRejectsNullArguments() {
        DefaultObjectQueryService service = service(new FakeObjectStorePort(), permitAll());
        assertThatNullPointerException().isThrownBy(() -> service.read(null, "Task", ObjectId.random()));
        assertThatNullPointerException().isThrownBy(() -> service.read(ALICE, null, ObjectId.random()));
        assertThatNullPointerException().isThrownBy(() -> service.read(ALICE, "Task", null));
    }

    private static BusinessObject seedTask(
            FakeObjectStorePort store, String createdBy, Instant createdAt, long version) {
        BusinessObject task = new BusinessObject(
                ObjectId.random(),
                new TypeRef(TASK_IRI),
                new TenantId("acme"),
                version,
                Optional.empty(),
                Map.of(new PropertyRef(TITLE_IRI), new TextValue("Write plan")),
                new Audit(createdAt, createdBy, createdAt, createdBy),
                false);
        store.seed(task);
        return task;
    }

    /**
     * Hand-written {@link QueryPort} and {@link BrowsePort} double: captures every {@link Query}
     * passed to {@link #browse}, returning a configurable (default empty) {@link QueryResult}. It
     * serves aggregate for the derivation planner and browse for the service under test; the
     * index preparation port is not exercised by these tests at all.
     */
    private static final class FakeQueryPort implements QueryPort, BrowsePort {

        private final List<Query> queries = new ArrayList<>();
        private final List<AggregateRequest> aggregateRequests = new ArrayList<>();
        private QueryResult response = new QueryResult(List.of(), 0, Map.of());

        @Override
        public QueryResult browse(Scope scope, Schema schema, Query query) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(schema, "schema must not be null");
            Objects.requireNonNull(query, "query must not be null");
            queries.add(query);
            return response;
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
        public AggregateResult aggregate(Scope scope, Schema schema, AggregateRequest request) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(schema, "schema must not be null");
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

    /**
     * Hand-written stand-in for the two read ports, serving one schema and one behaviour. Defaults
     * to the shared {@link #SCHEMA} schema and {@link #BEHAVIOUR} (every pre-existing test uses
     * the no-arg constructor), but a test that needs its own isolated schema and behaviour, e.g. a
     * state machine on its own type, can supply both directly rather than growing the shared
     * fixtures.
     */
    private static final class FakeOntology implements SchemaSource, BehaviourSource {

        private final Schema schema;
        private final Behaviour behaviour;

        FakeOntology() {
            this(SCHEMA, BEHAVIOUR);
        }

        FakeOntology(Schema schema, Behaviour behaviour) {
            this.schema = schema;
            this.behaviour = behaviour;
        }

        @Override
        public Schema schema(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            return schema;
        }

        @Override
        public Behaviour behaviour(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            return behaviour;
        }
    }

    /**
     * Hand-written {@link ObjectStorePort} double backed by an in-memory map, with a captured list
     * of committed {@link ChangeSet}s so tests can assert exactly what was committed. Deliberately
     * not {@code store-testkit}'s {@code InMemoryObjectStorePort}: the Command use cases live in
     * {@code store-core}, and that core must not gain a test dependency on the testkit just to reuse it.
     */
    private static final class FakeObjectStorePort implements ObjectStorePort {

        private final Map<ObjectId, BusinessObject> store = new HashMap<>();
        private final List<ChangeSet> commits = new ArrayList<>();
        private int findCalls;
        private int typesOfCalls;
        private int commitCalls;

        void seed(BusinessObject object) {
            store.put(object.id(), object);
        }

        @Override
        public Optional<BusinessObject> find(Scope scope, ObjectId id) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(id, "id must not be null");
            findCalls++;
            BusinessObject found = store.get(id);
            // Tenant-scoped, as the ObjectStorePort contract requires: another tenant's object is absent.
            if (found == null || found.deleted() || !found.tenant().equals(scope.tenantId())) {
                return Optional.empty();
            }
            return Optional.of(found);
        }

        @Override
        public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(ids, "ids must not be null");
            typesOfCalls++;
            Map<ObjectId, TypeRef> result = new HashMap<>();
            for (ObjectId id : ids) {
                BusinessObject found = store.get(id);
                if (found != null && !found.deleted() && found.tenant().equals(scope.tenantId())) {
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
                    BusinessObject deleted = new BusinessObject(
                            current.id(),
                            current.type(),
                            current.tenant(),
                            delete.expectedVersion() + 1,
                            current.state(),
                            current.properties(),
                            new Audit(
                                    current.audit().createdAt(), current.audit().createdBy(), delete.at(), delete.by()),
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
