-- Marks the timer rows that the drain left unprocessed (spec 13, step P3; DR-23). Flyway applies this once,
-- at app start. It runs after the old version has drained the TimerScheduled and TimerCancelled rows, and
-- after the owner's O-3 count, the deploy step that must show zero unprocessed rows of each claimed kind
-- before the new version starts. This statement is the backstop for any row that the count missed.
-- It stamps only those two kinds; the rows of every other kind keep processed_at NULL.
--
-- Marked, not dispatched: P3 removes both kinds from the code (ac46d8f), so no version that runs this
-- migration has a handler for them. The P3 code starts a timer from its TransitionFired row instead. A timer
-- row still unprocessed here is marked without dispatch, so its timer does not run and leaves no record.
--
-- Dead-lettered timer rows (V7) keep processed_at NULL, so this statement stamps them too, as V8 stamps
-- dead-lettered ActionRequest rows (open question 8).
UPDATE sq_outbox
SET processed_at = now()
WHERE kind IN ('TimerScheduled', 'TimerCancelled') AND processed_at IS NULL;
