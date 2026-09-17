package org.sequeless.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.AttributeDefinition;
import org.sequeless.spi.meta.Cardinality;
import org.sequeless.spi.meta.Datatype;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.RelationshipDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.ListValue;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.ReferenceValue;
import org.sequeless.spi.object.TextValue;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.validation.Violation;

/** Unit tests for {@link StructuralValidator}. */
class StructuralValidatorTest {

    private static final Scope SCOPE =
        new Scope(new TenantId("acme"), new Principal("alice", "Alice", Set.of("member")));

    private static final String NS = "https://sequeless.dev/ns/ref#";
    private static final String TITLE_IRI = NS + "title";
    private static final String TAGS_IRI = NS + "tags";
    private static final String ASSIGNED_TO_IRI = NS + "assignedTo";
    private static final String TASK_IRI = NS + "Task";
    private static final String PERSON_IRI = NS + "Person";
    private static final String EMPLOYEE_IRI = NS + "Employee";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String ABSTRACT_THING_IRI = NS + "AbstractThing";

    private static final AttributeDefinition TITLE =
        attribute(TITLE_IRI, Cardinality.required());
    private static final AttributeDefinition TAGS =
        attribute(TAGS_IRI, Cardinality.range(0, 2));
    private static final RelationshipDefinition ASSIGNED_TO =
        relationship(ASSIGNED_TO_IRI, Cardinality.optional(), PERSON_IRI);

    private static final TypeDefinition TASK =
        new TypeDefinition(
            TASK_IRI,
            "Task",
            List.of(),
            List.of(TITLE, TAGS, ASSIGNED_TO),
            DisplayHints.none(),
            false,
            Optional.empty());

    private static final TypeDefinition PERSON =
        new TypeDefinition(
            PERSON_IRI, "Person", List.of(), List.of(), DisplayHints.none(), false, Optional.empty());

    private static final TypeDefinition EMPLOYEE =
        new TypeDefinition(
            EMPLOYEE_IRI,
            "Employee",
            List.of(PERSON_IRI),
            List.of(),
            DisplayHints.none(),
            false,
            Optional.empty());

    private static final TypeDefinition PROJECT =
        new TypeDefinition(
            PROJECT_IRI,
            "Project",
            List.of(),
            List.of(),
            DisplayHints.none(),
            false,
            Optional.empty());

    private static final TypeDefinition ABSTRACT_THING =
        new TypeDefinition(
            ABSTRACT_THING_IRI,
            "AbstractThing",
            List.of(),
            List.of(),
            DisplayHints.none(),
            true,
            Optional.empty());

    private static final MetaModelSnapshot SNAPSHOT =
        new MetaModelSnapshot(
            NS.substring(0, NS.length() - 1),
            Optional.empty(),
            Map.of(),
            List.of(TASK, PERSON, EMPLOYEE, PROJECT, ABSTRACT_THING),
            new OntologyReport(true, List.of()));

    private static AttributeDefinition attribute(String iri, Cardinality cardinality) {
        return new AttributeDefinition(
            iri, iri, cardinality, false, false, false, false, DisplayHints.none(),
            Optional.empty(), Datatype.STRING);
    }

    private static RelationshipDefinition relationship(
        String iri, Cardinality cardinality, String targetTypeIri) {
        return new RelationshipDefinition(
            iri, iri, cardinality, false, false, false, false, DisplayHints.none(),
            Optional.empty(), targetTypeIri, Optional.empty(), false);
    }

    private static Map<PropertyRef, Value> propsWithTitle() {
        Map<PropertyRef, Value> props = new HashMap<>();
        props.put(new PropertyRef(TITLE_IRI), new TextValue("Write plan"));
        return props;
    }

    // --- required ---

