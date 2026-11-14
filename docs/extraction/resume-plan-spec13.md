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
3. **Revise the spec 13 draft.** Apply B1 and B3 as decided in section 3, and B4 as the navigator's choice that the owner confirms (section 3). Apply or record each of review 2's N1 to N18 and NB1. Rework the C5 migration design for B4. The P7 design must state where `sq_extract_searchable_text` (V2 line 28) and the GIN index (V2 line 72) live, how the Command-side trigger reads the Query-owned `sq_searchable_property` (V2 line 60), and which side owns the backfill `UPDATE sq_object` (`PostgresQueryStore` line 371). Make these spec changes:
   - Split P5 into P5a (recompute before ingress) and P5b (the Store-event ingress).
   - Replace "merged in this order" (draft line 76) with the step order P0, P1, P2a, P5a, P2, P3, P4, P5b, P6, P7. This is the order of the plan's steps 4 to 6 (P6 later split: spec 13 section 12, item 23).
   - Record B4 as "the navigator's choice; owner to confirm", not as decided. Use the same wording in the spec's section 12.
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
   - P5b, the Store-event ingress: replaces the adapter switches. It needs P2 to P4 landed, because until then the relay still dispatches ActionRequest, TimerScheduled, TimerCancelled and SignalReceived through the switch. It depends on **O-4** (the Temporal adapter changes with it), and on **B2** if the ingress reads the stamped version (#20 point 5).
   - P6a, the Read and Browse ports with the `BusinessObject` return type, the prepare port and use case, and the removals: not held on O-8 (spec 13 section 12, item 23). P6b, `ObjectView`, the `state` and `audit` fields and the JSON: depends on **O-8**.
6. **After the holds clear**, run in the order P2a is already in, with P6a moved before P2 (spec 13 section 12, item 23): P5a, P6a, P2, P3, P4, P5b, P6b. P6a is not held and runs now (step 5). Each step that removes a kind carries that kind's removal and backfill. P5b, P6a, P6b and P7 remove no kind. P7 is not repeated here (step 4).
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

## 8. Progress (recorded after P2a)

Merged on main (origin/main at the time of writing):
- Spec 13 draft: #115, squash `511280e`. Reviewed seven rounds; approved at `3ca9771`, wording fixes at `31addfc`.
- P0 (gate baseline): satisfied by the cut-over gate at `29c2492` (all 9 modules, JDK 25). Later commits on main are docs-only.
- P1 (outbox types to `org.sequeless.spi.outbox`): #116, squash `844e3b5`. Opus APPROVE, gate green (580 unit, 57 IT).
- P2a (TransitionFired claimable, Ontology version stamp): #117, squash `fbb82c5`. Opus APPROVE, gate green (643 tests). Migration V5 is merged. Recorded deviation: DefaultActionExecutor copies the version from its ActionRequest row; it reads no snapshot.

Every remaining step, except P6a (see section 9), waits on an owner answer:
- P2 (actions leave the outbox): held on B2, O-3 and O-4.
- P3 (timers leave the outbox): held on B2, O-3 and O-4.
- P4 (signals become Store Commands): held on O-3 and O-4; runs after P2 and P3.
- P5a (recompute before the ingress): in the P5a PR. P5a ships the relay recompute, the REQUIRES_NEW decorator and the in-process removal, and changes no Temporal workflow code (javadoc only). The Temporal removal of `activities.recompute` in `ChangeEventWorkflowImpl.run` moves to P5b, which stays held on O-4. Until P5b, a Temporal change event recomputes twice, and the second recompute is a no-op.
- P5b (Store-event ingress): held on O-4, and on B2 if it reads the stamp. It also carries the Temporal removal of `activities.recompute` moved from P5a. Its PR names `ChangeEventWorkflow` in its O-4 list, because the workflow code changes too.
- P6 (Read and Browse ports, Object view): held on O-8. (Split in section 9: P6a is not held; P6b is held on O-8.)
- P7 (index separation): held on the B4 confirmation, and on section 12 item 21 (the backfill transaction question), which must be settled before P7 merges.

Owner answers needed, in one place: O-8, B2, the B4 confirmation, O-3, O-4. The #41 comment with these questions was posted on 2026-11-11.

Follow-ups noted by the reviews, not yet done:
- P2a review NB-1: the frozen V1 to V5 migration set is not saved as a test resource. The P7 PR saves it from P2a's merge commit `fbb82c5`.
- P2a review NB-4: the V5 migration IT (PostgresOutboxV5MigrationIT) runs later migrations and uses kind constants that P2 to P4 delete. Each of P2, P3 and P4 must pin that test to `migrateTo("5")` and use string literals.
- P2a review judgment 1: if B2 selects the active version for P2's actions, the executor's stamp must be revisited.
- Stale text: docs/architecture/automation.md lines 21, 43, 65, 72 and the OutboxPort javadoc list (lines 13 to 16) still say TransitionFired is audit-only.
- Stale paths: docs/architecture/automation.md lines 37 and 131 cite the old `spi/object/Outbox*.java` paths.

Recorded after P5a (the docs change that follows it fixes the two stale-text items above):
- P5a merged as #119, squash `6e7764f`. Deviation from the spec: the Temporal removal of `activities.recompute` moved to P5b, which stays held on O-4 and names `ChangeEventWorkflow` in its O-4 list. Until P5b, a Temporal change event recomputes twice.
- The relay runs the recompute before the ingress. Open risk, with no remedy yet: a persistent recompute failure blocks the relay, because the relay has no dead-letter or skip. Choosing a remedy is an owner decision.
- The BOM refresh for sequeless-build: #49 merged as `593d03d` (filter versions). Release #50 (`6ba3d29`) published sequeless-bom 0.1.4. The import bump #51 (`a822b8e`) moved bom-contracts to 0.1.4.

## 9. Session of 2026-11-11 (after P5a; navigator Q1 and Q2)

- Start of session: origin/main `789609c`. P5a is #119 (`6e7764f`), and the docs follow-up is #120 (`789609c`). Issue #39 was closed 2026-11-11 14:02Z.
- Q2, closed: the SchemaValidationSchema tracker item is closed as not needed (option b). The navigator decision is recorded in its session. Evidence is in remaining-work.md's open-item text.
- Q1, allowed: P6 may be split. P6a is not held on O-8; P6b is. See spec 13 section 12, item 23.
- Not done in this session: the P6a code. Also still held on owner answers: P2, P3, P4, P5b, P6b and P7 (O-8, B2, the B4 confirmation, O-3 and O-4). The #41 questions are unanswered.
- Next action for the next session: write the P6a code PR in a fresh worktree from origin/main. Write it first as a short plan, get an Opus review, then implement, run the gate on JDK 25 (`source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify`), then get an Opus review of the diff before the PR, and comment on #41.

## 10. Progress after P6a (recorded 2026-11-12)

- P6a is merged as #122, squash `098171d`, on top of `544f64e`. The approved plan is the step list in the PR body and the #41 comment (posted 2026-11-12), not the draft files under /tmp, which are not durable. Its commits are C1 to C6 (additive first, removals last) plus the spec correction C7, and one javadoc follow-up `beda51b`.
- Evidence: the full `-Prelease verify` passes on `beda51b`. The golden JSON diff of five endpoints is empty between `544f64e` and `5987c3b`. The residual grep for removed members is empty.
- Decisions taken with the navigator in this session, and recorded in the PR body: `ReadRequest(ObjectId id)` (single component, type check stays in `DefaultBusinessObjectService`); the version path (each call site reads the schema and passes its version, and the use case re-reads it, with no guard); `PageResult` kept; the method name `BrowsePort.browse`.
- Still held on owner answers: P6b (O-8); P2, P3 and P5b (B2, O-3 and O-4; P4 also after P2 and P3); P7 (the B4 confirmation and spec item 21). No code step is unblocked.
- Follow-ups from the reviews, not blocking: `PageResult` is dead SPI surface; some `BrowseContract` assertions are weak; `CoreConfiguration` javadoc still lists `QueryPort` among the service's ports; stale prose in `docs/architecture/01-overview.md` (line 103) and a few test and main comments.

## 11. Progress after P6b and P7 (recorded 2026-11-12)

- P6b (Object view) merged as #125 (squash `211c91f`). P7 (index separation) merged as #124 (squash `9b6e316`). Both had Opus approval, and the gate was green on JDK 25 (690 and 684 tests).
- Decisions, settled by the Navigator under the owner's delegation (recorded on #41; the owner may confirm): O-8 = (a), keep state and audit in the Object view and the REST JSON; B2 = route 1, read the active version and fail and retry on a mismatch; B4 = as proposed (the search column, its function and trigger stay Command-side); item 21 = one host-opened transaction; P7 migration = option 1 (Command to V6, then Query, then the rest of Command); the backfill signal = a boolean from `prepare`; the backfill port = `SearchVectorBackfillPort` in `spi.object`; P6b derivation = D3 (eight-field `ObjectView`) and the transitions endpoint = T1 (an `ObjectView` overload of the interpreter).
- Correction to the P7 plan rationale: the success case of the transaction proof does not show that the backfill joins the host transaction. Without the transaction, `prepare` commits the registry row before the backfill, so the backfill still sees it. The rollback case is the discriminating proof. The PR body records this.
- Still held, unchanged: P2, P3, P4 and P5b wait on O-3 (unprocessed outbox rows on non-dev databases) and O-4 (running Temporal workflows). Neither can be checked from this environment. No code step is unblocked until the owner answers them.
- The stale P6a line in section 9 ("write the P6a code PR") is superseded: P6a merged as #122 (`098171d`).

## 12. Progress after #128 and #129 (recorded 2026-11-12)

- Merged since section 11: #128, the V5 migration IT pinned to V5 with string kind literals (the P2a review NB-4), squash `bce132e`, on reviewed head `71fb418`. Read-only review APPROVE, zero blocking. #129, the BrowseContract assertion strengthening (P6a follow-up), squash `cba1a4b`, on reviewed head `4c47072`. Read-only review APPROVE, zero blocking. origin/main is `cba1a4b`.
- Verified done, do not redo: the P2a review NB-1 (section 8; the frozen V1 to V5 set is saved at `sequeless-adapter-persistence-postgres/src/test/resources/frozen-migrations/fbb82c5`), the CoreConfiguration javadoc (it says QueryPort is reached through the DerivationPlanner), and the `docs/architecture/01-overview.md` port table (current). The P2a review NB-4 is done by #128. The BrowseContract follow-up is done by #129, except the id-order item below.
- Held, unchanged: P2, P3, P4 and P5b. Decision (Navigator, 2026-11-12, session `0bd19ad0c8764fafbdc8c7a44da034bb`): option A, hold with no code. Reason: O-3 and O-4 are facts about non-dev databases and running Temporal workflows that no agent here can check, and merging on assumption risks silent loss of unprocessed outbox rows (no dead-letter) and replay failures in running workflows. Draft PRs were rejected because they drift against the pinned-to-reviewed-head merge rule. P4 also waits for P2 and P3.
- The owner checks, exact commands: O-3 is `SELECT kind, count(*) FROM sq_outbox WHERE processed_at IS NULL GROUP BY kind;` on every non-dev database. O-4 is a check for running Temporal workflows of the types ActionWorkflow, TimerWorkflow, SignalEventWorkflow and ChangeEventWorkflow. The questions are on #41 (comment of 2026-11-11), still unanswered for O-3 and O-4.
- Decided and merged after section 12 was written: the BrowsePort id order is DR-21 (`docs/architecture/02-decision-records.md`), merged as #131 (squash `4ae250c`). Ids compare in unsigned byte order of the 16-byte UUID; the in-memory double now matches PostgreSQL; four fixed-id contract tests pin the tie-breaks. The tracker item in Open items is marked done.
- Follow-ups from the DR-21 review, not blocking: facet bucket order (Postgres has no tie-break at the facet ORDER BY); a null-key contract test; `docs/agents/domain.md` and `design-preferences.md` still name `docs/adr/`, and `CLAUDE.md:15` does too (the owner decides whether to update CLAUDE.md).
- Still open, unchanged: the relay's persistent-recompute-failure risk (owner decision: dead-letter or skip) (Superseded by section 13, step 4: the Navigator decided dead-letter, under the owner's delegation.); the P5b Temporal removal of `activities.recompute` (held on O-4); the prototype `codeql.yml` `packages: read` item (the pin stays at `workflows-v0.1.1`); `PageResult` is dead SPI surface (kept by a Navigator decision at P6a).
- Not touched, and not to be touched without the owner: PR #106 (draft, do not merge); sequeless-kernel #12 (stays open; owner decision iii); sequeless-expression #19 (its CI runs need owner approval; its patch release is pre-approved only after checks pass).
- Specs 14 to 19 wait for spec 13 and are not started.
- Next session: ask the owner for the O-3 and O-4 results on #41. Once they are in, P2, P3, P4 and P5b can be re-planned; each still needs its own plan, read-only review, and PR.

