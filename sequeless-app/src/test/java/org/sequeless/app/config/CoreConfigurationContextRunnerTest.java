package org.sequeless.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.sequeless.app.automation.OutboxRelay;
import org.sequeless.core.api.TransitionService;
import org.sequeless.core.automation.DefaultActionExecutor;
import org.sequeless.core.usecase.DefaultTransitionService;
import org.sequeless.spi.Scope;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.expression.ExpressionContext;
import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.ObjectId;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.OutboxEntry;
import org.sequeless.spi.object.OutboxPort;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.object.Value;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.query.AggregateRequest;
import org.sequeless.spi.query.AggregateResult;
import org.sequeless.spi.query.Query;
import org.sequeless.spi.query.QueryPort;
import org.sequeless.spi.query.QueryResult;
import org.sequeless.spi.validation.ValidationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Exercises {@link CoreConfiguration}'s two newest beans — {@link ActionExecutor} and {@link
 * TransitionService} — end to end against real {@link ApplicationContextRunner} contexts, mirroring
 * {@code PortRegistryContextRunnerTest}'s style. Also verifies, via reflection, that {@link
 * OutboxRelay#pollOnce()} carries the expected {@code @Scheduled} configuration, and that {@link
 * SchedulingConfiguration} actually registers Spring's scheduling infrastructure — without ever
 * letting a real clock tick fire {@code pollOnce()} inside a unit test.
 */
class CoreConfigurationContextRunnerTest {

