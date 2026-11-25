# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-25, at origin/main `b7cf42c` (PR #202). The docs PR that carries this handoff lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

## Start a new session

1. Read this file, then `docs/extraction/remaining-work.md` and the spec 13 plan named under "Read first".
2. Check the state against GitHub: `origin/main`, open PRs (only #106 is open, a draft, do not merge), and issue #41 (CLOSED; reopening is the owner's decision).
3. Check the running Navigator job under "Running jobs" first. It has a pending tool call the next driver must answer.
4. Aim the cycle at code. The code-bearing items are under "Next actions". Items that need a Navigator or owner answer stay blocked until the answer is recorded (see "Navigator decisions: verification").
5. Before any PR or merge, follow "Hazards".

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate, section 17 is the merge list): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent: [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md) (line 128 is the settling test: "In the plan's order, P2a's PR starts the chain")
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 4 is the change list, section 8 holds the open questions and Decided lines, section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (section 8 holds the open questions, OQ15 included)
- Decision record for the flip: [DR-24](../architecture/02-decision-records.md) (actions travel on the TransitionFired row)
- Automation architecture: [docs/architecture/automation.md](../architecture/automation.md)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, CLOSED; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Main:** origin/main `b7cf42c` (#202). This cycle's base was `8fc0b55`. No path outside `docs/` and `*.md` changed since `8fc0b55`. Check with `git diff --name-only 8fc0b55 origin/main -- . ':(exclude)docs' ':(exclude)*.md'`, which prints nothing.
- **Code on main from this cycle:** none.
- **Merged this cycle (docs only):** #202 (`b7cf42c`), the record-flow handoff, merged mid-cycle. From now on, decisions and status fold into the one handoff PR at the end of a cycle.
- **Open PRs:** only #106, a draft compare document. Do not merge it.
- **Issue #41:** CLOSED. It closed automatically when PR #169 merged. Its state is not a sign that spec 13 is finished. Reopening it is the owner's decision.
- **Migrations on main (Command set):** V1, V3, V4, V5, V6, V7, V8 (`V8__outbox_actions_drained.sql`, PR #168). The next free number is V9. The Query set on main (`postgres-query`) has V1 and V2 and is a separate sequence.
- **Held index branch:** `origin/spec-13-dead-letter-index-v9` at `87f5e42`. It holds the claim-index migration as `V9__outbox_dead_letter_index.sql`. It is unmerged and has no PR. The older `origin/spec-13-dead-letter-index-v8` (`6cf5938`) is stale and adds `V8__outbox_dead_letter_index.sql`, which collides with main's V8. Do not merge it.

## Running jobs

- **Navigator session `c4f4f2bbecca4223816663cbc1b096cb`** (which code unit is unblocked now). Its job `k11z2pnzk` returned a tool call, not an answer: a read of `docs/extraction/spec-13-p4-plan.md` (call `call-0004`, justified as "to see if P4 has dependencies that block P3 items"). The driver did not answer it, because the stop rule forbids new jobs this run. Next run: answer `call-0004` with `navigator_submit` (verify the path with `git ls-tree origin/main` first), then take the Navigator's answer through the record flow.
- **Navigator session `acac3649758f47d2ad75687c198f13e8`** (OQ15, first ask): answered P4; rejected on source. Ended as unresolved; do not reuse.

## Navigator decisions: verification

The record flow (the `navigator:pair` skill's flow, adopted by the owner on 2026-11-25):

- A Navigator decision counts once the driver has recorded it with `navigator_record` (kind `decided`), quoting the Navigator's reply in `text` and naming the source check in `rationale`, and `navigator_status` shows it in the DECIDED list of the session that made it.
- A `set_plan` or `decide` answer, a job answer, a CARRY FORWARD line or a relayed claim is not a decision until it is recorded this way.
- Before the next `navigator_ask`, record one `carry_forward_verdict` per CARRY FORWARD line: `kept`, or `wrong` with evidence. A claim the source contradicts is recorded as `rejected_claim`.
- Only the driver records decisions. One question per `navigator_ask`. A new question or batch goes to a fresh session, at most five questions per batch, one `navigator_ask` each.

Recorded this cycle:

- **Session `95d0ab97a06845088d993ce3caa0f05e`** (owner action 7): DECIDED **route (a)**, recorded on the `decide` reply. `step_done` `decide-route-a` and `carry_forward_verdict` `kept` are recorded. Do not reuse for a new question.
- **Session `cf09351ce0a144538f114de334e5d631`** (OQ15 starting point): DECIDED **P2a**, recorded on the `set_plan` reply. Source check: spec line 128 says "In the plan's order, P2a's PR starts the chain", and `frozen-migrations/fbb82c5` (P2a) is on main. The P3-start reading (P3 plan item 29c; sessions `0739f198` job `kxz0iicvx` and `32ac8aea`) and the P4-start reading (session `acac3649`, rejected) are not decisions. Do not reuse.
- **Session `acac3649758f47d2ad75687c198f13e8`**: its answer "P4, following the spec" was rejected on source: `rejected_claim` and `carry_forward_verdict` `wrong` are recorded, and nothing is decided. Do not reuse.

Earlier sessions (state as recorded by earlier runs):

- Verified in a DECIDED list: `09480ec46e184c8aa8dc03436a07e826` (P3 migration number, **Option B**: P3 takes V9 now; the held index branch's PR renames it to V10 if it merges later); `0739f198e75c403ab527d4ad6cc82164` (OQ15, **option (a)**); `c0e84f0a157f4cc1a0f5dc3f54952ce2` (P2e Q1 to Q12; P3 OQ1, OQ2, OQ3, OQ4 revised, OQ7, OQ8, OQ11 revised, OQ12, OQ13; round limit, do not resume); `cc206f68705e4d93956f7e58ddd8c6e9` (P2d Q-A, Q-B, Q-C, Q-E, DR-24; do not resume).
- Not decided (`DECIDED: (none)`): `32ac8aeab08346b6b67e54a49c2e1d44`, `ac015364523740e6b2eba306f8d31515` (P4 OQ5 to OQ9), `54529a32ee4b47b9a3ae073cabcbcb4e` (P4 OQ11, OQ12, OQ14, OQ17), `92e8178d1fdf4829837dab8eb4d0ef95`, `359d962fb8cd498b8b34339abd46e5bc`, `cb5a1ebdeb804ce893b35dced364435c`, `b150e05389fd46148771bec93b8bc980`, `c07146c090c444b18302e90478418aa7`. Do not reuse any of them.
- **Session `c4f4f2bbecca4223816663cbc1b096cb`** (code-unit question): open, not decided. It has the pending `call-0004` above.
- **B2, route 1:** unverified. Treat it as unverified until a DECIDED list shows it.
- **P3 plan section 8, Decided lines:** OQ5, OQ6, OQ9, OQ10, OQ14, OQ17, OQ18, OQ19, OQ20 and OQ21 are written as Decided in the plan but are not in any verified DECIDED list. OQ16 (dev-stack adapter) is open and owner-only.

## Denials and refusals

- **Refused, then passed on retry.** The auto-mode classifier refused the first merge of PR #177 (the P3 decisions). The next attempt, through the normal route, merged it. Handle a first refusal by trying once through the normal route, then reporting it. Do not work around it.
- **Refused, then reversed.** The classifier denied a Bash write that recorded the P4 answers in the repo. The owner then granted the docs edit. The answers were later found unverified and reverted in #188.

## Next actions

Aim the next cycle at code. No code is on main from the last two cycles. The owner's rule (2026-11-25): code first, one handoff PR at the end of a cycle, no mid-cycle docs PRs.

1. **Finish the code-unit question.** Answer `call-0004` in session `c4f4f2bb` (see "Running jobs"), then take the Navigator's answer through the record flow. Ask which code unit can be implemented now, naming the unit, its files outside `docs/`, and the plan lines that make it unblocked. A unit that depends on an unverified item in "Navigator decisions" is not unblocked.
2. **Unverified P3 Decided items.** Re-ask each in a fresh session, one question per `navigator_ask`, before code that depends on it. Gating: P3 code items 8, 10, 11, 13, 14, 16, 20, 21, 23a, 23b and 26 depend on OQ5, OQ9, OQ10, OQ17, OQ18, OQ19, OQ20 or OQ21; OQ6 and OQ14 gate the B2 wording and DR-25.
3. **P3 plan item 29c conflicts with the chain start.** Item 29c says P3 saves the post-P2 set. Spec line 128 says P2's PR saves its own set and adds the pair from after P7 to after P2. Ask a fresh session which phase saves the post-P2 set before P3 code item 29c and the saved-set tests. Also remove the "starting point is open" wording in P3 plan lines 117, 130 and 236 and P4 plan lines 175, 248 and 250, and cite the decision in session `cf09351c`.
4. **P3 code.** Gated on items 1 to 3 for the units that depend on them. Implement with a subagent in a worktree cut from `origin/main`, use the V9 file name, run a read-only review by a separate subagent, and pin the merge to the reviewed head. Re-run the O-3 count before any P3 deploy.
5. **P4 code.** Blocked on P3 merging, on the nine P4 answers (Batch C1 and C2, not decided), and on owner action 3 (dev-stack adapter). P4 plan lines 34 and 171 assume main ends at V7; V8 is on main. Rewrite them after P3 merges.
6. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.
7. **Index branch** (`spec-13-dead-letter-index-v9`). Waits on owner action 2 (the claim-index window).

## Owner actions still open

1. **#41:** reopen or not. Also the written loss acceptance (Q-A), which only a later hand-stamp needs. It is not needed for P2e.
2. **Claim-index window** (decision ix in `remaining-work.md`) for `spec-13-dead-letter-index-v9` (`87f5e42`). Pick the window for the exclusive-lock rebuild, or ask for the non-transactional route. This gates the index branch merge.
3. **Dev-stack adapter** (P3 OQ16, P4 OQ16). Which adapter the dev stack runs. The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. The setting, if set, lives outside the repo, so the owner answers it. P4 code waits on this.
4. **Re-run the O-3 count** on dev before any P3 deploy (and before the first start of a version that runs V9).
5. **Who renames the index file from V9 to V10** if P3 merges first: answered by the Navigator (Option B, session `09480ec46e184c8aa8dc03436a07e826`). The index branch's own PR does the rename. The owner may override.
6. Optional: confirm B2 route 1 on #41.

## Owner decisions and waivers recorded

- **Merge of #182 (2026-11-24).** The owner asked for the dev-stack checks to be run first. They passed, and #182 merged.
- **Merge of #168 (2026-11-24).** The owner instructed the merge directly. Recorded in the Navigator session `c0e84f0a` DECIDED list.
- **Withdrawn:** the nine P4 answers in #186. No Navigator session decided them. See Drift.
- **Navigator recording route (2026-11-25):** route (a), a fresh session for each new question or batch, is decided (session `95d0ab97`). The owner adopted the `navigator:pair` record flow: the driver records each checked reply with `navigator_record` `decided`. The owner may override.
- **OQ15 starting point (2026-11-25):** P2a (session `cf09351c`), from spec line 128.

## Loop: how the orchestration runs

- **Orchestrator:** the session that creates the children, and the target of their state reports. The current orchestrator is `session_01VYniu2Woriiut8U9HzyZrf`. The earlier one was `session_01VYniu1iGuKriV19CtTjn2U`. A new run uses the ID its parent gives it.
- **Child rules** (from the parent, 2026-11-25): code first; one handoff PR at the end of a cycle, no mid-cycle docs PRs; write `LIMIT` only when the run cannot continue; write `ALL DONE` only after a code commit is on `origin/main` during the cycle; report the code units tried, what blocks each, and whether code is on main.
- **Prompt file:** `prompt.md` is git-ignored on purpose, so it is not in the repo. It exists on the machine where the loop was started, and children read it from its absolute path.
- **Cost:** each child uses the owner's plan usage window. The owner watches it. The owner can stop the loop at any time.
- **Review:** this cycle's handoff was not reviewed by a separate subagent, because the parent's graceful stop barred new subagents. The next cycle's handoff PR should get that review, as `prompt.md` requires.

## Drift to check against GitHub

- **#41 is CLOSED** (since PR #169 merged). Spec 13 is not finished.
- **Route answer versus DECIDED: resolved.** Session `359d962f`'s route answer is still not in its DECIDED list, so it stays unrecorded. Route (a) is recorded in session `95d0ab97`, under the adopted flow.
- **OQ15 starting point is P2a** (session `cf09351c`). The P3 plan's item 29c and the P4 plan's OQ15 wording still assume the P3 or P4 start. Next actions item 3 covers them.
- **The P3 migration number** is settled by Option B (session `09480ec46e184c8aa8dc03436a07e826`). OQ7 in `c0e84f0a` verifies only the next-free-number rule, not the Option B rename.
- **The held index branch name** is `-v9` (`87f5e42`). The older plans still name `spec-13-dead-letter-index-v8` as the held branch and are stale: `docs/extraction/spec-13-p4-plan.md` line 171, `docs/extraction/remaining-work.md` lines 28, 41 (decision ix) and 300, and, in older plans, `docs/extraction/resume-plan-spec13.md` lines 223, 224, 261, 334, 372 and 429 and `docs/extraction/spec-13-p2d-plan.md` lines 176 and 192. Only `-v9` is the held branch.
- **The P2e follow-up commit on main** (`c6e1313`, PR #194) changes code, but its commit subject starts with `docs(core,temporal)`. The squash used the commit title. This is cosmetic. Do not rewrite main for it.
- **Navigator file-read requests** named paths that do not exist on main (`sequeless-backend/...`, `com/sequeless/...`). Check every path with `git ls-tree origin/main` first. The root has no `sequeless-query`, `sequeless-automation` or `sequeless-backend` directory.
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
- A relayed claim, a `set_plan` or `decide` answer, or a CARRY FORWARD line is not a decision until the driver records it with `navigator_record` `decided` and `navigator_status` shows it. Check the reply against source before recording it. `navigator_status` returns large output; if it is saved to a file, read it with `jq`.
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `set_plan` for the first question of a session and `decide` for later ones. `settle` and `answer` are not offered in pair mode. `options` is only valid with `settle`. Only one job runs per server: a `job <id> active` error means wait for that job's result. Rounds can take over an hour. One question per `navigator_ask`. A batch is a series of asks in one fresh session, at most five.
- Navigator file reads: check every path with `git ls-tree origin/main` before submitting an excerpt. Label any partial excerpt with `[...]`. Send corrections in the same submission.
- Do not reuse these Navigator sessions for a new question: `cc206f68705e4d93956f7e58ddd8c6e9`, `c0e84f0a157f4cc1a0f5dc3f54952ce2`, `cb5a1ebdeb804ce893b35dced364435c`, `b150e05389fd46148771bec93b8bc980`, `c07146c090c444b18302e90478418aa7`, `09480ec46e184c8aa8dc03436a07e826`, `0739f198e75c403ab527d4ad6cc82164`, `32ac8aeab08346b6b67e54a49c2e1d44`, `ac015364523740e6b2eba306f8d31515`, `54529a32ee4b47b9a3ae073cabcbcb4e`, `92e8178d1fdf4829837dab8eb4d0ef95`, `359d962fb8cd498b8b34339abd46e5bc`, `95d0ab97a06845088d993ce3caa0f05e`, `acac3649758f47d2ad75687c198f13e8` and `cf09351ce0a144538f114de334e5d631`. Session `c4f4f2bbecca4223816663cbc1b096cb` is open and may continue its pending `call-0004` step; it takes no new question. Ask new questions in fresh sessions.
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact. This applies to this handoff too.
- Machine-local facts (paths, tokens, environment IDs) stay out of the repo.

## Local state on this machine (not in the repo)

- `prompt.md` at the repo root, git-ignored. Children read it from its absolute path.
- Worktrees live in sibling directories outside the repo tree. Worktrees on merged branches can be removed. The worktree on `docs/handoff-cycle8` is this handoff's docs PR and can be removed after it merges.
- Temporary copies under `/tmp` are not needed. The results they held are recorded in this file.
