package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.common.RetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.time.Duration;
import org.sequeless.spi.automation.ActionExecutor;
import org.sequeless.spi.automation.AutomationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link TemporalAutomationPort} and its supporting Temporal client/worker beans into the
 * Spring context when {@code sequeless.automation.adapter} is set to {@code temporal}.
 *
 * <p><b>Deliberately hand-rolled, not {@code temporal-spring-boot-starter}.</b> That starter
 * transitively pulls Spring Boot 2.7.18 (verified via {@code mvn dependency:tree}: {@code
 * spring-boot-starter}/{@code spring-boot-autoconfigure} at 2.7.18), which is incompatible with
 * this project's Spring Boot 4.1.1 line and risks {@code NoSuchMethodError}/{@code
 * ClassNotFoundException} from stale bytecode even after this reactor's {@code
 * dependencyManagement} force-upgrades the jar *versions*. {@code temporal-sdk} itself has zero
 * Spring dependency, so this class builds {@link WorkflowServiceStubs}, {@link WorkflowClient}, and
 * {@link WorkerFactory} directly from it instead — exactly like {@code
 * PostgresPersistenceAutoConfiguration} and {@code JexlExpressionAutoConfiguration} hand-roll their
 * own wiring. Do not "simplify" this by adding the starter back.
 *
 * <p>Deliberately does <b>not</b> construct an {@link ActionExecutor}: this adapter module cannot
 * depend on {@code sequeless-core}, where the real implementation ({@code
 * org.sequeless.core.automation.DefaultActionExecutor}) lives (the {@code noAdapterDependsOnCore}
 * architecture rule). {@link #workerFactory} instead simply takes an {@link ActionExecutor} as an
 * injected {@code @Bean} method parameter; some later, application-level configuration task is
 * responsible for having registered that bean on the context by the time this auto-configuration
 * runs. Do not be tempted to {@code new DefaultActionExecutor(...)} here — this module cannot even
 * see that class.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.automation.adapter", havingValue = "temporal")
@EnableConfigurationProperties(TemporalAutomationProperties.class)
public class TemporalAutomationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TemporalAutomationAutoConfiguration.class);

    /**
     * gRPC stubs are constructed lazily and do not eagerly connect: this bean method returns
     * successfully even with no Temporal server reachable at {@code props.getTarget()} (or the
     * local default), so this auto-configuration is safe to load in a plain unit-test {@code
     * ApplicationContextRunner} context.
     *
     * @param props the bound {@code sequeless.automation.temporal.*} configuration
     * @return stubs targeting {@code props.getTarget()} if set, else the local default ({@code
     *     127.0.0.1:7233})
     */
    @Bean
    public WorkflowServiceStubs workflowServiceStubs(TemporalAutomationProperties props) {
        String target = props.getTarget();
        if (target == null || target.isBlank()) {
            return WorkflowServiceStubs.newLocalServiceStubs();
        }
        WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder().setTarget(target).build();
        return WorkflowServiceStubs.newServiceStubs(options);
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs);
    }

    /**
     * @param client the Temporal client {@link TemporalAutomationPort} starts workflow executions
     *     with
     * @param props the bound {@code sequeless.automation.temporal.*} configuration, supplying the
     *     task queue name
     * @return a new {@link TemporalAutomationPort} over both
     */
    @Bean
    public AutomationPort automationPort(WorkflowClient client, TemporalAutomationProperties props) {
        return new TemporalAutomationPort(client, props.getTaskQueue());
    }

    /**
     * Publishes {@link ActionWorkflowImpl#ACTIVITY_OPTIONS} (see that class's javadoc for why a
     * Temporal-instantiated workflow implementation cannot receive this via constructor injection)
     * and starts a worker polling {@code props.getTaskQueue()} for both {@link ActionWorkflow} and
     * {@link ActionActivities} work.
     *
     * <p><b>Empirically verified finding, unlike {@link #workflowServiceStubs}/{@link
     * #workflowClient}: {@code WorkerFactory.start()} is NOT lazy.</b> It issues a blocking {@code
     * DescribeNamespace} RPC to validate server/namespace capabilities before it begins polling, and
     * throws (wrapping a {@code io.grpc.StatusRuntimeException: UNAVAILABLE}) if no server is
     * reachable — confirmed by a {@code TemporalAutomationAutoConfigurationTest} run against no
     * live Temporal server, which failed the whole bean (and so the whole {@code
     * ApplicationContext}) until this method started catching that failure. The SDK exposes no
     * option to skip this check. Rather than make the entire application fail to start whenever
     * Temporal is briefly unreachable at boot, this method logs a warning and leaves the worker
     * unstarted (not polling) instead of propagating; {@link #automationPort}'s {@link
     * TemporalAutomationPort} still works in that state (starting workflows only needs the lazy
     * {@link WorkflowClient}), so actions get durably queued even if no worker is yet consuming
     * them. This is a known limitation worth revisiting when the Testcontainers-backed acceptance
     * task lands: a retry/reconnect strategy (or a readiness probe gating traffic) would close the
     * gap between "app is up" and "workers are actually polling."
     *
     * @param client the Temporal client the worker factory is built from
     * @param actionExecutor the {@link ActionExecutor} bean some other, application-level
     *     configuration is expected to have already registered on the context
     * @param props the bound {@code sequeless.automation.temporal.*} configuration, supplying the
     *     task queue name and retry policy
     * @return the {@link WorkerFactory}, kept as a bean so its lifecycle is visible to (and
     *     shuttable by) the Spring context, whether or not {@code start()} actually succeeded
     */
    @Bean
    public WorkerFactory workerFactory(
        WorkflowClient client, ActionExecutor actionExecutor, TemporalAutomationProperties props) {
        ActionWorkflowImpl.ACTIVITY_OPTIONS = buildActivityOptions(props.getRetry());

        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(props.getTaskQueue());
        worker.registerWorkflowImplementationTypes(ActionWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ActionActivitiesImpl(actionExecutor));
        try {
            factory.start();
        } catch (RuntimeException e) {
            log.warn(
                "Failed to start Temporal WorkerFactory for task queue '{}'; the application will "
                    + "continue starting, but no worker will poll this queue until it is restarted "
                    + "with connectivity to the Temporal server restored",
                props.getTaskQueue(),
                e);
        }
        return factory;
    }

    /**
     * How long a single attempt of any one {@link ActionActivities} method may run before Temporal
     * considers that attempt timed out (and, per {@link #buildActivityOptions}'s {@link
     * RetryOptions}, retries it). Not derived from the retry policy's own fields — {@code
     * StartToCloseTimeout} bounds one attempt's wall-clock duration, unrelated to how many attempts
     * {@code maximumAttempts} allows — so a flat, generous default is used instead; no
     * configuration property exposes this today.
     */
    private static final Duration ACTIVITY_START_TO_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    private static ActivityOptions buildActivityOptions(TemporalAutomationProperties.Retry retry) {
        RetryOptions retryOptions =
            RetryOptions.newBuilder()
                .setInitialInterval(retry.getInitialInterval())
                .setBackoffCoefficient(retry.getBackoffCoefficient())
                .setMaximumAttempts(retry.getMaximumAttempts())
                .build();
        return ActivityOptions.newBuilder()
            .setStartToCloseTimeout(ACTIVITY_START_TO_CLOSE_TIMEOUT)
            .setRetryOptions(retryOptions)
            .build();
    }
}
