package org.sequeless.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.query.BrowsePort;
import org.sequeless.spi.query.IndexPreparationPort;
import org.sequeless.spi.query.ReadPort;
import org.sequeless.store.spi.ObjectStorePort;
import org.sequeless.store.spi.QueryPort;
import org.sequeless.store.spi.SearchVectorBackfillPort;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exercises {@link PostgresPersistenceAutoConfiguration} against a real Spring context via {@link
 * ApplicationContextRunner}, rather than the reflection-only approach {@code
 * sequeless-adapter-ontology-jena}'s {@code JenaOntologyAutoConfigurationTest} and {@code
 * sequeless-adapter-authz-permitall}'s {@code PermitAllAuthorizationAutoConfigurationTest} use — see
 * the step plan's T6 task, which deliberately asks for this deviation here.
 *
 * <p>None of these tests need Docker or a real PostgreSQL instance: {@link
 * PostgresPersistenceAutoConfiguration}'s {@link DataSource} bean is built with {@code
 * HikariDataSource}'s no-arg constructor and never connects during bean creation, and the migration
 * that {@link PostgresMigratingDataSource} runs is deferred to the first real {@code
 * getConnection()} call, which none of these tests trigger.
 */
class PostgresPersistenceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PostgresPersistenceAutoConfiguration.class));

    @Test
    void wiresObjectStorePortAndDataSourceWhenAdapterPropertySet() {
        runner.withPropertyValues(
                        "sequeless.persistence.adapter=postgres",
                        "sequeless.persistence.url=jdbc:postgresql://localhost:5432/test",
                        "sequeless.persistence.username=test",
                        "sequeless.persistence.password=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ObjectStorePort.class);
                    assertThat(context).hasSingleBean(SearchVectorBackfillPort.class);
                    assertThat(context).hasSingleBean(DataSource.class);
                });
    }

    @Test
    void exposesOnlyQueryPortAmongTheQuerySidePorts() {
        // Spec 17 P2a: the Query-side ports (read, browse, index preparation) come from the host's adapters over the
        // released QueryService, so this adapter's query bean is the Command-side QueryPort alone.
        runner.withPropertyValues(
                        "sequeless.persistence.adapter=postgres",
                        "sequeless.persistence.url=jdbc:postgresql://localhost:5432/test",
                        "sequeless.persistence.username=test",
                        "sequeless.persistence.password=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QueryPort.class);
                    assertThat(context).doesNotHaveBean(ReadPort.class);
                    assertThat(context).doesNotHaveBean(BrowsePort.class);
                    assertThat(context).doesNotHaveBean(IndexPreparationPort.class);
                });
    }

    @Test
    void doesNotWireWhenAdapterPropertyAbsent() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(ObjectStorePort.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
        });
    }

    @Test
    void doesNotWireWhenAdapterPropertyIsSomethingElse() {
        runner.withPropertyValues("sequeless.persistence.adapter=in-memory").run(context -> {
            assertThat(context).doesNotHaveBean(ObjectStorePort.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
        });
    }
}
