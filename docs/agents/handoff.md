# Handoff

The state a new agent session needs to continue the CQRS extraction. Each run of the repeatable work prompt overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `ddc8dd7` (PR #175).

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md)
- Spec 13 resume plan (history and gotchas; section 21 is partly stale, see Drift): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md), [P3](../extraction/spec-13-p3-plan.md), [P4](../extraction/spec-13-p4-plan.md)
- Issue map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11); plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23); spec 13: [#41](https://github.com/sequeless/sequeless-prototype/issues/41)

## Merged in the last run

- PR [#168](https://github.com/sequeless/sequeless-prototype/pull/168), the P2d flip (`d577b20`). Merged after the owner's mixed-version procedure, per the session that reported it.
- PR [#175](https://github.com/sequeless/sequeless-prototype/pull/175), the P2e plan decisions, docs only (`ddc8dd7`).

## Open PRs

- [#106](https://github.com/sequeless/sequeless-prototype/pull/106): draft, compare document, **do not merge**.

## Drift to check against GitHub

- Issue [#41](https://github.com/sequeless/sequeless-prototype/issues/41) is **CLOSED** on GitHub, but P2e, P3 and P4 are not done and the plans treat #41 as open. Confirm with the owner whether to reopen it. Do not act on #41 as closed until this is settled.
- Resume plan section 21 still lists #168 as held. This file supersedes it for state.

## Next actions

1. **P2e code** is blocked on the owner's dev-stack checks (see Owner actions, items 1 and 2). Once they are reported: cut a branch from origin/main (do not rebase), implement [the P2e plan](../extraction/spec-13-p2e-plan.md), get a read-only review, run the gate, open the PR, and merge on approval.
2. Before P2e code, confirm that [#175](https://github.com/sequeless/sequeless-prototype/pull/175) recorded every P2e decision. The plan still shows some questions as open in places, for example Q3 at its line 51. If any are missing, a docs PR records them first.
3. **P3 decisions docs PR.** Recorded already: OQ7 and OQ11 (see Navigator decisions). Still open: OQ1, OQ2, OQ3, OQ4, OQ8, OQ12, OQ13 (gate or shape code), and OQ5, OQ6, OQ9, OQ10, OQ14, OQ15, OQ17 (not gating). OQ16 is an owner item. Record the answers in the P3 plan.
4. **P4 decisions docs PR.** Still open: OQ5(ii), OQ6, OQ7, OQ8, OQ9, OQ11, OQ12, OQ14, OQ15, OQ16, OQ17 in [the P4 plan](../extraction/spec-13-p4-plan.md), section 8.
5. **P3 code** after P2e merges and item 3 is done. The owner runs the dev-stack checks at deploy.
6. **P4 code** after P3 merges and item 4 is done.
7. **P5b plan** after item 4 is done: a plan drafted by a subagent, read-only review, Navigator questions, docs PR. Then P5b code after P4 merges.

## Navigator decisions (recorded in this file until the plans carry them)

Decided by the Navigator under the owner's delegation. Record each in the relevant plan when that plan's docs PR lands.

- P2e Q1: delete `KIND_ACTION_REQUEST` in P2e (option A, per DR-24).
- P2e Q2: holder class `TemporalActivityOptions` in `org.sequeless.adapter.automation.temporal`.
- P2e Q8: the zero check covers running ActionWorkflow executions only (option a).
- OQ7 (P3): the migration takes the next free number at merge time. V9 if the held index branch is unmerged, V10 if merged.
- OQ11 (P3): add `Workflow.getVersion` to `TimerWorkflowImpl.run`.
- B2 route 1 is recorded in the resume plan, section 11.

## Owner actions still open

1. **P2e dev-stack checks** (P2e plan, section 8, step 3): either the count of undrained `ActionRequest` rows, or a written loss acceptance on #41. Then the zero check of running `ActionWorkflow` executions.
2. **Q-A loss acceptance on #41**, in writing. The hand-stamp fallback needs it. Also decide whether #41 is reopened (see Drift).
3. **V8 or V9 claim-index window** (decision ix). The held branch `spec-13-dead-letter-index-v8` now can merge, since V8 is on main. It takes V9 or V10 under OQ7. The owner chooses the window for its exclusive-lock rebuild.
4. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. The owner says which adapter the dev stack runs.
5. Optional: confirm B2 route 1 on #41.

## Hazards

- Never use `--admin` to get past a red or missing required check, or a BLOCKED state.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check origin/main before merging.
- Work from a worktree cut from origin/main. The primary checkout is only safe after a fast-forward pull.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Maven needs the GitHub Packages token, so source the env file in the same command as every `mvn` run. Gate commands and the JDK path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default java is 21; the gate needs 25.
- Machine-local facts (paths, tokens) stay out of this repo.
