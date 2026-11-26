# Spec 17: Prototype stage 4 cut-over, consume query and automation

Issue: [#45](https://github.com/sequeless/sequeless-prototype/issues/45). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#16](https://github.com/sequeless/sequeless-prototype/issues/16) (query shape), [#20](https://github.com/sequeless/sequeless-prototype/issues/20) (automation placement). Tracker: `docs/extraction/remaining-work.md`, row 17. Glossary: `GLOSSARY.md`.

Status: draft, revision 1, written in the first code PR (P1). Facts are checked at prototype `origin/main` `8389ec6`, at `sequeless-query` `origin/main` `7096f55` (tags `query-*-v0.2.1`), at `sequeless-automation` `origin/main` `e3818e9` (tags `automation-*-v0.1.0`), and at the `sequeless-bom-v0.2.0` tag of `sequeless-build` (`0fca7a1`). Where a count depends on a command, the command is named in place. Open Navigator questions are listed in section 10 and are not settled by this draft.

## 1. Identity, stage and dependencies

- Stage 4, cut-over. Spec 17 of 19. The prototype stops carrying the Query and automation code that specs 14 and 15 copied out, and consumes the released artifacts instead.
- Waits for spec 14 ([#42](https://github.com/sequeless/sequeless-prototype/issues/42), `sequeless-query`, closed), spec 15 ([#43](https://github.com/sequeless/sequeless-prototype/issues/43), `sequeless-automation`, closed) and spec 16 ([#44](https://github.com/sequeless/sequeless-prototype/issues/44), closed). Spec 16 deleted `facet-core` from the ontology.
- Branch `spec-17-prototype-stage4-cutover`, based on `origin/main` `8389ec6`.
- Root POM: `sequeless-bom` moves from 0.1.5 to 0.2.0 (`pom.xml` line 64). Later P2 commits move it to 0.3.2 (section 1.1).

### 1.1 Released artifacts this spec consumes

The table is the BOM 0.2.0 pin, read from the `sequeless-bom-v0.2.0` pom (`sequeless-build/sequeless-bom/pom.xml`) at the start of the spec. Group `org.sequeless` for all of them. The current pin is 0.3.2; see the update after the table.

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

**Update (current pin, BOM 0.3.2).** The root POM pins `sequeless-bom` 0.3.2 (commit `cf68fc5`). The `sequeless-bom-v0.3.2` tag of `sequeless-build` lists `query-schema-adapter` 0.2.3, which carries the subtype union and the sort fix (`sequeless-query` #24, merged as `c231973`; released as `query-schema-adapter-v0.2.3` by release #25, `0b951eb`). It also lists `query-spring-boot-autoconfig` 0.3.0 (`sequeless-query` #23; tag `query-spring-boot-autoconfig-v0.3.0`). The same tag still lists `query-core` 0.2.1 and `query-postgres-jsonb-adapter` 0.2.1. The BOM change is `sequeless-build` #69 (`e08cde6`), with release PR #70 (`c5fed99`). The earlier BOM 0.3.1 pin listed `query-schema-adapter` 0.2.2, which made a single-valued property sortable (`sequeless-build` #67, `986a6ec`; release #68, `9d1ace8`).

Two version differences from the brief: `query-field` is 0.1.1 in the BOM, and the BOM also moves `kernel-identity`, `kernel-values` (0.1.0 to 0.1.1), `filter-core` (0.1.4 to 0.1.5), `expression-*` and the ontology modules. Each of those is picked up by the BOM bump, and P1's gate checks that nothing else moved.

## 2. Moves

The move is a port into the released repos, not a byte copy (spec 14 section 2.3, spec 15 section 2). The prototype deletes its copies in P4. Until then the copies and the released artifacts coexist, and their packages differ, so they do not clash on a classpath.

### 2.1 Query (`sequeless-query`)

Source: `sequeless/sequeless-prototype` at `48a0cc6` (the prototype's `origin/main` at the start of spec 14). The list is the one in `sequeless-query` `PROVENANCE.md`, section "Moved from sequeless-prototype at 48a0cc6". Its 23 named paths are all present in the prototype at `8389ec6`, and none changed since `48a0cc6`.

| Prototype path | Released home | Move |
|---|---|---|
| `sequeless-spi/.../spi/query/{BrowsePort,ReadPort,IndexPreparationPort,Query,QueryResult,FacetBucket,ObjectView,ReadRequest,Sort,Direction}.java` | `query-core`, `org.sequeless.query.spi` and `org.sequeless.query.api` | Ported. **Retained: host contract, not deleted** (Navigator decision, batch 14, Q1; section 2.4). The released `sequeless-query` implements these ports through the host's `ReleasedBrowsePortAdapter`, `ReleasedReadPortAdapter` and `ReleasedIndexPreparationPortAdapter`. |
| `sequeless-spi/.../spi/object/{Page,Audit,PropertyRef,TypeRef}.java` | `query-core`, `org.sequeless.query.api` | Ported. **`Page`: retained, host contract, not deleted** (Navigator decision, batch 14, Q1; section 2.4). Its PageResult and the core's `BrowseQuery` use it. `Audit`, `PropertyRef` and `TypeRef` keep a Command-side copy in `spi.object` (decision Q1, section 10), so the duplication is deliberate and their prototype copies are not deleted. |
| `sequeless-spi-testkit/.../testkit/query/{BrowseContract,ReadContract,ObjectViews,QueryFixtures}.java` | `query-testkit` | Ported |
| `sequeless-spi-testkit/src/test/.../testkit/query/{InMemoryBrowsePort,InMemoryReadPort}.java` | `query-testkit` | Ported |
| `sequeless-adapter-persistence-postgres/.../PostgresQueryStore.java` (`query`, `ensureIndexes`, `castExpression`) | `query-postgres-jsonb-adapter`, split into `PostgresBrowsePort`, `PostgresReadPort`, `PostgresIndexPreparation` | Ported and split. The browse, read and index methods were deleted in the query implementation commit of P4; the class keeps the aggregate `QueryPort` methods (section 2.4). |
| `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres-query/V1__index_registry.sql` | `query-postgres-jsonb-adapter`, same path | Copied unchanged; deleted from the prototype in P2 (`617e2f8`), not P4 (deviation, below) |
| `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres-query/V2__search_gin_index.sql` | `query-postgres-jsonb-adapter`, same path | Copied unchanged; deleted from the prototype in P2 (`617e2f8`), not P4 (deviation, below) |

Not moved, and staying in the prototype: `Criterion`, `Operator`, `AggregateRequest`, `AggregateResult`, `QueryPort`. Aggregates stay on the Command side. `AggregateContract` and `InMemoryAggregateContractTest` in the prototype are not on the list.

**Deviation from spec 14: the V1 and V2 copies are deleted in P2, not P4.** Commit `617e2f8` deletes the prototype's `V1__index_registry.sql` and `V2__search_gin_index.sql` and moves the released `query-postgres-jsonb-adapter` jar from test to runtime scope (`e1c9299`). The reason, as recorded in `617e2f8`: Flyway rejects two copies of one migration at one location, so the prototype's copies cannot stay on the classpath beside the released files. The released files are byte-identical to the copies (checked by byte comparison in `617e2f8`). The released jar and the prototype's `PostgresMigrations` both run this set against the history table `flyway_schema_history_query` (`PostgresMigrations.java` line 34). `PostgresMigrationFrozenSetsTest` pins the file names and checks the frozen copy against the released bytes. Section 2.3 leaves V1 and V2 out of the P4 deletion.

**Import commit (confirmed in P4).** The released repo's PROVENANCE names the prototype source SHA, not the import commits. `git -C sequeless-query log --diff-filter=A origin/main` (fetched in P4) gives the first commit that added each ported file. The query-core ports and api types (`BrowsePort`, `ReadPort`, `IndexPreparationPort`, `Audit`, `Direction`, `FacetBucket`, `ObjectView`, `Page`, `PropertyRef`, `ReadRequest`, `Sort`, `TypeRef`, and `BrowseQuery`, `BrowseResult`) first land in `072a6a3`. The query-testkit contracts and in-memory references (`BrowseContract`, `ReadContract`, `ObjectViews`, `QueryFixtures`, `InMemoryBrowsePort`, `InMemoryReadPort`) land in `6060d76`. `PostgresBrowsePort` and `PostgresIndexPreparation` land in `6ef2b78`, and `PostgresReadPort` in `1d59e5f`. The history is four commits, not one, so the import is this list. `Query.java` and `QueryResult.java` have no file of that name in the released repo (the released side uses `BrowseQuery` and `BrowseResult`). The query deletion, when it lands, names `072a6a3` as the primary query import commit, because it carries the port and type paths that are most of the list, and names the other three commits in its body. The query side is blocked in P4 (section 2.4).

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

P4 deletes the moved copies in one commit. The deletion list is section 2.1 and 2.2, minus V1 and V2 (deleted in P2, section 2.1), minus three exceptions settled by decision Q1 (section 10): `spi/object/Audit.java`, `PropertyRef.java` and `TypeRef.java` stay in the prototype as Command-side value types. `Page.java` is **retained** too, with the spi query ports, as the host's Query contract (Navigator decision, batch 14, Q1; section 2.4). The Query-side ports are not deleted. The commit message names `sequeless/sequeless-automation` and `5159284`, and names `sequeless/sequeless-query` with its confirmed import commit (section 2.1). The commit also adds reverse pointers in the prototype: the `docs/extraction/` note, and a comment in each host file that now imports the released package. Nothing the list does not name is deleted.

**As built in P4.** The automation side is deleted in `56e46de`. The query side keeps its contract (section 2.4). The query implementations that the released adapters replace are deleted in the P4 commit titled "delete the prototype's query implementations that the released adapters replace".

### 2.4 Reverse pointers and P4 status

**Status.** The automation side is deleted in `56e46de` (`Spec 17 P4: delete the moved automation runtime copies`). The query side keeps its contract and deletes only the implementations that the released adapters replace (below).

**Reverse pointers, grouped by target repo.** No deleted package keeps a stub. The `56e46de` commit body names the target repo and the import SHA. The host classes that replace a deleted class carry a one-line javadoc pointer: `org.sequeless.app.automation.StoreEvents`, `FailureRecordingActionExecutor` and `FailureRecordingTriggerEvaluator`.

- `sequeless/sequeless-automation`, import commit `5159284` (verify with `git -C <automation repo> show --stat 5159284`):
  - `sequeless-spi/.../spi/automation/{AutomationPort,ActionExecutor,TriggerEvaluator,ObjectChangedEvent,TransitionFiredEvent,StoreEvent,ResolvedTimer,ResolvedWebhookRequest,TimerScheduledEntry}.java` and tests `StoreEventTest`, `TimerScheduledEntryTest`: `automation-api`, `org.sequeless.automation.api`.
  - `sequeless-core/.../core/automation/{DefaultActionExecutor,DefaultTriggerEvaluator,package-info}.java` and their two tests: `automation-core`, `org.sequeless.automation.core`.
  - `sequeless-spi-testkit/.../testkit/automation/{AutomationContract,RecordingActionExecutor,RecordingTriggerEvaluator,package-info}.java`: `automation-testkit`, `org.sequeless.automation.testkit`.
  - `sequeless-adapter-automation-inprocess/` (module): `automation-inprocess-adapter`, `org.sequeless.automation.inprocess`.
  - `sequeless-adapter-automation-temporal/` (module): `automation-temporal-adapter`, `org.sequeless.automation.temporal`. The module held the temporal failure activities (`ActivityFailureCauses`, `AutomationFailureActivities`, `AutomationFailureActivitiesImpl`). The host does not use them; host failure recording is `TemporalFailureRecorder` in `sequeless-app`.
- `sequeless/sequeless-query`: the import commits are the four in section 2.1: `072a6a3` (primary), `6060d76`, `6ef2b78`, `1d59e5f`. The prototype's implementations that the released adapters replace are deleted in the P4 query commit, and its body names these SHAs and gives the reason for each deletion.

**Query side: retained as the host contract (decision, Navigator batch 14, Q1).** The P4 list does not delete the spi query contract. The host still uses it, and the released ports have different signatures (`BrowsePort.browse` takes `(FieldScope, BrowseQuery)` and returns `BrowseResult`; `IndexPreparationPort.prepare` returns `IndexPreparationResult`). Changing imports would change the core's contract, which Q7 (section 10) kept as the host's own. The Navigator decided, in batch 14 (Q1): keep the spi query ports and `Page` as the host's Query contract, and do not retarget core to query-core types. That retarget is a backlog item.

**Retained: host contract, not deleted.** These were ported to a host-owned role. The released `sequeless-query` is their implementation, through the three `ReleasedXPortAdapter` classes in `sequeless-app`. The copies are not deleted in P4, which is the explicit exception to the deletion list and to section 11:

- `sequeless-spi`, `org.sequeless.spi.query`: `BrowsePort`, `ReadPort`, `IndexPreparationPort`, `Query`, `QueryResult`, `FacetBucket`, `ObjectView`, `ReadRequest`, `Sort`, `Direction`.
- `sequeless-spi`, `org.sequeless.spi.object`: `Page`.
- `sequeless-spi-testkit`, `org.sequeless.testkit.query`: `BrowseContract`, `ReadContract`, `ObjectViews`, `QueryFixtures`, and the test in-memory ports `InMemoryBrowsePort` and `InMemoryReadPort`.

What P4 deletes on the query side is the implementation that the released adapters replace: the browse, read and index methods of `PostgresQueryStore` (`read`, `browse`, `prepare`), and the persistence tests that exercised only them, where the released testkit already covers the same contract. `PostgresQueryStore` keeps the aggregate `QueryPort` methods. The released coverage used for each test deletion is listed in the P4 query commit body.

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
| `sequeless-adapter-persistence-postgres` | `PostgresQueryStore` replaced by the released Postgres ports; `PostgresMigrations` keeps running the `postgres-query` location | Failure recorder stays (section 10) | Moved copies deleted; `V1` and `V2` in `postgres-query` deleted in P2 (`617e2f8`), not P4 (section 2.1) |
| `sequeless-adapter-automation-inprocess` | Not touched | Module replaced by the released artifact | Module deleted |
| `sequeless-adapter-automation-temporal` | Not touched | Module replaced by the released artifact | Module deleted |
| `sequeless-app` | REST mappers for Object views and `BrowseResult`; `CoreConfiguration` beans | Host bindings; relay maps rows to `StoreEventRow`; `CoreConfiguration` beans | Imports and wiring updated; `ArchitectureTest` rules retargeted |

Dependency versions come only from the BOM. Module POMs carry no version for the released artifacts.

## 6. Release config

- The prototype is not released. Its root version stays `0.1.0-SNAPSHOT`, and no artifact is published from it.
- No SNAPSHOT is a dependency of the prototype on `main`. Every released artifact comes from the BOM at a released version.
- The BOM bump (P1) and the released artifacts are the only release-side change. The released artifacts' releases were done at the start of the spec: query 0.2.1, automation 0.1.0, BOM 0.2.0. Since then: `query-schema-adapter` 0.2.3 (`sequeless-query` #24, release #25), `query-spring-boot-autoconfig` 0.3.0 (`sequeless-query` #23) and BOM 0.3.2 (section 1.1).
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

- **Criterion to FilterNode.** The Browse filter is a `FilterNode`. The prototype's `Criterion` and `Operator` remain for aggregates (`QueryPort`). The released query side rejects `like` and `not like` (`InvalidQueryException`, as its javadoc states; not re-verified in this draft), while filter-core's table has `is like` and `is not like` as operators. The REST aliases map to the released names by the table in section 10 (Q4), and the values become `JsonNode`.
- **Ordering comparisons on dates.** The released filter's ordering operators (`is greater than`, `is less than` and their variants) also apply to `date` and `date-time` strings (`alsoAppliesToFormats`). The prototype's ordering operators apply to the types its own applicability check allows. P2 records any filter that now validates and did not before, and any REST response that changes.
- **Null semantics.** Checked in P2; see "Null operators" below. The released `is` operator lists `is null` and `is not null` as aliases, and `is not` does not, so the representation used is `is` and `is not` with a JSON null (section 10, Q4). **`contains` case handling** is not checked in P2.
- **DATE comparisons and tenant-wide text search.** The cycle brief names both as runtime changes. They are not yet checked against the released Postgres adapter's SQL, so P2 must check them with the released query contracts before the cut-over merges.
- **Display labels and operator aliases.** Changed by the released query and REST layer. The REST wire format must stay wire-compatible with the hand-written TypeScript models for Object views and `BrowseResult` (DR-20).
- **Sorting and facets.** Core rejects an unsortable sort and an unfacetable facet with `InvalidQueryException` before the port runs. Facet keys are `FieldPath`s, not IRIs. A sort key on a relationship orders by target id.
- **`prepare`.** Returns `IndexPreparationResult.EMPTY` without calling the port when the version is superseded (query ADR 0002).
- **Unstamped `TransitionFired` rows (spec 15 D-2).** The released core throws when a command needs provenance and the row has no `ontologyVersion`. The prototype omitted the key. The relay (P3, commit `2f525f3`) records such a row through the host's `AutomationFailureRecorder` and claims it as done, so the rows behind it are ingested in the same poll. The released core never sees the row. The pre-deploy count of unstamped unprocessed rows (section 10) is not run on this host.
- **Store-side validation of automation's writes (spec 15 H7), decision (b).** Store-side validation now runs on automation's writes, so some actions the prototype accepted can be rejected. The released executor emits a single value for every property, including the ones the ontology declares multi-valued, and the Store takes a JSON list for those. The prototype wrote these values unchecked, so the Store's refusals are new behaviour. The Navigator decided option (b): the Store keeps its list shape, and the host binding coerces each scalar to a one-element list by the declared cardinality. `ShapeCoercion` (commit `b3da229`) reads the cardinality from the tenant's schema, never from a property name. It wraps a scalar only for a property whose cardinality maximum is absent or greater than one. A list is unchanged, a single-valued property is untouched, and a property or type the schema does not declare is left for the Store to decide. The earlier option (a), a `maxCardinality 1` on `Task.title`, is withdrawn and reverted (`d2cc1fd`, reverted in `10a3357`). `reference.ttl` and `OntologyFixtureDriftTest` are identical to origin/main, so the sortable-title change and the drift allowance do not apply. The reason for (b): it keeps one shape for each property on both sides, so the REST and the UI see the same lists.
- **Upstream backlog (released executor).** The released `DefaultActionExecutor` emits scalars and does not honour the declared cardinality. The executor should build each value to its declared shape, which would remove the host's coercion from the write path. Until a release does that, the host coerces.
- **Known UI consequence.** The Angular UI sends some Task titles as arrays (`object-form.spec.ts` and the e2e specs in `sequeless-ui`). Those lists are the Store's shape, so the change does not affect them. This spec does not change `sequeless-ui`.
- **Test fixture consequence.** `DerivationPluginEndToEndTest` runs on the released `reference-plugin.ttl`, which is unchanged. The payload shapes in the REST and IT tests are the Store's shape, as before.
- **Shape-refusal test.** `AutomatedWriteShapeRejectionIT` checks that an automated create with a wrong datatype (a text value for the integer `priority`) is refused by the Store, and that the host's failure recorder keeps the record. It also checks that a scalar title is accepted, because the host coerces it. `AutomationBeanUniquenessIT` checks that the context holds one `ActionExecutor`, one `TriggerEvaluator`, and one host binding each of `ObjectReadPort` and `StoreCommandPort`.
- **Failure recording (section 2.2).** The released automation does not record automation failures. After the cut-over the prototype's `sq_automation_failure` stops receiving rows unless the host records them.
- **Divergence (spec 15 D-1).** The prototype and the released artifacts differ in behaviour until the cut-over (P2 and P3). Each step's gate names the behaviour it checks.
- **Sort.** `query-schema-adapter` 0.2.2 makes a property sortable when its cardinality max is exactly 1 (`sequeless-query` #21, commit `83e7dc4`; `SchemaFieldSourceTest.sortableOnlyWhenCardinalityMaxIsExactlyOne`). Before it, the adapter hard-coded sortable to false. Only the sortable flag changed. This overrides spec 14 decision Q16 for sort only. `sequeless-query`'s ADR 0001 still says sortable is always false, which is stale on that point.
- **Subtype union.** A root's scope includes its transitive subtypes' properties. The root's own list, inherited copies included, wins for its IRIs. A subtype-only IRI is one usable Field when every copy agrees, and an explicit conflict Field (all capabilities false, type UNKNOWN) when they disagree (`953b2ba`; tests in `SchemaFieldSourceTest`). The sibling-conflict case is a behaviour change. The prototype's union takes one copy, which this spec recorded as a silent last-copy pick; that is not re-verified in P2. On the reference ontology the change adds 11 subtype-only IRIs at `Deliverable` and 9 at `WorkItem`, and changes no root-declared IRI (`953b2ba`, `ReferenceOntologyParityTest`). Released in `query-schema-adapter` 0.2.3: `sequeless-query` #24 (merged as `c231973`, release #25), pinned by `sequeless-bom` 0.3.2. The branch `fix/schema-subtype-properties` (head `3e26daa`) was the pre-merge work; its commit hash is not an ancestor of `origin/main`, but the merged content includes it.
- **Multi-valued filters and non-materialised derived properties.** The released API accepts both: its filter does not gate on cardinality, and it accepts derived properties. The prototype's core still refuses them before the port is called (`DefaultBusinessObjectService.java`, `resolveQueryableProperty`, called from `buildCriteria`, `buildSorts` and `buildFacets`). `cf1ea97` pins four REST cases in `QueryEndToEndTest`. With one core check removed at a time, the matching case fails (section 12). The facet case is also refused by the core's own "not a facet property" check, so with the pre-check removed it fails on wording, not on status.
- **Null operators.** `isnull` maps to `is` with a JSON null, and `notnull` to `is not` with a JSON null (`ca89624`). `ReleasedNullParityIT` (5 tests, EXIT 0, Testcontainers `postgres:18`) shows identical id sets on both sides for a missing key, a tagged empty list, a present value, the negated not-null, and the combined filter with a comparison (`9f24983`). An explicit JSON null is different. Both readers (`ValueJsonDecoder` in `query-postgres-jsonb-adapter`, and the prototype's `ValueJsonCodec`) fail on it with `IllegalArgumentException: Unrecognized tagged value: null`. A bare `[]` is undecodable on both sides. The domain writer cannot store a bare null, so only raw rows reach this. Follow-up: decode an explicit null on both sides, through an upstream patch and the prototype's `ValueJsonCodec`. Operator pre-deploy check, not run (no dev database): count object rows where a property value is a bare JSON null or a bare `[]`.
- **Reference facet labels.** `ReferenceFacetLabels` reads each distinct reference target once per browse, across all reference facets (`ca89624`). The cost is one released read per distinct target. Follow-ups: a batched read, or label translation in `sequeless-query`.
- **`includeDeleted=true`.** `ReleasedBrowsePortAdapter` throws `UnsupportedOperationException` for it. The core always passes `false` (`DefaultBusinessObjectService.java` line 220), so REST cannot reach it. Not changed.
- **Pre-existing gaps, not changed.** Deeper hops (`PathWalk.find`) do not compare the caller's target class with the edge's target. `describe()`'s terminal root hop returns the caller's path without comparing its target. The merged content (`sequeless-query` #24, from `3e26daa`) closes the root-hop comparison for `expand` and for non-terminal `describe`. Both gaps above stay open.
- **Live browse routing.** On this branch the core's `BrowsePort` is `ReleasedBrowsePortAdapter` (`CoreConfiguration.java` line 192). The prototype's `PostgresQueryStore` is not a `BrowsePort` bean, and no conditional gates the released bean, so the code does not hold live browse back. The release condition was a BOM that carries the subtype union. It is met: BOM 0.3.2 pins `query-schema-adapter` 0.2.3, which carries the union and the sort fix (section 1.1).
- **Limitation: no deploy gate.** No dev database exists on this host. The deploy gates (the O-3 unprocessed count, the live Temporal count and the owner's deploy approval) cannot run here. This spec records that as a limitation, and no deploy is claimed.
- **`typeOf` (P3, decision 2, option b).** The released `ObjectReadPort` and `StoreCommandPort` carry only the object id and no type (spec 15 R2 and R3). The Command use cases read, edit and create by type. The binding therefore resolves the type through `BusinessObjectService.typeOf(Scope, ObjectId)`, which reads `ObjectStorePort.typesOf` under the caller's `Scope` and returns empty for an absent, soft-deleted or other-tenant id. It authorizes nothing: the type is what an authorization decision needs, and read, edit and delete each authorize their own operation against it. A caller can therefore learn the type of a live object id in its own tenant without READ. The ids are random UUIDs. No binding class imports `ObjectStorePort`. **Upstream backlog:** a later `sequeless-automation` release carries the type in the ports, which removes this lookup from the binding path.
- **Timer test (P3, decision 3), test-only.** `TimerFailureRecordEndToEndTest` injects its failing `TriggerEvaluator` proxy with a `PriorityOrdered` post-processor, whose `@Bean` method returns the concrete class. The proxy then sits inside the host's failure-recording wrapper, which has no order. Production bean ordering is unchanged. Before the change the proxy sat outside the wrapper, nothing was recorded, and the workflow failed.
- **Failure-recording regressions (P3, Navigator ruling, option a). These are regressions, not neutral gaps.** The prototype recorded these failures in `sq_automation_failure`. P3 does not:
  1. **Stamp, mismatch and index refusals.** The prototype recorded them. P3 no longer records them. They are thrown in the released `ActionActivitiesImpl`, before the executor runs, and the host's wrapper never sees them.
  2. **Webhook delivery failures.** The prototype recorded them. P3 no longer records them. Delivery happens in the released activity, outside the executor.
  3. **A final `resolveWebhookAction` failure now fails the workflow.** The prototype recorded it and ended the workflow normally. The released `resolveWebhookAction` must return a request, so the wrapper cannot swallow it, and that workflow fails.
  4. **These failures remain visible** in the Temporal history and in the logs. Nothing is lost; the owner-facing record is what is missing.
  
  No mitigation was added. An ERROR log at the point of refusal would need a host seam in released code, which the Navigator ruled out. The onChange final failure, by contrast, is recorded again (commit `2b10e8a`), as the prototype's `ChangeEventWorkflow` recorded it.
  
  **Open, not verified.** (a) A `StartToClose` timeout on the final attempt is not recorded: the wrapper sees no exception from the activity. (b) The `setProperty` copy-back of stored non-derived values could be refused for a read-only property, because the write sends every stored value back. Neither is tested here.
  
  **Backlog prerequisite (upstream).** An upstream failure port in `sequeless-automation`, covering activity-level refusals, webhook delivery, and a result-returning `resolveWebhookAction`. It is a prerequisite to retire these gaps. The PR description for the owner states the same regression.
- **Drift guard (unchanged).** `OntologyFixtureDriftTest` is the strict byte-identity check against the released `ontology-testfixtures` 0.1.0 copy, as on origin/main. No allowance was added: the H7 edit that would have needed one is withdrawn.

## 10. Open Navigator questions

Settled by the Navigator's `set_plan` answer, in session `0e5543805193449c8caefabab95e002c`: the plan P1 to P5 (section 1 and the order of this spec); P1 is the first code PR only if the bump alone builds green, otherwise it folds into P2; the import SHA for automation is `5159284`; failure recording is host-owned, with `V12` untouched.

Settled by the Navigator (session `0e5543805193449c8caefabab95e002c`):

- **Q1, Command-side value types, option (b).** The Command side keeps its own `PropertyRef`, `TypeRef` and `Audit` in `org.sequeless.spi.object`. Only the Query side moves to `org.sequeless.query.api`. The two sets are mapped at the host boundary: in the P2 REST mappers and in the P3 `ObjectReadPort` and `StoreCommandPort` bindings, in small tested mapper classes. The duplication is reconsidered when a third consumer appears or a shared value-type module exists upstream. `Page` is Query-side only and leaves in P4.
- **Q2, failure recording, option (a).** The host keeps `AutomationFailure`, `AutomationFailureRecorder` and `PostgresAutomationFailureRecorder`, and `V12` is untouched. Nothing is added to `sequeless-automation`. P3's first task is to check the released temporal adapter for a seam: an activity interceptor, a workflow failure listener, or a host-registered activity. Without one, the host wraps at its `AutomationPort` or `ActionExecutor` boundary and calls the recorder. The released workflows are never forked. A failure that cannot be captured is recorded as a known gap in this spec and in the handoff. Follow-up, later: an upstream failure port in `sequeless-automation`, then delete the host wrapper.
- **Q3, host bindings, option (a).** `ObjectReadPort` and `StoreCommandPort` bind to the Command use cases, not to `ObjectStorePort`, so validation, authorization, provenance and the outbox write run as on the HTTP path. The mapping is: `fireTransition` to `TransitionService.fireAutomated`; `setProperty` to `BusinessObjectService.edit`, which reads the object first and handles a version conflict explicitly; `createObject` to `BusinessObjectService.add`; `find` to `BusinessObjectService.read`, mapped to `ObjectSnapshot`. The prototype's trigger evaluator already reaches the Command side through `fireAutomated` (`DefaultTriggerEvaluator.java` lines 159 and 240). The adapters live in `sequeless-app`, in a package such as `org.sequeless.app.automation`, wired from `CoreConfiguration`. No new module is added, and `sequeless-core` does not depend on the automation artifacts. Still open for P3: the released port signatures, and the principal that automation writes run as, which the released API has not been checked for.

- **Q4, REST filter operator names, option (a).** The REST alias set does not change: `eq`, `ne`, `in`, `lt`, `lte`, `gt`, `gte`, `contains`, `startswith`, `isnull`, `notnull`, parsed case-insensitively. The host translates each alias to a released filter operator in one tested mapping table, and the released names are not added to REST. The proposed mapping is `eq` to `is`, `ne` to `is not`, `gt` to `is greater than`, `gte` to `is greater than or equal to`, `lt` to `is less than`, `lte` to `is less than or equal to`, `contains` to `contains`, `startswith` to `starts with`, `in` to `is in`. P2 verifies it against the released code. The representation of `isnull` and `notnull` is open: P2 reads the released model and pins the choice in a test. `parseOperatorToken` stays the REST parser, and P2 adds a mapping-table test and a wire contract test.

- **Q5, legacy `TransitionFired` rows (spec 15 D-2), option (b), no V13.** The prototype already stamps new rows (`DefaultTransitionService.java` line 403) and already throws on an unstamped row (`DefaultTriggerEvaluator.java` lines 178 to 180), so the released behaviour matches the host today. A backfill would invent provenance for old rows, so it is not done. Before the new relay starts, an operator runs a read-only count of unprocessed `TransitionFired` rows without an `ontologyVersion` stamp, and the count must be 0. The gate is an operator pre-deploy step. It is not run on this host, which has no dev database, and the handoff records it as not run. A row with a missing stamp reaches the host failure recorder and does not crash the relay loop; P3 adds that test. V13 is not used by spec 17 unless P3 finds another need. The exact SQL for the gate is written in P3, after the outbox column names are checked against the schema.

Settled by the Navigator. Nothing is open in this section.

Settled after the P2 round, in Navigator session `0e5543805193449c8caefabab95e002c`. Each line names the commit that carries the decision.

- **Q6, migration copies in P2.** The prototype's `postgres-query` copies `V1` and `V2` are deleted in P2 (`617e2f8`), not P4. This deviates from spec 14 (section 2.1).
- **Q7, adapter shape.** The host's Query-side adapters call the released `QueryService`: `ReleasedBrowsePortAdapter`, `ReleasedReadPortAdapter` and `ReleasedIndexPreparationPortAdapter`, bound in `ReleasedQueryConfiguration` (`617e2f8`).
- **Q-root, `rootIri` on the core's `Query`.** The record `spi.query.Query` gains `rootIri`, appended last and non-blank. `DefaultBusinessObjectService.browse` sets it to the resolved type's IRI (`617e2f8`).
- **Sort.** A property is sortable when its cardinality max is exactly 1 (`83e7dc4`, released `query-schema-adapter` 0.2.2). This overrides spec 14 decision Q16 ("sorting is off") for sort only.
- **Subtype union.** A root's own copy of an IRI wins over subtype copies. A subtype-only IRI is one Field when every copy agrees, and an explicit conflict Field (all capabilities false) when they do not (`953b2ba`). Merged in `query-schema-adapter` 0.2.3 (`sequeless-query` #24) and pinned by BOM 0.3.2 (section 1.1).
- **Parity acceptance.** The released API accepts multi-valued filters and non-materialised derived properties, so the host gates them. The core refuses them before the port runs (`cf1ea97`; section 9).
- **Null mapping.** `isnull` maps to `is` with a JSON null, and `notnull` to `is not` with a JSON null (`ca89624`).
- **Explicit-null acceptance.** An explicit JSON null is not decoded by either side, and both fail with the same error. Accepted for this spec. The follow-up is in section 9 (`9f24983`).

Settled in P3, by the owner's decisions for this step (not by the Navigator):

- **H7, decision (b) (the Navigator's re-decision; option (a) is withdrawn).** The Store keeps the list shape. The host binding coerces a scalar to a one-element list by the declared cardinality (`ShapeCoercion`, commit `b3da229`), read from the schema and never from a property name. The reason: one shape per property on both sides, so the REST and the UI see the same lists. The option (a) change to `reference.ttl` was reverted (`d2cc1fd`, reverted in `10a3357`). On that option's branch the kickoff and idempotency Tasks in `TransitionsEndToEndTest`, `TemporalTransitionActivationEndToEndTest` and `TemporalAutomationEndToEndTest` failed. With the coercion on the reverted ontology, the unit gate passes (sequeless-app 244 tests).
- **Type lookup, option (b).** `BusinessObjectService.typeOf` (commit `2383b51`), with the no-authorization point and the upstream backlog in section 9.
- **Timer test, test-only.** The failing-proxy ordering moved, production ordering unchanged (section 9).
- **D-2, in the relay.** The relay records and skips an unstamped TransitionFired row through the host recorder (commit `2f525f3`). The released core is not changed, and no V13 is added.
- **Duplicate beans.** `AutomationBeanUniquenessIT` asserts exactly one `ActionExecutor`, one `TriggerEvaluator`, and one host binding of each `ObjectReadPort` and `StoreCommandPort` in the in-process context.
- **Failure recording, Navigator ruling (option a).** Behaviour stays as it is, with one exception: the onChange final failure is recorded on its final attempt, as the prototype did (`2b10e8a`). The stamp, mismatch and index refusals, the webhook delivery failures and the final `resolveWebhookAction` failure are regressions, stated in section 9. The upstream failure port is the backlog prerequisite to retire them.
- **Shape-coercion rule (review fix).** A scalar is wrapped unless its cardinality maximum is present and equal to one. A maximum of zero is wrapped (`148c4e5`).
- **Architecture exemption (review fix).** The core-dependency rule exempts the four automation bindings by name, and not the whole automation package. The outbox relay is covered (`1531b2e`).

## 11. Definition of done (issue #45)

- The new repos' CI is green. This is done for `sequeless-query` (releases 0.2.1) and `sequeless-automation` (0.1.0).
- The prototype's CI is green against released artifacts, with no SNAPSHOT on `main`. Checked at P1 and at the last code PR.
- The BOM's CI, which runs every testkit contract, is green. The BOM is released at 0.2.0 (`sequeless-bom-v0.2.0`).
- The release and the BOM re-release are done for the released repos. The prototype is not released.
- For cut-over specs, the prototype deletes the moved code in a commit that names the target repo and the import SHA. That commit is P4 (section 2.3).
- Explicit exception (Navigator decision, batch 14, Q1): the spi query ports (`BrowsePort`, `ReadPort`, `IndexPreparationPort`, `Query`, `QueryResult`, `FacetBucket`, `ObjectView`, `ReadRequest`, `Sort`, `Direction`), `spi.object.Page` and the testkit query contracts are retained as the host's Query contract, not deleted. They are ported to a host-owned role, and the released `sequeless-query` implements them through the `ReleasedXPortAdapter` classes. The DoD is met for the query side by this exception, and the exception is recorded in section 2.4.

## 12. Verification

Each step runs, before its PR is opened:

- P1: `spotless:check` and the JDK 25 unit gate with `-DskipITs`, then `dependency:tree` to confirm the BOM resolves every consumed artifact at a released version. Each integration test class named in section 7 is run alone, or recorded as not run.
- P2 and P3: the unit gate, a wire-format contract test for Object views and `BrowseResult` (P2), and a trigger-to-action test with in-process fakes (P3). Each integration test class is run alone under the Maven lock.
- P4: the unit gate, a grep showing no dangling import of a deleted package, and the integration tests run alone.
- P5: the handoff, `docs/agents/handoff.md`, in the last code PR.

Run for the P2 fix round (commits `ca89624` to `ed0db73` on `spec-17-p2a-query-consumption`; no PR):

- Unit gate after each commit: `spotless:apply`, then `mvn -Prelease -DskipITs verify` under `flock`, JDK 25, `-T 1`, `MAVEN_OPTS=-Xmx1g`. BUILD SUCCESS after `ca89624`, `9f24983`, `6a9fb5a`, `e1c9299`, `919ad90`, `283c1f0`, `cf1ea97` and `ed0db73`. The base commit `617e2f8` records its own gate in its message (`sequeless-app` 194 tests).
- `ReleasedNullParityIT` alone (`-pl sequeless-app -am verify -Dit.test=ReleasedNullParityIT`): EXIT 0, `Tests run: 5, Failures: 0, Errors: 0`. Run at `9f24983`, and again on the final branch after `283c1f0` and after `ed0db73`. The first run failed on spotless formatting before `spotless:apply`; the test code did not change.
- `QueryEndToEndTest` (`mvn test -pl sequeless-app -am -Dtest=QueryEndToEndTest`): 12 tests, 0 failures, including the four pre-check cases (`cf1ea97`).
- Pre-check removal, not committed: three runs of `QueryEndToEndTest`, each with one `resolveQueryableProperty` call in `DefaultBusinessObjectService` replaced by a plain lookup and the file restored afterwards. Removing the filter check fails the multi-valued and derived cases (both return 200) and `invalidFilterPropertyReturns400WithViolations`. Removing the sort check fails the sort case, because the released sort refuses with no core violation. Removing the facet check fails the facet case on wording.
- BOM 0.3.2 round (commits `cf68fc5` and `5d73376`): the unit gate (`spotless:apply`, then `verify -Prelease -DskipITs`), BUILD SUCCESS, EXIT 0, with `QueryEndToEndTest` at 15 tests and 0 failures, including the three WorkItem subtype-only cases. Each of these ITs alone, EXIT 0: `ReleasedNullParityIT` (5 tests), `AcceptanceCriteriaIT` (8), `OntologyPersistenceAcrossRestartIT` (1), `FatJarServesUiAndApiIT` (2), and `PostgresQueryStoreContractIT` in the persistence module (25).
- Not run: the full integration set (the other failsafe classes); the P2 wire-format contract test named in section 12 (not written in this round); any dev-database or deploy gate (no dev database on this host); the UI and ng tests.
