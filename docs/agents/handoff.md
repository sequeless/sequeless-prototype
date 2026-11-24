# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `404cfa1` (PR #190). This docs PR lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

## Start a new session

1. Read this file, then `docs/extraction/remaining-work.md` and the spec 13 plan named under "Read first".
2. Check the state against GitHub: `origin/main`, open PRs (only #106 is open, a draft, do not merge), and issue #41 (CLOSED; reopening is the owner's decision).
3. Find the next unblocked item under "Next actions". Items that need a Navigator answer or an owner answer are blocked until that answer is recorded.
4. Before recording any decision, check its Navigator session with `navigator_status` (see "Navigator decisions: verification"). A claim relayed from another session is not a decision.
5. Before any PR or merge, follow "Hazards".

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate, section 17 is the merge list): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent (B2 is decided; see Navigator decisions for its provenance): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 5 "Number" and section 8 hold the migration decision and the Decided lines; section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (section 8 holds the open questions; OQ15 is open, see Navigator decisions)
- Decision record for the flip: [DR-24](../architecture/02-decision-records.md) (actions travel on the TransitionFired row)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, CLOSED; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Merged, 2026-11-24:**
  - PR #182, the P2e code (`a8816f3`, pinned to head `481113d`). It removes `ActionWorkflow` and the `ActionRequest` drain.
  - PR #185, #187, #189, #190: handoffs and docs. PR #186 (nine P4 lines) was withdrawn by #188 (`5b5358c`).
  - The docs PR that carries this handoff, which records the P3 migration-number decision (Option B) in the P3 plan and corrects the held branch name to `-v9`. Its merge SHA is in `git log origin/main`.
