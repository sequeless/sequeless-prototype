-- Records an automation workflow whose activity failed after its Temporal retries were exhausted (spec 13,
-- R2). The Temporal workflow catches the failure and writes one row here, then ends, so the failure stays
-- visible after the workflow has ended. A new table: no existing row changes.
--
-- workflow_id is the Temporal workflow id of the failed execution. It is the outbox row id for a change
-- event, or the row id with ":" and the action index for a transition action. Both are unique, since the
-- row id is the sq_outbox primary key (V1). A second record for the same workflow id is ignored, so a
-- retried record write keeps the first record.
--
-- transition columns are NULL for a change event.

CREATE TABLE sq_automation_failure (
    workflow_id     text PRIMARY KEY,
    tenant_id       text NOT NULL,
    workflow_type   text NOT NULL,
    machine_iri     text,
    from_state      text,
    transition_name text,
    cause           text NOT NULL,
    failed_at       timestamptz NOT NULL DEFAULT now()
);
