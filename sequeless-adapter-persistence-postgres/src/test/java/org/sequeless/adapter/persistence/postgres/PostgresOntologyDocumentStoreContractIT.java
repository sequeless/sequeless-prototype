package org.sequeless.adapter.persistence.postgres;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.sequeless.ontology.testfixtures.OntologyDocumentStoreContract;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link PostgresOntologyDocumentStore} satisfies {@link OntologyDocumentStoreContract}, the
 * released document-store contract, against a real PostgreSQL 18 instance started by Testcontainers.
 * This is the runner that takes over the two {@code ontologyDocuments} clauses the prototype's
 * {@code ObjectStoreContract} carried before the cut-over.
 *
 * <p>{@link #freshDocuments()} returns a store over the container's {@link DataSource} on every call.
 * The table is truncated after each test, because the contract requires a brand-new, empty store.
 * Like {@link PostgresObjectStoreContractIT}, the migration runs once per container in {@link
 * #migrate()}.
 */
@Testcontainers
class PostgresOntologyDocumentStoreContractIT extends OntologyDocumentStoreContract {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PostgresMigrations.migrate(dataSource);
    }

    @AfterEach
    void truncateTables() {
        JdbcClient.create(dataSource).sql("TRUNCATE TABLE sq_ontology_document").update();
    }

    @Override
    protected PostgresOntologyDocumentStore freshDocuments() {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        return new PostgresOntologyDocumentStore(JdbcClient.create(dataSource), transactionManager);
    }
}
