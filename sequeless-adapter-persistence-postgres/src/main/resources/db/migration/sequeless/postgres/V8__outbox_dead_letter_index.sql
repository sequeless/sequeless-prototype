-- Narrows the claim index to exclude dead-lettered rows (spec 13, step 4; DR-23). A dead-lettered row
-- keeps processed_at NULL, so it stayed in this index at the head of occurred_at order and every claim
-- walked past it. The kinds and the processed_at predicate are unchanged; dead_lettered_at IS NULL is added.

-- Replaces V5's claim index with the same eight kinds, now also excluding dead-lettered rows.
DROP INDEX sq_outbox_unprocessed_dispatchable_idx;

CREATE INDEX sq_outbox_unprocessed_dispatchable_idx
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
