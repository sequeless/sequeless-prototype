# Spec 10 — Prototype stage 3 prepare: ontology ports and the Schema, Behaviour and Automation split

Issue: [#38](https://github.com/sequeless/sequeless-prototype/issues/38). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#12](https://github.com/sequeless/sequeless-prototype/issues/12), [#13](https://github.com/sequeless/sequeless-prototype/issues/13), [#20](https://github.com/sequeless/sequeless-prototype/issues/20), [#24](https://github.com/sequeless/sequeless-prototype/issues/24). Tracker: `docs/extraction/remaining-work.md`.

## 0. Scope and decisions

Scope, from #38: the `OntologyPort` split (`SchemaSource`, `BehaviourSource`, `OntologyAdministration`); `MetaModelSnapshot` split into Schema, Behaviour and automation halves; `OntologyReport` leaves the Schema; the index-preparation call leaves `DefaultOntologyAdministration`. It is an in-process refactor. Behaviour stays the same except for the three changes listed in section 10.4.

Decisions taken here, each with its reason:

- **D1. The halves are packages in `sequeless-spi`, not new modules:** `org.sequeless.spi.meta.schema`, `.behaviour` and `.automation`. This is the smallest change ArchUnit can pin. Spec 11 maps each package to its contract module and renames it (#23: the extraction PR is "only a move, a package rename and a dependency swap").
- **D2. Each read port lives with its half, and `OntologyAdministration` lives in `spi.ontology` with `OntologyReport`.** This mirrors #13 decisions 3 and 5, so spec 11 moves each port with its contract.
- **D3. All three read ports are keyed by `TenantId` in this spec.** (tenant, Ontology version) keying (#20 decision 2) is left to spec 15. Section 3.1 gives the reasons.
- **D4. The automation half holds transition actions only.** `TriggerSpec` stays on Behaviour's `Transition`. Section 3.3 gives the reasons.
- **D5. Behaviour keys state machines by governed type IRI and derivations by property IRI.** That is how `SnapshotMapper` already builds them: `sq:appliesTo` per machine, `sq:derivedBy` per property. No information is lost.
- **D6. All three halves carry an `OntologyVersion`** (#13 decision 4). It comes from the document store, or from an in-memory counter when there is no store. No consumer caches by it yet.
- **D7. `OntologyAdministration.reload` returns `OntologyReport`. `importDocument` still returns `ImportReport`, which wraps it. The read ports throw on inconsistency** (#13 decision 5).
- **D8. Index preparation moves to host code in `sequeless-app`.** The host wraps the admin port and also calls it at startup. `ensureIndexes` keeps its name until spec 13 (#24: "`QueryPort` itself is unchanged"). Its parameter type becomes `Schema`, because `MetaModelSnapshot` no longer exists.
- **D9. Core reads Schema and Behaviour through one helper that checks they have the same version.** The split turns one read into two, and the helper keeps a request from mixing two activations.
- **D10. The spi port takes the name #13 gives it, `OntologyAdministration`. Core's use-case interface `org.sequeless.core.api.OntologyAdministration` keeps its name.** The two files that need both write the spi one fully qualified. See section 11, question 2.
- **D11. `JenaOntologyPort`, `SnapshotMapper` and `MetaModelService` keep their names.** Renaming them belongs to spec 11's import, or later.
- **D12. `AggregateFunction` goes to the Schema package.** That keeps the halves acyclic while `Criterion` stays in `spi.query` (prefactor seam 2, superseded by #16 decision 5).
- **D13. `MetaModelService.describeType` returns a new core record, `TypeDescription`.** The REST type detail then keeps exactly one READ authorization.
- **D14. `SpiVersion` stays 0.1.0.** Every adapter is built in this reactor, the same as in specs 7 and 9.

Out of scope, and which spec takes it:

- The Spring split of the Jena adapter (#13 decision 9): spec 11's import leaves the Spring classes behind.
- `OntologyDocumentStore` moving to `ontology-core`: specs 11 and 12.
- The kernel-typed SHACL api: spec 12.
- `ensureIndexes` becoming `prepare(tenant, version)`: spec 13.
- Trigger details moving to automation: specs 13 and 15.
- Caching by version: spec 14 and later.

## 1. Identity, stage and dependencies

- Stage 3, prepare. Spec 10 of 19. No new repo and no release.
- Waits for spec 9 ([#37](https://github.com/sequeless/sequeless-prototype/issues/37)). Spec 9 is done (`remaining-work.md`, Done).
- Unblocks spec 11 ([#39](https://github.com/sequeless/sequeless-prototype/issues/39)). Spec 11 imports `spi.meta` (by then three packages), the Jena adapter and the `sq:` vocabulary into sequeless-ontology.
- Branch `spec-10-prototype-stage3-prepare`, cut from `origin/main` at `aafb5a9`. Every implementation slice (section 9) commits to this branch. One PR merges it.
- Status: slices S1 to S7 are committed on the branch, and the PR is pending. No PR number yet.

## 2. Moves

All moves are in-repo. Provenance:

- **Source repo:** `sequeless/sequeless-prototype`.
- **Source SHA:** `aafb5a959e946d6f9f132a4faeb958c1a26662d6` (`origin/main` when the branch was cut). The last commit that changes the moved paths is `d53d3f1` (spec 7 cut-over).
- **Source paths:** `sequeless-spi/src/main/java/org/sequeless/spi/meta/` (28 files), `sequeless-spi/src/main/java/org/sequeless/spi/ontology/OntologyPort.java`, and their tests under `sequeless-spi/src/test/java/org/sequeless/spi/{meta,ontology}/`.
- **The move:** `git mv` per file, in slice S1's commit, so that `git log --follow` traces each file. Package lines and imports change in the same commit.
- **Date:** the author date of S1's commit.

| From `org.sequeless.spi.meta` | To | Files |
|---|---|---|
| `TypeDefinition`, `PropertyDefinition`, `AttributeDefinition`, `RelationshipDefinition`, `Cardinality`, `Datatype`, `DisplayHints`, `AggregateFunction`; `MetaModelSnapshot` → `Schema` | `spi.meta.schema` | 9 moved; new: `OntologyVersion`, `SchemaSource`, `package-info` |
| `StateMachineDefinition`, `State`, `Transition`, `TriggerSpec`, `TriggerKind`, `UserActionTrigger`, `OnChangeTrigger`, `TimerTrigger`, `ExternalSignalTrigger`, `DerivationRule`, `RollupRule`, `PluginRule` | `spi.meta.behaviour` | 12 moved; new: `Behaviour`, `BehaviourSource`, `package-info` |
| `Action`, `SetPropertyAction`, `CreateObjectAction`, `WebhookAction`, `LogAction`, `PropertyAssignment` | `spi.meta.automation` | 6 moved; new: `Automation`, `AutomationSource`, `TransitionRef`, `package-info` |
| `package-info.java` | deleted | 1 |

- `spi/ontology/OntologyPort.java` → `spi/ontology/OntologyAdministration.java`.
- Tests: the six schema-type tests in `spi/meta/` (`TypeDefinitionTest`, `AttributeDefinitionTest`, `RelationshipDefinitionTest`, `CardinalityTest`, `DatatypeTest`, `DisplayHintsTest`) move to `spi/meta/schema/`. `MetaModelSnapshotTest` → `spi/meta/schema/SchemaTest`. `OntologyPortTest` → `OntologyAdministrationTest`.

**Known limitations**

- **Rename tracking.** `git log --follow` does not trace `SchemaTest` from `MetaModelSnapshotTest`, or `OntologyAdministrationTest` from `OntologyPortTest`. Each was rewritten by more than half, so git records a delete and an add. This is recorded here and in the PR body. The history was not rewritten.
- **Bisecting.** Commit `2020711` (app), the first two spi commits (`d4e31d7`, `ed7a16f`) and the first two testkit commits (`44fe03c`, `aa27d4f`) do not compile their tests on their own. The branch compiles from the first buildable commit of each slice onward.

## 3. Prerequisite refactors

### 3.1 The halves and the read ports (`sequeless-spi`)

```java
// org.sequeless.spi.meta.schema
record OntologyVersion(int value)                                  // value >= 1
record Schema(OntologyVersion ontologyVersion, String ontologyIri, Optional<String> versionIri,
              Map<String, String> prefixes, List<TypeDefinition> types)
    // keeps type(iri), typeByName(shortName), names() and the unique-short-name check
record TypeDefinition(String iri, String label, List<String> superTypes,
                      List<PropertyDefinition> properties, DisplayHints displayHints, boolean isAbstract)
interface SchemaSource { Schema schema(TenantId tenant); }

// org.sequeless.spi.meta.behaviour
record Transition(String name, String fromStateIri, String toStateIri, TriggerSpec trigger,
                  Optional<String> guard, Optional<String> guardMessage)
record Behaviour(OntologyVersion ontologyVersion,
                 Map<String, StateMachineDefinition> stateMachines,   // key: governed type IRI
                 Map<String, DerivationRule> derivations)             // key: property IRI
    Optional<StateMachineDefinition> stateMachine(String typeIri)
    Optional<DerivationRule> derivation(String propertyIri)
interface BehaviourSource { Behaviour behaviour(TenantId tenant); }
```

What is removed: `TypeDefinition.stateMachine`, `Transition.actions`, the `derivation` component of `AttributeDefinition` and `RelationshipDefinition`, `PropertyDefinition.derivation()` and `MetaModelSnapshot.report`. Each one moves to the half named above. Every other component, check and accessor stays as it is.

Read-port contract, written as the javadoc of each of the three ports:

- A `null` tenant throws `NullPointerException`.
- Reads are deterministic and have no side effects between activations.
- One call returns the half of exactly one Ontology version.
- The port throws an unchecked exception when no consistent Ontology is active. The adapter throws `OntologyException`. Each read port's javadoc names it as `{@code org.sequeless.spi.ontology.OntologyException}`, not as a `{@link}`, so that the half's package does not depend on `spi.ontology` once spec 11 makes it a module.

**Why D3 (`TenantId` now).** A read keyed by version must be able to serve a version that is no longer active. Neither the Jena adapter (it holds one live state) nor `OntologyDocumentStore` (it only reads the active document) can do that without new behaviour. Also, no caller holds a version to pass until spec 13 stamps the Ontology version on Store events. `behaviour(TenantId, OntologyVersion)` and `automation(TenantId, OntologyVersion)` arrive with their first caller, automation-core in spec 15. They can come as default methods that guard the version, which #12 decision 6 counts as a minor change.

### 3.2 `OntologyAdministration` (`spi.ontology`)

```java
interface OntologyAdministration {
    OntologyReport reload(Scope scope);                       // was MetaModelSnapshot
    OntologyDocument export(Scope scope, OntologyFormat format);
    ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode);
}
```

Its javadoc keeps the `OntologyPort` clauses: null handling, reload rebuilds from source, export/import round-trip, inconsistency always thrown. It changes two of them and adds one:

- **Round-trip:** "equal halves" now means equal in everything except `ontologyVersion`.
- **Failure:** a failed reload or import leaves the previous halves, with their version, as what the read ports return.
- **Versions (new):** a successful import gives a strictly greater version. A successful reload gives a version that is not lower. All three halves of one activation share one version.

`ImportReport`, `OntologyReport`, `OntologyIssue`, `Severity` and `OntologyException` do not change.

### 3.3 The automation half (`spi.meta.automation`)

```java
record TransitionRef(String stateMachineIri, String fromStateIri, String transitionName)
record Automation(OntologyVersion ontologyVersion, Map<TransitionRef, List<Action>> transitionActions)
    List<Action> actions(TransitionRef transition)       // List.of() when the transition has none
interface AutomationSource { Automation automation(TenantId tenant); }
```

- **Content:** the `sq:action` lists that sit on `Transition` today. Transitions without actions have no entry.
- **Key:** `TransitionRef`. `DefaultTransitionService` fires the first transition whose name and from-state both match (`DefaultTransitionService.java` lines 133 and 216). So (machine, from-state, name) is what identifies a firing today. When two transitions share a key, the compiler keeps the first one in `StateMachineDefinition.transitions()` order. Today the second can never fire, so its actions never run, and nothing changes.

**Why D4 (actions only).**

- #20 decision 3 says the Command side must stop knowing the shape of actions. Today only one site in core reads them, the ActionRequest builder in `DefaultTransitionService.commitTransition`, so actions can move with one consumer change.
- #20 decision 2 puts "which transitions are timer- or signal-triggered" in Behaviour.
- Core still reads trigger details in four places: user-action gating, signal acceptance by name, timer scheduling and on-change evaluation. Specs 13 and 15 move or redesign each of them (timers go to automation, signals become Store Commands, the evaluator goes to automation-core). Splitting `TriggerSpec` now would make all four read two halves, only for those specs to redo the work.
- Observables, Event types and Integration flows have no code in the prototype.

So spec 10 delivers the part of automation-contract that exists. Spec 15 adds the rest and decides where each trigger detail lives. Section 11, question 4 asks the owner to confirm.

### 3.4 Other spi signatures

- `QueryPort`: `query(Scope, Schema, Query)`, `ensureIndexes(Scope, Schema)` and `aggregate(Scope, Schema, AggregateRequest)`. Its methods and names do not change (#24). The javadoc says the host calls `ensureIndexes` after every activation.
- `ValidationPort.validate(Scope, Schema, BusinessObject)`.
- `DerivationContext(Scope scope, Schema schema, QueryPort queryPort)`. The component `snapshot` becomes `schema`.
- `AggregateRequest` changes its import only.
- Javadoc references to removed or moved types are updated in `spi/ontology/package-info.java`, `spi/automation/package-info.java`, `AutomationPort.java`, `spi/query/package-info.java`, `spi/validation/package-info.java` and `spi/derivation/package-info.java`.

### 3.5 Jena adapter (`sequeless-adapter-ontology-jena`)

- `JenaOntologyPort implements SchemaSource, BehaviourSource, AutomationSource, OntologyAdministration`.
  - `PortState` holds the model, the merged `OntologyReport` and the three halves, and is swapped atomically as today.
  - `requireConsistent` still checks the report.
  - `importDocument` builds `ImportReport` from the report and `schema.types().size()`.
- **Version.** The halves are stamped when they are adopted.
  - With a store: the version `activate` returns on import, or `active().version()` at startup and on reload.
  - Without a store: an `AtomicInteger` that starts at 1 and is incremented by each successful reload or import.
- `SnapshotMapper.map` returns `MappingResult(types, stateMachines, derivations, transitionActions, issues)`.
  - Types and properties no longer carry Behaviour.
  - Actions are keyed by `TransitionRef` with the first-wins rule in section 3.3.
  - Issues are unchanged.
- `unknownPluginIssues` still walks `types` and their properties, and looks up each derivation by IRI. That keeps the order of the issues.
- `ShaclValidationPort.validate(Scope, Schema, BusinessObject)` and `ShaclDataGraphBuilder.build(Schema, BusinessObject)`.
- `JenaOntologyAutoConfiguration`: `@Bean public JenaOntologyPort ontologyPort(...)`. The return type is the concrete class, so that one bean serves all four interfaces and `PortRegistry` can see it without eager init. The bean name stays `ontologyPort`.
- `ShaclValidationAutoConfiguration.validationPort(OntologyAdministration)`. Its `instanceof JenaOntologyPort` check stays.
- `JenaOntologyAdapterDescriptorProvider` names `OntologyAdministration.class`.
- The javadoc in `SqVocabulary` (`sq:action`, `sq:appliesTo`, `sq:derivedBy`) points to the new homes.

### 3.6 Core (`sequeless-core`)

Constructors. The new ports replace `OntologyPort` in its position, and the other parameters keep their order:

- `DefaultMetaModelService(SchemaSource, BehaviourSource, OntologyAdministration, AuthorizationPort)`
- `DefaultOntologyAdministration(OntologyAdministration, AuthorizationPort)`: the `QueryPort` parameter is gone.
- `DefaultBusinessObjectService(SchemaSource, BehaviourSource, ObjectStorePort, ValidationPort, AuthorizationPort, QueryPort, Clock, DerivationPlanner)`
- `DefaultTransitionService(SchemaSource, BehaviourSource, AutomationSource, ObjectStorePort, AuthorizationPort, ExpressionPort, Clock)`
- `DefaultTriggerEvaluator(SchemaSource, BehaviourSource, ObjectStorePort, TransitionService)`
- `DefaultDerivationRecomputer(SchemaSource, BehaviourSource, ObjectStorePort, DerivationPlanner, int, Duration, Clock)`
- `StateMachineInterpreter.availableTransitions(Scope, Behaviour, BusinessObject)`. It now looks up the machine by `object.type().iri()`.
- `DefaultBusinessObjectService` keeps its 7-argument convenience constructor, which leaves out `DerivationPlanner`. Core tests use it. It is not in the list above, and it is retained.

`MetaModelService`:

| Method | Returns | Authorizes |
|---|---|---|
| `schema(Scope)` | `Schema` | BROWSE on EVERYTHING (was `snapshot`) |
| `behaviour(Scope)` | `Behaviour` | BROWSE on EVERYTHING (new) |
| `describeType(Scope, String)` | `TypeDescription` | READ on the resolved type |
| `reload(Scope)` | `OntologyReport` | ADMIN on EVERYTHING |

- `describeType` resolves first and authorizes second, as today.
- `reload` goes through the admin port it was built with (section 3.7).
- New `org.sequeless.core.api.TypeDescription(TypeDefinition type, Optional<StateMachineDefinition> stateMachine, Map<String, DerivationRule> derivations)`. The map holds the derived properties in `type.properties()`, inherited ones included, keyed by property IRI.

Reading the halves (D9):

- New `org.sequeless.core.ontology.SchemaAndBehaviour(Schema schema, Behaviour behaviour)`, with `static SchemaAndBehaviour read(TenantId, SchemaSource, BehaviourSource)`.
  - It reads both halves. If their versions differ, it reads both again once.
  - A second mismatch throws `IllegalStateException`.
- Every core class that needs both halves uses it.
- In `commitTransition`, `DefaultTransitionService` also reads `Automation`. If its version differs from the pair's version, it throws `IllegalStateException` before anything is committed.

Mechanical rules for the rest of core:

- `type.stateMachine()` → `behaviour.stateMachine(type.iri())`.
- `property.derivation()` → `behaviour.derivation(property.iri())`.
- `transition.actions()` → `automation.actions(new TransitionRef(machine.iri(), transition.fromStateIri(), transition.name()))`.
- Code that depends on order keeps iterating `Schema.types()` and properties as today, and looks the halves up by IRI. Nothing iterates the new maps.
- `DerivationPlanner`, `StructuralValidator`, `ValueCoercer` and `TypeHierarchy` take whichever halves they read.
- Javadoc-only references in `core/api` change too.

### 3.7 Host: index preparation leaves the use case (#24)

- `DefaultOntologyAdministration.importTurtle` loses lines 67 and 68 (`ontologyPort.snapshot(scope)`, then `queryPort.ensureIndexes(scope, snapshot)`) and the `QueryPort` field.
- **New `org.sequeless.app.config.IndexPreparingOntologyAdministration implements OntologyAdministration` (the spi port).**
  - It takes the delegate, a `SchemaSource` and a `QueryPort`.
  - After the delegate's `importDocument` or `reload` returns normally, it calls `queryPort.ensureIndexes(scope, schemaSource.schema(scope.tenantId()))`. Then it returns the delegate's result.
  - `export` only delegates.
  - It is not a Spring bean, because a second `OntologyAdministration` bean would break `PortRegistry`'s one-bean check. `CoreConfiguration` builds it around the Jena bean and passes it to `DefaultOntologyAdministration` and `DefaultMetaModelService`.
- **Startup:** `OntologyStartupValidator` takes `@Lazy SchemaSource` and `@Lazy QueryPort`. It calls `schemaSource.schema(TenantId.DEFAULT)` (this throws on an inconsistent Ontology, as `snapshot` did), then `ensureIndexes(scope, schema)`.
- This gives the three host call sites #24 names: import (moved), reload (new) and startup seed load (new). Spec 13 swaps all three to `prepare(tenant, schema.ontologyVersion())`.
- **Why a port decorator rather than calls in the controllers:** import and reload get one call site in host code, the controllers do not change, and the use cases never see `QueryPort`.

### 3.8 App (`sequeless-app`)

- `PortRegistryConfiguration`: `new PortDefinition("sequeless.ontology.adapter", OntologyAdministration.class)`.
- `CoreConfiguration`: the constructors from section 3.6, plus the decorator from section 3.7.
- `TypesController`:
  - `listTypes` reads `schema(scope)`.
  - `describeType` passes the `TypeDescription` to `TypeResponseMapper.toDetail`.
  - `reload` returns `OntologyReportResponse.from(metaModelService.reload(scope))`.
- `TypeResponseMapper`: `toSummaries(Schema)` and `toDetail(TypeDescription)`.
- `ObjectsController`:
  - Its five `describeType` calls use `.type()`.
  - `availableTransitions` and `userActionTransitionNames` read `behaviour(scope)`. That is one BROWSE, as today.
- Javadoc-only references in `FacetResponseMapper`, `ObjectPropertyMapper`, `ApiExceptionAdvice` and `TomcatEncodedSlashConfiguration` change too.
- Every REST response body and status stays the same.

## 4. Package renames

| From | To (interim; spec 11 renames to sequeless-ontology packages) |
|---|---|
| `org.sequeless.spi.meta` (schema types) | `org.sequeless.spi.meta.schema` |
| `org.sequeless.spi.meta` (state-machine, trigger and derivation types) | `org.sequeless.spi.meta.behaviour` |
| `org.sequeless.spi.meta` (action types) | `org.sequeless.spi.meta.automation` |
| `org.sequeless.spi.meta.MetaModelSnapshot` | `org.sequeless.spi.meta.schema.Schema` (with `Behaviour` and `Automation`) |
| `org.sequeless.spi.ontology.OntologyPort` | `org.sequeless.spi.ontology.OntologyAdministration`, plus the three read ports above |

Grep at `aafb5a9`:

- 88 files import `org.sequeless.spi.meta.*`.
- 65 reference `OntologyPort` and 67 reference `MetaModelSnapshot`, including tests and `sequeless-spi-testkit`.
- No file outside `sequeless-spi`, `sequeless-spi-testkit`, `sequeless-core`, `sequeless-adapter-ontology-jena`, `sequeless-adapter-persistence-postgres` and `sequeless-app` refers to them. The automation adapters, `sequeless-adapter-authz-permitall` and `sequeless-ui` do not change.

## 5. Modules and their dependencies

- **No module is added or removed, and no POM changes.** No new dependency is added. The module graph stays as it is.
- **Package graph inside `sequeless-spi`:**
  - `meta.schema` → kernel.
  - `meta.behaviour` → `meta.schema` and kernel, and `spi.query` for `RollupRule` → `Criterion` (section 11, question 1).
  - `meta.automation` → `meta.schema` and kernel. It may also depend on `meta.behaviour`, but needs nothing from it today.
  - `spi.query`, `spi.validation` and `spi.derivation` depend on no half except `meta.schema`.
  - `spi.ontology` → none of the halves.
- **New `ArchitectureTest` rules** (`sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java`):
  - `schemaDependsOnlyOnJdkKernelAndItself`: classes in `..spi.meta.schema..` only depend on `java..`, `..spi.meta.schema..`, `org.sequeless.kernel..` and primitives or arrays.
  - `behaviourDependsOnlyOnSchema`: classes in `..spi.meta.behaviour..` only depend on `java..`, the behaviour and schema packages, the kernel, and `org.sequeless.spi.query..`. The `because` text names `Criterion` and spec 14.
  - `automationDependsOnlyOnBehaviourAndSchema`: classes in `..spi.meta.automation..` only depend on `java..`, the automation, behaviour and schema packages, and the kernel.
  - `querySeesOnlySchema`: no class in `org.sequeless.spi.query..` depends on `..spi.meta.behaviour..`, `..spi.meta.automation..` or `org.sequeless.spi.ontology..` (#13 decision 3).
  - `onlyHostPreparesIndexes`: no class outside `org.sequeless.app.config..` or `org.sequeless.testkit.query..` calls `QueryPort.ensureIndexes(Scope, Schema)` (#24). The testkit's query contract kit exercises the port and is test-scoped, so it is not a production caller. The rule matches calls through the owner type (assignable to `QueryPort`), so calls on a concrete store type are caught too.
  - The existing 12 rules stay. `suiteActuallyAnalyzesEveryModule` still finds `JenaOntologyPort` (D11).

## 6. Release config (first version, tags, BOM change)

N/A. This spec releases nothing and does not change the BOM. The root POM keeps importing `sequeless-bom` 0.1.2, and `SpiVersion` stays 0.1.0 (D14).

## 7. CI and workflows

- `.github/` does not change. `ci.yml` stays at `workflows-v0.1.0` and `codeql.yml` at `workflows-v0.1.1`.
- **Local gate:** `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify`, in one shell command. `./mvnw` does not exist. The `release` profile runs javadoc, so a dangling `{@link}` to a removed type fails the build.

### Definition of done

Quoted from #38, with this spec's reading:

- "The new repo's CI is green." N/A: this spec creates no repo.
- "The prototype's CI is green against *released* artifacts." Open. No dependency changes, so the PR's CI run is the proof.
- "The BOM's CI … is green." Unaffected: the BOM does not change. Confirm that `sequeless-build` CI on `main` is green when the PR merges.
- "The release and the BOM re-release are done." N/A: an in-process spec has no release.
- "For cut-over specs …" N/A: this is a prepare spec.

## 8. ADRs

- **No new ADR.** The decisions are recorded in #13, #20 and #24, and in section 0. The package layout is interim (D1), and the two deferrals (D3, D4) are reversible in specs 15 and 13.
- This repo has no `docs/adr/` directory.
- #13 amends sequeless-ontology's ADR 0001: a thin core, three ports and a version per module. That amendment is the draft ADR 0004 on branch `wayfinder/ontology-move-adrs` (`af03f93`), and spec 11 commits it.
  - The draft names `SchemaSource` and `BehaviourSource` but not `AutomationSource`, which #20 added later. Spec 11 must add it when it commits the ADR.
- The index-preparation ADR belongs to spec 14, in sequeless-query (#24).

## 9. Slice plan

- S1 lands first, alone. S2 to S6 own disjoint files and can then be written in parallel against the APIs in section 3. S7 closes.
- The reactor does not compile between S1 and the last of S2 to S6, so every slice commits to this branch, never to `main`.
- A slice that needs another slice's API changed asks that slice's owner. It never edits that slice's files.

| Slice | Owns (only these files) | Does | Depends on | Slice check |
|---|---|---|---|---|
| **S1 spi** | `sequeless-spi/**` | Sections 2, 3.1 to 3.4; the spi tests in section 10.1 | none | `mvn -B -Prelease -pl sequeless-spi verify` |
| **S2 testkit** | `sequeless-spi-testkit/**` | Section 10.2: `OntologyContract`, `OntologyPorts`, `QueryFixtures.schema()`, `ValidationContract.Fixture(ValidationPort port, Schema schema)`, `QueryContract`, `AggregateContract`, `WorkloadDerivationPlugin`, package-infos, the in-memory test ports | S1 | `-pl sequeless-spi-testkit -am` |
| **S3 jena** | `sequeless-adapter-ontology-jena/**` | Section 3.5 and its tests | S1; its tests need S2 | `-pl sequeless-adapter-ontology-jena -am` |
| **S4 postgres** | `sequeless-adapter-persistence-postgres/**` | `PostgresQueryStore` takes `Schema`; the ITs call `QueryFixtures.schema()` | S1; its ITs need S2 | `-pl sequeless-adapter-persistence-postgres -am` |
| **S5 core** | `sequeless-core/**` | Section 3.6, `DefaultOntologyAdministration` from section 3.7, and the core tests | S1 | `-pl sequeless-core -am` |
| **S6 app** | `sequeless-app/src/**` | Sections 3.7 (host) and 3.8, the `ArchitectureTest` rules, the app tests | S1, S5 (section 3.6 signatures), S3 (bean type) | full gate |
| **S7 docs** | `README.md` (line 183), `docs/architecture/01-overview.md` (port catalogue and sketches), `docs/agents/design-preferences.md` (line 32), `docs/extraction/remaining-work.md`, this file's status | Doc updates; runs the full gate and the grep in section 10.5 | S2 to S6 | full gate |

What each slice must not do:

- No slice touches a POM, `.github/`, `GLOSSARY.md` or `sequeless-ui/`, except the S7 description change below.
- S7 may change the `<description>` of `sequeless-adapter-ontology-jena/pom.xml` (description text only; no dependency, version or build change). It may also change the one javadoc sentence in `OntologyAdministration.java` (javadoc only).
- S3 and S4 do not edit the testkit to make their tests compile.
- S5 does not touch `sequeless-app`.
- S6 does not change a core signature.

Every slice check runs as `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease <-pl …> verify`.

## 10. Test plan

### 10.1 `sequeless-spi` (S1)

- `SchemaTest` comes from `MetaModelSnapshotTest`. It drops the report assertions and adds a version-required check.
- New `BehaviourTest`, `AutomationTest` and `OntologyVersionTest`:
  - lookups by IRI;
  - `actions()` returns empty when there is no entry;
  - null checks and defensive copies;
  - the version is at least 1;
  - `TransitionRef` has no blank parts.
- `TypeDefinitionTest`, `AttributeDefinitionTest` and `RelationshipDefinitionTest` drop the removed components.
- `OntologyAdministrationTest` replaces `OntologyPortTest`, with the same purpose: the interface can be implemented as written.

### 10.2 Contracts (`sequeless-spi-testkit`, S2): these must still pass

- **`OntologyContract`.** `portFor(OntologyDocument)` returns `OntologyPorts(SchemaSource schemaSource, BehaviourSource behaviourSource, AutomationSource automationSource, OntologyAdministration administration)`, a testkit record with `static <T extends SchemaSource & BehaviourSource & AutomationSource & OntologyAdministration> OntologyPorts of(T port)`. Its clauses:
  - every argument of the four interfaces rejects `null`;
  - repeated reads return equal halves, and `export` does not change them;
  - a failed import leaves the halves and their version as they were;
  - reload on an unchanged source returns a consistent report, the halves keep their content, and the version is not lower;
  - export then import keeps the content of all three halves and gives a greater version;
  - an inconsistent Ontology makes each read port throw `OntologyException` with an ERROR issue;
  - after any activation, the three halves share one version.
- **Runs:** `InMemoryOntologyPortContractTest` (its fake implements the four ports and the version counter) and `JenaOntologyPortContractTest`.
- **Unchanged clauses, new types:** `QueryContract` (Postgres IT), `AggregateContract` (in-memory and Postgres IT) and `ValidationContract` (`ShaclValidationPortContractTest`).
- **No change:** `ObjectStoreContract`, `AuthorizationContract` and `AutomationContract`.

### 10.3 Existing tests that change

- **Jena (S3):** `JenaOntologyPortTest`, `JenaOntologyPortAcceptanceTest`, `JenaOntologyPortStorePersistenceTest`, `SnapshotMapperTest`, `DerivationMappingTest`, `UnknownPluginActivationTest`, `ShaclDataGraphBuilderTest` and `JenaOntologyAdapterDescriptorProviderTest`. New assertions:
  - the version comes from the store when there is one, and from the counter when there is not;
  - first-wins `TransitionRef`;
  - for the reference ontology, `stateMachine(type)`, `derivation(property)` and `actions(ref)` equal what `TypeDefinition.stateMachine()`, `PropertyDefinition.derivation()` and `Transition.actions()` returned at `aafb5a9`.
- **Postgres (S4):** `PostgresQueryStoreContractIT`, `PostgresQueryStoreAggregateContractIT` and `PostgresQueryStoreIndexUsageIT`.
- **Core (S5):**
  - The seven private `FakeOntologyPort`s become fakes or lambdas of the read ports.
  - `DefaultOntologyAdministrationTest` loses its `QueryPort` double and the two `ensureIndexes` assertions (lines 169 and 180 to 181). They move to S6.
  - New `SchemaAndBehaviourTest`: a mismatch is read again once, then `IllegalStateException`.
  - `DefaultTransitionServiceTest` reads actions from `Automation` and adds a test for an Automation version mismatch.
- **App (S6):**
  - New `IndexPreparingOntologyAdministrationTest`: import and reload each call `ensureIndexes` once, with the scope and the current Schema; export does not call it; a delegate that throws means no call.
  - `QueryEndToEndTest` drops `triggerEnsureIndexes()` and the javadoc about the gap, so the class now proves the startup call.
  - `CoreConfigurationContextRunnerTest`, `PortRegistryContextRunnerTest` and `PortBindingStartupFailureTest` change port names and stubs.
  - `OntologyEndToEndTest`, `OntologyPersistenceAcrossRestartIT`, `InconsistentOntologyStartupTest`, `UnknownDerivationPluginStartupTest` and `PostgresTestcontainersSupport` change types.
  - `OntologyPersistenceAcrossRestartIT` and `UnknownDerivationPluginStartupTest`, listed above, reference no moved type. `TypesEndToEndTest` and `ApiExceptionAdviceTest` reference none either. All four compile and pass unchanged, so they were not edited.
- Answers to section 11 questions 2 to 4 are recorded there. Question 1 stays open.

### 10.4 Behaviour that may change (only these)

1. `ensureIndexes` also runs after `POST /types/reload` and at startup (#24).
2. `PortBindingException` messages name `OntologyAdministration` instead of `OntologyPort`.
3. During a concurrent activation, a core request may fail with `IllegalStateException` (HTTP 500) instead of mixing two versions (D9).

Everything else is identical, including the REST bodies and the order of the issues in an `OntologyReport`.

### 10.5 Verification

- `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify`: BUILD SUCCESS, with `ArchitectureTest` at 17/17.
- `git grep -nE '(^|[^A-Za-z])OntologyPort([^A-Za-z]|$)|MetaModelSnapshot|spi\.meta\.[A-Z]' -- '*.java'` returns nothing. It matches neither `JenaOntologyPort` nor the test class names built on it.

## 11. Open questions

1. **`RollupRule` → `spi.query.Criterion` blocks spec 11.** `behaviour-contract` cannot leave the prototype while `RollupRule` imports a prototype `spi.query` type. Seam 2 was superseded by #16 decision 5 (`FilterNode` replaces `Criterion`), and that lands in spec 14, after 11. Decide on #39 before spec 11 starts. The options are:
   - (a) spec 11's import changes `RollupRule.criteria` to filter-core's `FilterNode`;
   - (b) spec 10 moves `Criterion` and `Operator` into `spi.meta.schema`;
   - (c) spec 11 imports `Criterion` into `schema-contract`.
2. **Two `OntologyAdministration` interfaces (D10).** Rename core's use case (for example to `OntologyAdministrationService`, 7 files) now, or at spec 12 when the spi port comes from sequeless-ontology?
3. **Where (tenant, Ontology version) keying lands (D3).** In spec 11, before `behaviour-contract` 0.1.0 is published, or in spec 15 with its first caller? This spec recommends spec 15, as a default method.
4. **Is actions-only the right automation half (D4)?** The alternative is to also move timer durations and on-change watch lists into it now, and leave only the kind and signal name on `Transition`.

**Answers recorded.** The orchestrator accepted the defaults for questions 2 to 4. Question 1 stays open.

- **Question 1: open.** Decision for the owner on [#39](https://github.com/sequeless/sequeless-prototype/issues/39), before spec 11 starts.
- **Question 2:** the core use case keeps the name `OntologyAdministration`. The rename waits for spec 12.
- **Question 3:** (tenant, Ontology version) keying lands in spec 15, as default methods.
- **Question 4:** actions-only automation is accepted.
