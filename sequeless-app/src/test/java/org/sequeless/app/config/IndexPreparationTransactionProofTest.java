package org.sequeless.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.adapter.persistence.postgres.PostgresObjectStore;
import org.sequeless.adapter.persistence.postgres.PostgresPersistenceAutoConfiguration;
import org.sequeless.app.support.PostgresTestcontainersSupport;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.schema.api.AttributeDefinition;
import org.sequeless.ontology.schema.api.Cardinality;
import org.sequeless.ontology.schema.api.Datatype;
import org.sequeless.ontology.schema.api.DisplayHints;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.Create;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.SearchVectorBackfillPort;
import org.sequeless.spi.object.TypeRef;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Proves that {@link IndexPreparation#prepare} runs its registry write and its search backfill in one
 * transaction over the persistence DataSource, against a real PostgreSQL started by Testcontainers and
 * the real {@link CoreConfiguration} bean.
 *
 * <ul>
 *   <li>Success: the backfill sees the {@code sq_searchable_property} row written earlier in the same
 *       preparation. That row is uncommitted when the backfill runs, so the backfill is only correct when
 *       it joined the host transaction.
 *   <li>Rollback: the backfill throws after the registry row was written. The exception reaches the
 *       caller, and the registry row is gone, so the write was part of the same transaction.
 * </ul>
 *
 * <p>The schema has searchable properties only, with no {@code sq:indexed} property, so no {@code CREATE
 * INDEX} runs in either case. Each case builds its own {@link ApplicationContextRunner} context, and
 * empties the {@code public} schema first, because the persistence DataSource migrates once per instance.
 */
class IndexPreparationTransactionProofTest extends PostgresTestcontainersSupport {

    private static final String NS = "https://sequeless.test/index-preparation-proof#";
    private static final String NOTE_IRI = NS + "Note";
    private static final String BODY_IRI = NS + "body";
    private static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final TenantId TENANT = new TenantId("acme");
    private static final Scope SCOPE = new Scope(TENANT, Principal.ANONYMOUS);
    private static final Schema SCHEMA = buildSchema();

    @BeforeEach
    void emptyDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    @Test
    void theBackfillSeesTheRegistryRowWrittenBeforeItWhenThePreparationSucceeds() {
        runner(ProofConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            ObjectId note = writeNote(context, "quarterly ledger reconciliation");

            context.getBean(IndexPreparation.class).prepare(TENANT, SCHEMA.ontologyVersion());

            assertThat(registrationCount(context)).as("registry row written").isEqualTo(1L);
            assertThat(matchesSearch(context, note, "ledger"))
                    .as("backfill saw the uncommitted registry row")
                    .isTrue();
        });
    }

    @Test
    void aFailingBackfillRollsBackTheRegistryRowWrittenBeforeIt() {
        runner(ProofConfiguration.class, FailingBackfillConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            writeNote(context, "quarterly ledger reconciliation");
            FailingBackfill failing = context.getBean(FailingBackfill.class);
            failing.armed = true;
            IndexPreparation useCase = context.getBean(IndexPreparation.class);

            assertThatThrownBy(() -> useCase.prepare(TENANT, SCHEMA.ontologyVersion()))
                    .isSameAs(failing.failure);

            assertThat(failing.attempts)
                    .as("backfill reached after the registry write")
                    .isEqualTo(1);
            assertThat(registrationCount(context))
                    .as("registry row rolled back")
                    .isZero();
            assertThat(objectRowCount(context))
                    .as("object committed earlier survives")
                    .isEqualTo(1L);
        });
    }

    private static ApplicationContextRunner runner(Class<?>... extraConfigurations) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PostgresPersistenceAutoConfiguration.class))
                .withUserConfiguration(CoreConfiguration.class)
                .withUserConfiguration(extraConfigurations)
                .withPropertyValues(
                        "sequeless.persistence.adapter=postgres",
                        "sequeless.persistence.url=" + POSTGRES.getJdbcUrl(),
                        "sequeless.persistence.username=" + POSTGRES.getUsername(),
                        "sequeless.persistence.password=" + POSTGRES.getPassword());
    }

    /** Commits one {@code Note} through the auto-configured store, which also migrates the database. */
    private static ObjectId writeNote(AssertableApplicationContext context, String body) {
        ObjectId id = new ObjectId(UUID.randomUUID());
        BusinessObject note = new BusinessObject(
                id,
                new TypeRef(NOTE_IRI),
                TENANT,
                1,
                Optional.empty(),
                Map.of(new PropertyRef(BODY_IRI), Value.text(body)),
                new Audit(CREATED_AT, "carol", CREATED_AT, "carol"),
                false);
        context.getBean(ObjectStorePort.class).commit(SCOPE, new ChangeSet(List.of(new Create(note)), List.of()));
        return id;
    }

    private static long registrationCount(AssertableApplicationContext context) {
        return JdbcClient.create(context.getBean(DataSource.class))
                .sql("SELECT COUNT(*) FROM sq_searchable_property WHERE tenant_id = :tenantId "
                        + "AND property_iri = :propertyIri")
                .param("tenantId", TENANT.value())
                .param("propertyIri", BODY_IRI)
                .query(Long.class)
                .single();
    }

    private static long objectRowCount(AssertableApplicationContext context) {
        return JdbcClient.create(context.getBean(DataSource.class))
                .sql("SELECT COUNT(*) FROM sq_object")
                .query(Long.class)
                .single();
    }

    private static boolean matchesSearch(AssertableApplicationContext context, ObjectId id, String term) {
        Long matches = JdbcClient.create(context.getBean(DataSource.class))
                .sql("SELECT COUNT(*) FROM sq_object WHERE id = :id "
                        + "AND search_vector @@ plainto_tsquery('simple', :term)")
                .param("id", id.value())
                .param("term", term)
                .query(Long.class)
                .single();
        return matches == 1L;
    }

    /** One {@code Note} type whose only property is searchable, with no {@code sq:indexed} property. */
    private static Schema buildSchema() {
        AttributeDefinition body = new AttributeDefinition(
                BODY_IRI,
                "Body",
                Cardinality.optional(),
                false,
                false,
                true,
                false,
                false,
                DisplayHints.none(),
                Datatype.STRING);
        TypeDefinition note =
                new TypeDefinition(NOTE_IRI, "Note", List.of(), List.of(body), DisplayHints.none(), false);
        return new Schema(
                new OntologyVersion(1), NS.substring(0, NS.length() - 1), Optional.empty(), Map.of(), List.of(note));
    }

    @Configuration(proxyBeanMethods = false)
    static class ProofConfiguration {

        @Bean
        SchemaSource schemaSource() {
            return tenant -> SCHEMA;
        }
    }

    /**
     * Makes the real backfill throw while armed. It is {@code @Primary}, so it is the one {@link
     * SearchVectorBackfillPort} that {@link CoreConfiguration}'s {@code @Lazy} parameter resolves to.
     */
    @Configuration(proxyBeanMethods = false)
    static class FailingBackfillConfiguration {

        @Bean
        @Primary
        FailingBackfill failingBackfill(DataSource sequelessPersistenceDataSource) {
            return new FailingBackfill(new PostgresObjectStore(
                    JdbcClient.create(sequelessPersistenceDataSource),
                    new DataSourceTransactionManager(sequelessPersistenceDataSource)));
        }
    }

    static final class FailingBackfill implements SearchVectorBackfillPort {

        private final SearchVectorBackfillPort delegate;
        private final IllegalStateException failure = new IllegalStateException("injected backfill failure");
        private boolean armed;
        private int attempts;

        FailingBackfill(SearchVectorBackfillPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void backfill(Scope scope) {
            if (armed) {
                attempts++;
                throw failure;
            }
            delegate.backfill(scope);
        }
    }
}
