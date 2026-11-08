# Spec 11 — sequeless-ontology gains the prototype's ontology

Issue: [#39](https://github.com/sequeless/sequeless-prototype/issues/39). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#12](https://github.com/sequeless/sequeless-prototype/issues/12), [#13](https://github.com/sequeless/sequeless-prototype/issues/13), [#14](https://github.com/sequeless/sequeless-prototype/issues/14), [#16](https://github.com/sequeless/sequeless-prototype/issues/16), [#20](https://github.com/sequeless/sequeless-prototype/issues/20), [#23](https://github.com/sequeless/sequeless-prototype/issues/23), [#24](https://github.com/sequeless/sequeless-prototype/issues/24). Tracker: `docs/extraction/remaining-work.md`.

Status: draft, rework 2 after review. Not reviewed by the owner. No PR. The open questions in section 11 are not decided here, and the slices they block say so. Moved-path facts are read at `a3a6911`. `origin/main` is `f32f3c2`, and PR #88 changes docs and the root POM only (verified). Remote facts are read on 2026-11-08.

## 0. Scope and decisions

Scope, from #39: migrate `sequeless-ontology` to release-please, and rename `ontology-owl-adapter` to `ontology-jena-adapter`. Then make one import commit that brings `spi.meta` (the schema, behaviour and automation halves), `spi.ontology`, the Jena adapter, the `sq:` vocabulary and the three contract modules. Spec 11 also releases 0.1.0 of each new module and re-releases the BOM. It does not cut over. The prototype keeps its copies until spec 12 ([#40](https://github.com/sequeless/sequeless-prototype/issues/40)), which deletes them and names the import SHA.

Owner answers and applied decisions:

- **O1. Option A** (#39 comment, 2026-11-08). `RollupRule.criteria` changes from `spi.query.Criterion` to filter-core's `FilterNode`, in the import. It departs from #23 comment 1, point 2. D16 records that.
- **O2. Accepted defaults** for spec 10 section 11, questions 2 to 4. The orchestrator accepted these in spec 10. The owner has not reviewed them here. The core use case keeps the name `OntologyAdministration`. (tenant, Ontology version) keying lands in spec 15 as default methods. Automation is actions-only.
- **O3.** Migrate to release-please first, rename `ontology-owl-adapter`, then one import commit. `facet-core` and `facet-core-testfixtures` are frozen and excluded from release and deploy. Every module first releases 0.1.0.
- **O4.** Java packages change in the import PR, not in the migration PR (#39).

Decided before this spec:

- **#13 resolution** (2026-10-14). Jena stays. `ontology-core` is thin: the `OntologyAdministration` api, the validation api and the `OntologyDocumentStore` spi. There are two read ports, `SchemaSource` and `BehaviourSource`, and one admin port, `OntologyAdministration`. #20 adds `AutomationSource` as a third read port. No Spring in this repo (decision 9). Contract kits in `ontology-testfixtures` (decision 10). Independent module versions (decision 12).
- **#20 comment** (2026-10-18). `AutomationSource` lives in `automation-contract`.
- **#23 comment 1.** Existing repos take moved code in one import commit with a provenance section, and never merge histories (point 3). Behaviour changes land in the prototype first (point 2). The migration adopts the sequeless-build parent and workflows (point 4). First versions are 0.1.0, and they release before the BOM re-release ("First versions" bullet).

Decided here. D-numbers continue from spec 10's D14. "Open" marks a decision the owner must make. The body applies its default, and section 11 names the slices it blocks.

- **D15.** The import is a copy, not `git mv`. It is one commit in `sequeless-ontology`, with the provenance section in its body. No history is merged (#23 comment 1, point 3).
- **D16.** Option A (O1) changes the type of `RollupRule.criteria` in the import, not in the prototype. This departs from #23 comment 1, point 2. The owner's answer on #39 is the source. The mapping is section 3.2.
- **D17. Open (section 11, Q1).** SHACL stays in the prototype until spec 12. The body applies this default.
- **D18.** The Spring glue, `JenaOntologyProperties`, the `AdapterDescriptorProvider` classes and their `META-INF` resources stay in the prototype (#13 decision 9).
- **D19.** `OntologyDocumentStore` and `StoredOntologyDocument` move to `ontology-core`'s spi (#13 decision 7). The in-memory document store, and the two document-store clauses of `ObjectStoreContract`, are copied into `ontology-testfixtures`.
- **D20.** `automation-contract` depends on `schema-contract`, `kernel-identity` and `kernel-values`. It does not depend on `behaviour-contract`, as the code has it. #20 says otherwise. Open (section 11, Q10): the owner confirms.
- **D21.** The contract modules and `ontology-core` use ADR 0001's api, spi and internal split. The Jena adapter's layout is open (section 11, Q2).
- **D22.** `ontology-core` drops `jackson-databind`, which no moved type uses (ADR 0001: each module redeclares only what it uses).
- **D23. Open (section 11, Q3).** The shape of `criteria`: `Optional<FilterNode>`, or `FilterNode` with an empty `AndFilter`. The body applies `Optional`.
- **D24.** Libraries pin released versions directly and never import the BOM (the BOM pom says so). The pins are `kernel-identity` 0.1.0, `kernel-values` 0.1.0, `filter-core` 0.1.3 and `jena-ontapi` 6.1.0. The ontology modules are at 0.1.0.
- **D25.** The BOM lists six ontology artifacts at 0.1.0, including `ontology-testfixtures`. The expression precedent lists `expression-testkit` in the BOM (remote `main`, verified).
- **D26.** `schema-contract` needs `kernel-identity` only. `behaviour-contract` needs `kernel-identity` and `filter-core`. Neither needs `kernel-values`. This deviates from #12's resolution, point 3, which says both depend on `kernel-values`. The code does not.
- **D27.** The import commit title is `feat(ontology): import the ontology from sequeless-prototype@a3a6911`. Precedents: `feat(kernel): import identity and values from sequeless-prototype (#2)` and `feat(expression): import the expression port, JEXL adapter and contract (#36)`. The merge method is open (section 11, Q6).
- **D28.** The Jena adapter declares `filter-core` 0.1.3, `jackson-databind`, `kernel-values`, `jena-ontapi`, `ontology-core`, the three contracts and `kernel-identity` directly (ADR 0001). Section 5.3 gives the reasons.
- **D29.** The plugin-name lookup enters the Jena port as a `Supplier<Set<String>>`, not a `Set`, because `unknownPluginIssues` resolves the names on every build (section 3.1).

Out of scope, and the spec that takes it:

- Deleting the prototype's copies, the reverse pointer and the prototype's dependency on the new artifacts: spec 12 (#40).
- The kernel-typed SHACL api and the move of SHACL code (D17's default): spec 12.
- `ensureIndexes` and its ADR: specs 13 and 14. The facet move (ADR 0005, #44): spec 16.
- `expression-api` in the compiler (#20). The moved code uses no `expression` package today (verified). #20 names no spec for it. Which spec takes it is **unverified**.

## 1. Identity, stage and dependencies

- Stage 3, import. Spec 11 of 19. Waits for spec 5 (#33, done), spec 8 (#36, done) and spec 10 (#38, merged as `e53c0f1`, PR #85). `remaining-work.md` lists all three as done.
- Unblocks spec 12 (#40). Specs 14 and 15 also wait for this spec.
- Branch `spec-11-sequeless-ontology`, rebased on `origin/main` `f32f3c2`. The source SHA stays `a3a6911` (section 2).
- Repos touched: `sequeless-prototype` (S0 prerequisite, S6 docs), `sequeless-ontology` (S1, S1b, S2, S3, S4), and `sequeless-build` (S5a, S5b). `sequeless-filter` and `sequeless-kernel` are consumed and not changed.

## 2. Moves

Provenance:

- **Source repo:** `sequeless/sequeless-prototype`.
- **Source SHA:** `a3a6911e0797f962630e5577cb48514bc03afc0d`. Line numbers in this section are verified at this SHA. `origin/main` is now `f32f3c2`, and PR #88 changes no moved path (verified). The import takes the SHA that is current when S2 is cut, and that SHA goes into the commit body. The last commit that changes the moved paths is `e53c0f1` (2026-11-07).
- **Source paths:** relative to the prototype root. Tables below.
- **The move:** a copy, in one import commit. Its title and body are D27 and section 3.1. The prototype is unchanged by the import. Its copies are deleted in spec 12.
- **Date:** `<import date>`.

Known limitations:

- No `git mv` and no `git log --follow`. The import is cross-repo, and #23 forbids merged history. Spec 10's rename caveats carry over: `SchemaTest` (from `MetaModelSnapshotTest`), `OntologyAdministrationTest` (from `OntologyPortTest`), and `OntologyAdministration.java` (48% similar to `OntologyPort.java`). The target has no history for any of them.
- 23 commits touch the Jena module path at `a3a6911`. None is carried over.

Table 2.1, `sequeless-spi/src/main/java/org/sequeless/spi/`, 49 files:

| Source | Target module | Target package | Files |
|---|---|---|---|
| `meta/schema/`: 11 types and `package-info`. `OntologyVersion`, `SchemaSource` and `package-info` are new in spec 10 | schema-contract | `org.sequeless.ontology.schema.api`; `SchemaSource` in `.spi` | 12 |
| `meta/behaviour/`: 14 types and `package-info`. `Behaviour` and `BehaviourSource` are new in spec 10 | behaviour-contract | `org.sequeless.ontology.behaviour.api`; `BehaviourSource` in `.spi` | 15 |
| `meta/automation/`: 9 types and `package-info`. `Automation`, `AutomationSource` and `TransitionRef` are new in spec 10 | automation-contract | `org.sequeless.ontology.automation.api`; `AutomationSource` in `.spi` | 10 |
| `ontology/`: `OntologyAdministration` (renamed from `OntologyPort` in spec 10), 8 result, format and exception types, and `package-info` | ontology-core | `org.sequeless.ontology.api` | 10 |
| `object/OntologyDocumentStore.java`, `object/StoredOntologyDocument.java` | ontology-core | `org.sequeless.ontology.spi` | 2 |

Table 2.2, Jena main code, `sequeless-adapter-ontology-jena/src/main/java/org/sequeless/adapter/ontology/jena/`, 18 files:

| Source | Target | Note |
|---|---|---|
| `JenaOntologyPort`, `SnapshotMapper`, `ConsistencyChecker`, `ImportResolver`, `OntModelBuilder`, `TurtleExporter`, `SqVocabulary`, `SqAnnotations`, `OntologySource`, `ReasonerSetting` | ontology-jena-adapter | 10 classes move, and `package-info` is rewritten. Packages in section 4 |
| `src/main/resources/ontology/sq-meta.ttl` | ontology-jena-adapter, same path | Moves whole (section 11, Q9). Byte-identical to the testkit copy (verified) |
| `JenaOntologyAutoConfiguration`, `JenaOntologyProperties`, `ShaclValidationAutoConfiguration` | stays | Spring (D18; D17 for the SHACL class) |
| `JenaOntologyAdapterDescriptorProvider`, `ShaclValidationAdapterDescriptorProvider`, `META-INF/services/…`, `META-INF/spring/…imports` | stays | D18, D17 |
| `ShaclValidationPort`, `ShaclDataGraphBuilder` | stays | D17 (section 11, Q1). They import `spi.validation` and `spi.object.BusinessObject` |

Tests, spi (`sequeless-spi/src/test/java/org/sequeless/spi/`), 18 files:

- schema-contract (8): `SchemaTest`, `TypeDefinitionTest`, `AttributeDefinitionTest`, `RelationshipDefinitionTest`, `CardinalityTest`, `DatatypeTest`, `DisplayHintsTest`, `OntologyVersionTest`.
- behaviour-contract (1): `meta/behaviour/BehaviourTest`. automation-contract (1): `meta/automation/AutomationTest`.
- ontology-core (8): `ImportReportTest`, `OntologyAdministrationTest`, `OntologyDocumentTest`, `OntologyExceptionTest`, `OntologyIssueTest`, `OntologyReportTest`, `SeverityTest`, and `object/StoredOntologyDocumentTest`, which moves with its type (D19).

Tests, Jena (18 files). Moved (11): `ConsistencyCheckerTest`, `DerivationMappingTest`, `Halves`, `JenaOntologyPortAcceptanceTest`, `JenaOntologyPortContractTest`, `JenaOntologyPortStorePersistenceTest`, `JenaOntologyPortTest`, `SnapshotMapperTest`, `StateMachineMappingTest`, `TurtleExporterTest`, `UnknownPluginActivationTest`. Stay (7): `JenaOntologyAutoConfigurationTest`, `JenaOntologyPropertiesTest`, `JenaOntologyAdapterDescriptorProviderTest`, `ShaclValidationAutoConfigurationTest`, `ShaclValidationAdapterDescriptorProviderTest`, `ShaclDataGraphBuilderTest`, `ShaclValidationPortContractTest`.

Table 2.3, testkit and shared test helpers to `ontology-testfixtures` (`org.sequeless.ontology.testfixtures`):

| Source | Target | Note |
|---|---|---|
| `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/ontology/`: `OntologyContract`, `OntologyPorts`, `package-info` | `src/main/java`, same names | `package-info` links are rewritten (section 2.1). The prototype copy stays until spec 12 |
| `sequeless-spi-testkit/src/main/resources/ontology/`: `reference.ttl`, `inconsistent.ttl`, `inverse-transitive.ttl`, `reference-plugin.ttl` | `src/main/resources/ontology/` | 4 files: the ones the moved code loads, directly or through `Fixtures`. `unknown-plugin.ttl` stays in the testkit, since no moved code loads it |
| `org.sequeless.testkit.Fixtures` (`sequeless-spi-testkit/src/main/java/org/sequeless/testkit/Fixtures.java`), ontology subset only | `org.sequeless.ontology.testfixtures.Fixtures` | The moved code uses four loaders (`referenceOntology`, `inconsistentOntology`, `inverseTransitiveOntology`, `referencePluginOntology`), `defaultScope()` (line 160), and 25 IRI constants. `Fixtures` imports only kernel-identity and `spi.ontology` (verified). The prototype keeps the full class |
| `InMemoryOntologyPortContractTest.java`, `FakeOntologyPort` (from line 53 to the end of the 132-line file) | `InMemoryOntologyPort` in `src/main/java` | 80 lines extracted. The runner stays in `src/test`. The fake implements the four ports, so it moves with the contract |
| `InMemoryObjectStorePort.java` (testkit test sources), `InMemoryOntologyDocumentStore` (lines 222 to 249) | `InMemoryOntologyDocumentStore` in `src/main/java` (D19) | 28 lines copied. The prototype keeps its private copy |
| `ObjectStoreContract.java`: `ontologyDocumentsRejectsNullArguments` (line 184) and `ontologyDocumentActiveIsEmptyInitiallyThenTracksVersions` (line 403) | `OntologyDocumentStoreContract` in `src/main/java` (D19) | Copied, not moved. The prototype keeps both clauses until spec 12 |

### 2.1 Javadoc rewrites (main code)

Every `{@link}` to a type that stays in the prototype becomes `{@code}`, and every `{@code}` that names a moved type's old package is rewritten. Checked on the moved main files at `a3a6911`. Test code is not built by javadoc.

| File (moved) | Lines | Reference | Action |
|---|---|---|---|
| `spi/meta/behaviour/PluginRule.java` | 5, 7, 12 | `{@link …spi.derivation.DerivationPlugin}` | `{@code}` |
| `spi/meta/behaviour/RollupRule.java` | 13, 21, 34, 35 | `{@link AggregateRequest}`, `{@link Criterion}` (twice), `{@link Query#criteria()}` | `{@code}`. The text now describes `FilterNode` |
| `spi/meta/behaviour/StateMachineDefinition.java` | 11 | `{@link …spi.object.BusinessObject#state()}` | `{@code}` |
| `spi/meta/behaviour/package-info.java` | 9, 21 | `{@link Criterion}`, `{@link DerivationPlugin}` | `{@code}`. Text rewritten for `FilterNode` |
| `spi/meta/schema/AggregateFunction.java` | 6, 18, 25, 33, 40, 47 | `{@link AggregateRequest}`, `{@link AggregateResult}` | `{@code}` |
| `spi/meta/schema/package-info.java` | 15 | `{@link AggregateRequest}` | `{@code}` |
| `spi/meta/automation/Action.java` | 4, 7 | `{@link …behaviour.Transition}`, `{@link …behaviour.DerivationRule}` (D20: no behaviour dependency) | `{@code}` |
| `spi/meta/automation/SetPropertyAction.java` | 14 | `{@link …behaviour.RollupRule}` | `{@code}` |
| `spi/meta/automation/package-info.java` | 4–5, 20 | Text names "sibling types in `meta.behaviour`"; `{@link …behaviour.Transition}` | Text rewritten (automation imports only schema and kernel); `{@code}` |
| `spi/meta/automation/Automation.java` | 15 | `{@code …behaviour.StateMachineDefinition.transitions()}` | Reworded to "the transition order of the state machine" |
| `SchemaSource.java`, `BehaviourSource.java`, `AutomationSource.java` | 14 | `{@code org.sequeless.spi.ontology.OntologyException}` | `{@code org.sequeless.ontology.api.OntologyException}` |
| `spi/ontology/OntologyException.java` | 11 | `{@code org.sequeless.core.AuthorizationException}` | Text only: name the core exception without its package |
| `spi/object/OntologyDocumentStore.java` | 8, 13 | `{@link ObjectStorePort#ontologyDocuments()}` (a link); `{@code …ObjectStoreContract}` | `{@code}` for the link; the contract is `OntologyDocumentStoreContract` |
| Jena `JenaOntologyPort.java` | 143, 485 | `{@link …spi.derivation.DerivationPlugin}`, `{@link DerivationPlugin}` | `{@code}`. P1 removes the import |
| Jena `JenaOntologyPort.java` | 358 | `{@link ShaclValidationPort}` | `{@code}`. SHACL stays |
| Jena `SnapshotMapper.java` | 590, 914 | `{@code …spi.query.Operator}`, `{@code …core.validation.ValueCoercer}` | Text only: name the prototype's enum and coercer without packages |
| testkit `ontology/package-info.java` | 2–5, 7, 9–10 | Links to `spi.ontology.*`, `spi.meta.*`, `testkit.ontology.*` | Targets follow the new packages (section 4) |

Imports change with the package move, and javadoc `{@link}` resolves through imports. The rows above are the links that stop resolving. `ShaclValidationPort.java` (which stays) links to `JenaOntologyPort#currentModel` (section 3.3, section 11, Q1).

## 3. Prerequisite refactors

### 3.1 Prototype prerequisite PR (S0, before the import)

Both changes are seams. They keep behaviour, so the import stays a move.

- **P1. Plugin names come from the host.** `JenaOntologyPort.unknownPluginIssues` (near line 503) calls `ServiceLoader.load(DerivationPlugin.class)` every time it runs, and it runs on every build. The javadoc (lines 485 to 492) relies on this, so a plugin deployed after startup is seen on the next reload. The moved code cannot depend on `spi.derivation`. So the port stores a `Supplier<Set<String>> registeredPluginNames`, and `unknownPluginIssues` calls `get()` on each build. A plain `Set` would freeze the snapshot and break the documented behaviour (D29). The signatures after P1:
  - `static JenaOntologyPort fromDocument(OntologyDocument document, ReasonerSetting reasoner, Supplier<Set<String>> registeredPluginNames)`
  - `static JenaOntologyPort fromSource(String propertyName, String location, ReasonerSetting reasoner, Supplier<Set<String>> registeredPluginNames)`
  - `static JenaOntologyPort fromStore(OntologyDocumentStore documents, Scope scope, String sourcePropertyName, String source, ReasonerSetting reasoner, Supplier<Set<String>> registeredPluginNames)`

  The constructor takes the supplier too. No overloads are kept. The Spring glue passes `JenaOntologyAutoConfiguration.registeredDerivationPlugins()`, a new static method that holds the `ServiceLoader` call now at lines 62, 69 and 70.

  Call sites: 49 lines in 8 files (verified). These are 45 test call sites in the moved tests, and 4 in staying files (`JenaOntologyAutoConfiguration` 2, `ShaclDataGraphBuilderTest` 1, `ShaclValidationPortContractTest` 1). All 49 gain the argument. The test classpath registers exactly one plugin, `WorkloadDerivationPlugin`, whose name is `"workload"`. It is the only `DerivationPlugin` in `sequeless-spi-testkit` and in the Jena test tree (verified). So the tests pass `() -> Set.of("workload")`. `UnknownPluginActivationTest` and the reference-plugin test in `JenaOntologyPortAcceptanceTest` (near line 320) keep their assertions.

- **P2. Plain settings.** `JenaOntologyAutoConfiguration` reads `properties.getReasoner()` and `properties.getSource()` (lines 62 to 70). Add a plain record, `JenaOntologySettings(ReasonerSetting reasoner, String source)`, in the Jena module, and map `JenaOntologyProperties` into it. No factory takes the record in spec 11. The glue unpacks it into the reasoner and source parameters that already exist. A settings-taking factory serves a non-Spring host, and none exists yet (ADR 0004: "plain constructors and a settings record").

S0 gate: `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify` green. The 49 call sites are updated and no assertion changes. The prototype's CI stays green.

### 3.2 Option A: `RollupRule.criteria` (in the import, target only)

**Shape (D23, open section 11 Q3).** The body applies `Optional<FilterNode> criteria`. Empty means no clause. One criterion becomes its `FieldFilter`. Two or more become an `AndFilter` in their order, which is the AND the prototype's javadoc describes. The alternative is `FilterNode criteria` with `AndFilter(List.of())` for none. That is one type, but every consumer must know that an empty AND matches everything, and how filter-sql translates it is **unverified**.

**Operators.** The source is the top-level `org.sequeless.spi.query.Operator`, eleven constants, mapped from `sq:operator` local names in `SnapshotMapper` (lines 597 to 608). The canonical names come from filter-core's `BuiltinOperatorContributor` (remote `main`, lines 14 to 30). Value shapes come from `OperatorDefinition.getValueShape()`: unary gives NONE, list-valued gives LIST, and every other operator gives SCALAR.

| `Operator` (`sq:operator`) | `FieldFilter.op` | Value shape | Applicable JSON types (filter-core) |
|---|---|---|---|
| `EQ` (`eq`) | `is` | SCALAR | all |
| `NE` (`ne`) | `is not` | SCALAR | all |
| `IN` (`in`) | `is in` | LIST: an array, even for one element | all |
| `LT`, `LTE`, `GT`, `GTE` | `is less than`, `is less than or equal to`, `is greater than`, `is greater than or equal to` | SCALAR | `number`, `integer` only |
| `CONTAINS` (`contains`) | `contains` | SCALAR | `string` only |
| `STARTS_WITH` (`startsWith`) | `starts with` | SCALAR | `string` only |
| `IS_NULL` (`isNull`) | `is`, value `NullNode.instance` | SCALAR | all. Renders `lhs IS NULL` (spec 06 section 3.3) |
| `NOT_NULL` (`notNull`) | `is not`, value `NullNode.instance` | SCALAR | all. Renders `lhs IS NOT NULL` |

Rules, each checked against the code:

- **Null is `NullNode.instance`, never Java `null`.** `FilterValidator.checkValueShape` (SCALAR) rejects a Java `null` with "requires a value". A `NullNode` passes.
- **`is in` wraps the value.** `SnapshotMapper.criterionOf` reads one `sq:value` literal (`valueStmt.getString()`), so an `IN` criterion always carries one scalar. The mapper wraps it in a one-element array. `checkValueShape` (LIST) rejects a scalar with "expects a list value".
- **Canonical names only.** Do not use the aliases `is null` or `is not null`. `is not null` is registered as an alias of `is` and renders `IS NULL` (spec 06 section 3.3; sequeless-filter #25, open).
- **Comparisons and LIKE are type-restricted.** `FilterValidator.validateField` (lines 56 to 61) rejects an operator whose applicable types do not contain the field's JSON Schema type. Comparisons apply to number and integer only, and `contains` and `starts with` to string only. So a comparison on a date or date-time property fails validation unless the registry types the field as a number. The prototype accepts such a comparison today. Its `Operator` javadoc says the comparisons "typically apply to numeric, date, and date-time properties". Section 11, Q4, decides this.
- **NULL and LIKE semantics** are filter-sql's (spec 06 section 3.3). `is not` includes NULL rows (`IS DISTINCT FROM`). The prototype's `PostgresQueryStore` already renders `NE` that way, and `CONTAINS` and `STARTS_WITH` with `ILIKE` (lines 457 to 495, verified). Whether its wildcard escaping matches filter's is **unverified**.

**JSON value per kernel `Value` variant.** `kernel-values` 0.1.0 (remote `main`) defines `Value` as a sealed interface over the eight variants below. The prototype's literal-to-`Value` mapping is `SnapshotMapper.coerceLiteral` (lines 920 to 929). The object-property path is `Value.ref(ObjectId.parse(…))` (line 875).

| Variant (prototype source) | JSON form | Datatypes that produce it (`Datatype`) |
|---|---|---|
| `TextValue` | string | `STRING`, `ANY_URI`, `TIME`, `DURATION` |
| `IntegerValue` (long) | integer number | `INTEGER`, `LONG` |
| `DecimalValue` (BigDecimal) | number | `DECIMAL`, `DOUBLE` |
| `BoolValue` | boolean | `BOOLEAN` |
| `DateValue` (LocalDate) | string, ISO-8601 date (`2026-11-08`) | `DATE` |
| `DateTimeValue` (Instant) | string, ISO-8601 instant | `DATE_TIME` |
| `ReferenceValue` (`ObjectId`) | string, canonical UUID (`ObjectId.value().toString()`) | object properties |
| `ListValue` | array of the above | not produced from `sq:value` today; `IN` wraps scalars instead |

The mapper writes these rules, and the tests pin them. filter-core does not define them: `FieldFilter.value` is a plain `JsonNode`. How the query side types a field (its `FieldRegistry` JSON Schema type per datatype) is spec 14's.

**Path (open section 11, Q5).** `FieldFilter.path` must equal the key the query side's `FieldRegistry` uses, because `FilterValidator` calls `fields.find(path)`. The DSL grammar admits only dotted identifiers (`Filter.g4`: `path : WORD (DOT WORD)*`), and `Criterion.property` is an IRI. The rule is open. The mapper's tests and the spec 12 bridge wait for it.

**Checks.** Each mapped node runs through `FilterValidator.validate(node, operators, fields)` in a test. The operators come from `BuiltinOperatorContributor`, and the fields from a registry built for the reference ontology. **Unverified:** the public constructors for those registries.

### 3.3 Consequences for spec 12 (recorded now)

- The prototype's `spi.meta.behaviour.RollupRule` keeps `List<Criterion>` until spec 12. Nothing in the prototype changes in spec 11.
- Consumers spec 12 must change (verified by grep): `DerivationPlanner` (sequeless-core, line 177, passes `rollup.criteria()` into `AggregateRequest`); `TypeResponseMapper` (sequeless-app, lines 303 to 305, renders the criteria); `SnapshotMapper` (it moves); and the tests `DefaultDerivationRecomputerTest`, `DefaultBusinessObjectServiceTest`, `DefaultMetaModelServiceTest`, `ValueCoercerTest`, the testkit's `QueryFixtures`, and the Jena tests `DerivationMappingTest` and `JenaOntologyPortAcceptanceTest` (lines 256 and 269).
- `AggregateRequest` takes `List<Criterion>` until spec 14 (#16 decision 5 replaces `Criterion`). So spec 12 needs a `FilterNode`-to-`Criterion` bridge in core. The bridge is exact only if each node's path maps back to the IRI that `Criterion` carries. With an IRI path (the section 11, Q5 default) that is the identity. With a short-name path, the bridge needs the Schema to map the name back to an IRI.
- `ShaclValidationPort` (which stays) calls `JenaOntologyPort.currentModel(Scope)` at line 57. That method is package-private (`JenaOntologyPort.java` line 361). After the move, the prototype's SHACL code cannot reach it. The options are in section 11, Q1.
- After spec 12 the prototype depends on `behaviour-contract`, directly or through the Jena adapter. That brings filter-core 0.1.3, its ANTLR runtime and Jackson into the prototype's classpath.
- The prototype's `ArchitectureTest.behaviourDependsOnlyOnSchema` allows `spi.query` for `Criterion`. The target rule allows `org.sequeless.filter.api` instead (section 5.4). The prototype rule changes in spec 12.

## 4. Package renames

D21 and D22 cover the contract modules and `ontology-core`. The Jena layout is open (section 11, Q2).

| From | To | Note |
|---|---|---|
| `org.sequeless.spi.meta.schema` | `org.sequeless.ontology.schema.api`; `SchemaSource` in `org.sequeless.ontology.schema.spi` | ADR 0001 api/spi split. `SchemaSource` is a driven port |
| `org.sequeless.spi.meta.behaviour` | `org.sequeless.ontology.behaviour.api`; `BehaviourSource` in `.spi` | Same |
| `org.sequeless.spi.meta.automation` | `org.sequeless.ontology.automation.api`; `AutomationSource` in `.spi` | Same |
| `org.sequeless.spi.ontology` | `org.sequeless.ontology.api` | #13 decision 3 puts `OntologyAdministration` in ontology-core's api |
| `org.sequeless.spi.object` (two types) | `org.sequeless.ontology.spi` | D19 |
| `org.sequeless.adapter.ontology.jena`, public types | `org.sequeless.ontology.jena` | `JenaOntologyPort`, `ReasonerSetting`, `JenaOntologySettings` (P2), and `OntologySource` (its only user is `JenaOntologyPort`, verified) |
| `org.sequeless.adapter.ontology.jena`, compiler | `org.sequeless.ontology.jena.internal` | `SnapshotMapper`, `SqVocabulary`, `SqAnnotations`, `ConsistencyChecker`, `ImportResolver`, `OntModelBuilder`, `TurtleExporter`. Classes that `JenaOntologyPort` calls become `public`, a visibility change only. The layout is section 11, Q2 |
| `org.sequeless.testkit.ontology` and the ontology part of `org.sequeless.testkit.Fixtures` | `org.sequeless.ontology.testfixtures` | #13 decision 10 |
| `org.sequeless.ontology.owl.{api,spi}` (`package-info` only) and the owl test package | `org.sequeless.ontology.jena` (and `.internal`) | O3. The module rename is in S1 (`0e6d8ec`). The Java packages change in S2 |

Notes:

- Class names stay: `JenaOntologyPort`, `SnapshotMapper`, `OntologyAdministration`. Spec 10 D11 left renames to spec 11 or later. Keeping the names keeps spec 12's diff small. The core use case keeps its name (spec 10 Q2), so `OntologyAdministration` is fully qualified in core until spec 12.
- Repo-segment package names follow spec 8: `org.sequeless.kernel.*`, `org.sequeless.filter.*` and `org.sequeless.expression.*`.
- The testkit's `AutomationContract` tests `spi.automation.AutomationPort`. That is a different scope from `automation-contract`, so `automation-contract` gets no kit class in spec 11. The prototype keeps its copy. The simple name is shared, but the scopes differ, so this is not a clash.

## 5. Modules and their dependencies

### 5.1 Module list after spec 11 (`sequeless-ontology`)

| Module | Artifact | Release | Depends on |
|---|---|---|---|
| (root) | `sequeless-ontology` | aggregator. Keeps the Spring ban and the Enforcer rules | none |
| `ontology-core` | `ontology-core` | 0.1.0 | `kernel-identity` 0.1.0 |
| `schema-contract` | `schema-contract` | 0.1.0 | `kernel-identity` 0.1.0 |
| `behaviour-contract` | `behaviour-contract` | 0.1.0 | `schema-contract`, `kernel-identity`, `filter-core` 0.1.3 |
| `automation-contract` | `automation-contract` | 0.1.0 | `schema-contract`, `kernel-identity`, `kernel-values` 0.1.0 |
| `ontology-jena-adapter` | `ontology-jena-adapter` | 0.1.0 | section 5.3 (D28) |
| `ontology-testfixtures` | `ontology-testfixtures` | 0.1.0 | `ontology-core`, the three contracts, `kernel-identity`. JUnit and AssertJ at compile scope, as in ADR 0002's pattern |
| `ontology-facet-core`, `ontology-facet-core-testfixtures` | frozen, not released | unchanged | nothing new |

The Jena module also has `ontology-testfixtures` at test scope, for `JenaOntologyPortContractTest` and the moved tests. No prototype module changes its dependencies in spec 11.

### 5.2 Which artifacts depend on the four released artifacts

The BOM (remote `main`, 0.1.2) lists `kernel-identity` 0.1.0, `kernel-values` 0.1.0, `filter-core` 0.1.3, `filter-sql-adapter` 0.1.4, the filter testkit and postgres adapter, and the four expression artifacts at 0.1.1. Its pom says libraries do not import it (D24).

- **`kernel-identity`:** `ontology-core`, the three contracts, `ontology-jena-adapter`, and `ontology-testfixtures` (directly or through a contract). `facet-core` has none (verified).
- **`kernel-values`:** `automation-contract` (`SetPropertyAction`, `PropertyAssignment`) and `ontology-jena-adapter`. The Jena adapter needs it whatever happens to D16. `SnapshotMapper` builds `Value` for criterion values (lines 812 to 929) and for `sq:SetProperty` through `resolveValueForProperty` (line 1537). Not `schema-contract`, and not `behaviour-contract` (D26).
- **`expression-api`:** none. No file under `spi/meta`, `spi/ontology` or the Jena module imports `org.sequeless.expression` (verified).
- **`filter-core`:** `behaviour-contract` (`FilterNode`, `FieldFilter`, `AndFilter`), and `ontology-jena-adapter`, which builds these nodes directly (D28). Through `behaviour-contract`, also `ontology-testfixtures`. Not `schema-contract`, and not `ontology-core`.

### 5.3 Direct dependencies of `ontology-jena-adapter` (D28)

The Jena adapter declares in its own POM: `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `kernel-identity` 0.1.0, `kernel-values` 0.1.0, `filter-core` 0.1.3, `jackson-databind` (version from the root POM, 2.18.3, verified), and `jena-ontapi` 6.1.0. Its tests add `ontology-testfixtures` at test scope. `jena-shacl` is not a dependency, because SHACL stays (D17). ADR 0001 says each module redeclares what it uses.

Weight: `filter-core` brings `jackson-databind` 2.18.3 (the root pins the same version, verified), `jackson-annotations` and `antlr4-runtime` 4.13.2 (filter-core's pom, verified). The root POM enables `dependencyConvergence` (verified). Whether Jena's transitive versions converge with these is **unverified**.

### 5.4 ArchUnit rules (ADR 0001 needs one `BoundaryRulesTest` per module)

- `ontology-core`: drop `allowEmptyShould(true)` from `api_and_spi_types_are_public`. Its TODO says to do so once real types land, and this import is that point.
- Owl rules. `owl_adapter_does_not_reach_ontology_core_internals` forbids `..spi..`, but the adapter must use `OntologyDocumentStore` from ontology-core's spi. Replace it with `ontology_jena_adapter_does_not_reach_ontology_core_internals`, whose allowed set is ontology-core's `api` and `spi` and the three contract packages. Rename `ontology_core_does_not_depend_on_owl_adapter` for the Jena package. This reverses ADR 0001's consequence that the adapter "never reaches" ontology-core's internal and spi packages. ADR 0004 covers the reversal: it puts the `OntologyDocumentStore` spi in ontology-core, and it makes the adapter an implementer of the contract ports.
- `schema-contract` ports the prototype rule `schemaDependsOnlyOnJdkKernelAndItself` (`sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java`, line 208).
- `behaviour-contract` ports `behaviourDependsOnlyOnSchema` (line 219), with `org.sequeless.filter.api..` in place of `org.sequeless.spi.query..`.
- `automation-contract` ports `automationDependsOnlyOnBehaviourAndSchema` (line 233), without the behaviour package (D20).
- Each contract's api and spi types are public.
- The root's `ban-spring`, `dependencyConvergence` and `requireJavaVersion [21,)` stay.

## 6. Release config (first version, tags, BOM change)

### 6.1 First version and tags

- Every new module releases once, at 0.1.0 (#23 comment 1, "First versions"). Released: `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-jena-adapter` and `ontology-testfixtures`. Not released: the two facet modules (O3).
- Tags: `<component>-v0.1.0`, from `include-component-in-tag: true` and `tag-separator: "-"` in the migration branch's config (verified).
- The root's `v0.1.0` tag exists on the remote, pointing at `fd4f748`. That commit is not in `origin/main`'s history (verified with `merge-base --is-ancestor`). `CHANGELOG.md` on `main` has a `1.0.0 (2026-09-16)` section from the failed semantic-release run, and no `1.0.0` tag exists on the remote. `remaining-work.md` says no tag deletion is needed. The migration keeps the CHANGELOG as history.
- ADR 0004 says each module declares its own version. Section 6.2 says what the migration branch does about it.

### 6.2 The migration branch (S1), as it stands

The branch `chore/release-please-migration` in `sequeless-ontology` has three commits on top of `ace5313` (verified). It is not merged.

- `2d6250a` (migration). Deletes `.releaserc.json`. Adds `release-please-config.json` with `release-type: maven`, `include-component-in-tag`, `tag-separator: "-"`, `bootstrap-sha: ace5313` (so pre-migration `feat` commits open no 0.1.0 release), the `maven-workspace` plugin with `considerAllArtifacts: false`, and two `packages`, each with `initial-version: 0.1.0` and `skip-snapshot`. The manifest is `{}`. That is the kernel approach, not the filter approach. `release.yml` calls `sequeless-build`'s `maven-release.yml` at `workflows-v0.1.2` with `java-version` 21. The root POM gets a GitHub Packages `distributionManagement`. The facet POMs set `maven.deploy.skip`. The Makefile's release dry run is removed, and the semantic-release wording in README and CONTRIBUTING is updated.
- `0e6d8ec` (rename). `ontology-owl-adapter` becomes `ontology-jena-adapter` in the directory, artifactId, name, reactor entry, release package, `sonar-project.properties` and README. Java packages are unchanged.
- `6d36f67` (groupId and version). `ontology-core` and `ontology-jena-adapter` declare `groupId` `org.sequeless` and `<version>` 0.1.0 themselves. The Jena adapter depends on `ontology-core` at the literal 0.1.0. The commit says the first release failed while the children inherited these from the root.
- Not in S1: `ci.yml`, which is unchanged (zero diff against `ace5313`), and the parent (section 7).

What S2 must add to S1's config and POMs:

- Four `packages` entries: `schema-contract`, `behaviour-contract`, `automation-contract` and `ontology-testfixtures`, each with `initial-version` 0.1.0 and `skip-snapshot`. The config then lists six modules.
- Each new module declares `groupId`, `artifactId` and `<version>` 0.1.0, as `6d36f67` does for the other two. Modules that depend on each other pin 0.1.0 literally.
- A `feat` commit in the import does not bump the version, because `initial-version` applies to the first release and no release exists yet. That is the kernel precedent. **Unverified** for ontology until S4 runs.

### 6.3 BOM change (sequeless-build)

- Today the BOM lists no ontology artifact (remote `main`, 0.1.2).
- The change (D25): the BOM lists six ontology artifacts at 0.1.0: `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-core`, `ontology-jena-adapter` and `ontology-testfixtures`.
- Commit title: `fix(bom): list the ontology modules at 0.1.0`. Precedents: `fix(bom): list filter-sql-adapter 0.1.4, …` (`b4027f8`) and `fix(bom): list the expression modules at 0.1.1` (`1e5c73d`). The remote release config has `bump-minor-pre-major: true` (verified), so a `feat` would release 0.2.0. The change is therefore a `fix`, and the release is `sequeless-bom` 0.1.3.
- Only released versions go in (`BomListsOnlyReleasesTest`).
- Then `bom-contracts` gets the ontology contract: `OntologyBomContractTest extends OntologyContract`, with `JenaOntologyPort` as the port. It has the same shape as `ExpressionBomContractTest` (remote `main`, verified). Its import moves from 0.1.2 to 0.1.3 in the same PR, because `remaining-work.md` says the import is bumped on each BOM release. The BOM release comes first, since the import needs it.

### 6.4 Release steps that belong to spec 11

The DoD says "the release and the BOM re-release are done". In order:

1. S1 merged, then S1b.
2. S2 merged, with the merge method from section 11, Q6, then S3.
3. The release-please release PR for the six modules at 0.1.0 is merged, and each artifact publishes to GitHub Packages (S4). The precedent is `sequeless-kernel`, whose config sets `initial-version: 0.1.0` (remote `main`, verified).
4. The BOM fix PR is merged and `sequeless-bom` 0.1.3 is released (S5a).
5. The `bom-contracts` PR is merged, and its CI is green (S5b).

Not part of spec 11: the prototype's use of the new artifacts (spec 12).

## 7. CI and workflows

**Today (verified at `ace5313`, the head of `main`).** `ci.yml` runs `./mvnw -B verify` on JDK 21. Its job "Build (verify + gates)" succeeds. Its SonarCloud job fails, because `sonar.organization` is not set. `release.yml` runs the old semantic-release job, which fails. The migration branch replaces `release.yml` (section 6.2), not `ci.yml`.

**S1b (CI and parent PR, `sequeless-ontology`).** Three items, all before S2, because S2 adds dependencies that GitHub Packages must resolve:

1. Move `ci.yml` to `sequeless/sequeless-build/.github/workflows/maven-ci.yml` at the `workflows-v0.1.2` commit, as `sequeless-filter` does. Its `ci.yml` pins `maven-ci.yml` at `63bad368`, tagged `workflows-v0.1.2` (remote `main`, verified). Add `packages: read` for GitHub Packages resolution.
2. Adopt the sequeless-build parent. #23 comment 1, point 4 decides this: "The migration adopts the sequeless-build parent and workflows". The ontology root POM has no `<parent>` and no `<repositories>` today (verified). So kernel, filter and expression resolution from GitHub Packages has no parent to inherit repository settings from. Whether anonymous read works for those packages is **unverified**. The parent version for ontology is **unverified**. Filter uses 0.1.3 (its `remaining-work.md` entry).
3. The SonarCloud job: fix or drop. Section 11, Q8. Default: drop, as sequeless-filter did (`remaining-work.md`, the filter migration decisions).

**Formatting.** The prototype's formatter comes from its parent. The parent at tag `sequeless-build-parent-v0.1.2` pins `palantir-java-format` 2.102.0 (verified). The ontology root pins 2.50.0 (verified at `ace5313`, line 43). Expect a formatting commit in S2. **Unverified:** how many moved files it changes.

**JDK.** The prototype builds on 25 (`maven.compiler.release` 25, verified). The ontology builds on 21 (its CI, verified). The moved code must compile at `release 21`. **Unverified.** It is a gate in S2.

**Local gate for the target:** `./mvnw -B verify`. It runs the spotless check (bound to `verify`), the Enforcer rules, the ArchUnit tests, surefire and failsafe. JaCoCo's check is off by default (verified in the root POM).

Definition of done, quoted from #39, with this spec's reading:

- "The new repo's CI is green." No new repo. Read as: `sequeless-ontology`'s CI on `main` is green after S1, S1b, S2, S3 and each release.
- "The prototype's CI is green against *released* artifacts (never a SNAPSHOT on `main`)." Spec 11 changes no prototype dependency. The root POM keeps `sequeless-bom` 0.1.2 (line 64, verified). The S6 docs PR's CI is the proof.
- "The BOM's CI, which runs every testkit contract, is green." Before S5b, `bom-contracts` runs `BomListsOnlyReleasesTest` and the expression contract (remote `main`, verified). S5b adds the ontology contract.
- "The release and the BOM re-release are done." Section 6.4.
- "For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA." Not applicable here. Spec 12 carries it, and its reverse pointer uses the import SHA.

## 8. ADRs

Sources (verified):

- ADR 0004 and 0005 drafts: commit `af03f93` (2026-10-14), on branch `wayfinder/ontology-move-adrs` in the prototype's clone, local and on origin. The prototype-ui clone has it too. Both files are under `docs/drafts/`. There is no 0003 draft in this commit.
- ADR 0003 draft: commit `06dee75` (2026-10-14, "docs: draft sequeless-ontology ADR amending ADR 0002 for kernel-identity"), at the head of branch `wayfinder/kernel-adr-0002-amendment` in the prototype's clone, local and on origin. File: `docs/drafts/sequeless-ontology-adr-0003-facet-core-depends-on-kernel-identity.md`.

Each ADR is committed to `docs/adr/` in `sequeless-ontology` as `NNNN-<slug>.md`, with the draft's HTML comment header removed. Status stays `proposed`, as drafted. ADR 0000 says ADRs are immutable, so ADR 0001 and ADR 0002 are not edited. The drafts have no `## Context` or `## Decision` headings, and ADR 0001 in the target has them. S3 keeps the drafts as written, so they stay off-template. Whether the owner wants the template headings is **unverified**; the target's `_template.md` is not compared here.

- **ADR 0003** (`0003-facet-core-depends-on-kernel-identity.md`, source `06dee75`). Decision: `facet-core` may take one compile dependency, `kernel-identity`, and its `Principal` folds into it. Amends 0002. Its code (an Enforcer `<includes>` exception and an ArchUnit change) is not part of spec 11, because `facet-core` is frozen. The home is open (section 11, Q7). #13 decision 11 says the draft becomes a sequeless-query ADR, while #39 says commit it here.
- **ADR 0004** (`0004-thin-core-jena-adapter-independent-versions.md`, source `af03f93`). Decision: `ontology-core` is thin (the `OntologyAdministration` api, the validation api and the `OntologyDocumentStore` spi). The Jena adapter is `ontology-jena-adapter`, Spring-free. `SchemaSource` lives in `schema-contract` and `BehaviourSource` in `behaviour-contract`. Each module versions and releases on its own. `ontology-testfixtures` publishes the contract kits. Amends 0001. **Added in S3:** `AutomationSource` lives in `automation-contract`. The draft omits it (spec 10 section 8; #20 comment). The draft also says SHACL validation "is implemented in `ontology-jena-adapter`" and names `jena-shacl`. D17 defers that, and S3 does not edit it.
- **ADR 0005** (`0005-facet-core-moves-to-sequeless-query.md`, source `af03f93`). Decision: `facet-core` and `facet-core-testfixtures` move to `sequeless-query` (spec 16, #44). The zero-dependency rule moves with them. Supersedes 0002.

## 9. Slice plan

| Slice | Repo, PR | Does | Depends on | Gate |
|---|---|---|---|---|
| **S0 prototype prerequisite** | `sequeless-prototype`, one PR | P1 and P2 (section 3.1). 49 call sites in 8 files | none. Can run alongside S1 | `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify` green. No assertion changed |
| **S1 release-please migration** | `sequeless-ontology`, PR from `chore/release-please-migration` (commits `2d6250a`, `0e6d8ec`, `6d36f67`), not yet merged | Section 6.2 | none | Build job green on the PR. Facet modules are not listed as packages. No Java package changed |
| **S1b CI and parent** | `sequeless-ontology`, one PR after S1 | Section 7 items 1 to 3 | S1 merged. The Sonar item: section 11, Q8 | Build job green on the PR. GitHub Packages resolution of `kernel-identity` works from the target's CI. **Unverified** until this PR runs |
| **S2 import** | `sequeless-ontology`, one PR. One import commit titled per D27, the provenance section in its body, plus any formatting commit | Sections 2 to 5 and 10.1. Copies only. The prototype is not touched | S0, S1, S1b. **Blocked on section 11, Q1 to Q6** | `./mvnw -B verify` green on JDK 21 (spotless, Enforcer, ArchUnit, moved tests, `OntologyContract` for the Jena port and the in-memory port, `OntologyDocumentStoreContract` for the in-memory store). The owl grep in section 10.3 is clean |
| **S3 ADRs** | `sequeless-ontology`, PR separate from S2 | Section 8. 0004 and 0005 as drafted plus `AutomationSource`. 0003 as drafted | S2. 0003 is also blocked on section 11, Q7 | CI green. Each file matches its source, except the stated `AutomationSource` edit in 0004 |
| **S4 release 0.1.0** | `sequeless-ontology`, release-please release PR | Section 6.1: six modules at 0.1.0, tags, GitHub Packages | S2 and S3 on `main` | Each artifact resolves at 0.1.0 from GitHub Packages in a clean build. The release run is green. Must finish before S5a |
| **S5a BOM** | `sequeless-build`, PR `fix(bom): list the ontology modules at 0.1.0`, then the 0.1.3 release | Section 6.3, first half | S4 | `BomListsOnlyReleasesTest` green. Six entries at 0.1.0. Release published |
| **S5b bom-contracts** | `sequeless-build`, one PR | `OntologyBomContractTest`. The import moves to 0.1.3 | S5a | `bom-contracts` CI green, running the ontology contract |
| **S6 prototype docs** | `sequeless-prototype`, docs only | This spec's status. The import SHA for spec 12. The `remaining-work.md` row. The root POM comment on line 100 (its tracker item) | S2 to S5b | Prototype CI green on the PR. Import SHA recorded for spec 12 |

What no slice does: delete a prototype file (spec 12), change a prototype dependency (spec 12), or touch `facet-core` or `facet-core-testfixtures`.

Merge method for S2 (section 11, Q6): the default is a merge commit, not a squash, so the import commit keeps its own SHA on `main`. The two precedents, `f036c1d` and `017b292dd`, are squash merges, since their titles end in a PR number.

## 10. Test plan

### 10.1 Target tests

- Moved and passing without assertion changes: the tests in section 2 (18 spi tests, 11 Jena tests, and the contract runners).
- `OntologyContract` (ontology-testfixtures) passes for `JenaOntologyPort` (`JenaOntologyPortContractTest`) and for `InMemoryOntologyPort` (runner in `src/test`). This is the S2 contract gate.
- `OntologyDocumentStoreContract` passes for `InMemoryOntologyDocumentStore` and for the Jena port's document store path.
- The reference-domain parity assertions from spec 10 section 10.3 (`stateMachine`, `derivation` and `actions` equal the prototype's values at `aafb5a9`) move with `JenaOntologyPortAcceptanceTest`.
- New `RollupRuleMappingTest` (Jena module). One case per `Operator` (section 3.2 table), including `NOT_NULL` as `is not` with `NullNode`, never the alias. `IN` wraps one scalar. Zero criteria give `Optional.empty()`, one gives a `FieldFilter`, and two give an `AndFilter` in order. Each result passes `FilterValidator` (section 3.2). A date comparison gets whatever section 11, Q4, decides.
- Rewritten: the `criteria()` assertions in `DerivationMappingTest` (lines 105, 131, 158, 570) and `JenaOntologyPortAcceptanceTest` (lines 256, 269). They check the same facts in the `FilterNode` form.
- New `BoundaryRulesTest` classes in `schema-contract`, `behaviour-contract` and `automation-contract` (section 5.4), and the rewritten rules in `ontology-core` and the Jena module.

### 10.2 Prototype tests

None change in spec 11 beyond S0, which changes the Jena tests' factory calls (section 3.1). The prototype's `ArchitectureTest` keeps its 17 rules.

### 10.3 Verification commands

- Target, after S2: `git grep -nE 'org\.sequeless\.(spi|adapter|testkit)\.' -- '*.java'` returns nothing. The target never imports prototype packages.
- Target, after S1 and S2: `git grep -nE 'ontology[.-]owl|owl-adapter|ontology\.owl\b'` returns only `docs/adr/0001-…` and `CHANGELOG.md`, which are history. On the migration branch before S2, it returns the owl Java packages and ADR 0001.
- Prototype, S0 and S6: `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify`, in one shell command.

## 11. Open questions

Each question is undecided. The body applies the default, and that application is not a decision. Blocked slices are named.

1. **D17. SHACL placement.** Options: (a) keep SHACL in the prototype until spec 12 (default). Spec 12 then needs `JenaOntologyPort.currentModel(Scope)` (package-private, line 361) reachable from the prototype. The choices are to make it public, which puts a Jena `OntModel` on the adapter's public surface, or to make a further change in sequeless-ontology with a release, which is the shape of #13 decision 6. (b) Move SHACL now. That needs `spi.validation` and `BusinessObject` in the target, which is the kernel-typed SHACL api of spec 12 pulled into this spec. Blocks S2 (file list).
2. **Jena adapter layout (D21).** Options: (a) no api/spi split: `org.sequeless.ontology.jena` for public types and `.internal` for the compiler (default). This matches the expression precedent `org.sequeless.expression.jexl`, which the owner chose (`remaining-work.md`, spec 8). (b) `org.sequeless.ontology.jena.api` and `.internal`, as ADR 0001 literally says. Blocks S2 (packages).
3. **D23. Shape of `criteria`.** Options: (a) `Optional<FilterNode>` (default). Empty means no clause, and no empty `AndFilter` is ever built. (b) `FilterNode` always, with `AndFilter(List.of())` for none. That is one type, but it depends on how filter-sql translates an empty AND, which is **unverified**. This becomes the published API of `behaviour-contract` 0.1.0, so the choice is hard to undo. Blocks S2 and S4.
4. **Comparisons on date, date-time and non-text properties.** `FilterValidator` rejects `is less than` and the rest on date types, and `contains` and `starts with` on non-string types (section 3.2). The prototype accepts them. Options: (a) reject at load with an ERROR `OntologyIssue`, in the style of `SnapshotMapper`'s existing `sq:filter` errors (default). Under (a), an ontology with such a rule fails activation in spec 12, where it activates today. (b) Map it anyway and type the field as a number, which is wrong. (c) Ask filter-core for date comparison operators, which is a change and a release in `sequeless-filter`. Blocks S2 (mapper).
5. **Path of `FieldFilter` for a property.** Options: (a) the property IRI, as an opaque key (default). It is lossless, so the spec 12 bridge is exact, and the query side's registry keys by IRI (spec 14 decides). It departs from the documented dotted form. The DSL grammar enforces that form on parsed input, not on AST nodes built in code. (b) The short name. That needs uniqueness across all properties, which is **unverified** (`typeByName` relies on uniqueness for types only), and the bridge then needs the Schema. Related: #14 and #16 decision 5. Blocks S2 (mapper) and the spec 12 bridge.
6. **Merge method for S2.** Options: (a) a merge commit (default), so the import commit keeps its SHA on `main`, which spec 12 names. (b) A squash, which folds any formatting commit into the import and changes the SHA that spec 12 cites. The repo allows all three methods (verified), and the two precedents were squashed. Blocks the S2 merge.
7. **ADR 0003 home.** Options: (a) commit 0003 here as drafted, as #39 says, and record that spec 16 moves it with `facet-core` (default). (b) Do not commit 0003 here. Write it in `sequeless-query` with `facet-core`, as #13 decision 11 says. Blocks S3 for 0003 only.
8. **Sonar job.** Options: (a) drop the SonarCloud job, as sequeless-filter did (default). (b) Fix it by setting `sonar.organization` and its credentials in the repo. Blocks the Sonar item of S1b.
9. **`sq-meta.ttl` split (#13 decision 8, against #23 comment 1, point 2).** Options: (a) move the file whole (default), since #23 says an extraction is a move, and split it later. (b) Split it into `sq-schema.ttl` and `sq-behaviour.ttl` in this spec. That is a content change in the import. Blocks nothing, and the default is applied.
10. **D20. Automation and behaviour.** #20's comment says `automation-contract` depends on `behaviour-contract`. The code does not need it. Default: no dependency, as drafted. The owner confirms. It blocks only the automation POM, and only if the owner reverses it before S2.

Decided, and not open: O1 (Option A, the owner's answer on #39 on 2026-11-08). Its deviation from #23 is recorded as D16. The rename's placement is in S1 (`0e6d8ec`). D26 (no `kernel-values` in schema and behaviour) is a decision, not a question, because #12 point 3 is not an owner approval and the code is the evidence.
