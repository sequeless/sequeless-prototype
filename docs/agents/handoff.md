# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `aa48261` (PR #198). The docs PR that carries this handoff lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

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
- Automation architecture, updated by PR #196: [docs/architecture/automation.md](../architecture/automation.md)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, CLOSED; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Merged, 2026-11-24:**
  - PR #182, the P2e code (`a8816f3`, pinned to head `481113d`). It removes `ActionWorkflow` and the `ActionRequest` drain.
  - PR #185, #187, #189, #190: handoffs and docs. PR #186 (nine P4 lines) was withdrawn by #188 (`5b5358c`).
  - PR #191 (`a4d08c4`), which records the P3 migration-number decision (Option B) and corrects the held branch name to `-v9`.
  - PR #192 (`03da34a`) and PR #193 (`cc5ceac`), which record OQ15 option (a) as decided and the starting point as open, and correct the frozen-set facts.
  - PR #194 (`c6e1313`, code), the P2e follow-up comments and the `ObjectCreated` `typeIri` assertion. Gate on head `b2b8b22`: BUILD SUCCESS, 747 unit and 11 integration tests, 0 failures, 0 errors.
  - PR #195 (`d14293e`), the cycle 4 handoff. It also corrects P3 plan line 190 from `-v8` to `-v9`.
  - PR #196 (`37ead4e`, code), the P2e leftovers: `DefaultActionExecutor.buildContext` drops its always-`toState` parameter; `ActionExecutor.applyAction` and `resolveWebhookAction` become abstract; the round-trip test uses the real `TransitionFired` payload keys; `docs/architecture/automation.md` lines about the per-action path and the retry policy are corrected. Gate on head `c1bf22d`, JDK 25: BUILD SUCCESS, 758 tests, 0 failures, 0 errors. No failing checks (`combined` was skipped). Squash-merged, pinned to the head. The review's should-fix and nits are in this commit, except the optional note in Next actions item 8.
  - PR #197 (`8ec0655`), the cycle 5 handoff, docs only. It was reviewed against GitHub.
  - PR #198 (`aa48261`, code, test-only): the round-trip test's class javadoc is rewritten, and its `self` map holds the tagged `text`, `integer` and `bool` maps that `DefaultTransitionService` writes (Next actions item 8, now done). The adapter cannot import `PayloadValueCodec`, so the tags are literals, and the comment says why the integer tag is an `Integer`. Gate on head `2c1b503`, JDK 25: BUILD SUCCESS, 758 tests, 0 failures, 0 errors, and all required checks passed. Squash-merged, pinned to the head. Review APPROVED after one rework round.
  - The docs PR that carries this handoff is on branch `docs/handoff-cycle6`.
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

