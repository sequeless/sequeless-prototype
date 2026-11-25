# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-25, at origin/main `a7089bb` (PR #204, the claim index). The docs PR that carries this handoff lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

## Start a new session

1. Read this file, then `docs/extraction/spec-13-p4-plan.md` (its section 8 opens with the recorded Navigator decisions; section 4 is the P4 change list) and `docs/extraction/remaining-work.md`.
2. Check the state against GitHub: `origin/main`, open PRs (only #106, a draft that must not merge), and issue #41 (OPEN).
3. Navigator: the P3 and P4 questions the code depends on are decided (see "Navigator decisions"). Do not re-ask them. A new question goes to a fresh session, one `navigator_ask` per question, at most five per batch.
4. Aim the cycle at code: P4 (next actions, item 1). Code first, one handoff PR at the end of the cycle.
5. Before any PR or merge, follow "Hazards".

## Read first

- Spec 13 parent (the settling rule at line 128; O-16 at line 362 is the DR precedent): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- P4 plan (decisions block at the top of section 8; section 4 change list; section 9 merge order): [docs/extraction/spec-13-p4-plan.md](../extraction/spec-13-p4-plan.md). Its line numbers are stale: cite origin/main with `git grep -n`.
- P3 plan (done; its OQ19 line now carries the 2026-11-25 rule). The P3 decisions recorded in this run (sessions `5391f7cc...` and `9f8f8d8b...`, and the earlier sessions) live in this handoff under "Navigator decisions", not in the plan: [docs/extraction/spec-13-p3-plan.md](../extraction/spec-13-p3-plan.md)
- Resume plan (section 21.6 is the gate command): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Decision records: [DR-23](../architecture/02-decision-records.md) (dead-letter), [DR-24](../architecture/02-decision-records.md) (actions on the TransitionFired row), [DR-25](../architecture/02-decision-records.md) (timers from the TransitionFired row; cancellation ends). DR-26 is P4's, at merge.
- Automation architecture: [docs/architecture/automation.md](../architecture/automation.md)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, OPEN), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Main:** origin/main `a7089bb` (#204). Code landed this cycle, in order: #208 (`0fe5180`, the pre-change replay test for `TimerWorkflowImpl`), #209 (`3ce0902`, the timer lookup behind `TriggerEvaluator`, additive), #210 (`a722a7f`, the P3 cut-over: timers start from the `TransitionFired` row, the timer kinds are removed, V9, post-P3 frozen set, DR-25), and #204 (`a7089bb`, V10 non-transactional claim index, merged after V9 as the owner's rule required). P3 is on main.
- **P3 deploy is NOT done.** Merging P3 does not deploy it. Deploy needs the owner's approval and the counts in "Owner actions".
- **Migrations on main (Command):** V1, V3 to V10. V9 is P3's (drains `TimerScheduled` and `TimerCancelled`, stamps dead-lettered timer rows). V10 is the claim index. The next free number is **V11** (P4).
- **Query migrations on main:** V1 and V2, a separate sequence.
- **Frozen sets on main:** `frozen-migrations/3328993` (cut-over), `fbb82c5` (P2a, V1 to V5), `post-p7/command`, `post-p7/query`, `post-p2/command` (V1, V3 to V8, saved by #206), `post-p3/command` (V1, V3 to V9, saved by #210). V10 saves no frozen copy (Navigator decision, below). P4's settling pair starts at `post-p3` (spec line 128).
- **DRs on main:** DR-20 to DR-25. The next is **DR-26** (P4, at merge).
- **Open PRs:** #106 (draft compare document; do not merge). Nothing else.
- **Issue #41:** OPEN.
- **Superseded remote branches (owner action 2):** `origin/spec-13-dead-letter-index-v9` (`87f5e42`) carries a `V9__outbox_dead_letter_index.sql` that collides with main's V9. Do not merge it. `origin/spec-13-dead-letter-index-v8` (`6cf5938`) is stale. Both are superseded; the owner confirms their deletion.
- **Merged branches still on the remote (delete on the owner's go-ahead):** `spec-13-p3-replay-test`, `spec-13-p3-timer-lookup`, `spec-13-p3-cut-over`, `spec-13-claim-index-nontx`. Older `docs/spec-13-p3-*` and `docs/spec-13-p4-*` branches are not to be merged.

## Navigator decisions

The record flow (`navigator:pair`): a decision counts once it is recorded with `navigator_record` (kind `decided`) and `navigator_status` shows it in that session's DECIDED list. A reply that is a plan step, a relayed claim, or a carry-forward line is not a decision until recorded.

**P3 (all in the DECIDED lists; this run recorded them):**
- Session `5391f7cc4940429283819538a8396c37`:
  - OQ5: the timer lookup compares the active **behaviour**'s ontology version with the row's stamp; the adapters keep their automation-half check.
  - OQ6: wording follows DR-23 (three failed dispatches, then dead-letter); the spec B2 text is correct.
  - OQ14: P3's DR is a new entry, DR-25.
  - OQ15: accept the unbounded map in the in-process test adapter. The condition is a convention, not enforced: that adapter is selected only by `sequeless.automation.adapter=inprocess` (no default), and repo contexts set it in tests only.
  - V10 frozen set: **no** copy (option B). The reply's reason ("line 128 explicitly lists the steps") was rejected: line 128 states a general rule, and the chain sentence lists plan order. The claim-index migration is decision (ix)'s follow-up to DR-23, not a P-step. If the owner reads "step" more widely, PR #204 would need a copy; it did not add one.
- Session `9f8f8d8bbc3247daa8d5b8ef1b246e22`:
  - OQ19: the O-4 loss gate stays non-dev only; the count is run before every P3 deploy (owner's 2026-11-25 rule; the owner approves the deploy).
  - OQ9: the fresh O-3 count covers every database the new version migrates, dev Postgres included.
- Earlier verified decisions still stand (their sessions are in the earlier list in this file's history and in the plan): `c0e84f0a157f4cc1a0f5dc3f54952ce2` (P3 OQ1 to OQ4, OQ7, OQ8, OQ11 to OQ13, OQ17 (wording: putIfAbsent), OQ19-era items), `8af75131ba5d4b6dbf33036c37875d05` (V10 marker and lock, O-4 method, B2 route 1), `09480ec46e184c8aa8dc03436a07e826` (V9 numbering, Option B), `0739f198e75c403ab527d4ad6cc82164` (P4 OQ15: follow spec line 128), `cf09351ce0a144538f114de334e5d631`, `cc206f68705e4d93956f7e58ddd8c6e9`, `95d0ab97a06845088d993ce3caa0f05e`, and the P3 items `9a048a8e60cd40a6ac9c2b772425b441` (OQ18), `b24f8284114046c288fe52c7ee2efe56` (OQ17), `f97cbc9d2bbf4f56a9f515a84efb371e` (OQ10), `0a59730230a9406e876dfde5efda8d79` (OQ20), `20c28d32240040e78d7b5b05966cecb2` (OQ21), `712ced3373c1471296582ddd7f14d216` (chain start).

**P4 (plan section 8; the decisions block at the top of that section lists them):**
- Session `74165dcd1aef4042872e1eafbcae492b`:
  - OQ5: a state-less object's signal is rejected with 409 under C7. The first reply (202, no effect) conflicted with spec line 93 and was challenged; that reply's option is rejected.
  - OQ6: keep `existing.version()`; the existing `StaleObjectException` mapping (409 `stale-object`). The spec is silent on version races.
  - OQ7: **drop the signal body.** It is no longer recorded for audit. The owner may revisit this.
  - OQ8: an ontology reload during a signal is not mapped; `IllegalStateException` surfaces as HTTP 500 (Spring's default; no repo setting). The owner may prefer a 409 or 503 mapping later.
  - OQ9: several transitions matching one name: the first in transition order is taken; a failing guard is rejected with 409 under C7.
- Session `b1d038683633493cb43ea6656ffdd217`:
  - OQ11: the P4 migration stamps dead-lettered `SignalReceived` rows as processed, as V8 does for `ActionRequest`.
  - OQ12: once P4 lands, `awaitState` in `AutomationEndToEndTest.reopenSignalMovesAClosedProjectBackToActive` becomes a direct state assertion after the 202.
  - OQ14: P4's decision record is DR-26, a new entry at merge.
  - OQ17: change `object-signals.ts` line 47 only, to `the transition is applied when the request is accepted`. `signals-panel.ts` line 61 keeps `takes effect asynchronously` after P4. The same reasoning the spec gives for object-signals.ts line 47 applies to it, but the spec does not name it. The P4 PR may change it as a follow-up.
- Owner decision: OQ16 (dev-stack adapter) is temporal (owner action 3, 2026-11-25).
- Rejected claims recorded in those sessions (not relied on): OQ5's first reply; OQ17's quotation of spec line 93 as "takes effect synchronously" (the spec says "the transition is applied when the request is accepted"); OQ8's first reply (it answered OQ5 again); OQ11's first reply (a plan step with no option).

Navigator sessions not to reuse: `74165dcd1aef4042872e1eafbcae492b`, `b1d038683633493cb43ea6656ffdd217`, `9f8f8d8bbc3247daa8d5b8ef1b246e22`, `5391f7cc4940429283819538a8396c37`, and every session listed in earlier handoffs.

## Owner decisions (2026-11-25, the owner's own answers; do not re-ask)

- **#41:** reopened by the owner (2026-11-25). Spec 13 is not finished.
- **V9 rename:** the owner accepted Navigator Option B: P3 takes V9, and the claim index is V10 (PR #204, now merged).
- **Dev-stack adapter (owner action 3):** temporal.
- **Claim-index route (owner action 2):** the non-transactional route, with the Flyway marker.
- **Re-count before any deploy (owner action 4):** a live Temporal count of running `ActionWorkflow` and `TimerWorkflow` executions for P3 (for P4, `SignalEventWorkflow`, spec line 104), plus the `sq_outbox` unprocessed count for every kind. Orchestration runs the count. The owner approves each deploy.
- **Owner action 1, written loss acceptance (Q-A), recorded verbatim:** "I accept", given 2026-11-25.

## Owner actions open

1. **Deploy approval for P3, then for P4.** Before each deploy: the O-3 count (`sq_outbox` unprocessed, every kind, on every database the new version migrates, dev included, per the Navigator's OQ9); and the live Temporal count of running workflows of the types the step changes. For P3 that is `ActionWorkflow` and `TimerWorkflow`; for P4 it is `SignalEventWorkflow` (spec line 104). The owner approves. The O-4 loss gate is non-dev only (`spec-13-p3-plan.md` OQ19). If loss is accepted for running executions, terminate the listed ones before the new version starts (spec step 4, line 105). V9 marks timer rows processed, so P3's new version must not start while any `TimerScheduled` or `TimerCancelled` row is unprocessed.
2. **Delete the superseded remote branches** `spec-13-dead-letter-index-v8` and `-v9` (destructive; the owner confirms), and the merged branches listed under State on the owner's go-ahead.
3. **Two Navigator decisions with consequences the owner may want to change:** OQ7 (the signal body is no longer recorded) and OQ8 (an ontology reload during a signal returns HTTP 500). These were decided by the Navigator, not the owner; they are noted here so the owner can choose differently.

Nothing else is open with the owner. Escalate only for actions on the owner's machines, credentials, or major releases.

## Next actions

The owner's rule: code first, one handoff PR at the end of a cycle, no mid-cycle docs PRs.

1. **P4 code** (blocked on nothing now: the P3 prerequisite is merged, and the P4 questions are decided). Start from `origin/main` (`a7089bb` or later) in a worktree. Sequential commits on one branch, each reviewed by a separate review subagent against the plan, then one full gate and one PR. The units, in order:
   - **a. Core:** `TransitionService.signal` runs the matching transition in one `ChangeSet`, synchronously (spec line 93). The transition lookup moves from `DefaultTriggerEvaluator.onSignal` (about lines 162-194 on main) into the signal path of `DefaultTransitionService`. C7 (spec line 93, R4 at line 461): no transition from the current state (including a state-less object, OQ5) and a failing guard are 409 through `TransitionNotAvailableException` (the handler is `ApiExceptionAdvice` about line 204). An unknown name stays 400 (`UnknownSignalException`). First match in transition order (OQ9). The signal body is dropped (OQ7): the request body is accepted and not stored.
   - **b. Kinds:** remove `KIND_SIGNAL_RECEIVED` from `OutboxEntry`; `OutboxRelay` drops it from `DISPATCHABLE_KINDS`; the `OutboxEntryTest` kind-set assertion goes from five to four constants (spec P4 row: "Kind-set test: 4 constants"); the `AutomationPort` javadoc and `package-info` comments are reworded.
   - **c. Temporal:** remove `SignalEventWorkflow` and its implementation, its registration in `TemporalAutomationAutoConfiguration`, and the `SignalReceived` case in `TemporalAutomationPort`. Removing a workflow type with running executions is gated by O-4 (spec line 334): running `SignalEventWorkflow` executions outside a dev volume must drain before the deploy. The in-process adapter's `SignalReceived` case goes too (`InProcessAutomationPort.java`, about line 72 on main).
   - **d. Persistence:** V11 migration: `UPDATE sq_outbox SET processed_at = now() WHERE kind = 'SignalReceived' AND processed_at IS NULL;` with a header that names the drain precondition and DR-26 (OQ11 includes dead-lettered rows). `PostgresOutboxV11MigrationIT` modelled on `PostgresOutboxV9MigrationIT`. The settling test gets `11 SUCCESS` in its lists. The post-P4 frozen set (`frozen-migrations/post-p4/command`: V1, V3 to V11, byte-identical to live) and the pair from `post-p3` to `post-p4` (spec line 128). `PostgresMigrationFrozenSetsTest` learns post-p4.
   - **e. REST:** `ApiExceptionAdvice` unchanged (OQ8; an unmapped `IllegalStateException` is HTTP 500). In `TransitionService.java`, the `signal` javadoc is wrong beyond lines 110-111: lines 89-103 say the signal is recorded as `SignalReceived`, fires no transition, and is checked type-wide rather than against the object's current state (which contradicts C7). Reword that whole block. In `ObjectsController.java` (about lines 478-490), reword the `@Operation` summary ("Record an external signal ...; does not itself fire any transition"), the `@ApiResponse` 202 text ("any resulting transition fires asynchronously"), and the `body` javadoc ("recorded alongside the signal for audit"). Declare a 409 `@ApiResponse` for the rejection case. The 202 for an accepted signal stays (O-5).
   - **f. Tests:** `TransitionsEndToEndTest.reopenSignalOnClosedProjectIsAccepted` keeps its 202 and gains a state assertion; `AutomationEndToEndTest.reopenSignalMovesAClosedProjectBackToActive` replaces `awaitState` (line 283 on main) with a direct assertion (OQ12); add 409 cases for a signal with no transition and with a failing guard; `DefaultTransitionServiceTest` signal cases (the P4 row lists them).
   - **g. UI:** `object-signals.ts` line 47 changes to `Signal "{{ name }}" dispatched — the transition is applied when the request is accepted.` (OQ17). `signals-panel.ts` line 61 and its spec line 111 are left as they are (OQ17 consequence). The comments "Signals are handled asynchronously (202)" at `object-signals.ts` line 18 and `signals-panel.ts` line 22 are reworded to match the synchronous path, or left on purpose: decide in the PR and say which. `automation-signals.spec.ts`: the rejected-signal case (the 409 banner) per the P4 row.
   - **h. DR-26:** a new `### DR-26` entry after DR-25 (OQ14): the removal of the `SignalReceived` kind, the synchronous signal, and the body drop (OQ7).
   - **Gate:** `mvn spotless:apply`, then the full root `mvn -B -Prelease verify` on JDK 25 (command in the resume plan, section 21.6). If the UI changes, also `ng test` and the Playwright e2e in `sequeless-ui` (spec section 9). Record the results in the PR body.
   - **Mixed versions at deploy:** the order in spec lines 100-108 applies: stop writes, stop old processes, run O-3 and the Temporal count; if loss is accepted for running executions, terminate the listed ones (spec step 4, line 105); then start the new version, which runs V11.
2. **P5b** (the Store-event ingress): after P4 merges (resume plan section 20, step 6). It needs its own read-only plan review first. Its code waits for P4.
3. **Deploys** (P3, then P4): owner approval and the counts in owner action 1. No deploy is automatic.

## Units this cycle (tried and status)

- **Replay test before the change** (#208, `0fe5180`): landed. Replay of two recorded histories; an independent review approved it.
- **Timer lookup and carrier, additive** (#209, `3ce0902`): landed. an independent review approved after one should-fix (carrier test) and one nit (message assertion), both fixed.
- **P3 cut-over** (#210, `a722a7f`): landed. About ten commits on the branch (core writes no timer rows; in-process adapter; testkit contracts; Temporal adapter with `getVersion`; kinds removed; V9 and the post-P3 set; end-to-end wait, DR-25, docs; POM dependency fixes). an independent review covered each unit, and a whole-branch review approved after the V9 header fix (the squash-merge hash in the comment was replaced).
- **Claim index** (#204, `a7089bb`): landed after V9, merged with main. The settling test kept both `9 SUCCESS` and `10 SUCCESS`.
- **P4:** not started in code. All its Navigator questions the code depends on are decided (above).
- **P5b:** not started (waits on P4).

## Drift to check against GitHub

- **P4 plan line numbers are stale** (its line 10, 34 and 171 are stale per the P4 plan's own notes). Cite origin/main with `git grep -n`.
- **Spec line 128** is the settling rule; P4 adds the pair from post-P3 to the final set (the starting point question in the P4 plan is settled by that text).
- **The P3 plan's OQ19 line** now carries the 2026-11-25 rule (this PR).
- **Several of the Navigator's P4 replies** misquoted or misread the spec, or answered another question: the OQ17 quotation of spec line 93, and the first replies to OQ5, OQ8 and OQ11. Each is recorded above as a rejected claim or a challenge, and none is relied on.
- **The dependency analyser (maven-dependency-plugin)** runs in `verify`, not in the `test` goal: a module that imports `kernel-values` or `kernel-identity` without declaring it fails the root gate. Units that only ran `test` did not catch it; the full gate did. Check every module the P4 units touch.

## Hazards

- **Never use `--admin`** to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- **Never put a GitHub closing keyword** in front of issue #41 in a PR body or commit. Use `Refs #41`.
- **Use `gh pr merge --match-head-commit` with the full 40-character SHA.**
- **Merge `origin/main` into a branch; do not rebase.** When main has a squash of a branch's commits, the merge can conflict on the duplicated commits. Resolve by taking the branch's side only after checking that the resulting tree equals the gated commit (`git diff <gated-sha> -- .` must be empty); this is how the P3 cut-over and the claim-index branch were brought up to date.
- **Partial Maven runs give false failures.** Running only some ITs with `-Dit.test` (or `-Dfailsafe.failIfNoSpecifiedTests=false`) produced a `FileSystemNotFoundException` in `PostgresMigrationFrozenSetsTest`. Merge only on the full gate.
- **The dependency analyser runs in `verify`.** Declare every kernel artifact a module's main code imports.
- **Flyway order:** V10 (claim index) is on main after V9. A new version must be above V10. Never insert a lower version after a higher one has been applied.
- **Do not change a V9 file's bytes after merge** unless the frozen post-P3 copy changes with it; the checksum and the byte-identity test both depend on it.
- **Do not merge the superseded `spec-13-dead-letter-index-v9` branch** (its V9 collides with main's).
- **V8 and V9 stamp dead-lettered rows of their kind.** P4's V11 does the same for `SignalReceived` (OQ11).
- **The advisory-lock setting** `flyway.postgresql.transactional.lock=false` is required for V10 (`CREATE INDEX CONCURRENTLY`). Do not revert it.
- **Partial deploy risk:** an old relay that still claims `SignalReceived` and dispatches to a new adapter reaches the `default` branch and throws. The mixed-version order (spec lines 100-108) prevents it; do not skip the drain.
- **Renovate moves main during a session.** Re-check `origin/main` before a merge.
- **Work from a worktree cut from `origin/main`.** The primary checkout may be behind.
- **Push with an explicit refspec** (`git push -u origin HEAD:refs/heads/<branch>`), so a branch created from `origin/main` can never update `main`.
- **Maven needs the GitHub Packages token**, so source `~/.bashrc.ext` in the same command as every `mvn` run. The gate uses JDK 25 (`/opt/sdkman/candidates/java/25.0.3-tem`); the default `java` is 21.
- **Do not put model names** in commits, PR titles or bodies, or any other pushed artifact. This applies to this handoff too.
- **Machine-local facts** (paths, tokens, environment IDs) stay out of the repo.

## Loop: how the orchestration runs

- **Orchestrator:** the session that creates the children, and the target of their state reports. The current orchestrator is `session_01VYniu2Woriiut8U9HzyZrf`. A new run uses the ID its parent gives it.
- **Child rules** (from the parent): code first; one handoff PR at the end of a cycle, no mid-cycle docs PRs; write `LIMIT` only when the run cannot continue; write `ALL DONE` only after a code commit is on `origin/main` during the cycle, and only when no work item remains; report the code units tried, what blocks each, and whether code is on main.
- **Owner questions:** route every design, route, migration-number and count question to the Navigator. Ask the owner only for actions on the owner's machines, credentials, and major releases.
- **Prompt file:** `prompt.md` is git-ignored on purpose. It exists on the machine where the loop was started, and children read it from its absolute path.
- **Cost:** each child uses the owner's plan usage window. The owner can stop the loop at any time.
