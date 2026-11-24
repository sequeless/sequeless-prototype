# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `5538346` (PR #200). The docs PR that carries this handoff lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

## Start a new session

1. Read this file, then `docs/extraction/remaining-work.md` and the spec 13 plan named under "Read first".
2. Check the state against GitHub: `origin/main`, open PRs (only #106 is open, a draft, do not merge), and issue #41 (CLOSED; reopening is the owner's decision).
3. Find the next unblocked item under "Next actions". Items that need a Navigator answer or an owner answer are blocked until that answer is recorded in a DECIDED list.
4. Before recording any decision, check its Navigator session with `navigator_status` (see "Navigator decisions: verification"). A claim relayed from another session, or an answer that came back through `set_plan` without a DECIDED entry, is not a decision.
5. Before any PR or merge, follow "Hazards".

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate, section 17 is the merge list): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent (B2 is decided; see Navigator decisions for its provenance): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 5 "Number" and section 8 hold the migration decision and the Decided lines; section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (section 8 holds the open questions; OQ15 option (a) is decided, its starting point is open; the other open questions have Navigator answers that are not yet decided, see Navigator decisions)
- Decision record for the flip: [DR-24](../architecture/02-decision-records.md) (actions travel on the TransitionFired row)
- Automation architecture: [docs/architecture/automation.md](../architecture/automation.md)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, CLOSED; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Merged, 2026-11-24 (this cycle merged nothing).** The code and docs that landed before this cycle, in order: PR #182 (P2e code, `a8816f3`), #185, #187, #189, #190 (handoffs and docs), #191 (`a4d08c4`, migration number Option B, held branch `-v9`), #192 (`03da34a`) and #193 (`cc5ceac`) (OQ15 option (a) recorded; starting point open), #194 (`c6e1313`, P2e follow-up comments), #195 (`d14293e`), #196 (`37ead4e`, P2e leftovers), #197 (`8ec0655`, handoff), #198 (`aa48261`, test-only round-trip javadoc and tagged `self` map; gate on head `2c1b503`, JDK 25: 758 tests, 0 failures, 0 errors), #199 (`cb4e010`, handoff), #200 (`5538346`, drops a model name from the handoff). #186 was withdrawn by #188 (`5b5358c`).
- **Open PRs:** only #106, a draft compare document. Do not merge it.
- **Issue #41:** CLOSED. It closed automatically when PR #169 merged. Its state is not a sign that spec 13 is finished. Reopening it is the owner's decision.
- **Migrations on main (Command set):** V1, V3, V4, V5, V6, V7, V8 (`V8__outbox_actions_drained.sql`, PR #168). The next free number is V9. The Query set on main (`postgres-query`) has V1 and V2 and is a separate sequence.
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

A Navigator decision counts only if it appears in the DECIDED list of the session that made it, as shown by `navigator_status`. A decision mentioned in CARRY FORWARD notes, a relayed report, a `set_plan` or `decide` answer, or a session's reply does not count. The rule stands.

- **Session `09480ec46e184c8aa8dc03436a07e826`** (P3 migration number, Q1 and Q2). Decided (count 1 after recording): **Option B.** P3 takes V9 now and does not wait. If P3 merges before the held index branch, that branch's own PR renames `V9__outbox_dead_letter_index.sql` to V10. The stale `-v8` branch must not be merged.
- **Session `0739f198e75c403ab527d4ad6cc82164`** (OQ15, P4 saved sets). Verified in its DECIDED list: **option (a)**, follow spec line 128 (P4 saves a frozen settling set and adds the pair). **Not in its DECIDED list:** the starting point. Its CARRY FORWARD notes and job `kxz0iicvx` say option (i); they do not count.
- **Session `32ac8aeab08346b6b67e54a49c2e1d44`** (OQ15 starting point). Both answers say **option (i): the chain starts at P3.** `DECIDED: (none)`. Not decided. Do not reuse.
- **Session `ac015364523740e6b2eba306f8d31515`** (P4 OQ5 to OQ9). Its answers: OQ5 (a), OQ6 (a), OQ7 (b), OQ8 (a), OQ9 (a). `DECIDED: (none)`. Not decided. Do not reuse.
- **Session `54529a32ee4b47b9a3ae073cabcbcb4e`** (P4 OQ11, OQ12, OQ14, OQ17). Its answers: OQ11 (a), OQ12 (ii), OQ14 (b), OQ17 (a). `DECIDED: (none)`. Not decided. Do not reuse.
- **Session `c0e84f0a157f4cc1a0f5dc3f54952ce2`** (round limit reached; do not resume). Verified in its DECIDED list: P2e Q1 to Q12 and the P3 OQ1, OQ2, OQ3, OQ4 (revised), OQ7, OQ8, OQ11 (revised), OQ12 and OQ13. Not in its DECIDED list (unverified): P4 OQ15 (appears only in its CARRY FORWARD notes), and the P3 plan Decided lines at 184, 187, 196, 199, 211, 214, 218, 221, 224, 227 and 230.
- **Session `92e8178d1fdf4829837dab8eb4d0ef95`** (cycle 7, which code unit is next). Its set_plan answer: no code unit in spec 13 or later is unblocked; owner action 7 is the one decision that unblocks the most work. `DECIDED: (none)`. Not a decision; do not reuse for a new question.
- **Session `359d962fb8cd498b8b34339abd46e5bc`** (cycle 7, owner action 7 route). Its set_plan answer: route (a), re-ask in fresh sessions with a DECIDED entry for each answer; OQ15 starting point not decided; owner action 3 judged Navigator-decidable. A driver challenge was recorded on Q3 (the adapter is not derivable from the repo). **At check time, `DECIDED: (none)`.** So the route is not yet a decision under the rule. Do not reuse for a new question.
- **Session `cc206f68705e4d93956f7e58ddd8c6e9`** (round limit; do not resume). Verified: P2d Q-A, Q-B, Q-C, Q-E, and DR-24.
- **Session `cb5a1ebdeb804ce893b35dced364435c`** (earlier P3 migration number). Its DECIDED list is empty. Superseded by `09480ec46e184c8aa8dc03436a07e826`.
- **Session `b150e05389fd46148771bec93b8bc980`** (P4 answers). Its DECIDED list is empty. The nine answers are not decisions. They were reverted in #188.
- **Session `c07146c090c444b18302e90478418aa7`** (OQ15, a fresh duplicate; not used further). No DECIDED entry. Do not resume it.
- **B2, route 1:** not verified against any session checked here. Treat it as unverified until a session's DECIDED list shows it.

## Denials and refusals

- **Refused, then passed on retry.** The auto-mode classifier refused the first merge of PR #177 (the P3 decisions). The next attempt, through the normal route, merged it. Handle a first refusal by trying once through the normal route, then reporting it. Do not work around it.
- **Refused, then reversed.** The classifier denied a Bash write that recorded the P4 answers in the repo. The owner then granted the docs edit. The answers were later found unverified and reverted in #188.

## Next actions

No code unit is unblocked. Cycle 7 asked the Navigator which code unit is next (session `92e8178d`). Its set_plan answer (not a decision) names owner action 7 as the main blocker. The items below wait on owner action 7 or owner action 3; the held index branch waits on owner action 2.

1. **Owner action 7 (the recording route).** The Navigator's route answer (session `359d962f`, set_plan) recommends route (a). Its DECIDED list is empty, so it is not yet a decision. Next run: check `navigator_status` for `359d962f`. If its DECIDED list shows route (a), close owner action 7 as route (a). If not, the route is still open; do not record it from the driver. Ask a fresh session to decide and record it, as its own question.
2. **Re-ask in fresh sessions (if owner action 7 closes as route (a)).** Each answer counts only if the fresh session's DECIDED list shows it. Do not re-ask the same point in the same run. Batches, at most five questions each:
   - Batch A: the OQ15 starting point (P3 or P4). Gates the P3 PR (P3 plan section 9, item 3).
   - Batch B1: P3 Decided lines 184, 187, 196, 199, 211 (P3 plan section 8). Gates P3 code.
   - Batch B2: P3 Decided lines 214, 218, 221, 224, 227 (P3 plan section 8). Gates P3 code.
   - Batch B3: P3 Decided line 230 (P3 plan section 8). Gates P3 code.
   - Batch C1: P4 OQ5 to OQ9. Gates P4 code.
   - Batch C2: P4 OQ11, OQ12, OQ14, OQ17. Gates P4 code.
3. **When Batch A is DECIDED:** remove the "open" wording in P3 plan lines 117, 130 and 236 and P4 plan lines 175, 248 and 250, and record the starting point here.
4. **P3 code.** Blocked on item 2 (Batches A and B1 to B3) and on the item 3 edit. V9 is settled (PR #191). Implement P3 with a subagent in a worktree cut from `origin/main`, use the V9 file name, and run a read-only review. Pin the merge to the reviewed head. Re-run the O-3 count before any P3 deploy.
5. **P4 code.** Blocked on P3 merging, on Batches C1 and C2, and on owner action 3 (the dev-stack adapter). Before code, a read-only review of the P4 plan. P4 plan lines 34 and 171 assume main ends at V7; V8 is on main. Rewrite them after P3 merges.
6. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.
7. **Index branch.** Waits on owner action 2 (the claim-index window).

## Owner actions still open

1. **#41:** reopen or not. Also the written loss acceptance (Q-A), which only a later hand-stamp needs. It is not needed for P2e.
2. **Claim-index window** (decision ix in `remaining-work.md`) for `spec-13-dead-letter-index-v9` (`87f5e42`). Pick the window for the exclusive-lock rebuild, or ask for the non-transactional route. This gates the index branch merge.
3. **Dev-stack adapter** (P3 OQ16, P4 OQ16). Which adapter the dev stack runs. The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. The setting, if set, lives outside the repo, so the owner answers it. The Navigator's route answer judged it Navigator-decidable; the driver challenged that in session `359d962f`. P4 code waits on this.
4. **Re-run the O-3 count** on dev before any P3 deploy (and before the first start of a version that runs V9).
5. **Who renames the index file from V9 to V10** if P3 merges first: answered by the Navigator (Option B, session `09480ec46e184c8aa8dc03436a07e826`). The index branch's own PR does the rename. The owner may override.
6. Optional: confirm B2 route 1 on #41.
7. **Navigator recording route (open).** Route (a): re-ask in fresh sessions, and each answer counts only when that session's DECIDED list shows it. Route (b): the driver records the Navigator's verdict with `navigator_record` decided, quoting the reply. Route (b) changes the verification rule, and the Navigator's answer rejects it. The Navigator's set_plan answer (session `359d962f`) recommends route (a), but its DECIDED list is empty, so it is not a decision. Blocks Next actions items 2 to 5; item 1 is the check of this route. The owner may override.

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
- **Route answer versus DECIDED.** The route answer in session `359d962f` reads "decided" in its set_plan carry-forward, but its DECIDED list is empty. Treat it as open until `navigator_status` shows it.
- **OQ15:** option (a) is decided (session `0739f198`, DECIDED list). The starting point is open. The P3 plan's lines 117, 130 and 236 and the P4 plan's lines 175, 248 and 250 say so. Main saves three frozen Command sets, all added by #124 (P7, `9b6e316`): `3328993`, `fbb82c5` and `post-p7/command`. No step saved its own set (V7 and V8 saved none). The resume plan (lines 405 to 406) still lists OQ15 as ALL PENDING; it is stale and was not edited here.
- **The P3 migration number** is settled by Option B (session `09480ec46e184c8aa8dc03436a07e826`). OQ7 in `c0e84f0a` verifies only the next-free-number rule, not the Option B rename.
- **The held index branch name** is `-v9` (`87f5e42`). The older plans still name `spec-13-dead-letter-index-v8` as the held branch and are stale: `docs/extraction/spec-13-p4-plan.md` line 171, `docs/extraction/remaining-work.md` lines 28, 41 (decision ix) and 300, and, in older plans, `docs/extraction/resume-plan-spec13.md` lines 223, 224, 261, 334, 372 and 429 and `docs/extraction/spec-13-p2d-plan.md` lines 176 and 192. Only `-v9` is the held branch.
- **The P2e follow-up commit on main** (`c6e1313`, PR #194) changes code, but its commit subject starts with `docs(core,temporal)`. The squash used the commit title. This is cosmetic. Do not rewrite main for it.
- **Navigator file-read requests** named paths that do not exist on main (`sequeless-backend/...`, `com/sequeless/...`). Check every path with `git ls-tree origin/main` first. The correct paths are in the Hazards section.
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
- A relayed claim, a `set_plan` answer, or a CARRY FORWARD line is not a decision. Check the session's DECIDED list before recording a decision as decided. `navigator_status` returns large output; if it is saved to a file, read it with `jq`.
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `set_plan` for the first question of a session and `decide` for later ones. `settle` and `answer` are not offered in pair mode. `options` is only valid with `settle`. Only one job runs per server: a `job <id> active` error means wait for that job's result. Rounds can take over an hour. Batch related questions into one message per decision point, at most five per batch.
- Navigator file reads: check every path with `git ls-tree origin/main` before submitting an excerpt. Label any partial excerpt with `[...]`. Send corrections in the same submission.
- Do not reuse these Navigator sessions for a new question: `cc206f68705e4d93956f7e58ddd8c6e9`, `c0e84f0a157f4cc1a0f5dc3f54952ce2`, `cb5a1ebdeb804ce893b35dced364435c`, `b150e05389fd46148771bec93b8bc980`, `c07146c090c444b18302e90478418aa7`, `09480ec46e184c8aa8dc03436a07e826` (decided; no new questions), `0739f198e75c403ab527d4ad6cc82164` (option (a) decided; starting point not in DECIDED), `32ac8aeab08346b6b67e54a49c2e1d44`, `ac015364523740e6b2eba306f8d31515`, `54529a32ee4b47b9a3ae073cabcbcb4e`, `92e8178d1fdf4829837dab8eb4d0ef95` and `359d962fb8cd498b8b34339abd46e5bc`. Ask new questions in fresh sessions.
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact. This applies to this handoff too.
- Machine-local facts (paths, tokens, environment IDs) stay out of the repo.

## Local state on this machine (not in the repo)

- `prompt.md` at the repo root, git-ignored. Children read it from its absolute path.
- Worktrees live in a sibling directory outside the repo tree. Worktrees on merged branches can be removed. The worktree on `docs/handoff-cycle7` is this handoff's docs PR and can be removed after it merges.
- Temporary copies under `/tmp` are not needed. The results they held are recorded in this file.
