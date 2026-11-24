# Handoff

The state a new agent session needs to continue the CQRS extraction. Each run of the repeatable work prompt overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `c05de53` (PR #186, the P4 Navigator decisions). This handoff lands as a docs PR after it, so the next session starts from the merged head of that PR.

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate, section 17 is the merge list): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent (B2 is decided): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 8 decides questions 1 to 15 and 17 to 21; question 16 is the owner's, and question 7 is the migration number; section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (section 8 holds the Decided lines for OQ5 to OQ9, OQ11, OQ12, OQ14, OQ15 and OQ17; OQ16 is open; the evidence block after OQ17 has the current line citations)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, no longer open; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## Merged in this run

- PR [#186](https://github.com/sequeless/sequeless-prototype/pull/186), docs only (squash `c05de53`). It records the nine P4 Navigator decisions in P4 plan section 8 and refreshes the evidence block against `36d713f`. Two citations had moved after #182: `TransitionsEndToEndTest` (now 293) and the `automation.md` audit wording (now 646). The read-only review approved it; its two wording nits went in as a second commit (`a59159d`). Checks green on that head, merged with `--match-head-commit`, no `--admin`.
- This handoff PR, docs only.

## Open PRs

- [#106](https://github.com/sequeless/sequeless-prototype/pull/106): draft compare document. **Do not merge.** No other PR is open.

## Drift to check against GitHub

- The spec 13 tracker, [#41](https://github.com/sequeless/sequeless-prototype/issues/41), is no longer open. Its state changed on 2026-11-23T19:47:38Z, when #169 merged. #169's body used a negated closing phrase, which GitHub reads as a closing keyword. The spec 13 work is not finished. Whether to reopen it is the owner's decision. Do not treat the spec 13 work as finished until that is settled.
- **The P4 Decided lines are attributed to Navigator session `b150e05389fd46148771bec93b8bc980`, but its status at check time (2026-11-24) showed `DECIDED: (none)`, and it said it was still deciding from context.** The answers were supplied by the owner from that session's task `4e88dea4`, which this run could not read. Confirm them against the session before any P4 code relies on them. If they differ, correct section 8 in a docs PR (`Refs #41`).
- **P3 migration number: OQ7 is decided, but the rule does not cover the renumbered branch.** P3 plan section 8, question 7 (line 190) says V9 if the held branch `spec-13-dead-letter-index-v8` is unmerged, V10 if it is merged. Sections 4 (items 20 and 21) and 5 say V9 or V10 per that rule. The renumbered branch `spec-13-dead-letter-index-v9` (`87f5e42`, no PR) is unmerged and already commits `V9__outbox_dead_letter_index.sql`. If P3 merges first, that file needs renaming to V10. The gap is who does that and whether the rule covers `-v9`. Main ends at V8 (`V8__outbox_actions_drained.sql`).
- Two held index branches exist with no PR: `spec-13-dead-letter-index-v8` (`6cf5938`, behind main, carries a V8 that collides with main's V8) and `spec-13-dead-letter-index-v9` (`87f5e42`, unmerged, renumbered to V9).
- Resume plan sections 21.1 to 21.5 are partly stale; section 21.6 is the gate.

## Navigator decisions made this run

- **P4 (PR #186, P4 plan section 8).** Each is a `Decided` line attributed to `b150e05389fd46148771bec93b8bc980`; see the drift item above on that attribution.
  - OQ5: option (a), 409.
  - OQ6: option (a), keep `existing.version()`.
  - OQ7: option (a), drop the body.
  - OQ8: option (a), let `IllegalStateException` surface.
  - OQ9: option (a), first match in declaration order, a failing guard rejects with 409.
  - OQ11: option (b), the migration stamps dead-lettered rows.
  - OQ12: option (i), keep `awaitState`.
  - OQ14: P4 adds DR-26 at merge.
  - OQ17: option (a), the same wording in both panels.
- OQ15 is unchanged (option (a), session `c0e84f0a157f4cc1a0f5dc3f54952ce2`).
- **OQ16 is not decided.** It is the owner's (Owner action 3).
- No P3 question was asked this run. The run stopped at its work limit after #186 landed.

## Next actions

1. **Confirm the P4 Decided attribution.** Call `navigator_status` on `b150e05389fd46148771bec93b8bc980` and read its DECIDED list. If it shows the nine answers, nothing changes. If it shows others, or none, open a docs PR (`Refs #41`) that corrects P4 plan section 8. Do this before any P4 code.
2. **P3 migration number, narrow question.** Start a fresh Navigator session with `navigator_start`. Do not resume `c0e84f0a157f4cc1a0f5dc3f54952ce2` (round limit) or `cc206f68705e4d93956f7e58ddd8c6e9`. Check `navigator_status` first. Ask one narrow question. Question 7 is decided, and resume plan 21.3 says not to re-ask it. So ask only: does the question 7 rule cover `spec-13-dead-letter-index-v9` (`87f5e42`), which is unmerged, has no PR and already commits `V9__outbox_dead_letter_index.sql`? And if P3 merges first, who renames that file to V10? Record the answer in P3 plan section 8, question 7, with a docs PR (`Refs #41`). If there is no answer, record it as open. Do not start P3 code until it is settled.
3. **P2e follow-ups PR.** The bullets under "Known non-blocking follow-ups" in PR #182 hold eight changes, two of them optional (the `buildContext` simplification, and making `ActionExecutor` abstract), and one note about commit `a017f5b`, which is not a change. Land the changes as one PR (`Refs #41`). Items that change code or comments need the gate (resume plan 21.6, JDK 25 plus the Packages token) and a read-only review. Pin the merge to the reviewed head.
4. **P3 code.** After item 2 settles the number. The migration takes that number, and the saved frozen sets and the pair from P3 plan item 29c are added. Implement with a subagent, then a read-only review. Pin the merge to the reviewed head. Re-run the O-3 count on dev before any P3 deploy.
5. **P4 code.** Blocked on P3 merging and on Owner action 3 (the dev-stack adapter, P4 OQ16). Before code: a read-only review of the P4 plan. P4 plan lines 34 and 171 assume main ends at V7, but V8 is on main (PR #168); rewrite them after P3 merges.
6. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.

## Owner actions still open

1. **#41**: whether to reopen it, and the written loss acceptance (Q-A) that the hand-stamp fallback needs.
2. **Claim-index window** (decision ix in `remaining-work.md`) for `spec-13-dead-letter-index-v9` (`87f5e42`). The owner picks the window for the exclusive-lock rebuild, or asks for the non-transactional route. `spec-13-dead-letter-index-v8` (`6cf5938`) is the older held branch.
3. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. The owner says which adapter the dev stack runs.
4. **Dev-stack checks before any post-#168 main starts on dev** (P2e plan section 8, step 3). (a) A zero count of undrained `ActionRequest` rows on dev, or the owner's written loss acceptance (Q-A). (b) With the old version stopped, the zero check of running `ActionWorkflow` executions (Q8). Also re-run the O-3 count on dev before any P3 deploy.
5. Optional: confirm B2 route 1 on #41, and the optional confirmations in the resume plan (line 159: O-8, B4 and item 21).

## Hazards

- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Never write `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves` or `resolved` next to #41 in a PR body or commit, not even in a negation. Write `Refs #41`.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check `origin/main` before merging.
- Work from a worktree cut from `origin/main`. The primary checkout is behind `origin/main`; fast-forward it before use.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- A branch created from `origin/main` tracks `origin/main`. Push with an explicit refspec (`git push -u origin HEAD:refs/heads/<branch>`), so it can never update `main`.
- Maven needs the GitHub Packages token, so source the local env file in the same command as every `mvn` run. The gate commands and the JDK 25 path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default `java` is 21; the gate needs 25.
- The first start of any post-#168 main on dev runs V8, which marks undrained `ActionRequest` rows as processed (P2e plan section 9). Do the O-3 count before that start.
- P3's migration marks unprocessed timer rows as processed, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. The O-4 check gates only non-dev environments (P3 plan section 8, question 19).
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `decide`; `settle` is refused. A reply is often a `tool_calls` round: read the named files, then `navigator_submit`. Rounds can take over an hour. Do not ask a second question on a busy session.
- Do not resume Navigator session `cc206f68705e4d93956f7e58ddd8c6e9` (resume plan 21.3) or `c0e84f0a157f4cc1a0f5dc3f54952ce2` (round limit).
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact.
- Machine-local facts (paths, tokens) stay out of the repo.
