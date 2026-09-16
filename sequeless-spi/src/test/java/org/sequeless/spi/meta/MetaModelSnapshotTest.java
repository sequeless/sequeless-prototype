package org.sequeless.spi.meta;

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
import org.sequeless.spi.ontology.OntologyReport;

class MetaModelSnapshotTest {

    private static final OntologyReport CONSISTENT = new OntologyReport(true, List.of());

    private static TypeDefinition typeWithIri(String iri) {
        return new TypeDefinition(iri, "Label", List.of(), List.of(), DisplayHints.none(), false, Optional.empty());
    }

    private static MetaModelSnapshot snapshot(List<TypeDefinition> types) {
        return new MetaModelSnapshot(
            "https://example.org/ns", Optional.empty(), Map.of(), types, CONSISTENT);
    }

    @Test
    void shortNameIsDerivedFromTheHashFragment() {
        MetaModelSnapshot snapshot = snapshot(List.of(typeWithIri("https://example.org/ns#Task")));

        assertThat(snapshot.names()).containsExactly("Task");
        assertThat(snapshot.typeByName("Task")).isPresent();
    }

    @Test
    void shortNameFallsBackToTheLastSlashSegmentWhenNoHash() {
        MetaModelSnapshot snapshot = snapshot(List.of(typeWithIri("https://example.org/ns/Task")));

        assertThat(snapshot.names()).containsExactly("Task");
    }

    @Test
    void duplicateShortNameThrowsNamingBothIris() {
        List<TypeDefinition> types = List.of(
            typeWithIri("https://example.org/ns#Task"),
            typeWithIri("https://example.org/other#Task"));

        assertThatIllegalArgumentException()
            .isThrownBy(() -> snapshot(types))
            .withMessageContaining("https://example.org/ns#Task")
            .withMessageContaining("https://example.org/other#Task");
    }

    @Test
    void typeLooksUpByFullIri() {
        TypeDefinition task = typeWithIri("https://example.org/ns#Task");
        MetaModelSnapshot snapshot = snapshot(List.of(task));

        assertThat(snapshot.type("https://example.org/ns#Task")).contains(task);
        assertThat(snapshot.type("https://example.org/ns#Missing")).isEmpty();
    }

    @Test
    void typeByNameLooksUpByShortName() {
        TypeDefinition task = typeWithIri("https://example.org/ns#Task");
        MetaModelSnapshot snapshot = snapshot(List.of(task));

        assertThat(snapshot.typeByName("Task")).contains(task);
        assertThat(snapshot.typeByName("Missing")).isEmpty();
    }

    @Test
    void namesReturnsEveryShortNameInTypeOrder() {
        MetaModelSnapshot snapshot = snapshot(List.of(
            typeWithIri("https://example.org/ns#Task"),
            typeWithIri("https://example.org/ns#Project")));

        assertThat(snapshot.names()).containsExactly("Task", "Project");
    }

    @Test
    void rejectsNullOrBlankOntologyIri() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new MetaModelSnapshot(null, Optional.empty(), Map.of(), List.of(), CONSISTENT));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new MetaModelSnapshot("  ", Optional.empty(), Map.of(), List.of(), CONSISTENT));
    }

    @Test
    void rejectsNullVersionIriWrapper() {
        assertThatNullPointerException()
            .isThrownBy(() -> new MetaModelSnapshot("https://example.org/ns", null, Map.of(), List.of(), CONSISTENT));
    }

    @Test
    void rejectsNullPrefixes() {
        assertThatNullPointerException()
            .isThrownBy(() -> new MetaModelSnapshot(
                "https://example.org/ns", Optional.empty(), null, List.of(), CONSISTENT));
    }

    @Test
    void rejectsNullTypes() {
        assertThatNullPointerException()
            .isThrownBy(() -> new MetaModelSnapshot(
                "https://example.org/ns", Optional.empty(), Map.of(), null, CONSISTENT));
    }

    @Test
    void rejectsNullReport() {
        assertThatNullPointerException()
            .isThrownBy(() -> new MetaModelSnapshot(
                "https://example.org/ns", Optional.empty(), Map.of(), List.of(), null));
    }

    @Test
    void prefixesAreUnmodifiableAndUnaffectedByLaterMutationOfCallerMap() {
        Map<String, String> callerPrefixes = new HashMap<>();
        callerPrefixes.put("sq", "https://sequeless.dev/ns/meta#");

        MetaModelSnapshot snapshot = new MetaModelSnapshot(
            "https://example.org/ns", Optional.empty(), callerPrefixes, List.of(), CONSISTENT);
        callerPrefixes.put("ex", "https://example.org/ns#");

        assertThat(snapshot.prefixes()).containsOnlyKeys("sq");
        assertThatThrownBy(() -> snapshot.prefixes().put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void typesAreUnmodifiableAndUnaffectedByLaterMutationOfCallerList() {
        List<TypeDefinition> callerTypes = new ArrayList<>();
        callerTypes.add(typeWithIri("https://example.org/ns#Task"));

        MetaModelSnapshot snapshot = new MetaModelSnapshot(
            "https://example.org/ns", Optional.empty(), Map.of(), callerTypes, CONSISTENT);
        callerTypes.add(typeWithIri("https://example.org/ns#Project"));

        assertThat(snapshot.types()).hasSize(1);
        assertThatThrownBy(() -> snapshot.types().add(typeWithIri("x")))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
