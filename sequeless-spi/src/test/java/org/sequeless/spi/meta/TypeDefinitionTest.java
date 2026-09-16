package org.sequeless.spi.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TypeDefinitionTest {

    private static TypeDefinition sample(List<String> superTypes, List<PropertyDefinition> properties) {
        return new TypeDefinition(
            "https://example.org/ns#Task",
            "Task",
            superTypes,
            properties,
            DisplayHints.none(),
            false,
            Optional.empty());
    }

    @Test
    void constructsWithValidComponents() {
        TypeDefinition type = sample(List.of("https://example.org/ns#WorkItem"), List.of());

        assertThat(type.iri()).isEqualTo("https://example.org/ns#Task");
        assertThat(type.superTypes()).containsExactly("https://example.org/ns#WorkItem");
        assertThat(type.isAbstract()).isFalse();
        assertThat(type.stateMachine()).isEmpty();
    }

    @Test
    void rejectsNullOrBlankIri() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TypeDefinition(
                null, "Task", List.of(), List.of(), DisplayHints.none(), false, Optional.empty()));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TypeDefinition(
                "  ", "Task", List.of(), List.of(), DisplayHints.none(), false, Optional.empty()));
    }

    @Test
    void rejectsNullOrBlankLabel() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TypeDefinition(
                "https://example.org/ns#Task", null, List.of(), List.of(), DisplayHints.none(), false,
                Optional.empty()));
    }

    @Test
    void rejectsNullSuperTypes() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TypeDefinition(
                "https://example.org/ns#Task", "Task", null, List.of(), DisplayHints.none(), false,
                Optional.empty()));
    }

    @Test
    void rejectsNullProperties() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TypeDefinition(
                "https://example.org/ns#Task", "Task", List.of(), null, DisplayHints.none(), false,
                Optional.empty()));
    }

    @Test
    void rejectsNullDisplayHints() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TypeDefinition(
                "https://example.org/ns#Task", "Task", List.of(), List.of(), null, false,
                Optional.empty()));
    }

    @Test
    void rejectsNullStateMachineWrapper() {
        assertThatNullPointerException()
            .isThrownBy(() -> new TypeDefinition(
                "https://example.org/ns#Task", "Task", List.of(), List.of(), DisplayHints.none(), false,
                null));
    }

    @Test
    void rejectsNullElementWithinSuperTypes() {
        List<String> superTypesWithNull = new ArrayList<>();
        superTypesWithNull.add(null);

        assertThatNullPointerException()
            .isThrownBy(() -> sample(superTypesWithNull, List.of()));
    }

    @Test
    void returnedSuperTypesListIsUnmodifiableAndUnaffectedByLaterMutationOfCallerList() {
        List<String> callerSuperTypes = new ArrayList<>();
        callerSuperTypes.add("https://example.org/ns#WorkItem");

        TypeDefinition type = sample(callerSuperTypes, List.of());
        callerSuperTypes.add("https://example.org/ns#Deliverable");

        assertThat(type.superTypes()).containsExactly("https://example.org/ns#WorkItem");
        assertThatThrownBy(() -> type.superTypes().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnedPropertiesListIsUnmodifiable() {
        TypeDefinition type = sample(List.of(), List.of());

        assertThatThrownBy(() -> type.properties().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
