package org.sequeless.app.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.sequeless.adapter.persistence.postgres.PostgresObjectStore;
import org.sequeless.adapter.persistence.postgres.PostgresPersistenceAutoConfiguration;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.ResolvedTimer;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TransitionFiredEvent;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.automation.autoconfigure.InProcessAutomationAutoConfiguration;
import org.sequeless.automation.core.DefaultActionExecutor;
import org.sequeless.core.api.BusinessObjectService;
import org.sequeless.core.api.TransitionService;
import org.sequeless.core.derivation.DerivationPlanner;
import org.sequeless.core.derivation.DerivationPluginRegistry;
import org.sequeless.core.usecase.DefaultBusinessObjectService;
import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.TriggerKind;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.spi.OntologyDocumentStore;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.spi.outbox.OutboxPort;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves, against a real PostgreSQL store, that a retried {@code TransitionFired} fire applies each
 * {@code CreateObject} action exactly once after a rolled-back attempt.
 *
 * <p>The rule under test: {@code SetProperty} and {@code CreateObject} are Store actions. They commit
 * inside the claim transaction, so a failed claim rolls them back and the retry runs them again.
 * {@code LogAction} and {@code WebhookAction} are recorded per (entry id, action index) once they
 * succeed, and a retry runs, in index order, every action that is not recorded. A rule that records a
 * Store action before the claim commits would make the retry skip action 0, whose object was rolled
 * back, so this test fails under that rule: action 0's object would be missing.
 *
 * <p>The fault is injected at the store commit of action 1's object. The first claim fails and rolls
 * back, and the second claim, which is the retry, must create both objects. The store is
 * {@code PostgresObjectStore} and the outbox is the real {@code PostgresOutboxPort}, with the in-process
 * adapter and the real {@code DefaultActionExecutor} in between. The test runs on a database of its
 * own, so no other test's outbox relay can claim the row.
 */
class CreateObjectRetryProofTest extends PostgresTestcontainersSupport {

    private static final String NS = "https://sequeless.test/create-once-proof#";
    private static final String MACHINE_IRI = NS + "ProjectLifecycle";
    private static final String DRAFT_IRI = NS + "Draft";
    private static final String ACTIVE_IRI = NS + "Active";
    private static final String TRANSITION_NAME = "activate";
    private static final String FIRST_TYPE_IRI = NS + "FirstNote";
    private static final String SECOND_TYPE_IRI = NS + "SecondNote";

    private static final String TENANT = "tenant-1";
    private static final String PRINCIPAL_ID = "user-1";
    private static final Scope SCOPE = new Scope(new TenantId(TENANT), new Principal(PRINCIPAL_ID, Set.of()));
    private static final Set<String> CLAIMED_KINDS = Set.of(OutboxEntry.KIND_TRANSITION_FIRED);

