package org.sequeless.app.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.sequeless.adapter.authz.permitall.PermitAllAuthorizationAutoConfiguration;
import org.sequeless.adapter.ontology.jena.JenaOntologyAutoConfiguration;
import org.sequeless.app.config.PortRegistryConfiguration;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.StoredOntologyDocument;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.validation.ValidationPort;
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
     * the other slots unconfigured there is harmless, since those contexts are asserted to fail
     * anyway. A context that must actually <em>succeed</em> has no such luxury: it needs a valid
     * adapter and exactly one bean for all four slots — authz, ontology, persistence and validation
     * — which is why this is the one place the real {@link JenaOntologyAutoConfiguration} is
     * imported (pointed at the reference ontology) alongside {@link
     * StubPersistenceAndValidationConfiguration}'s in-memory {@link ObjectStorePort}/{@link
     * ValidationPort} stand-ins for the persistence and validation slots.
     *
     * <p>The stub {@link ObjectStorePort} is deliberately in-memory, not the real Postgres adapter:
     * once any {@link ObjectStorePort} bean exists, {@link JenaOntologyAutoConfiguration}'s {@code
     * ontologyPort(...)} bean method (see its own javadoc) resolves it via {@code
     * ObjectProvider#getIfAvailable()} and eagerly calls {@code
     * ObjectStorePort#ontologyDocuments()#active(Scope)} during bean construction — this test would
     * need a real, reachable Postgres for that call to succeed if the real adapter were wired in
     * here instead. The in-memory stub answers that same call without any external dependency,
     * keeping this a plain, container-free {@link ApplicationContextRunner} test.
     */
    @Test
    void happyPathSucceeds() {
        runnerWithPermitAllAdapter
            .withConfiguration(AutoConfigurations.of(JenaOntologyAutoConfiguration.class))
            .withUserConfiguration(StubPersistenceAndValidationConfiguration.class)
            .withPropertyValues(
                "sequeless.authz.adapter=permit-all",
                "sequeless.ontology.adapter=jena",
                "sequeless.ontology.source=classpath:ontology/reference.ttl",
                "sequeless.persistence.adapter=postgres",
                "sequeless.validation.adapter=shacl")
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

    /**
     * In-memory stand-ins for {@link ObjectStorePort} and {@link ValidationPort}, used only by
     * {@link #happyPathSucceeds()} to satisfy {@link PortRegistry}'s bean-cardinality check for the
     * persistence and validation slots without requiring a real Postgres. See that test's javadoc
     * for why a real adapter is not used here.
     */
    @Configuration(proxyBeanMethods = false)
    static class StubPersistenceAndValidationConfiguration {

        @Bean
        ObjectStorePort objectStorePort() {
            return new InMemoryObjectStorePort();
        }

        @Bean
        ValidationPort validationPort() {
            return (scope, snapshot, object) -> List.of();
        }
    }

    /**
     * The minimum {@link ObjectStorePort} needed for {@link JenaOntologyAutoConfiguration}'s
     * store-backed startup path (see {@code fromStore}'s javadoc) to succeed: only {@link
     * #ontologyDocuments()} and its {@link OntologyDocumentStore} are ever actually called during
     * this test's context refresh, so every other method is unimplemented on purpose.
     */
    private static final class InMemoryObjectStorePort implements ObjectStorePort {

        private final OntologyDocumentStore documentStore = new InMemoryOntologyDocumentStore();

        @Override
        public Optional<BusinessObject> find(Scope scope, ObjectId id) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public PageResult<BusinessObject> browse(Scope scope, Set<TypeRef> types, Page page) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public CommitResult commit(Scope scope, ChangeSet changeSet) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public OntologyDocumentStore ontologyDocuments() {
            return documentStore;
        }
    }

    /**
     * A single-tenant, single-version in-memory {@link OntologyDocumentStore}: enough for {@link
     * org.sequeless.adapter.ontology.jena.JenaOntologyPort#fromStore} to seed itself from {@code
     * sequeless.ontology.source} on the first {@link #active(Scope)} call (which finds nothing) and
     * activate what it built.
     */
    private static final class InMemoryOntologyDocumentStore implements OntologyDocumentStore {

        private final AtomicReference<StoredOntologyDocument> stored = new AtomicReference<>();

        @Override
        public Optional<StoredOntologyDocument> active(Scope scope) {
            return Optional.ofNullable(stored.get());
        }

        @Override
        public StoredOntologyDocument activate(Scope scope, OntologyDocument document) {
            StoredOntologyDocument next =
                new StoredOntologyDocument(UUID.randomUUID(), 1, document, Instant.now());
            stored.set(next);
            return next;
        }
    }
}
