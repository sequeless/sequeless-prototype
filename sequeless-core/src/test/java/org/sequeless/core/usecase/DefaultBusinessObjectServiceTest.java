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
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.core.validation.ValidationException;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.Delete;
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
import org.sequeless.spi.validation.ValidationPort;
import org.sequeless.spi.validation.Violation;

/**
 * Unit tests for {@link DefaultBusinessObjectService}, built against a hand-written 2-level type
 * hierarchy ({@code WorkItem} &gt; {@code Task}, plus unrelated {@code Person} and abstract {@code
 * AbstractThing}), the same idiom {@code TypeHierarchyTest} and {@code
 * DefaultMetaModelServiceTest} use: every port double is either a plain lambda ({@link
 * AuthorizationPort}, {@link ValidationPort}) or a small hand-written fake ({@link FakeOntologyPort},
 * {@link FakeObjectStorePort}) rather than a mocking framework. Core must not depend on the testkit
 * module at all, so {@code sequeless-spi-testkit}'s {@code InMemoryObjectStorePort} is deliberately
 * not reused here.
 */
class DefaultBusinessObjectServiceTest {

    private static final String NS = "https://sequeless.dev/ns/bo#";
    private static final String WORK_ITEM_IRI = NS + "WorkItem";
    private static final String TASK_IRI = NS + "Task";
    private static final String PERSON_IRI = NS + "Person";
    private static final String ABSTRACT_THING_IRI = NS + "AbstractThing";
    private static final String TITLE_IRI = NS + "title";
    private static final String HOURS_IRI = NS + "estimatedHours";
    private static final String ASSIGNED_TO_IRI = NS + "assignedTo";

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

    private static final TypeDefinition WORK_ITEM =
        new TypeDefinition(
            WORK_ITEM_IRI, "WorkItem", List.of(), List.of(), DisplayHints.none(), false,
            Optional.empty());
    private static final TypeDefinition TASK =
        new TypeDefinition(
            TASK_IRI, "Task", List.of(WORK_ITEM_IRI), List.of(TITLE, HOURS, ASSIGNED_TO),
            DisplayHints.none(), false, Optional.empty());
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
            List.of(WORK_ITEM, TASK, PERSON, ABSTRACT_THING),
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
        return new DefaultBusinessObjectService(
            new FakeOntologyPort(), store, validationPort, authorizationPort, CLOCK);
    }

    private static ValidationPort noViolations() {
        return (scope, snapshot, object) -> List.of();
    }

    // --- browse ---

    @Test
    void browseAuthorizesOnceAgainstResolvedTypeIriAndListsTypeAndSubtypes() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        List<Operation> capturedOps = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort =
            (scope, operation, resource) -> {
                capturedOps.add(operation);
                capturedResources.add(resource);
                return AccessDecision.permit("ok");
            };
        DefaultBusinessObjectService service = service(store, noViolations(), authorizationPort);

        service.browse(ALICE, "WorkItem", new Page(0, 20));

        assertThat(capturedOps).containsExactly(Operation.BROWSE);
        assertThat(capturedResources).containsExactly(WORK_ITEM_IRI);
        assertThat(store.lastBrowseTypes)
            .containsExactlyInAnyOrder(new TypeRef(WORK_ITEM_IRI), new TypeRef(TASK_IRI));
    }

    @Test
    void browseThrowsTypeNotFoundForUnknownTypeBeforeAnyPortCall() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(TypeNotFoundException.class)
            .isThrownBy(() -> service.browse(ALICE, "NoSuchType", new Page(0, 20)));
        assertThat(store.browseCalls).isZero();
    }

    @Test
    void browseThrowsAuthorizationExceptionOnDenyAndNeverCallsBrowse() {
        FakeObjectStorePort store = new FakeObjectStorePort();
        DefaultBusinessObjectService service = service(store, noViolations(), denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.browse(ALICE, "WorkItem", new Page(0, 20)));
        assertThat(store.browseCalls).isZero();
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
        BusinessObject person =
            new BusinessObject(
                ObjectId.random(), new TypeRef(PERSON_IRI), ALICE.tenantId(), 1, Optional.empty(),
                Map.of(), new Audit(NOW, "alice", NOW, "alice"), false);
        store.seed(person);
        DefaultBusinessObjectService service = service(store, noViolations(), permitAll());

        assertThatExceptionOfType(ObjectNotFoundException.class)
            .isThrownBy(() -> service.read(ALICE, "Task", person.id()));
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
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    null, store, noViolations(), permitAll(), CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), null, noViolations(), permitAll(), CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), store, null, permitAll(), CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), store, noViolations(), null, CLOCK));
        assertThatNullPointerException()
            .isThrownBy(
                () -> new DefaultBusinessObjectService(
                    new FakeOntologyPort(), store, noViolations(), permitAll(), null));
    }

    // --- null arguments per method ---

    @Test
    void browseRejectsNullArguments() {
        DefaultBusinessObjectService service =
            service(new FakeObjectStorePort(), noViolations(), permitAll());
        assertThatNullPointerException()
            .isThrownBy(() -> service.browse(null, "Task", new Page(0, 20)));
        assertThatNullPointerException().isThrownBy(() -> service.browse(ALICE, null, new Page(0, 20)));
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