    /**
     * {@link CoreConfiguration}'s bean methods take every port parameter {@code @Lazy}, exactly so
     * that (per that class's own javadoc) they construct cleanly with zero real port beans on the
     * context — this is the baseline case that proves the two new beans do not regress that
     * property.
     */
    @Test
    void actionExecutorAndTransitionServiceRegisterWithoutAnyPortBeans() {
        new ApplicationContextRunner()
            .withUserConfiguration(CoreConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ActionExecutor.class);
                assertThat(context).hasSingleBean(TransitionService.class);
                assertThat(context.getBean(ActionExecutor.class)).isInstanceOf(DefaultActionExecutor.class);
                assertThat(context.getBean(TransitionService.class))
                    .isInstanceOf(DefaultTransitionService.class);
            });
    }

    /**
     * The closer-to-deployment case: every port slot {@link CoreConfiguration} depends on
     * (transitively, across its five bean methods) has exactly one bean present, all inert stubs
     * that are never actually invoked by this test. This is what "the two new beans are what
     * {@code InProcessAutomationAutoConfiguration}'s and {@code TemporalAutomationAutoConfiguration}'s
     * own {@code automationPort(ActionExecutor actionExecutor, ...)} bean methods have been depending
     * on" (see {@link CoreConfiguration#actionExecutor}'s javadoc) looks like once those adapters'
     * own {@code @Bean} methods resolve {@code ActionExecutor} eagerly.
     */
    @Test
    void actionExecutorAndTransitionServiceRegisterWithStubPortBeans() {
        new ApplicationContextRunner()
            .withUserConfiguration(CoreConfiguration.class, StubPortsConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ActionExecutor.class);
                assertThat(context).hasSingleBean(TransitionService.class);
            });
    }

    /**
     * Confirms {@link OutboxRelay#pollOnce()} is annotated with the exact {@code fixedDelayString}
     * this task's plan specifies, including its default value. Pure reflection, no Spring context —
     * asserting the annotation's declared value is enough; actually waiting for it to fire on a real
     * clock would be unnecessary and flaky in a unit test.
     */
    @Test
    void pollOnceCarriesExpectedScheduledAnnotation() throws NoSuchMethodException {
        Method pollOnce = OutboxRelay.class.getMethod("pollOnce");
        Scheduled scheduled = pollOnce.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
            .isEqualTo("${sequeless.automation.relay.poll-interval-ms:1000}");
    }

    /**
     * Confirms {@link SchedulingConfiguration} actually activates Spring's {@code @Scheduled}
     * machinery: once it (and an {@link OutboxRelay} instance) are on the context, Spring registers
     * its internal {@code ScheduledAnnotationBeanPostProcessor}, which is what would go on to invoke
     * {@link OutboxRelay#pollOnce()} on a real clock in the running application. This test only
     * checks that the post-processor bean exists — it never lets a scheduled tick actually fire.
     */
    @Test
    void schedulingConfigurationRegistersScheduledAnnotationProcessor() {
        // OutboxPort#claimNext is itself generic, so a lambda cannot implement it (javac cannot
        // infer a per-call T for a lambda body); an anonymous class is required.
        OutboxPort neverClaimsOutboxPort =
            new OutboxPort() {
                @Override
                public <T> Optional<T> claimNext(
                    Set<String> kinds, BiFunction<String, OutboxEntry, T> handler) {
                    return Optional.empty();
                }
            };

        new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class, OutboxRelayConfiguration.class)
            .withBean(OutboxPort.class, () -> neverClaimsOutboxPort)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(
                    context.containsBean(
                        "org.springframework.scheduling.config.internalScheduledAnnotationProcessor"))
                    .isTrue();
            });
    }

    /**
     * Registers a plain, hand-built {@link OutboxRelay} bean (constructed with the {@link
     * OutboxPort} bean {@link ApplicationContextRunner} supplies, and an {@link ObjectProvider} that
     * always answers "no automation port available") so {@link
     * #schedulingConfigurationRegistersScheduledAnnotationProcessor()} can assert Spring picks up its
     * {@code @Scheduled} method without needing a full automation adapter on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    static class OutboxRelayConfiguration {

        @Bean
        OutboxRelay outboxRelay(OutboxPort outboxPort) {
            return new OutboxRelay(
                outboxPort,
                new ObjectProvider<>() {
                    @Override
                    public AutomationPort getIfAvailable() {
                        return null;
                    }
                });
        }
    }

    /**
     * One inert stub bean per port slot {@link CoreConfiguration} depends on across all five of its
     * bean methods, so {@link #actionExecutorAndTransitionServiceRegisterWithStubPortBeans()} can
     * assert the happy-path wiring without a real adapter. None of these stubs' methods are ever
     * actually invoked by that test — every method throws, mirroring {@code
     * PortRegistryContextRunnerTest}'s existing convention of only implementing what a given test
     * actually exercises.
     */
    @Configuration(proxyBeanMethods = false)
    static class StubPortsConfiguration {

        @Bean
        OntologyPort ontologyPort() {
            return new OntologyPort() {
                @Override
                public MetaModelSnapshot snapshot(Scope scope) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public MetaModelSnapshot reload(Scope scope) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public OntologyDocument export(Scope scope, OntologyFormat format) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
                    throw new UnsupportedOperationException("not needed by this test");
                }
            };
        }

        @Bean
        ObjectStorePort objectStorePort() {
            return new ObjectStorePort() {
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
                    throw new UnsupportedOperationException("not needed by this test");
                }
            };
        }

        @Bean
        AuthorizationPort authorizationPort() {
            return (scope, operation, resource) -> AccessDecision.permit("stub");
        }

        @Bean
        QueryPort queryPort() {
            return new QueryPort() {
                @Override
                public QueryResult query(Scope scope, MetaModelSnapshot snapshot, Query query) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public void ensureIndexes(Scope scope, MetaModelSnapshot snapshot) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public AggregateResult aggregate(
                    Scope scope, MetaModelSnapshot snapshot, AggregateRequest request) {
                    throw new UnsupportedOperationException("not needed by this test");
                }
            };
        }

        @Bean
        ValidationPort validationPort() {
            return (scope, snapshot, object) -> List.of();
        }

        @Bean
        ExpressionPort expressionPort() {
            return new ExpressionPort() {
                @Override
                public Value evaluate(String expression, ExpressionContext context) {
                    throw new UnsupportedOperationException("not needed by this test");
                }

                @Override
                public String renderTemplate(String template, ExpressionContext context) {
                    throw new UnsupportedOperationException("not needed by this test");
                }
            };
        }
    }
}
