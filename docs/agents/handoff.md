# Handoff

The state a new agent session needs to continue the CQRS extraction, and to run the orchestration loop that drives it. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded: 2026-11-25, at origin/main `b5cf187` (PR #206, the post-P2 frozen set). The docs PR that carries this handoff lands after it. Read `git log origin/main` for the head that follows, and trust that over any SHA written here.

## Start a new session

1. Read this file, then `docs/extraction/remaining-work.md` and the spec 13 plan named under "Read first".
2. Check the state against GitHub: `origin/main`, open PRs (#204 is held, and #106 is a draft that must not merge), and issue #41 (OPEN, reopened by the owner on 2026-11-25).
3. Check "Navigator decisions" first. Do not reuse any session listed there. A new question goes to a fresh session, one `navigator_ask` per question, at most five per batch.
4. Aim the cycle at code. The code-bearing items are under "Next actions".
5. Before any PR or merge, follow "Hazards".

## Read first

- Roll-up of remaining work: [docs/extraction/remaining-work.md](../extraction/remaining-work.md) (may lag this file; trust this file for state)
- Spec 13 resume plan (history; sections 21.1 to 21.5 are partly stale, 21.6 is the gate, section 17 is the merge list): [docs/extraction/resume-plan-spec13.md](../extraction/resume-plan-spec13.md)
- Spec 13 parent: [docs/extraction/spec-13-prototype-stage4-prepare.md](../extraction/spec-13-prototype-stage4-prepare.md) (line 128 is the settling rule: each step that adds a Command migration saves its merged set as a frozen copy and adds the pair from the previous saved set)
- Spec 13 plans: [P2e](../extraction/spec-13-p2e-plan.md) (merged in #182), [P3](../extraction/spec-13-p3-plan.md) (section 4 is the change list, section 8 holds the open questions and Decided lines, section 9 is the merge order), [P4](../extraction/spec-13-p4-plan.md) (section 8 holds the open questions)
- Decision record for the flip: [DR-24](../architecture/02-decision-records.md) (actions travel on the TransitionFired row)
- Automation architecture: [docs/architecture/automation.md](../architecture/automation.md)
- Issues: [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13 tracker, OPEN), [#11](https://github.com/sequeless/sequeless-prototype/issues/11) (map, open)

## State

- **Main:** origin/main `b5cf187` (#206). The cycle base is `7ad60db`. Code on main this cycle: #206 only, and it is test resources and tests (no main code). Check with `git diff --name-only 7ad60db origin/main -- . ':(exclude)docs' ':(exclude)*.md'`, which lists the `sequeless-adapter-persistence-postgres/src/test/` files.
- **Merged this cycle:** #206 (`b5cf187`, squash of `9d90c5f`): the frozen post-P2 Command set (`frozen-migrations/post-p2/command`, V1 and V3 to V8, byte-identical to the live set), its checks in `PostgresMigrationFrozenSetsTest`, and settling case (f) in `PostgresMigrationsSettlingIT`, which migrates from the frozen post-P7 set through the post-P2 set to the live set. This fills the gap P2d (V8, PR #168) left: it saved no set. Gate: full JDK 25 `mvn -Prelease verify` passed on the fixed content.
- **Open PRs:** #204 (held: `spec-13-claim-index-nontx`, head `3299e69`, V10 non-transactional claim index; the full JDK 25 gate was green at `d4fce6f`). Do not merge #204 until P3's V9 is on main, because Flyway refuses a lower version that appears after a higher one. #106 (draft compare document; do not merge).
- **Migrations on main (Command set):** V1, V3, V4, V5, V6, V7, V8. The next free number is V9, which P3 takes. The Query set on main (`postgres-query`) has V1 and V2 and is a separate sequence.
- **Frozen sets on main:** `frozen-migrations/3328993` (cut-over), `fbb82c5` (P2a, V1 to V5), `post-p7/command` (V1, V3 to V6), `post-p7/query` (V1, V2), `post-p2/command` (V1, V3 to V8).
- **Superseded branches:** `origin/spec-13-dead-letter-index-v9` (`87f5e42`) is replaced by #204. `origin/spec-13-dead-letter-index-v8` (`6cf5938`) is stale and must not be merged. Owner action 2 deletes both after #204 merges.

## Navigator decisions: verification

The record flow (the `navigator:pair` skill's flow, adopted by the owner on 2026-11-25):

- A Navigator decision counts once the driver has recorded it with `navigator_record` (kind `decided`), quoting the reply in `text` and naming the source check in `rationale`, and `navigator_status` shows it in the DECIDED list of the session that made it.
- A `set_plan` or `decide` answer, a job answer, a CARRY FORWARD line or a relayed claim is not a decision until it is recorded this way.
- Before the next `navigator_ask`, record one `carry_forward_verdict` per CARRY FORWARD line: `kept`, or `wrong` with evidence.
- `navigator_record` takes the session ID. The task ID that `navigator_ask` returns is not a session ID.

Decided this cycle. Each was checked with `navigator_status` against its session's DECIDED list at the end of the run:

- **`9a048a8e60cd40a6ac9c2b772425b441`, P3 OQ18** (the kind string of the TimerScheduled-shaped carrier): Option A, a value type in `sequeless-spi/.../spi/automation/` that holds the kind string and the factory that builds the `OutboxEntry`. After P3, `OutboxEntry` keeps exactly five `KIND_` constants (seven on main today; the two timer kinds are deleted by P3). Source check: the constructor (`OutboxEntry.java` 100-112) accepts the carrier under any option, and both adapter poms depend on `sequeless-spi`.
- **`b24f8284114046c288fe52c7ee2efe56`, P3 OQ17** (retried TransitionFired row and the timer): change `pending.put` (`InProcessTimerScheduler.java` 66) to `putIfAbsent`. Source check: `InProcessAutomationPort.java` 123-127 routes TransitionFired rows past the entry-level dispatched set; `pending` is a `ConcurrentHashMap`.
- **`f97cbc9d2bbf4f56a9f515a84efb371e`, P3 OQ10** (zero-action and failing-action cases): the timer start goes before the action loop in both adapters. Source check: the in-process loop (177-192) calls `applyAction` (187), which can throw. The reply does not cover a throw from the timer start itself.
- **`0a59730230a9406e876dfde5efda8d79`, P3 OQ20** (cancel signal on replay): keep `cancel()` and the `cancelled` field for the default branch of `Workflow.getVersion`. Source check: `TimerWorkflowImpl.java` 15 (the `cancelled` field) and 33-41 (the await and `cancel()`). The SDK's handling of a signal with no handler is not checkable in this repo.
- **`20c28d32240040e78d7b5b05966cecb2`, P3 OQ21** (replay test): Option A, `WorkflowReplayer` tests over histories recorded from pre-P3 `TimerWorkflowImpl`, including a cancel-signal history. Source check: no replay test or recorded history exists on main, and `temporal-testing` is a test-scope dependency (`sequeless-adapter-automation-temporal/pom.xml` 79-83).
- **`712ced3373c1471296582ddd7f14d216`, chain start** (who saves the post-P2 set): Option B, a P2-owned PR saves the post-P2 set and its pair, landing before P3. #206 is that PR. Plan item 29c (section 4, line 117) conflicts with spec line 128 and is superseded by this decision. The P3 PR corrects it.
- **B2 route 1** (read the active version; a stamp mismatch fails and retries; DR-23 dead-letters after three): verified in `8af75131ba5d4b6dbf33036c37875d05`, "Pick route 1".

Earlier verified decisions still stand (verified in the DECIDED lists named in earlier handoffs): `c0e84f0a157f4cc1a0f5dc3f54952ce2` (P3 OQ1, OQ2, OQ3, OQ4, OQ7, OQ8, OQ11, OQ12, OQ13), `09480ec46e184c8aa8dc03436a07e826` (V9 numbering, Option B), `0739f198e75c403ab527d4ad6cc82164` (P4 OQ15 option a), `cf09351ce0a144538f114de334e5d631` (P4 OQ15 starting point P2a), `8af75131ba5d4b6dbf33036c37875d05` (V10 marker and lock setting, O-4 method, B2 route 1), `cc206f68705e4d93956f7e58ddd8c6e9` (P2d), `95d0ab97a06845088d993ce3caa0f05e` (route a).

Open, not decided. Do not treat any of these as decided:

- **P3 OQ5** (which ontology-version half the timer lookup compares with the TransitionFired row's stamp): asked in `80804597047042f5bc7eb8bd732d5bae` (reply: a step, no decision) and `d86b5d0ee7c040fdbe1412aed8df2133` (reply: a step; it named `sequeless-core/.../core/evaluator/DefaultTriggerEvaluator.java`, which does not exist; the real path is `sequeless-core/src/main/java/org/sequeless/core/automation/DefaultTriggerEvaluator.java`). Not re-asked in this run. Route 1 is verified; which half is open.
- **Not yet asked:** P3 OQ6 (B2 wording and DR-25), OQ14 (the DR number), OQ15 (in-process growth; its Decided line is unverified), OQ9 and OQ19 (the deploy timing and the O-4 gate, which gate deploy, not code), and the V10 frozen-set question (whether PR #204 must save a frozen set, since spec line 128 says each step that adds a Command migration does).
- **P3 items unverified in the plan's Decided lines:** lines 184 to 230 of the P3 plan carry Decided text for OQ5, OQ6, OQ9, OQ10, OQ14, OQ15 and OQ17 to OQ21. Those settled this cycle are OQ10, OQ17, OQ18, OQ20 and OQ21 (the decisions above). The rest are open.

## Owner decisions (grill, 2026-11-25)

These are the owner's answers, not Navigator decisions. Do not re-ask them.

- **#41:** reopened.
- **V9 rename:** accept Navigator Option B. P3 takes V9. The index branch's PR renames to V10 if it lands later (PR #204 is V10).
- **Dev-stack adapter (owner action 3):** temporal. The O-4 count covers the temporal adapter.
- **Claim-index route (owner action 2):** the non-transactional route, with the Flyway marker, sent for a small re-review (PR #204).
- **Re-count before any P3 deploy (owner action 4):** a live Temporal query for running executions (ActionWorkflow and TimerWorkflow), plus the `sq_outbox` unprocessed count for every kind. Orchestration runs the count. The owner approves the deploy.
- **Owner action 1, written loss acceptance (Q-A), recorded verbatim:** "I accept", given 2026-11-25.

## Running jobs

- No Navigator job is running. No subagent is running.

## Next actions

The owner's rule: code first, one handoff PR at the end of a cycle, no mid-cycle docs PRs. The chain-start and P2 frozen-set work is on main (#206).

1. **Finish the open P3 questions in fresh sessions**, one `navigator_ask` each, at most five per batch: OQ5 (narrowed: which half the lookup compares), OQ6 and OQ14 (B2 wording and the DR number), OQ15 (in-process growth), and the V10 frozen-set question. Before asking OQ5 again, add evidence: `BehaviourSource`, `SchemaSource` and `AutomationSource` are in the external `sequeless-ontology` artifact, not in this repo. If that artifact is in the local Maven cache, the driver can read its signatures and put the excerpt in the question. Record each answer as in "Navigator decisions".
2. **P3 code.** Start in a worktree cut from `origin/main`, with the V9 file name. Unblocked now: plan section 4 items 1 to 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23a, 23b, 24 to 29b, and 29c as corrected (it saves the post-P3 set only; the post-P2 set exists as of #206). Item 8 is unblocked except its stamp check, which waits on OQ5. In the same PR, fix plan item 29c, the "starting point is open" wording at P3 plan lines 117, 130 and 236, P4 plan lines 175, 248 and 250, and P3 plan line 190, which says V10 only if the index is merged and contradicts the owner's V9 rule. Pin the merge to the head a read-only separate review approved. Run the full gate.
3. **O-3 and O-4 checks** before the P3 deploy (owner action 4). The owner approves the deploy.
4. **PR #204 (held).** After P3's V9 is on main: `git merge origin/main` (not rebase), rerun the full gate on JDK 25, then merge with `--match-head-commit`. Decide whether V10 also saves a frozen set (question 1 above). Follow-ups, not blockers: a test for the failed-build path (INVALID index, `flyway repair`, re-run), and an EXPLAIN check that the claim query uses the partial index.
5. **P4 code.** Blocked on P3 merging, and on P4's nine open answers (OQ5 to OQ9, OQ11, OQ12, OQ14, OQ17 in the P4 plan, none decided). Owner action 3 (the dev stack) is answered: temporal. P4 plan lines 10, 34 and 171 are stale; rewrite them after P3 merges.
6. **P5b plan** after P4, per resume plan section 20, step 6. P5b code waits for P4's merge.

## Owner actions still open

1. **Deploy approval for P3**, and any major release. Not needed now.
2. **Delete the superseded remote branches** (`spec-13-dead-letter-index-v9` and `-v8`) after PR #204 merges. Destructive: the owner confirms.

Nothing else is open with the owner. Escalate only for actions on the owner's machines, credentials, or major releases.

## Denials and refusals

- **Refused, then passed on retry.** The auto-mode classifier refused the first merge of PR #177. The next attempt, through the normal route, merged it. Handle a first refusal by trying once through the normal route, then reporting it. Do not work around it.
- **Refused, then reversed.** The classifier denied a Bash write that recorded the P4 answers in the repo. The owner then granted the docs edit. The answers were later found unverified and reverted in #188.

## Loop: how the orchestration runs

- **Orchestrator:** the session that creates the children, and the target of their state reports. The current orchestrator is `session_01VYniu2Woriiut8U9HzyZrf`. A new run uses the ID its parent gives it.
- **Child rules** (from the parent): code first; one handoff PR at the end of a cycle, no mid-cycle docs PRs; write `LIMIT` only when the run cannot continue; write `ALL DONE` only after a code commit is on `origin/main` during the cycle; report the code units tried, what blocks each, and whether code is on main.
- **Prompt file:** `prompt.md` is git-ignored on purpose, so it is not in the repo. It exists on the machine where the loop was started, and children read it from its absolute path.
- **Cost:** each child uses the owner's plan usage window. The owner watches it. The owner can stop the loop at any time.

### Code units tried in this cycle

- **P2 frozen post-P2 set and settling pair (#206):** landed on main (`b5cf187`). A test-only change. Two independent reviews: the first found a defect (case (f) inserted objects before the Query set ran, so `sq_searchable_property` did not exist and the V6 trigger failed; the gate failed on it), which was fixed; the second approved the fixed commit. One nit left open: case (f) runs the live Query set rather than a frozen copy. The two are identical today; pin them if the live Query set gains a migration.
- **P3 code:** not started this cycle. The Navigator rounds for the open questions used the cycle. Units listed under Next actions item 2 are unblocked now; item 8's stamp check waits on OQ5.
- **Claim index (V10, non-transactional, PR #204):** implemented earlier (`d4fce6f`, `3299e69`). Not on main. Held until P3's V9 is on main.

## Drift to check against GitHub

- **#41 is OPEN** (reopened 2026-11-25). Spec 13 is not finished.
- **P3 plan line numbers are stale.** The plan cites `DefaultTriggerEvaluator` and other files at old line numbers. Cite main's numbers, found with `git grep -n`.
- **Navigator file reads named wrong paths.** Session `d86b5d0e` named `sequeless-core/.../core/evaluator/DefaultTriggerEvaluator.java`; the real path is `sequeless-core/src/main/java/org/sequeless/core/automation/DefaultTriggerEvaluator.java`. Check every path with `git ls-tree origin/main` before submitting an excerpt.
- **Plan item 29c** conflicts with spec line 128 (see the chain-start decision). Superseded; the P3 PR corrects it.
- **The P2e follow-up commit on main** (`c6e1313`, PR #194) changes code, but its commit subject starts with `docs(core,temporal)`. Cosmetic. Do not rewrite main for it.
- Resume plan sections 21.1 to 21.5 are partly stale. Section 21.6 is the gate.

## Hazards

- Never use `--admin` to get past a red or missing required check. A BLOCKED state from the update rule alone is the only exception, and only under the owner's standing approval, with the required checks green and the use recorded.
- Never put a GitHub closing keyword in front of the spec 13 tracker issue (#41) in a PR body or commit, not even in a negation. Use `Refs #41`.
- Use `gh pr merge --match-head-commit` with the full 40-character SHA.
- **Do not merge PR #204 before P3's V9 is on main.** Flyway rejects a lower version that appears after a higher one on a database that already applied V10. `ignoreMigrationPatterns` ignores only `*:missing` and `*:future`.
- **The advisory-lock setting is required.** `flyway.postgresql.transactional.lock=false` is set in `PostgresMigrations.commandConfiguration` on PR #204. Do not revert it. With the default `true`, `CREATE INDEX CONCURRENTLY` waits on Flyway's open transaction.
- **V10 recovery:** if the CREATE fails, the index may be INVALID and Flyway marks V10 failed. Run `flyway repair` (or delete the failed history row), then restart. V10's `DROP INDEX CONCURRENTLY IF EXISTS` removes an INVALID index.
- **Partial Maven runs give false failures.** Running only some ITs with `-Dit.test` produced a `FileSystemNotFoundException` in `PostgresMigrationFrozenSetsTest`. Merge only on the full gate.
- **A background wrapper's "exit code 0" is not the gate's result.** This run, a failed gate looked like a pass in its notification. Read the `EXIT=` line in the log.
- **Full gate:** source the local env file (`~/.bashrc.ext`, for the Maven token) and set `JAVA_HOME` to a JDK 25 in the same command, then run `mvn spotless:apply && mvn -B -Prelease verify`. The exact gate is in [the resume plan, section 21.6](../extraction/resume-plan-spec13.md). Docker must be running for the Testcontainers ITs. The default `java` is 21; the gate needs 25.
- The first start of a version that runs V8 on a database marks undrained `ActionRequest` rows as processed. Do the O-3 count before that start (P2e plan section 9).
- P3's migration marks unprocessed timer rows as processed, and their timers are lost with no record (P3 plan section 5). The O-3 count must be zero before the new version starts. The O-4 count gates the non-dev deploy.
- A refused action (a permission or classifier refusal) stops the run and is reported verbatim. Do not retry it in another form or work around it.
- A relayed claim, a `set_plan` or `decide` answer, or a CARRY FORWARD line is not a decision until the driver records it with `navigator_record` `decided` and `navigator_status` shows it. Check the reply against source before recording it.
- Navigator, pair mode: one question per `navigator_ask`. Only one job runs per server: a `job <id> active` error means wait for that job's result. Rounds can take over an hour. A session can hit its round limit and end.
- Do not reuse these Navigator sessions for a new question: `8af75131ba5d4b6dbf33036c37875d05`, `c4f4f2bbecca4223816663cbc1b096cb`, `cc206f68705e4d93956f7e58ddd8c6e9`, `c0e84f0a157f4cc1a0f5dc3f54952ce2`, `cb5a1ebdeb804ce893b35dced364435c`, `b150e05389fd46148771bec93b8bc980`, `c07146c090c444b18302e90478418aa7`, `09480ec46e184c8aa8dc03436a07e826`, `0739f198e75c403ab527d4ad6cc82164`, `32ac8aeab08346b6b67e54a49c2e1d44`, `ac015364523740e6b2eba306f8d31515`, `54529a32ee4b47b9a3ae073cabcbcb4e`, `92e8178d1fdf4829837dab8eb4d0ef95`, `359d962fb8cd498b8b34339abd46e5bc`, `95d0ab97a06845088d993ce3caa0f05e`, `acac3649758f47d2ad75687c198f13e8`, `cf09351ce0a144538f114de334e5d631`, `9a048a8e60cd40a6ac9c2b772425b441`, `b24f8284114046c288fe52c7ee2efe56`, `f97cbc9d2bbf4f56a9f515a84efb371e`, `0a59730230a9406e876dfde5efda8d79`, `20c28d32240040e78d7b5b05966cecb2`, `712ced3373c1471296582ddd7f14d216`, `80804597047042f5bc7eb8bd732d5bae` and `d86b5d0ee7c040fdbe1412aed8df2133`.
- Do not put model names in commits, PR titles or bodies, or any other pushed artifact. This applies to this handoff too.
- Machine-local facts (paths, tokens, environment IDs) stay out of the repo.