    private static final Automation AUTOMATION = new Automation(
            new OntologyVersion(1),
            Map.of(
                    new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME),
                    List.<Action>of(
                            new CreateObjectAction(FIRST_TYPE_IRI, List.of()),
                            new CreateObjectAction(SECOND_TYPE_IRI, List.of()))));

    /** The two types the automation creates. They declare no properties, so the structural checks have nothing to reject. */
    private static final Schema SCHEMA = new Schema(
            new OntologyVersion(1),
            "https://sequeless.dev/ns/test",
            Optional.empty(),
            Map.of(),
            List.of(
                    new TypeDefinition(FIRST_TYPE_IRI, "First", List.of(), List.of(), DisplayHints.none(), false),
                    new TypeDefinition(SECOND_TYPE_IRI, "Second", List.of(), List.of(), DisplayHints.none(), false)));

    private static final Behaviour BEHAVIOUR = new Behaviour(new OntologyVersion(1), Map.of(), Map.of());

    @Test
    void aRetriedFireAppliesEachCreateObjectExactlyOnceAfterARolledBackAttempt() throws SQLException {
        String databaseUrl = createFreshDatabase();
        UUID rowId = UUID.randomUUID();
        ObjectId firstObjectId = derivedCreateId(rowId, 0);
        ObjectId secondObjectId = derivedCreateId(rowId, 1);

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PostgresPersistenceAutoConfiguration.class, InProcessAutomationAutoConfiguration.class))
                .withUserConfiguration(ProofConfiguration.class)
                .withPropertyValues(
                        "sequeless.persistence.adapter=postgres",
                        "sequeless.persistence.url=" + databaseUrl,
                        "sequeless.persistence.username=" + POSTGRES.getUsername(),
                        "sequeless.persistence.password=" + POSTGRES.getPassword(),
                        "sequeless.automation.adapter=inprocess")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FaultOnceObjectStore store = context.getBean(FaultOnceObjectStore.class);
                    OutboxPort outbox = context.getBean(OutboxPort.class);
                    AutomationPort automation = context.getBean(AutomationPort.class);
                    JdbcClient jdbcClient = JdbcClient.create(context.getBean(DataSource.class));

                    store.commit(SCOPE, new ChangeSet(List.of(), List.of(transitionFired(rowId))));
                    store.armFor(secondObjectId);

                    assertThatThrownBy(() -> claimOnce(outbox, automation))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("injected commit fault");
                    assertThat(countObjects(jdbcClient, firstObjectId)).isZero();
                    assertThat(countObjects(jdbcClient, secondObjectId)).isZero();
                    assertThat(countProcessed(jdbcClient, rowId)).isZero();

                    assertThat(claimOnce(outbox, automation)).isPresent();

                    assertThat(countObjects(jdbcClient, firstObjectId)).isEqualTo(1L);
                    assertThat(countObjects(jdbcClient, secondObjectId)).isEqualTo(1L);
                    assertThat(countProcessed(jdbcClient, rowId)).isEqualTo(1L);
                });
    }

    /** One claim of the {@code TransitionFired} kind. The handler ingests the row's event inside the claim. */
    private static Optional<UUID> claimOnce(OutboxPort outbox, AutomationPort automation) {
        return outbox.claimNext(CLAIMED_KINDS, (tenantId, entry) -> {
            automation.ingest(
                    new Scope(new TenantId(tenantId), new Principal(PRINCIPAL_ID, Set.of())),
                    new TransitionFiredEvent(entry.id(), entry.payload(), entry.occurredAt()));
            return entry.id();
        });
    }

    /**
     * The id {@code DefaultActionExecutor} derives for a {@code CreateObject} action: the row's id and the
     * action index, hashed. The test derives the same id so the fault can target action 1's object.
     */
    private static ObjectId derivedCreateId(UUID rowId, int actionIndex) {
        return new ObjectId(UUID.nameUUIDFromBytes((rowId + ":" + actionIndex).getBytes(StandardCharsets.UTF_8)));
    }

    /** A {@code TransitionFired} row carrying the keys {@code DefaultTransitionService} writes on one. */
    private static OutboxEntry transitionFired(UUID rowId) {
        Map<String, Object> payload = Map.of(
                "objectId", UUID.randomUUID().toString(),
                "tenantId", TENANT,
                "principalId", PRINCIPAL_ID,
                "transitionName", TRANSITION_NAME,
                "fromState", DRAFT_IRI,
                "toState", ACTIVE_IRI,
                "ontologyVersion", 1,
                "self", Map.of(),
                "typeIri", NS + "Project",
                "machineIri", MACHINE_IRI);
        return new OutboxEntry(
                rowId, OutboxEntry.KIND_TRANSITION_FIRED, payload, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static long countObjects(JdbcClient jdbcClient, ObjectId id) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM sq_object WHERE id = :id AND tenant_id = :tenantId")
                .param("id", id.value())
                .param("tenantId", TENANT)
                .query(Long.class)
                .single();
    }

    private static long countProcessed(JdbcClient jdbcClient, UUID rowId) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM sq_outbox WHERE id = :id AND processed_at IS NOT NULL")
                .param("id", rowId)
                .query(Long.class)
                .single();
    }

    /**
     * Creates a database of its own on the shared container and returns its JDBC URL. The shared container
     * also serves the scheduled outbox relays of other test contexts, which would claim a row written here.
     */
    private static String createFreshDatabase() throws SQLException {
        String name = "create_once_" + UUID.randomUUID().toString().replace('-', '_');
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return "jdbc:postgresql://%s:%d/%s"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), name);
    }

    /**
     * The proof's wiring. The fault store wraps the real {@code PostgresObjectStore}. The create path is the released
     * one: the released {@code DefaultActionExecutor} calls the host's {@code ObjectReadPort} and
     * {@code StoreCommandPort} ({@link AutomationBindingConfiguration}), and those call {@link BusinessObjectService#add}
     * over the fault store. The in-process adapter and the Postgres outbox come from their auto-configurations. The
     * schema, behaviour, validation and authorization are stubs.
     */
    @Configuration(proxyBeanMethods = false)
    @Import(AutomationBindingConfiguration.class)
    static class ProofConfiguration {

        @Bean
        @Primary
        FaultOnceObjectStore faultOnceObjectStore(DataSource dataSource) {
            return new FaultOnceObjectStore(new PostgresObjectStore(
                    JdbcClient.create(dataSource), new DataSourceTransactionManager(dataSource)));
        }

        @Bean
        BusinessObjectService businessObjectService(FaultOnceObjectStore faultOnceObjectStore) {
            return new DefaultBusinessObjectService(
                    tenant -> SCHEMA,
                    tenant -> BEHAVIOUR,
                    faultOnceObjectStore,
                    (scope, schema, object) -> List.of(),
                    (scope, operation, resource) -> new AccessDecision(true, "permitted"),
                    (scope, request) -> Optional.empty(),
                    (scope, schema, query) -> {
                        throw new UnsupportedOperationException("not needed by this test");
                    },
                    Clock.systemUTC(),
                    new DerivationPlanner(
                            (scope, schema, request) -> {
                                throw new UnsupportedOperationException("not needed by this test");
                            },
                            DerivationPluginRegistry.fromServiceLoader()));
        }

        @Bean
        BehaviourSource behaviourSource() {
            return tenant -> BEHAVIOUR;
        }

        @Bean
        TransitionService transitionService() {
            return new TransitionService() {
                @Override
                public org.sequeless.spi.object.BusinessObject fire(
                        Scope scope, String type, ObjectId id, String transitionName, long expectedVersion) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public Optional<org.sequeless.spi.object.BusinessObject> fireAutomated(
                        Scope scope, ObjectId id, String transitionName, TriggerKind expected) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public void signal(Scope scope, String type, ObjectId id, String signalName, Map<String, Object> body) {
                    throw new UnsupportedOperationException("not needed by this test");
                }
            };
        }

        @Bean
        ActionExecutor actionExecutor(ObjectReadPort objectReadPort, StoreCommandPort storeCommandPort) {
            // Neither CreateObject action carries a property assignment, so no expression is ever evaluated.
            ExpressionPort expressionPort = new ExpressionPort() {
                @Override
                public Value evaluate(String expression, ExpressionContext context) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public String renderTemplate(String template, ExpressionContext context) {
                    throw new UnsupportedOperationException("not needed by this test");
                }
            };
            return new DefaultActionExecutor(expressionPort, objectReadPort, storeCommandPort);
        }

        @Bean
        AutomationSource automationSource() {
            return tenant -> AUTOMATION;
        }

        /** No timers and no change triggers: this proof is about the create action alone. */
        @Bean
        TriggerEvaluator triggerEvaluator() {
            return new TriggerEvaluator() {
                @Override
                public void onChange(Scope scope, ObjectChangedEvent changeEvent) {}

                @Override
                public void onTimerElapsed(Scope scope, StoreEventRow timerScheduled) {}

                @Override
                public List<ResolvedTimer> timersStartedBy(Scope scope, TransitionFiredEvent transitionFired) {
                    return List.of();
                }
            };
        }
    }

    /**
     * An {@link ObjectStorePort} that throws once, on the first commit whose changeset creates the armed
     * object, before it delegates. The throw happens before any write, so the failed attempt's earlier
     * writes in the same claim transaction are what the rollback must undo. Reads delegate unchanged.
     */
    static final class FaultOnceObjectStore implements ObjectStorePort {

        private final ObjectStorePort delegate;
        private final AtomicReference<ObjectId> armedTarget = new AtomicReference<>();

        FaultOnceObjectStore(ObjectStorePort delegate) {
            this.delegate = delegate;
        }

        void armFor(ObjectId target) {
            armedTarget.set(target);
        }

        @Override
        public Optional<BusinessObject> find(Scope scope, ObjectId id) {
            return delegate.find(scope, id);
        }

        @Override
        public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
            return delegate.typesOf(scope, ids);
        }

        @Override
        public CommitResult commit(Scope scope, ChangeSet changeSet) {
            ObjectId target = armedTarget.get();
            if (target != null && createsObject(changeSet, target)) {
                armedTarget.set(null);
                throw new IllegalStateException("injected commit fault for object " + target.value());
            }
            return delegate.commit(scope, changeSet);
        }

        @Override
        public OntologyDocumentStore ontologyDocuments() {
            return delegate.ontologyDocuments();
        }

        private static boolean createsObject(ChangeSet changeSet, ObjectId target) {
            return changeSet.mutations().stream()
                    .anyMatch(mutation -> mutation instanceof Create create
                            && create.object().id().equals(target));
        }
    }
}
