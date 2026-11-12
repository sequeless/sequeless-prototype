# Draft 04: Ontology and the Sequeless repos (explorer: Haiku). Read 2026-11-12.
Note: sequeless-ontology was read from origin/main c9726a7 (local checkout is on branch fix/version, older). Paths: P=/Users/bbartolome/workspace/sequeless-prototype, O=/Users/bbartolome/workspace/sequeless-ontology (origin/main).

## 1 Sequeless
Platform for business objects; types come from data, not code; one generic set of operations for every type: Browse, Read, Edit, Add, Delete [P/README.md].

## 2 Ontology
- Ontology = authored source defining a tenant's types, rules, automation; written in OWL and SHACL [P/GLOSSARY.md].
- OWL describes classes, subclasses, properties, cardinality; stored as Turtle [P/docs/architecture/01-overview.md].
- SHACL holds validation rules (shapes) in same document [O/ontology-jena-adapter/.../ShaclValidationPort.java; P/sequeless-app/src/main/resources/ontology/reference.ttl].
- `sq:` vocabulary adds labels, facets, state machines, actions; namespace https://sequeless.dev/ns/meta# [P/docs/architecture/sq-vocabulary.md].
- Ontology version = one numbered activation [P/GLOSSARY.md].
- Example: ex:Task subclass of ex:WorkItem; at least one ex:title required [O/ontology-testfixtures/src/main/resources/ontology/reference.ttl].
- Reasoner derives implied facts. App uses owl reasoner and loads classpath:ontology/reference.ttl [P/sequeless-app/src/main/resources/application.yaml:14-19]. Levels: owl, rdfs, none; only owl detects inconsistency [ReasonerSetting.java].

## 3 Compile to Schema, Behaviour, Automation
Compile = turn OWL text into three plain Java records [O/schema-contract/.../Schema.java]. JenaOntologyPort steps [O/ontology-jena-adapter/.../JenaOntologyPort.java buildState]:
1 Parse into Jena model (OntModelBuilder). 2 Resolve imports; only bundled sq: vocabulary (ImportResolver). 3 Consistency check by reasoner (ConsistencyChecker). 4 Reject reserved sq: terms (SqVocabulary). 5 Map (SnapshotMapper): types, state machines, derivation rules, transition actions. 6 Stamp all three records with same Ontology version.
- Inconsistent reload/import throws; previous version stays active. Constructor exception: always adopts its build; read calls report problem later [class javadoc].
- Schema: types, properties, relationships, display hints, version. Behaviour: state machines keyed by type, derivation rules keyed by property [behaviour-contract/.../Behaviour.java]. Automation: actions run when a transition fires; triggers stay on Behaviour side [automation-contract/.../Automation.java].
- No Jena type crosses ports; only plain records [P/docs/architecture/02-decision-records.md DR-03].

## 4 Contracts
Interfaces and records; adapters must follow javadoc rules; test kit checks them. Each contract is its own Maven artifact with own version [O/docs/adr/0004-thin-core-jena-adapter-independent-versions.md].
- ontology-core: OntologyAdministration (reload, export, import), ShapeValidationPort, OntologyDocumentStore (versioned docs per tenant); depends on kernel-identity, kernel-values.
- schema-contract: Schema records, SchemaSource; depends only on kernel.
- behaviour-contract: state machines, transitions, derivation rules, triggers, BehaviourSource; depends on schema-contract, filter-core.
- automation-contract: Automation records, AutomationSource; depends on behaviour-contract, schema-contract.
- ontology-testfixtures: OntologyContract, OntologyDocumentStoreContract kits, in-memory fakes, sample .ttl.
- Three read ports return one half each; all halves of one activation share one version.
- Derivation rule: sealed RollupRule (aggregates related objects) or PluginRule (named Java code). Action kinds: SetPropertyAction, CreateObjectAction, WebhookAction, LogAction.

## 5 Jena adapter (ontology-jena-adapter)
Default implementation; uses jena-ontapi and jena-shacl [pom.xml; ADR 0004]. Public: JenaOntologyPort, ShaclValidationPort, ReasonerSetting, JenaOntologySettings; `internal` package not public. JenaOntologyPort implements three read ports plus OntologyAdministration. No Spring code. Build stays private until adopted; one volatile field holds state (reader sees all-old or all-new). With document store version comes from store; without, counter from 1. Bundles sq-schema.ttl and sq-behaviour.ttl. ShaclValidationPort checks object against shapes in live model. Limit: unresolvable owl:imports does not throw; detected after build.

## 6 Prototype use
- Consumes released artifacts; spec 12 removed prototype copies (PR #112, 3328993) [P/docs/extraction/remaining-work.md:153].
- sequeless-spi depends on ontology-core, schema-contract [spi/pom.xml:37,41]. sequeless-core depends on ontology-core and three contracts [core/pom.xml:41-53].
- sequeless-adapter-ontology-jena = Spring glue; JenaOntologyAutoConfiguration active when sequeless.ontology.adapter=jena. Keeps a copy of ValidationSchemaAdapter (released class package-private). Looks up derivation plugins on each build; late plugin appears on next reload.

## 7 Other repos
- sequeless-ontology: released library, six modules at 0.1.0, plus two frozen unreleased facet modules [O/pom.xml; spec-11:14; remaining-work.md:149].
- sequeless-build: Maven parent sequeless-build-parent 0.1.3, reusable GitHub workflows, Renovate preset, BOM.
- BOM sequeless-bom 0.1.4 on origin/main lists six ontology modules at 0.1.0; hosts import it, libraries do not.
- sequeless-build-expression-bom: clone of sequeless-build on branch bom-list-expression with commit 2ab3e78 "fix(bom): list the expression modules at 0.1.1" not on main; likely pending (unverified).
- sequeless-template: GitHub template for new repos; `make init name=<repo>`; parent POM, CI, release-please, BoundaryRulesTest; GLOSSARY placeholder.
- sequeless-platform: empty directory, skipped.
- sequeless: older monorepo; filter module moved out (0b07fab); different design (sync mirror); skipped as obsolete.

## Uncertain
Ontology read from origin/main, no fetch. sequeless-ontology README stale ("Scaffolded"). Prototype README stale (phase 0). Local prototype main 5a6c7fa behind origin/main 1d35e13. Prototype pins BOM 0.1.3 vs 0.1.4 on build main. ADRs 0004, 0006 only "proposed". Spec 11 body has superseded defaults; poms followed. 01-overview.md predates import. Expression-BOM commit intent unknown. Old monorepo usage unchecked. reference.ttl copies not diffed.
