package org.sequeless.store.spi;

/**
 * The outbound port an automation adapter calls to persist an {@link AutomationFailure}, so that a
 * workflow whose retries were exhausted leaves a durable trace rather than failing silently.
 *
 * <p>Recording is idempotent on {@link AutomationFailure#workflowId()}: a second record for the same
 * workflow id leaves the first one in place. A failure of the write itself propagates to the caller, so
 * the adapter can retry it or fail visibly; it is never swallowed.
 */
public interface AutomationFailureRecorder {

    /**
     * Persists {@code failure}, unless a record with the same workflow id already exists.
     *
     * @param failure the failure to record; must not be {@code null}
     */
    void record(AutomationFailure failure);
}
