# Spec 18: Prototype stage 5, final fold

Issue: [#46](https://github.com/sequeless/sequeless-prototype/issues/46). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23) (order, history, release rules). Decisions read: [#19](https://github.com/sequeless/sequeless-prototype/issues/19) (what stays in the prototype). Tracker: `docs/extraction/remaining-work.md`. Glossary: `GLOSSARY.md`. Decision records: `docs/architecture/02-decision-records.md`, DR-29 to DR-36.

Status: draft, revision 1, written in the first code PR of spec 18 (unit U2, branch `spec-18-u2-rename`). Base: prototype `origin/main` `5c0fffa`, checked on GitHub before the branch was cut. The spec is not complete until the definition of done in section 11 holds. Each later unit (section 10) updates the status of its own items here.

Where this spec gives a decision, its number is the Navigator's (Q1 to Q24, Q-A to Q-F, G1 to G4) or the parent's ruling, as named in section 2. Where a fact has not been checked against a file or a registry, the text says "unverified" and names the unit that settles it.

## 1. Identity, stage and dependencies

- Stage 5, the final fold. Spec 18 of 19. The Store (the Command side of a Sequeless host: object state, outbox, validation, derivation and the Postgres adapter for them) leaves the prototype and is published from its own repository, `sequeless-store`, with modules `store-core`, `store-testkit` and `store-postgres-jsonb-adapter`. The ontology's Postgres adapter moves into `sequeless-ontology`. The prototype keeps its name and its single host. `sequeless-ui/` is out of scope and is not moved or renamed.
- Waits for spec 17 ([#45](https://github.com/sequeless/sequeless-prototype/issues/45), closed). Spec 17 leaves the prototype consuming the released Query and automation artifacts.
- Released artifacts this spec depends on: `query-testkit` 0.2.1, `query-postgres-jsonb-adapter` 0.2.x (the prototype's Released* tests compile against `query-testkit` 0.2.1; verified at U6 PR-2, `#232`), `automation-*` 0.1.0, `sequeless-bom` 0.3.2 (the pin on `origin/main` at 5c0fffa), `ontology-core` 0.1.0 and `schema-contract`, `behaviour-contract` and `automation-contract` as the BOM carries them.
- No spec-18 unit changes the behaviour of the prototype. The rename units (U2 to U4) are rename-only commits, each with CI green. Behaviour changes, if any, live in the new repositories and reach the prototype only through the cut-over (U11, U14).

## 2. Decisions

The decisions below are the Navigator's and the parent's, as recorded in the planning cycles. Where a later decision amends an earlier one, the later one applies and the amendment is named.

| ID | Decision |
|---|---|
| Q1 | One new repository, `sequeless-store`, with modules `store-core`, `store-testkit` and `store-postgres-jsonb-adapter`. `ontology-postgres-adapter` is a module of `sequeless-ontology`, not of the store repository (see Q13). |
| Q2 | `store-postgres-jsonb-adapter` owns migrations V1 to V12, including `sq_ontology_document`, as the published table contract. V1 creates `sq_object`, `sq_ontology_document` and `sq_outbox` in one file on one Flyway history. `ontology-postgres-adapter` ships no migration and has no compile-time dependency on the store adapter; it requires the migrated schema. |
| Q3 | `store-core` takes the Command packages of `sequeless-spi` (object, outbox, derivation, validation, the `DerivationRecomputer` and the failure SPI from automation, the authz SPI, the aggregate query types) and the Command use cases of `sequeless-core`, except `MetaModelService`, `OntologyAdministration`, `WhoAmI` and their `Default*` use cases, which stay in the host (O12, Option A). The permit-all authz adapter and every Spring autoconfiguration stay in the host. Amended by Q18, Q-C and section 3. |
| Q4 | `store-postgres-jsonb-adapter` keeps the location `db/migration/sequeless/postgres` and the default `flyway_schema_history` table, with V1 to V12 byte-identical. `PostgresMigrationFrozenSetsTest` moves with it. The prototype deletes its copies in the cut-over commit, which names `sequeless-store` and the import SHA. |
| Q5 | `Page`, `Audit`, `PropertyRef` and `TypeRef` move to `store-core`. The host imports them from there. The query-side copies in `sequeless-query` stay; the Released* adapters map between the two sides. |
| Q6 | `store-core`, `store-testkit` and `store-postgres-jsonb-adapter` start at 0.1.0. `sequeless-bom` moves from 0.3.2 to 0.4.0 with a `feat(bom)` commit. Release order: store release, then the BOM release, then the prototype cut-over. |
| Q7 | Packages: `org.sequeless.store.spi` (outbound ports that adapters implement, and that the core calls), `org.sequeless.store.api` (inbound Store Command port and its types), `org.sequeless.store.core` (use cases), `org.sequeless.store.postgres` (the Postgres adapter). The rename is a prerequisite in the prototype, before the move, as rename-only commits with CI green. The Flyway location is unchanged. `app.*` and the host stay under `org.sequeless.app`. The names freeze at 0.1.0. |
| Q8 | The Postgres autoconfiguration, `PostgresPersistenceProperties`, `PostgresAdapterDescriptorProvider` and `PostgresMigratingDataSource` stay in the prototype host, re-pointed at `org.sequeless.store.postgres`. `store-postgres-jsonb-adapter` ships plain classes, `PostgresMigrations` and the SQL. A `store-spring-boot-autoconfig` module can follow later as an additive change. |
| Q9 | One host module, `sequeless-app`. The persistence, Jena and permit-all wiring fold into it in that order, one module at a time, each as a CI-green commit. Host-level tests move with their wiring. The emptied modules are deleted in the fold units (section 10). |
| Q10 | The original twelve-unit order. Superseded by the amended unit list in section 10 (cycle 17). |
| Q11 | `AutomationFailure` and `AutomationFailureRecorder` move to `store.spi` in the rename units. `PostgresAutomationFailureRecorder` moves to `store.postgres` in U7, unchanged. `sequeless-automation` #4 does not block U6 or U7. The recorder SPI is frozen at 0.1.0. |
| Q12 | `DerivationRecomputer` goes to `store.api`, because `store-core` implements it and the host's relay calls it. `DerivationPlugin`, `DerivationContext` and `QueryPort` go to `store.spi`, because plugins and adapters implement them. This amends the U3 placement of `QueryPort`. |
| Q13 | `ontology-postgres-adapter` is a new module of `sequeless-ontology`, unit U13. It does not block U10. The prototype deletes its `PostgresOntologyDocumentStore` in a later commit, after U11 and U13's release and a BOM re-release. |
| Q14 | The query-side spi-testkit files leave the prototype; `query-testkit` 0.2.1 owns them. `Fixtures.java` moves to `store-testkit`, with the query-only builders split out first. Timing amended by Q24. |
| Q15 | Only the command frozen sets move with `PostgresMigrationFrozenSetsTest`: `post-p2/command`, `post-p3/command`, `post-p4/command` and `post-p7/command`. `post-p7/query` is retired from the store repository and deleted from the prototype. Amended by Q-B for `3328993` and `fbb82c5`. |
| Q16 | U5 is one code unit with three parts: the `Fixtures.java` split, the separation of Spring-free contract tests from host tests, and the pre-condition checks. The QueryPort carve-out is dropped, because `PostgresQueryStore` is aggregate-only and moves whole in U7. |
| Q17 | `AdapterDescriptor`, `AdapterDescriptorProvider` and `SpiVersion` move into `sequeless-app`. **Withdrawn** by Q22: the move creates a Maven cycle until the adapter modules fold in. |
| Q18 | A browse and read split (amended list U6). The browse and read use cases leave `sequeless-core` for the host's query package, with `ObjectViewMapper`. `ObjectView` leaves `StateMachineInterpreter` and `PostgresObjectStore`. `QueryPort`, `Criterion`, `Operator`, `AggregateRequest` and `AggregateResult` move to `store.spi` as one group. The rest of `spi.query` stays in the host. Amends Q3, Q12 and Q16. |
| Q19 | The Command-side `schema()` builder is split out of `QueryFixtures` into `store-testkit`'s main sources, in `Fixtures.java`. `PostgresQueryStoreIndexUsageIT` and `PostgresSearchVectorBackfillIT` import it from there. |
| Q20 | `store-testkit`'s main sources publish `InMemoryObjectStorePort`, `InMemoryAggregateQueryPort`, `InMemoryAggregateObjectStorePort`, `WorkloadDerivationPlugin` with its `META-INF/services/org.sequeless.store.spi.DerivationPlugin` entry, and `ontology/unknown-plugin.ttl`. They move from `sequeless-spi-testkit` `src/test` in U5, so the import SHA is a pure copy. The U6 PR lists them, because they become part of the 0.1.0 testkit surface. |
| Q21 | `DefaultBusinessObjectServiceTest` moves to the host with the browse and read path (U6), and is split if it also covers Command cases. `ValueCoercerTest` stays in `store-core`, with a small local schema. There is no `store-core` to `store-testkit` test dependency, so there is no Maven cycle. |
| Q22 | The root `sequeless-spi` module stays until the host fold, holding only `AdapterDescriptor`, `AdapterDescriptorProvider` and `SpiVersion`. U10 trims it and does not delete it. The fold (U12d) moves the three types into `sequeless-app` and deletes the module. Withdraws Q17. |
| Q23 | `PostgresMigrations` in `store-postgres-jsonb-adapter` runs only the command set, on `flyway_schema_history`, with no dependency on the query adapter. `queryFlyway` is removed in U5, before the import SHA. The host runs the query set through `query-postgres-jsonb-adapter`'s own path on `flyway_schema_history_query`, command first and then query. The query-set cases of `PostgresMigrationsSettlingIT` move to the host. |
| Q24 | The query-side spi-testkit files (`BrowseContract`, `ReadContract`, `ObjectViews`, the query-only part of `QueryFixtures`, `InMemoryBrowsePort`, `InMemoryReadPort` and their contract tests) are deleted in U6, the browse and read split, because the module stops compiling there. The host's Released* tests use `query-testkit` 0.2.1. Supersedes the timing in Q14. |
| Q-A | The Postgres autoconfiguration and the adapter descriptor stay in the host (Q8). Amends #19 item 2, section 3. |
| Q-B | The frozen directories `frozen-migrations/3328993` and `frozen-migrations/fbb82c5` stay in the host. Amends Q15. |
| Q-C | The `AuthorizationPort` interface moves to `store.spi`. In U2 `AccessDecision` and `Operation` stay in `org.sequeless.spi.authz`; U3 moves them to `store.api`. The permit-all adapter, its autoconfiguration and its wiring stay in the host. Amends #19 item 6. |
| Q-D | `store.api` gains one minimal `ObjectState` (or `ObjectRecord`) type, with its fields sized by a grep at U4 (open item O5). |
| Q-E | An injected hook between command migrations V6 and V7 (`PostgresMigrations.migrate(DataSource, Consumer<DataSource>)`). **Amended in U5 (PR-B):** the default `migrate(DataSource)` runs the Query set in that hook, so every existing caller keeps its behaviour and its row writes keep working. A no-op hook gives the Command set alone. The original text said no-op by default; that default would have broken every caller that writes `sq_object` rows. The harness choice (open item O1) is still decided at U8. |
| Q-F | `ontology-postgres-adapter`'s tests take `store-postgres-jsonb-adapter` 0.1.0 at test scope, for the migrated schema only. It has no compile dependency and ships no migration. |
| G1 | The store's integration tests create `sq_searchable_property` with copied DDL. One host drift IT runs the real Query set and then the command set. There is no query artifact in the store. |
| G2 | `store-core` builds against the BOM's `ontology-core` 0.1.0. U8 and U10 each carry a compile check. Fallback: 0.2.0, if the check fails. |
| G3 | The store's ArchUnit rules move to `store-core`'s test (U8). |
| G4 | **Amended in U5 (PR-B):** all seven settling cases stay in the host for U5, because each one calls `assertQueryHistory` or `queryFlyway`, and the parent's ruling keeps those in the host. The store-side split is deferred to U8, after G1. The case map (open item O9) is closed by that ruling. The bare-database cases (default write works, command-only write fails, default history matches the released Query migrate) are added to the same host IT. |
| O2 | A JSONB-to-`Value` sync test lives in U10's `StoreBomContractTest`. **Fallback taken at U10 (#72):** the sync test is not in `StoreBomContractTest`. It is a recorded gap in that class's javadoc. The follow-up is sequeless-build#71 (store-testkit contracts without a public in-memory double). |
| O8 | Option B: `ontology-jena-adapter` stays at 0.1.2 in the BOM. BOM 0.5.0 adds the ontology-postgres-adapter entries only (U13b). |

The amended unit list (section 10) and the scope of each unit are the parent's rulings. Everything else in the table is the Navigator's, recorded in the planning cycles 14 to 17.

## 3. Superseded clauses of #19

#19 (closed) set the shape of the Command side. Its clauses below are superseded for spec 18. Its other clauses stand.

- **Item 2, autoconfiguration and adapter descriptor.** #19 put "their autoconfig and adapter descriptor" in `store-postgres-jsonb-adapter`. They stay in the host (Q-A, Q8).
- **Item 2, the use cases.** #19 lists `store-core` use cases and does not name `MetaModelService`, `OntologyAdministration` or `WhoAmI`. Under Q3, as amended in section 2, `MetaModelService`, `OntologyAdministration` and `WhoAmI` and their `Default*` use cases stay in the host. This placement is **closed as Option A** (O12, section 12), by the parent's cycle 21 ruling. U4 renames them to `org.sequeless.store.core` with the rest of `sequeless-core`, and U5 or U6 moves them to the host.
- **Item 3, tables.** #19 gave `store-postgres-jsonb-adapter` the object and outbox tables and their migrations. It owns V1 to V12, including `sq_ontology_document` (Q2).
- **Item 4, `ontology-postgres-adapter` "with its own migrations".** It ships no migration. Its tests use the store's `PostgresMigrations` at test scope (Q2, Q-F).
- **Item 6, `AuthorizationPort` stays in the host.** The interface moves to `store.spi` (Q-C). The permit-all adapter and its wiring stay in the host.

## 4. Rejected claims

Each claim below was tested against `origin/main` during planning and rejected. The evidence is given so that the claim is not raised again without new evidence.

1. **Split V1 and V3 to V12 by table** (object, outbox and failure in the store; the ontology document table elsewhere). Rejected. V1 creates `sq_object`, `sq_ontology_document` and `sq_outbox` in one file, V3 to V11 alter `sq_outbox` and `sq_object` only, and V12 creates `sq_automation_failure`. V1 is frozen by `PostgresMigrationFrozenSetsTest`, and `PostgresOntologyDocumentStore` reads and writes `sq_ontology_document`.
2. **`store-core` takes all of `spi.query` and the Command side without a carve-out** (Q3 as first written). Rejected. The `sequeless-core` source imports `spi.query` types in six files, and `spi.query` holds 16 files, including `BrowsePort`, `ReadPort`, `Query`, `QueryResult` and `ObjectView`, which `StateMachineInterpreter` and `PostgresObjectStore` use. A cut that leaves them out of `store-core` does not compile without Q18.
3. **`AdapterDescriptor`, `AdapterDescriptorProvider` and `SpiVersion` move into `sequeless-app` in U6** (Q17). Rejected. The Jena, permit-all and persistence adapter modules implement or import them, and `sequeless-app` depends on those modules until the fold, so the move makes a Maven cycle (Q22).
4. **`PostgresMigrations` moves whole, with `queryFlyway`.** Rejected. `PostgresMigrations` also runs the query set on `flyway_schema_history_query`, from the query adapter's jar, and `PostgresMigrationsSettlingIT` calls it. The store would then depend at runtime on the query migrations (Q23).
5. **The query-side spi-testkit files are deleted in U10 or U11** (Q14 timing). Rejected. They import `spi.query` types that U6 moves into the host, so the testkit stops compiling at the browse split (Q24).

## 5. Moves and PROVENANCE

Unit U8 moves `store-core`, `store-testkit` and `store-postgres-jsonb-adapter` from the prototype into `sequeless-store`, as a pure copy from the import SHA. The PROVENANCE record is written in U8 and is not complete yet. It will state:

- source repository: `sequeless-prototype`;
- source SHA: the commit that carries the rename and split units U2 to U6 (the import SHA), not the commit of U2 alone;
- source paths: `sequeless-spi/src/main/java/org/sequeless/store/spi`, `sequeless-spi-testkit` (the Command doubles, moved by Q20), `sequeless-core` (the Command use cases) and `sequeless-adapter-persistence-postgres` (the Postgres command adapter and `db/migration/sequeless/postgres`);
- the move: a pure copy with package names already final, so the copy changes no line;
- date: the U8 merge date.

The PROVENANCE is recorded as the spec requires; the import SHA is not known until U6 merges.

## 6. Prerequisite refactors and package renames

### 6.1 U2 (done, #226): rename-only, `org.sequeless.store.spi`

Rename only. No behaviour changes. Each type moves with its package-info; the old package-infos are deleted only when their package empties.

| Type | From | To |
|---|---|---|
| `ObjectStorePort`, `SearchVectorBackfillPort` | `org.sequeless.spi.object` | `org.sequeless.store.spi` |
| `OutboxPort` | `org.sequeless.spi.outbox` | `org.sequeless.store.spi` |
| `AuthorizationPort` (Q-C) | `org.sequeless.spi.authz` | `org.sequeless.store.spi` |
| `ValidationPort`, `Violation` | `org.sequeless.spi.validation` | `org.sequeless.store.spi` |
| `AutomationFailure`, `AutomationFailureRecorder` | `org.sequeless.spi.automation` | `org.sequeless.store.spi` |
| `DerivationPlugin`, `DerivationContext` | `org.sequeless.spi.derivation` | `org.sequeless.store.spi` |
| `QueryPort`, `Criterion`, `Operator`, `AggregateRequest`, `AggregateResult` (Q18) | `org.sequeless.spi.query` | `org.sequeless.store.spi` |

Also in U2:

- The unit tests of the moved types move with them: `AuthorizationPortTest` and `ViolationTest` (both in `sequeless-spi`).
- `META-INF/services/org.sequeless.spi.derivation.DerivationPlugin` in `sequeless-spi-testkit` is renamed to `META-INF/services/org.sequeless.store.spi.DerivationPlugin`, with its package. Its content, the implementing class name, is unchanged.
- The package-infos of `org.sequeless.spi.validation` and `org.sequeless.spi.derivation` are deleted, because those packages are empty. The other `org.sequeless.spi` packages keep theirs: `object`, `outbox`, `authz` (it keeps `AccessDecision` and `Operation`, which U3 moves), `automation` (it keeps `DerivationRecomputer`) and `query`.
- A new `org.sequeless.store.spi` package-info documents the package.
- Javadoc and imports of every importer are rewritten to the new names. The importer list comes from a grep of `origin/main`, which found more files than the count of 37 in the brief; the grep is the authority.

### 6.2 U3 (done, #227) and U4 (this PR)

- U3: the `store.api` rename of `AccessDecision`, `Operation` and `DerivationRecomputer` (Q12, Q-C), and the store-side ArchUnit rule for `org.sequeless.store.api..`, which is added only once that package exists (the parent's ruling).
- U4: `store.core` and `store.postgres` renames, and the minimal `ObjectState` type (Q-D, open item O5). The rename and the `ObjectState` change are separate commits. `ObjectState` holds the type IRI, state and properties that the interpreter reads. `ObjectView` leaves `StateMachineInterpreter` and `PostgresObjectStore`, following the Navigator's option B for the interpreter overload.

### 6.3 Other prerequisite units (planned, amended list)

- U5: the fixtures and contract split (Q14, Q16, Q19, Q20, Q23, G4). The Command doubles move from `sequeless-spi-testkit` `src/test` to its `src/main`, and `schema()` is split out.
- U6: the browse and read split (Q18, Q21, Q24). The browse and read use cases move to the host query package, and the query-side testkit files are deleted.

## 7. Modules and their dependencies (target)

The target shape, as planned. Each dependency is verified when its unit lands, not here.

- `store-core`: JDK, `kernel-identity`, `kernel-values`, `ontology-core` 0.1.0 (G2), `schema-contract`, `automation-contract`. No Spring, no Postgres. Its ArchUnit rules move from the host (G3, U8).
- `store-testkit`: `store-core`, and the contract tests it publishes. No Spring.
- `store-postgres-jsonb-adapter`: `store-core`, JDBC, Flyway. Owns `db/migration/sequeless/postgres` (V1 to V12). No Spring.
- `ontology-postgres-adapter` (in `sequeless-ontology`, U13): `ontology-core`. Test scope only: `store-postgres-jsonb-adapter` 0.1.0 (Q-F). No migration.
- `sequeless-app` (the host, after the fold): Spring wiring for the store adapter (Q8), the permit-all adapter (Q-C), the Jena adapter, and the Query and automation autoconfigurations.
- `sequeless-spi` (root): `AdapterDescriptor`, `AdapterDescriptorProvider` and `SpiVersion` until U12d (Q22). Deleted in U12d.

## 8. Release configuration

- First versions: 0.1.0 for `store-core`, `store-testkit` and `store-postgres-jsonb-adapter` (Q6, U9). The release PRs are release-please PRs in `sequeless-store`, with one config for the three modules (U7).
- BOM: 0.3.2 to 0.4.0 via `feat(bom)` (U10). The commit must be `feat`, not `fix`: `sequeless-build`'s release-please uses `bump-minor-pre-major`, so a `fix` would release 0.3.3 (unverified against that workflow's current config; U10 checks it).
- BOM 0.5.0 via `feat(bom)` (U13b) adds the `ontology-postgres-adapter` entries and pins `ontology-core` 0.2.0 in `bom-contracts` (F2). `ontology-jena-adapter` stays at 0.1.2 (O8, Option B).
- Publishing: GitHub Packages (`maven.pkg.github.com/sequeless/<repo>`). Maven Central is deferred to prototype issue #53.
- Every release PR gets a user empty-commit re-trigger, because a PR opened by the bot token does not start the `pull_request` workflows. Merge only on green required checks.

## 9. CI and workflows

- `sequeless-store` calls `sequeless-build`'s reusable `maven-ci` and `maven-release` workflows (U7). It has a CODEOWNERS file and GitHub Packages write permission for its release workflow.
- The prototype's CI is unchanged. Its `ci.yml` runs `npm run ci:combined`, which runs the UI and then `mvn -B -ntp verify`. `sequeless-ui` is not touched by any unit.
- `FatJarServesUiAndApiIT` must stay green on every U12 unit (the host keeps SPA serving and the `/api` prefix).
- Integration tests run one class at a time, under the shared Maven lock.

## 10. Unit plan (amended list, cycle 17)

Order: U2 > U3 > U4 > U5 > U6 > U7 > U8 > U9 > (U10 parallel with U13) > U11 > U12a > U12b > U12c > U12d > U13b > U14.

| Unit | Scope | Status |
|---|---|---|
| U2 | Spec doc, DR-29 to DR-36, the `store.spi` rename (section 6.1), the frozen-sets check (section 13), the ArchitectureTest retarget. Carries U1. | Done: #226, `9ecfea1` |
| U3 | `store.api` rename: `AccessDecision`, `Operation`, `DerivationRecomputer`; the `store.api` ArchUnit rule. | Done: #227, `c6ddb55` |
| U4 | `store.core` and `store.postgres` rename; minimal `ObjectState` (Q-D, O5). | This PR |
| U5 | Fixtures and contract split; Command doubles to `store-testkit` `src/main`; `schema()` split; `queryFlyway` removed; settling split (G4, O9). | Done: PR-A `#229` (`9e83a48`): doubles, `schema()` split, Spring guard. PR-B `#230` (`d3d9afc`): `queryFlyway` removed, the hook (Q-E amended), the bare-database cases; the settling cases stay in the host (G4 amended). The aggregate contract and the O12 host move wait for U6. |
| U6 | Browse and read split to the host; query-side testkit files deleted (Q24); host Released* tests against `query-testkit` 0.2.1. | Done: PR-1 `#231` (`b54d395`): browse and read to the host, O12 move. PR-2 `#232`: query-side testkit deleted (Q24), `query-testkit` 0.2.1 test dependency in `sequeless-app`; `Released*` compile against it. |
| U7 | Scaffold `sequeless-store` from `sequeless-template`; CI calls `maven-ci` and `maven-release`; CODEOWNERS; GitHub Packages write; release-please for three modules; placeholder build green. | Planned |
| U8 | Move `store-core`, `store-testkit` and `store-postgres-jsonb-adapter` from the import SHA (pure copy); V1 and V3 to V12 byte-identical; `PostgresMigrationFrozenSetsTest` with the four command sets (Q15 as amended by Q-B: `post-p2`, `post-p3`, `post-p4` and `post-p7`); store ITs with copied DDL (G1); the store ArchUnit rules (G3); the no-op hook between V6 and V7 (Q-E); `docs/adr/0001` in the store. | Planned |
| U9 | Release 0.1.0 of the three store modules. The release PR merges before any dependant runs. | Planned |
| U10 | BOM 0.4.0 with the store artifacts via `feat(bom)`; explicit test-scope pins in `bom-contracts` (F1); `StoreBomContractTest` with the O2 sync test; `store-core` compile check against `ontology-core` 0.1.0 (G2). Import stays 0.1.6. | Done: #72 (`26973b7`), release PR #73 (`c63b1fe`), BOM 0.4.0. O2 fallback taken: a recorded gap, follow-up sequeless-build#71. |
| U11 | Prototype cut-over commit naming `sequeless-store` and the import SHA (`429b2de`): delete the moved code, pin BOM 0.4.0, host composed migrate (command then query), G1 host IT and G4 host cases in the same commit. Section 12 records the U11 deviations. | This PR (one commit, one PR). |
| U12a | Persistence wiring folds into `sequeless-app` (Q8, Q9). | Planned |
| U12b | Jena wiring folds in. | Planned |
| U12c | Permit-all wiring folds in. | Planned |
| U12d | `AdapterDescriptor`, `AdapterDescriptorProvider` and `SpiVersion` move to `sequeless-app`; delete `sequeless-spi` and `sequeless-spi-testkit` after a grep for consumers (Q22). | Planned |
| U13 | `ontology-postgres-adapter` module in `sequeless-ontology`, test-scope dependency on `store-postgres-jsonb-adapter` 0.1.0 (Q-F), no migration; release-please package and manifest entry at 0.1.0. Runs in parallel with U10 after U9. | Planned |
| U13b | BOM 0.5.0 via `feat(bom)`; ontology-postgres-adapter entries only; `ontology-core` 0.2.0 pin in `bom-contracts` (F2); no-version resolution check (F1). Needs U10 and U13 releases. | Planned |
| U14 | The prototype deletes `PostgresOntologyDocumentStore`, in a commit naming `sequeless-ontology` and the import SHA; BOM pin moves to 0.5.0. Needs U12d, U13 and U13b released. | Planned |

The amended list's U12 is split into U12a to U12d. The Q10 numbering (U11 for the fold, U12 for ontology) is superseded.

## 11. Definition of done (from #46)

- [ ] The new repository's CI is green (`sequeless-store`, U7 to U9).
- [ ] The prototype's CI is green against released artifacts, never a SNAPSHOT on `main` (U11, U14).
- [ ] The BOM's CI, which runs every testkit contract, is green (U10, U13b).
  - Note (U11): the store contract run in the BOM is deferred until sequeless-build#71 closes. The U10 `StoreBomContractTest` is a compile and link check only, with the O2 gap recorded (section 2). This checkbox stays open until #71 closes.
- [ ] The release and the BOM re-release are done (U9, U10, U13b).
- [ ] For cut-over specs: the prototype deleted the moved code in a commit that names the target repository and the import commit's SHA (U11, U14).

The Refs on the PRs are `#46`, never a closing keyword, until every item above holds.

## 12. Hazards and open items

- No `V2` exists in the command set. The V2 deleted at 859a118 was the query set. Do not renumber; V1 and V3 to V12 stay as they are.
- V6's trigger reads `sq_searchable_property`. A bare command-only V6 fails, so the store ITs need the copied DDL (G1).
- `*:missing` and `transactional.lock=false` stay in the store's command configuration.
- The Released* tests compile against `query-testkit` 0.2.1 (verified at U6 PR-2, `#232`: compile-only use, no contract subclasses).
- The store-testkit test-scope dependency in `store-postgres` (unverified until U8).
- The ontology adapter's test-scope use of the command-only runner (unverified until U13).
- The `store-core` compile against `ontology-core` 0.1.0 (unverified until U8 and U10; fallback 0.2.0, G2).
- The `query` migrate method name and history table: verified at U11. `org.sequeless.query.postgres.PostgresMigrations.migrate(DataSource)` on `db/migration/sequeless/postgres-query` and history `flyway_schema_history_query`, in query-postgres-jsonb-adapter 0.2.1. The persistence pom now takes that jar at compile scope.
- The `dependency:analyze` claim (unverified; no unit settles it yet).
- O1: the V6 harness choice, decided at U8. O5: the `ObjectState` fields, sized at U4. O9: the settling case map, settled at U5.
- O7: `sequeless-automation` #4 (the upstream failure port) is additive later; the recorder SPI is frozen at 0.1.0 (Q11).
- O12, closed as Option A (host placement), by the parent's cycle 21 ruling: `MetaModelService`, `OntologyAdministration` and `WhoAmI`, with their `Default*` use cases, stay in the host (section 3, Q3). U4 renames them to `org.sequeless.store.core`, and U5 or U6 moves them to the host.
- Deviation (U6 PR-1; moot after U11, which deletes `sequeless-core`): `sequeless-core` declares `ontology-core` at test scope. Its main code imports nothing from `org.sequeless.ontology.api` or `.spi`, and the build parent's `dependency:analyze` (failOnWarning) rejects an unused compile-scope dependency. Section 7's wording (`ontology-core` 0.1.0 as a dependency of `store-core`) therefore holds for the tests only. Re-check at U8 and U10, and align section 7 then.
- Deviation (U11): the host's settling IT (`PostgresMigrationsSettlingIT`) loses its three frozen-pair cases, after the frozen post-P7, post-P2 and post-P3 Command sets, with the Query set. Their frozen directories are deleted in U11, as the brief requires. The store keeps those pairs command-only. The parent accepted this as a recorded gap. The host keeps the frozen 3328993 and fbb82c5 cases, the Query history, and the bare-database cases.
- Deviation (U11): `BrowseQuery` and `InvalidQueryException` are not copied into the host. store-core 0.1.0 carries byte-identical copies at the same FQN (`org.sequeless.store.core.api`), and a host copy would duplicate the class. The host imports them from store-core. The parent accepted this.
- Deviation (U11): the `spi.query` types (`BrowsePort`, `ReadPort`, `ObjectView` and the rest) are not in store-core. They move from `sequeless-spi` to `sequeless-app` under `org.sequeless.spi.query`, with the same FQNs (Q18, the host placement).
- Deviation (U11): `sequeless-spi-testkit`'s Java sources and resources are deleted, because no consumer remains (its consumers take `store-testkit` at the same FQNs). The module and its pom stay as a dependency-free shell until U12d, which is the section 10 text and the parent's ruling.
- Hazard (U11): `PostgresOntologyDocumentStore` is in the prototype (kept until U14, Q13) and in store-postgres-jsonb-adapter 0.1.0. The two files are byte-identical at the same FQN. U14 deletes the prototype copy. The package-info of `org.sequeless.store.postgres` is in both as well; the jar's copy has no annotations, so it is harmless, and the prototype's copy goes with the wiring in U12a.
- Deviation (U11): G1's drift IT (`PostgresQueryDriftIT`) runs the host's composed migrate, so the real Query set runs at the hook between Command V6 and V7, and Command V7 onward follows. It does not run the Query set first. The Query set's V2 indexes `sq_object`, which Command V1 creates, so the Query set cannot run first on an empty database.
- Deviation (U11): Q22 gives the `sequeless-spi` trim to U10. The trim (to `AdapterDescriptor`, `AdapterDescriptorProvider` and `SpiVersion`) lands in U11, as the U11 brief directs. The Command packages leave `sequeless-spi` in the same commit, because their FQNs are now in the released store.
- Divergence: the prototype's behaviour and the new repositories' behaviour must match at each cut-over. Spec 17's integration tests carry the prototype side.
- `sequeless-ui` is untouched. `FatJarServesUiAndApiIT` must stay green on every U12 unit.

## 13. What U2 changed

- The `store.spi` rename (section 6.1) and its importers, across the modules. The rename is package-only: no class body changed except the names in imports, javadoc links and the services file.
- **Frozen-sets check.** `PostgresMigrationFrozenSetsTest` gains `liveCommandSetContainsV1AndV3ToV12AndNoV2`: every live command migration name matches `V<n>__*.sql`, the live set contains V1 and V3 to V12, and it contains no V2. It asserts containment, not an exact set, so a later V13 does not need an edit here. The check that frozen files match live files pairs them by file name and compares their bytes; the directories are the only paths, and they are where the test reads its inputs, not what it identifies. The check does not depend on the Java package of the test.
- **ArchitectureTest.** The SPI rules (`spiDependsOnlyOnJdkAndItself`, `coreDependsOnlyOnJdkSpiAndItself`, `spiAndCoreDoNotDependOnSpring`, `noLombokInSpiOrCore`, `noJenaInSpiOrCore`) cover `org.sequeless.store.spi..` as well as `org.sequeless.spi..`. The parent's ruling said "retarget the SPI rule to `org.sequeless.store.spi..` only". Applied literally, that would fail: the moved ports import value types that stay in `org.sequeless.spi..` (`Page`, `Mutation`, `BusinessObject` and others), and `spiDependsOnlyOnJdkAndItself` would reject them. So the rules name both packages. They cover the classes they covered before, plus the moved ones.
- **ArchitectureTest, query rule.** `querySeesOnlySchema` now covers the five moved aggregate query types as well as `org.sequeless.spi.query..` (`MOVED_QUERY_TYPES`), so the rule does not lose them when they leave the query package.
- **`org.sequeless.store.api..` is in the SPI rules (U3).** U3 added `store.api` to the same five rules, once the package existed, as the parent ruled: an ArchUnit rule over an empty package can fail the build. The rules are `spiDependsOnlyOnJdkAndItself`, `coreDependsOnlyOnJdkSpiAndItself`, `spiAndCoreDoNotDependOnSpring`, `noLombokInSpiOrCore` and `noJenaInSpiOrCore`.
- DR-29 to DR-36 are added to `docs/architecture/02-decision-records.md`.
