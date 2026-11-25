# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-25, at origin/main `5acb48e` (PR #211), with P4 open as PR #212 at `f6d1408`. The docs PR that carries this handoff lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

## Start a new session

1. Read this file, then the spec 13 P4 plan (its section 8 holds the decisions block) and `docs/extraction/remaining-work.md`.
2. Check GitHub: `origin/main`; open PRs (#212, P4, green and mergeable, not yet merged; #106, a draft that must not merge; the handoff PR); and issue #41 (OPEN).
3. The P4 merge was refused once by the auto-mode classifier. Follow "Next actions", item 1. Do not work around a refusal (see "Hazards").
4. Navigator decisions for P4 are recorded (see "Navigator decisions"). Do not re-ask them. A new question goes to a fresh session, one `navigator_ask` per question, at most five per batch.
5. Before any PR or merge, follow "Hazards".

## Read first

- Spec 13 parent (the settling rule at line 128): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- P4 plan (decisions at the top of section 8; change list in section 4; merge order in section 9): [docs/extraction/spec-13-p4-plan.md](../extraction/spec-13-p4-plan.md). Its line numbers are stale: cite origin/main (or the PR head before merge) with `git grep -n`.
- Resume plan (section 20 is the order; section 21.6 is the gate command): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Decision records: [DR-23](../architecture/02-decision-records.md) (dead-letter), [DR-24](../architecture/02-decision-records.md) (actions on the TransitionFired row), [DR-25](../architecture/02-decision-records.md) (timers from the TransitionFired row), and DR-26 (the synchronous signal; on PR #212)
- Automation architecture: [docs/architecture/automation.md](../architecture/automation.md)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, OPEN), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Main:** origin/main `5acb48e` (#211) at the start of this cycle. No code from this cycle is on main. P3 is on main (#210): its V9, its frozen set and DR-25 are there. So is the claim index (V10, #204).
- **P4 is on PR #212** (branch `spec-13-p4-signals-commands`, head `f6d1408`). Eight commits on top of `5acb48e`, in branch order:
  - `004c292` core: a signal runs its transition in the request (one ChangeSet, C7 409, OQ9 first match, unknown name 400; the `SignalReceived` kind and `SignalEventWorkflow` removed).
  - `759d947` the version-race test and the 409 `transitionName` docs.
  - `a7ac9c4` V11 (`UPDATE ... SET processed_at ... WHERE kind = 'SignalReceived'`), its IT, the post-p4 frozen set, the settling entries.
  - `e06eabe` UI: the default panel's status text, the comments, the specs, the rejected-signal e2e case.
  - `63df79a` DR-26, `automation.md`, the overview row.
  - `b532ad3` javadoc and comment nits from review.
  - `2bf37a4` re-syncs the frozen V11 copy with the live header (the byte-identity test failed without it).
  - `f6d1408` the DR-26 layout and the stale-object clause, and the four-kind counts.
- **P4 gate (local):** `mvn -B -Prelease verify` on JDK 25 at `f6d1408`: BUILD SUCCESS, all 10 reactor modules, 3m14s. `ng test`: objects 149 of 149, automation 97 of 97 (at `e06eabe`).
- **P4 CI (GitHub, `f6d1408`):** `build / maven`, `ui`, SonarCloud and the CodeQL jobs all pass. `mergeStateStatus` is CLEAN.
- **P4 e2e not run:** Playwright `automation-signals.spec.ts` was not run; the environment cannot start a live stack (owner action 3). The rejected-signal case is unverified live.
- **P3 deploy is NOT done.** P4 is not deployed either. Both need the owner's approval and the counts under "Owner actions open".
- **Migrations on main (Command):** V1, V3 to V10. V11 is on PR #212 (not on main). The next free number after V11 is V12.
- **Query migrations on main:** V1 and V2, a separate sequence.
- **Frozen sets on main:** `3328993`, `fbb82c5`, `post-p7/command`, `post-p2/command`, `post-p3/command`. PR #212 adds `post-p4/command` (V1, V3 to V11).
- **DRs on main:** DR-20 to DR-25. DR-26 is on PR #212. The next is DR-27.
- **Open PRs:** #212 (P4; see "Next actions", item 1). #106 (draft compare document; do not merge).
- **Issue #41:** OPEN.
- **Superseded remote branches (owner action 2):** `origin/spec-13-dead-letter-index-v9` (`87f5e42`) carries a `V9__outbox_dead_letter_index.sql` that collides with main's V9. `origin/spec-13-dead-letter-index-v8` (`6cf5938`) is stale. Both are superseded; the owner confirms their deletion.
- **Merged branches still on the remote (delete on the owner's go-ahead):** `spec-13-p3-replay-test`, `spec-13-p3-timer-lookup`, `spec-13-p3-cut-over`, `spec-13-claim-index-nontx`. After #212 merges, `spec-13-p4-signals-commands` joins this list.

## Navigator decisions

The record flow (`navigator:pair`): a decision counts once it is recorded with `navigator_record` (kind `decided`) and `navigator_status` shows it in that session's DECIDED list. Earlier decisions and their sessions are in the previous handoff's history and in the P3 and P4 plans. Do not reuse any Navigator session from an earlier handoff. The P4 decisions came from sessions `74165dcd1aef4042872e1eafbcae492b` and `b1d038683633493cb43ea6656ffdd217`.

**P4 decisions (used by PR #212; recorded earlier, do not re-ask):**
- OQ5: a state-less object's signal, or no transition from the current state, is rejected with 409 `transition-not-available` (C7).
- OQ6: a version race keeps `existing.version()` and the existing 409 `stale-object` mapping.
- OQ7: the signal body is dropped. The REST contract still accepts it. The owner may revisit this.
- OQ8: an ontology reload during a signal is not mapped; it surfaces as HTTP 500. The owner may prefer a 409 or 503 later.
- OQ9: several transitions match one name: the first in transition order is taken; a failing guard is rejected with 409 (no fall-through).
- OQ11: V11 stamps the leftover `SignalReceived` rows, including dead-lettered ones.
- OQ12: the reopen e2e test asserts the state directly after the 202.
- OQ14: P4's decision record is DR-26.
- OQ17: the default panel's status text changes to "the transition is applied when the request is accepted". The live panel's status text is kept (see "Next actions", item 2).
- Chain start (spec line 128): P4's PR adds the settling pair from post-P3 to the final set. The P4 plan's body still calls the starting point open; its decisions block supersedes that text.

**This cycle:** no new Navigator question was asked. The P4 code used only the decisions above.

**Open for the Navigator (ask in a fresh session):** OQ17's consequence. The live panel (`signals-panel.ts`, status text, about line 61) still says "takes effect asynchronously", which is now false after P4. The decision kept it. Ask whether it changes, and to what wording.

## Owner decisions

These are the owner's answers. Do not re-ask them.

- **#41:** reopened (2026-11-25). Spec 13 is not finished.
- **V9 rename:** Navigator Option B. P3 takes V9; the claim index is V10 (#204, now on main).
- **Dev-stack adapter:** temporal.
- **Claim-index route:** the non-transactional route, with the Flyway marker.
- **Re-count before any deploy:** a live Temporal count of running executions of the types the step changes (P3: `ActionWorkflow` and `TimerWorkflow`; P4: `SignalEventWorkflow`), plus the `sq_outbox` unprocessed count for every kind. Orchestration runs the count. The owner approves each deploy.
- **Loss acceptance (Q-A):** "I accept", given 2026-11-25.

## Owner actions open

1. **Deploy approval for P3, then for P4.** No deploy is automatic. Before each deploy:
   - Stop the old version, then run the O-3 count: `sq_outbox` unprocessed rows for every claimed kind, on every database the new version migrates, dev included (`spec-13-p3-plan.md` OQ9).
   - Run the live Temporal count of running executions of the types the step changes (above). The O-4 loss gate applies to non-dev databases only (`spec-13-p3-plan.md` OQ19).
   - If loss is accepted for running executions, terminate the listed ones before the new version starts (spec step 4, line 105).
   - P3's new version must not start while any `TimerScheduled` or `TimerCancelled` row is unprocessed (V9 marks them processed, and their timers are lost with no record).
   - V11 must not start while any `SignalReceived` row is unprocessed.
   - The owner approves.
2. **Merge PR #212 if the next session cannot.** The squash merge was refused by the auto-mode classifier once (see "Hazards"). The owner approves the merge or runs it.
3. **Playwright e2e environment.** On the owner's machine, Docker Desktop did not publish container ports to the host, so the e2e could not start. Fix that, or confirm the e2e runs somewhere else, before the next UI change.
4. **Delete the superseded remote branches** `spec-13-dead-letter-index-v8` and `-v9`, and the merged branches listed under State (destructive; the owner confirms).
5. **Two Navigator decisions with consequences the owner may want to change:** OQ7 (the signal body is no longer recorded) and OQ8 (an ontology reload during a signal returns HTTP 500).

Nothing else is open with the owner. Escalate only for actions on the owner's machines, credentials, or major releases.

## Next actions

The owner's rule: code first, one handoff PR at the end of a cycle, no mid-cycle docs PRs.

1. **Merge PR #212.** Re-check its head SHA first. Then run `gh pr merge 212 --squash --match-head-commit <full 40-character head SHA>`, with no `--admin`. Attempt it once. If the merge is refused, stop, report the refusal verbatim, and do not retry in another form (owner action 2). Do not delete the remote branch (owner's call). After a successful merge, fast-forward the primary checkout and confirm `origin/main` contains the squash commit.
2. **Ask the Navigator the OQ17 follow-up** (one question, fresh session; see "Navigator decisions"). If the answer changes the live panel's wording, make that change in a small code PR.
3. **P5b (Store-event ingress).** Its plan can start now: resume plan section 21.2 (S8) puts the P5b plan after the P4 decisions, which are recorded. Write the P5b plan and get its read-only review before code. Ask the Navigator any question the plan raises (one `navigator_ask` per question, at most five per batch). P5b code waits for #212 to merge (P4 plan section 9, item 5).
4. **Deploys (P3, then P4):** owner action 1. No deploy is automatic.

## Units this cycle (tried and status)

- **P4 core** (`004c292`): on the branch, in PR #212. Reviewed by an Opus subagent (approved).
- **P4 version-race test and docs** (`759d947`): on the branch. Reviewed (approved).
- **P4 persistence (V11, IT, post-p4 set)** (`a7ac9c4`, re-sync `2bf37a4`): on the branch. Reviewed (approved). The re-sync was needed because a later header edit left the frozen copy stale; the byte-identity test caught it at the first gate run.
- **P4 UI** (`e06eabe`): on the branch. Reviewed (approved). Playwright not run (see State).
- **P4 docs** (`63df79a`, `f6d1408`) and **javadoc nits** (`b532ad3`): on the branch. Reviewed (approved after the fixes).
- **Blocked from main:** the squash merge of PR #212 was refused by the auto-mode classifier on the first attempt, through the normal `gh pr merge` route. It was not retried in another form (see "Hazards").
- **P5b:** plan not started; code not started (waits on #212 and its plan review).

**Code on origin/main this cycle:** none.

## Drift to check against GitHub

- **P4 plan line numbers are stale** (the plan's own notes say so). Cite origin/main, or the PR head before merge, with `git grep -n`.
- **The P4 plan body's OQ15 starting point** still says "open". The decisions block settles it (chain start from post-P3, spec line 128).
- **The live panel's status text** (`signals-panel.ts` line 61) is inaccurate after P4 (see "Navigator decisions").
- **V9's header** cites DR-23 for the timer rows; the timer decision is DR-25. Not changed in this cycle; a later docs fix can correct it.
- **P3 plan line numbers and the timer Decided lines** are stale (as recorded in the previous handoff).
- **The P2e follow-up commit on main** (`c6e1313`, PR #194) changes code, but its subject starts with `docs(core,temporal)`. Cosmetic. Do not rewrite main for it.

## Hazards

- **A refused action stops the run.** The P4 squash merge was refused by the auto-mode classifier. Try it once through the normal route, then report it verbatim. Do not retry it in another form and do not work around the refusal.
- **Docker Desktop does not publish container ports to the host** on the owner's machine. Testcontainers-based ITs still pass (the full gate ran). The Playwright e2e cannot run until owner action 3 is resolved.
- **Do not touch containers or processes you did not start.** Another session's container runs on this machine. Do not stop, remove, or connect to it.
- **A rework subagent made one stray `archive_session` call** with a placeholder ID. It errored and changed nothing. Do not archive sessions without an explicit decision.
- **Advisory-lock setting:** `flyway.postgresql.transactional.lock=false` is set in `PostgresMigrations.commandConfiguration` and applies to every Command migration. V10 (`CREATE INDEX CONCURRENTLY`) needs it: with the default `true`, Flyway holds a transaction open and the index build waits on it. Do not revert it.
- **Flyway order:** a new version must sit above V11, the highest applied so far. Never insert a lower version after a higher one has been applied on a database. `ignoreMigrationPatterns` ignores only `*:missing` and `*:future`.
- **V11 is not applied to any database.** Its header was changed before merge, which is safe only because no database has applied V11. Once a database applies it, do not change V11's bytes.
- **Do not change V9's bytes after merge** unless the frozen post-P3 copy changes with it; the checksum and the byte-identity test both depend on it.
- **Do not merge `spec-13-dead-letter-index-v9`.** Its V9 collides with main's V9.
- **V8, V9 and V11 stamp dead-lettered rows of their kind.** Dead-lettered rows keep `processed_at` NULL, so the stamp covers them.
- **Partial deploy risk:** an old relay that still claims `SignalReceived` and dispatches to the new adapter reaches the `default` branch and throws `IllegalArgumentException`. The mixed-version order (spec lines 100-108) prevents it; do not skip the drain.
- **Never use `--admin`** to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- **Never put a GitHub closing keyword** in front of issue #41 in a PR body or commit. Use `Refs #41`.
- **Use `gh pr merge --match-head-commit` with the full 40-character SHA.**
- **Merge `origin/main` into a branch; do not rebase.** When main has a squash of a branch's commits, the merge can conflict on the duplicated commits. Resolve by taking the branch's side only after checking that the resulting tree equals the gated commit (`git diff <gated-sha> -- .` must be empty).
- **Partial Maven runs give false failures.** Running only some ITs with `-Dit.test` (or `-Dfailsafe.failIfNoSpecifiedTests=false`) produced a `FileSystemNotFoundException` in `PostgresMigrationFrozenSetsTest`. Merge only on the full gate.
- **The dependency analyser runs in `verify`.** Declare every kernel artifact a module's main code imports.
- **Renovate moves main during a session.** Re-check `origin/main` before a merge.
- **Work from a worktree cut from `origin/main`.** The primary checkout may be behind.
- **Push with an explicit refspec** (`git push -u origin HEAD:refs/heads/<branch>`), so a branch created from `origin/main` can never update `main`.
- **Maven needs the GitHub Packages token**, so source `~/.bashrc.ext` in the same command as every `mvn` run. The gate uses JDK 25; the default `java` is 21.
- **Do not put model names** in commits, PR titles or bodies, or any other pushed artifact. This applies to this handoff too.
- **Machine-local facts** (paths, tokens, environment IDs, container names) stay out of the repo.

## Loop: how the orchestration runs

- **Orchestrator:** the session that creates the children, and the target of their state reports. The current orchestrator is `session_01VYniu2Woriiut8U9HzyZrf`. A new run uses the ID its parent gives it.
- **Child rules** (from the parent): code first; one handoff PR at the end of a cycle, no mid-cycle docs PRs; write `LIMIT` only when the run cannot continue; write `ALL DONE` only after a code commit is on `origin/main` during the cycle, and only when no work item remains; report the code units tried, what blocks each, and whether code is on main.
- **Owner questions:** route every design, route, migration-number and count question to the Navigator. Ask the owner only for actions on the owner's machines, credentials, and major releases.
- **Prompt file:** `prompt.md` is git-ignored. It exists on the machine where the loop was started, and children read it from its absolute path.
- **Cost:** each child uses the owner's plan usage window. The owner can stop the loop at any time.
