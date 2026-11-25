-- Marks processed the outbox rows that no relay may claim once TransitionFired is dispatched on its own
-- (spec 13, step P2d; DR-24). Flyway applies this once, at app start, to the rows the relay left behind.
-- It stamps only two groups: the ActionRequest rows, and the TransitionFired rows without a `self` key.

-- B1: ActionRequest rows are drained by the old version before this runs (section 8).
-- This statement only stamps any that remain, including dead-lettered ones (V7).
UPDATE sq_outbox
SET processed_at = now()
WHERE kind = 'ActionRequest' AND processed_at IS NULL;

-- Written before #158, without a `self` key. Their actions are their ActionRequest siblings,
-- which the drain ran (B1). Stamping them keeps the new relay from claiming them.
UPDATE sq_outbox
SET processed_at = now()
WHERE kind = 'TransitionFired' AND processed_at IS NULL AND payload -> 'self' IS NULL;
