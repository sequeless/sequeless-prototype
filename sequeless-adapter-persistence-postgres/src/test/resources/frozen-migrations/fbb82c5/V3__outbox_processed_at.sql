-- Adds the "processed" marker V1's sq_outbox table was missing: a relay claims one unprocessed
-- ActionRequest row at a time (SELECT ... FOR UPDATE SKIP LOCKED, see OutboxPort) and stamps
-- processed_at once the row's action has been durably dispatched. NULL means unprocessed.
--
-- Note: sq_outbox.attempts (added in V1) is write-only today -- every insert sets it to 0 and
-- nothing ever reads or increments it. This migration does not wire it up for retry/backoff
-- bookkeeping; that remains a later task's concern.

ALTER TABLE sq_outbox ADD COLUMN processed_at timestamptz;

CREATE INDEX sq_outbox_unprocessed_action_request_idx
    ON sq_outbox (occurred_at)
    WHERE kind = 'ActionRequest' AND processed_at IS NULL;
