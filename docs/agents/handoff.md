# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `9e8a13d` (PR #189). This docs PR lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

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
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 8 holds the Decided lines; question 7 is the migration number; section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (section 8 holds the open questions and their proposed answers; none is decided yet except as recorded under Navigator decisions)
- Decision record for the flip: [DR-24](../architecture/02-decision-records.md) (actions travel on the TransitionFired row)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, CLOSED; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Merged, 2026-11-24:**
  - PR #182, the P2e code (`a8816f3`, pinned to head `481113d`). It removes `ActionWorkflow` and the `ActionRequest` drain. Merged after the dev-stack checks below.
  - PR #185, the handoff after P2e (`36d713f`).
  - PR #186 (`c05de53`), which recorded nine P4 `Decided` lines. **Withdrawn by #188.**
  - PR #187 (`a1915e8`), the handoff after #186.
  - PR #188 (`5b5358c`), which reverted the nine P4 lines to open questions with their proposed answers marked unverified.
  - PR #189 (`9e8a13d`), the handoff after #188.
- **Open PRs:** only #106, a draft compare document. Do not merge it.
- **Issue #41:** CLOSED. It closed automatically when PR #169 merged. Its state is not a sign that spec 13 is finished. Reopening it is the owner's decision.
- **Migrations on main:** V1, V3, V4, V5, V6, V7, V8 (`V8__outbox_actions_drained.sql`, PR #168). The next free number is V9.
- **Held index branch:** `origin/spec-13-dead-letter-index-v9` at `87f5e42`. It holds the claim-index migration renumbered to V9. It is unmerged and has no PR. The older `origin/spec-13-dead-letter-index-v8` (`6cf5938`) is stale and still carries a V8 file that collides with main's V8.

## Dev-stack checks, recorded 2026-11-24

These are the P2e plan section 8, step 3 checks. This section is the record. Earlier handoffs said results were recorded, and that was wrong.

- Run by the orchestration session at the owner's request, before #182 merged. The dev stack is the compose project `sequeless-prototype-ui`.
- Result, unprocessed rows: `sq_outbox` has 0 rows, so the unprocessed count is 0 for every kind.
- Result, schema: Flyway history lists versions 1 to 4 on that database. V8 and later have not run there.
- Result, running `ActionWorkflow` executions: 0. No Temporal state volume exists on this Docker host, so no executions can be running. This is inferred from the missing volume, not from a live Temporal query.
- No sequeless container or application process was running, so no stop or drain step was needed.
- Only the dev Postgres service was started, for the read-only query, then stopped. Temporal was not started.
- The owner's written loss acceptance (Q-A) was not given and was not needed, because the count was zero.
- A new check is needed before any P3 deploy: re-run the O-3 count (see Owner actions).

## Owner decisions and waivers recorded

- **Merge of #182 (2026-11-24).** The owner said to merge #182 on the next cycle, then asked for the dev-stack checks to be run first. The checks above passed, and #182 merged. The owner did not give the written loss acceptance, which was not needed.
- **Merge of #168 (2026-11-24).** The owner instructed the merge directly, overriding the earlier hold. Recorded in the Navigator session `c0e84f0a` DECIDED list.
- **Withdrawn:** the nine P4 answers in #186. No Navigator session decided them. See Drift.

## Navigator decisions: verification

A Navigator decision counts only if it appears in the DECIDED list of the session that made it, as shown by `navigator_status`. A decision mentioned in CARRY FORWARD notes, a relayed report, or a session's reply does not count. Each entry below was checked against the DECIDED list of the named session on 2026-11-24.

- **Session `c0e84f0a157f4cc1a0f5dc3f54952ce2`** (round limit reached; do not resume). Verified in its DECIDED list:
  - P2e: Q1, Q2, Q3, Q4, Q5, Q6, Q7, Q8, Q9, Q10, Q11, Q12, the null-holder test and the METHOD_* constants.
  - P3: OQ1, OQ2, OQ3, OQ4 (revised, with its follow-up), OQ7 (V9 if the index branch is unmerged, V10 if merged), OQ8 (the migration stamps dead-lettered timer rows), OQ11 (revised), OQ12 and OQ13.
  - Not in its DECIDED list: P4 OQ15, which appears only in its CARRY FORWARD notes, and the P3 plan Decided lines at 184, 187, 196, 199, 211, 214, 218, 221, 224, 227 and 230. These are unverified.
- **Session `cc206f68705e4d93956f7e58ddd8c6e9`** (round limit; do not resume). Verified in its DECIDED list: P2d Q-A (mix), Q-B (remove the ActionRequest dispatch cases), Q-C (hold the P2d merge until the owner runs the mixed-version procedure), Q-E (convert the ActionRequest tests to TransitionFired form), and the decision to keep `ActionWorkflow` in P2d and delete it in P2e, recorded as DR-24.
- **Session `cb5a1ebdeb804ce893b35dced364435c`** (P3 migration number). Its DECIDED list is empty. Its reply said Q1 option (b) and Q2 option (iii). Those appear only in its CARRY FORWARD notes, so both are open. Its unverified proposal for Q1 is: P3 takes V9 while the index is unmerged, and the `-v9` file is renamed to V10 when it lands after P3.
- **Session `b150e05389fd46148771bec93b8bc980`** (P4 answers). Its DECIDED list is empty. The nine answers it was reported to have given are not decisions. They were reverted in #188 and are open.
- **B2, route 1:** not verified against any session checked here. Resume plan line 159 attributes it to 2026-11-12. Treat it as unverified until a session's DECIDED list shows it.
- **P2e plan lines other than those above:** not audited.

## Denials and refusals

- **Refused, then passed on retry.** The auto-mode classifier refused the first merge of PR #177 (the P3 decisions). The next attempt, through the normal route, merged it. Handle a first refusal by trying once through the normal route, then reporting it. Do not work around it.
- **Refused, then reversed.** The classifier denied a Bash write that recorded the P4 answers in the repo. The owner then granted the docs edit. The answers were later found unverified and reverted in #188.

## Next actions

1. **P3 migration number.** Ask Q1 and Q2 from "Navigator decisions" in a fresh pair session. Do not resume `cb5a1ebd`, `c0e84f0a`, `cc206f` or `b150e05`. Record only what the new session's DECIDED list shows, citing the session. If there is no decision, the question stays open for the owner. P3 code waits on Q1.
2. **OQ15 (P4 saved sets).** It gates the P3 PR (P3 plan item 29c). It is unverified. Ask it in a fresh session, and record only a decision that session's DECIDED list shows.
3. **Unverified P3 Decided lines** (listed under session `c0e84f0a`). Re-ask them in a fresh session, or leave them open for the owner. Do not treat them as decided.
4. **P4 open questions.** Ask OQ5, OQ6, OQ7, OQ8, OQ9, OQ11, OQ12, OQ14 and OQ17 in batches of at most five, each in a fresh session. Give the proposed answers from P4 plan section 8 as unverified inputs. Record only decisions the session shows. OQ16 is the owner's.
5. **P2e follow-ups.** The "Known non-blocking follow-ups" in PR #182's body. This is a separate PR (`Refs #41`). Code changes need the gate (resume plan 21.6) and a read-only review. Pin the merge to the reviewed head.
6. **P3 code.** After items 1 and 2 are settled. Implement with a subagent in a worktree cut from `origin/main`. Then a read-only review. Pin the merge to the reviewed head. Re-run the O-3 count before any P3 deploy.
7. **P4 code.** Blocked on P3 merging, on items 4 and on owner action 3 (the dev-stack adapter). Before code, a read-only review of the P4 plan. P4 plan lines 34 and 171 assume main ends at V7; V8 is on main. Rewrite them after P3 merges.
8. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.

## Owner actions still open

1. **#41:** reopen or not. Also the written loss acceptance (Q-A), which only a later hand-stamp needs. It is not needed for P2e.
2. **Claim-index window** (decision ix in `remaining-work.md`) for `spec-13-dead-letter-index-v9` (`87f5e42`). Pick the window for the exclusive-lock rebuild, or ask for the non-transactional route.
3. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. Say which adapter the dev stack runs. P4 code waits on this.
4. **Re-run the O-3 count** on dev before any P3 deploy (and before the first start of a version that runs V9).
5. **Who renames the index file from V9 to V10** if P3 merges before the index branch (migration Q2). No decided answer exists yet. The owner decides, unless a decided answer is recorded first.
6. Optional: confirm B2 route 1 on #41.

## Loop: how the orchestration runs

This is the procedure a new orchestration session follows. The loop runs child sessions one after another. Each child does a bounded amount of work, then reports back.

- **Orchestrator** is the session that creates the children. Its ID is needed for the children's reply. A new orchestrator uses its own session ID. The previous orchestrator was `session_01VYniu3XBwyAxbSPiXnjLqy`.
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
- **OQ15** is unverified (see Navigator decisions).
- **The P3 migration number** is open (see Navigator decisions).
- **The dev-stack checks** were recorded in this file only in this PR. The earlier statement that results were recorded was wrong.
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
- Navigator, pair mode: a reply is often a `tool_calls` round: read the named files, then `navigator_submit`. Rounds can take over an hour. Do not ask a second question on a busy session. Batch related questions into one message per decision point, at most five per batch.
- Do not resume Navigator sessions `cc206f68705e4d93956f7e58ddd8c6e9`, `c0e84f0a157f4cc1a0f5dc3f54952ce2`, `cb5a1ebdeb804ce893b35dced364435c` or `b150e05389fd46148771bec93b8bc980`. Ask new questions in fresh sessions.
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact.
- Machine-local facts (paths, tokens, environment IDs) stay out of the repo.

## Local state on this machine (not in the repo)

- `prompt.md` at the repo root, git-ignored. Children read it from its absolute path.
- Worktrees under `/Users/bbartolome/workspace/sequeless-prototype.worktrees/`. Their branches are pushed. Nothing in them is needed to resume, except as noted above. The worktree `p4-oq` is clean and matches its pushed branch.
- Temporary copies under `/tmp` are not needed. The results they held are recorded in this file.
