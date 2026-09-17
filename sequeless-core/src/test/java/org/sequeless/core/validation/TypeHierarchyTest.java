package org.sequeless.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.ontology.OntologyReport;

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

    private static final MetaModelSnapshot SNAPSHOT =
        new MetaModelSnapshot(
            NS.substring(0, NS.length() - 1),
            Optional.empty(),
            Map.of(),
            List.of(DELIVERABLE, WORK_ITEM, TASK, PROJECT, PERSON),
            new OntologyReport(true, List.of()));

    private static TypeDefinition type(String iri, List<String> superTypes) {
        return new TypeDefinition(
            iri, iri, superTypes, List.of(), DisplayHints.none(), false, Optional.empty());
    }

    @Test
    void taskIsSubtypeOfWorkItem() {
        assertThat(TypeHierarchy.isSubtypeOf(SNAPSHOT, TASK_IRI, WORK_ITEM_IRI)).isTrue();
    }

    @Test
    void taskIsTransitiveSubtypeOfDeliverable() {
        assertThat(TypeHierarchy.isSubtypeOf(SNAPSHOT, TASK_IRI, DELIVERABLE_IRI)).isTrue();
    }

    @Test
    void taskIsReflexivelyASubtypeOfItself() {
        assertThat(TypeHierarchy.isSubtypeOf(SNAPSHOT, TASK_IRI, TASK_IRI)).isTrue();
    }

    @Test
    void personIsNotASubtypeOfWorkItem() {
        assertThat(TypeHierarchy.isSubtypeOf(SNAPSHOT, PERSON_IRI, WORK_ITEM_IRI)).isFalse();
    }

    @Test
    void workItemIsNotASubtypeOfTaskWrongDirection() {
        assertThat(TypeHierarchy.isSubtypeOf(SNAPSHOT, WORK_ITEM_IRI, TASK_IRI)).isFalse();
    }

    @Test
    void candidateNotInSnapshotIsFalseNotAThrow() {
        assertThat(TypeHierarchy.isSubtypeOf(SNAPSHOT, "urn:unknown", WORK_ITEM_IRI)).isFalse();
    }

    @Test
    void typeAndSubtypesOfWorkItemReturnsWorkItemTaskAndProjectOnly() {
        assertThat(TypeHierarchy.typeAndSubtypes(SNAPSHOT, WORK_ITEM_IRI))
            .containsExactlyInAnyOrder(
                new TypeRef(WORK_ITEM_IRI), new TypeRef(TASK_IRI), new TypeRef(PROJECT_IRI));
    }

    @Test
    void typeAndSubtypesOfDeliverableReturnsAllFourWorkItemHierarchyTypes() {
        assertThat(TypeHierarchy.typeAndSubtypes(SNAPSHOT, DELIVERABLE_IRI))
            .containsExactlyInAnyOrder(
                new TypeRef(DELIVERABLE_IRI),
                new TypeRef(WORK_ITEM_IRI),
                new TypeRef(TASK_IRI),
                new TypeRef(PROJECT_IRI));
    }

    @Test
    void typeAndSubtypesOfLeafPersonReturnsOnlyItself() {
        assertThat(TypeHierarchy.typeAndSubtypes(SNAPSHOT, PERSON_IRI))
            .containsExactly(new TypeRef(PERSON_IRI));
    }

    @Test
    void isSubtypeOfRejectsNullArguments() {
        assertThatNullPointerException()
            .isThrownBy(() -> TypeHierarchy.isSubtypeOf(null, TASK_IRI, WORK_ITEM_IRI));
        assertThatNullPointerException()
            .isThrownBy(() -> TypeHierarchy.isSubtypeOf(SNAPSHOT, null, WORK_ITEM_IRI));
        assertThatNullPointerException()
            .isThrownBy(() -> TypeHierarchy.isSubtypeOf(SNAPSHOT, TASK_IRI, null));
    }

    @Test
    void typeAndSubtypesRejectsNullArguments() {
        assertThatNullPointerException()
            .isThrownBy(() -> TypeHierarchy.typeAndSubtypes(null, WORK_ITEM_IRI));
        assertThatNullPointerException()
            .isThrownBy(() -> TypeHierarchy.typeAndSubtypes(SNAPSHOT, null));
    }
}
