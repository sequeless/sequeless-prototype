# Spec 12 — Prototype stage 3 cut-over: consume ontology

Issue: [#40](https://github.com/sequeless/sequeless-prototype/issues/40). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#13](https://github.com/sequeless/sequeless-prototype/issues/13), [#39](https://github.com/sequeless/sequeless-prototype/issues/39). Tracker: `docs/extraction/remaining-work.md`.

Status: draft, not reviewed by the owner. Section 3 is pending. Open items are in section 12.

Facts are checked at prototype `origin/main` `611d2f0` and at sequeless-ontology `origin/main` `c9726a7` (the 0.1.0 release commit). Where a count or claim depends on a command, the command is named in place.

## 1. Identity, stage and dependencies

- Stage 3, cut-over. Spec 12 of 19. The prototype stops carrying the ontology code that spec 11 copied to `sequeless-ontology`, and consumes the released artifacts instead.
- Waits for spec 11 ([#39](https://github.com/sequeless/sequeless-prototype/issues/39)), which is done. Six modules are released at 0.1.0 from `c9726a7` (tags in section 2). The S2b validation api (sequeless-ontology #14) is in that release. `sequeless-bom` 0.1.3 is published and lists the six artifacts at 0.1.0 (`remaining-work.md`, spec 11 slices S4 and S5a; checked with `gh release list` and the BOM pom at tag `sequeless-bom-v0.1.3`).
- Blocks [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (issue metadata).
- Branch `spec-12-prototype-stage3-cutover`, based on `origin/main` `611d2f0`.
- Consumed artifacts, all 0.1.0, version managed by the BOM (no version in module POMs): `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-jena-adapter`, `ontology-testfixtures`. `filter-core` 0.1.3 comes in through `behaviour-contract` (spec 11 section 5.2).
- The root POM imports `sequeless-bom` 0.1.2 (`pom.xml` line 64). Changing it to 0.1.3 is the only BOM edit in this spec.

## 2. Moves

Provenance:

| Field | Value |
|---|---|
| Source repo | `sequeless/sequeless-prototype` |
| Source SHA | `8ee2360` (`feat(ontology): reject comparisons on non-numeric properties at load (#39) (#100)`). Computed with `git log -1 --format=%H origin/main -- <paths>` at `611d2f0`. The path set is in the note below |
| Import commit | `b2ff8a6` in sequeless-ontology (sequeless-ontology #9), titled `feat(ontology): import the ontology from sequeless-prototype@8ee2360`. The title's source SHA matches the row above. Review fixes `76f5d43` come after it |
| Release tags | `ontology-core-v0.1.0`, `schema-contract-v0.1.0`, `behaviour-contract-v0.1.0`, `automation-contract-v0.1.0`, `ontology-jena-adapter-v0.1.0`, `ontology-testfixtures-v0.1.0`, all from `c9726a7` |
| Date | The author date of the cut-over commit |

Note on the SHA. `git log 8ee2360..origin/main -- <paths>` is empty at `611d2f0`, so no later commit touches the paths. The path set is: the `meta/` and `ontology/` packages under `sequeless-spi` (main and test); `sequeless-spi/src/main/java/org/sequeless/spi/object/OntologyDocumentStore.java`, `StoredOntologyDocument.java` and the test `StoredOntologyDocumentTest.java`; `sequeless-spi-testkit`'s `testkit/ontology/` (main and test), `testkit/Fixtures.java` and `src/main/resources/ontology/`; the Jena `JenaOntologyPort.java`, `SnapshotMapper.java`, the 11 moved Jena tests, and `sequeless-adapter-ontology-jena/src/main/resources/ontology/`.

The move is a copy (spec 11 D15). In this commit the prototype deletes the paths below, which are the copies.

### 2.1 Deleted paths

Counts are `git ls-files <path> | wc -l` at `611d2f0`. The inventory column is the driver's inventory (not in the repo): its moving-file counts, recounted by tag from the raw file list. It reports 66 moving files in sequeless-spi, 10 in the Jena module, 4 in the testkit and 0 in postgres (80 in total), which matches the By-module table.

| Module | Deleted paths | Files | Inventory | Released home |
|---|---|---:|---:|---|
| sequeless-spi, main | `spi/meta/schema/` (12), `spi/meta/behaviour/` (15), `spi/meta/automation/` (10), `spi/ontology/` (10), `spi/object/OntologyDocumentStore.java`, `spi/object/StoredOntologyDocument.java` (2) | 49 | 49 | `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-core` (`spi`) |
| sequeless-spi, test | `spi/meta/` (10), `spi/ontology/` (7), `spi/object/StoredOntologyDocumentTest.java` (1) | 18 | 17 | the same modules' tests |
| sequeless-adapter-ontology-jena, main | `JenaOntologyPort`, `SnapshotMapper`, `ConsistencyChecker`, `ImportResolver`, `OntModelBuilder`, `TurtleExporter`, `SqVocabulary`, `SqAnnotations`, `OntologySource`, `ReasonerSetting`, `JenaOntologySettings`, `ShaclDataGraphBuilder` (open item 3) | 12 | 10 | `ontology-jena-adapter`, packages `org.sequeless.ontology.jena` and `.internal` |
| sequeless-adapter-ontology-jena, test | the 11 moved tests (`ConsistencyCheckerTest`, `DerivationMappingTest`, `Halves`, `JenaOntologyPortAcceptanceTest`, `JenaOntologyPortContractTest`, `JenaOntologyPortStorePersistenceTest`, `JenaOntologyPortTest`, `SnapshotMapperTest`, `StateMachineMappingTest`, `TurtleExporterTest`, `UnknownPluginActivationTest`), and `ShaclDataGraphBuilderTest` (open item 3) | 12 | 0 | the target test trees |
| sequeless-adapter-ontology-jena, resources | `src/main/resources/ontology/sq-schema.ttl`, `sq-behaviour.ttl` | 2 | not counted | the same paths in `ontology-jena-adapter` |
| sequeless-spi-testkit, main | `testkit/ontology/` (`OntologyContract`, `OntologyPorts`, `package-info`) (3); resources `reference.ttl`, `inconsistent.ttl`, `inverse-transitive.ttl`, `reference-plugin.ttl`, `sq-schema.ttl`, `sq-behaviour.ttl` (6) | 9 | 3 | `ontology-testfixtures`; the vocabulary files in `ontology-jena-adapter` |
| sequeless-spi-testkit, test | `testkit/ontology/InMemoryOntologyPortContractTest.java` | 1 | 1 | `ontology-testfixtures` |
| sequeless-spi-testkit, in-file | the 41 ontology members of `testkit/Fixtures.java` (the 3 general members stay: `OPAQUE_RESOURCE`, `defaultScope`, `scope`); the private in-memory store in `InMemoryObjectStorePort`; the two `ontologyDocuments` clauses of `ObjectStoreContract` (lines 184 and 403) | n/a | n/a | `InMemoryOntologyDocumentStore`, `OntologyDocumentStoreContract` (D19) |
| sequeless-app, test | `OntologyFixtureDriftTest.java` (section 7) | 1 | 0 | none |
| sequeless-adapter-persistence-postgres | none deleted. Three main files and one test file are rewired (section 5.2) | 0 | 0 | not applicable |

Totals: 64 main Java files (spi 49, Jena 12, testkit 3), 32 test Java files (spi 18, Jena 12, testkit 1, app 1) and 8 resource files (Jena 2, testkit 6), 104 files in all.

Notes on the inventory column:

- sequeless-spi test: the inventory lists `StoredOntologyDocumentTest` as a probable mover, not a moving file. Spec 11 D19 moves it with its type, so it is counted here.
- Jena main: the inventory does not count `JenaOntologySettings` or `ShaclDataGraphBuilder`. Spec 11 table 4 moves the settings record, and the released adapter has both files (open item 3).
- Jena test: the inventory lists seven of the moved tests as probable movers and four as rewire candidates, so its moving count for the module is 0. This table follows spec 11 section 2.

### 2.2 Fixtures split and the gap

The prototype `Fixtures` class has 44 static declarations (`grep -cE '^\s+(public |private )?static ' sequeless-spi-testkit/src/main/java/org/sequeless/testkit/Fixtures.java`). Three are general, so 41 are ontology members, as the brief says. The released `ontology-testfixtures` `Fixtures` at `c9726a7` has 36 distinct static member names (same extraction on `origin/main`). One of them is the general `defaultScope`, so 35 are ontology members. Six prototype ontology members are missing from it:

| Member | Prototype consumers outside `Fixtures.java` (`git grep -l -w`) |
|---|---|
| `EMAIL_IRI` | `QueryContract`, `QueryFixtures`, `ValidationContract` (testkit main, which stay) |
| `NAME_IRI` | the same three files |
| `OWNER_IRI` | none. The two core tests use their own private `OWNER_IRI` |
| `INCONSISTENT_ONTOLOGY_IRI` | none |
| `UNKNOWN_PLUGIN_TURTLE` | `UnknownPluginActivationTest` (moved) |
| `unknownPluginOntology()` | none |

So `EMAIL_IRI` and `NAME_IRI` are used by prototype code that stays, and the full move in the brief would strand them. Open item 1 is the decision.

The Fixtures importers are 24 files, of which 19 use the ontology part (inventory). The four classpath resources the moved code loads (`reference.ttl`, `inconsistent.ttl`, `inverse-transitive.ttl`, `reference-plugin.ttl`) are byte-identical in `ontology-testfixtures` and in the prototype's testkit copies (`cmp` at `c9726a7` and `611d2f0`).

## 3. Prerequisite refactors

PENDING: the FilterNode-to-Criterion bridge is either a prerequisite PR before this cut-over, or part of it. The driver sets this after the navigator answers. Do not treat this section as decided.

## 4. Package renames

Spec 11 section 4 rows, with the released names (`git ls-tree origin/main` in sequeless-ontology). Class names stay (spec 11 section 4): `JenaOntologyPort`, `SnapshotMapper`, `OntologyAdministration`.

| From (prototype) | To (released) | Note |
|---|---|---|
| `org.sequeless.spi.meta.schema` | `org.sequeless.ontology.schema.api`; `SchemaSource` in `org.sequeless.ontology.schema.spi` | ADR 0001 api/spi split |
| `org.sequeless.spi.meta.behaviour` | `org.sequeless.ontology.behaviour.api`; `BehaviourSource` in `.spi` | |
| `org.sequeless.spi.meta.automation` | `org.sequeless.ontology.automation.api`; `AutomationSource` in `.spi` | |
| `org.sequeless.spi.ontology` | `org.sequeless.ontology.api` | `OntologyAdministration` and its result types |
| `org.sequeless.spi.object` (`OntologyDocumentStore`, `StoredOntologyDocument`) | `org.sequeless.ontology.spi` | D19 |
| `org.sequeless.adapter.ontology.jena`, moved types | `org.sequeless.ontology.jena` (public: `JenaOntologyPort`, `ReasonerSetting`, `JenaOntologySettings`, `ShaclValidationPort`) and `org.sequeless.ontology.jena.internal` (compiler) | Spec 11 Q2. The prototype's `org.sequeless.adapter.ontology.jena` package stays for the wiring |
| `org.sequeless.testkit.ontology`, and the ontology part of `org.sequeless.testkit.Fixtures` | `org.sequeless.ontology.testfixtures` | #13 decision 10 |
| `org.sequeless.testkit.Fixtures`, general part | unchanged | |

Validation adapter mapping (the Command side keeps `ValidationPort`, spec 11 Q1):

| Prototype, stays | Released, `ontology-core` (`org.sequeless.ontology.api`) |
|---|---|
| `ValidationPort.validate(Scope, Schema, BusinessObject)` returns `List<Violation>` (`spi/validation/ValidationPort.java` line 57) | `ShapeValidationPort.validate(Scope, ValidationSchema, ValidatableObject)` returns `List<ShapeViolation>` |
| `Schema` | `ValidationSchema` |
| `BusinessObject` | `ValidatableObject` |
| `Violation` | `ShapeViolation` |

The prototype's `ShaclValidationPort` (wiring module) implements `ValidationPort` and delegates to the released `org.sequeless.ontology.jena.ShaclValidationPort`, which implements `ShapeValidationPort`. The two classes share a simple name, so the wiring refers to the released one by its fully qualified name.

## 5. Modules and dependencies

### 5.1 Root POM

- `sequeless-bom` 0.1.2 becomes 0.1.3 (`pom.xml` lines 63 and 64).
- The module list (`pom.xml` lines 24 to 32) is unchanged. No module is added or removed.
- The parent `sequeless-build-parent` 0.1.2 (line 8) is unchanged.
- The GitHub Packages repository is already declared (`pom.xml` line 202).

### 5.2 Per module

Non-moving Java files that reference a moving type, from the inventory's By-module table (its Java-file count minus its moving-file count): sequeless-spi 9, sequeless-core 36, sequeless-app 32, testkit 13, Jena module 17, postgres 4.

| Module | Change | POM additions | Notes |
|---|---|---|---|
| `sequeless-spi` | Delete the moved packages (2.1). Rewire the 9 non-moving files that reference a moving type (inventory count) in `spi/object`, `spi/automation`, `spi/derivation`, `spi/query` and `spi/validation` (section 4) | `ontology-core` (`OntologyDocumentStore` in `ObjectStorePort`); `schema-contract` (`Schema` in `QueryPort`, `ValidationPort`, `TypeRef`, `DerivationContext`); `behaviour-contract` (`PluginRule` and `RollupRule` in `DerivationPlugin`; behaviour types in `spi/automation`) | No non-moving spi code imports `org.sequeless.spi.meta.automation`; `spi/package-info.java` names it in javadoc only. So no `automation-contract` |
| `sequeless-spi-testkit` | Delete `testkit/ontology/`, the six resources, the 41 ontology members of `Fixtures` (keep the 3 general), the in-memory store in `InMemoryObjectStorePort` and the two `ObjectStoreContract` clauses. Rewire 10 main files (`WorkloadDerivationPlugin`, `ObjectStoreContract`, `Fixtures`, `query/*`, `validation/*`, `object/package-info`) and 3 test files (`InMemoryObjectStorePort`, `InMemoryAggregateObjectStorePort`, `InMemoryAggregateQueryPort`) | `schema-contract`, `behaviour-contract`, `ontology-core`; `ontology-testfixtures` (compile, since main code uses the `Fixtures` ontology members) | `EMAIL_IRI` and `NAME_IRI` are blocked (2.2, open item 1) |
| `sequeless-core` | Rewire the 36 files (imports and javadoc, section 4). `DerivationPlanner.java` line 177 passes `rollup.criteria()` into `AggregateRequest`; after the cut-over that value is `Optional<FilterNode>`, so the line depends on the section 3 bridge. Tests that build a `RollupRule` change with the type (spec 11 section 3.3 list) | `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract` (`DefaultTransitionService` uses automation types); `filter-core` only if the bridge sits in core | `expression-api` is unchanged |
| `sequeless-app` | Rewire the 32 files (22 main, 10 test). `TypeResponseMapper.java` lines 303 to 305 render `rollup.criteria()`, so its rendering changes with the type. Delete `OntologyFixtureDriftTest.java`. Change `ArchitectureTest` (section 6) | `ontology-testfixtures` (test scope): `ApiPathPrefixTest` and `TypesEndToEndTest` use ontology members | The app keeps its own `reference.ttl` (no code change) |
| `sequeless-adapter-ontology-jena` (wiring) | Keeps `JenaOntologyAutoConfiguration`, `JenaOntologyProperties`, `ShaclValidationAutoConfiguration`, `JenaOntologyAdapterDescriptorProvider`, `ShaclValidationAdapterDescriptorProvider`, `ShaclValidationPort` (rewritten, section 4), `package-info.java` (javadoc reworded), the two `META-INF` files, and six tests (`JenaOntologyAutoConfigurationTest`, `JenaOntologyPropertiesTest`, `JenaOntologyAdapterDescriptorProviderTest`, `ShaclValidationAutoConfigurationTest`, `ShaclValidationAdapterDescriptorProviderTest`, `ShaclValidationPortContractTest`). The glue calls the released `JenaOntologyPort.fromSource` and `fromStore` (`JenaOntologyAutoConfiguration` lines 66 and 73). `JenaOntologyProperties.settings()` (line 100) returns the released `JenaOntologySettings`. `ShaclValidationAutoConfiguration` line 42 builds the adapter `ShaclValidationPort` | `ontology-jena-adapter` (compile); `ontology-testfixtures` (test) | The released factories' parameter lists come from spec 11 section 3.1 and are not re-checked here |
| `sequeless-adapter-persistence-postgres` | Rewire `PostgresObjectStore` (`OntologyDocumentStore`), `PostgresOntologyDocumentStore` (`OntologyDocument`, `OntologyDocumentStore`, `OntologyFormat`, `StoredOntologyDocument`) and `PostgresQueryStore` (`AggregateFunction`, `AttributeDefinition`, `Datatype`, `PropertyDefinition`, `RelationshipDefinition`, `Schema`, `TypeDefinition`). The test `PostgresQueryStoreIndexUsageIT` uses `AggregateFunction` and seven ontology members, all present in the released `Fixtures` | `ontology-core`, `schema-contract` (compile); `ontology-testfixtures` (test) | These three main files are not in spec 11; the inventory lists them as unexpected consumers |
| `sequeless-adapter-authz-permitall`, `-automation-temporal`, `-automation-inprocess` | No change. The authz test uses only general `Fixtures` members. The temporal hit is a javadoc reference to the general `Fixtures#scope`. The inprocess module has no Java hits | none | |

### 5.3 Classpath resources

- `ontology/reference.ttl`, `inconsistent.ttl`, `inverse-transitive.ttl` and `reference-plugin.ttl` come from `ontology-testfixtures` after the deletion. The bytes are identical (2.2).
- `ontology/unknown-plugin.ttl` stays in the testkit. `UnknownDerivationPluginStartupTest` in sequeless-app loads it (inventory, classpath section).
- `ontology/sq-schema.ttl` and `ontology/sq-behaviour.ttl` are loaded by the moved `ImportResolver` from the classpath. The released `ontology-jena-adapter` has the same paths (`src/main/resources/ontology/`).

## 6. ArchitectureTest changes

Source: `sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java`. It analyzes `org.sequeless` with tests excluded (`@AnalyzeClasses`, line 35). It has 17 `@ArchTest` rules, and all 17 stay. The package constants on lines 44 to 46 and 48 change. `QUERY_PACKAGE` (line 47) stays, but it leaves the behaviour allowed set. A new constant `ONTOLOGY_CONTRACTS_PACKAGE = "org.sequeless.ontology.."` is used in the allowed sets, so that `ONTOLOGY_PACKAGE` keeps its meaning in `querySeesOnlySchema`.

### 6.1 Prototype rules

| Rule (line) | Change in spec 12 |
|---|---|
| `spiDependsOnlyOnJdkAndItself` (65) | Allowed set gains `ONTOLOGY_CONTRACTS_PACKAGE`, because spi imports the released ports and types. The `because` text names the kernel only and is updated |
| `coreDependsOnlyOnJdkSpiAndItself` (87) | Allowed set gains `ONTOLOGY_CONTRACTS_PACKAGE`, and `org.sequeless.filter..` if the section 3 bridge is in core. A Jackson type in a core signature (for example `JsonNode` from `FieldFilter.value()`) would fail the rule. Depends on section 3 |
| `schemaDependsOnlyOnJdkKernelAndItself` (208) | `SCHEMA_PACKAGE` (line 44) becomes `org.sequeless.ontology.schema..`, was `..spi.meta.schema..`. Allowed set unchanged |
| `behaviourDependsOnlyOnSchema` (219) | `BEHAVIOUR_PACKAGE` (line 45) becomes `org.sequeless.ontology.behaviour..`. The allowed `QUERY_PACKAGE` is replaced by `org.sequeless.filter.api..`, as spec 11 section 5.4 gives for the released rule. The `because` text cites spec 10 section 11 and is rewritten |
| `automationDependsOnlyOnBehaviourAndSchema` (233) | `AUTOMATION_PACKAGE` (line 46) becomes `org.sequeless.ontology.automation..`. Allowed set unchanged: behaviour stays allowed (Q10) |
| `querySeesOnlySchema` (245) | `BEHAVIOUR_PACKAGE` and `AUTOMATION_PACKAGE` as above. `ONTOLOGY_PACKAGE` (line 48) becomes `org.sequeless.ontology.api..`, not `org.sequeless.ontology..`, which would forbid the `Schema` the query port uses |
| `suiteActuallyAnalyzesEveryModule` (284) | The assertion on `org.sequeless.adapter.ontology.jena.JenaOntologyPort` becomes `org.sequeless.ontology.jena.JenaOntologyPort` |
| Unchanged | `spiAndCoreDoNotDependOnSpring` (100), `onlyAppMayDependOnSpringMvc` (112), `onlyAppRestAndConfigMayDependOnCore` (124), `noAdapterDependsOnCore` (137), `adaptersDoNotDependOnEachOther` (148), `noLombokInSpiOrCore` (156), `coreDoesNotDependOnAdapterSpecificLibraries` (167), `onlyTemporalAdapterMayDependOnTemporal` (180), `noJenaInSpiOrCore` (192), `onlyHostPreparesIndexes` (261) |

Two behaviours are unverified until the test runs. First, the retargeted rules check the released classes only if ArchUnit imports the dependency jars for `org.sequeless`. Second, a rule whose subject matches no class fails unless `allowEmptyShould(true)` is set (the ArchUnit default, unverified here).

### 6.2 Released rules (copied from spec 11 section 5.4; not run here)

The released `BoundaryRulesTest` files exist in six modules (`git ls-tree -r --name-only origin/main`). Their rule names are as spec 11 gives them; they were not checked against the released files.

| Module | Rule | Source |
|---|---|---|
| `ontology-core` | `api_and_spi_types_are_public`, without `allowEmptyShould` | 5.4 |
| `schema-contract` | `schemaDependsOnlyOnJdkKernelAndItself` (port of line 208) | 5.4 |
| `behaviour-contract` | `behaviourDependsOnlyOnSchema`, with `org.sequeless.filter.api..` in place of `org.sequeless.spi.query..` | 5.4 |
| `automation-contract` | `automationDependsOnlyOnBehaviourAndSchema`, behaviour allowed | 5.4, Q10 |
| `ontology-jena-adapter` | `api_and_spi_types_are_public` dropped (no api package); `ontology_jena_adapter_does_not_reach_ontology_core_internals`; `ontology_core_does_not_depend_on_owl_adapter` renamed | 5.4, Q2 |
| Each contract | api and spi types are public | 5.4 |

## 7. Vocabulary files

- The copies are `sq-schema.ttl` and `sq-behaviour.ttl` in `sequeless-spi-testkit/src/main/resources/ontology/` and in `sequeless-adapter-ontology-jena/src/main/resources/ontology/`. The released copies are in `ontology-jena-adapter/src/main/resources/ontology/` in sequeless-ontology.
- The byte-identity check runs before deletion. All four comparisons must print nothing:

```
for f in sq-schema.ttl sq-behaviour.ttl; do
  cmp <(git -C <sequeless-ontology checkout> show origin/main:ontology-jena-adapter/src/main/resources/ontology/$f) sequeless-spi-testkit/src/main/resources/ontology/$f
  cmp <(git -C <sequeless-ontology checkout> show origin/main:ontology-jena-adapter/src/main/resources/ontology/$f) sequeless-adapter-ontology-jena/src/main/resources/ontology/$f
done
```

- At drafting, all four pairs are byte-identical. The first 20 hex characters of the SHA-256 of the two prototype copies are `819bb6e426aa3e8a5bd2` (`sq-schema.ttl`) and `2b4877f1e50a77416193` (`sq-behaviour.ttl`).
- The cut-over deletes the four copies (2.1). The classpath paths do not change, so `ImportResolver` still resolves them from the released jar.
- Deleting them ends the cross-repo vocabulary invariant recorded in `docs/extraction/remaining-work.md` (the item "Cross-repo vocabulary invariant (temporary, open until spec 12)"). After this spec, nothing compares the two sets of files: `OntologyFixtureDriftTest` (sequeless-app) compares them by path, cannot run once a file is gone, and is deleted with them. The app's own `reference.ttl` loses its sibling, so its byte check goes too.

## 8. Release and BOM

- None. The prototype is not released, and this spec changes no prototype version.
- The root POM moves from `sequeless-bom` 0.1.2 to 0.1.3 (section 5.1). No BOM release is made here. Spec 11 released `sequeless-bom` 0.1.3 (`sequeless-build` #46, release #47, per `remaining-work.md`).

## 9. CI and workflows

- `.github/workflows/ci.yml` line 13 calls `sequeless/sequeless-build/.github/workflows/maven-ci.yml@workflows-v0.1.0`. The workflow grants `packages: read` (line 9). `codeql.yml` pins `workflows-v0.1.1` (line 15). Neither pin changes here: `remaining-work.md` keeps the codeql pin by decision. The reusable workflow's `java-version` input defaults to `'25'` at `workflows-v0.1.0` (checked with `gh api`).
- Rule: the prototype's CI runs against released artifacts, never a SNAPSHOT on `main`. After the cut-over, every ontology dependency comes from the BOM at 0.1.0. The prototype's own module versions are `0.1.0-SNAPSHOT`. That is the project's version, not a dependency.
- Dependency check, before the gate. This prints nothing when every SNAPSHOT is the project's own version:

```
git grep -n -- SNAPSHOT -- '*pom.xml' | grep -v '<version>0.1.0-SNAPSHOT</version>'
```

- Gate, run by the driver (this spec ran no `mvn`):

```
source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify
```

  Run it on JDK 25: the sdkman `25.0.3-tem` install on the driver's machine, with `JAVA_HOME` set to it. `./mvnw` does not exist in this repo (`git ls-files | grep -c '^mvnw'` returns 0), so the gate runs `mvn`. The `release` profile comes from `sequeless-build-parent` 0.1.2 (section 5.1). Its presence is unverified in this draft.
- The PR's CI run is the proof for the first clause of section 11. The BOM's CI is outside this spec.

## 10. ADRs

- No new ADR in this spec.
- ADRs in sequeless-ontology that govern the consumed artifacts (`docs/adr/` at `origin/main`):

| ADR | Status (file header) | What it governs here |
|---|---|---|
| 0001 `adopt-a-maven-reactor-with-an-api-spi-internal-hexagon` | accepted | The api/spi/internal split that the prototype's imports follow. Two clauses are superseded by 0006 |
| 0004 `thin-core-jena-adapter-independent-versions` | proposed; amends 0001 | `ontology-core` is thin (`OntologyAdministration`, the validation api, the `OntologyDocumentStore` spi). `ontology-jena-adapter` is Spring-free. Per-module versions |
| 0005 `facet-core-moves-to-sequeless-query` | proposed; supersedes 0002 | Not consumed by this spec. The facet modules are frozen |
| 0006 `supersede-0001-in-part` | proposed | Supersedes 0001 in part: per-module versions, and a thin `ontology-core` |

ADR 0003 is not in sequeless-ontology. Spec 11 Q7 puts it in sequeless-query.

## 11. Definition of done

Copied from issue #40:

- The new repo's CI is green.
- The prototype's CI is green against *released* artifacts (never a SNAPSHOT on `main`).
- The BOM's CI, which runs every testkit contract, is green.
- The release and the BOM re-release are done.
- For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA.
- The cut-over commit names sequeless-ontology and the import commit `b2ff8a6`.

## 12. Open items for the driver

Each item says what is unverified or needs a decision, and the command that settles it. Commands use `<sequeless-ontology checkout>` for a local checkout of sequeless-ontology, and repo-relative paths otherwise.

1. **Fixtures gap (blocks the full 41-member move).** Six prototype ontology members are missing from the released `Fixtures` (2.2). `EMAIL_IRI` and `NAME_IRI` are used by three testkit files that stay. Options: (a) add them to `ontology-testfixtures` in sequeless-ontology and release 0.1.1 before this cut-over, then bump the BOM; (b) keep those two in a residual prototype class; (c) other. Check: `git -C <sequeless-ontology checkout> show origin/main:ontology-testfixtures/src/main/java/org/sequeless/ontology/testfixtures/Fixtures.java | grep -c 'EMAIL_IRI\|NAME_IRI'` (today 0).

2. **RollupRule timing.** The brief said the prototype's `RollupRule` keeps `List<Criterion>` until spec 14. Spec 11 section 3.3 says "until spec 12" (`docs/extraction/spec-11-sequeless-ontology.md` line 238), and line 240 says `AggregateRequest` keeps `List<Criterion>` until spec 14. This draft follows spec 11. The cut-over deletes `spi/meta/behaviour`, so the prototype's `RollupRule` becomes the released one (`Optional<FilterNode>`), and only `AggregateRequest` stays on `List<Criterion>`. Check: `grep -n 'until spec' docs/extraction/spec-11-sequeless-ontology.md`.

3. **SHACL and settings files.** The released adapter has `ShaclDataGraphBuilder`, `JenaOntologySettings` and a public `ShaclValidationPort` (S2b). This draft deletes the prototype's `ShaclDataGraphBuilder`, `JenaOntologySettings` and `ShaclDataGraphBuilderTest`, and keeps `ShaclValidationPortContractTest` for the prototype's adapter. Spec 11 section 2.2 and Q1 still say `ShaclDataGraphBuilder` stays. Spec 11 table 2.2 also omits `JenaOntologySettings`, though its table 4 and OQ-10 move it. Check: `git -C <sequeless-ontology checkout> ls-tree -r --name-only origin/main ontology-jena-adapter/src/test | grep -i shacl`.

4. **ArchitectureTest.** This draft retargets the schema, behaviour and automation rules (6.1) and keeps all 17 rules. The released contract modules run the same rules in their `BoundaryRulesTest` files, so deleting the prototype copies is also possible. Decide, then run `ArchitectureTest` in sequeless-app on the cut-over branch (driver run; not run here). The run also confirms the classpath scan and the empty-should behaviour (6.1).

5. **Allowed packages in spi and core.** `org.sequeless.ontology..` is broader than the rewired code may need. Narrow it to the api and spi packages if only those are imported. Check after the rewire: `git grep -h 'import org.sequeless.ontology' -- sequeless-spi/src/main sequeless-core/src/main | sort | uniq -c`.

6. **Bridge and Jackson.** If the section 3 bridge reads `FieldFilter.value()` (a `JsonNode`), core gets a Jackson dependency and rule 87 fails. Check when the bridge is designed, by running `ArchitectureTest`.

7. **Drift test and remaining-work.** `OntologyFixtureDriftTest` is deleted here, but spec 11 OQ-9 said it stays in the prototype. Confirm. The `remaining-work.md` item "Cross-repo vocabulary invariant" closes in this PR or in a docs follow-up. Check the item: `grep -n 'Cross-repo vocabulary invariant' docs/extraction/remaining-work.md`.

8. **Source SHA.** `8ee2360` was computed with the path set in section 2. If the path set changes, recompute with `git log -1 --format=%H origin/main -- <paths>`, and check that `git log <SHA>..origin/main -- <paths>` is empty.

9. **Not verified in this draft.** The prototype CI on the PR, the `bom-contracts` run (sequeless-build #48, merged as `a871904` per `remaining-work.md`), the gate itself, and the `release` profile of `sequeless-build-parent` 0.1.2. Check: `gh pr view 48 -R sequeless/sequeless-build --json state,mergeCommit`, `gh pr checks <cut-over PR>`, and the gate in section 9.

10. **Placeholders.** `<cut-over date>` (section 2) and the cut-over PR number, which does not exist yet.
