# Handoff

The state a new session needs to continue the extraction. Each run overwrites this file; it does not append. Detail lives in the linked docs, not here.

Recorded with the spec 17 P4 PR (`spec-17-p4-delete-moved`, against `main`). The P4 PR is the last code PR of spec 17. Read `git log origin/main` for the head that follows, and trust that over this file.

## Start a new session

1. Read this file, then `docs/extraction/spec-17-prototype-stage4-cutover.md` (sections 2.1 to 2.4, 9, 11, 12).
2. Check GitHub: `origin/main`, open PRs (the P4 PR, until it merges), and issue #45 (spec 17).
3. Spec 17 is done except the deploy gates and the follow-ups below. Nothing is blocked on code.
4. Navigator decisions are not re-asked. Spec 17 decisions: batch 14, Q1 (the query contract is retained). Sessions `6d7e4ca3fa9447629ab72585842845e3` and `0e5543805193449c8caefabab95e002c` are recorded and not reused.
5. Before any PR or merge, follow "Hazards".

## State

- **origin/main** is the head after the P4 PR is squashed in. Before it, origin/main is `ea8ad60` (spec 17 P3).
- **This PR (P4):** `spec-17-p4-delete-moved`, head SHA recorded in the PR. Commits: `56e46de` (automation copies deleted), `c381b00` (reverse pointers, retained query contract), `c594c88` (query implementations deleted), `68b8924` (ArchitectureTest testkit rule retargeted), then the fix commits on top.
- **Merged units, with PR numbers and squash SHAs:**
  - sequeless-prototype: #222 `27d1619` (spec 17 cut-over doc and BOM 0.2.0), #223 `859a118` (P2, browse consumes the released query side), #224 `ea8ad60` (P3, automation consumes the released adapters).
  - sequeless-query: #21 `83e7dc4`, #22 `3d65428`, #23 `fa90d8e`, #24 `c231973` (subtype union, query-schema-adapter 0.2.3), #25 `0b951eb`, #26 `6386002`.
  - sequeless-build: #67 `986a6ec`, #68 `9d1ace8`, #69 `e08cde6`, #70 `c5fed99`. The `sequeless-bom-v0.3.2` tag is on `c5fed99`. It pins query-schema-adapter 0.2.3 and query-spring-boot-autoconfig 0.3.0.
- **Released, not yet pinned:** query-spring-boot-autoconfig 0.4.0 (dependency-only). A BOM bump to it is a follow-up that needs owner approval.
- **The prototype's Query contract** (`spi.query` ports, `spi.object.Page`, the spi-testkit query contracts and in-memory ports) is retained as the host's contract, not deleted. This is an explicit exception in spec sections 2.4 and 11. The owner must be told.
- **Deletions in P4:** the automation SPI, core and testkit copies and both adapter modules (`56e46de`); the query implementations `PostgresQueryStore` read, browse and prepare with their helpers, and the two covered persistence contract ITs (`c594c88`). The released packages named in spec section 2.4 are the homes.
- **Spec 17 status:** done except the deploy gates (below) and the follow-ups.

## Integration results (P4, one class at a time under the Maven lock)

All 27 EXIT 0, Failures 0, Errors 0. The per-class summary is kept on the machine where the run was made.

App (24): TransitionsEndToEndTest (8), CreateObjectRetryProofTest (1), RecomputeCommitRetryProofTest (1), DerivationPluginEndToEndTest (1), QueryEndToEndTest (15), ObjectsEndToEndTest (8), OntologyEndToEndTest (3), ReasonerSwitchEndToEndTest (1), RollupCriteriaOwlEndToEndTest (1), TypesEndToEndTest (10), WhoAmIEndToEndTest (2), MaterialisedRollupLoadTest (1), IndexPreparationTransactionProofTest (2), AcceptanceCriteriaIT (8), FatJarServesUiAndApiIT (2), OntologyPersistenceAcrossRestartIT (1), ReleasedNullParityIT (5), TimerFailureRecordEndToEndTest (2), TemporalAutomationEndToEndTest (2), AutomationEndToEndTest (4), TemporalTransitionActivationEndToEndTest (1), AutomatedWriteShapeRejectionIT (2), AutomationBeanUniquenessIT (2), ChangeEventFailureRecordIT (1).

Persistence (3): PostgresQueryStoreAggregateContractIT (9), PostgresQueryStoreIndexUsageIT (1), PostgresSearchVectorBackfillIT (1).

## Coverage of the deleted query contract tests

The released `BrowseContract` covers 18 of the prototype's 24 browse tests by name. The other six are covered host-side or by equivalents: `facetCountsOnReferencePropertyResolveDisplayLabel`, `referenceFacetDropsNullLabelBucket` and `referenceFacetTiesOrderByCodePoint` (`ReferenceFacetLabelsTest`, a unit test); `includeDeletedWhenRequestedIncludesSoftDeleted` (deliberately rejected by the host, `ReleasedBrowsePortAdapter.java:65`); `includeDeletedDefaultsToExcluded` (equivalent: `softDeletedObjectsAreNeverReturned`); `scalarFacetDropsNullKeyBucket` (equivalent: `scalarFacetDropsObjectsWithoutAValue`). The commit body of `c594c88` overstated this; spec section 2.4 is the correction.

## Follow-ups (open)

- **sequeless-automation #4:** an upstream failure port. It retires the three recording regressions (activity-level refusals, webhook delivery failures, a final `resolveWebhookAction` failure), spec section 9.
- **Query backlog:** retarget core use cases to query-core types (a P2-sized change; spec section 2.4); a reference-facet label translation batch; explicit-null decoding on both sides (both readers currently fail with the same error); the executor should honour declared cardinality; a cardinality gate on filterable properties; the `typeOf` lookup and the id-only ports, to be carried by a later sequeless-automation release.

## Owner-visible deviations

- Spec 14 decision Q16 is overridden for sort only: a property is sortable when its cardinality max is exactly 1 (spec section 9).
- The query ports are kept as the host contract (Navigator batch 14, Q1), not deleted.
- The H7 shape coercion in the host binding (`ShapeCoercion`) (spec section 10).
- The three failure-recording regressions (sequeless-automation #4, above).

## Untested items

- A `StartToClose` timeout on the final attempt is not recorded by the host wrapper (spec section 9).
- The `setProperty` copy-back of stored non-derived values could be refused for a read-only property; not tested.
- Operator pre-deploy counts, not run (no dev database): unprocessed `TransitionFired` rows without an `ontologyVersion`, which must be 0; object rows holding a bare JSON null or a bare `[]`.
- Deploy gates not run (no dev database on this host): the O-3 unprocessed count, the live Temporal count, and the owner's deploy approval.

## Hazards

- **Never use `--admin`** to get past a red or missing required check.
- **Push with an explicit refspec**, `git push -u origin HEAD:refs/heads/<branch>`, so a branch can never update `main`.
- **Maven needs the GitHub Packages token**, so source `~/.bashrc.ext` in the same command as every `mvn` run. The gate uses JDK 25 (`/opt/sdkman/candidates/java/25.0.3-tem`).
- **The unit gate** is `spotless:apply`, then `verify -Prelease -DskipITs`, under the lock. Partial runs give false failures; merge only on the full gate.
- **Do not put model names** in commits, PR titles or bodies, or any other pushed artifact.
- **Work from a worktree cut from `origin/main`.** The primary checkout may be behind.
- **Machine-local facts** (paths, tokens, environment IDs) stay out of the repo.
