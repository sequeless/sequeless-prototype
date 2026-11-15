-- Makes TransitionFired claimable (spec 13, step P2a). Until now the relay never claimed it, so every
-- TransitionFired row written before this migration is unprocessed and would be replayed through the
-- automation port as soon as the kind is claimable. Mark those rows processed first, the same way V4
-- handles the object-lifecycle kinds. Only TransitionFired rows are touched here: ObjectCreated,
-- ObjectUpdated and ObjectDeleted are already claimable, and the rows of the kinds that later steps
-- remove (ActionRequest, TimerScheduled, TimerCancelled, SignalReceived) keep dispatching until the
-- step that removes each kind has drained it.
UPDATE sq_outbox
SET processed_at = now()
WHERE kind = 'TransitionFired' AND processed_at IS NULL;

-- Replaces V4's claim index with one over the eight claimable kinds, TransitionFired included.
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
    ) AND processed_at IS NULL;
