# Handoff

The state a new agent session needs to continue the CQRS extraction. Each run of the repeatable work prompt overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `da876a7` (PR #176).

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md)
- Spec 13 resume plan (history and gotchas; section 21 is stale, see Drift): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md), [P3](../extraction/spec-13-p3-plan.md), [P4](../extraction/spec-13-p4-plan.md)
- Issue map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11); plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23) (closed); spec 13: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (closed; see Drift)

## Merged in this run

- Nothing to main. PR [#177](https://github.com/sequeless/sequeless-prototype/pull/177) (the P3 decisions, docs only) passed a read-only review (APPROVE; this is not a GitHub review, and its `reviewDecision` is empty). Its checks are green and its state is CLEAN at `89400a1`. The merge was refused by the auto-mode permission classifier. See Owner actions, item 6.

## Open PRs

- [#177](https://github.com/sequeless/sequeless-prototype/pull/177): the P3 decisions in the P3 plan, section 8. Open because the merge was refused. Merge with `--match-head-commit 89400a112df1c564c8f0958cf6430f1b0cdffbb5`.
- [#106](https://github.com/sequeless/sequeless-prototype/pull/106): draft, compare document, **do not merge**.

## Drift to check against GitHub

- Issue [#41](https://github.com/sequeless/sequeless-prototype/issues/41) is **CLOSED** on GitHub, but P2e, P3 and P4 are not done, and the plans treat #41 as open. The owner has not said whether to reopen it. Do not act on #41 as closed until that is settled.
- Resume plan section 21 is stale: it still lists #168 as held. This file supersedes it for state.
- The origin branch `docs/spec-13-p3-plan` is stale. Its four correction commits were already on main through PR #171, so there is nothing to merge from it. The owner may delete it.

## Next actions

1. Run `gh pr view 177 --json state`. If it is MERGED, skip the #177 merge and owner action 6, and tell the Navigator (session `c0e84f0a157f4cc1a0f5dc3f54952ce2`) that the P3 gating questions (1 to 4, 7, 8, 11 to 13) are recorded (resume plan section 21.2). If it is OPEN, merge it at `89400a1` once owner action 6 allows.
2. Ask the Navigator whether P2e adds the spec line 208 kind-set assertion at seven. The P2e plan does not assign it, and OQ13 only settles P3 (adds it fresh at five). Record the answer in the P2e plan by a docs PR before the P2e PR opens; if P2e adds the assertion, the P2e code PR includes it.
3. **P2e code can start now.** Cut a branch from origin/main (do not rebase), implement [the P2e plan](../extraction/spec-13-p2e-plan.md) (its decisions are recorded by PR #175), get a read-only review, run the gate on JDK 25, and open the PR. Only its merge waits on Owner actions 1 and 2 (P2e plan, section 8, item 1).
4. **Renumber the held index branch** `spec-13-dead-letter-index-v8`. It still adds `V8__outbox_dead_letter_index.sql` and `PostgresOutboxIndexV8IT`, which collide with main's V8. Renumber to the next free number (V9, or V10 if P3 lands first; P3 OQ7) in a worktree cut from origin/main, and open a PR. The owner then picks the window for its exclusive-lock rebuild (Owner actions, item 4).
5. **P3 open questions.** The Navigator settles OQ5, 6, 9, 10, 14, 15, 16, 17, 18, 19, 20 and 21 in [the P3 plan](../extraction/spec-13-p3-plan.md), section 8, then a docs PR records them. A read-only plan review may run once OQ18 is settled (P3 plan, section 9). OQ16 is an owner item.
6. **P3 code** after P2e merges and the read-only plan review. OQ18 is needed by plan item 16; the other open questions do not gate code (plan section 9). At deploy, the owner runs the O-3 and O-4 checks on the stopped dev stack.
7. **P4 decisions docs PR.** Settle the open P4 questions in [the P4 plan](../extraction/spec-13-p4-plan.md), section 8: OQ5(ii), 6, 7, 8, 9, 11, 12, 14, 15, 16, 17. Use the same challenge-and-review loop as P3.
8. **P4 code** after P3 merges and item 7 is done.
9. **P5b plan** after item 7: Haiku draft, Opus review, Navigator questions, docs PR. Then P5b code after P4 merges.

## Navigator decisions made this run

Session `c0e84f0a157f4cc1a0f5dc3f54952ce2` (pair mode). Recorded in [the P3 plan](../extraction/spec-13-p3-plan.md), section 8, on #177.

- P3 OQ2: keep `timerKey` as the workflow id; accept the re-hold divergence; no hold-resume-hold test in P3.
- P3 OQ3: a new `TriggerEvaluator` method resolves a `TransitionFired` row's timers; the adapters call it.
- P3 OQ4 (revised after a challenge): the port gains a `TriggerEvaluator`, resolves the timer before starting `TimerWorkflow`, keys it by the resolved `timerKey`, and builds the `TimerScheduled`-shaped entry that `run` takes. `onTimerElapsed` keeps its `OutboxEntry` input.
- P3 OQ8: the P3 migration stamps dead-lettered timer rows, as V8 does for `ActionRequest`.
- P3 OQ11 (revised after review): `run` keeps its `(OutboxEntry, Scope)` signature; `Workflow.getVersion` guards the body change; no drain or terminate of running `TimerWorkflow` executions (the O-4 half of spec step 4); the O-3 row drain stays (OQ8).
- P3 OQ12: `timerKey` moves to `spi/outbox`.
- P3 OQ13: P3 adds the `OutboxEntryTest` kind-set assertion at five constants.
- Earlier decisions in the same session: P3 OQ1 (no cancel after P3), OQ7 (migration number at merge); the P2e decisions recorded by PR #175.

## Owner actions still open

1. **P2e dev-stack check, count** (P2e plan, section 8, step 3): a zero count of undrained `ActionRequest` rows on dev, or written acceptance of the loss on #41.
2. **P2e dev-stack check, running executions** (P2e plan, section 8, step 3): the zero check of running `ActionWorkflow` executions, with the old version stopped.
3. **Q-A loss acceptance on #41**, in writing. Also decide whether #41 is reopened (see Drift).
4. **Claim-index window** (decision ix): the owner picks the window for the exclusive-lock rebuild of the held index branch, once item 4 of Next actions has renumbered it.
5. **Dev-stack adapter** (P3 OQ16, P4 OQ16): the repo sets no `sequeless.automation.adapter`. The owner says which adapter the dev stack runs.
6. **Merge #177** if it is still open. The auto-mode permission classifier refused the merge, though the work prompt pre-approves merges. Either merge it at `89400a1`, or allow PR merges for this workspace so the next run can.
7. Optional: confirm B2 route 1 on #41.

## Hazards

- Never use `--admin` to get past a red or missing required check, or a BLOCKED state.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check origin/main before merging.
- Work from a worktree cut from origin/main. The primary checkout is only safe after a fast-forward pull.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Maven needs the GitHub Packages token: source the env file in the same command as every `mvn` run. Gate commands and the JDK path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default java is 21; the gate needs 25.
- Old worktrees and branches exist on the machine. Check each against origin/main before reusing it; much of their content is already on main.
- Machine-local facts (paths, tokens) stay out of this repo.
