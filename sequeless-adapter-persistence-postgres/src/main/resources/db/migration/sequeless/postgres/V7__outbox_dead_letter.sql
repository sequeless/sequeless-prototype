-- Dead-letters an outbox row whose dispatch keeps failing (spec 13, step 4; DR-23). A failed dispatch
-- rolls back its claim, so the row keeps processed_at NULL and is claimed again on a later poll. The
-- failure is counted in fail_count in a separate transaction after that rollback. At the third failed
-- dispatch the same transaction stamps dead_lettered_at and error, and PostgresOutboxPort's claim skips
-- the row, so the rows behind it keep flowing. A dead-lettered row keeps processed_at NULL, so it still
-- counts as unprocessed for the owner's O-3 check.
--
-- sq_outbox.attempts (V1) stays write-only and is not reused: it never counted failures.

ALTER TABLE sq_outbox
    ADD COLUMN fail_count int NOT NULL DEFAULT 0,
    ADD COLUMN dead_lettered_at timestamptz,
    ADD COLUMN error text;
