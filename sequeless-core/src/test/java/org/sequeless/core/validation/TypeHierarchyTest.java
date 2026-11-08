package org.sequeless.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.meta.schema.DisplayHints;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.TypeDefinition;
import org.sequeless.spi.object.TypeRef;

/**
 * Unit tests for {@link TypeHierarchy}, built against a hand-written 3-level hierarchy ({@code
 * Deliverable} &gt; {@code WorkItem} &gt; {@code Task}, {@code Project}; {@code Person} outside the
 * hierarchy), mirroring the testkit's reference-ontology shape but constructed directly from SPI
 * records since {@code sequeless-core} does not depend on {@code sequeless-spi-testkit}.
 */
class TypeHierarchyTest {

    private static final String NS = "https://sequeless.dev/ns/ref#";
    private static final String DELIVERABLE_IRI = NS + "Deliverable";
    private static final String WORK_ITEM_IRI = NS + "WorkItem";
    private static final String TASK_IRI = NS + "Task";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String PERSON_IRI = NS + "Person";

    private static final TypeDefinition DELIVERABLE = type(DELIVERABLE_IRI, List.of());
    private static final TypeDefinition WORK_ITEM = type(WORK_ITEM_IRI, List.of(DELIVERABLE_IRI));
    private static final TypeDefinition TASK = type(TASK_IRI, List.of(WORK_ITEM_IRI));
    private static final TypeDefinition PROJECT = type(PROJECT_IRI, List.of(WORK_ITEM_IRI));
    private static final TypeDefinition PERSON = type(PERSON_IRI, List.of());

    private static final Schema SCHEMA = new Schema(
            new OntologyVersion(1),
            NS.substring(0, NS.length() - 1),
            Optional.empty(),
            Map.of(),
            List.of(DELIVERABLE, WORK_ITEM, TASK, PROJECT, PERSON));

    private static TypeDefinition type(String iri, List<String> superTypes) {
        return new TypeDefinition(iri, iri, superTypes, List.of(), DisplayHints.none(), false);
    }

    @Test
    void taskIsSubtypeOfWorkItem() {
        assertThat(TypeHierarchy.isSubtypeOf(SCHEMA, TASK_IRI, WORK_ITEM_IRI)).isTrue();
    }

    @Test
    void taskIsTransitiveSubtypeOfDeliverable() {
        assertThat(TypeHierarchy.isSubtypeOf(SCHEMA, TASK_IRI, DELIVERABLE_IRI)).isTrue();
    }

    @Test
    void taskIsReflexivelyASubtypeOfItself() {
        assertThat(TypeHierarchy.isSubtypeOf(SCHEMA, TASK_IRI, TASK_IRI)).isTrue();
    }

    @Test
    void personIsNotASubtypeOfWorkItem() {
        assertThat(TypeHierarchy.isSubtypeOf(SCHEMA, PERSON_IRI, WORK_ITEM_IRI)).isFalse();
    }

    @Test
    void workItemIsNotASubtypeOfTaskWrongDirection() {
        assertThat(TypeHierarchy.isSubtypeOf(SCHEMA, WORK_ITEM_IRI, TASK_IRI)).isFalse();
    }

    @Test
    void candidateNotInSnapshotIsFalseNotAThrow() {
        assertThat(TypeHierarchy.isSubtypeOf(SCHEMA, "urn:unknown", WORK_ITEM_IRI))
                .isFalse();
    }

    @Test
    void typeAndSubtypesOfWorkItemReturnsWorkItemTaskAndProjectOnly() {
        assertThat(TypeHierarchy.typeAndSubtypes(SCHEMA, WORK_ITEM_IRI))
                .containsExactlyInAnyOrder(new TypeRef(WORK_ITEM_IRI), new TypeRef(TASK_IRI), new TypeRef(PROJECT_IRI));
    }

    @Test
    void typeAndSubtypesOfDeliverableReturnsAllFourWorkItemHierarchyTypes() {
        assertThat(TypeHierarchy.typeAndSubtypes(SCHEMA, DELIVERABLE_IRI))
                .containsExactlyInAnyOrder(
                        new TypeRef(DELIVERABLE_IRI),
                        new TypeRef(WORK_ITEM_IRI),
                        new TypeRef(TASK_IRI),
                        new TypeRef(PROJECT_IRI));
    }

    @Test
    void typeAndSubtypesOfLeafPersonReturnsOnlyItself() {
        assertThat(TypeHierarchy.typeAndSubtypes(SCHEMA, PERSON_IRI)).containsExactly(new TypeRef(PERSON_IRI));
    }

    @Test
    void concreteTypeAndSubtypesOfConcreteLeafResolvesToItselfOnly() {
        assertThat(TypeHierarchy.concreteTypeAndSubtypes(SCHEMA, TASK_IRI)).containsExactly(TASK_IRI);
    }

    @Test
    void concreteTypeAndSubtypesOfAbstractTypeExcludesAbstractAncestorsAndSelf() {
        Schema schemaWithAbstractWorkItem = new Schema(
                SCHEMA.ontologyVersion(),
                SCHEMA.ontologyIri(),
                SCHEMA.versionIri(),
                SCHEMA.prefixes(),
                List.of(
                        DELIVERABLE,
                        new TypeDefinition(
                                WORK_ITEM_IRI,
                                WORK_ITEM_IRI,
                                List.of(DELIVERABLE_IRI),
                                List.of(),
                                DisplayHints.none(),
                                true),
                        TASK,
                        PROJECT,
                        PERSON));

        assertThat(TypeHierarchy.concreteTypeAndSubtypes(schemaWithAbstractWorkItem, WORK_ITEM_IRI))
                .containsExactlyInAnyOrder(TASK_IRI, PROJECT_IRI);
    }

    @Test
    void concreteTypeAndSubtypesRejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> TypeHierarchy.concreteTypeAndSubtypes(null, WORK_ITEM_IRI));
        assertThatNullPointerException().isThrownBy(() -> TypeHierarchy.concreteTypeAndSubtypes(SCHEMA, null));
    }

    @Test
    void isSubtypeOfRejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> TypeHierarchy.isSubtypeOf(null, TASK_IRI, WORK_ITEM_IRI));
        assertThatNullPointerException().isThrownBy(() -> TypeHierarchy.isSubtypeOf(SCHEMA, null, WORK_ITEM_IRI));
        assertThatNullPointerException().isThrownBy(() -> TypeHierarchy.isSubtypeOf(SCHEMA, TASK_IRI, null));
    }

    @Test
    void typeAndSubtypesRejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> TypeHierarchy.typeAndSubtypes(null, WORK_ITEM_IRI));
        assertThatNullPointerException().isThrownBy(() -> TypeHierarchy.typeAndSubtypes(SCHEMA, null));
    }
}
