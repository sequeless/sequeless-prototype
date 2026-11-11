# Spec 12 — Prototype stage 3 cut-over: consume ontology

Issue: [#40](https://github.com/sequeless/sequeless-prototype/issues/40). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#13](https://github.com/sequeless/sequeless-prototype/issues/13), [#39](https://github.com/sequeless/sequeless-prototype/issues/39). Tracker: `docs/extraction/remaining-work.md`.

Status: draft, revised after the first review, not reviewed by the owner. Section 3 is pending. Open items are in section 12.

Facts are checked at prototype `origin/main` `611d2f0` and at sequeless-ontology `c9726a7` (the 0.1.0 release commit). Where a count or claim depends on a command, the command is named in place.

## 1. Identity, stage and dependencies

- Stage 3, cut-over. Spec 12 of 19. The prototype stops carrying the ontology code that spec 11 copied to `sequeless-ontology`, and consumes the released artifacts instead.
- Waits for spec 11 ([#39](https://github.com/sequeless/sequeless-prototype/issues/39)), which is done. Six modules are released at 0.1.0 from `c9726a7` (tags in section 2). The S2b validation api (sequeless-ontology #14) is in that release. `sequeless-bom` 0.1.3 is published and lists the six artifacts at 0.1.0 (`remaining-work.md`, spec 11 slices S4 and S5a; checked with `gh release list` and the BOM pom at tag `sequeless-bom-v0.1.3`).
- Blocks [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (issue metadata).
- Branch `spec-12-prototype-stage3-cutover`, based on `origin/main` `611d2f0`.
- Consumed artifacts, all 0.1.0, version managed by the BOM (no version in module POMs): `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-jena-adapter`, `ontology-testfixtures`. `filter-core` comes in through `behaviour-contract` (spec 11 section 5.2). The BOM lists `filter-core` 0.1.3, and 0.1.4 is released (sequeless-filter #29). `remaining-work.md` tracks the stale BOM versions; spec 12 does not change them.
- The root POM imports `sequeless-bom` 0.1.2 (`pom.xml` line 64). Changing it to 0.1.3 is the only BOM edit in this spec.

## 2. Moves

Provenance:

| Field | Value |
|---|---|
| Source repo | `sequeless/sequeless-prototype` |
| Source SHA | `8ee2360` (`feat(ontology): reject comparisons on non-numeric properties at load (#39) (#100)`). Recomputed at `611d2f0` with `git log -1 --format=%H origin/main -- <paths>` over the path set in the note below; the result is still `8ee2360` |
| Import commit | `b2ff8a6` in sequeless-ontology (sequeless-ontology #9), titled `feat(ontology): import the ontology from sequeless-prototype@8ee2360`. The title's source SHA matches the row above. Review fixes `76f5d43` come after it |
| Release tags | `ontology-core-v0.1.0`, `schema-contract-v0.1.0`, `behaviour-contract-v0.1.0`, `automation-contract-v0.1.0`, `ontology-jena-adapter-v0.1.0`, `ontology-testfixtures-v0.1.0`, all from `c9726a7` |
| Date | The author date of the cut-over commit |

Note on the SHA. The path set is every path named in 2.1: all 12 Jena main files (`JenaOntologyPort`, `SnapshotMapper`, `ConsistencyChecker`, `ImportResolver`, `OntModelBuilder`, `TurtleExporter`, `SqVocabulary`, `SqAnnotations`, `OntologySource`, `ReasonerSetting`, `JenaOntologySettings`, `ShaclDataGraphBuilder`), the 12 Jena test files, the two Jena vocabulary files, the spi `meta/` and `ontology/` packages (main and test), `OntologyDocumentStore.java`, `StoredOntologyDocument.java` and `StoredOntologyDocumentTest.java`, the testkit `ontology/` packages (main and test), the six testkit vocabulary and resource files, and the in-file edits to `testkit/Fixtures.java`, `InMemoryObjectStorePort.java`, `ObjectStoreContract.java` and `OntologyFixtureDriftTest.java`. That is 107 paths. `git log 8ee2360..origin/main -- <paths>` is empty at `611d2f0`, so no later commit touches them. The cut-over commit body lists the same paths.

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
| sequeless-spi-testkit, in-file | the 39 ontology members of `testkit/Fixtures.java` (the 41 ontology members minus `EMAIL_IRI` and `NAME_IRI`, which are declared locally, 2.2; the 3 general members stay); the private in-memory store in `InMemoryObjectStorePort`; the two `ontologyDocuments` clauses of `ObjectStoreContract` (lines 184 and 403) | n/a | n/a | `InMemoryOntologyDocumentStore` (`public final class`), `OntologyDocumentStoreContract` (D19) |
| sequeless-app, test | in-file only: the two `sq-*` methods of `OntologyFixtureDriftTest.java`. The class and its `reference.ttl` method stay (section 7) | 0 | 0 | none |
| sequeless-adapter-persistence-postgres | none deleted. Three main files and one test file are rewired (section 5.2) | 0 | 0 | not applicable |

Totals: 64 main Java files (spi 49, Jena 12, testkit 3), 31 test Java files (spi 18, Jena 12, testkit 1), and 8 resource files (Jena 2, testkit 6): 103 files in all. The app test file is edited, not deleted.

Notes on the inventory column:

- sequeless-spi test: the inventory lists `StoredOntologyDocumentTest` as a probable mover, not a moving file. Spec 11 D19 moves it with its type, so it is counted here.
- Jena main: the inventory does not count `JenaOntologySettings` or `ShaclDataGraphBuilder`. Spec 11 table 4 moves the settings record, and the released adapter has both files (open item 3).
- Jena test: the inventory lists seven of the moved tests as probable movers and four as rewire candidates, so its moving count for the module is 0. This table follows spec 11 section 2.

### 2.2 Fixtures split and the gap

The prototype `Fixtures` class has 44 static declarations (`grep -cE '^\s+(public |private )?static ' sequeless-spi-testkit/src/main/java/org/sequeless/testkit/Fixtures.java`). Three are general (`OPAQUE_RESOURCE`, `defaultScope`, `scope`), so 41 are ontology members. The released `ontology-testfixtures` `Fixtures` at `c9726a7` has 36 distinct static member names, one of them the general `defaultScope`, so 35 ontology members are shared. Six prototype ontology members are missing from it:

| Member | Prototype consumers outside `Fixtures.java` (`git grep -l -w`) | Ruling |
|---|---|---|
| `EMAIL_IRI` | `QueryContract`, `QueryFixtures`, `ValidationContract` (testkit main, which stay) | declared locally (below) |
| `NAME_IRI` | the same three files | declared locally (below) |
| `OWNER_IRI` | none. The two core tests (`DefaultActionExecutorTest`, `DefaultTransitionServiceTest`) declare their own private `OWNER_IRI` | dropped with the move |
| `INCONSISTENT_ONTOLOGY_IRI` | none | dropped with the move |
| `UNKNOWN_PLUGIN_TURTLE` | none. The `Fixtures` member is private (line 152); `UnknownPluginActivationTest` declares its own private constant (line 42) | dropped with the move |
| `unknownPluginOntology()` | none | dropped with the move |

Ruling (open item 1, option c). `EMAIL_IRI` and `NAME_IRI` are declared locally, with the prototype's values (`REFERENCE_ONTOLOGY_IRI + "#email"` and `REFERENCE_ONTOLOGY_IRI + "#name"`):

- public constants in `QueryFixtures` (testkit, which stays). `QueryFixtures` and `QueryContract` read them from there;
- private constants in `ValidationContract` (testkit, which stays).

The other four missing members have no kept consumer, so none is declared locally. The move is therefore 39 members: the 41 ontology members minus the two declared locally. No owner decision and no release are needed. The reviewer checks this list with `git grep -n -w` over the six names: the only kept uses are the three files above.

The `Fixtures` importers are 24 files, of which 19 use the ontology part (inventory). The four classpath resources the moved code loads (`reference.ttl`, `inconsistent.ttl`, `inverse-transitive.ttl`, `reference-plugin.ttl`) are byte-identical in `ontology-testfixtures` and in the prototype's testkit copies (`cmp` at `c9726a7` and `611d2f0`).

## 3. Prerequisite refactors

PENDING: the FilterNode-to-Criterion bridge is either a prerequisite PR before this cut-over, or part of it. The driver sets this after the navigator answers. Do not treat this section as decided.

The bridge's Value reconstruction and REST summary compatibility are to be specified after the driver's decision; the two facts the design must meet are that the JSON form gives back a plain string with no Datatype (`FieldFilter.value` is a Jackson `JsonNode` at sequeless-filter `9ec6195`), and that the REST rollup summary string `count(Task via belongsToProject where status ne 'done')` is pinned by `sequeless-app/src/test/java/org/sequeless/app/TypesEndToEndTest.java` line 116 and by `sequeless-ui/projects/testing/src/lib/fixtures/project-type-detail.ts` line 101.

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
| `BusinessObject` | `ValidatableObject(ObjectId id, String typeIri, Map<String, Value> propertiesByIri)` |
| `Violation` | `ShapeViolation` |

The `BusinessObject` mapping copies the id, takes `typeIri` from the object's `TypeRef.iri()`, and turns each `PropertyRef` key into its `iri()` string. `BusinessObject` also carries tenant, version, state and audit, which `ValidatableObject` does not take.

Route (decided). The prototype's Command-side validation adapter, in `sequeless-adapter-ontology-jena` next to `ShaclValidationPort`, implements the public ontology-core interface `ValidationSchema` itself. The interface has three methods: `hasType`, `typeAndSupertypes` and `xsdDatatypeOf` (`ontology-core/src/main/java/org/sequeless/ontology/api/ValidationSchema.java` at `c9726a7`). The adapter delegates to the public API of the released schema-contract `Schema` for the type lookup, the supertype walk and the attribute datatype lookup. It does not use `SchemaValidationSchema`. In `ontology-jena-adapter` that class is `final`, package-private, with a package-private constructor, so the prototype cannot reach it. The route adds no public API to `sequeless-ontology` and needs no release. The adapter's class name is chosen at implementation. The reviewer's check of this route is in 5.2.

The prototype's `ShaclValidationPort` (wiring module) implements `ValidationPort` and delegates to the released `org.sequeless.ontology.jena.ShaclValidationPort`, which implements `ShapeValidationPort`. The two classes share a simple name, so the wiring refers to the released one by its fully qualified name.

## 5. Modules and dependencies

### 5.1 Root POM

- `sequeless-bom` 0.1.2 becomes 0.1.3 (`pom.xml` lines 63 and 64).
- The module list (`pom.xml` lines 24 to 32) is unchanged. No module is added or removed.
- The parent `sequeless-build-parent` 0.1.2 (line 8) is unchanged.
- The GitHub Packages repository is already declared (`pom.xml` line 202).

### 5.2 Per module

Rewire counts are the files that name a moved type, found by `git grep` outside the paths of 2.1. They are not computed by subtracting the inventory's moving count, which does not work for modules that contain moving files. The compiler is the authority for the rewire.

sequeless-spi has 17 such files: `automation/AutomationPort`; `derivation/DerivationContext`, `DerivationPlugin`, `package-info`; `object/ObjectStorePort`, `OutboxEntry`, `OutboxPort`, `StaleObjectException`, `TypeRef`, `package-info`; `spi/package-info`; `query/AggregateRequest`, `AggregateResult`, `QueryPort`, `package-info`; `validation/ValidationPort`, `package-info`.

| Module | Change | POM additions | Notes |
|---|---|---|---|
| `sequeless-spi` | Delete the moved packages (2.1). Rewire the 17 non-moving files listed above (section 4). Javadoc links to moved packages are rewritten (5.5) | `ontology-core` (`OntologyDocumentStore` in `ObjectStorePort`); `schema-contract` (`Schema` in `QueryPort`, `ValidationPort`, `TypeRef`, `DerivationContext`); `behaviour-contract` (`PluginRule` and `RollupRule` in `DerivationPlugin`; behaviour types in `spi/automation`) | No non-moving spi code imports `org.sequeless.spi.meta.automation`; `spi/package-info.java` names it in javadoc only. So no `automation-contract` |
| `sequeless-spi-testkit` | Delete `testkit/ontology/`, the six resources, the 39 ontology members of `Fixtures` (keep the 3 general), the in-memory store in `InMemoryObjectStorePort` (which switches to the released public `InMemoryOntologyDocumentStore`) and the two `ObjectStoreContract` clauses. Rewire 10 main files (`WorkloadDerivationPlugin`, `ObjectStoreContract`, `Fixtures`, `query/*`, `validation/*`, `object/package-info`) and 3 test files (`InMemoryObjectStorePort`, `InMemoryAggregateObjectStorePort`, `InMemoryAggregateQueryPort`) | `schema-contract`, `behaviour-contract`, `ontology-core`; `ontology-testfixtures` (compile, since main code uses the `Fixtures` ontology members); `filter-core` and `jackson-databind` (5.4) | `EMAIL_IRI` and `NAME_IRI` are declared locally in `QueryFixtures` (public) and `ValidationContract` (private), 2.2 |
| `sequeless-core` | Rewire the 36 files (imports and javadoc, section 4). `DerivationPlanner.java` line 177 passes `rollup.criteria()` into `AggregateRequest`; after the cut-over that value is a `FilterNode`, so the line depends on the section 3 bridge. Tests that build a `RollupRule` change with the type (spec 11 section 3.3 list) | `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract` (`DefaultTransitionService` uses automation types); `filter-core` and `jackson-databind` if the bridge uses them (5.4). The `ArchitectureTest` rule for core changes with them (6.1) | `expression-api` is unchanged |
| `sequeless-app` | Rewire the 32 files (22 main, 10 test). `TypeResponseMapper.java` lines 303 to 305 render `rollup.criteria()`, so its rendering changes with the type (section 3). Keep `OntologyFixtureDriftTest.java`, but delete its two `sq-*` methods and retarget `appReferenceTurtleIsByteIdenticalToTestkits` (section 7). Change `ArchitectureTest` (section 6) | `ontology-testfixtures` (test scope); `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract` (5.4) | The app keeps its own `reference.ttl` (no code change). Its other classpath resources come from the jar (5.3) |
| `sequeless-adapter-ontology-jena` (wiring) | Keeps `JenaOntologyAutoConfiguration`, `JenaOntologyProperties`, `ShaclValidationAutoConfiguration`, `JenaOntologyAdapterDescriptorProvider`, `ShaclValidationAdapterDescriptorProvider`, `ShaclValidationPort` (rewritten, section 4), `package-info.java` (javadoc reworded), the two `META-INF` files, and six tests (`JenaOntologyAutoConfigurationTest`, `JenaOntologyPropertiesTest`, `JenaOntologyAdapterDescriptorProviderTest`, `ShaclValidationAutoConfigurationTest`, `ShaclValidationAdapterDescriptorProviderTest`, `ShaclValidationPortContractTest`). The glue calls the released `JenaOntologyPort.fromSource` and `fromStore` (`JenaOntologyAutoConfiguration` lines 66 and 73). `JenaOntologyProperties.settings()` (line 100) returns the released `JenaOntologySettings`. `ShaclValidationAutoConfiguration` line 42 builds the adapter `ShaclValidationPort`. The Command-side validation adapter (section 4) lives here too | `ontology-jena-adapter` (compile); `ontology-core` and `schema-contract` (compile, since the Command-side adapter names `ValidationSchema` and `Schema`); `ontology-testfixtures` (test); the Jena pins are dropped if unused (5.4) | 8 of the 13 staying Java files reference a moved type: 5 main (`JenaOntologyAdapterDescriptorProvider`, `JenaOntologyAutoConfiguration`, `JenaOntologyProperties`, `ShaclValidationAutoConfiguration`, `ShaclValidationPort`) and 3 test (`JenaOntologyAdapterDescriptorProviderTest`, `JenaOntologyPropertiesTest`, `ShaclValidationPortContractTest`). `package-info.java` names one in javadoc. The released factories' parameter lists come from spec 11 section 3.1 and are not re-checked here |
| `sequeless-adapter-persistence-postgres` | Rewire `PostgresObjectStore` (`OntologyDocumentStore`), `PostgresOntologyDocumentStore` (`OntologyDocument`, `OntologyDocumentStore`, `OntologyFormat`, `StoredOntologyDocument`) and `PostgresQueryStore` (`AggregateFunction`, `AttributeDefinition`, `Datatype`, `PropertyDefinition`, `RelationshipDefinition`, `Schema`, `TypeDefinition`). The test `PostgresQueryStoreIndexUsageIT` uses `AggregateFunction` and seven ontology members, all present in the released `Fixtures`. **Contract coverage:** `PostgresObjectStoreContractIT` extends `ObjectStoreContract`, and its two `ontologyDocuments` clauses are removed from that contract in this PR. A runner class, name chosen at implementation, extends the released `OntologyDocumentStoreContract` (`ontology-testfixtures`, with `freshDocuments()` returning a `PostgresOntologyDocumentStore`) in the same Testcontainers setup, and replaces those two clauses | `ontology-core`, `schema-contract` (compile); `ontology-testfixtures` (test) | These three main files are not in spec 11; the inventory lists them as unexpected consumers |
| `sequeless-adapter-authz-permitall`, `-automation-temporal`, `-automation-inprocess` | No change. The authz test uses only general `Fixtures` members. The temporal hit is a javadoc reference to the general `Fixtures#scope`. The inprocess module has no Java hits | none | |

The Command-side validation adapter needs the public members of the released `Schema` for three things: type lookup, the supertype walk and the attribute datatype lookup. Reviewer check, against `c9726a7`: the public members of the released `schema-contract` `Schema` cover all three. Candidates to check, not yet confirmed as sufficient: `Schema.type(String)`, `TypeDefinition.superTypes()`, `TypeDefinition.properties()` and `AttributeDefinition.datatype()` (with `Datatype.xsdIri()`). If they do not cover all three, section 4's route is wrong and the driver decides again. This spec adds no public API to sequeless-ontology.

### 5.3 Classpath resources

- `ontology/reference.ttl`, `inconsistent.ttl`, `inverse-transitive.ttl` and `reference-plugin.ttl` come from `ontology-testfixtures` after the deletion. The bytes are identical (2.2). The app tests that load `classpath:ontology/reference-plugin.ttl` (`DerivationPluginEndToEndTest`) and `classpath:ontology/inconsistent.ttl` (`InconsistentOntologyStartupTest`) get them from the jar. The app keeps its own `reference.ttl`, which shadows the jar's copy in app tests (section 7).
- `ontology/unknown-plugin.ttl` stays in the testkit. `UnknownDerivationPluginStartupTest` in sequeless-app loads it (inventory, classpath section).
- `ontology/sq-schema.ttl` and `ontology/sq-behaviour.ttl` are loaded by the moved `ImportResolver` from the classpath. The released `ontology-jena-adapter` has the same paths (`src/main/resources/ontology/`).

### 5.4 POM rules

- The parent `sequeless-build-parent` 0.1.2 binds `dependency:analyze-only` to `verify` with `failOnWarning=true` and `ignoreNonCompile=true` (the parent POM in `~/.m2`, lines 234 to 242). The check is skipped where `sequeless.dependency-analyze.skip` is true (default false, line 44). `sequeless-app` sets it to true (`sequeless-app/pom.xml` line 27). Every other module therefore fails on an unused declared dependency or an undeclared one that it uses.
- The parent also binds an enforcer `bannedDependencies` rule on core modules: `org.springframework*`, `org.apache.jena*` and Lombok (parent POM lines 215 to 222, skip property `sequeless.enforcer.core.skip`). The released contract POMs at `c9726a7` declare none of them directly. The gate in section 9 checks the transitive closure.
- Each module declares the artifacts whose packages its code imports or names, and drops those it no longer uses. The lists in 5.2 are a starting point. The exact lists are fixed from the compiler and analyze output at implementation time, by running `source ~/.bashrc.ext && mvn -B -Prelease verify` on JDK 25.
- Jena wiring module (`sequeless-adapter-ontology-jena`): drop `jena-ontapi`, `jena-shacl`, `jena-core` and `jena-arq` (POM lines 42, 58, 93 and 98) if nothing in the module uses them after the rewire. Declare `ontology-core` and `schema-contract` directly, because the Command-side adapter names their types.
- `sequeless-core`: declare `filter-core` and `jackson-databind` if the section 3 bridge uses them. `FieldFilter.value()` is a `JsonNode`, so a call on it records the Jackson dependency.
- `sequeless-spi-testkit`: declare `filter-core` and `jackson-databind`. `QueryFixtures` builds the released `RollupRule` with a `FieldFilter`, which takes a `JsonNode`.
- `sequeless-app`: analysis is skipped, so its POM is not checked by the parent. It still declares the released artifacts its code imports: `ontology-core`, `schema-contract`, `behaviour-contract` and `automation-contract` (its main code imports the spi.meta schema, behaviour and automation packages, and `spi.ontology`, in 9, 4, 1 and 6 files today), and `ontology-testfixtures` at test scope. `PortRegistryContextRunnerTest` imports only the wiring class `JenaOntologyAutoConfiguration`, so its imports do not require `ontology-jena-adapter` at test scope.

### 5.5 Stale references the cut-over PR rewrites or justifies

The cut-over PR rewrites each item below, or justifies it in the PR body. The list comes from the greps named here. The implementation reruns them, because prose hits are not exhaustive.

- **Javadoc.** `git grep -n -E 'org\.sequeless\.(spi\.meta|spi\.ontology|spi\.object\.(OntologyDocumentStore|StoredOntologyDocument)|testkit\.ontology)' -- '*/src/main/*'`, over staying main code. Examples: `sequeless-spi/src/main/java/org/sequeless/spi/package-info.java` (lines 19 to 20), `spi/object/package-info.java` (line 30), the `core/ontology`, `core/derivation` and `core/statemachine` `package-info` files, and the `testkit/{object,query,validation}` `package-info` files. The `{@link org.sequeless.spi.meta.automation}` in `spi/package-info.java` becomes `{@code}`, because spi gets no `automation-contract` (spec 9 precedent). Otherwise `-Prelease` javadoc fails.
- **Docs.** `docs/architecture/sq-vocabulary.md` lines 7 to 8 (testkit vocabulary paths), 16 to 17, 81 and 170 (`spi.meta` FQNs); `docs/architecture/derived-properties.md` line 33; `docs/architecture/automation.md` line 404; `docs/architecture/01-overview.md` line 75 and `README.md` line 181, which say the testkit holds "the reference-domain ontology fixture".
- **UI.** `sequeless-ui/projects/rdf/src/testing/reference-ttl.ts` line 1 (generated from the deleted testkit path) and `sequeless-ui/projects/rdf/src/lib/projections.spec.ts` line 5. The UI's references to sequeless-app's `reference.ttl` stay valid.
- **Resources.** `sequeless-spi-testkit/src/main/resources/ontology/unknown-plugin.ttl` lines 11 and 15 name the testkit's `reference-plugin.ttl`, which moves.
- **POM descriptions.** `sequeless-spi/pom.xml` line 15 ("Imports nothing outside the JDK and sequeless-kernel") and `sequeless-core/pom.xml` line 15 ("Depends only on sequeless-spi and the JDK"). Both become false.
- **Root POM.** The comment at `pom.xml` line 114 says `jena-shacl` "backs the SHACL ValidationPort adapter". Reword it. Keep the Jena 6.1.0 pins, which match the released adapter.

## 6. ArchitectureTest changes

Source: `sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java`. It analyzes `org.sequeless` with tests excluded (`@AnalyzeClasses`, line 35). It has 17 `@ArchTest` members, the suite included. Three rules are deleted: `schemaDependsOnlyOnJdkKernelAndItself` (line 208), `behaviourDependsOnlyOnSchema` (219) and `automationDependsOnlyOnBehaviourAndSchema` (233). Spec 11 section 5.4 ported each one into its owning contract module, and the released `BoundaryRulesTest` files carry the same allowed sets (6.2). A copy here would check jars the prototype cannot fix. Fourteen members remain, and the suite's javadoc rule numbering is updated for the deletions.

Constants. `SCHEMA_PACKAGE` (line 44) is removed, because only the deleted rules use it. `BEHAVIOUR_PACKAGE` and `AUTOMATION_PACKAGE` (lines 45 and 46) stay for `querySeesOnlySchema`, retargeted below. `QUERY_PACKAGE` (line 47) leaves the behaviour allowed set. The released contracts are named by five packages: `org.sequeless.ontology.api..`, `.spi..`, `.schema..`, `.behaviour..` and `.automation..`. This narrowed set excludes `.jena..`, `.internal..` and `.testfixtures..` (open item 5). The allowed sets use these packages, not `org.sequeless.ontology..`. `ONTOLOGY_PACKAGE` (line 48) becomes `org.sequeless.ontology.api..`.

### 6.1 Prototype rules

| Rule (line) | Change in spec 12 |
|---|---|
| `spiDependsOnlyOnJdkAndItself` (65) | Allowed set gains the five released packages, because spi imports the released ports and types. The `because` text names the kernel and the ontology contracts |
| `coreDependsOnlyOnJdkSpiAndItself` (87) | Allowed set gains the five released packages, plus `org.sequeless.filter.api..` and `com.fasterxml.jackson.databind..` (decided, open item 6). This follows from the owner's Option A decision (#39 question 1; spec 11 D16 and O1): `RollupRule.criteria` becomes a `FilterNode`, so core's bridge touches the filter api and, through `FieldFilter.value()`, Jackson. The bridge stays in core (spec 11 section 3.3). Moving it to spi would widen spi's rule instead. The `because` text is rewritten. Core declares both artifacts (5.4) |
| `schemaDependsOnlyOnJdkKernelAndItself` (208) | Deleted (see above) |
| `behaviourDependsOnlyOnSchema` (219) | Deleted (see above) |
| `automationDependsOnlyOnBehaviourAndSchema` (233) | Deleted (see above) |
| `querySeesOnlySchema` (245) | `BEHAVIOUR_PACKAGE` and `AUTOMATION_PACKAGE` become `org.sequeless.ontology.behaviour..` and `org.sequeless.ontology.automation..`. `ONTOLOGY_PACKAGE` becomes `org.sequeless.ontology.api..`, not `org.sequeless.ontology..`, which would forbid the `Schema` the query port uses |
| `suiteActuallyAnalyzesEveryModule` (284) | The assertion on `org.sequeless.adapter.ontology.jena.JenaOntologyPort` (lines 292 to 294) becomes `org.sequeless.ontology.jena.JenaOntologyPort` |
| Unchanged | `spiAndCoreDoNotDependOnSpring` (100), `onlyAppMayDependOnSpringMvc` (112), `onlyAppRestAndConfigMayDependOnCore` (124), `noAdapterDependsOnCore` (137), `adaptersDoNotDependOnEachOther` (148), `noLombokInSpiOrCore` (156), `coreDoesNotDependOnAdapterSpecificLibraries` (167), `onlyTemporalAdapterMayDependOnTemporal` (180), `noJenaInSpiOrCore` (192), `onlyHostPreparesIndexes` (261) |

Two behaviours are unverified until the driver's run. First, the retargeted rules check the released classes only if ArchUnit imports the dependency jars for `org.sequeless`. The suite's assertion on the kernel's `Scope` is the evidence for that import. Second, a rule whose should-clause matches no class fails by default. Allowing empty matches requires `allowEmptyShould(true)`, or `archRule.failOnEmptyShould=false`. No released rule sets either (grep at `c9726a7` finds no `allowEmpty`), so each retargeted rule must match at least one class.

### 6.2 Released rules (names checked at `c9726a7`)

The `BoundaryRulesTest` files of six released modules name these rules (`git grep -n 'ArchRule' c9726a7 -- '*BoundaryRulesTest*'`). Their behaviour is not run here.

| Module | Rules |
|---|---|
| `ontology-core` | `ontology_is_free_of_spring`; `api_and_spi_types_are_public` |
| `schema-contract` | `schema_depends_only_on_jdk_kernel_and_itself`; `api_and_spi_types_are_public` |
| `behaviour-contract` | `behaviour_depends_only_on_schema`; `api_and_spi_types_are_public` |
| `automation-contract` | `automation_depends_only_on_behaviour_and_schema`; `api_and_spi_types_are_public` |
| `ontology-jena-adapter` | `ontology_jena_adapter_depends_only_on_allowed_packages`; `ontology_core_does_not_depend_on_jena_adapter` |
| `ontology-testfixtures` | `contract_runners_stay_adapter_neutral` |

## 7. Vocabulary files

- The copies are `sq-schema.ttl` and `sq-behaviour.ttl` in `sequeless-spi-testkit/src/main/resources/ontology/` and in `sequeless-adapter-ontology-jena/src/main/resources/ontology/`. The released copies are in `ontology-jena-adapter/src/main/resources/ontology/` in sequeless-ontology, at `c9726a7`.
- The byte-identity check runs before deletion, against the pinned release commit. All four comparisons must print nothing:

```
for f in sq-schema.ttl sq-behaviour.ttl; do
  cmp <(git -C <sequeless-ontology checkout> show c9726a7:ontology-jena-adapter/src/main/resources/ontology/$f) sequeless-spi-testkit/src/main/resources/ontology/$f
  cmp <(git -C <sequeless-ontology checkout> show c9726a7:ontology-jena-adapter/src/main/resources/ontology/$f) sequeless-adapter-ontology-jena/src/main/resources/ontology/$f
done
```

- At drafting, all four pairs are byte-identical. The first 20 hex characters of the SHA-256 of the two prototype copies are `819bb6e426aa3e8a5bd2` (`sq-schema.ttl`) and `2b4877f1e50a77416193` (`sq-behaviour.ttl`).
- The cut-over deletes the four copies (2.1). The classpath paths do not change, so `ImportResolver` still resolves them from the released jar.
- `OntologyFixtureDriftTest` (`sequeless-app/src/test/java/org/sequeless/app/OntologyFixtureDriftTest.java`) has three methods. Two compare the vocabulary copies that are deleted, `adapterSqSchemaTurtleIsByteIdenticalToTestkits` and `adapterSqBehaviourTurtleIsByteIdenticalToTestkits`, so those two methods are deleted. The third, `appReferenceTurtleIsByteIdenticalToTestkits`, stays and is retargeted at the jar. It enumerates `getResources("ontology/reference.ttl")`, chooses the `ontology-testfixtures` entry, and compares it with the app's own `reference.ttl`. The check is needed because the app's copy shadows the jar's copy in app tests (`Fixtures.readClasspathResource` uses `Fixtures.class.getResourceAsStream`). Without it, a drift between the two would go unnoticed.
- OQ-9 (`docs/extraction/spec-11-sequeless-ontology.md` line 521) says the drift test stays in the prototype and is not in the target. This spec does not delete the class. It removes two of its methods, so it does not contradict OQ-9. Those two methods compare the copies that spec 11 line 9 assigns to spec 12 for deletion.
- Deleting the copies ends the cross-repo vocabulary invariant (`remaining-work.md`, "Cross-repo vocabulary invariant (temporary, open until spec 12)"). After the cut-over nothing compares two sets of files, because one set is gone.

## 8. Release and BOM

- None. The prototype is not released, and this spec changes no prototype version. The validation adapter needs no release (section 4).
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

  Run it on JDK 25: the sdkman `25.0.3-tem` install on the driver's machine, with `JAVA_HOME` set to it. `./mvnw` does not exist in this repo (`git ls-files | grep -c '^mvnw'` returns 0), so the gate runs `mvn`. The `release` profile is present in `sequeless-build-parent` 0.1.2 (line 307 of its POM in `~/.m2`). The gate also runs the `analyze-only` check of 5.4 in every module except sequeless-app.
- Postgres contract runner (5.2). The new runner is a test under `sequeless-adapter-persistence-postgres/src/test`. The gate and the PR's CI run it, as they run `PostgresObjectStoreContractIT` today, and it uses the same Testcontainers PostgreSQL 18 setup. The ontology document store keeps contract coverage in the released repo as well (`InMemoryOntologyDocumentStoreContractTest` in `ontology-testfixtures`).
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

For this PR, beyond the clauses above:

- Status lines, to fill before merge: the gate on JDK 25 (not run in this draft), and the PR's checks (not run).
- `remaining-work.md`: tick the spec 12 row (line 149); close "Cross-repo vocabulary invariant" (line 225); resolve "SchemaValidationSchema has no public factory" (line 227) by the route in section 4, which needs no factory. The stale BOM filter-versions item (line 228) stays open.
- The PR body names the core rule change (6.1, open item 6) and the Postgres contract runner (5.2).
- The PR closes [#40](https://github.com/sequeless/sequeless-prototype/issues/40). Issue #40 is closed with a comment that links the cut-over PR.

## 12. Open items for the driver

Each item says what is unverified or needs a decision, and the command that settles it. Commands use `<sequeless-ontology checkout>` for a local checkout of sequeless-ontology, and repo-relative paths otherwise.

1. **Fixtures gap (decided, section 2.2).** Ruling (c): `EMAIL_IRI` and `NAME_IRI` are declared locally in `QueryFixtures` (public) and `ValidationContract` (private). The reviewer confirms the six-member list and that no kept file uses another member. Check that the released `Fixtures` has none of the six as a member: `git -C <sequeless-ontology checkout> show c9726a7:ontology-testfixtures/src/main/java/org/sequeless/ontology/testfixtures/Fixtures.java | grep -E 'static .*(EMAIL_IRI|NAME_IRI|OWNER_IRI|INCONSISTENT_ONTOLOGY_IRI|UNKNOWN_PLUGIN_TURTLE)|static .*unknownPluginOntology'` must print nothing.

2. **RollupRule timing (confirmed).** Spec 11 line 238 says the prototype's `RollupRule` keeps `List<Criterion>` until spec 12. The cut-over deletes `spi/meta/behaviour`, so the released `RollupRule` (`criteria` as `FilterNode`) takes its place. Line 240 says `AggregateRequest` keeps `List<Criterion>` until spec 14, so spec 12 needs the `FilterNode`-to-`Criterion` bridge in core. This draft reads both lines that way. Check: `grep -n 'until spec' docs/extraction/spec-11-sequeless-ontology.md`.

3. **SHACL and settings (decided).** The released `ontology-jena-adapter` already has `ShaclDataGraphBuilder` (package-private), `JenaOntologySettings` (public record) and a public `ShaclValidationPort` (implements `ShapeValidationPort`), with `ShaclDataGraphBuilderTest` and `ShaclValidationPortContractTest`. Deleting the prototype's `ShaclDataGraphBuilder`, `JenaOntologySettings` and `ShaclDataGraphBuilderTest` is consistent with spec 11 Q1 and D17. The "stays" rows for SHACL at spec 11 line 94 are superseded (line 106). Check: `git -C <sequeless-ontology checkout> ls-tree -r --name-only c9726a7 ontology-jena-adapter/src | grep -i shacl`.

4. **ArchitectureTest (decided, section 6).** Three rules are deleted. `querySeesOnlySchema` and the suite assertion are retargeted. The driver runs `ArchitectureTest` in sequeless-app on the cut-over branch to confirm the classpath scan and the empty-should behaviour (6.1). Not run in this draft.

5. **Allowed packages (decided, section 6).** The narrowed set (api, spi, schema, behaviour and automation) is applied in the cut-over. Check after the rewire: `git grep -h 'import org.sequeless.ontology' -- sequeless-spi/src/main sequeless-core/src/main | sort | uniq -c` shows only those five packages.

6. **Bridge and Jackson (decided, section 6.1).** This follows from the owner's Option A decision (#39 question 1; spec 11 D16 and O1). Core's rule allows `org.sequeless.filter.api..` and `com.fasterxml.jackson.databind..`, and core declares both artifacts (5.4). Name it in the PR body. Check: the `ArchitectureTest` run in item 4.

7. **Drift test (decided, section 7).** The two `sq-*` methods are deleted. The class and its `reference.ttl` method stay, and that method is retargeted. This does not contradict OQ-9 (spec 11 line 521). Check: `grep -n 'OQ-9' docs/extraction/spec-11-sequeless-ontology.md`. The `remaining-work.md` vocabulary item closes in the cut-over PR (line 225).

8. **Source SHA (verified at `611d2f0`).** `8ee2360` is the last commit touching the 107 paths of section 2. If the path set changes, recompute with `git log -1 --format=%H origin/main -- <paths>` and check that `git log <SHA>..origin/main -- <paths>` is empty.

9. **Not verified in this draft.** The gate itself (section 9), the PR's CI, and the `bom-contracts` run (sequeless-build #48, merged as `a871904` per `remaining-work.md`). Checks: `gh pr view 48 -R sequeless/sequeless-build --json state,mergeCommit` and `gh pr checks <cut-over PR>`.

10. **Placeholders.** The only placeholder left is the cut-over PR number, `<cut-over PR>`. The cut-over date is the author date of the cut-over commit (section 2), by spec 9's rule.
