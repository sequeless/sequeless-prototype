# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-25, at origin/main `216e875` (PR #203). The docs PR that carries this handoff lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

## Start a new session

1. Read this file, then `docs/extraction/remaining-work.md` and the spec 13 plan named under "Read first".
2. Check the state against GitHub: `origin/main`, open PRs (#204 is held, and #106 is a draft that must not merge), and issue #41 (OPEN, reopened by the owner on 2026-11-25).
3. Check the Navigator state under "Navigator decisions: verification" first. Session `c4f4f2bb` hit its round limit and is closed. Do not reuse any listed session.
4. Aim the cycle at code. The code-bearing items are under "Next actions". Items that need a Navigator answer stay blocked until the answer is recorded.
5. Before any PR or merge, follow "Hazards".

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate, section 17 is the merge list): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent: [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md) (line 128 is the settling test: "In the plan's order, P2a's PR starts the chain")
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 4 is the change list, section 8 holds the open questions and Decided lines, section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (section 8 holds the open questions)
- Decision record for the flip: [DR-24](../architecture/02-decision-records.md) (actions travel on the TransitionFired row)
- Automation architecture: [docs/architecture/automation.md](../architecture/automation.md)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, OPEN), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Main:** origin/main `216e875` (#203) at the start of this run. No code has landed on main in this cycle. The cycle base is `216e875`. `git diff --name-only 216e875 origin/main -- . ':(exclude)docs' ':(exclude)*.md'` prints nothing.
- **Merged this cycle:** none to main. The cycle's code is on branch `spec-13-claim-index-nontx` (commits `d4fce6f` and `3299e69`), in open PR #204, which is held (see "Next actions", item 4).
- **Open PRs:** #204 (held; do not merge until P3's V9 is on main). #106 (draft compare document; do not merge).
- **Issue #41:** OPEN. The owner reopened it on 2026-11-25.
- **Migrations on main (Command set):** V1, V3, V4, V5, V6, V7, V8 (`V8__outbox_actions_drained.sql`, PR #168). The next free number is V9, which P3 takes. The index migration is V10 on PR #204. The Query set on main (`postgres-query`) has V1 and V2 and is a separate sequence.
- **Superseded branches:** `origin/spec-13-dead-letter-index-v9` (`87f5e42`) is replaced by PR #204. `origin/spec-13-dead-letter-index-v8` (`6cf5938`) is stale and must not be merged. Delete both only after PR #204 merges, on the owner's go-ahead.

## Navigator decisions: verification

The record flow (the `navigator:pair` skill's flow, adopted by the owner on 2026-11-25):

- A Navigator decision counts once the driver has recorded it with `navigator_record` (kind `decided`), quoting the Navigator's reply in `text` and naming the source check in `rationale`, and `navigator_status` shows it in the DECIDED list of the session that made it.
- A `set_plan` or `decide` answer, a job answer, a CARRY FORWARD line or a relayed claim is not a decision until it is recorded this way.
- Before the next `navigator_ask`, record one `carry_forward_verdict` per CARRY FORWARD line: `kept`, or `wrong` with evidence. A claim the source contradicts is recorded as `rejected_claim`.
- Only the driver records decisions. One question per `navigator_ask`. A new question or batch goes to a fresh session, at most five questions per batch.

Decided this run (each shows in the session's DECIDED list, checked with `navigator_status`):

- **Session `8af75131ba5d4b6dbf33036c37875d05`** (batch of five, 2026-11-25):
  - Q1, marker: keep `-- flyway:executeInTransaction=false`. It needs `flyway.postgresql.transactional.lock=false` in the Command configuration.
  - Q3, merge order: hold the index migration (V10) until P3 merges. This follows the owner's rule that the index migration lands after P3.
  - Q4, O-4 count: the Temporal part covers running ActionWorkflow and TimerWorkflow executions. This is wider than the owner's ActionWorkflow-only text, and it keeps every owner check.
  - Q5, B2: route 1 (read the active version; on a stamp mismatch, fail and retry, which DR-23 makes retry three times, then dead-letter). The owner may confirm.
  - Q2 (the lock setting as its own question) was not asked. Q1's answer covers it.
- **Session `c4f4f2bbecca4223816663cbc1b096cb`** (code-unit question): round limit reached, no decision. The question is superseded by "Next actions", items 1 and 2. Do not reuse.

Earlier sessions (state as recorded by earlier runs):

- `09480ec46e184c8aa8dc03436a07e826` (P3 migration number, Option B: P3 takes V9; the index branch's PR renames to V10 if it lands later). The owner also decided this.
- `0739f198e75c403ab527d4ad6cc82164` (OQ15, option (a): follow spec line 128).
- `cf09351ce0a144538f114de334e5d631` (OQ15 starting point: P2a, from spec line 128).
- `c0e84f0a157f4cc1a0f5dc3f54952ce2` (P2e Q1 to Q12; P3 OQ1, OQ2, OQ3, OQ4 revised, OQ7, OQ8, OQ11 revised, OQ12, OQ13; do not resume).
- `cc206f68705e4d93956f7e58ddd8c6e9` (P2d Q-A, Q-B, Q-C, Q-E, DR-24; do not resume).
- `95d0ab97a06845088d993ce3caa0f05e` (route (a), the fresh-session route; do not reuse).
- `acac3649758f47d2ad75687c198f13e8` (P4 answer rejected on source; nothing decided).
- Not decided (`DECIDED: (none)`): `32ac8aeab08346b6b67e54a49c2e1d44`, `ac015364523740e6b2eba306f8d31515` (P4 OQ5 to OQ9), `54529a32ee4b47b9a3ae073cabcbcb4e` (P4 OQ11, OQ12, OQ14, OQ17), `92e8178d1fdf4829837dab8eb4d0ef95`, `359d962fb8cd498b8b34339abd46e5bc`, `cb5a1ebdeb804ce893b35dced364435c`, `b150e05389fd46148771bec93b8bc980`, `c07146c090c444b18302e90478418aa7`.
- **P3 plan section 8, Decided lines:** written as Decided, but not in any verified DECIDED list: lines 184, 187, 196, 199, 211, 214, 218, 221, 224, 227 and 230 of `spec-13-p3-plan.md`. Line to open question, checked against the file: 184=OQ5, 187=OQ6, 196=OQ9, 199=OQ10, 211=OQ14, 214=OQ15 (P3's OQ15, the in-process growth question, not P4's OQ15), 218=OQ17, 221=OQ18, 224=OQ19, 227=OQ20, 230=OQ21. OQ16 (dev-stack adapter) is answered by the owner (see "Owner decisions").

## Owner decisions (2026-11-25, the grill)

These are the owner's answers, not Navigator decisions. Do not re-ask them.

- **#41:** reopened (done).
- **V9 rename:** accept Navigator Option B. P3 takes V9. The index branch's PR renames to V10 if it lands later (PR #204 is V10).
- **Dev-stack adapter (owner action 3):** temporal. The O-4 count covers the temporal adapter.
- **Claim-index route (owner action 2):** the non-transactional route, with the Flyway marker, sent for a small re-review (PR #204).
- **Re-count before any P3 deploy (owner action 4):** a live Temporal query for running executions (per the Navigator's Q4, ActionWorkflow and TimerWorkflow), plus the `sq_outbox` unprocessed count for every kind. Orchestration runs the count. The owner approves the deploy.
- **Owner action 1, written loss acceptance (Q-A), recorded verbatim:** "I accept", given 2026-11-25.

## Running jobs

- No Navigator job is running. No subagent is running.

## Next actions

The owner's rule: code first, one handoff PR at the end of a cycle, no mid-cycle docs PRs. No code is on main from this cycle.

1. **Verify P3's unverified Decided items** (OQ5, OQ6, OQ9, OQ10, OQ14, OQ15, OQ17, OQ18, OQ19, OQ20, OQ21; the mapping is under "Navigator decisions"). Re-ask in fresh sessions, one question per `navigator_ask`, at most five per batch, before the P3 code that depends on them. Gates, as the earlier handoff recorded them: P3 code items 8, 10, 11, 13, 14, 16, 20, 21, 23a, 23b and 26 depend on OQ5, OQ9, OQ10, OQ17, OQ18, OQ19, OQ20 or OQ21. OQ6 and OQ14 gate the B2 wording and DR-25. OQ15 is unverified too; check which P3 items it gates when it is asked.
2. **P3 plan item 29c versus the chain start.** Item 29c says P3 saves the post-P2 set. Spec line 128 says P2's PR saves its own set. Ask a fresh session which phase saves the post-P2 set. Also fix the stale "starting point is open" wording in P3 plan lines 117, 130 and 236, and in P4 plan lines 175, 248 and 250, citing session `cf09351c`. P3 plan line 190 (V10 only if the index is merged) contradicts the owner's V9 rule. Fix it in the P3 PR.
3. **Frozen set for the index migration (not yet asked).** Spec line 128 asks each step that adds a Command migration to save its merged set as a frozen copy and add a settling pair. Ask a fresh session whether V10 must do that. P2d (V8) saved no set. Until this is answered, PR #204 has no frozen copy.
4. **Index migration, PR #204 (held).** Its state shows BLOCKED with the required checks green; that is the hold, not a failure. Merge only after P3's V9 is on main. Then bring the new main into the branch with `git merge origin/main` (not rebase), rerun the full gate on JDK 25 (the command is in resume plan section 21.6), and merge with `--match-head-commit`. Follow-ups, not blockers: a test for the failed-build path (INVALID index, `flyway repair`, re-run), and an EXPLAIN check that the claim query uses the partial index under a generic plan (`PostgresOutboxPort.java:84`, which predates the PR).
5. **P3 code.** Gated on items 1 to 3 for each unit that depends on them. Implement in a worktree cut from `origin/main`, with the V9 file name. Run a read-only review by a separate subagent, and pin the merge to the reviewed head. Take the O-3 count (zero before any version runs V9) and the O-4 count (the Q4 method) before the P3 deploy. The owner approves the deploy.
6. **P4 code.** Blocked on P3 merging, on the nine P4 answers (OQ5 to OQ9, OQ11, OQ12, OQ14 and OQ17, not decided), and on the temporal adapter being the decided dev stack (owner action 3). P4 plan lines 10, 34 and 171 are stale. Rewrite them after P3 merges.
7. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.

## Owner actions still open

1. **Deploy approval for P3,** and any major release. Not needed now.
2. **Delete the superseded remote branches** (`-v9`, `-v8`) after PR #204 merges. This is a destructive action, so the owner confirms it.

Nothing else is open with the owner. Escalate only for actions on the owner's machines, credentials, or major releases.

## Denials and refusals

- **Refused, then passed on retry.** The auto-mode classifier refused the first merge of PR #177 (the P3 decisions). The next attempt, through the normal route, merged it. Handle a first refusal by trying once through the normal route, then reporting it. Do not work around it.
- **Refused, then reversed.** The classifier denied a Bash write that recorded the P4 answers in the repo. The owner then granted the docs edit. The answers were later found unverified and reverted in #188.

## Loop: how the orchestration runs

- **Orchestrator:** the session that creates the children, and the target of their state reports. The current orchestrator is `session_01VYniu2Woriiut8U9HzyZrf`. A new run uses the ID its parent gives it.
- **Child rules** (from the parent): code first; one handoff PR at the end of a cycle, no mid-cycle docs PRs; write `LIMIT` only when the run cannot continue; write `ALL DONE` only after a code commit is on `origin/main` during the cycle; report the code units tried, what blocks each, and whether code is on main.
- **Parent correction (this cycle):** route every decision through the Navigator. Ask the owner only for actions on the owner's machines, credentials, and major releases.
- **Prompt file:** `prompt.md` is git-ignored on purpose, so it is not in the repo. It exists on the machine where the loop was started, and children read it from its absolute path.
- **Cost:** each child uses the owner's plan usage window. The owner watches it. The owner can stop the loop at any time.
- **Review:** this run's handoff was reviewed by an independent subagent against GitHub's state before it landed.

### Code units tried in this cycle

- **Claim index (V10, non-transactional):** implemented. The full JDK 25 gate passed at `d4fce6f`, and the V10 and settling ITs passed at `3299e69`. Blocked from main by Flyway order: it must wait for P3's V9. The code is on the branch, not on main.
- **P3 code:** not started. Blocked by "Next actions", items 1 to 3 (unverified Decided items and the chain-start conflict).
- **Code-unit question to the Navigator (`c4f4f2bb`):** no answer; round limit reached.

## Drift to check against GitHub

- **#41 is OPEN** (reopened 2026-11-25). Earlier handoffs recorded it as not open.
- **P3 plan line 190** names the superseded `-v9` branch. Its V10 case ("V10 if the index branch is merged") conflicts with the owner's rule (P3 takes V9) if the index merges first. The owner's rule governs. The P3 PR fixes the line.
- **B2 route 1** is recorded in session `8af75131` (this run). `resume-plan-spec13.md` line 159 called it "settled by the Navigator" before any DECIDED list showed it; it now does.
- **The index branch** is `spec-13-claim-index-nontx` (PR #204). The older `-v9` (`87f5e42`) and `-v8` (`6cf5938`) branches are superseded.
- **P4 plan line 10** says P2d (PR #168) is open. It is merged (V8 on main). **P4 plan line 171** says main ends at V7. It ends at V8.
- **P3 plan lines 21 and 71** give `timerKey` at `DefaultTransitionService.java` 430 to 438. On main the helper is at line 436 (used at 408 and 425). The plan's line numbers are stale; cite main's numbers.
- **Navigator file reads** have named paths that do not exist (`com/sequeless/...`, `sequeless-backend/...`, `sequeles-...`). Check every path with `git ls-tree origin/main` before submitting an excerpt.
- **The P2e follow-up commit on main** (`c6e1313`, PR #194) changes code, but its commit subject starts with `docs(core,temporal)`. Cosmetic. Do not rewrite main for it.
- Resume plan sections 21.1 to 21.5 are partly stale. Section 21.6 is the gate.

## Hazards

- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Never put a GitHub closing keyword in front of the spec 13 tracker issue in a PR body or commit, not even in a negation. Use the reference form `Refs` with the issue number.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- **Do not merge PR #204 before P3's V9 is on main.** Flyway rejects a lower version that appears after a higher one on a database that already applied V10. `ignoreMigrationPatterns` ignores only `*:missing` and `*:future`.
- **The advisory-lock setting is required.** `flyway.postgresql.transactional.lock=false` is set in `PostgresMigrations.commandConfiguration` on PR #204. It applies to every Command migration. With the default `true`, Flyway holds an open transaction during the run, and `CREATE INDEX CONCURRENTLY` waits on it. Do not revert it.
- **V10 recovery:** if the CREATE fails, the index may be INVALID and Flyway marks V10 failed. Run `flyway repair` (or delete the failed history row), then restart. V10's `DROP INDEX CONCURRENTLY IF EXISTS` removes an INVALID index.
- **Partial Maven runs give false failures.** Running only some ITs with `-Dit.test` produced a `FileSystemNotFoundException` in `PostgresMigrationFrozenSetsTest`. The full gate passed. Merge only on the full gate.
- Renovate moves main during a session. Re-check `origin/main` before merging.
- Work from a worktree cut from `origin/main`. The primary checkout may be behind; fast-forward it before use.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Push with an explicit refspec (`git push -u origin HEAD:refs/heads/<branch>`), so a branch created from `origin/main` can never update `main`.
- Maven needs the GitHub Packages token, so source the local env file in the same command as every `mvn` run. The gate commands and the JDK 25 path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default `java` is 21; the gate needs 25.
- The first start of a version that runs V8 on a database marks undrained `ActionRequest` rows as processed. Do the O-3 count before that start (P2e plan section 9).
- P3's migration marks unprocessed timer rows as processed, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. The O-4 count (the Q4 method) gates the non-dev deploy.
- A refused action (a permission or classifier refusal) stops the run and is reported verbatim. Do not retry it in another form or work around it.
- A relayed claim, a `set_plan` or `decide` answer, or a CARRY FORWARD line is not a decision until the driver records it with `navigator_record` `decided` and `navigator_status` shows it. Check the reply against source before recording it.
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `set_plan` for the first question of a session and `decide` for later ones. `options` is only valid with `settle`. Only one job runs per server: a `job <id> active` error means wait for that job's result. Rounds can take over an hour. A session can hit its round limit and end (`c4f4f2bb` did). One question per `navigator_ask`.
- Do not reuse these Navigator sessions for a new question: `8af75131ba5d4b6dbf33036c37875d05`, `c4f4f2bbecca4223816663cbc1b096cb`, `cc206f68705e4d93956f7e58ddd8c6e9`, `c0e84f0a157f4cc1a0f5dc3f54952ce2`, `cb5a1ebdeb804ce893b35dced364435c`, `b150e05389fd46148771bec93b8bc980`, `c07146c090c444b18302e90478418aa7`, `09480ec46e184c8aa8dc03436a07e826`, `0739f198e75c403ab527d4ad6cc82164`, `32ac8aeab08346b6b67e54a49c2e1d44`, `ac015364523740e6b2eba306f8d31515`, `54529a32ee4b47b9a3ae073cabcbcb4e`, `92e8178d1fdf4829837dab8eb4d0ef95`, `359d962fb8cd498b8b34339abd46e5bc`, `95d0ab97a06845088d993ce3caa0f05e`, `acac3649758f47d2ad75687c198f13e8` and `cf09351ce0a144538f114de334e5d631`. Ask new questions in fresh sessions.
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact. This applies to this handoff too.
- Machine-local facts (paths, tokens, environment IDs) stay out of the repo.
