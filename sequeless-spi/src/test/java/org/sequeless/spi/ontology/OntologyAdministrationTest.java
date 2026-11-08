package org.sequeless.spi.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;

/**
 * Unit-level tests for the {@link OntologyAdministration} interface shape itself. The full
 * behavioural contract every implementation must satisfy (null handling, reload semantics,
 * export/import round-tripping, versions, failure behaviour, throw-on-inconsistency) is asserted
 * mechanically against arbitrary implementations by {@code sequeless-spi-testkit}'s {@code
 * OntologyContract}, not repeated here.
 */
class OntologyAdministrationTest {

    private static final OntologyReport REPORT = new OntologyReport(true, List.of());

    private static final OntologyDocument DOCUMENT =
            new OntologyDocument("@prefix ex: <https://example.org/> .", OntologyFormat.TURTLE);

    @Test
    void isUsableAsAThreeMethodImplementation() {
        OntologyAdministration administration = new OntologyAdministration() {
            @Override
            public OntologyReport reload(Scope scope) {
                Objects.requireNonNull(scope, "scope must not be null");
                return REPORT;
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
                return new ImportReport(true, REPORT, 0);
            }
        };

        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

        assertThat(administration.reload(scope)).isEqualTo(REPORT);
        assertThat(administration.export(scope, OntologyFormat.TURTLE)).isEqualTo(DOCUMENT);
        assertThat(administration
                        .importDocument(scope, DOCUMENT, ImportMode.REPLACE)
                        .accepted())
                .isTrue();
    }

    @Test
    void aConformingImplementationRejectsNullArguments() {
        OntologyAdministration administration = new OntologyAdministration() {
            @Override
            public OntologyReport reload(Scope scope) {
                Objects.requireNonNull(scope, "scope must not be null");
                return REPORT;
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
                return new ImportReport(true, REPORT, 0);
            }
        };
        Scope scope = new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);

        assertThatNullPointerException().isThrownBy(() -> administration.reload(null));
        assertThatNullPointerException().isThrownBy(() -> administration.export(null, OntologyFormat.TURTLE));
        assertThatNullPointerException().isThrownBy(() -> administration.export(scope, null));
        assertThatNullPointerException()
                .isThrownBy(() -> administration.importDocument(null, DOCUMENT, ImportMode.REPLACE));
        assertThatNullPointerException()
                .isThrownBy(() -> administration.importDocument(scope, null, ImportMode.REPLACE));
        assertThatNullPointerException().isThrownBy(() -> administration.importDocument(scope, DOCUMENT, null));
    }
}
