# Handoff

The state a new agent session needs to continue the CQRS extraction. Each run of the repeatable work prompt overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `76008b0` (PR #180).

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md)
- Spec 13 resume plan (history): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent (section 12 open items; B2 is decided): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (decisions in section 7, gate in section 8), [P3](../extraction/spec-13-p3-plan.md) (decisions in section 8; all decided except question 16), [P4](../extraction/spec-13-p4-plan.md) (open questions in section 8)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, CLOSED; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## Merged in this run

- PR [#180](https://github.com/sequeless/sequeless-prototype/pull/180), the P3 Navigator decisions, docs only (squash `76008b0`). It settles P3 open questions 5, 6, 9, 10, 14, 15 and 17 to 21, corrects the spec's B2 item to DECIDED, and fixes the stale lines in `remaining-work.md`.

## Open PRs

- [#106](https://github.com/sequeless/sequeless-prototype/pull/106): draft compare document. **Do not merge.** It is the only open PR.

## Drift to check against GitHub

- [#41](https://github.com/sequeless/sequeless-prototype/issues/41) is **CLOSED** (2026-11-23T19:47:38Z). It closed automatically when #169 merged, because #169's body uses a closing-keyword phrase for #41 (a negated form, which GitHub still reads as a closing keyword). The spec 13 work is not finished. Whether to reopen #41 is the owner's decision. Do not act on #41 as closed until that is settled.
- The held branch `origin/spec-13-dead-letter-index-v8` (`6cf5938`) has no PR. It is behind main and still carries `V8__outbox_dead_letter_index.sql`, which collides with main's V8 (`V8__outbox_actions_drained.sql`).
- Resume plan 21.1 to 21.5 predate #168's merge and #180. Only 21.6 and the 21.3 do-not-resume note still apply.
- Fixed in #180: the stale lines in `remaining-work.md` (the PR #168 "open" text and the #41 "open" text), and the spec's B2 item.

## Next actions

1. **P2e code.** The change list is P2e plan section 3, items 1 to 8 are section 4, and the commit order is section 8, step 6. Cut `spec-13-p2e` from `origin/main` (do not rebase). Run the gate (resume plan section 21.6). Open the PR with the section 4 statuses in its body. An implementer subagent implements; a read-only review subagent reviews. The code is not gated; only the merge is. Do not merge until owner action 1 is reported (see below).
2. **P3 plan review.** P3 plan section 9 requires a read-only review of the plan before any P3 code. The review checks the plan against the section 8 decisions and the code, and it does not re-decide them. Its scope includes resume plan 21.5 item 4 (P3 plan section 5 against spec line 128 on settling sets). Fix its findings in a docs PR.
3. **P3 code** after P2e merges and item 2 is done. Implement the section 8 decisions: the lookup checks the active behaviour's version against the row's stamp, and a mismatch fails the dispatch (open question 5); the timer start goes before the action loop in both adapters (question 10); the in-process put becomes `putIfAbsent` (question 17); the carrier kind string is in a value type in `spi/automation`, and `OutboxEntry` keeps five kinds (question 18); a `WorkflowReplayer` test covers the cancel-signal case (question 21); the new DR entry is DR-25 (question 14). The migration takes the next free number at merge: V9 if the held branch is unmerged, V10 if it has merged (question 7). Pin the merge to the reviewed head. The gate needs JDK 25.
4. **P4 Navigator questions** (P4 plan section 8). Settle OQ5 to OQ9, OQ11 and OQ12 with the Navigator before any P4 code. OQ16 is the owner's (owner action 4), and it also gates P4 code. Settle OQ14, OQ15 and OQ17 before the P4 PR. Docs PR, read-only review before merge.
5. **P4 code** after P3 merges, item 4 is done, and owner action 4 is answered. A read-only review of the P4 plan comes first.
6. **P5b plan** after item 4: a plan drafted by a subagent, a read-only review, Navigator questions, then a docs PR. P5b code waits for P4's merge.

## Navigator decisions made this run

Navigator session `c0e84f0a157f4cc1a0f5dc3f54952ce2` holds the P2e, P3 and P4 context. It is still open, and a later run can resume it. Each decision is a **Decided:** line in the P3 plan section 8, attributed to the Navigator:

- Q5: the lookup reads the active behaviour and fails the dispatch on a version mismatch with the row's stamp. The failure counts toward DR-23's three failures.
- Q6: P3 follows DR-23 (three failed dispatches, then dead-letter). The spec's B2 item is corrected to DECIDED.
- Q9: a fresh O-3 count at P3's deploy. O-4 is recorded at deploy, and it gates only non-dev environments.
- Q10: the timer start goes before the action loop in both adapters.
- Q14: a new DR in the P3 PR (DR-25 at merge).
- Q15: the in-process map is unbounded for the test adapter.
- Q17: the in-process put becomes `putIfAbsent`. This is a P3 code change. Its effect on a re-hold while the first timer is pending (it makes the adapters agree there) is a consequence to confirm at the code review, not a separate decision.
- Q18: the carrier's kind string lives in a value type in `spi/automation`. `OutboxEntry` keeps exactly five constants.
- Q19: the O-4 check is recorded at deploy and gates only non-dev environments.
- Q20: keep `cancel()` and the `cancelled` field for the default `getVersion` branch.
- Q21: P3 adds a `WorkflowReplayer` test over a recorded pre-P3 history.

Question 16 (the dev-stack adapter) is the owner's and stays open.

## Owner actions still open

1. **P2e dev-stack checks** (P2e plan section 8, step 3): the zero count of undrained `ActionRequest` rows, or a written loss acceptance on #41; then the zero check of running `ActionWorkflow` executions. Both come before the P2e merge.
2. **#41**: whether to reopen it, and the written loss acceptance (Q-A) that the hand-stamp fallback needs.
3. **Claim-index window** (decision ix in `remaining-work.md`): the owner picks the window for the exclusive-lock rebuild, or asks for the non-transactional route. After that, an agent merges `origin/main` into the held branch `spec-13-dead-letter-index-v8`, renumbers to the next free number, renames `PostgresOutboxIndexV8IT`, runs the gate, and opens a PR.
4. **Dev-stack adapter** (P3 question 16, P4 question 16): which adapter the dev stack runs. The repo sets no `sequeless.automation.adapter`.
5. Optional confirmations, which the repo says the owner may give: B2 (route 1, as amended by DR-23), O-8 (decided a), the B4 placement and item 21, and the Navigator's decisions above.

## Hazards

- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Never write `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves` or `resolved` next to #41 in a PR body or commit, not even in a negation. GitHub reads them as closing keywords. Write `Refs #41`.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- Renovate moves main during a session. Re-check `origin/main` before merging.
- Work from a worktree cut from `origin/main`. The primary checkout is only safe after a fast-forward pull.
- Use `git merge origin/main`, not rebase, to bring main into a feature branch.
- Maven needs the GitHub Packages token, so source the env file in the same command as every `mvn` run. The gate commands are in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). The default `java` is 21; the gate needs 25.
- The first start of any post-#168 main on dev runs V8, which marks undrained `ActionRequest` rows as processed (P2e plan section 9). Do the O-3 count before that start.
- P3's migration marks unprocessed timer rows as processed, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. The O-4 check gates only non-dev environments (question 19).
- Navigator, pair mode: `navigator_ask` takes `answer_tool` `decide`; pair mode offers only `set_plan` and `decide`, and `settle` is refused. A reply is often a `tool_calls` round (read the named files or grep, then `navigator_submit`), and most rounds take a few minutes, and some take over an hour. Do not ask a second question on a session while one is busy.
- Do not resume Navigator session `cc206f68705e4d93956f7e58ddd8c6e9` (resume plan 21.3).
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact.
- Machine-local facts (paths, tokens) stay out of the repo.
