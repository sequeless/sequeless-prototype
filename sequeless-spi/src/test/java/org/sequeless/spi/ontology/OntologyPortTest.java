package org.sequeless.spi.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.meta.MetaModelSnapshot;

/**
 * Unit-level tests for the {@link OntologyPort} interface shape itself. The full behavioural
 * contract every implementation must satisfy (null handling, determinism, reload semantics,
 * export/import round-tripping, throw-on-inconsistency) is asserted mechanically against arbitrary
 * implementations by {@code sequeless-spi-testkit}'s {@code OntologyContract}, not repeated here.
 */
class OntologyPortTest {

    private static final MetaModelSnapshot SNAPSHOT = new MetaModelSnapshot(
        "https://example.org/ns", Optional.empty(), Map.of(), List.of(), new OntologyReport(true, List.of()));

    private static final OntologyDocument DOCUMENT =
        new OntologyDocument("@prefix ex: <https://example.org/> .", OntologyFormat.TURTLE);

    @Test
    void isUsableAsAFourMethodImplementation() {
        OntologyPort port = new OntologyPort() {
            @Override
            public MetaModelSnapshot snapshot(Scope scope) {
                Objects.requireNonNull(scope, "scope must not be null");
                return SNAPSHOT;
            }

            @Override
            public MetaModelSnapshot reload(Scope scope) {
                Objects.requireNonNull(scope, "scope must not be null");
                return SNAPSHOT;
            }

            @Override
            public OntologyDocument export(Scope scope, OntologyFormat format) {
                Objects.requireNonNull(scope, "scope must not be null");
                Objects.requireNonNull(format, "format must not be null");
                return DOCUMENT;
            }

            @Override
            public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
                Objects.requireNonNull(scope, "scope must not be null");
                Objects.requireNonNull(document, "document must not be null");
                Objects.requireNonNull(mode, "mode must not be null");
                return new ImportReport(true, new OntologyReport(true, List.of()), 0);
            }
        };

        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

        assertThat(port.snapshot(scope)).isEqualTo(SNAPSHOT);
        assertThat(port.reload(scope)).isEqualTo(SNAPSHOT);
        assertThat(port.export(scope, OntologyFormat.TURTLE)).isEqualTo(DOCUMENT);
        assertThat(port.importDocument(scope, DOCUMENT, ImportMode.REPLACE).accepted()).isTrue();
    }

    @Test
    void aConformingImplementationRejectsNullArguments() {
        OntologyPort port = new OntologyPort() {
            @Override
            public MetaModelSnapshot snapshot(Scope scope) {
                Objects.requireNonNull(scope, "scope must not be null");
                return SNAPSHOT;
            }

            @Override
            public MetaModelSnapshot reload(Scope scope) {
                Objects.requireNonNull(scope, "scope must not be null");
                return SNAPSHOT;
            }

            @Override
            public OntologyDocument export(Scope scope, OntologyFormat format) {
                Objects.requireNonNull(scope, "scope must not be null");
                Objects.requireNonNull(format, "format must not be null");
                return DOCUMENT;
            }

            @Override
            public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
                Objects.requireNonNull(scope, "scope must not be null");
                Objects.requireNonNull(document, "document must not be null");
                Objects.requireNonNull(mode, "mode must not be null");
                return new ImportReport(true, new OntologyReport(true, List.of()), 0);
            }
        };
        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

        assertThatNullPointerException().isThrownBy(() -> port.snapshot(null));
        assertThatNullPointerException().isThrownBy(() -> port.reload(null));
        assertThatNullPointerException().isThrownBy(() -> port.export(null, OntologyFormat.TURTLE));
        assertThatNullPointerException().isThrownBy(() -> port.export(scope, null));
        assertThatNullPointerException()
            .isThrownBy(() -> port.importDocument(null, DOCUMENT, ImportMode.REPLACE));
        assertThatNullPointerException()
            .isThrownBy(() -> port.importDocument(scope, null, ImportMode.REPLACE));
        assertThatNullPointerException().isThrownBy(() -> port.importDocument(scope, DOCUMENT, null));
    }
}
