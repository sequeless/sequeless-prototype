-- Widens the "unprocessed, claimable" partial index from ActionRequest alone to the full
-- dispatchable set introduced alongside TriggerSpec/derived-property automation: the three
-- object-lifecycle kinds and the three new automation kinds. OutboxEntry.KIND_TRANSITION_FIRED
-- stays audit-only and is deliberately excluded here -- nothing ever claims it (see OutboxPort's
-- javadoc) and indexing it as claimable would just be dead weight.
--
-- V3's index only ever covered 'ActionRequest', so it must be dropped before the wider one is
-- created -- Postgres does not let two partial indexes silently coexist as "the" claim index, and
-- keeping the old one around would just be unused bloat.

DROP INDEX sq_outbox_unprocessed_action_request_idx;

CREATE INDEX sq_outbox_unprocessed_dispatchable_idx
    ON sq_outbox (occurred_at)
    WHERE kind IN (
        'ActionRequest',
        'ObjectCreated',
        'ObjectUpdated',
        'ObjectDeleted',
        'TimerScheduled',
        'TimerCancelled',
        'SignalReceived'
    ) AND processed_at IS NULL;

-- Backfill: mark every pre-existing, still-unprocessed row of the six kinds that were NOT
-- previously claimable as already processed. Without this, upgrading a database that already has
-- object-lifecycle history would suddenly make the relay replay that entire history through the
-- brand-new onChange/timer/signal automation paths, firing transitions retroactively for events
-- that predate this feature. ActionRequest rows are deliberately untouched -- they were already
-- claimable before this migration and must keep dispatching normally.
UPDATE sq_outbox
SET processed_at = now()
WHERE kind IN (
    'ObjectCreated',
    'ObjectUpdated',
    'ObjectDeleted',
    'TimerScheduled',
    'TimerCancelled',
    'SignalReceived'
) AND processed_at IS NULL;
