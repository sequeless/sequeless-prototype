package org.sequeless.core.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.core.AuthorizationException;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.ontology.OntologyReport;

/**
 * Unit tests for {@link DefaultOntologyAdministration}, mirroring {@link DefaultWhoAmITest}'s
 * shape: {@link AuthorizationPort} is a functional interface exercised via plain lambdas, and
 * {@link FakeOntologyPort} is a small hand-written stand-in for {@link OntologyPort}. Core must not
 * depend on the testkit module at all.
 */
class DefaultOntologyAdministrationTest {

    private static final Scope SCOPE =
        new Scope(new TenantId("acme"), new Principal("alice", "Alice", Set.of("member")));

    private static final OntologyDocument DOCUMENT =
        new OntologyDocument("@prefix sq: <https://sequeless.org/vocab/sq#> .", OntologyFormat.TURTLE);

    private static final ImportReport IMPORT_REPORT =
        new ImportReport(true, new OntologyReport(true, List.of()), 3);

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static AuthorizationPort denyAll() {
        return (scope, operation, resource) -> AccessDecision.deny("nope");
    }

    // --- export ---

    @Test
    void exportDelegatesToOntologyPortExportWithTurtleFormatOnPermit() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort();
        DefaultOntologyAdministration administration =
            new DefaultOntologyAdministration(ontologyPort, permitAll());

        OntologyDocument result = administration.export(SCOPE);

        assertThat(result).isEqualTo(DOCUMENT);
        assertThat(ontologyPort.exportFormats).containsExactly(OntologyFormat.TURTLE);
    }

    @Test
    void exportConsultsPortWithAdminOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort =
            (scope, operation, resource) -> {
                capturedOperations.add(operation);
                capturedResources.add(resource);
                return AccessDecision.permit("ok");
            };
        DefaultOntologyAdministration administration =
            new DefaultOntologyAdministration(new FakeOntologyPort(), authorizationPort);

        administration.export(SCOPE);

        assertThat(capturedOperations).containsExactly(Operation.ADMIN);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void exportThrowsWithPortsOwnDecisionOnDenyAndNeverCallsOntologyPort() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort();
        AccessDecision denial = AccessDecision.deny("no access");
        DefaultOntologyAdministration administration =
            new DefaultOntologyAdministration(ontologyPort, (scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> administration.export(SCOPE))
            .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(ontologyPort.exportFormats).isEmpty();
    }

    @Test
    void exportRejectsNullScope() {
        DefaultOntologyAdministration administration =
            new DefaultOntologyAdministration(new FakeOntologyPort(), permitAll());

        assertThatNullPointerException().isThrownBy(() -> administration.export(null));
    }

    // --- importTurtle ---

    @Test
    void importTurtleBuildsDocumentAndDelegatesWithReplaceModeOnPermit() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort();
        DefaultOntologyAdministration administration =
            new DefaultOntologyAdministration(ontologyPort, permitAll());

        ImportReport result = administration.importTurtle(SCOPE, "@prefix sq: <urn:x> .");

        assertThat(result).isEqualTo(IMPORT_REPORT);
        assertThat(ontologyPort.importedDocuments)
            .containsExactly(new OntologyDocument("@prefix sq: <urn:x> .", OntologyFormat.TURTLE));
        assertThat(ontologyPort.importedModes).containsExactly(ImportMode.REPLACE);
    }

    @Test
    void importTurtleConsultsPortWithAdminOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort =
            (scope, operation, resource) -> {
                capturedOperations.add(operation);
                capturedResources.add(resource);
                return AccessDecision.permit("ok");
            };
        DefaultOntologyAdministration administration =
            new DefaultOntologyAdministration(new FakeOntologyPort(), authorizationPort);

        administration.importTurtle(SCOPE, "@prefix sq: <urn:x> .");

        assertThat(capturedOperations).containsExactly(Operation.ADMIN);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void importTurtleThrowsWithPortsOwnDecisionOnDenyAndNeverCallsOntologyPort() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort();
        AccessDecision denial = AccessDecision.deny("no access");
        DefaultOntologyAdministration administration =
            new DefaultOntologyAdministration(ontologyPort, (scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> administration.importTurtle(SCOPE, "@prefix sq: <urn:x> ."))
            .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(ontologyPort.importedDocuments).isEmpty();
    }

    @Test
    void importTurtleRejectsNullArguments() {
        DefaultOntologyAdministration administration =
            new DefaultOntologyAdministration(new FakeOntologyPort(), permitAll());

        assertThatNullPointerException()
            .isThrownBy(() -> administration.importTurtle(null, "@prefix sq: <urn:x> ."));
        assertThatNullPointerException()
            .isThrownBy(() -> administration.importTurtle(SCOPE, null));
    }

    // --- constructor ---

    @Test
    void constructorRejectsNullOntologyPort() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultOntologyAdministration(null, permitAll()));
    }

    @Test
    void constructorRejectsNullAuthorizationPort() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultOntologyAdministration(new FakeOntologyPort(), null));
    }

    /**
     * Hand-written {@link OntologyPort} double; only {@code export} and {@code importDocument} are
     * exercised by these tests, so {@code snapshot} and {@code reload} are unimplemented.
     */
    private static final class FakeOntologyPort implements OntologyPort {

        private final List<OntologyFormat> exportFormats = new ArrayList<>();
        private final List<OntologyDocument> importedDocuments = new ArrayList<>();
        private final List<ImportMode> importedModes = new ArrayList<>();

        @Override
        public MetaModelSnapshot snapshot(Scope scope) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public MetaModelSnapshot reload(Scope scope) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public OntologyDocument export(Scope scope, OntologyFormat format) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(format, "format must not be null");
            exportFormats.add(format);
            return DOCUMENT;
        }

        @Override
        public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(document, "document must not be null");
            Objects.requireNonNull(mode, "mode must not be null");
            importedDocuments.add(document);
            importedModes.add(mode);
            return IMPORT_REPORT;
        }
    }
}