- **Session `09480ec46e184c8aa8dc03436a07e826`** (P3 migration number, Q1 and Q2). Decided (count 1 after recording): **Option B.** P3 takes V9 now and does not wait. If P3 merges before the held index branch, that branch's own PR renames `V9__outbox_dead_letter_index.sql` to V10. The stale `-v8` branch must not be merged. Its reply was checked against the sources. P3 plan line 190 is verified by `c0e84f0a` (OQ7).
- **Session `0739f198e75c403ab527d4ad6cc82164`** (OQ15, P4 saved sets). Verified in its DECIDED list: **option (a)**, follow spec line 128 (P4 saves a frozen settling set and adds the pair). **Not in its DECIDED list:** the starting point. Checked again in cycle 5: `DECIDED` shows option (a) only and says the starting point is not settled by it. The CARRY FORWARD notes say option (i); they do not count.
- **Session `32ac8aeab08346b6b67e54a49c2e1d44`** (OQ15 starting point, pair mode, `set_plan` then `decide`). Both answers say **option (i): the chain starts at P3.** Checked again in cycle 5: `DECIDED: (none)`. Not decided. The session is not reused. See owner action 7.
- **Session `ac015364523740e6b2eba306f8d31515`** (P4 OQ5, OQ6, OQ7, OQ8, OQ9; `set_plan`, then file-read rounds). Its answers: **OQ5 (a)** a signal for a state-less object is rejected 409 through `TransitionNotAvailableException`; **OQ6 (a)** keep `existing.version()`, so a stale version maps to 409 `stale-object`; **OQ7 (b)** carry the signal body in the `TransitionFired` payload under a new key; **OQ8 (a)** the `IllegalStateException` surfaces as HTTP 500, with no class-level handler; **OQ9 (a)** the first match in declaration order, and a failing guard on it is a 409. Checked in cycle 5: `DECIDED: (none)`. Not decided. Its CARRY FORWARD notes repeat these answers, and they do not count.
- **Session `54529a32ee4b47b9a3ae073cabcbcb4e`** (P4 OQ11, OQ12, OQ14, OQ17; `set_plan`). Its answers: **OQ11 (a)** re-arm the dead-lettered `SignalReceived` rows before the old version stops, and let the migration stamp only rows that dead-letter again; **OQ12 (ii)** a direct same-request assertion in `AutomationEndToEndTest.java` in place of `awaitState` (the file is `sequeless-app/src/test/java/org/sequeless/app/AutomationEndToEndTest.java`); **OQ14 (b)** a DR-file entry numbered at merge (DR-26 if P3 takes DR-25); **OQ17 (a)** the same new wording in `object-signals.ts` line 47 and `signals-panel.ts` line 61. Checked in cycle 5: `DECIDED: (none)`. Not decided.
- **Session `c07146c090c444b18302e90478418aa7`** (OQ15, a fresh duplicate; not used further). No DECIDED entry. Do not resume it.
- **Session `c0e84f0a157f4cc1a0f5dc3f54952ce2`** (round limit reached; do not resume). Verified in its DECIDED list: P2e Q1 to Q12 and the P3 OQ1, OQ2, OQ3, OQ4 (revised), OQ7, OQ8, OQ11 (revised), OQ12 and OQ13. Not in its DECIDED list (unverified): P4 OQ15 (appears only in its CARRY FORWARD notes), and the P3 plan Decided lines at 184, 187, 196, 199, 211, 214, 218, 221, 224, 227 and 230.
- **Session `cc206f68705e4d93956f7e58ddd8c6e9`** (round limit; do not resume). Verified: P2d Q-A, Q-B, Q-C, Q-E, and DR-24.
- **Session `cb5a1ebdeb804ce893b35dced364435c`** (earlier P3 migration number). Its DECIDED list is empty. Superseded by `09480ec46e184c8aa8dc03436a07e826`.
- **Session `b150e05389fd46148771bec93b8bc980`** (P4 answers). Its DECIDED list is empty. The nine answers are not decisions. They were reverted in #188.
- **B2, route 1:** not verified against any session checked here. Treat it as unverified until a session's DECIDED list shows it.
- **P2e plan lines other than those above:** not audited.

## Denials and refusals

- **Refused, then passed on retry.** The auto-mode classifier refused the first merge of PR #177 (the P3 decisions). The next attempt, through the normal route, merged it. Handle a first refusal by trying once through the normal route, then reporting it. Do not work around it.
- **Refused, then reversed.** The classifier denied a Bash write that recorded the P4 answers in the repo. The owner then granted the docs edit. The answers were later found unverified and reverted in #188.

## Next actions

No code unit is unblocked after PR #198. Items 2 to 7 wait, directly or through earlier items, on owner action 7 (the Navigator recording route) or on owner action 3. The held index branch waits on owner action 2. If a later run finds nothing unblocked, it asks the Navigator which code unit to do next, as `prompt.md` step 2 says. That is a new question, not one of the blocked points. No Navigator question was asked in the cycle that wrote this file.

