# Extraction: pre-factor plan

**Superseded on 2026-11-05.** The seams below were proposed as behaviour-preserving package moves inside the prototype. The decided specs now cover them, so no pre-factor code PRs will be opened, and spec 7 runs first. The parallelism claim was wrong: specs 14 and 15 both wait for spec 13 (#41), and spec 11 waits for spec 10 (#38). Each seam below points to the decision that settles it. The original analysis is kept for history.

Status: superseded, 2026-11-05 (see above). Each claim was checked against `main` at `52fa2a5`. Paths are relative to `sequeless-spi/src/main/java/org/sequeless/spi/` unless they start with a module name. The spec issues are the source of truth; where a seam overlaps a spec's scope, the spec issue decides the design. See [remaining-work.md](remaining-work.md) for the route.

## Why pre-factor

The `spi` module is one Maven module whose packages form cycles. The later extractions move whole packages into separate repos (`sequeless-ontology`, `sequeless-query`, `sequeless-automation`). A cycle between two packages that are bound for different repos blocks both of them. A package that holds types for two target repos forces those two specs to run one after the other. Breaking the cycles now, in small PRs, keeps those knots out of the extraction specs. It also lets each later spec move a package without editing the others.

## Seams

The seams are ordered by how many specs each one unblocks. "Verified" means the import lines were checked at `52fa2a5`. "Inferred" means the classification or consequence is a judgement.

### 1. Break the `meta` ↔ `ontology` cycle (specs 10, 11, 12)

**Superseded by #13 decision 5 and spec 10 (#38).** `OntologyReport` leaves the schema-contract, so no `spi.snapshot` package is created.

- **Coupling (verified):** `ontology/OntologyPort.java:4` imports `meta.MetaModelSnapshot`. `meta/MetaModelSnapshot.java:8` imports `ontology.OntologyReport`. No other imports cross between `ontology` and `meta` in either direction.
- **Proposal:** move `MetaModelSnapshot` out of `meta` into its own package, `spi.snapshot`. Move `OntologyReport` with it, because the snapshot carries the report. The result is acyclic: `ontology → snapshot → meta`.
- **Overlap:** this is part of spec 10's scope, "ontology and snapshot splits". Pull it forward only if the spec 10 issue agrees with the target package.
- **Size:** medium. Snapshot importers span every module.

### 2. Break the `meta` ↔ `query` cycle (specs 14, 15)

**Superseded by #16 decision 5.** `FilterNode` replaces `Criterion`, so `Criterion` is not moved. #16 decision 2 moves aggregates off the Query side.

- **Coupling (verified):**
  - `meta/RollupRule.java:6` imports `query.Criterion`.
  - `query/QueryPort.java:4` imports `meta.AggregateFunction`, and `query/QueryPort.java:5` imports `meta.MetaModelSnapshot`.
  - `query/AggregateRequest.java:7` imports `meta.AggregateFunction`.
- **Proposal:** move `Criterion` and its hierarchy into a package below both `meta` and `query`, for example `spi.criteria`. Then `meta → criteria` and `query → criteria, meta`. Before choosing the package, check `Criterion`'s own imports. Also check whether `filter-core` (sequeless-filter, spec 6) should own these types. Spec 14 waits for 6, which suggests it should (inferred).
- **Ordering:** after seam 1, `QueryPort` depends on `snapshot`, not on `meta`.
- **Size:** medium.

### 3. Move ontology documents out of `object` (specs 10, 11)

**Superseded by #13 decision 7.** `OntologyDocumentStore` moves into ontology-core in spec 11/12. No pre-factor PR. The `object → ontology` edge stays, which is the allowed direction.

- **Coupling (verified):** `object/OntologyDocumentStore.java:5` and `object/StoredOntologyDocument.java:6` import `ontology.OntologyDocument`. This makes `object → ontology`. Meanwhile `meta/PropertyAssignment.java:5` and `meta/SetPropertyAction.java:5` import `object.Value`, so the chain `object → ontology → (snapshot) → meta → object` is a cycle.
- **Proposal:** move `OntologyDocumentStore` and `StoredOntologyDocument` into `ontology`. `ObjectStorePort` uses `OntologyDocumentStore` from the same package without an import, so it needs an import added. The decision is whether `ObjectStorePort` should keep extending or exposing the ontology store. If it should not, give the store its own port.
- **Blast radius (verified):**
  - `OntologyDocumentStore` has 15 importers, in Postgres, Jena, the testkit, core tests and app tests.
  - `StoredOntologyDocument` has 7 importers.
- **Note:** `object.Value` moves to `kernel-values` in spec 7, which ends the `meta → object` leg for `Value`.
- **Size:** small to medium.

### 4. Give the outbox its own package (specs 13, 15)

**Superseded by #41 (spec 13).** Spec 13 rewrites `OutboxEntry` and picks its package. No pre-factor PR.

- **Coupling (verified):**
  - `object/OutboxEntry.java` and `object/OutboxPort.java` live in `object`.
  - Four `automation` files import `object.OutboxEntry` at line 4: `ActionExecutor`, `TriggerEvaluator`, `DerivationRecomputer` and `AutomationPort`.
  - Across all modules, 49 files import `OutboxEntry` or `OutboxPort`.
- **Proposal:** create a new package, `spi.outbox`, and move both types into it. Spec 13, "outbox, automation ingress, Read port", and spec 15, `sequeless-automation`, can then move or depend on the outbox without pulling in `object`.
- **Stale javadoc (verified):** `object/OutboxPort.java:28-29` still asks "(display name? roles? …)". `Principal` lost `displayName` in spec 4, so drop the display-name question in the same PR.
- **Size:** medium. The change is mechanical, mostly import rewrites.

### 5. Split `meta` into schema and behaviour (specs 10, 15). Inferred.

**Superseded by #20 and spec 10.** The behaviour-contract split is spec 10 work. The cross-references listed below are why it cannot be a pre-factor move.

- **Contents (verified):** `meta` has 28 files.
- **Classification (inferred):**
  - **Schema, 9 files:** `TypeDefinition`, `PropertyDefinition`, `AttributeDefinition`, `RelationshipDefinition`, `Cardinality`, `Datatype`, `DisplayHints`, `AggregateFunction`, `MetaModelSnapshot`.
  - **Behaviour, 18 files:**
    - Actions: `Action`, `CreateObjectAction`, `SetPropertyAction`, `PropertyAssignment`, `LogAction`, `WebhookAction`.
    - Triggers: `TriggerSpec`, `TriggerKind`, `OnChangeTrigger`, `TimerTrigger`, `UserActionTrigger`, `ExternalSignalTrigger`.
    - Derivation: `DerivationRule`, `RollupRule`, `PluginRule`.
    - State machines: `StateMachineDefinition`, `State`, `Transition`.
- **Why:** the ontology (spec 11) needs the schema half. Automation (spec 15) needs the behaviour half.
- **Do after seams 1 and 2.** `MetaModelSnapshot` leaves in seam 1, and `AggregateFunction` is shared with `query`.
- **Size:** medium. Decide it with spec 10.

### 6. Expression is already clean (spec 8). Verified, no work.

`spi/expression` has one non-JDK import, `object.Value`, at `expression/ExpressionPort.java:3` and `expression/ExpressionContext.java:6`. Spec 7 rewrites it to `kernel-values`, and after that spec 8 can extract the package as it is.

## Decision needed before spec 14 (settled)

Settled: `BusinessObject` stays out of the query side (#16 decision 9). `ValidationPort` stays Command-side (#13 decision 6). `Page` is query-owned (owner decision, 2026-11-05). The analysis below is kept for history; its options are no longer open.

`BusinessObject` (`object/BusinessObject.java:25`) and `Page` (`object/Page.java:9`) stay in the prototype under spec 5. Four files import them:

- `query/QueryResult.java:6`
- `query/Query.java:7`
- `validation/ValidationPort.java:6`
- `derivation/DerivationPlugin.java:5`

If `query` moves to `sequeless-query` as it is now, that repo would depend back on the prototype (inferred). There are three options:

- Move `BusinessObject` and `Page` into the kernel, or into a shared module.
- Make `Query` and `QueryResult` generic over the row type.
- Keep a query-owned row type.

Record the decision in the spec 14 issue before spec 14 starts.

## Sequencing and conflicts (superseded: spec 7 runs first)

- **Spec 7 runs alone.** Spec 5 §4 counts 125 importers of the six moving kernel types at `a2e402a`. Spec 7 also owns these changes:
  - `sequeless-app/src/test/java/org/sequeless/app/ArchitectureTest.java:50` (`spiDependsOnlyOnJdkAndItself`), which fails once `spi` imports the kernel.
  - The `spi/package-info.java` javadoc.
- **Order:**
  1. Land seams 4 and 3 before the spec 7 window. Neither touches the kernel types much.
  2. Land seams 1 and 2 before or after spec 7, as the spec 10 and 14 issues agree.
  3. Seam 5 follows 1 and 2.
- **Shared hot files:**
  - The root `pom.xml:23-34` module list. Serialize any PR that adds a module.
  - `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/Fixtures.java`, referenced by 18 test files.- **Worktrees:** four `research/*` worktrees under `.claude/worktrees/`, plus `sequeless-prototype-ui` and `test/ui-kit`, are checked out. Check them for `pom.xml` and `ArchitectureTest` changes before spec 7.

## PR breakdown (superseded: no pre-factor PRs)

Each seam ships as one PR, whose title is a `refactor(spi)` conventional commit. Each PR must meet these conditions:

- No behaviour change. Package moves and import rewrites only.
- `source ~/.bashrc.ext && mvn spotless:apply && mvn -B -Prelease verify` passes. (`./mvnw` does not exist in this repo.)
- `ArchitectureTest` still passes. If the PR adds a package, add a rule that pins the new direction of dependency, for example `ontology` must not depend on `object`.
- `remaining-work.md` is updated in the same PR.
