# Spec 17: Prototype stage 4 cut-over, consume query and automation

Issue: [#45](https://github.com/sequeless/sequeless-prototype/issues/45). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#16](https://github.com/sequeless/sequeless-prototype/issues/16) (query shape), [#20](https://github.com/sequeless/sequeless-prototype/issues/20) (automation placement). Tracker: `docs/extraction/remaining-work.md`, row 17. Glossary: `GLOSSARY.md`.

Status: draft, revision 1, written in the first code PR (P1). Facts are checked at prototype `origin/main` `8389ec6`, at `sequeless-query` `origin/main` `7096f55` (tags `query-*-v0.2.1`), at `sequeless-automation` `origin/main` `e3818e9` (tags `automation-*-v0.1.0`), and at the `sequeless-bom-v0.2.0` tag of `sequeless-build` (`0fca7a1`). Where a count depends on a command, the command is named in place. Open Navigator questions are listed in section 10 and are not settled by this draft.

## 1. Identity, stage and dependencies

- Stage 4, cut-over. Spec 17 of 19. The prototype stops carrying the Query and automation code that specs 14 and 15 copied out, and consumes the released artifacts instead.
- Waits for spec 14 ([#42](https://github.com/sequeless/sequeless-prototype/issues/42), `sequeless-query`, closed), spec 15 ([#43](https://github.com/sequeless/sequeless-prototype/issues/43), `sequeless-automation`, closed) and spec 16 ([#44](https://github.com/sequeless/sequeless-prototype/issues/44), closed). Spec 16 deleted `facet-core` from the ontology.
- Branch `spec-17-prototype-stage4-cutover`, based on `origin/main` `8389ec6`.
- Root POM: `sequeless-bom` moves from 0.1.5 to 0.2.0 (`pom.xml` line 64). This is the only BOM edit.

### 1.1 Released artifacts this spec consumes

Read from the `sequeless-bom-v0.2.0` pom (`sequeless-build/sequeless-bom/pom.xml`). Group `org.sequeless` for all of them.

| Artifact | BOM 0.2.0 version | Source of the version |
|---|---|---|
| `query-core` | 0.2.1 | `query-core-v0.2.1` (tag in `sequeless-query`; `origin/main` `7096f55` is the release commit's lineage) |
| `query-schema-adapter` | 0.2.1 | `query-schema-adapter-v0.2.1` |
| `query-postgres-jsonb-adapter` | 0.2.1 | `query-postgres-jsonb-adapter-v0.2.1` |
| `query-spring-boot-autoconfig` | 0.2.1 | `query-spring-boot-autoconfig-v0.2.1` |
| `query-testkit` | 0.2.1 | `query-testkit-v0.2.1` |
| `query-field` | **0.1.1** | `query-field-v0.1.1` (`8585c9c`). The BOM pins 0.1.1, not 0.2.x. |
| `query-field-testfixtures` | 0.2.0 | listed in the BOM; not used by the prototype's code paths checked here |
| `automation-api` | 0.1.0 | `automation-api-v0.1.0` |
| `automation-core` | 0.1.0 | `automation-core-v0.1.0` |
| `automation-inprocess-adapter` | 0.1.0 | `automation-inprocess-adapter-v0.1.0` |
| `automation-temporal-adapter` | 0.1.0 | `automation-temporal-adapter-v0.1.0` |
| `automation-spring-boot-autoconfig` | 0.1.0 | `automation-spring-boot-autoconfig-v0.1.0` |
| `automation-testkit` | 0.1.0 | `automation-testkit-v0.1.0` |
| `filter-core` | 0.1.5 | BOM; `filter-core-v0.1.5` |
| `schema-contract` | 0.2.0 | BOM; `schema-contract-v0.2.0` |

Two version differences from the brief: `query-field` is 0.1.1 in the BOM, and the BOM also moves `kernel-identity`, `kernel-values` (0.1.0 to 0.1.1), `filter-core` (0.1.4 to 0.1.5), `expression-*` and the ontology modules. Each of those is picked up by the BOM bump, and P1's gate checks that nothing else moved.

## 2. Moves

The move is a port into the released repos, not a byte copy (spec 14 section 2.3, spec 15 section 2). The prototype deletes its copies in P4. Until then the copies and the released artifacts coexist, and their packages differ, so they do not clash on a classpath.

### 2.1 Query (`sequeless-query`)

Source: `sequeless/sequeless-prototype` at `48a0cc6` (the prototype's `origin/main` at the start of spec 14). The list is the one in `sequeless-query` `PROVENANCE.md`, section "Moved from sequeless-prototype at 48a0cc6". Its 23 named paths are all present in the prototype at `8389ec6`, and none changed since `48a0cc6`.

| Prototype path | Released home | Move |
|---|---|---|
| `sequeless-spi/.../spi/query/{BrowsePort,ReadPort,IndexPreparationPort,Query,QueryResult,FacetBucket,ObjectView,ReadRequest,Sort,Direction}.java` | `query-core`, `org.sequeless.query.spi` and `org.sequeless.query.api` | Ported |
| `sequeless-spi/.../spi/object/{Page,Audit,PropertyRef,TypeRef}.java` | `query-core`, `org.sequeless.query.api` | Ported. `Page` is Query-side only and leaves in P4. `Audit`, `PropertyRef` and `TypeRef` keep a Command-side copy in `spi.object` (decision Q1, section 10), so the duplication is deliberate and their prototype copies are not deleted. |
| `sequeless-spi-testkit/.../testkit/query/{BrowseContract,ReadContract,ObjectViews,QueryFixtures}.java` | `query-testkit` | Ported |
| `sequeless-spi-testkit/src/test/.../testkit/query/{InMemoryBrowsePort,InMemoryReadPort}.java` | `query-testkit` | Ported |
| `sequeless-adapter-persistence-postgres/.../PostgresQueryStore.java` (`query`, `ensureIndexes`, `castExpression`) | `query-postgres-jsonb-adapter`, split into `PostgresBrowsePort`, `PostgresReadPort`, `PostgresIndexPreparation` | Ported and split |
| `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres-query/V1__index_registry.sql` | `query-postgres-jsonb-adapter`, same path | Copied unchanged |
| `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres-query/V2__search_gin_index.sql` | `query-postgres-jsonb-adapter`, same path | Copied unchanged |

Not moved, and staying in the prototype: `Criterion`, `Operator`, `AggregateRequest`, `AggregateResult`, `QueryPort`. Aggregates stay on the Command side. `AggregateContract` and `InMemoryAggregateContractTest` in the prototype are not on the list.

**Import commit (to confirm).** The released repo's PROVENANCE names the prototype source SHA, not the import commit. The code commits that carry the ported paths are `072a6a3` (query-core ports, use cases), `6ef2b78` (`query-postgres-jsonb-adapter`), `1d59e5f` (read port with contract) and `6060d76` (query-testkit contracts). P4 names the commit that first carries each path, and this list is checked with `git log --diff-filter=A` before P4.

### 2.2 Automation (`sequeless-automation`)

Source: `sequeless/sequeless-prototype` at `48a0cc6`. Import commit: `5159284` in `sequeless-automation` (`feat: move the automation runtime into sequeless-automation (spec 15, #43) (#2)`). The list is spec 15 section 2 in `sequeless-automation/docs/extraction/spec-15-sequeless-automation.md`.

| Prototype group | Released home | Move |
|---|---|---|
| `spi/automation`: `AutomationPort`, `ActionExecutor`, `TriggerEvaluator`, `ObjectChangedEvent`, `TransitionFiredEvent`, `StoreEvent`, `ResolvedTimer`, `ResolvedWebhookRequest`, `TimerScheduledEntry`, and their tests | `automation-api`, `org.sequeless.automation.api` | Ported (`OutboxEntry` becomes `StoreEventRow`) |
| `core/automation`: `DefaultActionExecutor`, `DefaultTriggerEvaluator` and tests | `automation-core` | Ported |
| `adapter/automation/inprocess`: `InProcessAutomationPort`, `InProcessTimerScheduler`, `InProcessAutomationProperties`, `WebhookDeliveryException`, `InProcessAutomationAutoConfiguration` | `automation-inprocess-adapter`, `automation-spring-boot-autoconfig` | Ported |
| `adapter/automation/temporal`: `TemporalAutomationPort`, the workflows and activities, `TemporalActivityOptions`, `TemporalAutomationProperties`, `TemporalAutomationAutoConfiguration` | `automation-temporal-adapter`, `automation-spring-boot-autoconfig` | Ported |
| `testkit/automation`: `AutomationContract`, `RecordingActionExecutor`, `RecordingTriggerEvaluator` | `automation-testkit` | Ported |

**The list is stale.** Spec 15's list was written at `48a0cc6`. These items are in the prototype's moved directories and on no list:

- `spi/automation/AutomationFailure.java` and `AutomationFailureRecorder.java`, with `PostgresAutomationFailureRecorder` in the persistence adapter.
- The temporal failure activities: `ActivityFailureCauses`, `AutomationFailureActivities` and `AutomationFailureActivitiesImpl`.
- Migration `V12__automation_failure.sql`, which creates `sq_automation_failure`.
- Tests and fixtures added after `48a0cc6`: `InProcessAutomationPortRedeliveryTest`, `InProcessTimerSchedulerTest`, `ChangeEventWorkflowTest`, `FailureRecordReplayTest`, `TimerWorkflowFailureRecordTest`, `ActivityFailureCausesTest`, and the fixtures `r2-histories/*.json` and `timer-histories/timer-activity-fails-post-p3.json`, `timer-fires-post-p3.json`.

The released automation repo has no failure port or failure activities (verified against `origin/main` `e3818e9`). Failure recording is therefore a gap at the cut-over. Section 10 asks the Navigator how it is handled. This spec does not drop `sq_automation_failure`. Its rows stay where they are.

**Stays in the prototype (host code):** `spi/automation/DerivationRecomputer.java` (in store-core, spec 15 section 2 and R7); `sequeless-app/.../automation/OutboxRelay.java` and `StoreEvents.java`, with their tests; and the host bindings of `ObjectReadPort` and `StoreCommandPort`, which are new work in this spec (section 3).

### 2.3 Deletion commit (P4)

P4 deletes the moved copies in one commit. The deletion list is section 2.1 and 2.2, minus three exceptions settled by decision Q1 (section 10): `spi/object/Audit.java`, `PropertyRef.java` and `TypeRef.java` stay in the prototype as Command-side value types. `Page.java` is deleted, because every code user of it is on the Query side. The commit message names `sequeless/sequeless-automation` and `5159284`, and names `sequeless/sequeless-query` with its confirmed import commit (section 2.1). The commit also adds reverse pointers in the prototype: the `docs/extraction/` note, and a comment in each host file that now imports the released package. Nothing the list does not name is deleted.

## 3. Prerequisite refactors

None, as in specs 14 and 15: no pre-factor PR is opened. Spec 13 P6a and P6b (`ReadPort`, `BrowsePort`, the index-preparation port and the Object view in `spi.query`) are on `origin/main` and are the surface the cut-over replaces.

Host work this spec adds, which is not a prerequisite refactor:

- **Host bindings of the new automation ports.** `org.sequeless.automation.api.ObjectReadPort` and `StoreCommandPort` have no prototype implementation (spec 15 H12). The host binds them. How they route, through the Command use cases or straight to the store, is an open Navigator question (section 10).
- **Host beans the released autoconfigs require.** `SchemaSource` (`schema-contract`), `BehaviourSource` and `AutomationSource` (`behaviour-contract`, `automation-contract`), `ExpressionPort` (`expression-api`), `ObjectReadPort`, `StoreCommandPort`, `DataSource`. The query autoconfig gates on `SchemaSource` and `DataSource`. The automation core gates on `ObjectReadPort`, `StoreCommandPort`, `ExpressionPort` and `BehaviourSource`. Whether the prototype already supplies each one is checked in P2 and P3.

## 4. Package renames

Renames happen in the released repos, in place (spec 8 section 4, spec 15 section 3). Consumers change their imports in this spec. There is no bridge package.

| Prototype | Released |
|---|---|
| `org.sequeless.spi.query.*` (ports, `Query`, `QueryResult`, `ObjectView`, `ReadRequest`, `Sort`, `Direction`, `FacetBucket`) | `org.sequeless.query.spi` (ports) and `org.sequeless.query.api` (types, `BrowseQuery`, `BrowseResult`) |
| `org.sequeless.spi.object.{Page,Audit,PropertyRef,TypeRef}` | `org.sequeless.query.api.{Page,Audit,PropertyRef,TypeRef}` |
| `org.sequeless.adapter.persistence.postgres.PostgresQueryStore` | `org.sequeless.query.postgres.{PostgresBrowsePort,PostgresReadPort,PostgresIndexPreparation}` |
| `org.sequeless.spi.automation.*` | `org.sequeless.automation.api.*` (`OutboxEntry` becomes `StoreEventRow`) |
| `org.sequeless.core.automation.*` | `org.sequeless.automation.core.*` |
| `org.sequeless.adapter.automation.inprocess.*` | `org.sequeless.automation.inprocess.*` |
| `org.sequeless.adapter.automation.temporal.*` | `org.sequeless.automation.temporal.*` |
| `org.sequeless.testkit.query.*` | `query-testkit` packages (the released testkit's package names, confirmed in P2) |

Two signature changes come with the packages, and they are behaviour changes, not renames (section 9): `BrowsePort.browse` takes `(FieldScope, BrowseQuery)` and returns `BrowseResult`, and `IndexPreparationPort.prepare` returns an `IndexPreparationResult`, not a boolean.

Name collisions to keep: the released query code imports filter's `Field` by its full name (spec 14 section 10). The ontology contract is `org.sequeless.ontology.automation.{api,spi}`, and its simple names repeat in automation (`Action`, `WebhookAction`).

## 5. Modules and their dependencies

Prototype modules this spec changes:

| Module | P2 (query) | P3 (automation) | P4 (delete) |
|---|---|---|---|
| `sequeless-spi` | Query types leave. `PropertyRef`, `TypeRef` and `Audit` keep a Command-side copy (decision Q1). The Query side's mapping to the released types is in the REST mappers | Automation SPI leaves | Moved packages deleted, minus the Q1 exceptions |
| `sequeless-spi-testkit` | Query contracts leave | Automation contracts leave | Moved contracts deleted; the prototype's `AggregateContract` stays |
| `sequeless-core` | Use cases call `QueryService` | Uses the released `ActionExecutor` and `TriggerEvaluator` | `core/automation` deleted; `DerivationRecomputer` and the derivation planner stay |
| `sequeless-adapter-persistence-postgres` | `PostgresQueryStore` replaced by the released Postgres ports; `PostgresMigrations` keeps running the `postgres-query` location | Failure recorder stays (section 10) | Moved copies deleted; `V1` and `V2` in `postgres-query` deleted from the prototype (the released jar supplies the same files) |
| `sequeless-adapter-automation-inprocess` | Not touched | Module replaced by the released artifact | Module deleted |
| `sequeless-adapter-automation-temporal` | Not touched | Module replaced by the released artifact | Module deleted |
| `sequeless-app` | REST mappers for Object views and `BrowseResult`; `CoreConfiguration` beans | Host bindings; relay maps rows to `StoreEventRow`; `CoreConfiguration` beans | Imports and wiring updated; `ArchitectureTest` rules retargeted |

Dependency versions come only from the BOM. Module POMs carry no version for the released artifacts.

## 6. Release config

- The prototype is not released. Its root version stays `0.1.0-SNAPSHOT`, and no artifact is published from it.
- No SNAPSHOT is a dependency of the prototype on `main`. Every released artifact comes from the BOM at a released version.
- The BOM bump (P1) and the released artifacts are the only release-side change. The released artifacts' releases are done: query 0.2.1, automation 0.1.0, BOM 0.2.0.
- The prototype's own `codeql.yml` caller grant is an open item in `remaining-work.md`. It is checked before the prototype's pin moves.

## 7. CI and workflows

- The prototype's CI is unchanged. Its pin to `sequeless-build` does not move in this spec.
- The gate is JDK 25 `-Prelease verify` on the worktree, under `flock -w 1800 $HOME/.mvn-build.lock`, with `MAVEN_OPTS=-Xmx1g` and `-T 1`. Maven needs the GitHub Packages token: `source ~/.bashrc.ext` in the same command. Unit tests run in `verify`; integration tests (failsafe) run one class at a time.
- Testcontainers reach the host through `host.docker.internal`. Temporal starts only for a test that needs it.
- The prototype's CI runs against released artifacts. A SNAPSHOT on `main` is a failure.

## 8. ADRs

- Query side (`sequeless-query/docs/adr`): ADR 0001, the Query side sees the Schema only through the Field contract; ADR 0002, index preparation is a host-called `prepare` over the resolved Fields; ADR 0003, the Field contract depends on kernel-identity and `Principal` folds into it.
- Automation side (`sequeless-automation/docs/adr`): ADR 0001, the Store's outbox holds only Store events, and automation owns Side-effect durability; ADR 0002, automation reads and writes through its own ports.
- Prototype: the cut-over changes the prototype's runtime behaviour (section 9). Decision record DR-28 is the next free number (`docs/architecture/02-decision-records.md`). Whether it is written in P2 or in the handoff is open.

## 9. Behaviour and risk

This cut-over changes the prototype's runtime behaviour. The differences are the ones the released specs record, and the released contracts are the reference:

- **Criterion to FilterNode.** The Browse filter is a `FilterNode`. The prototype's `Criterion` and `Operator` remain for aggregates (`QueryPort`). The released filter has no `like` or `not like` (`InvalidQueryException`). The prototype's operators (`EQ`, `NE`, `IN`, `LT`, `LTE`, `GT`, `GTE`, `CONTAINS`, `STARTS_WITH`, `IS_NULL`, `NOT_NULL`) map to filter-core operator names, and the values become `JsonNode`. Section 10 asks how the REST aliases map.
- **DATE comparisons and tenant-wide text search.** The cycle brief names both as runtime changes. They are not yet checked against the released Postgres adapter's SQL, so P2 must check them with the released query contracts before the cut-over merges.
- **Display labels and operator aliases.** Changed by the released query and REST layer. The REST wire format must stay wire-compatible with the hand-written TypeScript models for Object views and `BrowseResult` (DR-20).
- **Sorting and facets.** Core rejects an unsortable sort and an unfacetable facet with `InvalidQueryException` before the port runs. Facet keys are `FieldPath`s, not IRIs. A sort key on a relationship orders by target id.
- **`prepare`.** Returns `IndexPreparationResult.EMPTY` without calling the port when the version is superseded (query ADR 0002).
- **Unstamped `TransitionFired` rows (spec 15 D-2).** The released core throws when a command needs provenance and the row has no `ontologyVersion`. The prototype omitted the key. A legacy row therefore dead-letters after the cut-over. Section 10 asks how this is handled.
- **Authorization (spec 15 H7).** Store-side validation runs on automation's writes, so some actions the prototype accepted may now be rejected. The cut-over's tests cover the rejections.
- **Failure recording (section 2.2).** The released automation does not record automation failures. After the cut-over the prototype's `sq_automation_failure` stops receiving rows unless the host records them.
- **Divergence (spec 15 D-1).** The prototype and the released artifacts differ in behaviour until the cut-over (P2 and P3). Each step's gate names the behaviour it checks.
- **Limitation: no deploy gate.** No dev database exists on this host. The deploy gates (the O-3 unprocessed count, the live Temporal count and the owner's deploy approval) cannot run here. This spec records that as a limitation, and no deploy is claimed.

## 10. Open Navigator questions

Settled by the Navigator's `set_plan` answer, in session `0e5543805193449c8caefabab95e002c`: the plan P1 to P5 (section 1 and the order of this spec); P1 is the first code PR only if the bump alone builds green, otherwise it folds into P2; the import SHA for automation is `5159284`; failure recording is host-owned, with `V12` untouched.

Settled by the Navigator (session `0e5543805193449c8caefabab95e002c`):

- **Q1, Command-side value types, option (b).** The Command side keeps its own `PropertyRef`, `TypeRef` and `Audit` in `org.sequeless.spi.object`. Only the Query side moves to `org.sequeless.query.api`. The two sets are mapped at the host boundary: in the P2 REST mappers and in the P3 `ObjectReadPort` and `StoreCommandPort` bindings, in small tested mapper classes. The duplication is reconsidered when a third consumer appears or a shared value-type module exists upstream. `Page` is Query-side only and leaves in P4.

Open, to be asked in batches of at most five (Q2 to Q5):

2. **Failure recording.** The released automation has no failure port. Options: (a) the host keeps `AutomationFailureRecorder` and calls it from its own wrapper of the released temporal adapter; (b) the failure rows stop being written at the cut-over, and the table is kept untouched; (c) a failure port is added to `sequeless-automation` first, as a separate release.
3. **Host bindings of `ObjectReadPort` and `StoreCommandPort`.** Options: (a) the host binds them to the Command use cases, so validation and authorization run as they do for the HTTP path; (b) the host binds them straight to `ObjectStorePort`, which skips validation.
4. **Filter operator and REST alias mapping.** The prototype's `Operator` set and the filter-core operator names differ, and the released filter rejects `like`. Options: (a) keep the REST alias set, and translate it to the released operator names in the host, so the wire format does not change; (b) change the REST aliases to the released names.
5. **Legacy `TransitionFired` rows and V13 (spec 15 D-2).** Options: (a) a V13 migration backfills `ontologyVersion` on existing rows, if a version can be derived; (b) accept the dead-letter of legacy rows, and gate the cut-over on a count of them; (c) the host stamps only new rows and dead-letters are re-driven by hand.

## 11. Definition of done (issue #45)

- The new repos' CI is green. This is done for `sequeless-query` (releases 0.2.1) and `sequeless-automation` (0.1.0).
- The prototype's CI is green against released artifacts, with no SNAPSHOT on `main`. Checked at P1 and at the last code PR.
- The BOM's CI, which runs every testkit contract, is green. The BOM is released at 0.2.0 (`sequeless-bom-v0.2.0`).
- The release and the BOM re-release are done for the released repos. The prototype is not released.
- For cut-over specs, the prototype deletes the moved code in a commit that names the target repo and the import SHA. That commit is P4 (section 2.3).

## 12. Verification

Each step runs, before its PR is opened:

- P1: `spotless:check` and the JDK 25 unit gate with `-DskipITs`, then `dependency:tree` to confirm the BOM resolves every consumed artifact at a released version. Each integration test class named in section 7 is run alone, or recorded as not run.
- P2 and P3: the unit gate, a wire-format contract test for Object views and `BrowseResult` (P2), and a trigger-to-action test with in-process fakes (P3). Each integration test class is run alone under the Maven lock.
- P4: the unit gate, a grep showing no dangling import of a deleted package, and the integration tests run alone.
- P5: the handoff, `docs/agents/handoff.md`, in the last code PR.
