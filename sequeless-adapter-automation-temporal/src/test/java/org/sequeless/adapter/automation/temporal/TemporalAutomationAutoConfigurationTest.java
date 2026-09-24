package org.sequeless.adapter.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.DerivationRecomputer;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingDerivationRecomputer;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exercises {@link TemporalAutomationAutoConfiguration} via a real Spring context ({@link
 * ApplicationContextRunner}), mirroring {@code sequeless-adapter-expression-jexl}'s {@code
 * JexlExpressionAutoConfigurationTest} and {@code sequeless-adapter-automation-inprocess}'s {@code
 * InProcessAutomationAutoConfigurationTest}.
 *
 * <p>Registers a {@link RecordingActionExecutor} as the context's {@link ActionExecutor} bean, and
 * likewise a {@link RecordingTriggerEvaluator}/{@link RecordingDerivationRecomputer} pair as the
 * context's {@link TriggerEvaluator}/{@link DerivationRecomputer} beans, in place of the real
 * {@code sequeless-core} implementations this adapter module cannot depend on — standing in for
 * the later, application-level task that wires the real beans in.
 *
 * <p><b>No live Temporal server is required, but not for the reason it might first seem.</b> {@link
 * WorkflowServiceStubs#newLocalServiceStubs()} builds gRPC stubs lazily, without eagerly connecting,
 * so {@code workflowServiceStubs} and {@code workflowClient} bean construction succeed with nothing
 * listening on 127.0.0.1:7233. {@code WorkerFactory.start()}, however, was verified empirically
 * (this test failed with a wrapped {@code io.grpc.StatusRuntimeException: UNAVAILABLE} before this
 * finding was addressed) to be <b>not</b> lazy: it issues a blocking {@code DescribeNamespace} RPC
 * and throws when no server answers. {@link TemporalAutomationAutoConfiguration#workerFactory} now
 * catches that failure and logs a warning instead of propagating it — see that method's own javadoc
 * for the full reasoning — which is the only reason {@code assertThat(context).hasNotFailed()} below
 * holds without a live Temporal server.
 *
 * <p>Deliberately does <b>not</b> call {@code WorkerFactory.shutdown()}/{@code shutdownNow()}
 * explicitly from within a test body: both were verified empirically to make their own blocking
 * {@code shutdownWorker} RPC (since the worker registered task-queue metadata with the server even
 * though {@code start()} itself failed before polling began), which throws the same {@code
 * UNAVAILABLE} failure with no live server and would fail the test. {@link
 * ApplicationContextRunner#run} closes the context it builds for each call once the assertion
 * callback returns; {@link WorkerFactory#shutdown()} is Spring's inferred destroy method for a
 * {@code @Bean} with no explicit {@code destroyMethod} (it has a public no-arg {@code shutdown()}
 * method), and Spring's bean-destruction machinery swallows (logs, does not rethrow) any exception a
 * destroy method throws — so the automatic close still cleans up, silently, without this test needing
 * to trigger or tolerate that RPC failure itself.
 */
class TemporalAutomationAutoConfigurationTest {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TemporalAutomationAutoConfiguration.class))
            .withBean(ActionExecutor.class, RecordingActionExecutor::new)
            .withBean(TriggerEvaluator.class, RecordingTriggerEvaluator::new)
            .withBean(DerivationRecomputer.class, RecordingDerivationRecomputer::new);

    @Test
    void wiresAutomationPortWhenAdapterPropertySetAndActionExecutorPresent() {
        runner
            .withPropertyValues("sequeless.automation.adapter=temporal")
            .run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AutomationPort.class);
                    assertThat(context.getBean(AutomationPort.class)).isInstanceOf(TemporalAutomationPort.class);
                    assertThat(context).hasSingleBean(WorkflowClient.class);
                    assertThat(context).hasSingleBean(WorkflowServiceStubs.class);
                    assertThat(context).hasSingleBean(WorkerFactory.class);
                });
    }

    @Test
    void doesNotWireWhenAdapterPropertyAbsent() {
        runner.run(
            context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(AutomationPort.class);
            });
    }

    @Test
    void doesNotWireWhenAdapterPropertyIsSomethingElse() {
        runner
            .withPropertyValues("sequeless.automation.adapter=other")
            .run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AutomationPort.class);
                });
    }

    @Test
    void bindsRetryAndTaskQueuePropertiesWithDefaults() {
        runner
            .withPropertyValues("sequeless.automation.adapter=temporal")
            .run(
                context -> {
                    assertThat(context).hasNotFailed();
                    TemporalAutomationProperties props = context.getBean(TemporalAutomationProperties.class);
                    assertThat(props.getTaskQueue()).isEqualTo("sequeless-actions");
                    assertThat(props.getRetry().getMaximumAttempts()).isEqualTo(5);
                });
    }

    @Test
    void bindsCustomRetryAndTaskQueueProperties() {
        runner
            .withPropertyValues(
                "sequeless.automation.adapter=temporal",
                "sequeless.automation.temporal.task-queue=custom-queue",
                "sequeless.automation.temporal.retry.initial-interval=2s",
                "sequeless.automation.temporal.retry.backoff-coefficient=1.5",
                "sequeless.automation.temporal.retry.maximum-attempts=7")
            .run(
                context -> {
                    assertThat(context).hasNotFailed();
                    TemporalAutomationProperties props = context.getBean(TemporalAutomationProperties.class);
                    assertThat(props.getTaskQueue()).isEqualTo("custom-queue");
                    assertThat(props.getRetry().getInitialInterval()).isEqualTo(java.time.Duration.ofSeconds(2));
                    assertThat(props.getRetry().getBackoffCoefficient()).isEqualTo(1.5);
                    assertThat(props.getRetry().getMaximumAttempts()).isEqualTo(7);
                });
    }
}
