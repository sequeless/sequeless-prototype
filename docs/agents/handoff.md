# Handoff

The state a new agent session needs to continue the CQRS extraction. Each run of the repeatable work prompt overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-24, at origin/main `5b5358c` (PR #188, the P4 revert; `a1915e8` was PR #187). This docs PR lands after it, so the next session starts from the merged head of this PR.

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate, section 17 is the merge list): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent (B2 is decided): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 8 holds the Decided lines; question 7 is the migration number; section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (section 8 holds proposed answers for OQ5 to OQ9, OQ11, OQ12, OQ14 and OQ17, all open and unverified; OQ15 is recorded but unverified; OQ16 is open)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, not open; see Drift), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## Merged in this run

- PR [#188](https://github.com/sequeless/sequeless-prototype/pull/188), docs only (squash `5b5358c`, `Refs #41`). It reverts the nine P4 `Decided` lines in P4 plan section 8 to open questions, keeps each proposed answer labelled "Proposed (unverified, no Navigator decision recorded)", and removes their attribution to a Navigator session. The owner approved this correction. Checks were green on head `5723d1e`; merged with `--match-head-commit`, no `--admin`. A read-only review approved after one rework round.
- This docs PR, docs only. It overwrites this handoff to record the migration question as open (see Navigator decisions) and to add the rename question as an owner action.

## Open PRs

- [#106](https://github.com/sequeless/sequeless-prototype/pull/106): draft compare document. **Do not merge.** No other PR is open (checked 2026-11-24).

## Drift to check against GitHub

- [#41](https://github.com/sequeless/sequeless-prototype/issues/41) is **not open**. Its state changed at 2026-11-23T19:47:38Z, when #169 merged; #169's body used a negated closing phrase. The spec 13 work is not finished. Reopening it is the owner's decision. Do not treat spec 13 as finished until that is settled.
- **P4 nine lines (corrected in #188).** PR #186 attributed nine P4 `Decided` lines to Navigator session `b150e05389fd46148771bec93b8bc980`. A status check on 2026-11-24 showed that session has `DECIDED: (none)`, so the attribution is withdrawn. The owner chose to revert them to open. Their proposed answers are not decisions.
- **Navigator attributions not confirmed (left unchanged, owner to review):**
  - P4 plan OQ15, option (a), attributed to session `c0e84f0a157f4cc1a0f5dc3f54952ce2`. That session's status dump (2026-11-24) has a DECIDED list with P2e and P3 entries but no P4 entry. The OQ15 wording appears only in its CARRY FORWARD notes. It is unverified. The P4 plan sections 5 and 6 and item 27, the P3 plan at lines 117, 130 and 236 (item 29c, section 4 and section 9), and this handoff rely on it.
  - P3 plan Decided lines other than OQ7 and OQ8 were not audited in this run. OQ7 (V9/V10 rule) and OQ8 (the migration stamps dead-lettered timer rows) are both in session `c0e84f0a`'s DECIDED list.
- **P3 migration number.** P3 OQ7 (P3 plan line 190) names the held branch `spec-13-dead-letter-index-v8`. The renumbered branch `origin/spec-13-dead-letter-index-v9` (`87f5e42`, no PR) is unmerged and already commits `V9__outbox_dead_letter_index.sql`. The -v8 branch (`6cf5938`) is behind main and carries a V8 that collides with main's V8. Main ends at V8 (`V8__outbox_actions_drained.sql`, PR #168, squash `d577b20`). Whether the OQ7 rule covers `-v9`, and who renames the file if P3 merges first, is open (see Navigator decisions).
- **Dev-stack checks.** The run notice said the P2e section 8 checks ran on 2026-11-24 with results recorded in this handoff. Neither this file nor origin/main records results. Treat both as open until a result is recorded.
- Resume plan sections 21.1 to 21.5 are partly stale; section 21.6 is the gate.

## Navigator decisions

- **Confirmed by session `c0e84f0a` (DECIDED list):** P3 OQ7 (the migration takes the next free number at merge: V9 if the held index branch is unmerged, V10 if merged) and P3 OQ8 (the migration stamps dead-lettered timer rows).
- **P4 OQ15:** option (a) is recorded in the P4 plan, attributed to `c0e84f0a`. Unverified (see Drift).
- **Open, with proposed answers that are not decisions** (P4 plan section 8):
  - OQ5: proposed (a), 409 for a state-less object.
  - OQ6: proposed (a), keep `existing.version()`.
  - OQ7: proposed (a), drop the signal body.
  - OQ8: proposed (a), let `IllegalStateException` surface.
  - OQ9: proposed (a), first match in declaration order; a failing guard rejects with 409.
  - OQ11: proposed (b), the migration stamps dead-lettered rows.
  - OQ12: proposed (i), keep `awaitState`.
  - OQ14: proposed, P4 adds DR-26 at merge.
  - OQ17: proposed (a), the same wording in both panels.
- **OQ16:** open, owner action 3.
- **P3 migration number, open.** Sent on 2026-11-24 to a fresh pair session, `cb5a1ebdeb804ce893b35dced364435c`. Q1: does the OQ7 rule cover `spec-13-dead-letter-index-v9`? Q2: if P3 merges first, who renames the index file to V10? The session first returned a `tool_calls` round, and an excerpt of P3 plan lines 95 to 96, 127, 188 and 190 and the `main` migration listing were submitted. Its reply then said Q1 option (b) is Decided, and Q2 option (iii), the owner, is not decided. That reply is not a recorded decision. A status check after the reply shows `DECIDED: (none)`, and Q1 appears only in its CARRY FORWARD notes. Both questions stay open. The reply's Q1 proposal, unverified: P3 takes V9 while the index is unmerged, and the `-v9` file is renamed to V10 when it lands after P3.

## Next actions

1. **P3 migration number (open).** Call `navigator_status` on `cb5a1ebdeb804ce893b35dced364435c`. If its DECIDED list shows Q1 or Q2, record that entry, citing the session, in P3 plan section 8, question 7, in a docs PR (`Refs #41`). If it does not, ask the two questions again in a fresh pair session at the start of the next run, and record only what that session's DECIDED list shows. Do not resume `c0e84f0a` (round limit). P3 code waits on Q1.
2. **OQ15, then the P4 open questions.** OQ15 gates the P3 PR (P3 plan item 29c) and is unverified (see Drift). Ask it first, to a fresh session, and record only a decision that session shows. Then settle OQ5 to OQ9, OQ11 and OQ12 before any P4 code, and OQ14 and OQ17 before the P4 PR. Ask them in batches of at most five, to a fresh session, with the proposed answers above given as unverified inputs. Record only decisions the session shows.
3. **P2e follow-ups.** The bullets under "Known non-blocking follow-ups" in PR #182 are a separate docs or code PR (`Refs #41`). Code changes need the gate (resume plan 21.6, JDK 25 plus the Packages token) and a read-only review. Pin the merge to the reviewed head.
4. **P3 code.** After item 1 settles the number. The migration takes that number, and the saved frozen sets and the pair from P3 plan item 29c are added. Implement with a subagent, then a read-only review. Pin the merge to the reviewed head. Re-run the O-3 count on dev before any P3 deploy.
5. **P4 code.** Blocked on P3 merging and on Owner action 3 (the dev-stack adapter, P4 OQ16). Before code: a read-only review of the P4 plan. P4 plan lines 34 and 171 assume main ends at V7; V8 is on main (PR #168). Rewrite them after P3 merges.
6. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.

## Owner actions still open

1. **#41**: whether to reopen it, and the written loss acceptance (Q-A) that the hand-stamp fallback needs.
2. **Claim-index window** (decision ix in `remaining-work.md`) for `spec-13-dead-letter-index-v9` (`87f5e42`). The owner picks the window for the exclusive-lock rebuild, or asks for the non-transactional route. `spec-13-dead-letter-index-v8` (`6cf5938`) is the older, stale branch.
3. **Dev-stack adapter** (P3 OQ16, P4 OQ16). The repo sets no `sequeless.automation.adapter`, and neither auto-configuration has `matchIfMissing`. The owner says which adapter the dev stack runs.
4. **Dev-stack checks before any post-#168 main starts on dev** (P2e plan section 8, step 3). (a) A zero count of undrained `ActionRequest` rows on dev, or the owner's written loss acceptance (Q-A). (b) With the old version stopped, the zero check of running `ActionWorkflow` executions (Q8). No result is recorded yet (see Drift). Also re-run the O-3 count on dev before any P3 deploy.
5. Optional: confirm B2 route 1 on #41, and the optional confirmations in the resume plan (line 159: O-8, B4 and item 21).
6. **Who renames the index file from V9 to V10 if P3 merges before the index branch** (migration question Q2). The Navigator did not assign it. The owner decides, unless a decided answer is recorded first.

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
- Do not resume Navigator sessions `cc206f68705e4d93956f7e58ddd8c6e9` (resume plan 21.3) or `c0e84f0a157f4cc1a0f5dc3f54952ce2` (round limit).
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact.
- Machine-local facts (paths, tokens) stay out of the repo.
