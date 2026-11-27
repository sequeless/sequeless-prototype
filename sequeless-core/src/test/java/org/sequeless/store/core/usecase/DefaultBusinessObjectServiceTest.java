package org.sequeless.store.core.usecase;

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
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.filter.api.FieldFilter;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.behaviour.api.State;
import org.sequeless.ontology.behaviour.api.StateMachineDefinition;
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
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.StaleObjectException;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Update;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.store.api.AccessDecision;
import org.sequeless.store.core.AuthorizationException;
import org.sequeless.store.core.statemachine.PayloadValueCodec;
import org.sequeless.store.core.validation.ValidationException;
import org.sequeless.store.spi.AuthorizationPort;
import org.sequeless.store.spi.ObjectStorePort;
import org.sequeless.store.spi.ValidationPort;
import org.sequeless.store.spi.Violation;

/**
 * Unit tests for {@link DefaultBusinessObjectService}'s command side: {@code add}, {@code edit}, {@code delete} and
 * the {@code typeOf} lookup. The browse and read tests are in the host, {@code
 * org.sequeless.app.query.DefaultObjectQueryServiceTest}, because those use cases moved out of core in spec 18 U6.
 *
 * <p>The schema is a hand-written two-level type hierarchy (abstract {@code WorkItem} over concrete {@code Task} and
 * {@code Project}, plus unrelated {@code Person} and abstract {@code AbstractThing}). Every port double is a plain
 * lambda or a small hand-written fake rather than a mocking framework. Core must not depend on the testkit module at
 * all, so {@code sequeless-spi-testkit}'s {@code InMemoryObjectStorePort} is deliberately not reused here.
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

    private static DefaultBusinessObjectService service(
            FakeObjectStorePort store, ValidationPort validationPort, AuthorizationPort authorizationPort) {
        return new DefaultBusinessObjectService(
                new FakeOntology(), new FakeOntology(), store, validationPort, authorizationPort, CLOCK);
    }

    private static ValidationPort noViolations() {
        return (scope, schema, object) -> List.of();
    }

    @Test
    void typeOfReturnsTheTypeOfALiveObjectOfTheCallersTenant() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject task = seedTask(store, "alice", NOW, 1);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThat(service.typeOf(ALICE, task.id())).contains(new TypeRef(TASK_IRI));
    }

    @Test
    void typeOfIsEmptyForAnIdThatDoesNotExist() {
        DefaultBusinessObjectService service = service(new FakeObjectStorePort(), noViolations(), permitAll());

        assertThat(service.typeOf(ALICE, ObjectId.random())).isEmpty();
    }

    @Test
    void typeOfIsEmptyForAnObjectOfAnotherTenant() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject task = seedTask(store, "alice", NOW, 1);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());
        Scope otherTenant = new Scope(new TenantId("globex"), new Principal("carol", Set.of("member")));

        assertThat(service.typeOf(otherTenant, task.id())).isEmpty();
    }

    // --- read ---

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
        assertThat(outbox.payload().get("typeIri")).isEqualTo(TASK_IRI);
        assertThat(result.version()).isEqualTo(1);
    }

    @Test
    void addStampsTheOntologyVersionOfTheSchemaItValidatedAgainst() {
        // Isolated schema and behaviour at version 7, so the stamp must come from the snapshot read,
        // not from the shared fixtures' version 1.
        String ticketIri = NS + "Ticket";
        TypeDefinition ticket =
                new TypeDefinition(ticketIri, "Ticket", List.of(), List.of(), DisplayHints.none(), false);
        Schema schema = new Schema(
                new OntologyVersion(7), NS.substring(0, NS.length() - 1), Optional.empty(), Map.of(), List.of(ticket));
        Behaviour behaviour = new Behaviour(new OntologyVersion(7), Map.of(), Map.of());

        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultBusinessObjectService service = new DefaultBusinessObjectService(
                new FakeOntology(schema, behaviour),
                new FakeOntology(schema, behaviour),
                store,
                new FakeValidationPort(List.of()),
                permitAll(),
                CLOCK);

        service.add(ALICE, "Ticket", Map.of());

        OutboxEntry created = store.commits.get(0).outbox().get(0);
        assertThat(created.kind()).isEqualTo(OutboxEntry.KIND_OBJECT_CREATED);
        assertThat(created.payload()).containsEntry("ontologyVersion", 7);
    }

    @Test
    void addWithStateMachineInitializesStateToInitialState() {
        // Isolated schema and behaviour (not the shared fixtures) whose behaviour carries the state
        // machine for its one type, so this test exercises add()'s new "initialize state" branch
        // without perturbing any other test's shared type list or state machines.
        String ticketIri = NS + "Ticket";
        String draftIri = NS + "Draft";
        String openIri = NS + "Open";
        State draft = new State(draftIri, "Draft", 0);
        State open = new State(openIri, "Open", 1);
        StateMachineDefinition machine =
                new StateMachineDefinition(NS + "TicketLifecycle", List.of(draft, open), draft, List.of());
        TypeDefinition ticket =
                new TypeDefinition(ticketIri, "Ticket", List.of(), List.of(), DisplayHints.none(), false);
        Schema schema = new Schema(
                new OntologyVersion(1), NS.substring(0, NS.length() - 1), Optional.empty(), Map.of(), List.of(ticket));
        Behaviour behaviour = new Behaviour(new OntologyVersion(1), Map.of(ticketIri, machine), Map.of());

        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = new DefaultBusinessObjectService(
                new FakeOntology(schema, behaviour),
                new FakeOntology(schema, behaviour),
                store,
                validationPort,
                permitAll(),
                CLOCK);

        BusinessObject result = service.add(ALICE, "Ticket", Map.of());

        assertThat(result.state()).contains(draftIri);
        assertThat(store.commits).hasSize(1);
        Create create = (Create) store.commits.get(0).mutations().get(0);
        assertThat(create.object().state()).contains(draftIri);
    }

    @Test
    void addWithBadPropertyThrowsStructuralValidationExceptionWithoutCommitOrValidate() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.add(ALICE, "Task", Map.of("title", 42)))
                .satisfies(
                        exception -> assertThat(exception.source()).isEqualTo(ValidationException.Source.STRUCTURAL));
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void addWithDerivedPropertyThrowsValidationExceptionNamingItAsDerived() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.add(ALICE, "Project", Map.of("openTaskCount", 3)))
                .satisfies(exception -> {
                    assertThat(exception.source()).isEqualTo(ValidationException.Source.STRUCTURAL);
                    assertThat(exception.violations()).anySatisfy(violation -> {
                        assertThat(violation.path()).isEqualTo(OPEN_TASK_COUNT_IRI);
                        assertThat(violation.message()).contains("is a derived property and cannot be set directly");
                    });
                });
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void addWithMissingRequiredTitleThrowsStructuralValidationExceptionWithoutValidate() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.add(ALICE, "Task", Map.of()))
                .satisfies(
                        exception -> assertThat(exception.source()).isEqualTo(ValidationException.Source.STRUCTURAL));
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
                .satisfies(exception -> assertThat(exception.source()).isEqualTo(ValidationException.Source.SHACL));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void addingAbstractTypeThrowsStructuralValidationExceptionWithAbstractViolation() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.add(ALICE, "AbstractThing", Map.of()))
                .satisfies(exception -> {
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

        BusinessObject result = service.edit(BOB, "Task", existing.id(), 1, Map.of("title", "Updated"));

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
        assertThat(outbox.payload().get("typeIri")).isEqualTo(TASK_IRI);
        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    void editNonexistentIdThrowsObjectNotFoundWithoutCommit() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
                .isThrownBy(() -> service.edit(BOB, "Task", ObjectId.random(), 1, Map.of("title", "x")));
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void editWithMismatchedStoredTypeThrowsObjectNotFound() {
        FakeObjectStorePort store = new FakeObjectStorePort();
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
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
                .isThrownBy(() -> service.edit(BOB, "Task", person.id(), 1, Map.of("title", "x")));
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
                        exception -> assertThat(exception.source()).isEqualTo(ValidationException.Source.STRUCTURAL));
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void editWithDerivedPropertyThrowsValidationExceptionNamingItAsDerived() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedProject(store, "alice", NOW, 1);
        FakeValidationPort validationPort = new FakeValidationPort(List.of());
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.edit(BOB, "Project", existing.id(), 1, Map.of("totalEstimatedHours", 40)))
                .satisfies(exception -> {
                    assertThat(exception.source()).isEqualTo(ValidationException.Source.STRUCTURAL);
                    assertThat(exception.violations()).anySatisfy(violation -> {
                        assertThat(violation.path()).isEqualTo(TOTAL_ESTIMATED_HOURS_IRI);
                        assertThat(violation.message()).contains("is a derived property and cannot be set directly");
                    });
                });
        assertThat(store.commitCalls).isZero();
        assertThat(validationPort.calls).isZero();
    }

    @Test
    void editWithShaclViolationThrowsShaclValidationExceptionWithoutCommit() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 1);
        FakeValidationPort validationPort = new FakeValidationPort(List.of(new Violation(TITLE_IRI, "shacl says no")));
        DefaultBusinessObjectService service = service(store, validationPort, permitAll());

        assertThatExceptionOfType(ValidationException.class)
                .isThrownBy(() -> service.edit(BOB, "Task", existing.id(), 1, Map.of("title", "Updated")))
                .satisfies(exception -> assertThat(exception.source()).isEqualTo(ValidationException.Source.SHACL));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void editThrowsAuthorizationExceptionOnDenyBeforeFind() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 1);
        DefaultBusinessObjectService service = service(store, noViolations(), denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.edit(BOB, "Task", existing.id(), 1, Map.of("title", "Updated")));
        assertThat(store.findCalls).isZero();
    }

    @Test
    void editPropagatesStaleObjectException() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 3);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(StaleObjectException.class)
                .isThrownBy(() -> service.edit(BOB, "Task", existing.id(), 1, Map.of("title", "Updated")));
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
        assertThat(outbox.payload().get("typeIri")).isEqualTo(TASK_IRI);
        // ObjectStorePort#find can never read a soft-deleted object back, so ObjectDeleted embeds a
        // full-IRI-keyed snapshot of the object's properties, tagged exactly as PayloadValueCodec
        // encodes every other outbox value.
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) outbox.payload().get("properties");
        assertThat(properties).containsEntry(TITLE_IRI, PayloadValueCodec.toPayload(new TextValue("Write plan")));
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
                .isThrownBy(() -> service.delete(ALICE, "Task", ObjectId.random(), OptionalLong.empty()));
        assertThat(store.commitCalls).isZero();
    }

    @Test
    void deleteWithMismatchedStoredTypeThrowsObjectNotFound() {
        FakeObjectStorePort store = new FakeObjectStorePort();
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
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
                .isThrownBy(() -> service.delete(ALICE, "Task", person.id(), OptionalLong.empty()));
    }

    @Test
    void deleteThrowsAuthorizationExceptionOnDenyBeforeFind() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        BusinessObject existing = seedTask(store, "alice", NOW, 3);
        DefaultBusinessObjectService service = service(store, noViolations(), denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.delete(ALICE, "Task", existing.id(), OptionalLong.empty()));
        assertThat(store.findCalls).isZero();
    }

    // --- constructor ---

    @Test
    void constructorRejectsNullArguments() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultBusinessObjectService(
                        null, new FakeOntology(), store, noViolations(), permitAll(), CLOCK));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultBusinessObjectService(
                        new FakeOntology(), null, store, noViolations(), permitAll(), CLOCK));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultBusinessObjectService(
                        new FakeOntology(), new FakeOntology(), null, noViolations(), permitAll(), CLOCK));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultBusinessObjectService(
                        new FakeOntology(), new FakeOntology(), store, null, permitAll(), CLOCK));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultBusinessObjectService(
                        new FakeOntology(), new FakeOntology(), store, noViolations(), null, CLOCK));
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultBusinessObjectService(
                        new FakeOntology(), new FakeOntology(), store, noViolations(), permitAll(), null));
    }

    // --- null arguments per method ---

    @Test
    void addRejectsNullArguments() {
        DefaultBusinessObjectService service = service(new FakeObjectStorePort(), noViolations(), permitAll());
        assertThatNullPointerException().isThrownBy(() -> service.add(null, "Task", Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.add(ALICE, null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.add(ALICE, "Task", null));
    }

    @Test
    void editRejectsNullArguments() {
        DefaultBusinessObjectService service = service(new FakeObjectStorePort(), noViolations(), permitAll());
        ObjectId id = ObjectId.random();
        assertThatNullPointerException().isThrownBy(() -> service.edit(null, "Task", id, 1, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.edit(ALICE, null, id, 1, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.edit(ALICE, "Task", null, 1, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.edit(ALICE, "Task", id, 1, null));
    }

    @Test
    void deleteRejectsNullArguments() {
        DefaultBusinessObjectService service = service(new FakeObjectStorePort(), noViolations(), permitAll());
        ObjectId id = ObjectId.random();
        assertThatNullPointerException().isThrownBy(() -> service.delete(null, "Task", id, OptionalLong.empty()));
        assertThatNullPointerException().isThrownBy(() -> service.delete(ALICE, null, id, OptionalLong.empty()));
        assertThatNullPointerException().isThrownBy(() -> service.delete(ALICE, "Task", null, OptionalLong.empty()));
        assertThatNullPointerException().isThrownBy(() -> service.delete(ALICE, "Task", id, null));
    }

    private static BusinessObject seedProject(
            FakeObjectStorePort store, String createdBy, Instant createdAt, long version) {
        BusinessObject project = new BusinessObject(
                ObjectId.random(),
                new TypeRef(PROJECT_IRI),
                new TenantId("acme"),
                version,
                Optional.empty(),
                Map.of(),
                new Audit(createdAt, createdBy, createdAt, createdBy),
                false);
        store.seed(project);
        return project;
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
        public List<Violation> validate(Scope scope, Schema schema, BusinessObject object) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(schema, "schema must not be null");
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
