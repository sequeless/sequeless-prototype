package org.sequeless.app.port;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.adapter.authz.permitall.PermitAllAuthorizationAutoConfiguration;
import org.sequeless.adapter.ontology.jena.JenaOntologyAutoConfiguration;
import org.sequeless.app.config.PortRegistryConfiguration;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exercises {@link PortRegistry}'s fail-fast checks end to end against real Spring contexts, built
 * with {@link ApplicationContextRunner} rather than a booted web server. {@code
 * sequeless-adapter-authz-permitall} is a real compile dependency of {@code sequeless-app}, so its
 * {@code META-INF/services} entry is genuinely discovered by {@link
 * java.util.ServiceLoader} — none of these tests need to mock adapter discovery.
 */
class PortRegistryContextRunnerTest {

    private final ApplicationContextRunner runnerWithPermitAllAdapter =
        new ApplicationContextRunner()
            .withUserConfiguration(PortRegistryConfiguration.class)
            .withConfiguration(AutoConfigurations.of(PermitAllAuthorizationAutoConfiguration.class));

    private final ApplicationContextRunner runnerWithoutAnyAdapter =
        new ApplicationContextRunner().withUserConfiguration(PortRegistryConfiguration.class);

    @Test
    void absentPropertyFailsFast() {
        runnerWithPermitAllAdapter.run(context -> {
            assertThat(context).hasFailed();
            PortBindingException cause = findCause(context.getStartupFailure(), PortBindingException.class);
            assertThat(cause).isNotNull();
            assertThat(cause.getMessage())
                .contains("sequeless.authz.adapter")
                .contains("is not set")
                .contains("permit-all");
        });
    }

    @Test
    void unknownAdapterNameFailsFast() {
        runnerWithPermitAllAdapter
            .withPropertyValues("sequeless.authz.adapter=totally-unknown")
            .run(context -> {
                assertThat(context).hasFailed();
                PortBindingException cause = findCause(context.getStartupFailure(), PortBindingException.class);
                assertThat(cause).isNotNull();
                assertThat(cause.getMessage()).contains("totally-unknown").contains("permit-all");
            });
    }

    @Test
    void validNameWithoutAutoConfigurationFindsZeroBeans() {
        // No AutoConfigurations imported here, but the descriptor is still discovered via
        // ServiceLoader (service-file discovery does not depend on the auto-config class being
        // imported), so checks 1-3 pass and check 4 (bean cardinality) is what actually fails.
        // This isolates "valid name, absent bean" from "absent property".
        runnerWithoutAnyAdapter
            .withPropertyValues("sequeless.authz.adapter=permit-all")
            .run(context -> {
                assertThat(context).hasFailed();
                PortBindingException cause = findCause(context.getStartupFailure(), PortBindingException.class);
                assertThat(cause).isNotNull();
                assertThat(cause.getMessage())
                    .contains("permit-all")
                    .contains("no AuthorizationPort bean was found");
            });
    }

    @Test
    void twoBeansFailsFast() {
        runnerWithPermitAllAdapter
            .withUserConfiguration(ExtraAuthorizationPortConfiguration.class)
            .withPropertyValues("sequeless.authz.adapter=permit-all")
            .run(context -> {
                assertThat(context).hasFailed();
                PortBindingException cause = findCause(context.getStartupFailure(), PortBindingException.class);
                assertThat(cause).isNotNull();
                assertThat(cause.getMessage())
                    .contains("2 AuthorizationPort beans")
                    .contains("authorizationPort")
                    .contains("other");
            });
    }

    /**
     * The only test here that has to satisfy <em>every</em> port slot at once. Each test above
     * deliberately configures the authz slot alone, because each is probing one specific failure
     * mode of that slot and {@link PortRegistry} fails on the first slot it finds wanting — leaving
     * the ontology slot unconfigured there is harmless, since those contexts are asserted to fail
     * anyway. A context that must actually <em>succeed</em> has no such luxury: it needs a valid
     * adapter and exactly one bean for the authz slot and for the ontology slot alike, which is why
     * this is the one place the real {@link JenaOntologyAutoConfiguration} is imported and pointed
     * at the reference ontology.
     */
    @Test
    void happyPathSucceeds() {
        runnerWithPermitAllAdapter
            .withConfiguration(AutoConfigurations.of(JenaOntologyAutoConfiguration.class))
            .withPropertyValues(
                "sequeless.authz.adapter=permit-all",
                "sequeless.ontology.adapter=jena",
                "sequeless.ontology.source=classpath:ontology/reference.ttl")
            .run(context -> assertThat(context).hasNotFailed());
    }

    private static <T extends Throwable> T findCause(Throwable root, Class<T> type) {
        Throwable current = root;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    @Configuration(proxyBeanMethods = false)
    static class ExtraAuthorizationPortConfiguration {

        @Bean
        AuthorizationPort other() {
            return (scope, operation, resource) -> AccessDecision.deny("other");
        }
    }
}