- **Open PRs:** only #106, a draft compare document. Do not merge it.
- **Issue #41:** CLOSED. It closed automatically when PR #169 merged. Its state is not a sign that spec 13 is finished. Reopening it is the owner's decision.
- **Migrations on main:** V1, V3, V4, V5, V6, V7, V8 (`V8__outbox_actions_drained.sql`, PR #168). The next free number is V9.
- **Held index branch:** `origin/spec-13-dead-letter-index-v9` at `87f5e42`. It holds the claim-index migration as `V9__outbox_dead_letter_index.sql`. It is unmerged and has no PR. The older `origin/spec-13-dead-letter-index-v8` (`6cf5938`) is stale and adds `V8__outbox_dead_letter_index.sql`, which collides with main's V8. Do not merge it.

## Dev-stack checks, recorded 2026-11-24

These are the P2e plan section 8, step 3 checks. This section is the record.

- Run by the orchestration session at the owner's request, before #182 merged. The dev stack is the compose project `sequeless-prototype-ui`.
- Result, unprocessed rows: `sq_outbox` has 0 rows, so the unprocessed count is 0 for every kind.
- Result, schema: Flyway history lists versions 1 to 4 on that database. V8 and later have not run there.
- Result, running `ActionWorkflow` executions: 0. No Temporal state volume exists on this Docker host. This is inferred from the missing volume, not from a live Temporal query.
- No sequeless container or application process was running, so no stop or drain step was needed.
- The owner's written loss acceptance (Q-A) was not given and was not needed, because the count was zero.
- A new check is needed before any P3 deploy: re-run the O-3 count (see Owner actions).

## Owner decisions and waivers recorded

- **Merge of #182 (2026-11-24).** The owner asked for the dev-stack checks to be run first. They passed, and #182 merged.
- **Merge of #168 (2026-11-24).** The owner instructed the merge directly. Recorded in the Navigator session `c0e84f0a` DECIDED list.
- **Withdrawn:** the nine P4 answers in #186. No Navigator session decided them. See Drift.

## Navigator decisions: verification

A Navigator decision counts only if it appears in the DECIDED list of the session that made it, as shown by `navigator_status`. A decision mentioned in CARRY FORWARD notes, a relayed report, or a session's reply does not count.

- **Session `09480ec46e184c8aa8dc03436a07e826`** (this cycle, P3 migration number, Q1 and Q2). Decided (count 1 after recording): **Option B.** P3 takes V9 now and does not wait. If P3 merges before the held index branch, that branch's own PR renames `V9__outbox_dead_letter_index.sql` to V10. The stale `-v8` branch must not be merged. Its reply was checked against the sources: the main migration list, the two index branches (`git ls-tree`, `git show --stat`), and the origin/main 404cfa1 handoff, lines 35, 36 and 44. One carry-forward line was wrong: it said P3 plan line 190 was unverified. The origin/main 404cfa1 handoff, line 63, verifies OQ7 in `c0e84f0a`, so line 190 is verified.
- **Session `0739f198e75c403ab527d4ad6cc82164`** (this cycle, OQ15, P4 saved sets). The question was sent after the one-job-per-server limit cleared. The Navigator asked for file reads, and the driver sent excerpts (spec lines 122 to 134; P4 plan lines 246 to 248 and 268 to 276). **Outcome: see Next actions item 2.** If no DECIDED entry appears in this session, OQ15 stays open.
- **Session `c0e84f0a157f4cc1a0f5dc3f54952ce2`** (round limit reached; do not resume). Verified in its DECIDED list: P2e Q1 to Q12 and the P3 OQ1, OQ2, OQ3, OQ4 (revised), OQ7, OQ8, OQ11 (revised), OQ12 and OQ13. Not in its DECIDED list (unverified): P4 OQ15 (appears only in its CARRY FORWARD notes), and the P3 plan Decided lines at 184, 187, 196, 199, 211, 214, 218, 221, 224, 227 and 230. The P3 plan line 190 (OQ7) is verified.
- **Session `cc206f68705e4d93956f7e58ddd8c6e9`** (round limit; do not resume). Verified: P2d Q-A, Q-B, Q-C, Q-E, and DR-24.
- **Session `cb5a1ebdeb804ce893b35dced364435c`** (earlier P3 migration number). Its DECIDED list is empty. Superseded by `09480ec46e184c8aa8dc03436a07e826`.
- **Session `b150e05389fd46148771bec93b8bc980`** (P4 answers). Its DECIDED list is empty. The nine answers are not decisions. They were reverted in #188.
- **B2, route 1:** not verified against any session checked here. Treat it as unverified until a session's DECIDED list shows it.
- **P2e plan lines other than those above:** not audited.

## Denials and refusals

- **Refused, then passed on retry.** The auto-mode classifier refused the first merge of PR #177 (the P3 decisions). The next attempt, through the normal route, merged it. Handle a first refusal by trying once through the normal route, then reporting it. Do not work around it.
- **Refused, then reversed.** The classifier denied a Bash write that recorded the P4 answers in the repo. The owner then granted the docs edit. The answers were later found unverified and reverted in #188.

## Next actions

1. **P3 migration number: settled (Option B, session `09480ec46e184c8aa8dc03436a07e826`).** The P3 plan (section 5, section 8 item 7, and section 4 items 20 and 21) uses V9. No code change is needed for the number.
2. **OQ15 (P4 saved sets). Gates the P3 PR (P3 plan item 29c).** Outcome of session `0739f198e75c403ab527d4ad6cc82164` is open at the time of writing. If its DECIDED list shows an option, record it in P4 plan section 8 and in this file, citing the session. If it shows no decision, ask again in a fresh session with the excerpts from this run. Do not reuse `0739f198`.
3. **P3 code.** Blocked on item 2 (OQ15). Once OQ15 is decided, implement P3 with a subagent in a worktree cut from `origin/main`, use the V9 file name, and run a read-only review. Pin the merge to the reviewed head. Re-run the O-3 count before any P3 deploy.
4. **Unverified P3 Decided lines** (listed under session `c0e84f0a`, lines 184, 187, 196, 199, 211, 214, 218, 221, 224, 227 and 230). Re-ask them in a fresh session, or leave them open for the owner. Do not treat them as decided.
5. **P4 open questions.** Ask OQ5, OQ6, OQ7, OQ8, OQ9, OQ11, OQ12, OQ14 and OQ17 in batches of at most five, each in a fresh session. Give the proposed answers from P4 plan section 8 as unverified inputs. Record only decisions the session shows. OQ16 is the owner's.
6. **P2e follow-ups.** The "Known non-blocking follow-ups" in PR #182's body. This is a separate PR (`Refs #41`). Code changes need the gate (resume plan 21.6) and a read-only review. Pin the merge to the reviewed head.
7. **P4 code.** Blocked on P3 merging, on items 2 and 5 and on owner action 3 (the dev-stack adapter). Before code, a read-only review of the P4 plan. P4 plan lines 34 and 171 assume main ends at V7; V8 is on main. Rewrite them after P3 merges.
8. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.

## Owner actions still open

1. **#41:** reopen or not. Also the written loss acceptance (Q-A), which only a later hand-stamp needs. It is not needed for P2e.
2. **Claim-index window** (decision ix in `remaining-work.md`) for `spec-13-dead-letter-index-v9` (`87f5e42`). Pick the window for the exclusive-lock rebuild, or ask for the non-transactional route. This gates the index branch merge.
3. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. Say which adapter the dev stack runs. P4 code waits on this.
4. **Re-run the O-3 count** on dev before any P3 deploy (and before the first start of a version that runs V9).
5. **Who renames the index file from V9 to V10** if P3 merges first: answered by the Navigator (Option B, session `09480ec46e184c8aa8dc03436a07e826`). The index branch's own PR does the rename. The owner may override.
6. Optional: confirm B2 route 1 on #41.

## Loop: how the orchestration runs

This is the procedure a new orchestration session follows. The loop runs child sessions one after another. Each child does a bounded amount of work, then reports back.

- **Orchestrator** is the session that creates the children. Its ID is needed for the children's reply. A new orchestrator uses its own session ID. The previous orchestrator was `session_01VYniu1iGuKriV19CtTjn2U`.
- **Environment:** find the bridge environment for this machine with `list_environments`. Use it for every child. Do not hard-code it in the repo.
- **Child settings:** the smaller model, effort `xhigh`, `extra_allowed_tools` set to `["mcp__claude-code-remote__send_message"]`, and a title of `prompt-cycle-<n>`.
- **Child prompt** (send it verbatim, with the orchestrator ID and the state filled in):

  ```
  Read prompt.md and follow its instructions. It is at <absolute path to prompt.md on this machine>. If you cannot read it, send an error line as your last line and stop.

  Limit: stop after a work item makes it on main and all subagents have had a chance to gracefully stop.
  Output: write only terse one-line updates.
  Last line: if no work remaining is available and no subagent operates, write ALL DONE. If you stop at the limit, write LIMIT.

  When you stop, send a message with the send_message tool to <orchestrator session ID>. The message must contain the state of the remaining work (what is merged, what is open and why, and the next action), followed by your last line exactly as written above (ALL DONE or LIMIT).

  State from the previous session (verify against GitHub; it may be stale): <paste the state from the last report>
  ```

- **After each report:** check `origin/main` and the open PRs yourself, archive the finished child, then create the next child with the state from its report. Stop when the last line is ALL DONE, or when the owner says stop.
- **Prompt file:** `prompt.md` is git-ignored on purpose, so it is not in the repo. It exists on the machine where the loop was started, and children read it from its absolute path. On another machine, recreate it from the repeatable prompt text the owner pastes.
- **Cost:** each child uses the owner's plan usage window. The owner watches it. The owner can stop the loop at any time.

## Drift to check against GitHub

- **#41 is CLOSED** (since PR #169 merged). Spec 13 is not finished.
- **The P4 nine answers** in PR #186 were withdrawn in #188 (see above).
- **OQ15** is open (see Navigator decisions). The P3 plan's lines 117 and 130 cite OQ15 option (a) as decided. That citation is unverified; the docs PR flags both lines UNVERIFIED. The resume plan (lines 405 to 406) lists OQ15 as ALL PENDING.
- **The P3 migration number** is settled by Option B (session `09480ec46e184c8aa8dc03436a07e826`), not by `c0e84f0a`'s relayed line.
- **The held index branch name** was written as `-v8` in the P3 plan (section 5 and section 8, item 7). It is `-v9` (`87f5e42`). The plan was corrected in the docs PR that carries this handoff.
- Resume plan sections 21.1 to 21.5 are partly stale. Section 21.6 is the gate.

## Hazards

- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Never write `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves` or `resolved` next to #41 in a PR body or commit, not even in a negation. Write `Refs #41`.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check `origin/main` before merging.
- Work from a worktree cut from `origin/main`. The primary checkout may be behind; fast-forward it before use.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Push with an explicit refspec (`git push -u origin HEAD:refs/heads/<branch>`), so a branch created from `origin/main` can never update `main`.
- Maven needs the GitHub Packages token, so source the local env file in the same command as every `mvn` run. The gate commands and the JDK 25 path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default `java` is 21; the gate needs 25.
- The first start of a version that runs V8 on a database marks undrained `ActionRequest` rows as processed. Do the O-3 count before that start (P2e plan section 9).
- P3's migration marks unprocessed timer rows as processed, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. The O-4 check gates only non-dev environments (P3 plan section 8, question 19).
- A refused action (a permission or classifier refusal) stops the run and is reported verbatim. Do not retry it in another form or work around it.
- A relayed claim is not a decision. Check the session's DECIDED list before recording a decision as decided. `navigator_status` returns large output; if it is saved to a file, read it with `jq`.
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `set_plan` for the first question of a session and `decide` for later ones. `settle` and `answer` are not offered in pair mode. `options` is only valid with `settle`. Only one job runs per server: a `job <id> active` error means wait for that job's result. Rounds can take over an hour. Batch related questions into one message per decision point, at most five per batch.
- Do not resume Navigator sessions `cc206f68705e4d93956f7e58ddd8c6e9`, `c0e84f0a157f4cc1a0f5dc3f54952ce2`, `cb5a1ebdeb804ce893b35dced364435c` or `b150e05389fd46148771bec93b8bc980`. Ask new questions in fresh sessions. Session `09480ec46e184c8aa8dc03436a07e826` (P3 migration number) is decided; do not reuse it for new questions. Session `0739f198e75c403ab527d4ad6cc82164` (OQ15) is awaiting an answer; do not reuse it for a new question.
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact.
- Machine-local facts (paths, tokens, environment IDs) stay out of the repo.

## Local state on this machine (not in the repo)

- `prompt.md` at the repo root, git-ignored. Children read it from its absolute path.
- Worktrees under `/Users/bbartolome/workspace/sequeless-prototype.worktrees/`. Their branches are pushed. Nothing in them is needed to resume, except as noted above.
- Temporary copies under `/tmp` are not needed. The results they held are recorded in this file.
