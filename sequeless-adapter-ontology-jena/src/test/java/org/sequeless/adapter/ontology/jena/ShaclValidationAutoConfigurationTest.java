package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.spi.validation.ValidationPort;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exercises {@link ShaclValidationAutoConfiguration} together with {@link
 * JenaOntologyAutoConfiguration} via a real Spring context ({@link ApplicationContextRunner}),
 * mirroring {@code sequeless-adapter-persistence-postgres}'s {@code
 * PostgresPersistenceAutoConfigurationTest} rather than the reflection-only style {@link
 * JenaOntologyAutoConfigurationTest} uses on its own — this configuration's whole point is that it
 * shares a live bean instance with another auto-configuration, which only a real context proves.
 */
class ShaclValidationAutoConfigurationTest {

    private static final String ONTOLOGY_SOURCE = "classpath:ontology/reference.ttl";

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(JenaOntologyAutoConfiguration.class, ShaclValidationAutoConfiguration.class));

    @Test
    void wiresValidationPortWhenBothAdaptersAreConfigured() {
        runner
            .withPropertyValues(
                "sequeless.ontology.adapter=jena",
                "sequeless.ontology.source=" + ONTOLOGY_SOURCE,
                "sequeless.validation.adapter=shacl")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ValidationPort.class);
                assertThat(context.getBean(ValidationPort.class)).isInstanceOf(ShaclValidationPort.class);
            });
    }

    @Test
    void doesNotWireWhenValidationAdapterPropertyAbsent() {
        runner
            .withPropertyValues(
                "sequeless.ontology.adapter=jena", "sequeless.ontology.source=" + ONTOLOGY_SOURCE)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ValidationPort.class);
            });
    }

    @Test
    void contextFailsWhenNoOntologyAdapterIsConfigured() {
        runner
            .withPropertyValues("sequeless.validation.adapter=shacl")
            .run(context -> assertThat(context).hasFailed());
    }
}
