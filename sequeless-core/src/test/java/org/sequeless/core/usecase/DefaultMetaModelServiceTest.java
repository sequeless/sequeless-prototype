package org.sequeless.core.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.meta.DisplayHints;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.ontology.OntologyReport;

/**
 * Unit tests for {@link DefaultMetaModelService}. Neither {@link OntologyPort} nor {@link
 * AuthorizationPort} is mocked with a framework — {@code AuthorizationPort} is a functional
 * interface so its doubles are plain lambdas, and {@link FakeOntologyPort} is a small
 * hand-written stand-in, following the same idiom as {@link DefaultWhoAmITest}. Core also must
 * not depend on the testkit module at all.
 */
class DefaultMetaModelServiceTest {

    private static final Scope SCOPE =
        new Scope(new TenantId("acme"), new Principal("alice", "Alice", Set.of("member")));

    private static final String TASK_IRI = "https://sequeless.org/vocab/sq#Task";
    private static final String PROJECT_IRI = "https://sequeless.org/vocab/sq#Project";

    private static final TypeDefinition TASK =
        new TypeDefinition(
            TASK_IRI, "Task", List.of(), List.of(), DisplayHints.none(), false, Optional.empty());

    private static final TypeDefinition PROJECT =
        new TypeDefinition(
            PROJECT_IRI,
            "Project",
            List.of(),
            List.of(),
            DisplayHints.none(),
            false,
            Optional.empty());

    private static final MetaModelSnapshot SNAPSHOT =
        new MetaModelSnapshot(
            "https://sequeless.org/vocab/sq",
            Optional.empty(),
            Map.of(),
            List.of(TASK, PROJECT),
            new OntologyReport(true, List.of()));

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static AuthorizationPort denyAll() {
        return (scope, operation, resource) -> AccessDecision.deny("nope");
    }

    // --- snapshot ---

    @Test
    void snapshotReturnsPortsSnapshotOnPermit() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort(SNAPSHOT);
        DefaultMetaModelService service = new DefaultMetaModelService(ontologyPort, permitAll());

        MetaModelSnapshot result = service.snapshot(SCOPE);

