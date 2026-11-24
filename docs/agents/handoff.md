# Handoff

The state a new agent session needs to continue the CQRS extraction. Each run of the repeatable work prompt overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `a8816f3` (PR #182, the P2e code). This handoff lands as a docs PR after it, so the next session starts from the merged head of that PR.

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate, section 17 is the merge list): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent (B2 is decided): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 8 decides questions 1 to 15 and 17 to 21; question 16 is the owner's; section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (open questions in section 8)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, CLOSED; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## Merged in this run

- PR [#182](https://github.com/sequeless/sequeless-prototype/pull/182), P2e code: removes `ActionWorkflow`, `KIND_ACTION_REQUEST` and the `ActionRequest` drain. Squash `a8816f3`, merged 2026-11-24T12:16:36Z with `--match-head-commit 481113d9707281908006043c604b90ae9280b4e1`. Gate PASS on that head (758 tests, 0 failures). Read-only review: APPROVE. Checks green. Merged on the owner's instruction, after the dev-stack checks below. No `--admin`.
- Dev-stack checks (P2e plan section 8, step 3), run on the owner's instruction on 2026-11-24 on the dev stack, before the merge:
  - (a) Undrained outbox count is zero for every kind. `sq_outbox` has 0 rows. Flyway is at V4.
  - (b) No Temporal state volume exists, so there are no running `ActionWorkflow` executions (Q8). No old-version process was running.
- This handoff PR, docs only.

## Open PRs

- [#106](https://github.com/sequeless/sequeless-prototype/pull/106): draft compare document. **Do not merge.**
- No other open PRs as of this handoff.

## Drift to check against GitHub

- [#41](https://github.com/sequeless/sequeless-prototype/issues/41) is **CLOSED** (2026-11-23T19:47:38Z). It closed when #169 merged, because #169's body used a negated closing phrase, which GitHub reads as a closing keyword. The spec 13 work is not finished. Whether to reopen is the owner's decision. Use `Refs #41` in PR bodies.
- **Migration number conflict (open).** P3 plan question 7 (and section 5) says P3 takes V9 if the held index branch (named there `spec-13-dead-letter-index-v8`) is unmerged, and V10 if it is merged. Resume plan 21.4(c) agrees. The renumbered `origin/spec-13-dead-letter-index-v9` (`87f5e42`, adds `V9__outbox_dead_letter_index.sql`, one commit behind main) is unmerged and already claims V9. That implies P3 takes V10. Flyway `outOfOrder` is false (resume plan section 16), so a V10 on main before V9 would break any database that applied V10. Settle this before P3 code, then update P3 plan section 4 items 20 and 21, section 5, section 6, question 7 and section 9 item 3 in a docs PR.
- The held branch `origin/spec-13-dead-letter-index-v8` (`6cf5938`) has no PR. It is behind main and still carries `V8__outbox_dead_letter_index.sql`, which collides with main's V8. It is superseded by the `-v9` branch above.
- Main ends at `V8__outbox_actions_drained.sql` (PR #168). P2e added no migration.

## Navigator decisions made this run

- None recorded in the repo this run.
- **Decided, not yet recorded.** Nine P4 open questions were decided by Navigator session `b150e05389fd46148771bec93b8bc980`: **OQ5, OQ6, OQ7, OQ8, OQ9, OQ11, OQ12, OQ14, OQ17** (P4 plan section 8). Recording them in the repo was denied by the auto-mode classifier. The owner has not decided how they should be recorded. Do not write them into the repo, or work around the denial, until the owner decides (Owner action 1).
- The local, uncommitted draft from the previous run (branch `docs/spec-13-p4-oq-decisions`, in a machine-local worktree) is left untouched. Do not commit it or delete it without the owner.
- P4 OQ15 is decided, option (a), by Navigator session `c0e84f0a157f4cc1a0f5dc3f54952ce2` (recorded in #184). P3 Decided lines are attributed to the same session.
- Do not resume Navigator session `cc206f68705e4d93956f7e58ddd8c6e9` (resume plan 21.3). Check a session with `navigator_status` before asking it again. Do not ask a second question while a session is busy.

## Next actions

1. **Record the nine P4 decisions** (listed above). Blocked on Owner action 1. If the owner gives a permission or records them, record them in the P4 plan's section 8 as "**Decided:** (Navigator, session `b150e05389fd46148771bec93b8bc980`)", in a docs PR with `Refs #41`, read-only review first. Take the decision text from that session's transcript or the owner, not from memory. Do not re-ask the questions.
2. **Settle the P3 migration number** (see Drift). Check `navigator_status` for session `c0e84f0a157f4cc1a0f5dc3f54952ce2` (the P3 session), then ask one Navigator question quoting P3 OQ7, resume plan 21.4(c), the `-v9` branch and the `outOfOrder` constraint. Then a P3-plan docs PR (see Drift). Do this before P3 code.
3. **P2e follow-ups.** A small PR with `Refs #41`, from the "Known non-blocking follow-ups" list in the PR #182 body. The three comment fixes, the `automation.md` wording and the one-assertion coverage test (risk 7) are the PR's scope. The `buildContext`, abstract `ActionExecutor` and round-trip-key items are optional. They touch code, so the gate runs again on the new head. Items 2 and 3 can start while item 1 waits on the owner.
4. **P3 code** (P3 plan section 9, item 3). After item 2's docs PR. The plan was already reviewed in #183. Include the saved frozen sets and the pair from section 4, item 29c (P4 OQ15 gates the P3 PR). Implement with a subagent, then a read-only review, then pin the merge to the reviewed head. The gate needs JDK 25. Find cited lines by name, not number, because P2e shifted them. The migration takes the number from item 2.
5. **P4 code** after P3 merges and Owner action 4 is answered. Order: record the nine decisions (item 1), a read-only review of the P4 plan, then code. Rewrite the stale citations in the P4 plan before code: line 31 (`ActionWorkflowImpl.ACTIVITY_OPTIONS`, now `TemporalActivityOptions`), lines 34 and 171 (they assume main ends at V7 and that P2d is unmerged), and its stale P3 line citations. Do this after P3 merges.
6. **P5b plan** after item 1 (resume plan S8: after S4, the P4 Navigator questions are recorded). A plan drafted by a subagent, a read-only review, Navigator questions, then a docs PR. P5b code waits for P4's merge.

## Owner actions still open

1. **Nine P4 decisions:** how they should be recorded. Either give a permission for that docs edit, or record them yourself. The Navigator has decided them (session `b150e05389fd46148771bec93b8bc980`).
2. **#41:** whether to reopen it, and the written loss acceptance (Q-A) that the hand-stamp fallback needs.
3. **Claim-index window** (decision ix in `remaining-work.md`) for the renumbered branch `spec-13-dead-letter-index-v9`. The owner picks the window for the exclusive-lock rebuild, or asks for the non-transactional route.
4. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. The owner says which adapter the dev stack runs. P4 code waits on this. P3's O-4 deploy check depends on it.
5. Optional: confirm B2 route 1 on #41, and the optional confirmations in the resume plan (line 159: O-8, B4 and item 21).

## Hazards

- **Dev is at Flyway V4** (checked 2026-11-24, outbox count zero). Current main's V5 to V8 run on the first dev start. V8 marks undrained `ActionRequest` rows as processed. Re-check the count before any dev start.
- **P3's migration marks unprocessed timer rows as processed**, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. The O-4 check gates only non-dev environments (P3 Q19). Re-run O-3 on dev before any P3 deploy. The count from 2026-11-24 does not carry over.
- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Write `Refs #41` in PR bodies and commits. Never put any of these words before that issue number, not even in a negation: `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves`, `resolved`.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check `origin/main` before merging.
- Work from a worktree cut from `origin/main`. The primary checkout is only safe after a fast-forward pull.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Maven needs the GitHub Packages token, so source the env file in the same command as every `mvn` run. The gate commands and the JDK 25 path are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default `java` is 21; the gate needs 25.
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `decide`; `settle` is refused. A reply is often a `tool_calls` round: read the named files, then `navigator_submit`. Rounds can take over an hour.
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact.
- Machine-local facts (paths, tokens) stay out of the repo.
