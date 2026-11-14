package org.sequeless.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.sequeless.app.automation.OutboxRelay;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.RollupRule;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.AggregateFunction;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.RelationshipDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.ontology.spi.OntologyDocumentStore;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.outbox.OutboxEntry;
import org.sequeless.spi.outbox.OutboxPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Proves that one {@link OutboxRelay#pollOnce()} call drives an object-lifecycle row through the
 * recompute {@code CoreConfiguration} builds, against a real PostgreSQL store, when the first commit
 * of that recompute loses an optimistic-lock race.
 *
 * <p>The store the recompute commits through is the {@code CoreConfiguration} decorator
 * ({@code RequiresNewCommitObjectStore}) over an {@link ArmableObjectStore}, over the real
 * {@code PostgresObjectStore}. The fixture is written first, and only then is the store armed. The
 * first armed commit bumps the target project's version from a separate connection, so that commit
 * fails with {@code StaleObjectException}. {@code DefaultDerivationRecomputer} does the retrying,
 * not the decorator: its second attempt re-reads the project and commits. The decorator's
 * {@code REQUIRES_NEW} commit rolled back the failed attempt on its own, so the claim is not
 * marked rollback-only and the single poll processes the row.
 *
 * <p>Built with {@link ApplicationContextRunner}, not {@code @SpringBootTest}, so the scheduled
 * {@code pollOnce()} never runs here: the test calls it once, directly. Only the real
 * {@code CoreConfiguration} recomputer and the real Postgres DataSource, outbox and query beans are
 * under test. The ontology is a small hand-written {@code Project}/{@code Task} rollup.
 */
class RecomputeCommitRetryProofTest extends PostgresTestcontainersSupport {

    private static final String NS = "https://sequeless.test/recompute-retry-proof#";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String TASK_IRI = NS + "Task";
    private static final String BELONGS_TO_PROJECT_IRI = NS + "belongsToProject";
    private static final String OPEN_TASK_COUNT_IRI = NS + "openTaskCount";

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Scope SCOPE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));

    private static final Schema SCHEMA = buildSchema();
    private static final Behaviour BEHAVIOUR = new Behaviour(
            new OntologyVersion(1),
            Map.of(),
            Map.of(
                    OPEN_TASK_COUNT_IRI,
                    new RollupRule(
                            TASK_IRI,
                            BELONGS_TO_PROJECT_IRI,
                            AggregateFunction.COUNT,
                            Optional.empty(),
                            Optional.empty(),
                            true)));

    @Test
    void oneRelayPollCommitsTheClaimAndRecomputesOnTheAttemptThatSucceeds() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PostgresPersistenceAutoConfiguration.class))
                .withUserConfiguration(CoreConfiguration.class, ProofConfiguration.class)
                .withPropertyValues(
                        "sequeless.persistence.adapter=postgres",
                        "sequeless.persistence.url=" + POSTGRES.getJdbcUrl(),
                        "sequeless.persistence.username=" + POSTGRES.getUsername(),
                        "sequeless.persistence.password=" + POSTGRES.getPassword())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ArmableObjectStore store = context.getBean(ArmableObjectStore.class);
                    OutboxRelay relay = context.getBean(OutboxRelay.class);
                    JdbcClient jdbcClient = JdbcClient.create(context.getBean(DataSource.class));

                    ObjectId projectId = new ObjectId(UUID.randomUUID());
                    ObjectId taskId = new ObjectId(UUID.randomUUID());
                    UUID fixtureEntryId = UUID.randomUUID();
                    store.commit(
                            SCOPE,
                            new ChangeSet(
                                    List.of(
                                            new Create(newObject(projectId, PROJECT_IRI, Map.of())),
                                            new Create(newObject(
                                                    taskId,
                                                    TASK_IRI,
                                                    Map.of(
                                                            new PropertyRef(BELONGS_TO_PROJECT_IRI),
                                                            Value.ref(projectId))))),
                                    List.of(new OutboxEntry(
                                            fixtureEntryId,
                                            OutboxEntry.KIND_OBJECT_UPDATED,
                                            Map.of("objectId", taskId.value().toString(), "typeIri", TASK_IRI),
                                            Instant.now().truncatedTo(ChronoUnit.MICROS)))));
                    store.armFor(projectId);

                    relay.pollOnce();

                    Long processed = jdbcClient
                            .sql("SELECT COUNT(*) FROM sq_outbox WHERE id = :id AND processed_at IS NOT NULL")
                            .param("id", fixtureEntryId)
                            .query(Long.class)
                            .single();
                    assertThat(processed).isEqualTo(1L);

                    BusinessObject project = store.find(SCOPE, projectId).orElseThrow();
                    assertThat(project.version()).isEqualTo(3L);
                    assertThat(project.properties())
                            .containsEntry(new PropertyRef(OPEN_TASK_COUNT_IRI), new IntegerValue(1));
                });
    }

    private static BusinessObject newObject(ObjectId id, String typeIri, Map<PropertyRef, Value> properties) {
        return new BusinessObject(
                id,
                new TypeRef(typeIri),
                new TenantId("acme"),
                1,
                Optional.empty(),
                properties,
                new Audit(CREATED_AT, "carol", CREATED_AT, "carol"),
                false);
    }

    private static Schema buildSchema() {
        RelationshipDefinition belongsToProject = new RelationshipDefinition(
                BELONGS_TO_PROJECT_IRI,
                "belongsToProject",
                Cardinality.optional(),
                false,
                false,
                false,
                false,
                false,
                DisplayHints.none(),
                PROJECT_IRI,
                Optional.empty(),
                false);
        AttributeDefinition openTaskCount = new AttributeDefinition(
                OPEN_TASK_COUNT_IRI,
                "openTaskCount",
                Cardinality.required(),
                false,
                false,
                false,
                true,
                false,
                DisplayHints.none(),
                Datatype.INTEGER);
        TypeDefinition project = new TypeDefinition(
                PROJECT_IRI, "Project", List.of(), List.of(openTaskCount), DisplayHints.none(), false);
        TypeDefinition task =
                new TypeDefinition(TASK_IRI, "Task", List.of(), List.of(belongsToProject), DisplayHints.none(), false);
        return new Schema(
                new OntologyVersion(1),
                NS.substring(0, NS.length() - 1),
                Optional.empty(),
                Map.of(),
                List.of(project, task));
    }

    /**
     * The proof's wiring. {@link ArmableObjectStore} is {@code @Primary}, so it is the one
     * {@code ObjectStorePort} every {@code @Lazy} parameter in {@link CoreConfiguration} resolves to.
     * The auto-configured {@code objectStorePort} bean is left in place but never resolved here.
     */
    @Configuration(proxyBeanMethods = false)
    static class ProofConfiguration {

        @Bean
        SchemaSource schemaSource() {
            return tenant -> SCHEMA;
        }

        @Bean
        BehaviourSource behaviourSource() {
            return tenant -> BEHAVIOUR;
        }

        @Bean
        AutomationPort automationPort() {
            return (scope, entry) -> {
                // The ingress is a no-op: the proof is about the recompute and the claim.
            };
        }

        @Bean
        @Primary
        ArmableObjectStore armableObjectStore(DataSource sequelessPersistenceDataSource) {
            return new ArmableObjectStore(
                    new PostgresObjectStore(
                            JdbcClient.create(sequelessPersistenceDataSource),
                            new DataSourceTransactionManager(sequelessPersistenceDataSource)),
                    sequelessPersistenceDataSource);
        }

        @Bean
        OutboxRelay outboxRelay(
                OutboxPort outboxPort,
                ObjectProvider<AutomationPort> automationPortProvider,
                DerivationRecomputer derivationRecomputer) {
            return new OutboxRelay(outboxPort, automationPortProvider, derivationRecomputer);
        }
    }

    /**
     * An {@link ObjectStorePort} that, once {@link #armFor} has been called, bumps the armed object's
     * stored version from a separate autocommit connection before its next {@link #commit}. The bump
     * commits at once, so the commit's own version check sees a version it did not expect and throws
     * {@code StaleObjectException}. Only the first commit after arming is affected. Reads delegate
     * unchanged.
     */
    static final class ArmableObjectStore implements ObjectStorePort {

        private final ObjectStorePort delegate;
        private final DataSource dataSource;
        private final AtomicReference<ObjectId> armedTarget = new AtomicReference<>();

        ArmableObjectStore(ObjectStorePort delegate, DataSource dataSource) {
            this.delegate = delegate;
            this.dataSource = dataSource;
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
            ObjectId target = armedTarget.getAndSet(null);
            if (target != null) {
                bumpVersionOnAnotherConnection(target, scope.tenantId().value());
            }
            return delegate.commit(scope, changeSet);
        }

        @Override
        public OntologyDocumentStore ontologyDocuments() {
            return delegate.ontologyDocuments();
        }

        private void bumpVersionOnAnotherConnection(ObjectId target, String tenantId) {
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE sq_object SET version = version + 1 WHERE id = ? AND tenant_id = ?")) {
                statement.setObject(1, target.value());
                statement.setString(2, tenantId);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("armed bump matched no row for " + target.value());
                }
            } catch (SQLException e) {
                throw new IllegalStateException("armed bump failed for " + target.value(), e);
            }
        }
    }
}