1. **Owner action 7 (Navigator recording gap) gates the Navigator answers for items 2, 3 and 5.** Three sets are open because no DECIDED entry exists: the OQ15 starting point (item 2), the nine P4 answers (item 3), and the unverified P3 Decided lines (item 5). Decide the route first (owner action 7 below). Nothing on these three points can be recorded as decided, and no question on these points can be re-asked, until then.
2. **OQ15 starting point (P3 or P4). Gates the P3 PR (P3 plan item 29c).** Option (a) is decided (session `0739f198`, DECIDED list). The starting point is open: `0739f198` and `32ac8aeab08346b6b67e54a49c2e1d44` both show no P3 start in their DECIDED lists (checked in cycle 5). If a later check shows the P3 start in a DECIDED list, remove the "open" wording in P3 plan lines 117, 130 and 236 and P4 plan lines 175, 248 and 250, and record it here. Do not re-ask in the same run.
3. **P4 open questions, answered but not decided.** Sessions `ac015364523740e6b2eba306f8d31515` (OQ5 to OQ9) and `54529a32ee4b47b9a3ae073cabcbcb4e` (OQ11, OQ12, OQ14, OQ17) both show `DECIDED: (none)` (checked in cycle 5). If a later check shows their answers in a DECIDED list, record them in P4 plan section 8 and in this file. If not, they stay open. A new Navigator question on the same point waits for owner action 7. OQ16 is the owner's (owner action 3).
4. **P3 code.** Blocked on item 2 (the OQ15 starting point) and on item 5 (the unverified P3 Decided lines, which the P3 plan's section 9 lists as code-gating). V9 is settled (PR #191). Once both are decided, implement P3 with a subagent in a worktree cut from `origin/main`, use the V9 file name, and run a read-only review. Pin the merge to the reviewed head. Re-run the O-3 count before any P3 deploy.
5. **Unverified P3 Decided lines** (listed under session `c0e84f0a`, lines 184, 187, 196, 199, 211, 214, 218, 221, 224, 227 and 230). Re-asking them waits for owner action 7: no new Navigator question on the same point until that route is decided. Do not treat them as decided.
6. **P4 code.** Blocked on P3 merging, on items 2 and 3, and on owner action 3 (the dev-stack adapter). Before code, a read-only review of the P4 plan. P4 plan lines 34 and 171 assume main ends at V7; V8 is on main. Rewrite them after P3 merges.
7. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.
8. **Done in PR #198 (`aa48261`).** The round-trip test's javadoc and tagged `self` map. Nothing left in this item.

## Owner actions still open

1. **#41:** reopen or not. Also the written loss acceptance (Q-A), which only a later hand-stamp needs. It is not needed for P2e.
2. **Claim-index window** (decision ix in `remaining-work.md`) for `spec-13-dead-letter-index-v9` (`87f5e42`). Pick the window for the exclusive-lock rebuild, or ask for the non-transactional route. This gates the index branch merge.
3. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. Say which adapter the dev stack runs. P4 code waits on this.
4. **Re-run the O-3 count** on dev before any P3 deploy (and before the first start of a version that runs V9).
5. **Who renames the index file from V9 to V10** if P3 merges first: answered by the Navigator (Option B, session `09480ec46e184c8aa8dc03436a07e826`). The index branch's own PR does the rename. The owner may override.
6. Optional: confirm B2 route 1 on #41.
7. **Navigator recording gap (open; the blocker for items 1 to 5 of Next actions).** It gates the OQ15 starting point (item 2), the P4 answers (item 3) and the unverified P3 Decided lines (item 5). The OQ15 starting-point answers (`kxz0iicvx` in `0739f198`, and the `set_plan` and `decide` answers in `32ac8aeab08346b6b67e54a49c2e1d44`), and the nine P4 answers in `ac015364523740e6b2eba306f8d31515` and `54529a32ee4b47b9a3ae073cabcbcb4e`, are not in any DECIDED list. Decide the route: (a) re-ask the questions in fresh sessions with the Navigator required to record a DECIDED entry, or (b) let the driver record a Navigator verdict. Route (b) makes the driver's record the source, so the verification rule would need changing. Not decided by this run.

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
- **The P4 nine answers** in PR #186 were withdrawn in #188 (see above). The nine answers in the two Navigator sessions named above are the new set, and they are not decided.
- **OQ15:** option (a) is decided (session `0739f198`, DECIDED list). The starting point is open (see Navigator decisions and Next actions item 2). The P3 plan's lines 117, 130 and 236 and the P4 plan's lines 175, 248 and 250 say so. Main saves three frozen Command sets, all added by #124 (P7, `9b6e316`): `3328993`, `fbb82c5` and `post-p7/command`. No step saved its own set (V7 and V8 saved none). The resume plan (lines 405 to 406) still lists OQ15 as ALL PENDING; it is stale and was not edited here.
- **The P3 migration number** is settled by Option B (session `09480ec46e184c8aa8dc03436a07e826`). OQ7 in `c0e84f0a` verifies only the next-free-number rule, not the Option B rename.
- **The held index branch name** was written as `-v8` in the P3 plan (section 5 and section 8, item 7). It is `-v9` (`87f5e42`). The plan was corrected in PR #191 (`a4d08c4`), and P3 plan line 190 in PR #195 (`d14293e`). These files still name `spec-13-dead-letter-index-v8` as the held branch and are stale: `docs/extraction/spec-13-p4-plan.md` line 171, `docs/extraction/remaining-work.md` lines 28, 41 (decision ix) and 300, and, in older plans, `docs/extraction/resume-plan-spec13.md` lines 223, 224, 261, 334, 372 and 429 and `docs/extraction/spec-13-p2d-plan.md` lines 176 and 192. They were not edited in cycle 5 or cycle 6. Only `-v9` is the held branch.
- **The P2e follow-up commit on main** (`c6e1313`, PR #194) changes code, but its commit subject starts with `docs(core,temporal)`. The squash used the commit title. This is cosmetic. Do not rewrite main for it.
- **The P2e leftovers** (PR #196, `37ead4e`) are done. The round-trip test keys, the `buildContext` parameter, the abstract `ActionExecutor` methods and the two `automation.md` passages are all fixed. The optional nit in Next actions item 8 landed in PR #198 (`aa48261`), so no nits remain.
- **The re-review gap on PR #191** (the fixed version was never re-reviewed) is closed. A read-only re-review passed with one nit, the line 190 `-v8` fixed in PR #195.
- **Navigator file-read requests** named paths that do not exist on main (`sequeless-backend/...`, `com/sequeless/...`). The correct paths are in the Hazards section.
- Resume plan sections 21.1 to 21.5 are partly stale. Section 21.6 is the gate.

## Hazards

- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Never write `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves` or `resolved` next to #41 in a PR body or commit, not even in a negation. Write `Refs #41`.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check `origin/main` before merging.
- Work from a worktree cut from `origin/main`. The primary checkout may be behind; fast-forward it before use. At the start of cycle 6 it was at `cc5ceac`, behind `origin/main` by 4 commits.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Push with an explicit refspec (`git push -u origin HEAD:refs/heads/<branch>`), so a branch created from `origin/main` can never update `main`.
- Maven needs the GitHub Packages token, so source the local env file in the same command as every `mvn` run. The gate commands and the JDK 25 path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default `java` is 21; the gate needs 25.
- The first start of a version that runs V8 on a database marks undrained `ActionRequest` rows as processed. Do the O-3 count before that start (P2e plan section 9).
- P3's migration marks unprocessed timer rows as processed, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. The O-4 check gates only non-dev environments (P3 plan section 8, question 19).
- A refused action (a permission or classifier refusal) stops the run and is reported verbatim. Do not retry it in another form or work around it.
- A relayed claim, a `set_plan` answer, or a CARRY FORWARD line is not a decision. Check the session's DECIDED list before recording a decision as decided. `navigator_status` returns large output; if it is saved to a file, read it with `jq`.
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `set_plan` for the first question of a session and `decide` for later ones. `settle` and `answer` are not offered in pair mode. `options` is only valid with `settle`. Only one job runs per server: a `job <id> active` error means wait for that job's result. Rounds can take over an hour. Batch related questions into one message per decision point, at most five per batch.
- Navigator file reads: check every path with `git ls-tree origin/main` before submitting an excerpt. Label any partial excerpt with `[...]`. Send corrections in the same submission. The batch 1 excerpt of the P4 plan's section 8 abridged OQ11, OQ14, OQ15, OQ16 and OQ17; its OQ5 to OQ9 text was verbatim. Those answers did not decide anything, so nothing is affected, but the same mistake must not recur.
- Do not resume Navigator sessions `cc206f68705e4d93956f7e58ddd8c6e9`, `c0e84f0a157f4cc1a0f5dc3f54952ce2`, `cb5a1ebdeb804ce893b35dced364435c` or `b150e05389fd46148771bec93b8bc980`. Ask new questions in fresh sessions. Session `09480ec46e184c8aa8dc03436a07e826` (P3 migration number) is decided; do not reuse it for new questions. Session `0739f198e75c403ab527d4ad6cc82164` (OQ15) has option (a) decided; its starting-point answer is not in its DECIDED list. Do not reuse it for a new question. Session `c07146c090c444b18302e90478418aa7` is not used further; do not resume it. Session `32ac8aeab08346b6b67e54a49c2e1d44` (OQ15 starting point) gave option (i) twice with an empty DECIDED list; do not reuse it for a new question. Sessions `ac015364523740e6b2eba306f8d31515` (P4 OQ5 to OQ9) and `54529a32ee4b47b9a3ae073cabcbcb4e` (P4 OQ11, OQ12, OQ14, OQ17) gave answers with empty DECIDED lists; do not reuse them for a new question.
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact. This applies to this handoff too.
- Machine-local facts (paths, tokens, environment IDs) stay out of the repo.

## Local state on this machine (not in the repo)

- `prompt.md` at the repo root, git-ignored. Children read it from its absolute path.
- Worktrees live in a sibling directory outside the repo tree. These are merged and can be removed: the worktrees on branches `fix/p2e-followup-comments-coverage`, `fix/p2e-leftovers` and `docs/handoff-cycle4`. The worktrees on branches `fix/round-trip-test-tagged-self` (PR #198) and `docs/handoff-cycle5` are merged and can be removed. The worktree on `docs/handoff-cycle6` is this handoff's docs PR and can be removed after it merges. Worktrees from earlier runs, including one on a `spec-13-p2e-followups` branch that is not on origin, are not needed to resume.
- Temporary copies under `/tmp` are not needed. The results they held are recorded in this file.
