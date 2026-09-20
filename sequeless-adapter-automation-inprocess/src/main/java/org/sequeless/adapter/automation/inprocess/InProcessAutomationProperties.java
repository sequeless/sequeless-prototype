package org.sequeless.adapter.automation.inprocess;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code sequeless.automation.inprocess.*} configuration namespace this adapter reads at
 * startup: the small fixed retry loop {@link InProcessAutomationPort} uses around a {@code
 * sq:Webhook} action's actual HTTP call (see that class's javadoc for why the loop is fixed-delay
 * rather than exponential — that contrast is deliberate, against a future Temporal adapter's
 * configurable {@code RetryOptions}).
 */
@ConfigurationProperties("sequeless.automation.inprocess")
public class InProcessAutomationProperties {

    /**
     * How many total attempts {@link InProcessAutomationPort} makes to deliver a {@code
     * sq:Webhook} action's HTTP request before giving up and throwing {@link
     * WebhookDeliveryException}; includes the first attempt, not just retries. Defaults to 3.
     */
    private int retryAttempts = 3;

    /**
     * The fixed delay {@link InProcessAutomationPort} waits between successive webhook delivery
     * attempts. Defaults to 200 milliseconds.
     */
    private Duration retryDelay = Duration.ofMillis(200);

    /**
     * @return how many total attempts are made to deliver a webhook action's HTTP request
     */
    public int getRetryAttempts() {
        return retryAttempts;
    }

    /**
     * @param retryAttempts how many total attempts to make to deliver a webhook action's HTTP
     *     request
     */
    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    /**
     * @return the fixed delay waited between successive webhook delivery attempts
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * @param retryDelay the fixed delay to wait between successive webhook delivery attempts
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }
}