    @Test
    void missingRequiredPropertyProducesOneViolationNamingItsIri() {
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, Map.of(), store);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).path()).isEqualTo(TITLE_IRI);
    }

    @Test
    void presentRequiredPropertyProducesNoViolation() {
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, propsWithTitle(), store);

        assertThat(violations).isEmpty();
    }

    // --- max cardinality ---

    @Test
    void listPropertyExceedingDeclaredMaxProducesOneViolation() {
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());
        Map<PropertyRef, Value> props = propsWithTitle();
        props.put(
            new PropertyRef(TAGS_IRI),
            new ListValue(
                List.of(new TextValue("a"), new TextValue("b"), new TextValue("c"))));

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, props, store);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).path()).isEqualTo(TAGS_IRI);
    }

    // --- reference targets ---

    @Test
    void referenceToNonexistentIdProducesViolationNamingTheId() {
        ObjectId missing = ObjectId.random();
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());
        Map<PropertyRef, Value> props = propsWithTitle();
        props.put(new PropertyRef(ASSIGNED_TO_IRI), new ReferenceValue(missing));

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, props, store);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).path()).isEqualTo(ASSIGNED_TO_IRI);
        assertThat(violations.get(0).message()).contains(missing.value().toString());
    }

    @Test
    void referenceToIdOfWrongTypeProducesViolation() {
        ObjectId projectId = ObjectId.random();
        FakeObjectStorePort store =
            new FakeObjectStorePort(Map.of(projectId, new TypeRef(PROJECT_IRI)));
        Map<PropertyRef, Value> props = propsWithTitle();
        props.put(new PropertyRef(ASSIGNED_TO_IRI), new ReferenceValue(projectId));

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, props, store);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).path()).isEqualTo(ASSIGNED_TO_IRI);
    }

    @Test
    void referenceToIdOfExactTargetTypeProducesNoViolation() {
        ObjectId personId = ObjectId.random();
        FakeObjectStorePort store =
            new FakeObjectStorePort(Map.of(personId, new TypeRef(PERSON_IRI)));
        Map<PropertyRef, Value> props = propsWithTitle();
        props.put(new PropertyRef(ASSIGNED_TO_IRI), new ReferenceValue(personId));

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, props, store);

        assertThat(violations).isEmpty();
    }

    @Test
    void referenceToIdOfSubtypeOfTargetTypeProducesNoViolation() {
        ObjectId employeeId = ObjectId.random();
        FakeObjectStorePort store =
            new FakeObjectStorePort(Map.of(employeeId, new TypeRef(EMPLOYEE_IRI)));
        Map<PropertyRef, Value> props = propsWithTitle();
        props.put(new PropertyRef(ASSIGNED_TO_IRI), new ReferenceValue(employeeId));

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, props, store);

        assertThat(violations).isEmpty();
    }

    // --- abstract type / create vs update divergence ---

    @Test
    void validateForCreateOnAbstractTypeProducesOneViolationAndNeverCallsTypesOf() {
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, ABSTRACT_THING, Map.of(), store);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).path()).isEmpty();
        assertThat(store.typesOfCalls).isZero();
    }

    @Test
    void validateForUpdateOnAbstractTypeProducesNoAbstractTypeViolation() {
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());

        List<Violation> violations =
            StructuralValidator.validateForUpdate(SCOPE, SNAPSHOT, ABSTRACT_THING, Map.of(), store);

        assertThat(violations).isEmpty();
    }

    // --- batching ---

    @Test
    void multiplePropertiesReferencingSameIdCallTypesOfOnceWithASetContainingItOnce() {
        ObjectId personId = ObjectId.random();
        RelationshipDefinition secondRelationship =
            relationship(NS + "backup", Cardinality.optional(), PERSON_IRI);
        TypeDefinition typeWithTwoRelationships =
            new TypeDefinition(
                NS + "Dual",
                "Dual",
                List.of(),
                List.of(TITLE, ASSIGNED_TO, secondRelationship),
                DisplayHints.none(),
                false,
                Optional.empty());
        FakeObjectStorePort store =
            new FakeObjectStorePort(Map.of(personId, new TypeRef(PERSON_IRI)));
        Map<PropertyRef, Value> props = propsWithTitle();
        props.put(new PropertyRef(ASSIGNED_TO_IRI), new ReferenceValue(personId));
        props.put(new PropertyRef(NS + "backup"), new ReferenceValue(personId));

        List<Violation> violations =
            StructuralValidator.validateForCreate(
                SCOPE, SNAPSHOT, typeWithTwoRelationships, props, store);

        assertThat(violations).isEmpty();
        assertThat(store.typesOfCalls).isEqualTo(1);
        assertThat(store.lastTypesOfIds).containsExactly(personId);
    }

    // --- empty ---

    @Test
    void emptyPropertiesWithNoRequiredPropertiesOnTypeProducesNoViolations() {
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());

        List<Violation> violations =
            StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, PERSON, Map.of(), store);

        assertThat(violations).isEmpty();
    }

    // --- null arguments ---

    @Test
    void validateForCreateRejectsNullArguments() {
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForCreate(null, SNAPSHOT, TASK, Map.of(), store));
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForCreate(SCOPE, null, TASK, Map.of(), store));
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, null, Map.of(), store));
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, null, store));
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForCreate(SCOPE, SNAPSHOT, TASK, Map.of(), null));
    }

    @Test
    void validateForUpdateRejectsNullArguments() {
        FakeObjectStorePort store = new FakeObjectStorePort(Map.of());
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForUpdate(null, SNAPSHOT, TASK, Map.of(), store));
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForUpdate(SCOPE, null, TASK, Map.of(), store));
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForUpdate(SCOPE, SNAPSHOT, null, Map.of(), store));
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForUpdate(SCOPE, SNAPSHOT, TASK, null, store));
        assertThatNullPointerException()
            .isThrownBy(
                () -> StructuralValidator.validateForUpdate(SCOPE, SNAPSHOT, TASK, Map.of(), null));
    }

    /**
     * Hand-written {@link ObjectStorePort} double; only {@code typesOf} is exercised by these
     * tests, so every other method throws {@link UnsupportedOperationException}, the same idiom as
     * {@code DefaultMetaModelServiceTest}'s {@code FakeOntologyPort}.
     */
    private static final class FakeObjectStorePort implements ObjectStorePort {

        private final Map<ObjectId, TypeRef> types;
        private int typesOfCalls;
        private Set<ObjectId> lastTypesOfIds;

        FakeObjectStorePort(Map<ObjectId, TypeRef> types) {
            this.types = types;
        }

        @Override
        public Optional<BusinessObject> find(Scope scope, ObjectId id) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public PageResult<BusinessObject> browse(Scope scope, Set<TypeRef> types, Page page) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
            java.util.Objects.requireNonNull(scope, "scope must not be null");
            java.util.Objects.requireNonNull(ids, "ids must not be null");
            typesOfCalls++;
            lastTypesOfIds = ids;
            Map<ObjectId, TypeRef> result = new HashMap<>();
            for (ObjectId id : ids) {
                TypeRef type = types.get(id);
                if (type != null) {
                    result.put(id, type);
                }
            }
            return result;
        }

        @Override
        public CommitResult commit(Scope scope, ChangeSet changeSet) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public OntologyDocumentStore ontologyDocuments() {
            throw new UnsupportedOperationException("not exercised by these tests");
        }
    }
}
