package org.sequeless.spi.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttributeDefinitionTest {

    private static AttributeDefinition sample() {
        return new AttributeDefinition(
            "https://example.org/ns#title",
            "Title",
            Cardinality.optional(),
            false,
            false,
            false,
            false,
            false,
            DisplayHints.none(),
            Optional.empty(),
            Datatype.STRING);
    }

    @Test
    void ordersDelegatesToDisplayHints() {
        AttributeDefinition attribute = new AttributeDefinition(
            "https://example.org/ns#title",
            "Title",
            Cardinality.optional(),
            false,
            false,
            false,
            false,
            false,
            new DisplayHints(2, Optional.empty(), false),
            Optional.empty(),
            Datatype.STRING);

        assertThat(attribute.order()).isEqualTo(2);
    }

    @Test
    void rejectsNullOrBlankIri() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AttributeDefinition(
                null, "Title", Cardinality.optional(), false, false, false, false, false,
                DisplayHints.none(), Optional.empty(), Datatype.STRING));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AttributeDefinition(
                "  ", "Title", Cardinality.optional(), false, false, false, false, false,
                DisplayHints.none(), Optional.empty(), Datatype.STRING));
    }

    @Test
    void rejectsNullOrBlankLabel() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AttributeDefinition(
                "https://example.org/ns#title", null, Cardinality.optional(), false, false, false,
                false, false, DisplayHints.none(), Optional.empty(), Datatype.STRING));
    }

    @Test
    void rejectsNullCardinality() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AttributeDefinition(
                "https://example.org/ns#title", "Title", null, false, false, false, false, false,
                DisplayHints.none(), Optional.empty(), Datatype.STRING));
    }

    @Test
    void rejectsNullDisplayHints() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AttributeDefinition(
                "https://example.org/ns#title", "Title", Cardinality.optional(), false, false,
                false, false, false, null, Optional.empty(), Datatype.STRING));
    }

    @Test
    void rejectsNullDerivationWrapper() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AttributeDefinition(
                "https://example.org/ns#title", "Title", Cardinality.optional(), false, false,
                false, false, false, DisplayHints.none(), null, Datatype.STRING));
    }

    @Test
    void rejectsNullDatatype() {
        assertThatNullPointerException()
            .isThrownBy(() -> new AttributeDefinition(
                "https://example.org/ns#title", "Title", Cardinality.optional(), false, false,
                false, false, false, DisplayHints.none(), Optional.empty(), null));
    }

    @Test
    void isUsableAsAPropertyDefinition() {
        PropertyDefinition property = sample();

        assertThat(property.iri()).isEqualTo("https://example.org/ns#title");
        assertThat(property).isInstanceOf(AttributeDefinition.class);
    }
}
