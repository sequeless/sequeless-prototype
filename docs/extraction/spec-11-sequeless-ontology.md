# Spec 11 — sequeless-ontology gains the prototype's ontology

Issue: [#39](https://github.com/sequeless/sequeless-prototype/issues/39). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#12](https://github.com/sequeless/sequeless-prototype/issues/12), [#13](https://github.com/sequeless/sequeless-prototype/issues/13), [#14](https://github.com/sequeless/sequeless-prototype/issues/14), [#16](https://github.com/sequeless/sequeless-prototype/issues/16), [#20](https://github.com/sequeless/sequeless-prototype/issues/20), [#23](https://github.com/sequeless/sequeless-prototype/issues/23), [#24](https://github.com/sequeless/sequeless-prototype/issues/24). Tracker: `docs/extraction/remaining-work.md`.

Status: draft, rework 4 after the third review. Not reviewed by the owner. No PR. The open questions in section 11 are not decided here, and the slices they block say so. Line numbers in this spec are verified at `a3a6911`, which is the baseline for line numbers only (section 2). `origin/main` is `f32f3c2`. PR #88 changes docs and the root POM only, and no moved path (verified). Remote facts are read on 2026-11-08.

## 0. Scope and decisions

Scope, from #39: migrate `sequeless-ontology` to release-please, and rename `ontology-owl-adapter` to `ontology-jena-adapter`. Then make one import commit that brings `spi.meta` (the schema, behaviour and automation halves), `spi.ontology`, the Jena adapter, the `sq:` vocabulary and the three contract modules. Spec 11 also releases 0.1.0 of each new module and re-releases the BOM. It does not cut over. The prototype keeps its copies until spec 12 ([#40](https://github.com/sequeless/sequeless-prototype/issues/40)), which deletes them and names the import SHA.

Owner answers and applied decisions:

- **O1. Option A** (#39 comment, 2026-11-08). `RollupRule.criteria` changes from `spi.query.Criterion` to filter-core's `FilterNode`, in the import. It departs from #23 comment 1, point 2. D16 records that.
- **O3.** Migrate to release-please first, rename `ontology-owl-adapter`, then one import commit. `facet-core` and `facet-core-testfixtures` are frozen and excluded from release and deploy. Every module first releases 0.1.0.
- **O4.** Java packages change in the import PR, not in the migration PR (#39).

Decided before this spec:

- **#13 resolution** (2026-10-14). Jena stays. `ontology-core` is thin: the `OntologyAdministration` api, the validation api and the `OntologyDocumentStore` spi. There are two read ports, `SchemaSource` and `BehaviourSource`, and one admin port, `OntologyAdministration`. #20 adds `AutomationSource` as a third read port. No Spring in this repo (decision 9). Contract kits in `ontology-testfixtures` (decision 10). Independent module versions (decision 12).
- **#20 comment** (2026-10-18), item 2, read as placing `AutomationSource` in `automation-contract`. The comment says `AutomationSource` "sits next to `BehaviourSource`".
- **#23 comment 1.** Existing repos take moved code in one import commit with a provenance section, and never merge histories (point 3). Behaviour changes land in the prototype first (point 2). The migration adopts the sequeless-build parent and workflows (point 4). First versions are 0.1.0, and they release before the BOM re-release ("First versions" bullet).
- **Spec 10 section 11, "Answers recorded"** (merged in PR #85, line 399). The orchestrator accepted the defaults for questions 2 to 4: the core use case keeps the name `OntologyAdministration`, (tenant, Ontology version) keying lands in spec 15 as default methods, and automation is actions-only. The owner has not reviewed these here.

Decided here. D-numbers continue from spec 10's D14. Each D-number is defined once, in this list. "Open" marks a decision the owner must make. The body applies its default, and section 11 names the slices it blocks.

- **D15.** The import is a copy, not `git mv`. It is one commit in `sequeless-ontology`, with the provenance section in its body. No history is merged (#23 comment 1, point 3).
- **D16.** Option A (O1) changes the type of `RollupRule.criteria` in the import, not in the prototype. This departs from #23 comment 1, point 2. The owner's answer on #39 is the source. The mapping is section 3.2.
- **D17. Decided (section 11, Q1), by #13 decision 6.** SHACL validation moves with the import: its api is in `ontology-core` and its implementation is in `ontology-jena-adapter`, next to the loader. The body below still states the superseded default (SHACL stays in the prototype).
- **D18.** The Spring glue, `JenaOntologyProperties`, the `AdapterDescriptorProvider` classes and their `META-INF` resources stay in the prototype (#13 decision 9).
- **D19.** `OntologyDocumentStore` and `StoredOntologyDocument` move to `ontology-core`'s spi (#13 decision 7). The in-memory document store, and the two document-store clauses of `ObjectStoreContract`, are copied into `ontology-testfixtures`.
- **D20. Decided (section 11, Q10):** `automation-contract` depends on `behaviour-contract`, as #20 decision 2 says. The body below states the superseded default (no dependency).
- **D21. Decided (section 11, Q2):** the Jena adapter has no api package. Its public types are in `org.sequeless.ontology.jena` and its compiler in `org.sequeless.ontology.jena.internal`. The contract modules and `ontology-core` keep ADR 0001's api, spi and internal split.
- **D22.** `ontology-core` drops `jackson-databind`, which no moved type uses (ADR 0001: each module redeclares only what it uses).
- **D23. Decided (section 11, Q3):** `criteria` is `Optional<FilterNode>`: empty means no clause, one criterion is a `FieldFilter`, and two or more are an `AndFilter`.
- **D24.** Libraries pin released versions directly and never import the BOM (the BOM pom says so). The pins are `kernel-identity` 0.1.0, `kernel-values` 0.1.0, `filter-core` 0.1.3 and `jena-ontapi` 6.1.0. The ontology modules are at 0.1.0. Under Q11 option (b) the filter-core pin changes (section 11, Q11).
  > **Superseded by section 11, Q11:** the release level is 25 (section 11, Q11).
- **D25.** The BOM lists six ontology artifacts at 0.1.0, including `ontology-testfixtures`. The expression precedent lists `expression-testkit` in the BOM (remote `main`, verified).
- **D26. Decided (section 11, Q12):** `schema-contract` and `behaviour-contract` depend on `kernel-values`, as #12 point 3 says. No code uses it yet. The body below states the superseded default (no dependency).
- **D27.** The import commit title is `feat(ontology): import the ontology from sequeless-prototype@<source SHA>`. The source SHA is the prototype's `main` commit after S0 merges, not the baseline (section 2). A squash sets its title and body from the repository's squash settings, not from the PR's head commit. Kernel is set to `PR_TITLE` and `PR_BODY`; sequeless-ontology to `COMMIT_OR_PR_TITLE` and `COMMIT_MESSAGES` (verified with the API). A one-commit PR squashes to its own commit's title and body, and a multi-commit PR to the PR title plus the commit messages. Kernel PR #2's head commit was titled `feat(kernel): import identity and values from sequeless-prototype@3a070ad`. Its squashed commit `f036c1d814` landed as `feat(kernel): import identity and values from sequeless-prototype (#2)`, with the PR body underneath. So, under either setting, the PR title must equal the D27 title, and the PR body and the import commit body must carry the provenance section. Expression PR #2 (head `1c8fba18db`) was squashed the same way (`017b292dd6`, single parent). The merge method is open (section 11, Q6).
- **D28.** The Jena adapter declares `filter-core` 0.1.3, `jackson-databind`, `kernel-values`, `jena-ontapi`, `ontology-core`, the three contracts and `kernel-identity` directly (ADR 0001). Section 5.3 gives the reasons.
- **D29.** The plugin-name lookup enters the Jena port as a `Supplier<Set<String>>`, not a `Set`, because the lookup runs on every build (section 3.1).

Out of scope, and the spec that takes it:

- Deleting the prototype's copies, the reverse pointer and the prototype's dependency on the new artifacts: spec 12 (#40).
- The kernel-typed SHACL api and the move of SHACL code (D17's default): spec 12.
  > **Superseded by section 11, Q1:** SHACL validation moves with the import. The validation api is in `ontology-core`, and its implementation is in `ontology-jena-adapter`, next to the loader.
- `ensureIndexes` and its ADR: specs 13 and 14. The facet move (ADR 0005, #44): spec 16.
- `expression-api` in the compiler (#20). The moved code uses no `expression` package today (verified). #20 names no spec for it. Which spec takes it is **unverified**.

## 1. Identity, stage and dependencies

- Stage 3, import. Spec 11 of 19. Waits for spec 5 (#33, done), spec 8 (#36, done) and spec 10 (#38, merged as `e53c0f1`, PR #85). `remaining-work.md` lists all three as done.
- Unblocks spec 12 (#40). Specs 14 and 15 also wait for this spec.
- Branch `spec-11-sequeless-ontology`, rebased on `origin/main` `f32f3c2`.
- Repos touched: `sequeless-prototype` (S0 prerequisite, S6 docs), `sequeless-ontology` (S1, S1b, S2, S3, S4), and `sequeless-build` (S5a, S5b). `sequeless-filter` and `sequeless-kernel` are consumed and not changed, except under Q11 option (b), the default, where `sequeless-filter` changes: it must publish filter-core built at release 21.
  > **Superseded by section 11, Q11:** The release level is 25 (owner answer on #39, 2026-11-08). Done in sequeless-ontology PR #4 (827c472).

## 2. Moves

Provenance:

- **Source repo:** `sequeless/sequeless-prototype`.
- **Baseline SHA:** `a3a6911e0797f962630e5577cb48514bc03afc0d`. Every line number in this spec is verified at this SHA. It is not the source SHA of the import. S0 changes moved files (P1 and its call sites), and S2 depends on S0, so the import cannot come from `a3a6911`.
- **Source SHA of the import:** the prototype's `main` commit after S0 merges. It goes into the import's title (D27) and body. The target's import-commit SHA is recorded in S6 for spec 12, which names it. This spec does not name that SHA.
- **Last commit that changed the moved paths at the baseline:** `e53c0f1` (2026-11-07). S0 adds a later one.
- **Source paths:** relative to the prototype root. Tables below.
- **The move:** a copy, in one import commit. The prototype is unchanged by the import. Its copies are deleted in spec 12.
- **Date:** `<import date>`.

Known limitations:

- No `git mv` and no `git log --follow`. The import is cross-repo, and #23 forbids merged history. Spec 10's rename caveats carry over: `SchemaTest` (from `MetaModelSnapshotTest`), `OntologyAdministrationTest` (from `OntologyPortTest`), and `OntologyAdministration.java` (48% similar to `OntologyPort.java`). The target has no history for any of them.
- 23 commits touch the Jena module path at the baseline. None is carried over.

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
| `src/main/resources/ontology/sq-meta.ttl` | ontology-jena-adapter, same path | Moves whole under the default. S2 is blocked on the split question (section 11, Q9). `ImportResolver.SQ_META_CLASSPATH_RESOURCE` (line 30) names this path. Byte-identical to the testkit copy (verified) |
| `JenaOntologyAutoConfiguration`, `JenaOntologyProperties`, `ShaclValidationAutoConfiguration` | stays | Spring (D18; D17 for the SHACL class) |
| `JenaOntologyAdapterDescriptorProvider`, `ShaclValidationAdapterDescriptorProvider`, `META-INF/services/…`, `META-INF/spring/…imports` | stays | D18, D17 |
| `ShaclValidationPort`, `ShaclDataGraphBuilder` | stays | D17 (section 11, Q1). They import `spi.validation` and `spi.object.BusinessObject` |

> **Superseded by section 11, Q9:** S0b (Q9) split this file, merged as PR #94 (8c35bc2). It is now sq-schema.ttl (old lines 1-184, the single owl:Ontology) and sq-behaviour.ttl (old lines 186-349). S2 moves both files.

Tests, spi (`sequeless-spi/src/test/java/org/sequeless/spi/`), 18 files:

- schema-contract (8): `SchemaTest`, `TypeDefinitionTest`, `AttributeDefinitionTest`, `RelationshipDefinitionTest`, `CardinalityTest`, `DatatypeTest`, `DisplayHintsTest`, `OntologyVersionTest`.
- behaviour-contract (1): `meta/behaviour/BehaviourTest`. automation-contract (1): `meta/automation/AutomationTest`.
- ontology-core (8): `ImportReportTest`, `OntologyAdministrationTest`, `OntologyDocumentTest`, `OntologyExceptionTest`, `OntologyIssueTest`, `OntologyReportTest`, `SeverityTest`, and `object/StoredOntologyDocumentTest`, which moves with its type (D19).

Tests, Jena (18 files). Moved (11): `ConsistencyCheckerTest`, `DerivationMappingTest`, `Halves`, `JenaOntologyPortAcceptanceTest`, `JenaOntologyPortContractTest`, `JenaOntologyPortStorePersistenceTest`, `JenaOntologyPortTest`, `SnapshotMapperTest`, `StateMachineMappingTest`, `TurtleExporterTest`, `UnknownPluginActivationTest`. Stay (7): `JenaOntologyAutoConfigurationTest`, `JenaOntologyPropertiesTest`, `JenaOntologyAdapterDescriptorProviderTest`, `ShaclValidationAutoConfigurationTest`, `ShaclValidationAdapterDescriptorProviderTest`, `ShaclDataGraphBuilderTest`, `ShaclValidationPortContractTest`.

> **Superseded by section 11, Q1:** SHACL validation moves with the import. Its validation api lives in ontology-core, and its implementation lives in ontology-jena-adapter, next to the loader. The Command side keeps ValidationPort and adapts it.

Table 2.3, testkit and shared test helpers to `ontology-testfixtures` (`org.sequeless.ontology.testfixtures`):

| Source | Target | Note |
|---|---|---|
| `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/ontology/`: `OntologyContract`, `OntologyPorts`, `package-info` | `src/main/java`, same names | `package-info` links are rewritten (section 2.1). The prototype copy stays until spec 12. `OntologyContract` uses `org.sequeless.testkit.Fixtures` in 28 places, so it takes the subset below |
| `sequeless-spi-testkit/src/main/resources/ontology/`: `reference.ttl`, `inconsistent.ttl`, `inverse-transitive.ttl`, `reference-plugin.ttl` | `src/main/resources/ontology/` | 4 files: the ones the moved code loads, directly or through `Fixtures`. `unknown-plugin.ttl` stays in the testkit, since no moved code loads it |
| `org.sequeless.testkit.Fixtures` (`sequeless-spi-testkit/src/main/java/org/sequeless/testkit/Fixtures.java`), ontology subset only | `org.sequeless.ontology.testfixtures.Fixtures` | The moved code uses four loaders (`referenceOntology`, `inconsistentOntology`, `inverseTransitiveOntology`, `referencePluginOntology`), `defaultScope()` (line 160), and 25 IRI constants. `Fixtures` imports only kernel-identity and `spi.ontology` (verified). The prototype keeps the full class |
| `InMemoryOntologyPortContractTest.java`, `FakeOntologyPort` (javadoc at lines 47 to 52, class at 53 to 131, 85 lines in all; line 132 closes the outer class) | `InMemoryOntologyPort` in `src/main/java` | 85 lines extracted. The runner stays in `src/test`. The fake implements the four ports, so it moves with the contract |
| `InMemoryObjectStorePort.java` (testkit test sources), `InMemoryOntologyDocumentStore` (javadoc at lines 216 to 221, class at 222 to 248, 33 lines; line 249 closes the outer class) | `InMemoryOntologyDocumentStore` in `src/main/java` (D19) | 33 lines copied, class javadoc included. The prototype keeps its private copy |
| `ObjectStoreContract.java`: `ontologyDocumentsRejectsNullArguments` (line 184) and `ontologyDocumentActiveIsEmptyInitiallyThenTracksVersions` (line 403) | `OntologyDocumentStoreContract` in `src/main/java` (D19) | **Adapted, not copied as is.** The clauses call `freshStore()`, so the copy takes a `freshDocuments()` factory and keeps the assertions. The prototype keeps both clauses until spec 12 |

### 2.1 Javadoc rewrites (main code, and the test javadoc that names prototype types)

Checked on the moved main files at the baseline, by a sweep of every `{@link}`, `{@code org.sequeless…}` and `@throws` line, and every import of a type that stays in the prototype.

Two kinds of reference exist, and they need different handling:

- **Fully qualified references to types that move.** These are rewritten by the package rename (section 4). The rename rewrites them in `{@link}`, `{@code}` and `@throws`, not only in imports. Examples: `TurtleExporter.java` lines 22 and 37, `JenaOntologyPort.java` line 231 (`@throws`), and the `package-info` files named below.
- **References to types that stay in the prototype.** These are changed by hand. An import is not enough. A `{@link}` to a type that stays becomes `{@code}` or plain text.

| File (moved unless noted) | Lines | Reference | Action |
|---|---|---|---|
| `spi/meta/behaviour/PluginRule.java` | 5, 7, 12 | `{@link org.sequeless.spi.derivation.DerivationPlugin}` (stays) | `{@code}` |
| `spi/meta/behaviour/RollupRule.java` | 13, 21, 34, 35 | `{@link …spi.query.AggregateRequest}`, `{@link Criterion}` (21, 34), `{@link …spi.query.Query#criteria()}` (stay) | `{@code}`. The text describes `FilterNode` (Option A) |
| `spi/meta/behaviour/StateMachineDefinition.java` | 11 | `{@link org.sequeless.spi.object.BusinessObject#state()}` (stays) | `{@code}` |
| `spi/meta/behaviour/package-info.java` | 6 | "Like every package beneath `{@code org.sequeless.spi}`…" (text) | Reworded. Non-breaking |
| `spi/meta/behaviour/package-info.java` | 9, 21 | `{@link …spi.query.Criterion}` (9), `{@link …spi.derivation.DerivationPlugin}` (21) | `{@code}`. Text rewritten for `FilterNode` |
| `spi/meta/schema/AggregateFunction.java` | 6, 18, 25, 33, 40, 47 | `{@link …spi.query.AggregateRequest}`, `{@link …spi.query.AggregateResult}` (stay) | `{@code}` |
| `spi/meta/schema/AggregateFunction.java` | 4, 7, 9, 11, 19, 24, 32, 39, 46 | `{@code org.sequeless.spi.meta.behaviour.RollupRule…}` (text; schema-contract does not depend on behaviour-contract) | Reworded to plain text |
| `spi/meta/schema/package-info.java` | 5 | "Like every package beneath `{@code org.sequeless.spi}`…" (text) | Reworded. Non-breaking |
| `spi/meta/schema/package-info.java` | 15 | `{@link …spi.query.AggregateRequest}` (stays) | `{@code}`. Lines 19–20 name behaviour and automation as text, which is fine |
| `spi/meta/automation/package-info.java` | 4 | "Like every package beneath `{@code org.sequeless.spi}`…" (text) | Reworded. Non-breaking |
| `spi/meta/automation/Action.java` | 4, 7 | `{@link …meta.behaviour.Transition}`, `{@link …meta.behaviour.DerivationRule}` (D20: no behaviour dependency) | `{@code}` |
| `spi/meta/automation/SetPropertyAction.java` | 14 | `{@link …meta.behaviour.RollupRule}` | `{@code}` |
| `spi/meta/automation/package-info.java` | 5–6 | Text: "sibling types in `meta.schema` and `meta.behaviour`" | Reworded: automation imports only schema and kernel |
| `spi/meta/automation/package-info.java` | 20 | `{@code org.sequeless.spi.meta.behaviour.Transition}` (a `{@code}`, not a link) | Reworded to plain text. No build effect |
| `spi/meta/automation/package-info.java` | 7–16 | Links to automation types | Rename only |
| `spi/meta/automation/Automation.java` | 15 | `{@code …meta.behaviour.StateMachineDefinition.transitions()}` (text) | Reworded to "the transition order of the state machine" |
| `SchemaSource.java`, `BehaviourSource.java`, `AutomationSource.java` | 14 | `{@code org.sequeless.spi.ontology.OntologyException}` | `{@code org.sequeless.ontology.api.OntologyException}` |
| `spi/ontology/OntologyException.java` | 11 | `{@code org.sequeless.core.AuthorizationException}` (text) | Reworded to plain text |
| `spi/ontology/OntologyException.java` | 19–20 | `{@link org.sequeless.spi.authz.AccessDecision}` (stays in the prototype; the link breaks) | `{@code}` |
| `spi/ontology/package-info.java` | 6 | "Like every package beneath `{@code org.sequeless.spi}`…" (text) | Reworded. Non-breaking |
| `spi/ontology/package-info.java` | 20 | "mechanically by `{@code sequeless-spi-testkit}`'s `{@code OntologyContract}`" (text) | Reworded to name `ontology-testfixtures`. Non-breaking |
| `spi/meta/schema/Datatype.java` | 10–11 | `{@link org.sequeless.spi.ontology.Severity#WARNING}` (`Severity` moves to ontology-core, and schema-contract does not depend on ontology-core, section 5.1) | `{@code}` |
| `spi/object/OntologyDocumentStore.java` | 8 | `{@link ObjectStorePort#ontologyDocuments()}` (stays) | `{@code}` |
| `spi/object/OntologyDocumentStore.java` | 13 | `{@code sequeless-spi-testkit}`'s `{@code ObjectStoreContract}` | Names `OntologyDocumentStoreContract` in the new home |
| Jena `JenaOntologyPort.java` | 143, 485 | `{@link …spi.derivation.DerivationPlugin}`, `{@link DerivationPlugin}`. The code has three uses, all removed by P1 | `{@code}` |
| Jena `JenaOntologyPort.java` | 132 | `{@link #buildStateFrom(OntModel)}` (class javadoc). P1 adds a parameter | S0 updates the parameter list |
| Jena `JenaOntologyPort.java` | 141 | `{@link #unknownPluginIssues(List, Map)}` (class javadoc). P1 adds a parameter | S0 updates the parameter list |
| Jena `JenaOntologyPort.java` | 51, 52 | `{@link #fromDocument(OntologyDocument, ReasonerSetting)}`, `{@link #fromSource(String, String, ReasonerSetting)}`. Both signatures change in P1 | S0 updates the parameter lists to the P1 signatures |
| Jena `JenaOntologyPort.java` | 358 | `{@link ShaclValidationPort}` (stays) | `{@code}` |
| Jena `JenaOntologyPort.java` | 231 | `@throws org.sequeless.spi.ontology.OntologyException` (moves) | Rename only |
| Jena `TurtleExporter.java` | 22, 37 | `{@link org.sequeless.spi.ontology.OntologyAdministration#importDocument}`, `{@link org.sequeless.spi.meta.schema.Schema}` (both move) | Rename only |
| Jena `SnapshotMapper.java` | 590, 914 | `{@code org.sequeless.spi.query.Operator}`, `{@code org.sequeless.core.validation.ValueCoercer}` (text) | Reworded to plain text |
| Jena `SnapshotMapper.java` | 742, 1578 and code | `{@link Criterion}` (javadoc). Code: `Operator` (14 uses) and `Criterion` (7 uses) | The Option A mapping (section 3.2) replaces both imports. The mapper imports no `spi.query` type |
| Jena `JenaOntologyProperties.java` (stays) | 10–11, 45 | `{@link JenaOntologyPort#fromSource(String, String, ReasonerSetting)}`. The parameter list is stale after P1 | S0 updates the parameter list |
| Jena `JenaOntologyAutoConfiguration.java` (stays) | 39 | `{@link JenaOntologyPort#fromStore}` (no parameter list) | Resolves after P1. No change |
| Jena tests, `JenaOntologyPortAcceptanceTest.java` | 313–314 | `META-INF/services/org.sequeless.spi.derivation.DerivationPlugin}`, a services path that the target does not have | Reworded: "the testkit's services registration". Test javadoc, not built |
| Jena tests, `UnknownPluginActivationTest.java` | 21–22 | `{@link org.sequeless.spi.derivation.DerivationPlugin}` (stays) | `{@code}`. Test javadoc, not built |
| testkit `ontology/package-info.java` | 2–5, 7, 9–10 | Links to `spi.ontology.*`, `spi.meta.*` and `testkit.ontology.*` | Rename only. Targets follow the new packages (section 4) |

The test javadoc rows are not built by javadoc, but a reference to a missing type is still wrong, so they are listed. Build gate for the main code: section 9, S2.

## 3. Prerequisite refactors

### 3.1 Prototype prerequisite PR (S0, before the import)

Both changes are seams. They keep behaviour, so the import stays a move.

- **P1. Plugin names come from the host.** The port looks up the registered `DerivationPlugin` names on every build. The lookup is in `unknownPluginIssues(List, Map)`, a private static helper (line 501). `buildStateFrom(OntModel)`, also a private static helper (line 427), calls it at line 459. `buildState(Supplier)` (instance method, line 404) calls `buildStateFrom` at line 414, and the constructor calls `buildState` at line 180. The javadoc (lines 485 to 492) relies on this per-build lookup, so a plugin deployed after startup is seen on the next reload. The moved code cannot depend on `spi.derivation`. So the port stores a `Supplier<Set<String>> registeredPluginNames` as a field, assigned before the constructor's first `buildState` call. `buildStateFrom` and `unknownPluginIssues` take the supplier as a new last parameter, and `unknownPluginIssues` calls `get()` on each build. A plain `Set` would freeze the snapshot and break the documented behaviour (D29). The signatures after P1:
  - `static JenaOntologyPort fromDocument(OntologyDocument document, ReasonerSetting reasoner, Supplier<Set<String>> registeredPluginNames)`
  - `static JenaOntologyPort fromSource(String propertyName, String location, ReasonerSetting reasoner, Supplier<Set<String>> registeredPluginNames)`
  - `static JenaOntologyPort fromStore(OntologyDocumentStore documents, Scope scope, String sourcePropertyName, String source, ReasonerSetting reasoner, Supplier<Set<String>> registeredPluginNames)`
  - `private static Built buildStateFrom(OntModel model, Supplier<Set<String>> registeredPluginNames)`
  - `private static List<OntologyIssue> unknownPluginIssues(List<TypeDefinition> types, Map<String, DerivationRule> derivations, Supplier<Set<String>> registeredPluginNames)`

  The constructor takes the supplier too. No overloads are kept. The Spring glue passes `JenaOntologyAutoConfiguration.registeredDerivationPlugins()`, a new static method that holds the `ServiceLoader` call. The glue's factory calls are at lines 61 (`fromSource`) and 65 (`fromStore`). It reads `properties.getSource()` and `properties.getReasoner()` at lines 62, 69 and 70.

  Call sites: 49 lines in 8 files (verified). These are 45 test call sites in the moved tests, and 4 in staying files (`JenaOntologyAutoConfiguration` 2, `ShaclDataGraphBuilderTest` 1, `ShaclValidationPortContractTest` 1). All 49 gain the argument. The test classpath registers exactly one plugin, `WorkloadDerivationPlugin`, whose name is `"workload"`. It is the only `DerivationPlugin` in `sequeless-spi-testkit` and in the Jena test tree (verified). So the tests pass `() -> Set.of("workload")`. The assertions do not change, including those in `UnknownPluginActivationTest` and the reference-plugin test in `JenaOntologyPortAcceptanceTest` (near line 320).

- **P2. Plain settings.** Add a plain record, `JenaOntologySettings(ReasonerSetting reasoner, String source)`, in the Jena module, and map `JenaOntologyProperties` into it. No factory takes the record in spec 11. The glue unpacks it into the reasoner and source parameters that already exist. A settings-taking factory serves a non-Spring host, and none exists yet (ADR 0004: "plain constructors and a settings record").

S0 gate: `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify` green. The 49 call sites are updated, the javadoc links in section 2.1 that name the changed signatures are updated, and no assertion changes. The prototype's CI stays green.

### 3.2 Option A: `RollupRule.criteria` (in the import, target only)

**Shape (D23, open section 11, Q3).** The body applies `Optional<FilterNode> criteria`. Empty means no clause. One criterion becomes its `FieldFilter`. Two or more become an `AndFilter` in their order, which is the AND the prototype's javadoc describes. The alternative is `FilterNode criteria` with `AndFilter(List.of())` for none. That is one type, but every consumer must know that an empty AND matches everything, and how filter-sql translates it is **unverified**.

**Operators.** The source is the top-level `org.sequeless.spi.query.Operator`, eleven constants, mapped from `sq:operator` local names in `SnapshotMapper` (lines 597 to 608). The mapper stops importing that enum (section 2.1). Canonical names come from filter-core's `BuiltinOperatorContributor` (remote `main`, lines 14 to 30). Value shapes come from `OperatorDefinition.getValueShape()`: unary gives NONE, list-valued gives LIST, and every other operator gives SCALAR.

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
- **Canonical names only.** Do not use the aliases `is null` or `is not null`. `is not null` is registered as an alias of `is` and renders `IS NULL` (spec 06 section 3.3; sequeless-filter #25, an open issue).
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
  > **Superseded by section 11, Q1:** SHACL validation moves with the import. The validation api is in `ontology-core`, and its implementation is in `ontology-jena-adapter`, next to the loader.
- After spec 12 the prototype depends on `behaviour-contract`, directly or through the Jena adapter. That brings filter-core 0.1.3, its ANTLR runtime and Jackson into the prototype's classpath.
- The prototype's `ArchitectureTest.behaviourDependsOnlyOnSchema` allows `spi.query` for `Criterion`. The target rule allows `org.sequeless.filter.api` instead (section 5.4). The prototype rule changes in spec 12.

## 4. Package renames

D21 and D22 cover the contract modules and `ontology-core`. The Jena layout is decided (section 11, Q2).

The rename rewrites every reference to a moved type. That means imports, `{@link}`, `{@code}` and `@throws`, fully qualified or not (section 2.1).

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
- The testkit's `AutomationContract` tests `spi.automation.AutomationPort`, in the scope of the prototype's automation port. `automation-contract` is a different scope, so it gets no kit class in spec 11, and the prototype keeps its copy. The simple name is shared across two scopes, which is why this is not a clash to fix.

## 5. Modules and their dependencies

### 5.1 Module list after spec 11 (`sequeless-ontology`)

| Module | Artifact | Release | Depends on |
|---|---|---|---|
| (root) | `sequeless-ontology` | aggregator. Under S1b it stays a parent-less aggregator (section 7) | none |
| `ontology-core` | `ontology-core` | 0.1.0 | `kernel-identity` 0.1.0 |
| `schema-contract` | `schema-contract` | 0.1.0 | `kernel-identity` 0.1.0 (kernel-values: Q12) |
| `behaviour-contract` | `behaviour-contract` | 0.1.0 | `schema-contract`, `kernel-identity`, `filter-core` 0.1.3 (kernel-values: Q12; the filter-core pin changes under Q11 (b)) |
| `automation-contract` | `automation-contract` | 0.1.0 | `schema-contract`, `kernel-identity`, `kernel-values` 0.1.0 (behaviour-contract: Q10) |
| `ontology-jena-adapter` | `ontology-jena-adapter` | 0.1.0 | section 5.3 (D28) |
| `ontology-testfixtures` | `ontology-testfixtures` | 0.1.0 | `ontology-core`, the three contracts, `kernel-identity`. JUnit and AssertJ at compile scope, as in ADR 0002's pattern |
| `ontology-facet-core`, `ontology-facet-core-testfixtures` | frozen, not released | unchanged | nothing new |

> **Superseded by section 11, Q12:** schema-contract and behaviour-contract depend on kernel-values, as #12 point 3 says.

> **Superseded by section 11, Q10:** automation-contract depends on behaviour-contract, as #20 decision 2 says.

> **Superseded by section 11, Q11:** the release level is 25 (section 11, Q11).

The Jena module also has `ontology-testfixtures` at test scope, for `JenaOntologyPortContractTest` and the moved tests. No prototype module changes its dependencies in spec 11.

### 5.2 Which artifacts depend on the four released artifacts

The BOM (remote `main`, 0.1.2) lists `kernel-identity` 0.1.0, `kernel-values` 0.1.0, `filter-core` 0.1.3, `filter-sql-adapter` 0.1.4, the filter testkit and postgres adapter, and the four expression artifacts at 0.1.1. Its pom says libraries do not import it (D24).

- **`kernel-identity`:** `ontology-core`, the three contracts, `ontology-jena-adapter`, and `ontology-testfixtures` (directly or through a contract). `facet-core` has none (verified).
- **`kernel-values`:** `automation-contract` (`SetPropertyAction`, `PropertyAssignment`) and `ontology-jena-adapter`. The Jena adapter needs it whatever happens to D16. `SnapshotMapper` builds `Value` for criterion values (lines 812 to 929) and for `sq:SetProperty` through `resolveValueForProperty` (line 1537). Schema and behaviour: see Q12 (D26).
  > **Superseded by section 11, Q12:** schema-contract and behaviour-contract depend on kernel-values, as #12 point 3 says.
- **`expression-api`:** none. No file under `spi/meta`, `spi/ontology` or the Jena module imports `org.sequeless.expression` (verified).
- **`filter-core`:** `behaviour-contract` (`FilterNode`, `FieldFilter`, `AndFilter`), and `ontology-jena-adapter`, which builds these nodes directly (D28). Through `behaviour-contract`, also `ontology-testfixtures`. Not `schema-contract`, and not `ontology-core`.

### 5.3 Direct dependencies of `ontology-jena-adapter` (D28)

The Jena adapter declares in its own POM: `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `kernel-identity` 0.1.0, `kernel-values` 0.1.0, `filter-core` 0.1.3, `jackson-databind` 2.18.3 and `jena-ontapi` 6.1.0. The `jackson-databind` version is declared in the module POM under S1b, as `filter-core` does (its own `jackson-databind.version` property, `filter-core/pom.xml` line 26, verified). The parent 0.1.3 manages only ArchUnit (`dependencyManagement`, lines 67 to 89; the comment at line 64 says so), so the root's `jackson-databind.version` 2.18.3 (line 49 on the migration branch, line 37 at `ace5313`) stops applying under S1b. Its tests add `ontology-testfixtures` at test scope. `jena-shacl` is not a dependency, because SHACL stays (D17). ADR 0001 says each module redeclares what it uses. Under Q11 (b) the `filter-core` line changes to the version filter publishes at release 21.

> **Superseded by section 11, Q1:** SHACL validation moves with the import. The validation api is in `ontology-core`, and its implementation is in `ontology-jena-adapter`, next to the loader.

> **Superseded by section 11, Q11:** The release level is 25 (owner answer on #39, 2026-11-08). Done in sequeless-ontology PR #4 (827c472).

Weight: `filter-core` brings `jackson-databind` 2.18.3 (the root pins the same version, verified), `jackson-annotations` and `antlr4-runtime` 4.13.2 (filter-core's pom, verified). The root POM enables `dependencyConvergence` (verified), and the parent 0.1.3 enables it too (line 214). Whether Jena's transitive versions converge with these is **unverified**. Build and release levels: section 7 and Q11.

### 5.4 ArchUnit rules (ADR 0001 needs one `BoundaryRulesTest` per module)

- `ontology-core`: drop `allowEmptyShould(true)` from `api_and_spi_types_are_public`. Its TODO says to do so once real types land, and this import is that point.
- The Jena module's owl rules, from the migration branch (`BoundaryRulesTest`, lines 20 to 53):
  - `api_and_spi_types_are_public` (lines 20 to 32, with its `allowEmptyShould` and its TODO on `owl.api`/`owl.spi`). Under the Q2 default there is no api or spi package in the Jena module, so this rule is **dropped** for the Jena module. Under Q2 option (b), it is **rewritten** for `org.sequeless.ontology.jena.api`.
    > **Confirmed by section 11, Q2:** No api package. Public types are in `org.sequeless.ontology.jena` and the compiler is in `.internal`. The `api_and_spi_types_are_public` rule is dropped.
  - `owl_adapter_does_not_reach_ontology_core_internals` (lines 34 to 41) forbids `..spi..`, but the adapter must use `OntologyDocumentStore` from ontology-core's spi. Replace it with `ontology_jena_adapter_does_not_reach_ontology_core_internals`, whose allowed set is ontology-core's `api` and `spi` and the three contract packages. This reverses ADR 0001's consequence that the adapter "never reaches" ontology-core's internal and spi packages. ADR 0004 covers the reversal: it puts the `OntologyDocumentStore` spi in ontology-core, and it makes the adapter an implementer of the contract ports.
  - `ontology_core_does_not_depend_on_owl_adapter` (lines 43 to 53) is renamed for the Jena package.
- `schema-contract` ports the prototype rule `schemaDependsOnlyOnJdkKernelAndItself` (`sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java`, line 208).
- `behaviour-contract` ports `behaviourDependsOnlyOnSchema` (line 219), with `org.sequeless.filter.api..` in place of `org.sequeless.spi.query..`.
- `automation-contract` ports `automationDependsOnlyOnBehaviourAndSchema` (line 233), without the behaviour package (D20).
  > **Superseded by section 11, Q10:** automation-contract depends on behaviour-contract, as #20 decision 2 says.
- Each contract's api and spi types are public.
- Each module inherits the sequeless-build parent's enforce-baseline: `requireJavaVersion [25,)` (the build JDK floor, separate from the release level in Q11) and `dependencyConvergence`. The parent has two enforcer executions: `enforce-baseline` (lines 200 to 218) and `ban-core-dependencies` (lines 219 to 238). The second bans Spring, Jena and Lombok, and it is skipped unless a module sets `sequeless.enforcer.core.skip` to false. That property is `true` at line 43 (verified), and #17 point 5 is the decision behind it. The parent has no Spring-only ban. The Jena adapter's Spring ban is handled in section 7, S1b item 2.

## 6. Release config (first version, tags, BOM change)

### 6.1 First version and tags

- Every new module releases once, at 0.1.0 (#23 comment 1, "First versions"). Released: `ontology-core`, `schema-contract`, `behaviour-contract`, `automation-contract`, `ontology-jena-adapter` and `ontology-testfixtures`. Not released: the two facet modules (O3).
- Tags: `<component>-v0.1.0`, from `include-component-in-tag: true` and `tag-separator: "-"` in the migration branch's config (verified).
- The root's `v0.1.0` tag exists on the remote, pointing at `fd4f748`. That commit is not in `origin/main`'s history (verified with `merge-base --is-ancestor`). `CHANGELOG.md` on `main` has a `1.0.0 (2026-09-16)` section from the failed semantic-release run, and no `1.0.0` tag exists on the remote. `remaining-work.md` says no tag deletion is needed. The migration keeps the CHANGELOG as history.
- ADR 0004 says each module declares its own version. Section 6.2 says what the migration branch does about it.

### 6.2 The migration branch (S1), as it stands

The branch `chore/release-please-migration` in `sequeless-ontology` has three commits on top of `ace5313` (verified). It is open as PR sequeless-ontology#3 (verified), with head `chore/release-please-migration`, titled "chore: migrate release to release-please and rename ontology-jena-adapter". It is not merged.

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
- Then `bom-contracts` gets the ontology contract: `OntologyBomContractTest extends OntologyContract`, with `JenaOntologyPort` as the port. It has the same shape as `ExpressionBomContractTest` (remote `main`, verified). Its import moves from 0.1.2 to 0.1.3 in the same PR, because `remaining-work.md` says the import is bumped on each BOM release. The BOM release comes first, since the import needs it. Its release level is re-checked under Q11 (section 7).

### 6.4 Release steps that belong to spec 11

The DoD says "the release and the BOM re-release are done". In order:

1. S1 merged, then S1b.
2. S2 merged, with the merge method from section 11, Q6, then S3.
3. The release-please release PR for the six modules at 0.1.0 is merged, and each artifact publishes to GitHub Packages (S4). The precedent is `sequeless-kernel`, whose config sets `initial-version: 0.1.0` (remote `main`, verified).
4. The BOM fix PR is merged and `sequeless-bom` 0.1.3 is released (S5a).
5. The `bom-contracts` PR is merged, and its CI is green (S5b).

Not part of spec 11: the prototype's use of the new artifacts (spec 12).

## 7. CI, workflows and Java level

**Today (verified at `ace5313`, the head of `main`).** `ci.yml` runs `./mvnw -B verify` on JDK 21. Its job "Build (verify + gates)" succeeds. Its SonarCloud job fails, because `sonar.organization` is not set. `release.yml` runs the old semantic-release job, which fails. The migration branch replaces `release.yml` (section 6.2), not `ci.yml`. The branch's `ci.yml` pins Java `"21"` at lines 25 and 42.

**Java level.** Two different things are called "Java level" here. Q11 decides only the second.

- **Build JDK: 25, under both options.** #17's owner-closed answer says "everything is built on JDK 25" (point 1 of the resolution comment). The sequeless-build parent's `enforce-baseline` requires `requireJavaVersion [25,)` (sequeless-build-parent 0.1.3, rule at lines 208 to 210 inside `enforce-baseline`, lines 200 to 218, verified). The reusable CI workflow defaults to 25 (verified). S1b adopts the parent, so the ontology builds on JDK 25 through it. S1b also sets the ontology's CI and release `java-version` to 25 (`release.yml` and `ci.yml`).
- **Release level (`maven.compiler.release`, the bytecode target): Q11.** The parent sets 21 (line 37). #17 says libraries compile with `--release 21`.
  > **Superseded by section 11, Q11:** The release level is 25 (owner answer on #39, 2026-11-08). Done in sequeless-ontology PR #4 (827c472).

The release-level problem, with evidence:

- The filter-core tag `filter-core-v0.1.3` sets `maven.compiler.release` to 25 (pom line 22, verified). The local Maven copy of `filter-core-0.1.3.jar` has `FilterNode.class` at class-file major version 69 (Java 25). `kernel-values-0.1.0.jar` has `IntegerValue.class` at major 65 (Java 21) (verified).
- A stub importing `org.sequeless.filter.api.FilterNode`, compiled with `javac --release 21` (JDK 21.0.13) against that jar, fails with "class file has wrong version 69.0, should be 65.0". The stub ran in a scratch directory and was deleted. A release-25 check cannot run on this JDK.
- sequeless-filter's `ci.yml` passes no `java-version` input, so filter builds on the reusable workflow's default, 25 (verified).
- `bom-contracts` compiles at 21 (`maven.compiler.release` 21, pom line 28, remote `main`, verified). Its enforcer requires `[25,)`, so its tests run on JDK 25.
- `remaining-work.md` line 83 says the Java 25 floor on sequeless-filter is consumer-facing, and it must be recorded in an ADR in that repo.

**Javadoc and the release profile.** The release workflow runs `-Prelease` (`maven-release.yml`, lines 59 and 66, at `workflows-v0.1.2`). The migration branch's root POM has no `<profile>` (verified), so `-Prelease` does nothing there today. The parent 0.1.3 has a `release` profile (the `<profiles>` block is lines 318 to 350, the profile itself 319 to 349, its comment 316 to 317; verified) that binds maven-source-plugin `jar-no-fork` and maven-javadoc-plugin `jar`. It pins maven-javadoc-plugin 3.11.2 (line 56, used at lines 178 to 179). After S1b each module inherits the parent's release profile (sources and javadoc jars) and maven-javadoc-plugin 3.11.2, so `-Prelease` runs javadoc from S2 on. The S2 gate still runs `javadoc:javadoc` explicitly. Spec 8's 0.1.0 deploy failed on a javadoc error (`remaining-work.md`, line 25).

**S1b (CI, parent and Java level PR, `sequeless-ontology`).** Three items, all before S2, because S2 adds dependencies that GitHub Packages must resolve:

1. Move `ci.yml` to `sequeless/sequeless-build/.github/workflows/maven-ci.yml` at the `workflows-v0.1.2` commit, as `sequeless-filter` does. Its `ci.yml` pins `maven-ci.yml` at `63bad368`, tagged `workflows-v0.1.2` (remote `main`, verified). Add `packages: read` for GitHub Packages resolution (verified in `maven-ci.yml`, line 18). The reusable workflow's `java-version` input defaults to `'25'` (verified at that SHA). `java-version` is 25 under both Q11 options: the parent's enforce-baseline is `[25,)`, and #17 section 1 says the build runs on JDK 25. S1b sets it explicitly in `ci.yml` and in `release.yml`.
2. Adopt the sequeless-build parent. #23 comment 1, point 4 decides this: "The migration adopts the sequeless-build parent and workflows". Placement: **each module** takes `sequeless-build-parent` 0.1.3, and the root stays a parent-less aggregator. This follows `sequeless-filter` (`remaining-work.md`, line 45: "the root pom is a parent-less aggregator and each module takes `sequeless-build-parent` 0.1.3"). Maven gives each module one parent, so the ontology root's own settings (its spotless pin, its Enforcer rules and its `dependencyManagement`) stop applying to the modules by inheritance. The Jena adapter cannot opt into `ban-core-dependencies`, because it depends on Jena, so S1b copies filter's per-module `ban-spring` enforcer execution (`sequeless-filter`, `filter-core/pom.xml`, lines 173 to 194). Whether `ontology-core`, the three contracts and the testfixtures opt into `ban-core-dependencies` by setting `sequeless.enforcer.core.skip` to false, as #17 point 5 says, is **unverified**. `filter-core` does not set that property (verified). Which other root settings must move is **unverified**. The parent 0.1.3 declares `https://maven.pkg.github.com/sequeless/*` in its `<repositories>` (lines 91 to 102, verified), which each module inherits. The reusable CI workflow sets Maven credentials for GitHub Packages (`maven-ci.yml`, lines 30 to 36, verified), so CI resolves kernel, filter and expression. The parent version is 0.1.3, the pin filter uses.
3. The SonarCloud job: fix or drop. Section 11, Q8. Default: drop, as sequeless-filter did (`remaining-work.md`, the filter migration decisions).
   > **Confirmed by section 11, Q8:** the Sonar job is dropped. The required SonarCloud Code Analysis check stays.

**Formatting.** The formatter comes from the parent: `palantir-java-format` 2.102.0, pinned by sequeless-build-parent 0.1.3 (line 60, verified). Once S1b adopts the parent, the moved code is formatted at 2.102.0, not the root's 2.50.0 (verified at `ace5313`, line 43). Expect a formatting commit in S2. **Unverified:** how many moved files it changes.

**Local gate for the target:** `./mvnw -B verify`. It runs the spotless check (bound to `verify`), the Enforcer rules, the ArchUnit tests, surefire and failsafe. JaCoCo's check is off by default (verified in the root POM).

Definition of done, quoted from #39, with this spec's reading:

- "The new repo's CI is green." No new repo. Read as: `sequeless-ontology`'s CI on `main` is green after S1, S1b, S2, S3 and each release.
- "The prototype's CI is green against *released* artifacts (never a SNAPSHOT on `main`)." Spec 11 changes no prototype dependency. The root POM keeps `sequeless-bom` 0.1.2 (line 64, verified). The S6 docs PR's CI is the proof.
- "The BOM's CI, which runs every testkit contract, is green." Before S5b, `bom-contracts` runs `BomListsOnlyReleasesTest` and the expression contract (remote `main`, verified). S5b adds the ontology contract, at the release level Q11 sets, on JDK 25.
- "The release and the BOM re-release are done." Section 6.4.
- "For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA." Not applicable here. Spec 12 carries it, and its reverse pointer uses the import SHA.

## 8. ADRs

Sources (verified):

- ADR 0004 and 0005 drafts: commit `af03f93` (2026-10-14), on branch `wayfinder/ontology-move-adrs` in the prototype's clone, local and on origin. The prototype-ui clone has it too. Both files are under `docs/drafts/`. There is no 0003 draft in this commit.
- ADR 0003 draft: commit `06dee75` (2026-10-14, "docs: draft sequeless-ontology ADR amending ADR 0002 for kernel-identity"), at the head of branch `wayfinder/kernel-adr-0002-amendment` in the prototype's clone, local and on origin. File: `docs/drafts/sequeless-ontology-adr-0003-facet-core-depends-on-kernel-identity.md`.

Each ADR is committed to `docs/adr/` in `sequeless-ontology` as `NNNN-<slug>.md`, with the draft's HTML comment header removed. Status stays `proposed`, as drafted. ADR 0000 says ADRs are immutable, so ADR 0001 and ADR 0002 are not edited. The drafts have no `## Context` or `## Decision` headings, and ADR 0001 in the target has them. S3 keeps the drafts as written, so they stay off-template. Whether the owner wants the template headings is **unverified**; the target's `_template.md` is not compared here.

- **ADR 0003** (`0003-facet-core-depends-on-kernel-identity.md`, source `06dee75`). Decision: `facet-core` may take one compile dependency, `kernel-identity`, and its `Principal` folds into it. Amends 0002. Its code (an Enforcer `<includes>` exception and an ArchUnit change) is not part of spec 11, because `facet-core` is frozen. The home is open (section 11, Q7). #13 decision 11 says the draft becomes a sequeless-query ADR, while #39 says commit it here.
  > **Superseded by section 11, Q7:** ADR 0003 lives in sequeless-query (#13 decision 11). S3 does not commit it here.
- **ADR 0004** (`0004-thin-core-jena-adapter-independent-versions.md`, source `af03f93`). Decision: `ontology-core` is thin (the `OntologyAdministration` api, the validation api and the `OntologyDocumentStore` spi). The Jena adapter is `ontology-jena-adapter`, Spring-free. `SchemaSource` lives in `schema-contract` and `BehaviourSource` in `behaviour-contract`. Each module versions and releases on its own. `ontology-testfixtures` publishes the contract kits. Amends 0001. **Added in S3:** `AutomationSource` lives in `automation-contract`. The draft omits it (spec 10 section 8; #20 comment, read as in section 0). The draft also says SHACL validation "is implemented in `ontology-jena-adapter`" and names `jena-shacl`. D17 defers that, and S3 does not edit it.
- **ADR 0005** (`0005-facet-core-moves-to-sequeless-query.md`, source `af03f93`). Decision: `facet-core` and `facet-core-testfixtures` move to `sequeless-query` (spec 16, #44). The zero-dependency rule moves with them. Supersedes 0002.

## 9. Slice plan

The decisions in section 11 (2026-11-09) change this table: S0b is added (Q9). SHACL moves in S2b, right after S2 (section 11, item 13). S3 no longer commits ADR 0003 (Q7) but commits the ADR 0001 supersession (N1). The S2 row is aligned with items 13 to 15. Other rows may still show superseded defaults; section 11 governs.

| Slice | Repo, PR | Does | Depends on | Gate |
|---|---|---|---|---|
| **S0 prototype prerequisite** | `sequeless-prototype`, one PR | P1 and P2 (section 3.1). 49 call sites in 8 files. The javadoc links that name changed signatures (section 2.1) | none. Can run alongside S1 | `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify` green. No assertion changed |
| **S0b sq-meta.ttl split** | `sequeless-prototype`, one PR, before S2 (merged as PR #94, 8c35bc2) | Section 11, Q9 (decided: split in the prototype first). Split `sq-meta.ttl` into `sq-schema.ttl` and `sq-behaviour.ttl`, and show that the loaded union is unchanged | S0 merged | `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify` green. The loaded union is unchanged |
| **S1 release-please migration** | `sequeless-ontology`, PR sequeless-ontology#3 (branch `chore/release-please-migration`, commits `2d6250a`, `0e6d8ec`, `6d36f67`), open | Section 6.2 | none | Build job green on the PR. Facet modules are not listed as packages. No Java package changed |
| **S1b CI, parent and Java level** | `sequeless-ontology`, one PR after S1. Q11 release level done in sequeless-ontology PR #4 (827c472). | Section 7 items 1 to 3, and the release level from Q11 | S1 merged. Q8 for the Sonar item. Q11 is decided and done (release level 25, PR #4) | Build job green on the PR on JDK 25. GitHub Packages resolution of `kernel-identity` works from the target's CI |
| **S2 import** | `sequeless-ontology`, one PR. One import commit titled per D27, the provenance section in its body, plus any formatting commit | Sections 2 to 5 and 10.1, without the SHACL files (section 11, item 13). Copies only. The prototype is not touched | S0, S1, S1b, the Q4 prototype PR (section 11, item 14), and the facet javadoc PR (item 15, merged as sequeless-ontology `d183bcd`). Section 11 Q1 to Q6 and Q9 to Q12 are decided | `./mvnw -B verify javadoc:javadoc` green on JDK 25 (spotless, Enforcer, ArchUnit, moved tests, `OntologyContract` for the Jena port and the in-memory port, `OntologyDocumentStoreContract` for the in-memory store). The owl grep in section 10.3 matches history files only |
| **S2b SHACL** | `sequeless-ontology`, one PR after S2. Not a copy: a new kernel-typed validation api in `ontology-core`, with its implementation in `ontology-jena-adapter` (section 11, item 13) | Q1 destination. The Command side keeps `ValidationPort` and adapts it in spec 12 | S2 | Its own gate: `./mvnw -B verify javadoc:javadoc` green on JDK 25, plus the SHACL contract tests from the prototype moved as copies. Design reviewed before merge |
| **S3 ADRs** | `sequeless-ontology`, PR separate from S2 | Section 8. 0004 and 0005 as drafted plus `AutomationSource`. 0003 as drafted | S2. 0003 is also blocked on section 11, Q7 | CI green. Each file matches its source, except the stated `AutomationSource` edit in 0004 |
| **S4 release 0.1.0** | `sequeless-ontology`, release-please release PR | Section 6.1: six modules at 0.1.0, tags, GitHub Packages | S2, S2b and S3 on `main` (item 16) | Each artifact resolves at 0.1.0 from GitHub Packages in a clean build. The release run is green. Must finish before S5a |
| **S5a BOM** | `sequeless-build`, PR `fix(bom): list the ontology modules at 0.1.0`, then the 0.1.3 release | Section 6.3, first half | S4 | `BomListsOnlyReleasesTest` green. Six entries at 0.1.0. Release published |
| **S5b bom-contracts** | `sequeless-build`, one PR | `OntologyBomContractTest`. The import moves to 0.1.3. Release level re-checked under Q11 | S5a. **Blocked on Q11** | `bom-contracts` CI green, running the ontology contract |
| **S6 prototype docs** | `sequeless-prototype`, docs only | This spec's status. The target's import-commit SHA for spec 12. The `remaining-work.md` line 102 tick (the root POM comment was already fixed by PR #88, so this slice does not edit the POM) | S2 to S5b | Prototype CI green on the PR. Import-commit SHA recorded for spec 12 |

> **S1 row:** PR sequeless-ontology#3 merged as 1f94b5c.

> **S1b row:** merged as sequeless-ontology #7 (`036c9e9`, rebase, admin merge recorded in sequeless-prototype #39).

> **S2 row:** S0b is merged. The spec 11 docs follow-up that annotates superseded defaults, and the sq-schema.ttl header fix, must land before S2 starts.

> **Section 11, item 13:** S2 is copy-only. The SHACL move is S2b.

What no slice does: delete a prototype file (spec 12), change a prototype dependency (spec 12), or touch `facet-core` or `facet-core-testfixtures`. The facet javadoc PR of section 11, item 15 is not a slice. It is a comment-only change in frozen code, merged before S2.

Merge method for S2 (section 11, Q6): the default is a merge commit, not a squash, so the import commit keeps its own SHA on `main`. A squash takes its title and body from the repository's squash settings (D27), so the PR title must equal the D27 title, and the PR body and the import commit body must carry the provenance section. Both precedents were squashed.

> **Superseded by section 11, Q6:** the merge method is rebase, because the sequeless-ontology main ruleset allows only rebase.

## 10. Test plan

### 10.1 Target tests

- Moved and passing: the tests in section 2 (18 spi tests, 11 Jena tests, and the contract runners). Two tests change, with six assertion sites, because they check `criteria()`: `DerivationMappingTest` (lines 105, 131, 158, 570) and `JenaOntologyPortAcceptanceTest` (lines 256, 269). They check the same facts in the `FilterNode` form. Every other moved test keeps its assertions.
- `OntologyContract` (ontology-testfixtures) passes for `JenaOntologyPort` (`JenaOntologyPortContractTest`) and for `InMemoryOntologyPort` (runner in `src/test`). This is the S2 contract gate.
- `OntologyDocumentStoreContract` passes for `InMemoryOntologyDocumentStore`. The Jena port does not implement a document store, so the contract does not run against it. The Jena port's use of a store is covered by the moved `JenaOntologyPortStorePersistenceTest`.
- The reference-domain parity assertions from spec 10 section 10.3 (`stateMachine`, `derivation` and `actions` equal the prototype's values at `aafb5a9`) move with `JenaOntologyPortAcceptanceTest`.
- New `RollupRuleMappingTest` (Jena module). One case per `Operator` (section 3.2 table), including `NOT_NULL` as `is not` with `NullNode`, never the alias. `IN` wraps one scalar. Zero criteria give `Optional.empty()`, one gives a `FieldFilter`, and two give an `AndFilter` in order. Each result passes `FilterValidator` (section 3.2). A date comparison gets whatever section 11, Q4, decides.
- New `BoundaryRulesTest` classes in `schema-contract`, `behaviour-contract` and `automation-contract` (section 5.4), and the rewritten rules in `ontology-core` and the Jena module.

### 10.2 Prototype tests

S0 changes the Jena tests' factory calls (section 3.1), with no assertion change. The prototype's `ArchitectureTest` keeps its 17 rules.

### 10.3 Verification commands

- Target, after S2: `git grep -nE 'org\.sequeless\.(spi|adapter|testkit)\.' -- '*.java'` returns nothing. The target never imports prototype packages.
- Target, after S2: `git grep -nE 'ontology[.-]owl|owl-adapter|ontology\.owl\b'` matches history files only. On the migration branch before S2, the hits are: the owl Java packages in `ontology-jena-adapter` (two `package-info` files and `BoundaryRulesTest`), plus `CHANGELOG.md` and ADR 0001 (history). No POM matches. After S3, ADR 0004 matches too: its draft (`af03f93`, line 11) names `ontology-owl-adapter`, and the committed ADR keeps that history sentence. Only the Java packages change in S2, so the grep then matches history files only.
- Prototype, S0 and S6: `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify`, in one shell command.

## 11. Decisions

Approved by the owner on 2026-11-09, from the navigator's scout session. Each decision was checked against the closed resolutions in #12, #13, #17, #20 and #23. Where a body section still states a default, the decision here governs, and the body is marked for a follow-up edit.

1. **Q1, SHACL placement (D17): decided by #13 decision 6.** SHACL validation moves with the import. The validation api lives in `ontology-core` over kernel types, and its implementation lives in `ontology-jena-adapter`, next to the loader. The Command side keeps `ValidationPort` and adapts it. This supersedes the default that keeps SHACL in the prototype until spec 12, and section 2, 3.3 and 5 need a follow-up edit before S2.
2. **Q2, Jena adapter layout (D21): no api package.** Public types are in `org.sequeless.ontology.jena` and the compiler is in `.internal`. The owl `api_and_spi_types_are_public` rule is dropped (section 5.4).
3. **Q3, shape of `criteria` (D23): `Optional<FilterNode>`.** Empty means no clause, one criterion is a `FieldFilter`, and two or more are an `AndFilter`.
4. **Q4, comparisons on date, date-time and non-text properties: reject at load.** An ERROR `OntologyIssue` is raised at load, in the style of the existing `sq:filter` errors. An ontology with such a rule fails activation in spec 12, where it activates today. This is a behaviour change, and the owner approved it. The spec's default was option (a).
5. **Q5, `FieldFilter.path`: the full property IRI, as an opaque key.**
6. **Q6, merge method for S2: rebase.** The sequeless-ontology `main` ruleset allows only rebase. The import commit's SHA on `main` is recorded in S6 for spec 12.
7. **Q7, ADR 0003 home: sequeless-query.** #13 decision 11 says the Principal-folding ADR becomes a sequeless-query ADR. S3 does not commit 0003 in sequeless-ontology.
8. **Q8, Sonar job: drop it.** The required SonarCloud Code Analysis check stays.
9. **Q9, `sq-meta.ttl` split: split in a prototype PR first (option c).** New slice S0b, before S2, splits `sq-meta.ttl` into `sq-schema.ttl` and `sq-behaviour.ttl` in the prototype, and shows that the loaded union is unchanged. S2 then moves both files. This follows #13 decision 8 and #23 rule 2. It supersedes the default that moves the file whole.
10. **Q10, automation-contract (D20): depends on behaviour-contract, as #20 decision 2 says.** This is a POM change. It supersedes the default of no dependency.
11. **Q11, release level: 25.** The owner's answer on #39, 2026-11-08 (option a). Done in sequeless-ontology PR #4, merged as `827c472`.
12. **Q12, kernel-values in the contract modules (D26): both depend on it, as #12 point 3 says.** `schema-contract` and `behaviour-contract` depend on `kernel-values`, though no code uses it yet. It supersedes the default of no dependency.

Items added after the spec's original list:

- **N1. ADR 0001 supersession: in S3, before the first release.** S3 commits ADRs 0004 and 0005 (plus `AutomationSource`), and a superseding ADR for 0001. The first release is S4, which follows S3.
- **N2. Core-only release: accept the bump.** A release of `ontology-core` also bumps `ontology-jena-adapter`'s POM version, and the adapter is released with core, as #17 decision 4 says. The core version is not moved into the root `dependencyManagement`.
- **N3. Frozen facet modules: accept the formatter reflow as formatting only.** palantir-java-format 2.102.0 reflows one switch expression in each of `FacetMerger.java` and `InMemoryFacetSource.java` (sequeless-ontology commit `0991d2a`). The tokens are unchanged. #23 defines "frozen" as excluded from release and deploy, not as byte-stable. Spotless is not excluded for these modules.

Decided, and not open: O1 (Option A, the owner's answer on #39 on 2026-11-08), recorded as D16. The rename's placement is in S1 (`0e6d8ec`).

**Owner answers, 2026-11-10** (asked through the driver after the S2 inventory found three conflicts; the navigator did not settle them):

13. **S2 is copy-only, and SHACL moves in S2b.** Q1 stands: SHACL moves to `sequeless-ontology`, and its destination is unchanged: the validation api in `ontology-core`, the implementation in `ontology-jena-adapter`. The PR boundary changes. The S2 import is a pure copy. The SHACL move is a follow-up slice S2b, right after S2, with its own kernel-typed validation api. Reason: `ShaclValidationPort` and `ShaclDataGraphBuilder` depend on `BusinessObject`, which stays in the prototype (#16 decision 9), so a copy cannot compile.
14. **Q4 lands in the prototype first.** The reject-at-load check is one behaviour PR in the prototype, before S2, so that the import copies code that already has the check (#23's prototype-first rule). Branch `feat/q4-reject-comparisons-at-load`. The PR number is recorded in `remaining-work.md` when it exists.
15. **Facet javadoc: fix the three comments in place.** `FacetPathCodec.java:22` and `FacetSource.java:53` and `:127` are comment-only fixes in frozen `facet-core`, as a separate PR before S2 (branch `docs/facet-javadoc-errors` in sequeless-ontology). After it merges, the S2 gate `./mvnw -B verify javadoc:javadoc` covers the whole reactor.
16. **S4 waits for S2b.** 0.1.0 ships the validation api in `ontology-core` (Q1 and ADR 0004), so S4 depends on S2b as well as S2 and S3. This is a driver decision of 2026-11-10 from the review of PR #99, not an owner answer. The owner may redirect it.
