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
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyAdministration;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyReport;

/**
 * Unit tests for {@link DefaultOntologyAdministration}, mirroring {@link DefaultWhoAmITest}'s shape:
 * {@link AuthorizationPort} is a functional interface exercised via plain lambdas, and {@link
 * FakeOntologyAdministration} is a small hand-written stand-in for the spi {@link
 * OntologyAdministration} port. Core must not depend on the testkit module at all.
 */
class DefaultOntologyAdministrationTest {

    private static final Scope SCOPE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));

    private static final OntologyDocument DOCUMENT =
            new OntologyDocument("@prefix sq: <https://sequeless.org/vocab/sq#> .", OntologyFormat.TURTLE);

    private static final ImportReport IMPORT_REPORT = new ImportReport(true, new OntologyReport(true, List.of()), 3);

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static AuthorizationPort denyAll() {
        return (scope, operation, resource) -> AccessDecision.deny("nope");
    }

    // --- export ---

    @Test
    void exportDelegatesToOntologyAdministrationExportWithTurtleFormatOnPermit() {
        FakeOntologyAdministration administration = new FakeOntologyAdministration();
        DefaultOntologyAdministration service = new DefaultOntologyAdministration(administration, permitAll());

        OntologyDocument result = service.export(SCOPE);

        assertThat(result).isEqualTo(DOCUMENT);
        assertThat(administration.exportFormats).containsExactly(OntologyFormat.TURTLE);
    }

    @Test
    void exportConsultsPortWithAdminOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort = (scope, operation, resource) -> {
            capturedOperations.add(operation);
            capturedResources.add(resource);
            return AccessDecision.permit("ok");
        };
        DefaultOntologyAdministration service =
                new DefaultOntologyAdministration(new FakeOntologyAdministration(), authorizationPort);

        service.export(SCOPE);

        assertThat(capturedOperations).containsExactly(Operation.ADMIN);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void exportThrowsWithPortsOwnDecisionOnDenyAndNeverCallsOntologyAdministration() {
        FakeOntologyAdministration administration = new FakeOntologyAdministration();
        AccessDecision denial = AccessDecision.deny("no access");
        DefaultOntologyAdministration service =
                new DefaultOntologyAdministration(administration, (scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.export(SCOPE))
                .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(administration.exportFormats).isEmpty();
    }

    @Test
    void exportRejectsNullScope() {
        DefaultOntologyAdministration service =
                new DefaultOntologyAdministration(new FakeOntologyAdministration(), permitAll());

        assertThatNullPointerException().isThrownBy(() -> service.export(null));
    }

    // --- importTurtle ---

    @Test
    void importTurtleBuildsDocumentAndDelegatesWithReplaceModeOnPermit() {
        FakeOntologyAdministration administration = new FakeOntologyAdministration();
        DefaultOntologyAdministration service = new DefaultOntologyAdministration(administration, permitAll());

        ImportReport result = service.importTurtle(SCOPE, "@prefix sq: <urn:x> .");

        assertThat(result).isEqualTo(IMPORT_REPORT);
        assertThat(administration.importedDocuments)
                .containsExactly(new OntologyDocument("@prefix sq: <urn:x> .", OntologyFormat.TURTLE));
        assertThat(administration.importedModes).containsExactly(ImportMode.REPLACE);
    }

    @Test
    void importTurtleConsultsPortWithAdminOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort = (scope, operation, resource) -> {
            capturedOperations.add(operation);
            capturedResources.add(resource);
            return AccessDecision.permit("ok");
        };
        DefaultOntologyAdministration service =
                new DefaultOntologyAdministration(new FakeOntologyAdministration(), authorizationPort);

        service.importTurtle(SCOPE, "@prefix sq: <urn:x> .");

        assertThat(capturedOperations).containsExactly(Operation.ADMIN);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void importTurtleThrowsWithPortsOwnDecisionOnDenyAndNeverCallsOntologyAdministration() {
        FakeOntologyAdministration administration = new FakeOntologyAdministration();
        AccessDecision denial = AccessDecision.deny("no access");
        DefaultOntologyAdministration service =
                new DefaultOntologyAdministration(administration, (scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.importTurtle(SCOPE, "@prefix sq: <urn:x> ."))
                .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(administration.importedDocuments).isEmpty();
    }

    @Test
    void importTurtleRejectsNullArguments() {
        DefaultOntologyAdministration service =
                new DefaultOntologyAdministration(new FakeOntologyAdministration(), permitAll());

        assertThatNullPointerException().isThrownBy(() -> service.importTurtle(null, "@prefix sq: <urn:x> ."));
        assertThatNullPointerException().isThrownBy(() -> service.importTurtle(SCOPE, null));
    }

    // --- constructor ---

    @Test
    void constructorRejectsNullOntologyAdministration() {
        assertThatNullPointerException().isThrownBy(() -> new DefaultOntologyAdministration(null, permitAll()));
    }

    @Test
    void constructorRejectsNullAuthorizationPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultOntologyAdministration(new FakeOntologyAdministration(), null));
    }

    /**
     * Hand-written spi {@link OntologyAdministration} double; {@code export} and {@code
     * importDocument} are exercised by these tests, so only {@code reload} is unimplemented.
     */
    private static final class FakeOntologyAdministration implements OntologyAdministration {

        private final List<OntologyFormat> exportFormats = new ArrayList<>();
        private final List<OntologyDocument> importedDocuments = new ArrayList<>();
        private final List<ImportMode> importedModes = new ArrayList<>();

        @Override
        public OntologyReport reload(Scope scope) {
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