## 13. Plan for the next steps (recorded 2026-11-12)

- Plan source: Navigator set_plan answer, session a470346d6cc14ddb86329d19b3546e41. Opus review of the plan, agent a6394304614e9e856: REWORK, then APPROVE after the step 2 wording and the step 5 wording were amended. The reviewer's conditions are copied below.
- Steps, in order:
  - **1-tracker-refresh** (this commit, lands first): record #128 to #132, DR-21, the Navigator hold, the O-3/O-4 commands; tick the spec-drafts item; add the open items.
  - **2-docs-adr-path**: docs/agents/domain.md and design-preferences.md, path and format wording; CLAUDE.md:15 left for the owner.
  - **3-facet-tiebreak**: DR-22 plus code and tests in one PR. Decided by the Navigator (2026-11-12): ORDER BY bucket_count DESC, bucket_value COLLATE "C" in both facets; NULL keys excluded in both facets (add IS NOT NULL to the reference facet at PostgresQueryStore:682-684). BrowsePort javadoc gets a bucket-order clause next to "Facet counting" (buckets sorted by count descending, then value in Unicode code point order; null keys excluded). BrowseContract gets a tied-count, mixed-case test. DR-22 must state the edge: COLLATE "C" orders UTF-8 bytes, which is code point order, but Java String.compareTo (the in-memory double) compares UTF-16 code units, so the two differ for supplementary characters above U+FFFF. Gate: JDK 25 `-Prelease verify`. Starts only after its own plan and Opus review.
  - **4-relay-deadletter**: dead-letter, not skip. Its own plan and its own review first. O-3's command stays unchanged; see the mechanism below. Its migration is V7 (the Command side is at V6), so later backfills move to V8 or above. Under B2 route 1, "fail and retry" becomes "retry, then dead-letter". Plans for P2 to P4 must drain or resolve dead-lettered rows. Not gated on O-4.
    Mechanism, decided by the Navigator (session a470346d6cc14ddb86329d19b3546e41, 2026-11-12):
    - Migration V7 on the Command side adds three columns to sq_outbox: fail_count int NOT NULL DEFAULT 0, dead_lettered_at timestamptz NULL, error text NULL.
    - A dead-lettered row keeps processed_at NULL. The relay claim query (PostgresOutboxPort.java lines 67 to 68, not PostgresQueryStore) gains AND dead_lettered_at IS NULL, so the row leaves the claim queue but stays in sq_outbox.
    - Threshold: 3 consecutive failures. It reads fail_count, not attempts. sq_outbox.attempts is never incremented on origin/main (V1 defines it, PostgresObjectStore.java line 304 inserts 0, V3 says it is write-only), so it cannot count failures.
    - fail_count is incremented in its own transaction after each rolled-back dispatch, because a failing handler rolls back the claim transaction. When the count reaches 3, the same own transaction writes dead_lettered_at = now() and error = the handler message.
    - O-3's command is unchanged and still counts dead-lettered rows as unprocessed, so the owner's kind-removal gate sees them. Option A (set processed_at) and option C (a separate table) were rejected because they hide the row from O-3.
    - The OutboxRelay javadoc paragraph "Known, accepted limitation" (OutboxRelay.java lines 112 to 117) is rewritten in the same PR.
    - The DR is DR-23 (DR-22 is the facet decision). It records that B2 route 1 becomes "retry 3 times, then dead-letter".
  - **5-ontology-q4**: in scope; first decide range-or-reject (a semantic decision, Q4 was an owner answer, #100). The commit must be non-breaking: the sequeless-ontology release-please config lacks bump-minor-pre-major, so the commit must have no `feat!` and no `BREAKING CHANGE` footer, because a breaking commit would cut 1.0.0 (a major release that needs the owner's confirmation). Chain: ontology PR (rebase-only; admin only for the update rule, each use recorded), its release PR (the config has no bump-patch-for-minor-pre-major, so a feat: commit would release 0.2.0; section 5 pre-approves minor releases once checks pass, so that release needs no separate approval), the sequeless-build BOM PR and its release, the bom-contracts import, and the prototype BOM bump with the JDK 25 gate.
  - **6-tracker-final**: record the PRs from steps 3 to 5 and close the facet follow-up items.
- Held and unchanged: P2, P3, P4 and P5b on O-3 and O-4. Specs 14 to 19 wait on spec 13.
- Process: section 5 of this file applies to every step: Opus review before each PR, merges with `--match-head-commit`, Haiku in a fresh worktree from origin/main, the JDK 25 `-Prelease verify` gate for steps 3 and 4 and the prototype BOM bump, no CI approvals, no ruleset changes, no `--admin` (the prototype has no rulesets). The step 5 rule still applies to sequeless-ontology: admin only for the update rule, each use recorded.
- Not touched: PR #106, sequeless-kernel #12, sequeless-expression #19 (CI approval needed, not pre-approved). CLAUDE.md:15 (owner question).
- Reviewer conditions for step 4:
  - The O-3 command stays unchanged and still counts dead-lettered rows (Navigator decision; see the mechanism in step 4).
  - P2 to P4 drain the dead-lettered rows.
  - The B2 route 1 wording is updated: "fail and retry" becomes "retry, then dead-letter".
  - The dead-letter mark is written in a separate transaction.

### Status on 2026-11-13

- **1-tracker-refresh**: done, #134 (`e456538`).
- **2-docs-adr-path**: done, #133 (`057b07f`).
- **3-facet-tiebreak**: done, #136 (`c1e8ad4`), DR-22.
- **4-relay-deadletter**: done, #138 (`8639410`), DR-23. The docs were in #135 (`6a15d3e`).
- **5-ontology-q4**: the fix is merged as sequeless-ontology #15 (`10f5a57`). Still open: release PR #16 (blocked on owner CI approval), then the sequeless-build BOM update to ontology 0.1.1 and its release, the bom-contracts import, and the prototype BOM bump with the JDK 25 gate. The owner decision is (viii) in remaining-work.md.
- **6-tracker-final**: this commit.
- Held: P2, P3, P4 and P5b on O-3 and O-4 (owner checks, unanswered). Specs 14 to 19 wait on spec 13.
- **Update 2026-11-14, step 5 is done.** The Q4 chain is complete: sequeless-ontology #15 (`10f5a57`, the fix) and #16 (`4a63388`, the release, admin rebase) are merged; ontology-jena-adapter 0.1.1 is published (tag `ontology-jena-adapter-v0.1.1`); sequeless-build #52 (the BOM lists ontology-jena-adapter 0.1.1), #53 (release sequeless-bom 0.1.5) and #54 (bom-contracts import of 0.1.5) are merged; prototype #152 (the BOM import of 0.1.5 and the OWL rollup test alignment) is merged. sequeless-ontology #17 (`28e0a2e`, a javadoc fix) was merged on its own and is not a step of the chain. sequeless-ontology #18, a separate docs-only release of ontology-jena-adapter 0.1.2, was merged on 2026-11-14 as `67b91af` (see section 15). It is not part of step 5.

## 14. Progress after #140 to #143 (recorded 2026-11-13)

- Merged on sequeless-prototype `main` since section 13, with squash SHAs: #140 `74b11d3` (docs/architecture/automation.md, the stale dead-letter text corrected, DR-23); #141 `2a52ff1` (BrowseContract javadoc records the facet bucket order; the BrowsePort coverage claim removed); #142 `9d37327` (the relay row mapper records the row id before the payload is decoded, so an undecodable payload counts as a failed dispatch and is dead-lettered at the third failure; new PostgresOutboxDecodeFailureIT); #143 `7d678da` (dead-letter failure-path tests in PostgresOutboxPortIT). origin/main is `7d678da`.
- Spec 13 steps: 1 to 4 are done. Step 4 is #138 (`8639410`, with the docs in #135); its gaps are closed by #142 and #143. Step 5 is blocked on owner decision (viii): sequeless-ontology release PR #16, read on 2026-11-13 with `gh pr view` at head `c58c2038b96aa03b4419ea649d95819fc69cad24`, state OPEN, mergeStateStatus BLOCKED, with its CI and CodeQL runs awaiting approval. Step 6 is the tracker update in this commit.
- P0, P1, P2a, P5a, P6a, P6b and P7 are merged. P2, P3, P4 and P5b are held on O-3 and O-4, and no code step is unblocked. The owner checks are unchanged:
  - O-3: `SELECT kind, count(*) FROM sq_outbox WHERE processed_at IS NULL GROUP BY kind;` on every non-dev database.
  - O-4: a check for running Temporal workflows of the types ActionWorkflow, TimerWorkflow, SignalEventWorkflow and ChangeEventWorkflow.
- Decode fix decision: option A (Navigator, 2026-11-13, session `08e650684ebf42cd99de1f1fe99a026f`). The merged fix is #142.
- Partial index sq_outbox_unprocessed_dispatchable_idx (V5): predicate kind IN (8 kinds) AND processed_at IS NULL. A dead-lettered row keeps processed_at NULL, so it stays in the index at the head of occurred_at order. The claim query already filters dead_lettered_at IS NULL, so the cost is a few wasted index entries per claim, not a correctness gap. Branch spec-13-dead-letter-index-v8 (commits b838237 and 6cf5938) adds migration V8 that adds AND dead_lettered_at IS NULL; the gate is green and Opus approved it. HELD, Navigator decision 2026-11-13 (option C): do not merge until the owner has run O-3 on the non-dev databases and chosen a window. Reason: V8 is DROP INDEX then CREATE INDEX in one transaction, run at app start by PostgresMigratingDataSource, so it takes an ACCESS EXCLUSIVE lock on sq_outbox that blocks reads and every claim for the build time, which grows with the table (processed rows are never deleted). No CONCURRENTLY precedent exists in the repo; the lock-free route needs the Flyway non-transactional marker in V8, a small additive change to re-review.
- Migration V8 timing, owner decision (ix) in remaining-work.md: branch `spec-13-dead-letter-index-v8` (commits b838237 and 6cf5938) is pushed to origin with no PR, and is held, not merged. Checked 2026-11-13 with `gh pr list --head` (no PR in any state).
- Navigator sessions of the earlier plans have ended and must not be resumed (sections 7, 12 and 13 name them). Navigator session `08e650684ebf42cd99de1f1fe99a026f` was alive when this section was written.
- New open items, recorded in remaining-work.md on 2026-11-13: the DR-11 misattribution (automation.md, derived-properties.md, sq-vocabulary.md, sequeless-app/src/main/resources/ontology/reference.ttl:251; sequeless-ui/projects/rdf/src/testing/reference-ttl.ts:2 (a byte-copy of reference.ttl; hand-maintained, see the coupled-sites item below)); the `KIND_TRANSITION_FIRED` javadoc in `OutboxEntry.java`; the BrowseContract coverage gaps; the decode-failure scope wording; and the Q4 release chain. Each is docs-only or needs its own PR.
- Next session starts by asking the owner for the items listed in section 15. This replaces the 2026-11-13 ask about owner decision (viii) on sequeless-ontology #16 CI, which is obsolete: #16 merged as `4a63388`.
- Do not approve any CI run. Do not merge sequeless-kernel #12. Do not touch PR #106 (draft, do not merge). (Updated 2026-11-14: kernel #12 is already merged as `11a92be`; see section 15.)

## 15. Handoff recorded 2026-11-14

- Checked against origin/main `7b168e6` (2026-11-14). Read the tracker with `git show origin/main:...`, because a local checkout can be behind main.
- Navigator decision (2026-11-14, option A): P2, P3, P4 and P5b are unblocked. Each gets its own plan and read-only review before any code. Order: P2, P3, P4, then P5b. P4 follows P2 and P3 landing, and P5b follows P2 to P4 landing. The other held items are:
  - V8 index migration: held on owner decision (ix), the window.
  - DR-11 coupled sites (`sequeless-app/src/main/resources/ontology/reference.ttl` line 251 and `sequeless-ui/projects/rdf/src/testing/reference-ttl.ts` line 2): blocked on an ontology-testfixtures release that carries the corrected bytes.
  - Mockito 5.14.2 bump in sequeless-ontology (remaining-work.md, the Mockito item): waits for the first Mockito test in that repo.
  - Specs 14 to 19: wait on spec 13.
- Owner asks for the next session:
  1. O-3: answered by the owner on 2026-11-14. There are no non-dev environments, so there are no non-dev databases and no non-dev unprocessed rows.
  2. O-4: answered by the owner on 2026-11-14. Cleared at the owner's request: the dev Temporal volumes `sequeless-prototype-ui_sequeless-temporal` and `ui-kit_sequeless-temporal`, after the stopped container `ui-kit-temporal-1` was removed. The Postgres volumes were not touched. No `sequeless-prototype_sequeless-temporal` volume existed before.
  3. The V8 window, owner decision (ix).
  4. Optional: an owner check of the GitHub Packages listing for `ontology-jena-adapter` 0.1.2. This run's token cannot read packages, so the listing is unchecked. The tag and release run are checked (see below).
  5. Confirm whether the expression-jexl-adapter 0.1.2 gap is moot now that 0.1.3 is released (remaining-work.md, the #21 item). This is not blocking.
- Done since section 14: sequeless-ontology #18 (docs-only release of ontology-jena-adapter 0.1.2) is merged as `67b91af` (2026-11-14T01:17:36Z). It was an admin rebase pinned to head `a596a17`, update rule only, all four checks green, under the Navigator's decision for this run. Its CI run `43519358243` (attempt 2) was triggered by bbartolome, and its checks passed. Release run `43539704411` succeeded, and tag `ontology-jena-adapter-v0.1.2` exists.
- Closed since section 14: sequeless-kernel #16 is CLOSED and was not merged (2026-11-13T21:01:56Z). Its close-or-refresh question is answered. remaining-work.md (the kernel #16 hazard and the kernel release gap item) now says so.
- Done 2026-11-14: the `ci.yml` pin is at `workflows-v0.1.3`, and `codeql.yml` is at `workflows-v0.1.3` with `packages: read`, in the ci-pins PR that carries this section. No Renovate PR is open for either pin.
- The Navigator session that set this plan is `3731b1f872a54a4ab1050a6bbaa558ef` (2026-11-14). Do not resume it.
- Guardrails:
  - Do not approve any CI run.
  - sequeless-kernel #12 is merged (`11a92be`, 2026-11-13, by bbartolome). Before merging any kernel release PR, check that its label is `autorelease: pending`, not `autorelease: snapshot` (remaining-work.md, Open items, the kernel release gap item).
  - Do not touch PR #106.
  - PR #137 (draft, branch `docs/sequeless-explained-ste`, "docs: add Sequeless explanation") was CLOSED by bbartolome at 2026-11-14T01:09:00Z. The handoff run did not close it. Do not reopen it without the user's say-so.
  - `--admin` only for the update rule, with each use recorded (remaining-work.md, Merge rules).

## 16. Handoff recorded 2026-11-14, after #156 (P2 is next)

- Checked against origin/main `e6b2400` (#156, squash, merged 2026-11-14T06:58:23Z). Read the tracker with `git show origin/main:...`, or in a worktree at origin/main, because a local checkout can be behind main.
- Done: the reusable workflow pins are at `workflows-v0.1.3`. ci.yml calls the reusable `maven-ci.yml`, and codeql.yml, which now grants `packages: read`, calls the reusable `maven-codeql.yml`. The CI and CodeQL checks on #156 passed, including the CodeQL javascript-typescript and actions jobs on codeql-action v4.
- Owner answers, recorded on #41 (comment 6873466422): O-3, no non-dev environments, so no non-dev databases and no non-dev unprocessed rows. O-4, the dev Temporal volumes `sequeless-prototype-ui_sequeless-temporal` and `ui-kit_sequeless-temporal` were cleared at the owner's request, and the stopped container `ui-kit-temporal-1` was removed. Temporal's dev state is the SQLite file in those volumes. The Postgres volumes were not touched.
- Navigator decision (session `3b9f327e2e4f479b99c42d14c1e51665`, 2026-11-14, option A): P2, P3, P4 and P5b are unblocked, in the order P2, P3, P4, then P5b. Each gets its own plan and read-only review before any code, and its own PR. P4 follows P2 and P3 landing, and P5b follows P2 to P4 landing.
- Navigator decision (same session, 2026-11-14): P2's migration is V8. The held index branch `spec-13-dead-letter-index-v8` renumbers V8 to V9 when the owner picks its window (decision (ix)). Constraint: never merge V9 before V8 is on main. `outOfOrder` is false in `PostgresMigrations`, so Flyway fails validation on any database that has applied V9 when V8 is still pending. Numbers are taken at merge, in merge order (spec 13 line 127). The navigator's decision assumes P2 merges first. If the index window comes first, the numbering needs a new navigator decision before its merge. V1, V3 to V7 exist on main. V2 is retired: spec 13 lines 120 and 127.
- Still held, not started: the V8 index window (owner decision (ix)); B2, settled by the Navigator as route 1, which the owner may confirm on #41; the DR-11 coupled sites (blocked on an ontology-testfixtures release); the Mockito 5.14.2 bump (waits for the first Mockito test); specs 14 to 19 (wait on spec 13).
- P2 plan sources, in `docs/extraction/spec-13-prototype-stage4-prepare.md`: the P2 row (line 91); section 3, mixed versions (lines 100 to 110, steps 1 to 5); B1 (line 364); B2 (line 366 onward, route 1 is "read the active version; on a stamp mismatch, fail and retry", now "retry, then dead-letter" under DR-23); N7 redelivery, one record per (event id, action index) (line 112); N11, the TransitionFired rows without `self` (lines 108 to 110); section 5.3, the P2 migration (line 183).
- The P2 migration, V8, is `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres/V8__<name>.sql`. Per spec 5.3 it marks the remaining `ActionRequest` rows processed, after their drain (B1), and it marks unprocessed `TransitionFired` rows without `self` processed (N11).
- Gate for P2, on JDK 25. The machine default is Java 21. Run it in one command from the repo root: `source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify`.
- The mixed-version procedure on the dev stack, since there is no non-dev environment: stop writes; stop the relays and the Temporal worker; run O-3 on the stopped dev database for every claimed kind, and it must show zero; run the running-workflow check for `ActionWorkflow`, and it must show zero; start the new version, which runs V8.
- Next steps: (1) the Navigator approves the P2 plan, from the file list in the spec rows; (2) a read-only reviewer checks that plan; (3) an implementation subagent works on a branch `p2-actions-leave-outbox` from origin/main, in its own worktree, and does not push; (4) a read-only reviewer checks the diff; (5) the gate runs on JDK 25; (6) PR, CI and a squash merge, then an update on #41; (7) P3, P4 and P5b follow in that order, each the same way.
- Guardrails carried forward: `--admin` only for the update rule, with each use recorded, and never to pass a red check. Maven needs `source ~/.bashrc.ext` in the same command. PR #106 is a draft marked do-not-merge: do not touch it. PR #137 was closed by the owner: do not reopen it without the owner's say-so. sequeless-kernel #12 is merged, and before any kernel release PR, check that its label is `autorelease: pending`.
- Worktrees: `/Users/bbartolome/workspace/sequeless-extraction` (branch `ci-pins-workflows-v0.1.3`, merged as #156, safe to remove) and `/Users/bbartolome/workspace/sequeless-handoff` (this PR). Create the P2 worktree from origin/main, not from either of them.
