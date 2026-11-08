package org.sequeless.spi.meta.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchemaTest {

    private static final OntologyVersion VERSION = new OntologyVersion(1);

    private static TypeDefinition typeWithIri(String iri) {
        return new TypeDefinition(iri, "Label", List.of(), List.of(), DisplayHints.none(), false);
    }

    private static Schema schema(List<TypeDefinition> types) {
        return new Schema(VERSION, "https://example.org/ns", Optional.empty(), Map.of(), types);
    }

    @Test
    void carriesTheVersionOfTheActivationItWasReadFrom() {
        assertThat(schema(List.of()).ontologyVersion()).isEqualTo(VERSION);
    }

    @Test
    void rejectsNullOntologyVersion() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Schema(null, "https://example.org/ns", Optional.empty(), Map.of(), List.of()));
    }

    @Test
    void shortNameIsDerivedFromTheHashFragment() {
        Schema schema = schema(List.of(typeWithIri("https://example.org/ns#Task")));

        assertThat(schema.names()).containsExactly("Task");
        assertThat(schema.typeByName("Task")).isPresent();
    }

    @Test
    void shortNameFallsBackToTheLastSlashSegmentWhenNoHash() {
        Schema schema = schema(List.of(typeWithIri("https://example.org/ns/Task")));

        assertThat(schema.names()).containsExactly("Task");
    }

    @Test
    void duplicateShortNameThrowsNamingBothIris() {
        List<TypeDefinition> types =
                List.of(typeWithIri("https://example.org/ns#Task"), typeWithIri("https://example.org/other#Task"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> schema(types))
                .withMessageContaining("https://example.org/ns#Task")
                .withMessageContaining("https://example.org/other#Task");
    }

    @Test
    void typeLooksUpByFullIri() {
        TypeDefinition task = typeWithIri("https://example.org/ns#Task");
        Schema schema = schema(List.of(task));

        assertThat(schema.type("https://example.org/ns#Task")).contains(task);
        assertThat(schema.type("https://example.org/ns#Missing")).isEmpty();
    }

    @Test
    void typeByNameLooksUpByShortName() {
        TypeDefinition task = typeWithIri("https://example.org/ns#Task");
        Schema schema = schema(List.of(task));

        assertThat(schema.typeByName("Task")).contains(task);
        assertThat(schema.typeByName("Missing")).isEmpty();
    }

    @Test
    void namesReturnsEveryShortNameInTypeOrder() {
        Schema schema = schema(
                List.of(typeWithIri("https://example.org/ns#Task"), typeWithIri("https://example.org/ns#Project")));

        assertThat(schema.names()).containsExactly("Task", "Project");
    }

    @Test
    void rejectsNullOrBlankOntologyIri() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Schema(VERSION, null, Optional.empty(), Map.of(), List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Schema(VERSION, "  ", Optional.empty(), Map.of(), List.of()));
    }

    @Test
    void rejectsNullVersionIriWrapper() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Schema(VERSION, "https://example.org/ns", null, Map.of(), List.of()));
    }

    @Test
    void rejectsNullPrefixes() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Schema(VERSION, "https://example.org/ns", Optional.empty(), null, List.of()));
    }

    @Test
    void rejectsNullTypes() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Schema(VERSION, "https://example.org/ns", Optional.empty(), Map.of(), null));
    }

    @Test
    void prefixesAreUnmodifiableAndUnaffectedByLaterMutationOfCallerMap() {
        Map<String, String> callerPrefixes = new HashMap<>();
        callerPrefixes.put("sq", "https://sequeless.dev/ns/meta#");

        Schema schema = new Schema(VERSION, "https://example.org/ns", Optional.empty(), callerPrefixes, List.of());
        callerPrefixes.put("ex", "https://example.org/ns#");

        assertThat(schema.prefixes()).containsOnlyKeys("sq");
        assertThatThrownBy(() -> schema.prefixes().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void typesAreUnmodifiableAndUnaffectedByLaterMutationOfCallerList() {
        List<TypeDefinition> callerTypes = new ArrayList<>();
        callerTypes.add(typeWithIri("https://example.org/ns#Task"));

        Schema schema = new Schema(VERSION, "https://example.org/ns", Optional.empty(), Map.of(), callerTypes);
        callerTypes.add(typeWithIri("https://example.org/ns#Project"));

        assertThat(schema.types()).hasSize(1);
        assertThatThrownBy(() -> schema.types().add(typeWithIri("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
