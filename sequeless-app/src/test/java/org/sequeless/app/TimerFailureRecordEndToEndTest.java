package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sequeless.adapter.automation.temporal.TimerWorkflow;
import org.sequeless.app.support.TemporalTestcontainersSupport;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.spi.automation.ResolvedTimer;
import org.sequeless.spi.automation.TimerScheduledEntry;
import org.sequeless.spi.automation.TriggerEvaluator;
import org.sequeless.spi.outbox.OutboxEntry;

/**
 * The Postgres end of the timer failure record (spec 13, R2): a real Temporal server, a real Postgres, and the
 * application's real Temporal worker. A timer whose {@code onTimerElapsed} fails after its retries must leave
 * exactly one {@code sq_automation_failure} row, keyed by the timer's {@code timerKey}, and a second run under
 * the same key must not add a row.
 *
 * <p>The only test double is the {@link TriggerEvaluator}: a {@code BeanPostProcessor} wraps it so that
 * {@code onTimerElapsed} always throws, and every other method delegates to the real evaluator. Nothing else is
 * stubbed.
 *
 * <p>Run this class on its own. It starts Postgres and Temporal containers.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "sequeless.persistence.adapter=postgres",
            "sequeless.automation.adapter=temporal",
            "sequeless.expression.adapter=jexl",
            "sequeless.automation.temporal.retry.initial-interval=50ms",
            "sequeless.automation.temporal.retry.backoff-coefficient=1.0",
            "sequeless.automation.temporal.retry.maximum-attempts=3"
        })
class TimerFailureRecordEndToEndTest extends TemporalTestcontainersSupport {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final String TYPE_IRI = "https://sequeless.dev/ns/test#Thing";
    private static final String STATE_IRI = "https://sequeless.dev/ns/test#Waiting";
    private static final String TRANSITION = "expire";

    /** The task queue the application's Temporal worker polls, the default of {@code sequeless.automation.temporal}. */
    private static final String TASK_QUEUE = "sequeless-actions";

    @Autowired
    private WorkflowClient workflowClient;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void aTimerWhoseElapsedActivityFailsLeavesExactlyOneRecordKeyedByItsTimerKey() throws TimeoutException {
        ObjectId objectId = ObjectId.parse(UUID.randomUUID().toString());
        String timerKey = OutboxEntry.timerKey(objectId, STATE_IRI, TRANSITION);
        OutboxEntry timer = timer(objectId, timerKey);

        // The workflow ends normally: the failure is recorded, not thrown.
        runTimer(timer, timerKey);

        List<Row> rows = rowsFor(timerKey);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).workflowType()).isEqualTo("TimerWorkflow");
        assertThat(rows.get(0).tenantId()).isEqualTo("tenant-1");
        assertThat(rows.get(0).cause()).isEqualTo("java.lang.IllegalStateException: timer evaluation broke");
    }

    @Test
    void aSecondRunUnderTheSameTimerKeyAddsNoSecondRow() throws TimeoutException {
        ObjectId objectId = ObjectId.parse(UUID.randomUUID().toString());
        String timerKey = OutboxEntry.timerKey(objectId, STATE_IRI, TRANSITION);
        OutboxEntry timer = timer(objectId, timerKey);
        runTimer(timer, timerKey);

        // The second start uses Temporal's default reuse policy, which allows a closed workflow id to start again.
        // The port starts timers with REJECT_DUPLICATE, so in production this re-run is refused before it starts.
        // Using the default here reaches the record insert a second time, which is what ON CONFLICT must absorb.
        runTimer(timer, timerKey);

        assertThat(rowsFor(timerKey)).hasSize(1);
    }

    private static OutboxEntry timer(ObjectId objectId, String timerKey) {
        ResolvedTimer resolved = new ResolvedTimer(timerKey, Duration.ofMillis(200), STATE_IRI, TRANSITION);
        return TimerScheduledEntry.of(SCOPE, objectId, TYPE_IRI, 1, resolved, OCCURRED_AT);
    }

    /** Starts the timer under {@code timerKey}, as the Temporal port does, and waits, bounded, for it to close. */
    private void runTimer(OutboxEntry entry, String timerKey) throws TimeoutException {
        TimerWorkflow workflow = workflowClient.newWorkflowStub(
                TimerWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(timerKey)
                        .setTaskQueue(TASK_QUEUE)
                        .build());
        WorkflowClient.start(workflow::run, entry, SCOPE);
        WorkflowStub.fromTyped(workflow).getResult(30, TimeUnit.SECONDS, Void.class);
    }

    private List<Row> rowsFor(String workflowId) {
        return jdbcClient
                .sql("SELECT workflow_type, tenant_id, cause FROM sq_automation_failure WHERE workflow_id = :id")
                .param("id", workflowId)
                .query((rs, rowNum) -> new Row(
                        rs.getString("workflow_type"), rs.getString("tenant_id"), rs.getString("cause")))
                .list();
    }

    private record Row(String workflowType, String tenantId, String cause) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingOnTimerElapsedConfiguration {

        /**
         * Wraps every {@link TriggerEvaluator} bean in a proxy whose {@code onTimerElapsed} throws, as a broken
         * timer evaluation would. Every other method is delegated to the real evaluator.
         */
        @Bean
        static BeanPostProcessor failingOnTimerElapsed() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof TriggerEvaluator evaluator)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            TriggerEvaluator.class.getClassLoader(),
                            new Class<?>[] {TriggerEvaluator.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("onTimerElapsed")) {
                                    throw new IllegalStateException("timer evaluation broke");
                                }
                                try {
                                    return method.invoke(evaluator, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
                }
            };
        }
    }
}
