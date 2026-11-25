-- flyway:executeInTransaction=false
-- Narrows the claim index to exclude dead-lettered rows (spec 13, step 4; DR-23). A dead-lettered row
-- keeps processed_at NULL, so it stayed in this index at the head of occurred_at order and every claim
-- walked past it. The kinds and the processed_at predicate are unchanged; dead_lettered_at IS NULL is added.

-- This migration runs outside a transaction so that both statements use CONCURRENTLY and take no
-- ACCESS EXCLUSIVE lock on sq_outbox: reads and writes continue during the build. The cost is that a
-- claim may scan the table for the length of the build.
--
-- If the CREATE fails, the index may be left INVALID. Flyway then marks this version failed. Before the
-- next start, drop the index with DROP INDEX CONCURRENTLY, repair the history, and run the migration again.

DROP INDEX CONCURRENTLY IF EXISTS sq_outbox_unprocessed_dispatchable_idx;

CREATE INDEX CONCURRENTLY sq_outbox_unprocessed_dispatchable_idx
    ON sq_outbox (occurred_at)
    WHERE kind IN (
        'ActionRequest',
        'ObjectCreated',
        'ObjectUpdated',
        'ObjectDeleted',
        'TransitionFired',
        'TimerScheduled',
        'TimerCancelled',
        'SignalReceived'
    ) AND processed_at IS NULL AND dead_lettered_at IS NULL;
