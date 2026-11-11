# Resume plan: extraction after spec 12 (spec 13 next)

Recorded 2026-11-11 so another session can continue. Facts checked at origin/main `dbf20e6`. Machine-local paths are in the auto-memory note, not here. Review reports from earlier sessions were under `/tmp` and are not durable; their findings are copied into section 6.

## 1. Done and merged

- Spec 11 (#39): sequeless-ontology 0.1.0 released, six modules, tags `<component>-v0.1.0` at `c9726a7`. #14 (S2b, `7b44bdc`) merged 03:01Z before #10 (release PR, `c9726a7`, merged 03:06Z by bbartolome), so 0.1.0 includes S2b. The tracker records no owner go-ahead for #10; the admin-merge list cites the owner's standing direction. sequeless-build #46 (`c22f30a`), #47 (`4403dfe`, release), #48 (`a871904`) merged; sequeless-bom 0.1.3 (tag `sequeless-bom-v0.1.3`, at `4403dfe`) lists the six at 0.1.0. Issue #39 is still OPEN; step 1 closes it.
- Spec 12 (#40): draft #109 (`98ffe20`); filter bridge #110 (`c8eb638`); decimal fix #111 (`01cbced`); cut-over #112 (`3328993`, squash; gate on JDK 25 at head `29c2492`, all 9 modules, no failures); tracker #113 (`dbf20e6`). Issue #40 closed 2026-11-11 09:31Z.
- Gate (spec 12 section 9): `source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify`.

## 2. Open, not ours to merge

- sequeless-expression #19 (patch 0.1.1 to 0.1.2, documentation). CI, CodeQL and the PR-title run are `action_required` on head `c728a60`. Once the owner approves those runs and the checks pass, the patch release is pre-approved (section 5). Until then, leave it.
- sequeless-kernel #12 (release PR, snapshot). Held: owner decision iii (skip-snapshot) is open.
- sequeless-prototype #106: draft, "do not merge". Leave it.

## 3. Spec 13 (#41): state

- Draft: `docs/extraction/spec-13-prototype-stage4-prepare.md` at `05c3b6a`, on branch `spec-13-draft` (on origin). No PR yet.
- Reviews of the draft: review 1 of `8a5437c` NOT READY; `05c3b6a` applied its findings. Review 2 of `05c3b6a` NOT READY with blockers B1 to B4 and non-blocking N1 to N18. Those findings are in section 6 and must be posted on #41 (step 2) before the spec PR.
- Decided by the navigator (pair session of 2026-11-11, ended) and checked against the code at `dbf20e6`:
  - B1, V5 backfill timing: option B. Each removed outbox kind is backfilled in the PR that removes it, after the relay drains that kind. V5 backfills only `TransitionFired`. Basis: V4 lines 29 to 30 ("ActionRequest rows ... must keep dispatching normally"); `OutboxRelay.java` lines 46 to 53 still dispatch those kinds.
  - B3, where `prepare(tenant, Ontology version)` gets its Schema: option A. A host-side use case resolves the Schema through `SchemaSource` and calls the driven port with `(scope, version, Schema)`. Basis: `OntologyStartupValidator` line 64 and `IndexPreparingOntologyAdministration` line 82 already do this; `PostgresQueryStore` holds no `SchemaSource`.
  - B4, `search_vector` placement: option A, keep the column (V2 line 19), its function (line 54) and its trigger (line 68) on the Command side. Basis: they sit on `sq_object`; GLOSSARY line 52 says the Query side never changes the Store. **Navigator's choice; the owner confirms it.** The spec records it that way, not as decided. Review 1 classed it as an owner question. The C5 migration design must account for two facts: V2's trigger function reads `sq_searchable_property`, a Query registry (V2 line 60), and the backfill `UPDATE sq_object` runs in `PostgresQueryStore` line 371.
- OPEN, owner decisions (the plan does not choose them):
  - **O-8, Object view `state` and `audit`.** Option keep: keep both in the Object view and REST JSON. Cost: amend GLOSSARY lines 47 to 48 and #16 point 9. Option drop: remove both from the Object view and REST JSON, and change `sequeless-ui/projects/core-api/src/lib/models/index.ts` (lines 115 to 130), the readers `sequeless-ui/projects/automation/src/lib/signal-catalogue/signals-panel.ts` line 87 and `sequeless-ui/projects/objects/src/lib/signals/object-signals.ts` line 72, and the spec files that reference the type, including `sequeless-ui/projects/core-api/src/lib/models/models.spec.ts`. Review 1 leaned keep; the navigator leaned drop.
  - **B2, the Ontology version an outbox row is checked against.** Three routes:
    1. Read the active version; on a stamp mismatch, fail and retry. Matches the fail-on-mismatch check at `DefaultTransitionService` lines 325 to 330. Cost: a permanently mismatched row blocks every row behind it (the relay has no dead-letter; `OutboxRelay` javadoc lines 90 to 96).
    2. Read the active version; on a mismatch, skip and log. Cost: the row's actions or timer are dropped silently.
    3. Version-keyed reads. Reopens spec 10 Q3 (spec 10 line 403) and needs a `behaviour-contract` or `automation-contract` release.
    The navigator leaned to route 1.
- OPEN, owner checks (not run here):
  - O-3: whether any non-dev database has unprocessed rows of the four removed kinds. Command: `SELECT kind, count(*) FROM sq_outbox WHERE processed_at IS NULL GROUP BY kind;`. Under B1 it gates P2, P3 and P4, not P2a.
  - O-4: whether any Temporal workflows are running. Only the dev volumes named in `docker-compose.yml` are known. It gates every step that changes a Temporal workflow's code or input: P2, P3, P4, P5a and P5b (section 4). It does not gate P1 or P2a.

## 4. Plan, in order

1. **Land the plan and the tracker pointer, in one docs PR.** Add to `docs/extraction/remaining-work.md`, under "Start here" in "Next actions" (tracker line 23): "Resume from `docs/extraction/resume-plan-spec13.md`. The spec 13 draft is on branch `spec-13-draft` at `05c3b6a`, with no PR yet." Close issue #39 with a comment that links the spec 11 PRs. The PR body records the Opus verdict that approved this plan, with its SHA. The PR is done when it is merged.
2. **Put the open questions to the owner, and carry review 2 into the record.**
   - Post one comment on #41 listing O-8, B2 (all three routes, with leans and costs), the B4 confirmation, O-3 and O-4. Give each its options. The owner's answers are recorded in spec 13 section 12 and in the tracker.
   - Post review 2's blocking and non-blocking findings (B1 to B4, N1 to N18, NB1) as a second comment on #41, so they survive outside `/tmp`.
   - Posting comments on #41 and closing #39 are issue actions the owner's standing direction covers: "updating related GitHub issues in the process of merging into main".
3. **Revise the spec 13 draft.** Apply B1, B3 and B4 as decided in section 3. Apply or record each of review 2's N1 to N18 and NB1. Rework the C5 migration design for B4. The P7 design must state where `sq_extract_searchable_text` (V2 line 28) and the GIN index (V2 line 72) live, how the Command-side trigger reads the Query-owned `sq_searchable_property` (V2 line 60), and which side owns the backfill `UPDATE sq_object` (`PostgresQueryStore` line 371). Make these spec changes:
   - Split P5 into P5a (recompute before ingress) and P5b (the Store-event ingress).
   - Replace "merged in this order" (draft line 76) with the step order P0, P1, P2a, P5a, P2, P3, P4, P5b, P6, P7. This is the order of the plan's steps 4 to 6.
   - Record B4 as "the navigator's choice; owner to confirm", not as decided.
   - Opus review until the verdict is APPROVE with zero blocking findings. Then open the spec PR. Merge it before any code step, as spec 12 did (#109 before #110).
4. **Implement the steps that need no owner decision.** One PR per step. Each gets an Opus review before its PR, and the full gate on JDK 25 before merge.
   - P0: gate baseline. No code. Record the result in the spec PR body or a #41 comment.
   - P1: move `OutboxEntry` and `OutboxPort` from `spi.object` to `spi.outbox`. Mechanical. It changes no Temporal workflow or activity type name, and no JSON payload.
   - P2a: stamp the Ontology version on Store events. V5 backfills only `TransitionFired` (B1). The relay adds `TransitionFired` to `DISPATCHABLE_KINDS`. The adapters accept it as a no-op; that no-op is in `TemporalAutomationPort.dispatch`, which is client side. This step reads no stamp, so it does not depend on B2.
   - P7: index separation, per the C5 rework. **Held until the owner confirms B4 and the C5 rework is merged.** Runs once, here; it is not repeated in step 6.
5. **Hold until the owner answers.** Each hold states its own dependencies.
   - P5a, recompute before the ingress: changes `ChangeEventWorkflowImpl.run`, which removes `activities.recompute` from the workflow. A running `ChangeEventWorkflow` whose history already holds that activity would hit a non-determinism failure on replay. Held on **O-4**, unless the change is versioned with `Workflow.getVersion`. The in-process side (the recompute call in `InProcessAutomationPort` line 122) has no running state and is not held on O-4. Its transaction boundary is in section 6 (N8).
   - P2, actions leave the outbox: changes `ActionWorkflow`'s input and key, and builds actions from the version that B2 selects. Held on **B2, O-3 and O-4**.
   - P3, timers leave the outbox: changes `TimerWorkflowImpl.run`, which waits in `Workflow.await` with a 72-hour timer (reference.ttl line 310). Schedules from `TransitionFired`, and its timer lookup reads the version that B2 selects. Held on **B2, O-3 and O-4**.
   - P4, signals become Store Commands: the signal path (`DefaultTransitionService.signal`) reads the active `SchemaAndBehaviour` at request time (line 272) and commits through the agreement check at lines 325 to 330, so it does not read a stamp and does not depend on B2. It depends on **O-3** (drain pending `SignalReceived` before its backfill, under B1), **O-4** (running `SignalEventWorkflow` instances), and the kind-set count. Its kind-set test expects 4 constants, which holds only after P2 and P3; if it runs earlier the count is 7. So P4 runs after P2 and P3, not ahead of them.
   - P5b, the Store-event ingress: replaces the adapter switches. It needs P2 to P4 landed, because until then the relay still dispatches ActionRequest, TimerScheduled, TimerCancelled and SignalReceived through the switch. It depends on **O-4** if it changes workflow code, and on **B2** if the ingress reads the stamped version (#20 point 5).
   - P6, Read and Browse ports and the Object view: depends on **O-8**.
6. **After the holds clear**, run in the order P2a is already in: P5a, P2, P3, P4, P5b, P6. Each step that removes a kind carries that kind's removal and backfill. P5b, P6 and P7 remove no kind. P7 is not repeated here (step 4).
7. **Then specs 14 to 19.** Each needs its own draft, review and PR. Spec 14 creates `sequeless-query`; spec 15 creates `sequeless-automation`; spec 19 creates `sequeless-knowledge`. The owner approved creating those three repos on 2026-11-06; each is created when its spec starts.

## 5. Process rules

- Opus reviewers are read-only. Their reports go under `/tmp` for the session, and their blocking and non-blocking findings go into a #41 comment or the spec PR body, because `/tmp` is not durable.
- Haiku implements. Each branch gets its own git worktree; the main checkout is never used for work.
- Pre-approved by the owner: create and merge PRs and push branches, for docs and sequeless PRs; minor and patch releases after checks pass; majors need confirmation. Comments on issues, and closing issues that the work completes, are covered by "updating related GitHub issues in the process of merging into main". Not pre-approved: ruleset changes, CI approvals, the kernel snapshot decision (iii), and every owner decision. Pre-approval covers the sequeless repos only.
- Merge pinned to the reviewed head: `gh pr merge <n> -R <repo> --squash --match-head-commit <sha>` for sequeless-prototype. sequeless-ontology is rebase-only. Set the squash subject and body explicitly when the commit subjects would overstate the change.
- Admin merges: only for the `update` rule of ruleset `main-restrict-updates`, on sequeless-ontology (id 25931311) and sequeless-filter (id 23522900), with required checks green on a head that is up to date with main. sequeless-prototype has no rulesets. Record each use in the tracker. Never use `--admin` past a red or missing required check, a BLOCKED result, or the unattributed-changes rule (tracker lines 65 to 66).
- The navigator is for scope questions. Its answers are recorded and checked against the code. Owner-level decisions stay with the owner.
- The plan is approved when an Opus review gives APPROVE with zero blocking findings. Until then, no step runs.

## 6. Review findings (of `05c3b6a`), carried here so they survive

Blocking:
- **B1** V5 backfill drops live work. Fixed by the navigator decision (section 3, B1).
- **B2** O-1 and O-2 need reads keyed by the Ontology version of the event, which do not exist (released `BehaviourSource` and `AutomationSource` read only the active version). Open owner decision (section 3, B2).
- **B3** `prepare` has no source for the Schema. Fixed by the navigator decision (section 3, B3).
- **B4** "Only O-8 is an owner decision" is false: `search_vector` placement is an open owner question. Chosen by the navigator for the owner to confirm (section 3, B4).
- **NB1** (from the second plan review) O-4 gates every step that changes Temporal workflow code, not only P4. Fixed in section 5 of this plan.

Non-blocking (to apply or record in step 3):
- N1: the timer lookup is at `DefaultTransitionService` 377 to 378 (a loop over `machine.transitions()` tests `instanceof TimerTrigger`), not in `DefaultTriggerEvaluator`. `onTimerElapsed` (133 to 154) only reads the transition name.
- N2: P3 must edit `AutomationEndToEndTest` line 202 (`awaitOutboxProcessed(…, "TimerScheduled")`), and also lines 252 and 253 (`TimerScheduled` and `TimerCancelled`).
- N3: P6's "in-memory adapters" for `ReadContract` and `BrowseContract` do not exist. Name the doubles to write, or drop the phrase. `QueryContract` is extended only by `PostgresQueryStoreContractIT`.
- N4: `InMemoryAggregateQueryPort` (testkit `src/test`, line 42) is a sixth `QueryPort` test implementation. It drops `query` and `ensureIndexes` at P6.
- N5: section 5.2 omits the `dispatchStillRejectsTransitionFiredKind` edit in `AutomationContract` (line 168, changed in P2a), the `PostgresOutboxPortIT` edits (`KIND_ACTION_REQUEST` at lines 63 and 180, changed in P2 to P4), and `TemporalAutomationEndToEndTest` line 234.
- N6: the core P2a entry omits `DefaultActionExecutor` (114, 163) and `DefaultDerivationRecomputer` (278), which also build Store events.
- N7: the `AutomationPort` redelivery contract is not restated (javadoc lines 29 to 31: a second call with the same `entry.id()` must not re-invoke the handler). `InProcessAutomationPort` records `entry.id()` before dispatch (line 115), so a failure part-way loses the remaining actions on retry.
- N8: P5a has a transaction boundary. The in-process default is COALESCE mode (`InProcessRecomputeProperties` line 58): the recompute runs on its own thread (`RecomputeCoordinator` line 120), outside the relay's claim transaction. A synchronous recompute in the relay would run inside the claim's `TransactionTemplate` (`PostgresOutboxPort` line 53; `PostgresObjectStore` line 67; both REQUIRED). A `StaleObjectException` that `DefaultDerivationRecomputer` retries can mark the claim transaction rollback-only. RETRY_ONLY mode already runs that way today. The spec must state the boundary and the mode.
- N9: the C5 design has three open points. (a) The baseline at 2 applies only where the Command history records V2; a blanket `baselineOnMigrate` also baselines a fresh database, which is non-empty after the Command instance runs. (b) "V1..V7" matches neither V1 to V5 nor V1 to V8. (c) "The numbers are the owner's range" is unclear: state the final version set.
- N10: `TransitionFired` lacks the `typeIri` and the machine IRI that `automation.actions` needs (`DefaultTransitionService` line 349, `TransitionRef(machine.iri(), …)`). `actionRequestPayload` writes `typeIri` at line 492.
- N11: mixed-version rows at P2's deploy: a row without `self` runs no actions. Its cause: the `ActionRequest` siblings share `occurred_at` (lines 347 and 351 both use `now`), and once P2 drops the kind those siblings become unclaimable. A row without `self` would otherwise fail and block the relay.
- N12: after P4, the UI text "Signal … dispatched — takes effect asynchronously" (`object-signals.ts` line 47) and the 1500 ms refetch (line 21) are stale. State whether the text changes.
- N13: P6 cites #24 point 2 for the Read request record, which is in #24's spec 14 list. Say spec 13 adopts it early.
- N14: the O-9 line references overlap: two lines cover the three sites.
- N15: the `InProcessAutomationPort` switch is lines 119 to 129, not 120 to 138.
- N16: O-5 cites line 78 as the line that returns `Observable<void>`. That type is on line 76 of `objects-client.ts`; line 78 is the `.post(` call. Correct the sentence to say so.
- N17: P0's "the gate passes on `3328993`" is a target, not a result.
- N18: line 5 of the draft claims every finding is applied or recorded; C6's redelivery contract and C8's record rule are neither.

## 7. Self-sufficiency

- Base SHA of every fact here: origin/main `dbf20e6`. Spec 13 draft `05c3b6a` on branch `spec-13-draft`. This plan is on branch `docs-spec13-resume`.
- Navigator session `a63eab5e54944407971cf5274c5fda21` has ended and is recorded only here. Do not resume it.
- Worktrees are machine-local and are not needed: the branch names are enough.
- The JDK 25 path is machine-local; the gate command in section 1 needs it.
