# Spec 11 — sequeless-ontology gains the prototype's ontology

Issue: [#39](https://github.com/sequeless/sequeless-prototype/issues/39). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#12](https://github.com/sequeless/sequeless-prototype/issues/12), [#13](https://github.com/sequeless/sequeless-prototype/issues/13), [#16](https://github.com/sequeless/sequeless-prototype/issues/16), [#20](https://github.com/sequeless/sequeless-prototype/issues/20), [#24](https://github.com/sequeless/sequeless-prototype/issues/24). Tracker: `docs/extraction/remaining-work.md`.

Status: draft, not reviewed, no PR. Facts were read from `a3a6911` on this branch and from the remote `main` of each repo on 2026-11-08. Items marked **unverified** were not checked in code.

## 0. Scope and decisions

Scope, from #39: migrate `sequeless-ontology` to release-please, rename `ontology-owl-adapter` to `ontology-jena-adapter`, then make one import commit, "Import … from sequeless-prototype@\<SHA\>". The import brings `spi.meta` (schema, behaviour and automation halves), `spi.ontology`, the Jena adapter, the `sq:` vocabulary and the three contract modules. Spec 11 also releases 0.1.0 of each new module and re-releases the BOM. It does not cut over. The prototype keeps its copies until spec 12 ([#40](https://github.com/sequeless/sequeless-prototype/issues/40)), which deletes them and names the import SHA.

Owner decisions, applied and not reopened:

- **O1. Option A** (#39 comment, 2026-11-08). In the import, `RollupRule.criteria` changes from `spi.query.Criterion` to filter-core's `FilterNode`. Section 3.2 gives the mapping.
- **O2.** Spec 10 section 11, questions 2 to 4, at their defaults. The core use case keeps the name `OntologyAdministration`. (tenant, Ontology version) keying lands in spec 15 as default methods. Automation is actions-only.
- **O3.** Migrate to release-please first, and rename `ontology-owl-adapter`. Then one import commit. `facet-core` and `facet-core-testfixtures` are frozen and excluded from release-please and deploy. Every module first releases 0.1.0.
- **O4.** Java packages change in the import PR, not in the migration PR (#39).

Decided before this spec:

- **#13 resolution** (2026-10-14). Jena stays. `ontology-core` is thin: the `OntologyAdministration` api, the validation api and the `OntologyDocumentStore` spi. Three read ports: `SchemaSource` in `schema-contract`, `BehaviourSource` in `behaviour-contract`, `OntologyAdministration` in `ontology-core`'s api. No Spring in this repo (decision 9). Contract kits in a new `ontology-testfixtures` (decision 10). Independent module versions (decision 12).
- **#20 comment** (2026-10-18). `automation-contract` holds `AutomationSource`.
- **#23 comments.** Existing repos take moved code in one import commit with a provenance section. Histories are never merged. First versions are 0.1.0 and must release before the BOM re-release. Behaviour changes land in the prototype before each extraction, so an extraction is a move.

Decided here (D15 onward; spec 10 used D1 to D14):

- **D15.** The import is a copy, not `git mv`. It is one commit in `sequeless-ontology`, with the provenance section in its body. No `--allow-unrelated-histories`.
- **D16.** The Option A mapping is section 3.2. `NOT_NULL` becomes the canonical `is not` with a JSON null, not the alias `is not null`.
- **D17.** `ShaclValidationPort`, `ShaclDataGraphBuilder`, `ShaclValidationAutoConfiguration` and `ShaclValidationAdapterDescriptorProvider` stay in the prototype until spec 12. They implement or take `spi.validation` and `spi.object.BusinessObject`, which the moved code cannot depend on. Spec 10 section 0 puts the kernel-typed SHACL api in spec 12.
- **D18.** The Spring glue, `JenaOntologyProperties`, the `AdapterDescriptorProvider` classes and their `META-INF` resources stay in the prototype (#13 decision 9).
- **D19.** `OntologyDocumentStore` and `StoredOntologyDocument` move to `ontology-core`. The in-memory reference store is new code in `ontology-testfixtures` (#13 decision 7).
- **D20.** `automation-contract` depends on `schema-contract`, `kernel-identity` and `kernel-values`. It does not depend on `behaviour-contract`. #20 says it does; the code has no `meta.behaviour` import in `meta.automation` (section 11, question 4).
- **D21.** Package names are in section 4.
- **D22.** `ontology-core` drops its `jackson-databind`, which no moved type uses. ADR 0001 says each module redeclares only what it uses.

Out of scope, and which spec takes it:

- Deleting the prototype's copies, the reverse pointer and the prototype's dependency on the new artifacts: spec 12.
- The kernel-typed SHACL api and the move of SHACL code: spec 12.
- Splitting `sq-meta.ttl` into `sq-schema.ttl` and `sq-behaviour.ttl` (#13 decision 8). Section 11, question 5.
- `expression-api` in the compiler (#20). The moved code uses no `expression` package today (verified), so this is a later spec.
- `ensureIndexes` and its ADR: specs 13 and 14. The facet move (ADR 0005): spec 16.

## 1. Identity, stage and dependencies

- Stage 3, import. Spec 11 of 19. Waits for spec 5 (#33, done), spec 8 (#36, done) and spec 10 (#38, merged as `e53c0f1`, PR #85). `remaining-work.md` lists all three as done.
- Unblocks spec 12 (#40). Specs 14 and 15 also wait for this spec.
- Branch `spec-11-sequeless-ontology`, cut from `origin/main` at `a3a6911`. Verified: the local and remote `main` of `sequeless-prototype` are both at `a3a6911`.
- Repos touched: `sequeless-prototype` (prerequisite PR S0, docs PR S6), `sequeless-ontology` (S1 to S3), `sequeless-build` (S5a, S5b). `sequeless-filter` and `sequeless-kernel` are consumed and not changed.

## 2. Moves

Provenance:

- **Source repo:** `sequeless/sequeless-prototype`.
- **Source SHA:** `a3a6911e0797f962630e5577cb48514bc03afc0d`, which is `origin/main` when the branch was cut. It includes spec 10's merge. The last commit that changes the moved paths is `e53c0f1` (2026-11-07). 23 commits touch the Jena module path at this SHA.
- **Source paths:** relative to the prototype root. Listed in the tables below.
- **The move:** a copy, in one import commit in `sequeless-ontology`, message `Import … from sequeless-prototype@a3a6911e…`. The prototype is unchanged by the import. Its copies are deleted in spec 12.
- **Date:** `<import date>`.

Known limitations:

- No `git mv` and no `git log --follow`. The import is cross-repo, and #23 forbids merging histories. Each file's history is `git log` in the prototype at `a3a6911`. Spec 10's rename caveats carry over: `SchemaTest` from `MetaModelSnapshotTest`, `OntologyAdministrationTest` from `OntologyPortTest`, and `OntologyAdministration.java` from `OntologyPort.java` (48% similar). The target has no history for any of them.

Table 2.1, `sequeless-spi` (`sequeless-spi/src/main/java/org/sequeless/spi/`), 49 files:

| Source | Target module | Target package | Files |
|---|---|---|---|
| `meta/schema/` (incl. `OntologyVersion`, `SchemaSource`, `package-info`, all new in spec 10) | schema-contract | `org.sequeless.ontology.schema.api`; `SchemaSource` in `.spi` | 12 |
| `meta/behaviour/` (incl. `Behaviour`, `BehaviourSource`, `package-info`, new in spec 10) | behaviour-contract | `org.sequeless.ontology.behaviour.api`; `BehaviourSource` in `.spi` | 15 |
| `meta/automation/` (incl. `Automation`, `AutomationSource`, `TransitionRef`, `package-info`, new in spec 10) | automation-contract | `org.sequeless.ontology.automation.api`; `AutomationSource` in `.spi` | 10 |
| `ontology/` (`OntologyAdministration`, renamed from `OntologyPort` in spec 10, plus 8 result, format and exception types) | ontology-core | `org.sequeless.ontology.api` | 10 |
| `object/OntologyDocumentStore.java`, `object/StoredOntologyDocument.java` | ontology-core | `org.sequeless.ontology.spi` | 2 |

Table 2.2, the Jena adapter (`sequeless-adapter-ontology-jena/src/main/java/org/sequeless/adapter/ontology/jena/`), 18 files:

| Source | Target | Decision |
|---|---|---|
| `JenaOntologyPort`, `SnapshotMapper`, `ConsistencyChecker`, `ImportResolver`, `OntModelBuilder`, `TurtleExporter`, `SqVocabulary`, `SqAnnotations`, `OntologySource`, `ReasonerSetting` | ontology-jena-adapter | moves (10 classes); the `package-info` is rewritten |
| `src/main/resources/ontology/sq-meta.ttl` | ontology-jena-adapter, same path | moves whole (section 11, question 5). Byte-identical to the testkit copy (verified) |
| `JenaOntologyAutoConfiguration`, `JenaOntologyProperties`, `ShaclValidationAutoConfiguration` | stays | Spring (D18, D17) |
| `JenaOntologyAdapterDescriptorProvider`, `ShaclValidationAdapterDescriptorProvider`, `META-INF/services/…`, `META-INF/spring/…imports` | stays | D18, D17 |
| `ShaclValidationPort`, `ShaclDataGraphBuilder` | stays | D17 |

Tests, `sequeless-spi/src/test/java/org/sequeless/spi/` (17 files):

- To schema-contract (8): `SchemaTest`, `TypeDefinitionTest`, `AttributeDefinitionTest`, `RelationshipDefinitionTest`, `CardinalityTest`, `DatatypeTest`, `DisplayHintsTest`, `OntologyVersionTest`.
- To behaviour-contract (1): `meta/behaviour/BehaviourTest`. To automation-contract (1): `meta/automation/AutomationTest`.
- To ontology-core (7): `ImportReportTest`, `OntologyAdministrationTest`, `OntologyDocumentTest`, `OntologyExceptionTest`, `OntologyIssueTest`, `OntologyReportTest`, `SeverityTest`.

Table 2.3, the Jena tests (18 files). Moved (11): `ConsistencyCheckerTest`, `DerivationMappingTest`, `Halves`, `JenaOntologyPortAcceptanceTest`, `JenaOntologyPortContractTest`, `JenaOntologyPortStorePersistenceTest`, `JenaOntologyPortTest`, `SnapshotMapperTest`, `StateMachineMappingTest`, `TurtleExporterTest`, `UnknownPluginActivationTest`. Stays (7): `JenaOntologyAutoConfigurationTest`, `JenaOntologyPropertiesTest`, `JenaOntologyAdapterDescriptorProviderTest`, `ShaclValidationAutoConfigurationTest`, `ShaclValidationAdapterDescriptorProviderTest`, `ShaclDataGraphBuilderTest`, `ShaclValidationPortContractTest`.

Table 2.4, the testkit (`sequeless-spi-testkit/`) to ontology-testfixtures (`org.sequeless.ontology.testfixtures`):

| Source | Target |
|---|---|
| `src/main/java/org/sequeless/testkit/ontology/`: `OntologyContract`, `OntologyPorts`, `package-info` | `src/main/java`, same names |
| `src/main/resources/ontology/`: `reference.ttl`, `inconsistent.ttl`, `inverse-transitive.ttl`, `reference-plugin.ttl`, `unknown-plugin.ttl` | `src/main/resources/ontology/` |
| `src/test/java/org/sequeless/testkit/ontology/InMemoryOntologyPortContractTest.java`: its fake, extracted into a main class `InMemoryOntologyPort` (ADR 0002 pattern; the extraction size is unverified) | `src/main/java`, plus the runner in `src/test` |
| (new) in-memory `OntologyDocumentStore` (D19) | `src/main/java` |

The testkit's `sq-meta.ttl` and the testkit's own classes stay in the prototype until spec 12.

Javadoc. The three read-port javadocs name `{@code org.sequeless.spi.ontology.OntologyException}` (spec 10 section 3.1). They become `{@code org.sequeless.ontology.api.OntologyException}`. They stay `{@code}`, not `{@link}`, because the schema-contract must not depend on ontology-core.

## 3. Prerequisite refactors

### 3.1 Prototype prerequisite PR (S0, before the import)

Both changes are seams. They change no behaviour, so the import stays a move.

- **P1. Plugin names come from the host.** `JenaOntologyPort` calls `ServiceLoader.load(DerivationPlugin.class)` (near line 503, in the unknown-plugin check). The moved code cannot depend on `spi.derivation`. The port takes the names as a `Set<String>` argument. The Spring glue computes the set with the same `ServiceLoader` call. Tests: `UnknownPluginActivationTest` and the app's `UnknownDerivationPluginStartupTest`.
- **P2. Plain settings.** `JenaOntologyAutoConfiguration` reads `getReasoner()` and `getSource()` from `JenaOntologyProperties` (lines 62 to 70). Add a plain record, `JenaOntologySettings(ReasonerSetting reasoner, String source)`, in the Jena module. `JenaOntologyProperties` keeps the Spring binding and maps to the record (#13 decision 9).

### 3.2 Option A: `RollupRule.criteria` (in the import, target only)

- **Shape (D-decision here).** `Optional<FilterNode> criteria`. Empty replaces the empty list. One criterion becomes its `FieldFilter`. Two or more become an `AndFilter` in their order. The prototype's javadoc says the criteria are combined with AND, and this keeps that.
- **Mapping.** The canonical names are in filter-core's `BuiltinOperatorContributor`.

| `Criterion.Operator` (`sq:operator`) | `FieldFilter.op` | `value` |
|---|---|---|
| `EQ` (`eq`) | `is` | the converted value |
| `NE` (`ne`) | `is not` | the converted value |
| `IN` (`in`) | `is in` (list-valued) | JSON array |
| `LT`, `LTE`, `GT`, `GTE` | `is less than`, `is less than or equal to`, `is greater than`, `is greater than or equal to` | number |
| `CONTAINS` (`contains`) | `contains` | string |
| `STARTS_WITH` (`startsWith`) | `starts with` | string |
| `IS_NULL` (`isNull`) | `is` | JSON null (renders `IS NULL`) |
| `NOT_NULL` (`notNull`) | `is not` | JSON null (renders `IS NOT NULL`) |

- **NULL rows and LIKE.** The NULL table is spec 06 section 3.3 in `sequeless-filter` (`docs/specs/spec-06-filter-0.1.1.md`). `is not` includes NULL rows (`IS DISTINCT FROM`), and `contains` is case-insensitive. The prototype's `PostgresQueryStore` already renders `NE` as `IS DISTINCT FROM` and `CONTAINS` and `STARTS_WITH` as `ILIKE` (lines 457 to 495, verified). Its wildcard escaping against filter's is **unverified**.
- **Aliases.** Do not build `is null` or `is not null` by alias. Spec 06 section 3.3 records that `is not null` is registered as an alias of `is` (`BuiltinOperatorContributor.java:18`), so it renders `IS NULL`. Use the canonical names above.
- **Path.** `FieldFilter.path` is documented as a "dot-notation application-layer path". The DSL grammar admits only `WORD (DOT WORD)*` (`Filter.g4`). `Criterion.property` is an IRI. The rule for the path is section 11, question 2. The mapper's tests cannot be finished before that answer.
- **Values.** filter-core carries a plain `JsonNode`. It does not define the JSON form per datatype. The mapper writes the rule for each kernel `Value` variant, and the tests pin it. **Unverified** against filter-sql's `convertValue`.
- **Checks.** Each mapped node goes through filter-core's `FilterValidator` in a test. **Unverified** that it accepts list-valued and unary nodes built this way.

### 3.3 Consequences for spec 12 (recorded now)

- The prototype's `spi.meta.behaviour.RollupRule` keeps `List<Criterion>` until spec 12. Nothing in the prototype changes in spec 11.
- Consumers spec 12 must change (verified by grep): `DerivationPlanner` (sequeless-core, line 177, passes `rollup.criteria()` into `AggregateRequest`); `TypeResponseMapper` (sequeless-app, lines 303 to 305, renders the criteria); the tests `DefaultDerivationRecomputerTest`, `DefaultBusinessObjectServiceTest`, `DefaultMetaModelServiceTest`, `ValueCoercerTest`, the testkit's `QueryFixtures`, and the Jena tests `DerivationMappingTest` and `JenaOntologyPortAcceptanceTest` (lines 256 and 269).
- `AggregateRequest` still takes `List<Criterion>` until spec 14 (#16 decision 5 replaces `Criterion`). Spec 12 therefore needs a `FilterNode`-to-`Criterion` bridge in core. The mapper always produces a flat AND of `FieldFilter`s with the 11 operators, so the bridge can be exact for what the Jena adapter produces.
- After spec 12, the prototype depends on `behaviour-contract`, directly or through the Jena adapter. That brings filter-core 0.1.3 and its ANTLR runtime into the prototype's classpath.
- The prototype's `ArchitectureTest.behaviourDependsOnlyOnSchema` allows `spi.query` for `Criterion`. The target rule allows `org.sequeless.filter.api` instead (section 5.3). The prototype rule changes in spec 12.

## 4. Package renames

Target package names. All are decisions of this spec (D21), except where marked.

| From | To | Note |
|---|---|---|
| `org.sequeless.spi.meta.schema` | `org.sequeless.ontology.schema.api`; `SchemaSource` in `org.sequeless.ontology.schema.spi` | ADR 0001 api/spi split. `SchemaSource` is a driven port |
| `org.sequeless.spi.meta.behaviour` | `org.sequeless.ontology.behaviour.api`; `BehaviourSource` in `.spi` | same |
| `org.sequeless.spi.meta.automation` | `org.sequeless.ontology.automation.api`; `AutomationSource` in `.spi` | same |
| `org.sequeless.spi.ontology` | `org.sequeless.ontology.api` | #13 decision 3: `OntologyAdministration` is in ontology-core's api |
| `org.sequeless.spi.object` (two types) | `org.sequeless.ontology.spi` | D19; #13 decision 7 |
| `org.sequeless.adapter.ontology.jena`, public entry points | `org.sequeless.ontology.jena` | `JenaOntologyPort`, `ReasonerSetting`, `JenaOntologySettings` (P2), and `OntologySource` (kept beside its user) |
| `org.sequeless.adapter.ontology.jena`, compiler and vocabulary | `org.sequeless.ontology.jena.internal` | `SnapshotMapper`, `SqVocabulary`, `SqAnnotations`, `ConsistencyChecker`, `ImportResolver`, `OntModelBuilder`, `TurtleExporter`. Classes that `JenaOntologyPort` calls become `public` (visibility only) |
| `org.sequeless.testkit.ontology` | `org.sequeless.ontology.testfixtures` | #13 decision 10 |
| `org.sequeless.ontology.owl.{api,spi}` (`package-info` only, in the skeleton) | deleted | O3 |

Notes:

- Class names stay: `JenaOntologyPort`, `SnapshotMapper`, `OntologyAdministration`. Spec 10 D11 left renames to spec 11 or later. Keeping the names keeps spec 12's diff small. Spec 10 Q2 keeps the core use case's name, so the two `OntologyAdministration` types are fully qualified in core until spec 12.
- The testkit already has `org.sequeless.testkit.automation.AutomationContract`, which tests `spi.automation.AutomationPort`. The name is taken. `automation-contract` gets no kit class in spec 11, and the prototype's copy stays.
- Repo-segment package names follow spec 8 (`org.sequeless.kernel.*`, `org.sequeless.filter.*`, `org.sequeless.expression.*`).

## 5. Modules and their dependencies

### 5.1 Module list after spec 11 (`sequeless-ontology`)

| Module | Artifact | Release | Depends on |
|---|---|---|---|
| (root) | `sequeless-ontology` | aggregator and parent. Keeps the Spring ban and the Enforcer rules | none |
| `ontology-core` | `ontology-core` | 0.1.0 | `kernel-identity` |
| `schema-contract` | `schema-contract` | 0.1.0 | `kernel-identity` |
| `behaviour-contract` | `behaviour-contract` | 0.1.0 | `schema-contract`, `kernel-identity`, `filter-core` 0.1.3 |
| `automation-contract` | `automation-contract` | 0.1.0 | `schema-contract`, `kernel-identity`, `kernel-values` 0.1.0 |
| `ontology-jena-adapter` | `ontology-jena-adapter` | 0.1.0 | `ontology-core`, the three contracts, `kernel-identity`, `jena-ontapi` 6.1.0 (the prototype's pin, root pom line 44). `kernel-values`: see 5.2 |
| `ontology-testfixtures` | `ontology-testfixtures` | 0.1.0 | `ontology-core`, the three contracts, `kernel-identity`. JUnit and AssertJ at compile scope, as `facet-core-testfixtures` does (ADR 0002) |
| `facet-core` | `ontology-facet-core` | frozen, not released | none (unchanged) |
| `facet-core-testfixtures` | `ontology-facet-core-testfixtures` | frozen, not released | `ontology-facet-core` (unchanged) |

No prototype module changes its dependencies in spec 11.

### 5.2 Which artifacts depend on the four released artifacts

The BOM (remote `main`, 0.1.2) lists `kernel-identity` 0.1.0, `kernel-values` 0.1.0, `filter-core` 0.1.3, `filter-sql-adapter` 0.1.4, the filter testkit and postgres adapter, and the four expression artifacts at 0.1.1. Its pom says "Libraries do not import it." So the ontology modules pin these versions themselves (D24).

- `kernel-identity`: `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-jena-adapter`, `ontology-testfixtures` (directly or through a contract). `facet-core` has none today (verified).
- `kernel-values`: `automation-contract` (`SetPropertyAction`, `PropertyAssignment`). `ontology-jena-adapter`: `SnapshotMapper` uses it for criterion values (lines 812 to 925). Whether it still needs it after D16 is **unverified**.
- `expression-api`: none. Verified: no file under `spi/meta`, `spi/ontology` or the Jena module imports `org.sequeless.expression`. #20 plans it for the compiler (a later spec).
- `filter-core`: `behaviour-contract` directly. `ontology-jena-adapter` and `ontology-testfixtures` transitively. Not `schema-contract` or `ontology-core`.
- Weight: filter-core brings `jackson-databind` 2.18.3 (the root pom already pins 2.18.3, verified), `jackson-annotations` and `antlr4-runtime` 4.13.2 (filter-core's pom, verified). The root pom enables `dependencyConvergence`. Convergence with Jena's transitive versions is **unverified**.

### 5.3 ArchUnit rules (ADR 0001 needs one `BoundaryRulesTest` per module)

- `ontology-core`. Drop `allowEmptyShould(true)` from `api_and_spi_types_are_public`. Its TODO says to drop it once real types land, and this import is that point.
- The owl rules. `owl_adapter_does_not_reach_ontology_core_internals` forbids `..spi..`, so it would reject the adapter's use of `OntologyDocumentStore`. Replace it with `ontology_jena_adapter_does_not_reach_ontology_core_internals`, whose allowed set is ontology-core's `api` and `spi` and the three contract packages. Rename `ontology_core_does_not_depend_on_owl_adapter` to the Jena package.
- `schema-contract` ports the prototype rule `schemaDependsOnlyOnJdkKernelAndItself` (`sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java`, line 208).
- `behaviour-contract` ports `behaviourDependsOnlyOnSchema` (line 219), with `org.sequeless.filter.api..` in place of `org.sequeless.spi.query..`.
- `automation-contract` ports `automationDependsOnlyOnBehaviourAndSchema` (line 233), without the behaviour package (D20).
- Each contract's api and spi types are public.
- The root's `ban-spring`, `dependencyConvergence` and `requireJavaVersion [21,)` stay.

## 6. Release config (first version, tags, BOM change)

### 6.1 First version and tags

- Every new module releases once, at `0.1.0` (#23). Released: `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-jena-adapter`, `ontology-testfixtures`. Not released: the two facet modules (O3).
- Tags: `<component>-v0.1.0` (`include-component-in-tag`). This is the pattern in `sequeless-filter`'s config. **Unverified** for ontology until the migration runs.
- The root's `v0.1.0` tag exists on the remote and points at `fd4f748`. That commit is not in `origin/main`'s history (verified with `merge-base --is-ancestor`). `CHANGELOG.md` on `main` has a `1.0.0 (2026-09-16)` section from the failed semantic-release run, and no `1.0.0` tag exists on the remote. `remaining-work.md` says no tag deletion is needed. The migration PR decides the root CHANGELOG.
- `.releaserc.json`, the semantic-release steps in `release.yml` and the `versions:set` in `prepareCmd` go in the migration PR.
- ADR 0004 says each module declares its own `<version>`. Children no longer inherit the root's `0.1.0`. The migration PR makes that change.

### 6.2 Migration PR (S1). Decisions only

The PR is separate and in progress in `sequeless-ontology` (branch `chore/release-please-migration`). Its contents are not designed here.

- Mirror `sequeless-filter`'s `release-please-config.json` and manifest approach.
- Drop `.releaserc.json`.
- Keep the facet modules out of release and deploy.
- Keep Java packages unchanged in this PR.
- The rename `ontology-owl-adapter` to `ontology-jena-adapter` (directory, artifactId, root module list, README table, `sonar-project.properties`) goes in this PR, following #39's wording. Section 11, question 6.

### 6.3 BOM change (sequeless-build)

- Today the BOM lists no ontology artifact.
- Change: the BOM lists the six ontology artifacts at 0.1.0. That is `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-core`, `ontology-jena-adapter` and `ontology-testfixtures`. Including the testfixtures follows the expression precedent, where the BOM lists `expression-testkit` (D26).
- Released as `sequeless-bom` 0.1.3. **Unverified:** the next version. release-please decides it. Only released versions go in (`BomListsOnlyReleasesTest`).
- Then `bom-contracts` gets the ontology contract. `OntologyBomContractTest extends OntologyContract` with `JenaOntologyPort`, the same shape as `ExpressionBomContractTest` (remote `main`, verified). Its import moves from 0.1.2 to 0.1.3 in the same PR (`remaining-work.md`, open items: the import is bumped on each BOM release). The BOM release comes first, because the import needs the released version.

### 6.4 Release steps that belong to spec 11

The DoD says "the release and the BOM re-release are done". The steps are, in order:

1. S1 migration PR merged.
2. S2 import PR merged, and S3 ADR commit merged.
3. The release-please release PR for the six modules at 0.1.0 is merged, and each artifact publishes to GitHub Packages (S4). The precedent is `sequeless-kernel`, whose config sets `initial-version: 0.1.0` (remote `main`, verified).
4. The BOM PR is merged and `sequeless-bom` 0.1.3 is released (S5a).
5. The `bom-contracts` PR is merged and its CI is green (S5b).

Not part of spec 11: the prototype's use of the new artifacts (spec 12).

## 7. CI and workflows

- **Today.** `sequeless-ontology`'s `ci.yml` runs `./mvnw -B verify` on JDK 21 and a SonarCloud job. Its `release.yml` runs semantic-release (`origin/main`, verified). `remaining-work.md` says its CI and Release runs fail on `main`. **Unverified** here.
- **Target.** `ci.yml` and `release.yml` call the sequeless-build reusable workflows, as `sequeless-filter` does (`maven-ci.yml` and `maven-release.yml` at `workflows-v0.1.2`, remote `main`, verified). This assumes section 11, question 6 is answered yes.
- **Gate.** `./mvnw -B verify` runs the spotless check (`palantir-java-format` 2.50.0, bound to `verify`), the Enforcer rules, the ArchUnit tests and the surefire and failsafe runs. JaCoCo's check is off by default (verified in the root pom).
- **Formatting.** If spotless rejects copied files, the formatting goes in a separate commit in the same PR. **Unverified:** how many files that touches. The prototype's formatter version is also **unverified**.
- **JDK.** The prototype builds on JDK 25 (root pom `maven.compiler.release` 25, verified). The target builds on 21. The moved code must compile at `release 21`. **Unverified**. It is a gate in S2.
- **Sonar.** Whether the ontology keeps its SonarCloud job is **unverified**.

Definition of done, quoted from #39, with this spec's reading:

- "The new repo's CI is green." No new repo. Read as: `sequeless-ontology`'s CI on `main` is green after S1, after S2, and after each release.
- "The prototype's CI is green against *released* artifacts (never a SNAPSHOT on `main`)." Spec 11 changes no prototype dependency. The root pom keeps `sequeless-bom` 0.1.2 (line 64, verified). The S6 docs PR's CI is the proof.
- "The BOM's CI, which runs every testkit contract, is green." Before S5b, `bom-contracts` runs `BomListsOnlyReleasesTest` and the expression contract (remote `main`, verified). S5b adds the ontology contract.
- "The release and the BOM re-release are done." Section 6.4.
- "For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA." Not applicable here. Spec 12 carries it, and its reverse pointer uses the import SHA.

## 8. ADRs

Sources (verified):

- Commit `af03f93` is in the prototype's clone and in the prototype-ui clone. Spec 10 said it was not in the local clones, which was wrong. In the prototype clone it is on branch `wayfinder/ontology-move-adrs`, local and on origin. It holds two drafts, both under `docs/drafts/`: ADR 0004 and ADR 0005. It has no 0003 draft. Its author date is 2026-10-14.
- The ADR 0003 draft is on branch `wayfinder/kernel-adr-0002-amendment`, local and on origin, at `docs/drafts/sequeless-ontology-adr-0003-facet-core-depends-on-kernel-identity.md`.

Each ADR is committed to `docs/adr/` in `sequeless-ontology` as `NNNN-<slug>.md`, with the draft's header comment removed. Status is `proposed`, as drafted. ADR 0000 says ADRs are immutable, so ADR 0001 and ADR 0002 are not edited.

- **ADR 0003** (`0003-facet-core-depends-on-kernel-identity.md`, source: the 0003 draft). Decision: `facet-core` may take one compile dependency, `kernel-identity`, and its `Principal` folds into it. Amends 0002. Its code (an Enforcer `<includes>` exception and an ArchUnit change) is not part of spec 11, because `facet-core` is frozen. Section 11, question 3.
- **ADR 0004** (`0004-thin-core-jena-adapter-independent-versions.md`, source: `af03f93`). Decision: `ontology-core` is thin (the `OntologyAdministration` api, the validation api and the `OntologyDocumentStore` spi). The Jena adapter is `ontology-jena-adapter` and Spring-free. `SchemaSource` lives in `schema-contract` and `BehaviourSource` in `behaviour-contract`. Each module versions and releases on its own. `ontology-testfixtures` publishes the contract kits. Amends 0001. **Added in this spec:** `AutomationSource` lives in `automation-contract`, which the draft omits (spec 10 section 8; #20 comment).
- **ADR 0005** (`0005-facet-core-moves-to-sequeless-query.md`, source: `af03f93`). Decision: `facet-core` and `facet-core-testfixtures` move to `sequeless-query` (spec 16, #44). The zero-dependency rule moves with them. Supersedes 0002.

## 9. Slice plan

| Slice | Repo and PR | Does | Depends on | Gate |
|---|---|---|---|---|
| **S0 prototype prerequisite** | `sequeless-prototype`, one PR | P1 and P2 (section 3.1) | none. Can run alongside S1 | `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify` green; the unknown-plugin tests pass unchanged |
| **S1 release-please migration** | `sequeless-ontology`, separate PR (in progress, another agent) | Section 6.2, including the rename | none | Merged; CI green on `main`; a release-please run on `main` opens nothing for the facet modules; no Java package changed |
| **S2 import** | `sequeless-ontology`, one PR, one import commit plus any formatting commit | Sections 2, 3.2, 4, 5 and 10. Copies only; no prototype file touched | S0, S1 | `./mvnw -B verify` green on JDK 21 (spotless, Enforcer, ArchUnit, moved tests, `OntologyContract` against the Jena port and the in-memory fake). The provenance section is in the commit body |
| **S3 ADRs** | `sequeless-ontology`, same PR or a follow-up commit | Section 8: 0003, 0004 (with `AutomationSource`), 0005 | S2 | Each file matches its source except the stated edit; CI green; owner reviews the text |
| **S4 release 0.1.0** | `sequeless-ontology`, release-please release PR | Section 6.1: six modules at 0.1.0, tags, GitHub Packages | S2, S3 on `main` | Each artifact resolves at 0.1.0 in a clean build; release CI green. Must finish before S5a |
| **S5a BOM** | `sequeless-build`, then the `sequeless-bom` 0.1.3 release | Section 6.3, first half | S4 | `BomListsOnlyReleasesTest` green; six artifacts listed at 0.1.0; release published |
| **S5b bom-contracts** | `sequeless-build`, one PR | `OntologyBomContractTest`; import moves to 0.1.3 | S5a | `bom-contracts` CI green, running the ontology contract |
| **S6 prototype docs** | `sequeless-prototype`, docs only | This spec's status; the import SHA for spec 12; the `remaining-work.md` row; the root pom comment on line 100 (its tracker item) | S2 to S5b | Prototype CI green on the PR; the import SHA recorded |

What no slice does: delete a prototype file (spec 12), change a prototype dependency (spec 12), or touch `facet-core` or `facet-core-testfixtures`.

## 10. Test plan

### 10.1 Target tests

- Moved and passing unchanged in behaviour: the tests in section 2 (17 spi tests, 11 Jena tests, and the testkit's runner).
- `OntologyContract` (ontology-testfixtures) must pass for `JenaOntologyPort` (`JenaOntologyPortContractTest`) and for the in-memory fake. This is the contract gate for S2.
- The reference-domain parity assertions from spec 10 section 10.3 (`stateMachine`, `derivation` and `actions` equal the values the prototype returned at `aafb5a9`) move with `JenaOntologyPortAcceptanceTest`.
- New: `RollupRuleMappingTest` (in the Jena module). Each of the 11 operators maps to the canonical name in section 3.2. `NOT_NULL` maps to `is not` with a JSON null, and never to the alias. One criterion gives a `FieldFilter`, two give an `AndFilter`, and none give `Optional.empty()`. Each node passes `FilterValidator` (**unverified**, section 3.2).
- Rewritten: the `criteria()` assertions in `DerivationMappingTest` (lines 105 to 570) and `JenaOntologyPortAcceptanceTest` (lines 256 and 269). They check the same facts in the `FilterNode` form.
- New `BoundaryRulesTest` classes in `schema-contract`, `behaviour-contract` and `automation-contract` (section 5.3), and the rewritten rules in `ontology-core` and the Jena module.

### 10.2 Prototype tests

None change in spec 11. The prototype's `ArchitectureTest` keeps its 17 rules, as spec 10 left it.

### 10.3 Verification commands

- Target, after S2: `git grep -nE 'org\.sequeless\.(spi|adapter|testkit)\.' -- '*.java'` returns nothing. The target never imports prototype packages.
- Target, after S1 and S2: `git grep -n 'owl' -- '*.java' '*.xml' 'README.md' 'sonar-project.properties'` returns nothing except history files (CHANGELOG, ADR 0001).
- Prototype, S0 and S6: `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify`, in one shell command.

## 11. Open questions

1. **Option A against #23.** #23 (comment 3) says an extraction is a move, and behaviour changes land in the prototype first. Option A changes the type of `RollupRule.criteria` inside the import (D16). This draft applies Option A as the owner decided. Confirm that the import may carry that one change. If not, the prototype takes it first. That is a larger S0: `DerivationPlanner`, `TypeResponseMapper`, `SnapshotMapper`, and the `FilterNode`-to-`Criterion` bridge.
2. **The path of `FieldFilter` for a property.** `Criterion.property` is an IRI. `FieldFilter.path` is an application path, and the DSL admits only dotted identifiers. Options: the property's short name (needs a unique-name rule, which the schema already relies on for types, via `typeByName`), or the IRI as an opaque string (lossless, but outside the documented form). The mapper's tests wait for the answer.
3. **Where ADR 0003 lives.** #39 says to commit ontology 0003. The 0005 draft says the `Principal` folding "becomes a sequeless-query ADR". Commit 0003 here as proposed, as drafted? Or commit it and record that it moves with `facet-core` in spec 16?
4. **`automation-contract` and `behaviour-contract`.** #20 (2026-10-18) says `automation-contract` depends on `behaviour-contract`. The code does not need it, so D20 says it does not. Confirm D20.
5. **The `sq-meta.ttl` split.** #13 decision 8 splits the vocabulary into `sq-schema.ttl` and `sq-behaviour.ttl`. This draft moves the file whole, because #23 says an extraction is a move. Confirm: whole in spec 11, and the split later?
6. **Migration PR contents.** The brief's list of migration decisions does not name two things that #39's wording implies. Does S1 include the rename of `ontology-owl-adapter`? Does S1 also move the ontology's CI and Release to the sequeless-build reusable workflows, as `sequeless-filter` does? This draft assumes yes to both.

## Appendix: the sources, for the implementer

- Issue #39 body and both comments (the second is "Option A"). Issue #13 resolution comment. Issue #20 comment. Issue #23 comments (history and first versions).
- Prototype: `sequeless-spi/src/main/java/org/sequeless/spi/meta/`, `…/spi/ontology/`, `…/spi/object/OntologyDocumentStore.java`, `sequeless-adapter-ontology-jena/`, `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/ontology/`, and the test trees named in section 2.
- Target (`sequeless-ontology` `origin/main`, `ace5313`): root `pom.xml`, `ontology-core/`, `ontology-owl-adapter/`, `facet-core/`, `facet-core-testfixtures/`, `docs/adr/0000` to `0002`, `.releaserc.json`, `.github/workflows/`.
- Filter (`sequeless-filter` remote `main`): `filter-core/src/main/java/org/sequeless/filter/api/FilterNode.java` and `FieldFilter.java`, `…/internal/BuiltinOperatorContributor.java`, `filter-core/src/main/antlr4/…/Filter.g4`, `docs/specs/spec-06-filter-0.1.1.md` section 3.3, `docs/adr/0002-field-expression-port.md`.
- BOM (`sequeless-build` remote `main`): `sequeless-bom/pom.xml`, `bom-contracts/pom.xml`, `bom-contracts/src/test/…/ExpressionBomContractTest.java`.
- Drafts: commit `af03f93` (ADR 0004, 0005), branch `wayfinder/kernel-adr-0002-amendment` (ADR 0003).
