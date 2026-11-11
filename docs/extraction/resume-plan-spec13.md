# Resume plan: extraction after spec 12 (spec 13 next)

Recorded 2026-11-11 so that another session can continue. Facts are checked at origin/main at the time of writing. Machine-local paths are in the auto-memory note, not here.

## 1. Done and merged

- Spec 11 (#39): sequeless-ontology 0.1.0 released. Six modules in GitHub Packages. #10 (release PR) merged 2026-11-11 03:06Z after #14 (S2b validation API), so 0.1.0 includes S2b. No owner go-ahead for #10 is recorded in the tracker; the admin-merge list records it under the owner's standing direction. sequeless-build #46, #47, #48 merged (sequeless-bom 0.1.3 lists the six at 0.1.0).
- Spec 12 (#40), closed:
  - Draft: #109 (98ffe20).
  - Filter bridge: #110 (c8eb638), then the decimal fix #111 (01cbced). The OWL integer risk is closed by #111 and by RollupCriteriaOwlEndToEndTest in #112.
  - Cut-over: #112 (3328993), squash-merged after a full gate on JDK 25 at 29c2492: all 9 modules, no failures.
  - Tracker: #113 (dbf20e6).
- Gate command (spec 12, section 9): `source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify`.

## 2. Open, not ours to merge

- sequeless-expression #19 (patch release 0.1.1 to 0.1.2): CI approval pending. This is the owner's CI route.
- sequeless-kernel #12: held. The skip-snapshot decision (owner decision iii) is open.
- sequeless-prototype #106: draft, marked "do not merge".

## 3. Spec 13 (#41): state

- Draft: docs/extraction/spec-13-prototype-stage4-prepare.md at 05c3b6a, on branch spec-13-draft. Not yet a PR.
- Reviews: review 1 NOT READY; review 2 NOT READY with blockers B1 to B4 (reports under /tmp/seq-spec13, not in the repo). The 05c3b6a revision applied the review findings.
- Navigator decisions (pair session, checked against the code):
  - B1, V5 backfill timing: option B. Each removed outbox kind is backfilled in the PR that removes it, after the relay drains that kind. V5 backfills only TransitionFired (P2a). Reason: V5 as written marks rows processed that OutboxRelay.DISPATCHABLE_KINDS (lines 46 to 53) still dispatches.
  - B3, where prepare gets its Schema: option A. The host-side use case resolves the Schema via SchemaSource and calls the driven port with (scope, version, Schema). Evidence: OntologyStartupValidator line 64 and IndexPreparingOntologyAdministration line 82 already do this; PostgresQueryStore holds no SchemaSource.
  - B4, search_vector placement: option A. The column (V2 line 19), function (line 54) and trigger (line 68) stay on the Command side. Only the query registries and indexes move to the query Flyway instance. Evidence: they sit on sq_object, and the trigger writes to it. GLOSSARY line 52 says the Query side never changes the Store. The review had classed this as an owner question, so the owner should confirm it.
- OPEN, owner decisions (the plan does not choose them):
  - O-8, Object view: keep state and audit in the Object view and REST JSON (the amended glossary keeps them), or drop them and change the SPA model in the same PR. The navigator leans to drop; the reviewer leans to keep. Cost of drop: BusinessObjectResponse, the SPA model (sequeless-ui/projects/core-api/src/lib/models/index.ts lines 115 to 130), and the wire contract.
  - B2, stamp mismatch: when an outbox row's Ontology version does not match the active version, automation either fails and retries (the navigator's lean, matching DefaultTransitionService lines 325 to 330), or skips and logs. Fail-and-retry has a poison-row risk: the relay has no dead-letter, so one permanently mismatched row blocks every row behind it.
- OPEN, owner checks (not run here):
  - O-3: whether any non-dev database has unprocessed rows of the four removed kinds. Command: `SELECT kind, count(*) FROM sq_outbox WHERE processed_at IS NULL GROUP BY kind;`. Under decision B1 this no longer gates P2a, but it matters before P2 to P4.
  - O-4: whether any Temporal workflows are running. The dev volumes are the only ones the repo names.

## 4. Plan, in order

1. Land this file and the tracker pointer as one docs PR. Done when merged.
2. Revise the spec 13 draft for B1, B3 and B4 as decided above. Keep B2 and O-8 as open owner items, with the leans and costs stated. Opus review until approved. Then open the spec 13 docs PR, marked as waiting on those two owner decisions.
3. Implement the steps that do not depend on B2 or O-8. One PR per step. Each step gets an Opus review before its PR, and a full gate on JDK 25 before merge:
   - P0: gate baseline. No code. Record the result in the PR body.
   - P1: move the outbox types from spi.object to spi.outbox. Mechanical.
   - P2a: stamp the Ontology version on Store events. V5 backfills only TransitionFired. The relay adds TransitionFired to DISPATCHABLE_KINDS. The adapters accept it as a no-op.
   - P7: index separation, per the C5 design in the spec. The search_vector DDL stays on the Command side (B4).
   - P5: the ingress takes Store events, and the relay feeds the recomputer first. Run after P2a.
4. Hold until the owner decides B2 and O-8:
   - P2 (actions leave the outbox) and P3 (timers leave the outbox): both read the active Ontology version, so they depend on B2.
   - P4 (signals become Store Commands): the signal path reads behaviour, so treat it as dependent on B2 until confirmed.
   - P6 (Read and Browse ports, Object view): depends on O-8.
5. After the holds clear, run P2, P3, P4 and P6 in order, each with its own removal of the kind it retires.
6. Then specs 14 to 19. Each one needs its own draft, review and PR. Spec 14 creates sequeless-query; spec 15 creates sequeless-automation; spec 19 creates sequeless-knowledge. Repo creation was approved on 2026-11-06 and happens when each spec starts.

## 5. Process rules (from this run)

- Opus reviewers are read-only and write their reports under /tmp. Haiku agents implement. Work happens in git worktrees, one per branch, never in the main checkout.
- PRs: create and merge are pre-approved for docs and sequeless PRs. Minor and patch releases are pre-approved after checks pass; majors need confirmation. Ruleset changes, CI approvals and the kernel snapshot decision are the owner's.
- Merge pinned to the reviewed head with `gh pr merge --squash --match-head-commit <sha>`. Set the squash subject and body explicitly when earlier commit subjects would overstate the change.
- The navigator is a pair partner for scope questions. It answers in a pair session (navigator_start, set_plan, then decide). Its answers are recorded and checked against the code; owner-level decisions stay with the owner.
