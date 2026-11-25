# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-25, at origin/main `22db676` (PR #214, the P5b Store-event ingress, squash of `1b8ddd8`). The docs PR that carries this handoff lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

## Start a new session

1. Read this file, then `docs/extraction/spec-13-p5b-plan.md` (its section 8 holds the five P5b Navigator decisions and the decisions block) and `docs/extraction/remaining-work.md`.
2. Check GitHub: `origin/main`; open PRs (only #106, a draft that must not merge, and this handoff PR); issue #41 (OPEN).
3. Spec 13 code is on main for every step: P0, P1, P2a, P2, P3, P4, P5a, P5b, P6a, P6b and P7. Nothing is blocked on code. Follow "Next actions".
4. Navigator decisions are recorded (see "Navigator decisions"). Do not re-ask them. A new question goes to a fresh session. Start a pair session with `navigator_start(mode="pair")`; the first question of a session uses `answer_tool="set_plan"`, and every later one uses `answer_tool="decide"`. One `navigator_ask` per question, at most five per batch.
5. Before any PR or merge, follow "Hazards".

## Read first

- Spec 13 parent (the settling rule at line 128; the Store-event ingress at line 94): [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md)
- P5b plan (decisions block at the top of section 8): [docs/extraction/spec-13-p5b-plan.md](../extraction/spec-13-p5b-plan.md)
- P4 plan (section 8 decisions; historical now): [docs/extraction/spec-13-p4-plan.md](../extraction/spec-13-p4-plan.md)
- Resume plan (section 21.6 is the gate command): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Decision records: [DR-23](../architecture/02-decision-records.md) (dead-letter), [DR-24](../architecture/02-decision-records.md) (actions on the TransitionFired row), [DR-25](../architecture/02-decision-records.md) (timers from the TransitionFired row), [DR-26](../architecture/02-decision-records.md) (signals run in the request), [DR-27](../architecture/02-decision-records.md) (the Store-event ingress)
- Automation architecture: [docs/architecture/automation.md](../architecture/automation.md)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, OPEN), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Main:** origin/main `22db676` (#214). This cycle's code on main: #214 only, squashed from seven commits: the plan (`eb0d439`), the Temporal change-event workflow calls `onChange` only (`dca759b`), the `StoreEvent` SPI types (`fb45f6f`), the cut-over (`79cc0b1`), the architecture rule (`4d89c5e`), the signal panel wording (`2a6ecfd`), and the docs with DR-27 (`1b8ddd8`).
- **What P5b changed:** `AutomationPort.dispatch(Scope, OutboxEntry)` is now `ingest(Scope, StoreEvent)`. `StoreEvent` is a sealed interface over `ObjectChangedEvent` (with `Change` CREATED/UPDATED/DELETED) and `TransitionFiredEvent`. The relay maps each row with `StoreEvents.from`. The recompute still runs in the relay before the ingress for lifecycle rows (P5a). Both adapters match on the sealed type with no default. The Temporal `ChangeEventWorkflow` runs `onChange` only; its recompute activity is removed (moved from P5a).
- **No migration in P5b.** Latest migration on main is V11 (P4, signals drained). Next free migration: **V12**.
- **Decision records on main:** DR-20 to DR-27. Next free: **DR-28**.
- **Deploys: none of P3, P4 or P5b is deployed.** Each needs the owner's approval (see "Owner actions").
- **Checks on #214:** full root gate on the code head `2a6ecfd`, JDK 25: `BUILD SUCCESS` (per the session log). CI on `1b8ddd8`: build/maven, CodeQL (three languages), ui, and SonarCloud all green, mergeStateStatus CLEAN, merged by squash pinned to the full head SHA. The ng unit spec and the Playwright e2e were not run locally (no `node_modules` in the worktree); the CI `ui` job ran the Angular tests.
- **Open PRs:** #106 (draft, do not merge). This handoff PR. Nothing else.
- **Issue #41:** OPEN. The owner decides when spec 13 is finished. Specs 14 and 15 wait on it.

## Navigator decisions

Decisions below were recorded with `navigator_record` in the sessions named. A reply that is only a plan step or a carry-forward line is not a decision until recorded.

**P5b (session `06f6998baaef4a1293463bad7fa0fb07`, recorded in the P5b plan section 8 and DR-27):**
- OQ1 (a): one ingress, `ingest(Scope, StoreEvent)`, with a sealed `StoreEvent` and an exhaustive switch in each adapter.
- OQ2 (a): the event carries the outbox row's id, payload and `occurredAt`, unchanged.
- OQ3 (a): `ActionExecutor`, `TransitionActionWorkflow` and `ActionActivities` keep `OutboxEntry`; the adapters rebuild it from the event. **This departs from the plan's own recommendation (b)** and from the spec 5.2 table. The O-4 list is only `ChangeEventWorkflow`.
- OQ4 (a): `onTimerElapsed` and `TimerWorkflow` keep `OutboxEntry`. **This departs from spec line 94**: an elapsed timer is not a Store event (GLOSSARY lines 77-82).
- OQ5 (a): a new decision record, DR-27, which records both departures.

**UI (session `c7d77f8543ab4dc390045b4804a054fe`):** the signal panel's status text changed to the `object-signals` wording (`signals-panel.ts`, its spec line 111, the header comment at line 8). Landed in #214.

**P4, still standing (session `74165dcd1aef4042872e1eafbcae492b`):** OQ5 (a signal on a state-less object is 409), OQ6 (keep `existing.version()`, `stale-object` 409), OQ7 (**the signal body is no longer recorded**), OQ8 (**an ontology reload during a signal is not mapped; HTTP 500**), OQ9 (first match in transition order; a failing guard is 409). OQ7 and OQ8 are **the Navigator's decisions, not the owner's**. The owner may choose otherwise; they are listed under Owner actions so the owner can.

**P3 and P4 secondary decisions** are recorded in the plans on main, not here: `docs/extraction/spec-13-p3-plan.md` (its `Decided:` lines, for example the O-4 count rule at line 224, and section 8) and `docs/extraction/spec-13-p4-plan.md` (section 8). Their Navigator sessions are in those lines.

Navigator sessions not to reuse: `06f6998baaef4a1293463bad7fa0fb07` (P5b, ended), `c7d77f8543ab4dc390045b4804a054fe` (UI, ended), `74165dcd1aef4042872e1eafbcae492b`, `b1d038683633493cb43ea6656ffdd217`, `9f8f8d8bbc3247daa8d5b8ef1b246e22`, `5391f7cc4940429283819538a8396c37`, and every session listed in earlier handoffs.

## Owner decisions (2026-11-25, the owner's own answers; do not re-ask)

- **#41:** reopened by the owner (2026-11-25). Spec 13 is not finished.
- **V9 rename:** Navigator Option B: P3 takes V9, the claim index is V10.
- **Dev-stack adapter:** temporal.
- **Claim-index route:** the non-transactional route, with the Flyway marker.
- **Re-count before any deploy:** a live Temporal count of running executions of the types the step changes, plus the `sq_outbox` unprocessed count for every kind, run by orchestration; the owner approves each deploy.
- **Loss acceptance (Q-A), verbatim:** "I accept", given 2026-11-25.

## Owner actions open

1. **Deploy approval, one per step, in order: P3 (#210), P4 (#212), P5b (#214).** Follow the steps in spec 13 section 3, "Mixed versions at P2" (spec lines 100-108) exactly; the reasons follow the list in that section:
   1. Stop writes.
   2. Stop every process that runs the old version: its relays, its timer sweep and its Temporal workers.
   3. After the stop, run and record: the O-3 count (`sq_outbox` unprocessed rows, every claimed kind, on every database the version migrates, dev included, per the P3 plan's OQ9); and the live Temporal count of running executions of the types the step changes. P3: `TimerWorkflow` (`ActionWorkflow` went in P2e). P4: `SignalEventWorkflow`, which P4 removes. P5b: `ChangeEventWorkflow`, the only workflow type on its O-4 list.
   4. If either count is nonzero, restart the old version with writes still stopped, let it drain, and repeat steps 2 and 3. Where the owner has accepted loss for running executions (Q-A, "I accept", 2026-11-25), terminate the listed executions before step 5 (spec step 4, line 105). A running workflow counts only outside a dev volume (spec line 105). At P3 the O-4 list is recorded but gates only non-dev environments, and running `TimerWorkflow` executions are neither drained nor terminated, because `Workflow.getVersion` guards them (P3 plan lines 151 and 158).
   5. Start the new version. It runs the migrations not yet applied, in version order: P3 runs V9; P4 runs V10 (`V10__outbox_dead_letter_index`, from #204, which rebuilds the claim index with `CREATE INDEX CONCURRENTLY`) and then V11; P5b runs none.
   The owner approves each deploy.
2. **Delete superseded and merged remote branches (destructive; the owner confirms):** `spec-13-dead-letter-index-v8` and `spec-13-dead-letter-index-v9` are superseded (the V9 on `-v9` collides with main's V9). The other spec-13 and docs branches still on the remote have merged PRs. List them with `git ls-remote --heads origin`, and check each one's PR with `gh pr list --state merged --head <branch>` before the owner's go-ahead. Do not delete anything without that.
3. **Navigator decisions for the owner's awareness:** OQ7 (signal body no longer recorded) and OQ8 (ontology reload during a signal returns 500); OQ3(a) and OQ4(a) (the departures from spec line 94 and the spec 5.2 table). The owner may choose otherwise.

Nothing else is open with the owner. Escalate only for actions on the owner's machines, credentials, or major releases.

## Next actions

1. **Nothing is blocked on code.** Optional cosmetic follow-ups, one small code PR if the owner wants them (a code commit counts toward a cycle; a docs commit does not):
   - `InProcessAutomationPort` field `dispatched` (about lines 94 and 99) could be `ingested`.
   - The Temporal `package-info` key wording: `<entryId>:<index>` could read `<eventId>:<index>` (same value).
   - Several adapter test names still say `dispatch`/`redispatch` (for example in `InProcessAutomationPortTransitionFiredTest` and the Temporal contract test).
   - `RecordingTriggerEvaluator.Call.sequenceNanos()` has no consumer; it is a public testkit record component and is kept.
   - `docs/architecture/automation.md` and `01-overview.md` keep the generic word "dispatch" for the relay's handoff, on purpose.
2. Spec 14 and 15 wait on #41. Do not start them without the owner closing #41.
3. Deploys are the owner's (Owner actions 1).

## Units this cycle (tried and status)

- **P5b plan** (`eb0d439`): written by a subagent, reviewed read-only by a second subagent (approved with nits; the nits were applied), five Navigator questions settled (see above).
- **Temporal change-event workflow calls `onChange` only** (`dca759b`): landed in #214. The recompute activity and its wiring removed. The parent read the diff; no separate review was run on this commit (the cut-over review covers the same files).
- **`StoreEvent` SPI types** (`fb45f6f`): landed in #214; unit tests pass.
- **Cut-over** (`79cc0b1`): landed in #214. Reviewed read-only by a subagent: approved with nits; the relay wording and the contract test names were fixed. The unit suite passed after a fixture fix (`StoreEventsTest`).
- **Architecture rule** (`4d89c5e`): landed in #214. `onlyTheRelayFeedsAutomation` and `theRelayFeedsAutomation`. The testkit exclusion is a deviation from spec line 207, recorded in the commit and the PR body.
- **Signal panel wording** (`2a6ecfd`): landed in #214.
- **Docs and DR-27** (`1b8ddd8`): landed in #214. The reversibility rating was changed from "high" to "moderate" to match DR-23 to DR-26.

## Drift to check against GitHub

- The P5b plan's section 2 cites resume plan "section 6" for the P5b steps; they are in section 4 (lines 58 and 60). The plan has the corrected citations.
- The P5b plan and spec 13 line 94 disagree on the timer input (OQ4). DR-27 records the departure, and the spec line is unchanged.
- The spec 5.2 table (line 167) does not list `ActionExecutor` for P5b; the departure is recorded in DR-27 (OQ3).
- The spec's stale line numbers are listed in the P5b plan section 2.

## Hazards

- **Never use `--admin`** to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- **Never put a GitHub closing keyword** in front of issue #41 in a PR body or commit. Use `Refs #41`.
- **Use `gh pr merge --match-head-commit` with the full 40-character SHA.**
- **Merge `origin/main` into a branch; do not rebase.**
- **Partial Maven runs give false failures.** Merge only on the full gate. Unit-only runs (`-Dtest=...` with `-Dsurefire.failIfNoSpecifiedTests=false`) are fine for a single module's check, not as a gate.
- **The dependency analyser runs in `verify`.** Declare every kernel artifact a module's main code imports.
- **Flyway order:** the next free version is V12. Never insert a lower version after a higher one has been applied.
- **The advisory-lock setting** `flyway.postgresql.transactional.lock=false` is required for V10 (`CREATE INDEX CONCURRENTLY`). Do not revert it.
- **`ChangeEventWorkflow`'s input type changed (P5b).** An old worker that polls the same Temporal task queue could fail to decode a new-shaped task. The stop-then-start order (spec lines 100-108) prevents that. Do not start a new worker while an old one runs.
- **`TimerWorkflow` and `TransitionActionWorkflow` keep their input types** (OQ3(a), OQ4(a)). Do not change them without recounting.
- **V9 (P3) and V11 (P4) stamp rows as processed.** The drain precondition and the O-3 count gate each deploy.
- **Work from a worktree cut from `origin/main`.** The primary checkout may be behind.
- **Push with an explicit refspec** (`git push -u origin HEAD:refs/heads/<branch>`), so a branch can never update `main`.
- **Maven needs the GitHub Packages token**, so source `~/.bashrc.ext` in the same command as every `mvn` run. The gate uses JDK 25 (`/opt/sdkman/candidates/java/25.0.3-tem`); the default `java` is 21.
- **The ng unit spec needs `node_modules`.** A fresh worktree has none; the CI `ui` job runs it.
- **Renovate moves main during a session.** Re-check `origin/main` before a merge.
- **Do not put model names** in commits, PR titles or bodies, or any other pushed artifact. This applies to this handoff too.
- **Machine-local facts** (paths, tokens, environment IDs) stay out of the repo.

## Loop: how the orchestration runs

- **Orchestrator:** the session that creates the children, and the target of their state reports. The current orchestrator is `session_01VYniu2Woriiut8U9HzyZrf`. A new run uses the ID its parent gives it.
- **Child rules** (from the parent): code first; one handoff PR at the end of a cycle, no mid-cycle docs PRs; write `LIMIT` only when the run cannot continue; write `ALL DONE` only after a code commit is on `origin/main` during the cycle, and only when no work item remains; report the code units tried, what blocks each, and whether code is on main.
- **Owner questions:** route every design, route, migration-number and count question to the Navigator. Ask the owner only for actions on the owner's machines, credentials, and major releases.
- **Prompt file:** `prompt.md` is git-ignored on purpose. It exists on the machine where the loop was started, and children read it from its absolute path.
- **Cost:** each child uses the owner's plan usage window. The owner can stop the loop at any time.