        assertThat(result).isEqualTo(SNAPSHOT);
        assertThat(ontologyPort.snapshotCalls).isEqualTo(1);
    }

    @Test
    void snapshotConsultsPortWithBrowseOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort =
            (scope, operation, resource) -> {
                capturedOperations.add(operation);
                capturedResources.add(resource);
                return AccessDecision.permit("ok");
            };
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), authorizationPort);

        service.snapshot(SCOPE);

        assertThat(capturedOperations).containsExactly(Operation.BROWSE);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void snapshotThrowsWithPortsOwnDecisionOnDenyAndDoesNotCallOntologyPort() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort(SNAPSHOT);
        AccessDecision denial = AccessDecision.deny("no access");
        DefaultMetaModelService service =
            new DefaultMetaModelService(ontologyPort, (scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.snapshot(SCOPE))
            .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(ontologyPort.snapshotCalls).isZero();
    }

    @Test
    void snapshotRejectsNullScope() {
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), permitAll());

        assertThatNullPointerException().isThrownBy(() -> service.snapshot(null));
    }

    // --- describeType ---

    @Test
    void describeTypeResolvesByShortName() {
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), permitAll());

        TypeDefinition result = service.describeType(SCOPE, "Task");

        assertThat(result).isEqualTo(TASK);
    }

    @Test
    void describeTypeResolvesByFullIri() {
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), permitAll());

        TypeDefinition result = service.describeType(SCOPE, TASK_IRI);

        assertThat(result).isEqualTo(TASK);
    }

    @Test
    void describeTypeThrowsTypeNotFoundForUnknownName() {
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), permitAll());

        assertThatExceptionOfType(TypeNotFoundException.class)
            .isThrownBy(() -> service.describeType(SCOPE, "NoSuchType"));
    }

    @Test
    void describeTypeConsultsPortWithReadOperationAndResolvedTypeIri() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort =
            (scope, operation, resource) -> {
                capturedOperations.add(operation);
                capturedResources.add(resource);
                return AccessDecision.permit("ok");
            };
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), authorizationPort);

        service.describeType(SCOPE, "Task");

        assertThat(capturedOperations).containsExactly(Operation.READ);
        assertThat(capturedResources).containsExactly(TASK_IRI);
    }

    @Test
    void describeTypeThrowsWithPortsOwnDecisionOnDeny() {
        AccessDecision denial = AccessDecision.deny("no access");
        DefaultMetaModelService service =
            new DefaultMetaModelService(
                new FakeOntologyPort(SNAPSHOT), (scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.describeType(SCOPE, "Task"))
            .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
    }

    @Test
    void describeTypeThrowsTypeNotFoundBeforeConsultingAuthorizationPortForUnknownName() {
        List<Operation> capturedOperations = new ArrayList<>();
        AuthorizationPort authorizationPort =
            (scope, operation, resource) -> {
                capturedOperations.add(operation);
                return AccessDecision.permit("ok");
            };
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), authorizationPort);

        assertThatExceptionOfType(TypeNotFoundException.class)
            .isThrownBy(() -> service.describeType(SCOPE, "NoSuchType"));
        assertThat(capturedOperations).isEmpty();
    }

    @Test
    void describeTypeRejectsNullScope() {
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), permitAll());

        assertThatNullPointerException().isThrownBy(() -> service.describeType(null, "Task"));
    }

    @Test
    void describeTypeRejectsNullNameOrIri() {
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), permitAll());

        assertThatNullPointerException().isThrownBy(() -> service.describeType(SCOPE, null));
    }

    // --- reload ---

    @Test
    void reloadReturnsPortsSnapshotOnPermit() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort(SNAPSHOT);
        DefaultMetaModelService service = new DefaultMetaModelService(ontologyPort, permitAll());

        MetaModelSnapshot result = service.reload(SCOPE);

        assertThat(result).isEqualTo(SNAPSHOT);
        assertThat(ontologyPort.reloadCalls).isEqualTo(1);
    }

    @Test
    void reloadConsultsPortWithAdminOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort =
            (scope, operation, resource) -> {
                capturedOperations.add(operation);
                capturedResources.add(resource);
                return AccessDecision.permit("ok");
            };
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), authorizationPort);

        service.reload(SCOPE);

        assertThat(capturedOperations).containsExactly(Operation.ADMIN);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void reloadThrowsWithPortsOwnDecisionOnDenyAndDoesNotCallOntologyPort() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort(SNAPSHOT);
        AccessDecision denial = AccessDecision.deny("no access");
        DefaultMetaModelService service =
            new DefaultMetaModelService(ontologyPort, (scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.reload(SCOPE))
            .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(ontologyPort.reloadCalls).isZero();
    }

    @Test
    void reloadRejectsNullScope() {
        DefaultMetaModelService service =
            new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), permitAll());

        assertThatNullPointerException().isThrownBy(() -> service.reload(null));
    }

    // --- deny-all across all three methods ---

    @Test
    void denyAllAuthorizationPortDeniesAllThreeMethods() {
        FakeOntologyPort ontologyPort = new FakeOntologyPort(SNAPSHOT);
        DefaultMetaModelService service = new DefaultMetaModelService(ontologyPort, denyAll());

        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.snapshot(SCOPE));
        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.describeType(SCOPE, "Task"));
        assertThatExceptionOfType(AuthorizationException.class)
            .isThrownBy(() -> service.reload(SCOPE));

        // describeType resolves against the snapshot before authorizing (see its javadoc), so
        // the one snapshot() call above comes from describeType's resolution step, not from
        // snapshot() itself, which must never reach the ontology port when denied.
        assertThat(ontologyPort.snapshotCalls).isEqualTo(1);
        assertThat(ontologyPort.reloadCalls).isZero();
    }

    // --- constructor ---

    @Test
    void constructorRejectsNullOntologyPort() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultMetaModelService(null, permitAll()));
    }

    @Test
    void constructorRejectsNullAuthorizationPort() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultMetaModelService(new FakeOntologyPort(SNAPSHOT), null));
    }

    /** Hand-written {@link OntologyPort} double; only {@code snapshot} and {@code reload} are
     * exercised by these tests, so {@code export} and {@code importDocument} are unimplemented. */
    private static final class FakeOntologyPort implements OntologyPort {

        private final MetaModelSnapshot snapshot;
        private int snapshotCalls;
        private int reloadCalls;

        FakeOntologyPort(MetaModelSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public MetaModelSnapshot snapshot(Scope scope) {
            java.util.Objects.requireNonNull(scope, "scope must not be null");
            snapshotCalls++;
            return snapshot;
        }

        @Override
        public MetaModelSnapshot reload(Scope scope) {
            java.util.Objects.requireNonNull(scope, "scope must not be null");
            reloadCalls++;
            return snapshot;
        }

        @Override
        public OntologyDocument export(Scope scope, OntologyFormat format) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }

        @Override
        public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
            throw new UnsupportedOperationException("not exercised by these tests");
        }
    }
}
