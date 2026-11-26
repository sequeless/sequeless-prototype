package org.sequeless.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.sequeless.app.support.TemporalTestcontainersSupport;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.automation.temporal.ChangeEventWorkflow;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The Postgres and Temporal end of the change-event failure record (spec 13, R2; spec 17, section 9). A change-event
 * workflow whose {@code onChange} fails on every attempt must leave exactly one {@code sq_automation_failure} row, keyed
 * by the event id that the released port starts the workflow under, and the workflow must complete normally.
 *
 * <p>The only test double is the {@link TriggerEvaluator}: a post-processor, ordered ahead of the host's
 * failure-recording post-processor, makes {@code onChange} throw. Every other method delegates to the real evaluator.
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
class ChangeEventFailureRecordIT extends TemporalTestcontainersSupport {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-change"), new Principal("user-change", Set.of()));

    /** The task queue the application's Temporal worker polls, the default of {@code sequeless.automation.temporal}. */
    private static final String TASK_QUEUE = "sequeless-actions";

    @Autowired
    private WorkflowClient workflowClient;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void anOnChangeThatKeepsFailingRecordsOneFailureUnderTheEventIdAndTheWorkflowCompletes() throws TimeoutException {
        UUID eventId = UUID.randomUUID();
        ObjectChangedEvent event =
                new ObjectChangedEvent(eventId, ObjectChangedEvent.Change.UPDATED, Map.of(), Instant.now());

        // Returns normally only if the workflow completed: a failed workflow makes getResult throw.
        runChangeEvent(event);

        List<Row> rows = rowsFor(eventId.toString());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).workflowType()).isEqualTo("ChangeEventWorkflow");
        assertThat(rows.get(0).tenantId()).isEqualTo("tenant-change");
        assertThat(rows.get(0).cause()).isEqualTo("java.lang.IllegalStateException: change evaluation broke");
    }

    /** Starts the change-event workflow under the event id, as the released port does, and waits, bounded, for it. */
    private void runChangeEvent(ObjectChangedEvent event) throws TimeoutException {
        ChangeEventWorkflow workflow = workflowClient.newWorkflowStub(
                ChangeEventWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(event.id().toString())
                        .setTaskQueue(TASK_QUEUE)
                        .build());
        WorkflowClient.start(workflow::run, event, SCOPE);
        WorkflowStub.fromTyped(workflow).getResult(30, TimeUnit.SECONDS, Void.class);
    }

    private List<Row> rowsFor(String workflowId) {
        return jdbcClient
                .sql("SELECT workflow_type, tenant_id, cause FROM sq_automation_failure WHERE workflow_id = :id")
                .param("id", workflowId)
                .query((rs, rowNum) ->
                        new Row(rs.getString("workflow_type"), rs.getString("tenant_id"), rs.getString("cause")))
                .list();
    }

    private record Row(String workflowType, String tenantId, String cause) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingOnChangeConfiguration {

        /**
         * Applied before the host's failure-recording post-processor, which has no order and so runs later. The failing
         * proxy sits inside the host wrapper, which records the final failure. This ordering is test-only (spec 17,
         * section 9). The return type is the concrete class, because Spring reads PriorityOrdered from the declared
         * return type of a {@code @Bean} method.
         */
        @Bean
        static FailingOnChange failingOnChange() {
            return new FailingOnChange();
        }

        static final class FailingOnChange implements BeanPostProcessor, PriorityOrdered {

            @Override
            public int getOrder() {
                return Ordered.HIGHEST_PRECEDENCE;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof TriggerEvaluator evaluator)) {
                    return bean;
                }
                return Proxy.newProxyInstance(
                        TriggerEvaluator.class.getClassLoader(),
                        new Class<?>[] {TriggerEvaluator.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("onChange")) {
                                throw new IllegalStateException("change evaluation broke");
                            }
                            try {
                                return method.invoke(evaluator, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        });
            }
        }
    }
}
