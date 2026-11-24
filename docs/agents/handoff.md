# Handoff

The state a new agent session needs to continue the CQRS extraction. Each run of the repeatable work prompt overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `12f9a6f` (PR #183, the P3 plan fixes). This handoff lands with the Navigator's P4 open question 15 decision (see below), so the next session starts from the merged head of its PR.

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent (B2 is decided): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (code is on PR #182), [P3](../extraction/spec-13-p3-plan.md) (section 8 decides questions 1 to 15 and 17 to 21; question 16 is the owner's), [P4](../extraction/spec-13-p4-plan.md) (open questions in section 8)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, CLOSED; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## Merged in this run

- PR [#183](https://github.com/sequeless/sequeless-prototype/pull/183), the P3 plan after two review rounds, docs only (squash `12f9a6f`). The Decided lines are unchanged except Q17, which gained a pointer to item 16.
- This handoff PR, docs only. It records the Navigator's P4 OQ15 decision in the P4 plan and updates the P3 plan's frozen-set text to match (section 4, new item 29c; section 5; section 9, item 3).

## Open PRs

- [#182](https://github.com/sequeless/sequeless-prototype/pull/182): P2e code, branch `spec-13-p2e`, head `481113d`. Gate PASS on that head (758 tests, 0 failures, failsafe ITs including the Postgres Testcontainers ITs). Read-only review: APPROVE, no blockers. **Not merged:** it waits on Owner action 1. Non-blocking follow-ups are listed in the PR body.
- [#106](https://github.com/sequeless/sequeless-prototype/pull/106): draft compare document. **Do not merge.**

## Drift to check against GitHub

- [#41](https://github.com/sequeless/sequeless-prototype/issues/41) is **CLOSED** (2026-11-23T19:47:38Z). It closed when #169 merged, because #169's body used a negated closing phrase, which GitHub reads as a closing keyword. The spec 13 work is not finished. Whether to reopen is the owner's decision. Do not act on #41 as closed until that is settled.
- The held branch `origin/spec-13-dead-letter-index-v8` (`6cf5938`) has no PR. It is behind main and still carries `V8__outbox_dead_letter_index.sql`, which collides with main's V8.
- The P4 plan's OQ15 text cites the P3 plan's section 5 at "line 117" (its number when the P4 plan was written). #183 withdrew that sentence. The frozen-set bullets are now section 5 of the P3 plan, lines 129 to 130 after this PR. The OQ15 text keeps the citation as history.

## Navigator decisions made this run

- **P4 OQ15 (frozen sets): option (a)**, recorded in the P4 plan under OQ15 as "**Decided:** (Navigator, session ...)". The chain starts at P3: P3 saves the post-P2 set and the post-P3 set and adds the pair. OQ15 gates the P3 PR. Its consequences are section 4, item 29c and section 9, item 3 of the P3 plan.
- No other new Decided line. The P3 Decided lines are unchanged and attributed to the Navigator in the P3 plan, section 8.
- The Navigator session is `c0e84f0a157f4cc1a0f5dc3f54952ce2`. Check its state with `navigator_status` before asking again. Do not ask a second question while the session is busy.

## Next actions

1. **P2e merge** (PR #182). Waits on Owner action 1. When the owner reports it, confirm the head is still `481113d` and origin/main is as expected, then merge with `gh pr merge 182 --squash --match-head-commit <full 40-character SHA>`. Then the non-blocking follow-ups listed in the PR body, as a separate PR. Any code change moves the head, so it needs the gate again.
2. **P3 code**, after #182 merges (P3 plan section 9, item 3). The migration takes the next free number at merge: V9 if the held branch is unmerged, V10 if it is merged (P3 OQ7). Also add the saved frozen sets and the pair from item 29c. Implement with a subagent, then a read-only review; pin the merge to the reviewed head. The gate needs JDK 25. Find cited lines by name, not number, because P2e shifts them.
3. **P4 Navigator questions** (P4 plan section 8). OQ15 is answered. Settle OQ5 to OQ9, OQ11 and OQ12 before any P4 code. OQ16 is the owner's (Owner action 4) and also gates P4 code. Settle OQ14 and OQ17 before the P4 PR. Docs PR, read-only review. The P4 plan cites P3 plan line numbers that are now stale; refresh them in that PR. The P4 plan's section 5 bullet, section 6 line and item 27 are already edited for OQ15 option (a) in this PR; check them against the Decided line.
4. **P4 code** after P3 merges, item 3 is done, and Owner action 4 is answered. A read-only review of the P4 plan comes first. P4 plan lines 34 and 171 assume main ends at V7 and that P2d is unmerged; rewrite them after P3 merges.
5. **P5b plan** after item 3: a plan drafted by a subagent, a read-only review, Navigator questions, then a docs PR. P5b code waits for P4's merge.

## Owner actions still open

1. **P2e dev-stack checks** (P2e plan section 8, step 3). Both come before the #182 merge: (a) a zero count of undrained `ActionRequest` rows on dev, or the owner's written loss acceptance on #41; (b) with the old version stopped, the zero check of running `ActionWorkflow` executions (Q8). A green gate does not satisfy either check. Do the count before the first start of any post-#168 main on dev (V8 marks undrained rows processed).
2. **#41**: whether to reopen it, and the written loss acceptance (Q-A) that the hand-stamp fallback needs.
3. **Claim-index window** (decision ix in `remaining-work.md`) for the held branch `spec-13-dead-letter-index-v8`. The owner picks the window for the exclusive-lock rebuild, or asks for the non-transactional route.
4. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. The owner says which adapter the dev stack runs.
5. Optional: confirm B2 route 1 on #41, and the optional confirmations in the resume plan (line 159: O-8, B4 and item 21).

## Hazards

- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Never write `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves` or `resolved` next to #41 in a PR body or commit, not even in a negation. Write `Refs #41`.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check `origin/main` before merging.
- Work from a worktree cut from `origin/main`. The primary checkout is only safe after a fast-forward pull.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Maven needs the GitHub Packages token, so source the env file in the same command as every `mvn` run. The gate commands and the JDK 25 path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default `java` is 21; the gate needs 25.
- The first start of any post-#168 main on dev runs V8, which marks undrained `ActionRequest` rows as processed (P2e plan section 9). Do the O-3 count before that start.
- P3's migration marks unprocessed timer rows as processed, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. The O-4 check gates only non-dev environments (P3 Q19).
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `decide`; `settle` is refused. A reply is often a `tool_calls` round: read the named files, then `navigator_submit`. Rounds can take over an hour. Do not ask a second question on a busy session.
- Do not resume Navigator session `cc206f68705e4d93956f7e58ddd8c6e9` (resume plan 21.3).
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact.
- Machine-local facts (paths, tokens) stay out of the repo.
