package org.sequeless.store.spi;

import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.TransitionRef;

/**
 * The durable record that an automation workflow's activity failed after its retries were exhausted.
 * An adapter builds one, and an {@link AutomationFailureRecorder} persists it, so the failure stays
 * visible after the workflow has ended.
 *
 * @param scope the tenant and principal the workflow ran under; only the tenant is persisted
 * @param workflowId the adapter's workflow id for the failed execution, unique across tenants
 * @param workflowType the workflow type that failed, for example {@code ChangeEventWorkflow}; must not be
 *     blank
 * @param transition the transition the failed action belonged to, or {@code null} for a change event
 * @param cause the failure's type and message, as the workflow saw them; must not be blank
 */
public record AutomationFailure(
        Scope scope, String workflowId, String workflowType, TransitionRef transition, String cause) {

    public AutomationFailure {
        Objects.requireNonNull(scope, "scope must not be null");
        requireNonBlank(workflowId, "workflowId");
        requireNonBlank(workflowType, "workflowType");
        requireNonBlank(cause, "cause");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AutomationFailure " + name + " must not be blank");
        }
    }
}
