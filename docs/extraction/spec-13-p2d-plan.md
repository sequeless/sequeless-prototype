# Spec 13, P2d: the flip and V8 (plan)

Status: revision 3. It takes in the read-only Opus review of revision 2 (verdict REWORK). Its line claims were checked against the code, and the two blocking items are fixed in section 8. Base: `origin/main` `60f87fb`. Tracker: [#41](https://github.com/sequeless/sequeless-prototype/issues/41). Scope decisions M1 (a) to (d) are from the navigator session `1bf3726d9cb34bfea35f455da8703742`. Items marked **OPEN** are not decided; they go to the navigator before code. Resume plan section 18 lists the 2d items; this file supersedes that list where they differ.

## 1. Scope

**In P2d** (the single flip and the writer stop):

1. **Temporal routes TransitionFired.** In `TemporalAutomationPort.dispatch`, the case at line 92 becomes `case OutboxEntry.KIND_TRANSITION_FIRED -> startTransitionFiredActions(scope, entry);` (line 142, package-private). It no longer is `-> {}`.
2. **In-process routes TransitionFired.** In `InProcessAutomationPort.dispatch`, the if-block at lines 126 to 129 keeps its position before the `dispatched` guard at line 131. Its body becomes `dispatchTransitionFired(scope, entry); return;` (line 174). The `return` is required: without it the row reaches the switch default and throws `IllegalArgumentException`. The position matters because `dispatched.add` runs before the work. A SetProperty or CreateObject that rolled back would be skipped on a retry. The per-(entry id, action index) redelivery record inside `dispatchTransitionFired` is the right record.
3. **The writer stops.** In `DefaultTransitionService`, lines 350 to 355 (the `actions` list and the per-action loop that calls `actionRequestEntry`) are removed. Then this becomes dead and is removed too, if nothing else calls it:
   - `actionRequestEntry` (lines 476 to 488);
   - `actionRequestPayload` (from line 499), and `createPropertiesPayload` (lines 542 to 551), which only `actionRequestPayload` calls (line 526);
   - the imports at lines 28 and 30 to 35 that lose their use. Remove only those that no longer compile-use, and let the javadoc changes in section 4 settle the rest.
   - Stays: the TransitionFired write at line 349, with its `self`, `typeIri`, `machineIri` and `ontologyVersion` fields; and the automation read and version check at lines 326 to 331.
4. **V8 migration**, section 3.
5. **Stale text and tests**, sections 4 and 5.

**Kept, not removed in P2d** (the reason is **OPEN**, Q-B):

- `KIND_ACTION_REQUEST` in `OutboxRelay.DISPATCHABLE_KINDS` (`sequeless-app/src/main/java/org/sequeless/app/automation/OutboxRelay.java`, line 56).
- The `ActionRequest` case in `TemporalAutomationPort.dispatch` (line 85), with `startActionWorkflow` (lines 97 to 105).
- The `ActionRequest` case in `InProcessAutomationPort.dispatch` (line 136), with `dispatchActionRequest` (lines 146 to 159).
- `ActionWorkflow` and its registration.

The navigator's stated reason was "so the relay can drain the pre-flip ActionRequest rows before V8 runs". That reason does not hold. V8 runs inside the first `getConnection` (`PostgresMigratingDataSource` lines 40 to 43), before any claim, and the drain is done by the old version (section 8). So on a migrated database the kept ActionRequest cases never receive a row. A reason that does hold: keeping them lets the hand-built ActionRequest tests pass unchanged, which keeps P2d small. Those tests are `AutomationContract` lines 103 to 161, `TemporalAutomationEndToEndTest`, `OutboxRelayTest` and `PostgresOutboxPortIT`. If Q-B removes the kept cases, `AutomationContract.redispatchWithSameEntryIdIsIdempotent` (line 147) is the idempotency contract, and it then needs a TransitionFired form, not just a deletion. Spec line 91 (the P2 row) removes these items in P2, so keeping them is a deviation from the spec. The navigator decides whether to keep them with that reason, or remove them in P2d and update those tests. **OPEN (Q-B).**

## 2. Base and gate

- Branch from `origin/main` `60f87fb`, not `6601efc`.
- Gate, in a fresh worktree: `source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify`. A test-only run misses the dependency analysis.

## 3. V8 migration

File: `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres/V8__<short-name>.sql`. The backfill precedent is V5 lines 8 to 10, an `UPDATE` on TransitionFired. V3 has no `UPDATE`, so do not cite it. `sq_outbox.payload` is `jsonb` (V1 line 48).

```sql
-- B1: ActionRequest rows are drained by the old version before this runs (section 8).
-- This statement only stamps any that remain, including dead-lettered ones (V7).
UPDATE sq_outbox SET processed_at = now()
 WHERE kind = 'ActionRequest' AND processed_at IS NULL;

-- Written before #158, without a `self` key. Their actions are their ActionRequest siblings,
-- which the drain ran (B1). Stamping them keeps the new relay from claiming them.
UPDATE sq_outbox SET processed_at = now()
 WHERE kind = 'TransitionFired' AND processed_at IS NULL AND payload -> 'self' IS NULL;
```

V8 stamps only these two groups. It does not stamp a TransitionFired row that has `self`, nor a row of any other kind. `payload -> 'self' IS NULL` avoids the `?` operator, which JDBC reads as a bind placeholder in a `PreparedStatement`. For an object payload it equals `NOT (payload ? 'self')`. Rows written after #158 always carry `self` (`selfPayload` returns a map, possibly empty, never SQL NULL), and rows written before it do not.

**IT.** Add `PostgresOutboxV8MigrationIT`, modelled on `PostgresOutboxV5MigrationIT`. Run `migrateTo("7")` (`migrateTo` is package-private, `PostgresMigrations` line 62), insert rows, migrate to V8, and assert:

- ActionRequest unprocessed becomes processed;
- ActionRequest dead-lettered becomes processed;
- ActionRequest already processed is unchanged;
- TransitionFired with `self` is unchanged;
- TransitionFired without `self` becomes processed.

Write the IT's SQL with `payload -> 'self'`, not `?`, for the same reason.

## 4. Stale text to update in the same PR

Javadoc and comments that describe the dormant or no-op behaviour:

- `TemporalAutomationPort`: class javadoc (about lines 27 to 28, "dormant, and dispatch does not reach it yet"); the comment at line 91 ("carries no action or timer yet"); the javadoc at lines 129 to 130 ("Dormant: dispatch does not call this method yet").
- `InProcessAutomationPort`: class javadoc lines 29 to 33, which say that `dispatch` runs an ActionRequest's action (the TransitionFired sentence is not here); the TransitionFired "is accepted and does nothing here" sentence at lines 70 to 72; the `dispatchTransitionFired` javadoc at lines 161 to 164.
- `OutboxRelay` javadoc lines 26 to 27 ("accept ... as a no-op").
- `OutboxPort` javadoc lines 18 to 20 ("accept it as a no-op").
- `AutomationPort` (`sequeless-spi/src/main/java/org/sequeless/spi/automation/AutomationPort.java`):
  - class javadoc lines 9 to 10, which say only ActionRequest "applies a fired transition's action";
  - lines 41 to 42, "An `OutboxEntry#KIND_TRANSITION_FIRED` entry is accepted and does nothing". Change it to: TransitionFired applies the transition's actions, and throws `IllegalStateException` on a missing or mismatched `ontologyVersion` stamp;
  - the idempotency sentence at lines 29 to 31 ("must not cause the underlying inbound-port method to be invoked a second time"). Add a TransitionFired carve-out: the in-process adapter runs SetProperty and CreateObject again on a repeat call (N7).
- `ActionExecutor` (`sequeless-spi/src/main/java/org/sequeless/spi/automation/ActionExecutor.java`), class javadoc around lines 17 to 24, which describes only ActionRequest-kind entries. Rewrite it to cover the drain path and the per-action path that reads a TransitionFired row.

Javadoc that describes the writer:

- `TransitionService` (`sequeless-core/src/main/java/org/sequeless/core/api/TransitionService.java`) lines 39 to 42: "one `ActionRequest` outbox event per action".
- `DefaultTransitionService`: the class javadoc at lines 71 to 75 (the reviewer reads 72 to 75 as the span; implementer confirms), and the `commitTransition` javadoc at lines 304 to 306.

Docs, `docs/architecture/automation.md`, all of which describe the pre-flip behaviour:

- lines 21 to 22 ("both automation adapters accept it as a no-op");
- lines 44 to 46 (the TransitionFired key line, "no-op until it carries actions or timers");
- line 66 (adapters treat TransitionFired as a no-op);
- lines 73 to 76 (the TransitionFired payload key list; add `ontologyVersion`, `self`, `typeIri`, `machineIri`);
- line 78, the heading "`ActionRequest` payload (built by `DefaultTransitionService.actionRequestPayload`)", which names a method this PR deletes;
- lines 184 to 186 (in-process "runs an `ActionRequest`'s action");
- lines 197 to 200 (Temporal describes only `ActionWorkflow`);
- lines 265 to 270 (idempotency per entry id; TransitionFired is per (id, index) after the flip);
- line 451 (`fire()` writes one ActionRequest per `sq:action`);
- line 512 (the diagram's ActionRequest step);
- lines 524 to 526 (both adapters accept TransitionFired as a no-op).

Say that `ActionRequest` rows still exist until the drain completes.

Test comments:

- `TransitionsEndToEndTest` lines 198 to 199 ("the ActionRequest outbox row this transition wrote");
- `TemporalTransitionActivationEndToEndTest` lines 173 to 176 (the comment ends at 177);
- the class javadocs that say "dormant" in `InProcessAutomationPortTransitionFiredTest` (line 35) and `TemporalAutomationPortTransitionFiredTest` (line 44).

Tracker: `docs/extraction/remaining-work.md` "Next actions" item 0 points at section 18 (done on the plan branch). The same PR records the admin merges and the Renovate merges in the admin-merge list (done on the plan branch).

## 5. Tests

**Shared contract, which breaks after the flip:**

- `AutomationContract.dispatchAcceptsTransitionFiredAsANoOp` (`sequeless-spi-testkit/src/main/java/org/sequeless/testkit/automation/AutomationContract.java`, line 169). Its row has no `ontologyVersion`. After the flip both adapters throw `IllegalStateException` here. Replace it with `dispatchRejectsTransitionFiredWithoutOntologyVersionStamp`, which expects `IllegalStateException`. Both adapters check the stamp before they use the automation, so the test works for each. The Temporal source returns null after `reset()`, and the in-process source returns an empty v1 automation; neither reaches the action path.

**In-process:**

- `InProcessAutomationPortTransitionFiredTest` calls the package-private `dispatchTransitionFired` directly, so no test goes through `dispatch`. Add one unit case through `dispatch`: a stamped TransitionFired row whose automation has one action is applied once. Without the `return` in section 1 item 2 it would throw `IllegalArgumentException`. `TransitionsEndToEndTest` (inprocess, line 61) also covers the return.

**Temporal:**

- `TemporalAutomationPortContractTest.dispatchDoesNotStartTransitionActionWorkflows` (about line 309) asserts that `<id>:0` does not exist, so it fails after the flip. Its javadoc (lines 301 to 307) says the shared row "would keep passing after the dispatch flip", which is wrong. Turn the test around into a per-action test: `<id>:0` exists and completes.
- The `port()` wrapper's default branch (about lines 183 to 186) must wait for `<id>:<i>` workflows. It learns how many to wait for from the automation the test set: `SwappableAutomationSource.current.actions(new TransitionRef(...))` gives the count of actions.
- `SwappableActionExecutor` (the test helper, about line 400, test code only) has no `applyAction` or `resolveWebhookAction` override, and the defaults throw `UnsupportedOperationException`. Add the delegates. This is resume plan D9.

**Core writer:** `sequeless-core/src/test/java/org/sequeless/core/usecase/DefaultTransitionServiceTest.java`:

- line 313, `fireCommitsUpdateMovingStateAndDispatchesTransitionFiredPlusOneActionRequestPerAction`: assert that no ActionRequest row is written, and rename it. Its `hasSize(5)` at line 335 becomes `hasSize(1)`.
- line 398, `fireTransitionFiredCarriesSelfTypeIriAndMachineIriLikeTheActionRequests`: it compares with the ActionRequest `self`. Rewrite it to assert on the TransitionFired row alone.
- line 420, `fireActionRequestOmitsAbsentOptionalFieldsRatherThanNull`: delete it.
- line 723, `fireAutomatedFiresNormallyMovingStateAndDispatchingTransitionFiredPlusActionRequest`: `hasSize(2)` at line 741 becomes `hasSize(1)`, and the ActionRequest kind assertion at line 743 goes.

**Other tests** that assert ActionRequest writes (check each; keep those that exercise the drain path of ActionRequest dispatch if Q-B keeps the cases): `OutboxEntryDataConverterRoundTripTest`, `DefaultActionExecutorTest`, `InProcessAutomationPortWebhookTest`, `TemporalAutomationEndToEndTest`, `OutboxRelayTest`.

**End-to-end proof:** `TransitionsEndToEndTest` and `TemporalTransitionActivationEndToEndTest` must pass. Their comments change (section 4).

**V8 IT:** section 3.

**Not in P2d:** the eight items of resume plan section 17 ("Deferred to step 2e").

## 6. PR body

- The five scope items in section 1, and the kept items with the reason for each. The reason is **OPEN** until Q-B is answered.
- Resume plan section 17, item 8: the derived create id under the TransitionFired id differs from the ActionRequest id. This is safe only because of the drain gate in section 8.
- The mixed-version procedure in section 8 is operational. This PR does not run it.
- Squash merge, pinned to the reviewed head with `--match-head-commit`.

## 7. Merge timing

**OPEN (Q-C).** Merging this PR puts V8 on `main`, and the next start of the app against any database runs V8. The navigator's answer on whether the merge waits for the section 8 procedure is pending in navigator task `kft7f4pw4`.

## 8. Operational procedure (not part of the PR)

Copied from spec 13 section 3, "Mixed versions at P2" (`docs/extraction/spec-13-prototype-stage4-prepare.md` lines 100 to 110), and resume plan sections 16 and 18. P2 deploys without overlap: no old and new relays run together.

Facts this depends on:

- V8 runs inside the first `getConnection` (`PostgresMigratingDataSource` lines 40 to 43), before any claim. So the new version never drains a pre-flip row. The old version drains.
- `OutboxRelay.pollOnce` returns without polling when no `AutomationPort` is configured (lines 120 to 122). A drain needs the old version to run with its automation adapter set. No YAML file in the repo sets `sequeless.automation.adapter`, so the dev stack's setting is for the owner to confirm.

Steps:

1. **Stop writes.** Block inbound writes. The relays keep running, so they can drain. The mechanism is the owner's. Before this, run O-3 (step 3's query) to size the backlog. If the dev database ran without an automation adapter, its rows are unprocessed and the drain would replay old events through the old code. The owner then decides whether to drain or accept the loss in writing.
2. **Stop every process that runs the old version**: its relays, its timer sweep and its Temporal workers. No old relay or old pod can then write or claim any row. On the in-process adapter this drops the pending timers, which live in memory and are not recovered after the stop (`InProcessTimerScheduler` javadoc, lines 19 to 22). For that adapter the owner accepts the loss in writing, or the step waits until those timers have fired.
3. **Run O-3 on the stopped database** for every kind the old relay claims, including TransitionFired. Each claimed kind must show zero. The query is `SELECT kind, count(*) FROM sq_outbox WHERE processed_at IS NULL GROUP BY kind;`. Also run the running-workflow check for `ActionWorkflow` after the stop, and it must show zero. The spec lets the owner accept loss for dev volumes; this plan requires zero, which is stricter and safe.
4. **If either check is non-zero**, restart the old version with writes still stopped, let it drain those rows and finish those workflows, and repeat steps 2 and 3. The claim skips dead-lettered rows (`PostgresOutboxPort` line 83, `dead_lettered_at IS NULL`), so a dead-lettered row stays unprocessed and the zero check cannot pass on it alone. Two routes, and the navigator chooses (**OPEN, Q-A**):
   - (a) re-arm the row under the old version: set `dead_lettered_at = NULL`, `error = NULL` and `fail_count = 0` (the V7 columns, V7 lines 10 to 13), let it drain, and repeat the checks;
   - (b) the owner accepts the loss in writing for the listed rows. With the old version stopped, stamp them by hand: `UPDATE sq_outbox SET processed_at = now() WHERE id IN (…)`. Then repeat step 3. V8 runs only after step 3 passes, so it cannot clear these rows for step 3; a hand stamp does.
   - Never re-arm a pre-flip TransitionFired row after V8. That would run its actions a second time.
5. **Start the new version**, which runs V8 at startup. Do not start it before steps 1 to 4 complete. Two failures follow if it starts early:
   - V8 stamps every undrained ActionRequest row. Their actions are lost, unless their TransitionFired sibling is also unprocessed and carries `self`. Siblings share `occurred_at`, so the claim order between them is arbitrary;
   - an unprocessed TransitionFired row with `self` whose siblings already ran would run its actions twice.

**Why the zero check on every kind, including TransitionFired.** A zero taken after the old relay stops means no pre-flip TransitionFired row can reach the new code. Stopping writes alone does not rule this out, because the relays keep writing until they stop.

**Temporal.** The drain itself starts `ActionWorkflow` executions (`TemporalAutomationPort.dispatch` starts each one and returns). So the running-workflow check after the stop is part of the gate, and step 4's restart finishes them.

No non-dev database exists (O-3, answered 2026-11-14), so the dev stack is the only target.

## 9. Owner items, unchanged

- Decision (ix), the V8 claim-index window. The held branch `spec-13-dead-letter-index-v8` renumbers to V9, and V9 must never merge before V8 is on `main`.
- The DR-11 coupled sites wait for an ontology-testfixtures release.
- The Mockito 5.14.2 bump waits for the first Mockito test.
