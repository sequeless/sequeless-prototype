package org.sequeless.adapter.automation.temporal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code sequeless.automation.temporal.*} configuration namespace this adapter reads at
 * startup: which task queue {@link ActionWorkflow} executions run on, an optional Temporal server
 * {@code target} to connect to (host:port, e.g. for a future Testcontainers-backed acceptance
 * test), and the {@link Retry} policy mapped onto every activity's {@code RetryOptions} (see {@link
 * ActionWorkflowImpl}'s static {@code ACTIVITY_OPTIONS} holder).
 *
 * <p>Spring Boot 4.1's relaxed binding parses a YAML/properties {@link Duration} value like {@code
 * "1s"} automatically (via {@code DurationStyle}) — no custom {@code Converter} bean is needed for
 * {@link Retry#getInitialInterval()} below, exactly as with {@code
 * org.sequeless.adapter.automation.inprocess.InProcessAutomationProperties#getRetryDelay()}.
 */
@ConfigurationProperties("sequeless.automation.temporal")
public class TemporalAutomationProperties {

    /**
     * The task queue {@link ActionWorkflow} executions are started on, and that {@link
     * TemporalAutomationAutoConfiguration}'s worker polls. Defaults to {@code "sequeless-actions"}.
     */
    private String taskQueue = "sequeless-actions";

    /**
     * The Temporal server to connect to, as a {@code host:port} gRPC target (e.g. {@code
     * "localhost:7233"}). Left {@code null} (the default) means "use {@code
     * WorkflowServiceStubs.newLocalServiceStubs()}", which targets {@code 127.0.0.1:7233} without
     * eagerly connecting. Intended for a later task to point at a Testcontainers-managed {@code
     * temporalio/auto-setup} instance on a dynamic port.
     */
    private String target;

    private final Retry retry = new Retry();

    /** @return the task queue name {@link ActionWorkflow} executions are started/polled on */
    public String getTaskQueue() {
        return taskQueue;
    }

    /** @param taskQueue the task queue name to start/poll {@link ActionWorkflow} executions on */
    public void setTaskQueue(String taskQueue) {
        this.taskQueue = taskQueue;
    }

    /** @return the configured Temporal server target, or {@code null} to use the local default */
    public String getTarget() {
        return target;
    }

    /** @param target the Temporal server target to connect to, or {@code null} for the local default */
    public void setTarget(String target) {
        this.target = target;
    }

    /** @return the retry policy bound from {@code sequeless.automation.temporal.retry.*} */
    public Retry getRetry() {
        return retry;
    }

    /**
     * The {@code sequeless.automation.temporal.retry.*} sub-namespace, mapped onto {@link
     * io.temporal.common.RetryOptions} and applied uniformly to all four {@link ActionActivities}
     * methods via the {@link io.temporal.activity.ActivityOptions} {@link
     * TemporalAutomationAutoConfiguration} builds.
     */
    public static class Retry {

        /** The first retry's delay. Defaults to 1 second. */
        private Duration initialInterval = Duration.ofSeconds(1);

        /** The multiplier applied to the delay after each retry. Defaults to 2.0. */
        private double backoffCoefficient = 2.0;

        /** The total number of attempts (including the first) before giving up. Defaults to 5. */
        private int maximumAttempts = 5;

        /** @return the first retry's delay */
        public Duration getInitialInterval() {
            return initialInterval;
        }

        /** @param initialInterval the first retry's delay */
        public void setInitialInterval(Duration initialInterval) {
            this.initialInterval = initialInterval;
        }

        /** @return the multiplier applied to the delay after each retry */
        public double getBackoffCoefficient() {
            return backoffCoefficient;
        }

        /** @param backoffCoefficient the multiplier to apply to the delay after each retry */
        public void setBackoffCoefficient(double backoffCoefficient) {
            this.backoffCoefficient = backoffCoefficient;
        }

        /** @return the total number of attempts (including the first) before giving up */
        public int getMaximumAttempts() {
            return maximumAttempts;
        }

        /** @param maximumAttempts the total number of attempts (including the first) before giving up */
        public void setMaximumAttempts(int maximumAttempts) {
            this.maximumAttempts = maximumAttempts;
        }
    }
}
