# Handoff

The state a new agent session needs to continue the CQRS extraction. Each run of the repeatable work prompt overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `e47f4ec` (PR #178).

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md)
- Spec 13 resume plan (history and gotchas; section 21 is partly stale, see Drift): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (decisions in section 7, gate in section 8), [P3](../extraction/spec-13-p3-plan.md) (decisions in section 8), [P4](../extraction/spec-13-p4-plan.md) (open questions in section 8)
- Issue map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (open); plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23) (closed); spec 13 tracker: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (closed; see Drift)

## Merged in this run

- PR [#177](https://github.com/sequeless/sequeless-prototype/pull/177), the P3 decisions, docs only (`b76a2c6`).
- PR [#178](https://github.com/sequeless/sequeless-prototype/pull/178), the previous handoff, docs only (`e47f4ec`). This file replaces it.

## Open PRs

- [#106](https://github.com/sequeless/sequeless-prototype/pull/106): draft, compare document. **Do not merge.**

## Drift to check against GitHub

- Issue [#41](https://github.com/sequeless/sequeless-prototype/issues/41) is **CLOSED** (closed 2026-11-23), but P2e, P3 and P4 are not done, and the plans treat #41 as open. The owner decides whether to reopen it. Do not act on #41 as closed until this is settled.
- Resume plan section 21 still lists #168 as held. This file supersedes it for state.
- `docs/extraction/remaining-work.md` is stale: lines 5, 13 and 26 predate the #168 merge and the #41 closure. Trust this file for state.
- #41 was closed automatically when #169 merged (2026-11-23 19:47Z). #169's body says "This PR does not close #41.", which GitHub still reads as a closing keyword. #168, #170, #171 and #172 also list #41 in `closingIssuesReferences`, but closed nothing. See the hazard on close keywords.
- Resolved since the last handoff: all P2e decisions are recorded (P2e plan section 7, items 1 to 14, from #175), and the P3 decisions are recorded (P3 plan section 8, from #177).

## Next actions

1. **Navigator questions for P3** (P3 plan section 8). Settle the open questions 5, 6, 9, 10 and 14 to 21 with the Navigator (`/navigator:pair`), except OQ16, which is the owner's (Owner action 4). Record each answer as a **Decided:** line under its question, attributed to the Navigator, in a docs PR. An Opus subagent reviews the PR before merge.
2. **P2e code.** The code is not gated; only the merge is (see Owner actions 1 and 2). Cut `spec-13-p2e` from `origin/main` (do not rebase). Implement the P2e plan: change list in section 3, items 1 to 8 in section 4, commit order in section 8, step 6. Run the gate in section 5 with the commands in resume plan section 21.6. Open the PR with the section 4 statuses in its body. Haiku implements; an Opus subagent reviews. Do not merge until Owner action 1 is reported in full: the zero count, or, if the count is not zero, the written acceptance on #41 (Owner action 2); then the zero check of running `ActionWorkflow` executions.
3. **P3 code** after P2e merges and item 1 is done. The migration takes the next free number at merge (P3 OQ7): V9 if the held index branch is unmerged, V10 if it is merged. Owner action 3 covers the held branch. Pin the merge to the reviewed head.
4. **P4 Navigator questions** (P4 plan section 8). Settle OQ5 to OQ9, OQ11, OQ12 and OQ16 before any P4 code, and OQ14, OQ15 and OQ17 before the P4 PR. Docs PR, Opus review.
5. **P4 code** after P3 merges and item 4 is done. Run the read-only review of the P4 plan before the code starts.
6. **P5b plan** after item 4: a plan drafted by a subagent, a read-only review, Navigator questions, a docs PR. P5b code waits for P4's merge.

## Navigator decisions

None this run. This run merged #177 and #178, then stopped at its work limit before any Navigator session. Earlier decisions are recorded in the plans:

- P2e: items 1 to 14 in P2e plan section 7 (for example Q1: delete `KIND_ACTION_REQUEST`; Q2: holder class `TemporalActivityOptions`; Q8: the zero check covers running `ActionWorkflow` executions only).
- P3: OQ1, OQ2, OQ3, OQ4 (revised), OQ7, OQ8, OQ11 (revised), OQ12 and OQ13 in P3 plan section 8. OQ7 is the migration-number rule in item 3 above.
- B2 route 1 is recorded in the resume plan, section 11.

## Owner actions still open

1. **P2e dev-stack checks** (P2e plan section 8, step 3): either the zero count of undrained `ActionRequest` rows, or a written loss acceptance on #41. Then the zero check of running `ActionWorkflow` executions. Both come before the P2e merge.
2. **Written loss acceptance on #41** (Q-A). The hand-stamp fallback needs it. Also decide whether #41 is reopened (see Drift).
3. **Claim-index window** (decision ix). The held branch `spec-13-dead-letter-index-v8` has no PR. It is behind main and still carries `V8__outbox_dead_letter_index.sql` and `PostgresOutboxIndexV8IT`, which collide with main's V8. Before it merges: `git merge origin/main`, renumber to the next free number (V9 if before P3, V10 after, P3 OQ7), rename the IT, run the gate, open a PR. The owner chooses the window for its exclusive-lock rebuild.
4. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. The owner says which adapter the dev stack runs.
5. Optional: confirm B2 route 1 on #41.

## Hazards

- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Never write `close`, `closes`, `fix`, `fixes`, `resolve` or `resolves` next to #41 in a PR body or commit, not even in a negation such as "does not close #41". GitHub reads it as a closing keyword. That is how #41 was closed. Write `Refs #41`.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check origin/main before merging.
- Work from a worktree cut from origin/main. The primary checkout is only safe after a fast-forward pull.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Maven needs the GitHub Packages token, so source the env file in the same command as every `mvn` run. Gate commands and the JDK path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default java is 21; the gate needs 25.
- The first start of any post-#168 main on dev runs V8, which marks undrained `ActionRequest` rows as processed (P2e plan section 9). Do the O-3 count before that start.
- P3's migration marks unprocessed timer rows as processed, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. Whether the O-4 check (no running `TimerWorkflow`) still gates the deploy is P3 OQ19, open.
- Do not resume Navigator session `cc206f68705e4d93956f7e58ddd8c6e9` (resume plan 21.3). `c0e84f0a157f4cc1a0f5dc3f54952ce2` is listed in 21.2 as not ended. Next action 1 may resume it or start a fresh session; record the answers in the plan either way.
- Machine-local facts (paths, tokens) stay out of this repo.
