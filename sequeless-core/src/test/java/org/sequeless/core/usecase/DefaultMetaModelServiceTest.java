package org.sequeless.core.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.TypeDescription;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.api.ImportMode;
import org.sequeless.ontology.api.ImportReport;
import org.sequeless.ontology.api.OntologyDocument;
import org.sequeless.ontology.api.OntologyFormat;
import org.sequeless.ontology.api.OntologyReport;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;

/**
 * Unit tests for {@link DefaultMetaModelService}. Neither the ports nor {@link AuthorizationPort} is
 * mocked with a framework: {@code AuthorizationPort} is a functional interface so its doubles are
 * plain lambdas, and {@link FakeOntology} is a small hand-written stand-in for the three ontology
 * ports, following the same idiom as {@link DefaultWhoAmITest}. Core also must not depend on the
 * testkit module at all.
 */
class DefaultMetaModelServiceTest {

    private static final Scope SCOPE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));

    private static final String TASK_IRI = "https://sequeless.org/vocab/sq#Task";
    private static final String PROJECT_IRI = "https://sequeless.org/vocab/sq#Project";
    private static final String TOTAL_IRI = "https://sequeless.org/vocab/sq#total";
    private static final String PROJECT_REF_IRI = "https://sequeless.org/vocab/sq#project";

    private static final TypeDefinition TASK =
            new TypeDefinition(TASK_IRI, "Task", List.of(), List.of(), DisplayHints.none(), false);

    private static final AttributeDefinition TOTAL = new AttributeDefinition(
            TOTAL_IRI,
            "total",
            new Cardinality(0, OptionalInt.of(1)),
            false,
            false,
            false,
            true,
            false,
            DisplayHints.none(),
            Datatype.INTEGER);

    private static final TypeDefinition PROJECT =
            new TypeDefinition(PROJECT_IRI, "Project", List.of(), List.of(TOTAL), DisplayHints.none(), false);

    private static final Schema SCHEMA = new Schema(
            new OntologyVersion(1),
            "https://sequeless.org/vocab/sq",
            Optional.empty(),
            Map.of(),
            List.of(TASK, PROJECT));

    private static final Behaviour BEHAVIOUR = new Behaviour(new OntologyVersion(1), Map.of(), Map.of());

    private static final RollupRule TOTAL_ROLLUP = new RollupRule(
            TASK_IRI, PROJECT_REF_IRI, AggregateFunction.COUNT, Optional.empty(), Optional.empty(), true);

    private static final Behaviour BEHAVIOUR_WITH_ROLLUP =
            new Behaviour(new OntologyVersion(1), Map.of(), Map.of(TOTAL_IRI, TOTAL_ROLLUP));

    private static final OntologyReport REPORT = new OntologyReport(true, List.of());

    private static AuthorizationPort permitAll() {
        return (scope, operation, resource) -> AccessDecision.permit("ok");
    }

    private static AuthorizationPort denyAll() {
        return (scope, operation, resource) -> AccessDecision.deny("nope");
    }

    private static DefaultMetaModelService service(FakeOntology ontology, AuthorizationPort authorizationPort) {
        return new DefaultMetaModelService(ontology, ontology, ontology, authorizationPort);
    }

    // --- schema ---

    @Test
    void schemaReturnsPortsSchemaOnPermit() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);

        Schema result = service(ontology, permitAll()).schema(SCOPE);

        assertThat(result).isEqualTo(SCHEMA);
        assertThat(ontology.schemaCalls).isEqualTo(1);
    }

    @Test
    void schemaConsultsPortWithBrowseOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort = (scope, operation, resource) -> {
            capturedOperations.add(operation);
            capturedResources.add(resource);
            return AccessDecision.permit("ok");
        };

        service(new FakeOntology(SCHEMA, BEHAVIOUR), authorizationPort).schema(SCOPE);

        assertThat(capturedOperations).containsExactly(Operation.BROWSE);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void schemaThrowsWithPortsOwnDecisionOnDenyAndDoesNotCallSchemaSource() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);
        AccessDecision denial = AccessDecision.deny("no access");

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service(ontology, (scope, operation, resource) -> denial)
                        .schema(SCOPE))
                .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(ontology.schemaCalls).isZero();
    }

    @Test
    void schemaRejectsNullScope() {
        assertThatNullPointerException()
                .isThrownBy(() -> service(new FakeOntology(SCHEMA, BEHAVIOUR), permitAll())
                        .schema(null));
    }

    // --- behaviour ---

    @Test
    void behaviourReturnsPortsBehaviourOnPermit() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);

        Behaviour result = service(ontology, permitAll()).behaviour(SCOPE);

        assertThat(result).isEqualTo(BEHAVIOUR);
        assertThat(ontology.behaviourCalls).isEqualTo(1);
    }

    @Test
    void behaviourConsultsPortWithBrowseOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort = (scope, operation, resource) -> {
            capturedOperations.add(operation);
            capturedResources.add(resource);
            return AccessDecision.permit("ok");
        };

        service(new FakeOntology(SCHEMA, BEHAVIOUR), authorizationPort).behaviour(SCOPE);

        assertThat(capturedOperations).containsExactly(Operation.BROWSE);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void behaviourRejectsNullScope() {
        assertThatNullPointerException()
                .isThrownBy(() -> service(new FakeOntology(SCHEMA, BEHAVIOUR), permitAll())
                        .behaviour(null));
    }

    @Test
    void behaviourThrowsWithPortsOwnDecisionOnDenyAndDoesNotCallBehaviourSource() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);
        AccessDecision denial = AccessDecision.deny("no access");

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service(ontology, (scope, operation, resource) -> denial)
                        .behaviour(SCOPE))
                .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(ontology.behaviourCalls).isZero();
    }

    // --- describeType ---

    @Test
    void describeTypeResolvesByShortName() {
        DefaultMetaModelService service = service(new FakeOntology(SCHEMA, BEHAVIOUR), permitAll());

        TypeDescription result = service.describeType(SCOPE, "Task");

        assertThat(result.type()).isEqualTo(TASK);
        assertThat(result.stateMachine()).isEmpty();
        assertThat(result.derivations()).isEmpty();
    }

    @Test
    void describeTypeResolvesByFullIri() {
        DefaultMetaModelService service = service(new FakeOntology(SCHEMA, BEHAVIOUR), permitAll());

        TypeDescription result = service.describeType(SCOPE, TASK_IRI);

        assertThat(result.type()).isEqualTo(TASK);
    }

    @Test
    void describeTypeKeysDerivationsByPropertyIriOfTheDerivedProperties() {
        DefaultMetaModelService service = service(new FakeOntology(SCHEMA, BEHAVIOUR_WITH_ROLLUP), permitAll());

        TypeDescription result = service.describeType(SCOPE, "Project");

        assertThat(result.type()).isEqualTo(PROJECT);
        assertThat(result.derivations()).containsExactly(Map.entry(TOTAL_IRI, TOTAL_ROLLUP));
    }

    @Test
    void describeTypeThrowsTypeNotFoundForUnknownName() {
        DefaultMetaModelService service = service(new FakeOntology(SCHEMA, BEHAVIOUR), permitAll());

        assertThatExceptionOfType(TypeNotFoundException.class)
                .isThrownBy(() -> service.describeType(SCOPE, "NoSuchType"));
    }

    @Test
    void describeTypeConsultsPortWithReadOperationAndResolvedTypeIri() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort = (scope, operation, resource) -> {
            capturedOperations.add(operation);
            capturedResources.add(resource);
            return AccessDecision.permit("ok");
        };

        service(new FakeOntology(SCHEMA, BEHAVIOUR), authorizationPort).describeType(SCOPE, "Task");

        assertThat(capturedOperations).containsExactly(Operation.READ);
        assertThat(capturedResources).containsExactly(TASK_IRI);
    }

    @Test
    void describeTypeThrowsWithPortsOwnDecisionOnDeny() {
        AccessDecision denial = AccessDecision.deny("no access");
        DefaultMetaModelService service =
                service(new FakeOntology(SCHEMA, BEHAVIOUR), (scope, operation, resource) -> denial);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.describeType(SCOPE, "Task"))
                .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
    }

    @Test
    void describeTypeThrowsTypeNotFoundBeforeConsultingAuthorizationPortForUnknownName() {
        List<Operation> capturedOperations = new ArrayList<>();
        AuthorizationPort authorizationPort = (scope, operation, resource) -> {
            capturedOperations.add(operation);
            return AccessDecision.permit("ok");
        };
        DefaultMetaModelService service = service(new FakeOntology(SCHEMA, BEHAVIOUR), authorizationPort);

        assertThatExceptionOfType(TypeNotFoundException.class)
                .isThrownBy(() -> service.describeType(SCOPE, "NoSuchType"));
        assertThat(capturedOperations).isEmpty();
    }

    @Test
    void describeTypeRejectsNullScope() {
        DefaultMetaModelService service = service(new FakeOntology(SCHEMA, BEHAVIOUR), permitAll());

        assertThatNullPointerException().isThrownBy(() -> service.describeType(null, "Task"));
    }

    @Test
    void describeTypeRejectsNullNameOrIri() {
        DefaultMetaModelService service = service(new FakeOntology(SCHEMA, BEHAVIOUR), permitAll());

        assertThatNullPointerException().isThrownBy(() -> service.describeType(SCOPE, null));
    }

    // --- reload ---

    @Test
    void reloadReturnsPortsReportOnPermit() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);

        OntologyReport result = service(ontology, permitAll()).reload(SCOPE);

        assertThat(result).isEqualTo(REPORT);
        assertThat(ontology.reloadCalls).isEqualTo(1);
    }

    @Test
    void reloadConsultsPortWithAdminOperationAndEverythingResource() {
        List<Operation> capturedOperations = new ArrayList<>();
        List<String> capturedResources = new ArrayList<>();
        AuthorizationPort authorizationPort = (scope, operation, resource) -> {
            capturedOperations.add(operation);
            capturedResources.add(resource);
            return AccessDecision.permit("ok");
        };

        service(new FakeOntology(SCHEMA, BEHAVIOUR), authorizationPort).reload(SCOPE);

        assertThat(capturedOperations).containsExactly(Operation.ADMIN);
        assertThat(capturedResources).containsExactly(AuthorizationPort.EVERYTHING);
    }

    @Test
    void reloadThrowsWithPortsOwnDecisionOnDenyAndDoesNotCallAdministrationPort() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);
        AccessDecision denial = AccessDecision.deny("no access");

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service(ontology, (scope, operation, resource) -> denial)
                        .reload(SCOPE))
                .satisfies(exception -> assertThat(exception.decision()).isEqualTo(denial));
        assertThat(ontology.reloadCalls).isZero();
    }

    @Test
    void reloadRejectsNullScope() {
        assertThatNullPointerException()
                .isThrownBy(() -> service(new FakeOntology(SCHEMA, BEHAVIOUR), permitAll())
                        .reload(null));
    }

    // --- deny-all across all four methods ---

    @Test
    void denyAllAuthorizationPortDeniesAllFourMethods() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);
        DefaultMetaModelService service = service(ontology, denyAll());

        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> service.schema(SCOPE));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> service.behaviour(SCOPE));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> service.describeType(SCOPE, "Task"));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> service.reload(SCOPE));

        // describeType resolves against the halves before authorizing (see its javadoc), so the
        // one read of each half above comes from describeType's resolution step, not from schema()
        // or behaviour(), which must never reach their ports when denied.
        assertThat(ontology.schemaCalls).isEqualTo(1);
        assertThat(ontology.behaviourCalls).isEqualTo(1);
        assertThat(ontology.reloadCalls).isZero();
    }

    // --- constructor ---

    @Test
    void constructorRejectsNullSchemaSource() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultMetaModelService(null, ontology, ontology, permitAll()));
    }

    @Test
    void constructorRejectsNullBehaviourSource() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultMetaModelService(ontology, null, ontology, permitAll()));
    }

    @Test
    void constructorRejectsNullAdministrationPort() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultMetaModelService(ontology, ontology, null, permitAll()));
    }

    @Test
    void constructorRejectsNullAuthorizationPort() {
        FakeOntology ontology = new FakeOntology(SCHEMA, BEHAVIOUR);
        assertThatNullPointerException()
                .isThrownBy(() -> new DefaultMetaModelService(ontology, ontology, ontology, null));
    }

    /**
     * Hand-written stand-in for the two read ports and the administration port. Each call is
     * counted. {@code export} and {@code importDocument} are not exercised by these tests.
     */
    private static final class FakeOntology
            implements SchemaSource, BehaviourSource, org.sequeless.ontology.api.OntologyAdministration {

        private final Schema schema;
        private final Behaviour behaviour;
        private int schemaCalls;
        private int behaviourCalls;
        private int reloadCalls;

        FakeOntology(Schema schema, Behaviour behaviour) {
            this.schema = schema;
            this.behaviour = behaviour;
        }

        @Override
        public Schema schema(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            schemaCalls++;
            return schema;
        }

        @Override
        public Behaviour behaviour(TenantId tenant) {
            Objects.requireNonNull(tenant, "tenant must not be null");
            behaviourCalls++;
            return behaviour;
        }

        @Override
        public OntologyReport reload(Scope scope) {
            Objects.requireNonNull(scope, "scope must not be null");
            reloadCalls++;
            return REPORT;
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
