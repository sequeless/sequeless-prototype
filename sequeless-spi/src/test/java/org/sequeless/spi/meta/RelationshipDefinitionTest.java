package org.sequeless.spi.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RelationshipDefinitionTest {

    private static RelationshipDefinition sample(DisplayHints displayHints) {
        return new RelationshipDefinition(
            "https://example.org/ns#hasTask",
            "Has task",
            Cardinality.optional(),
            false,
            false,
            false,
            false,
            false,
            displayHints,
            Optional.empty(),
            "https://example.org/ns#Task",
            Optional.of("https://example.org/ns#belongsToProject"),
            false);
    }

    @Test
    void orderDelegatesToDisplayHints() {
        RelationshipDefinition relationship = sample(new DisplayHints(4, Optional.empty(), false));

        assertThat(relationship.order()).isEqualTo(4);
    }

    @Test
    void carriesTargetTypeInverseAndTransitive() {
        RelationshipDefinition relationship = sample(DisplayHints.none());

        assertThat(relationship.targetTypeIri()).isEqualTo("https://example.org/ns#Task");
        assertThat(relationship.inverseIri()).contains("https://example.org/ns#belongsToProject");
        assertThat(relationship.transitive()).isFalse();
    }

    @Test
    void rejectsNullOrBlankIri() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RelationshipDefinition(
                null, "Has task", Cardinality.optional(), false, false, false, false, false,
                DisplayHints.none(), Optional.empty(), "https://example.org/ns#Task", Optional.empty(), false));
    }

    @Test
    void rejectsNullOrBlankLabel() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new RelationshipDefinition(
                "https://example.org/ns#hasTask", "  ", Cardinality.optional(), false, false, false,
                false, false, DisplayHints.none(), Optional.empty(), "https://example.org/ns#Task",
                Optional.empty(), false));
    }

    @Test
    void rejectsNullCardinality() {
        assertThatNullPointerException()
            .isThrownBy(() -> new RelationshipDefinition(
                "https://example.org/ns#hasTask", "Has task", null, false, false, false, false, false,
                DisplayHints.none(), Optional.empty(), "https://example.org/ns#Task", Optional.empty(),
                false));
    }

    @Test
    void rejectsNullDisplayHints() {
        assertThatNullPointerException()
            .isThrownBy(() -> new RelationshipDefinition(
                "https://example.org/ns#hasTask", "Has task", Cardinality.optional(), false, false,
                false, false, false, null, Optional.empty(), "https://example.org/ns#Task", Optional.empty(),
                false));
    }

    @Test
    void rejectsNullDerivationWrapper() {
        assertThatNullPointerException()
            .isThrownBy(() -> new RelationshipDefinition(
                "https://example.org/ns#hasTask", "Has task", Cardinality.optional(), false, false,
                false, false, false, DisplayHints.none(), null, "https://example.org/ns#Task", Optional.empty(),
                false));
    }

    @Test
    void rejectsNullTargetTypeIri() {
        assertThatNullPointerException()
            .isThrownBy(() -> new RelationshipDefinition(
                "https://example.org/ns#hasTask", "Has task", Cardinality.optional(), false, false,
                false, false, false, DisplayHints.none(), Optional.empty(), null, Optional.empty(), false));
    }

    @Test
    void rejectsNullInverseIriWrapper() {
        assertThatNullPointerException()
            .isThrownBy(() -> new RelationshipDefinition(
                "https://example.org/ns#hasTask", "Has task", Cardinality.optional(), false, false,
                false, false, false, DisplayHints.none(), Optional.empty(), "https://example.org/ns#Task", null,
                false));
    }

    @Test
    void isUsableAsAPropertyDefinition() {
        PropertyDefinition property = sample(DisplayHints.none());

        assertThat(property).isInstanceOf(RelationshipDefinition.class);
    }
}
