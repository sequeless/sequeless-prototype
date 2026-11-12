# Draft 01: Sequeless core, SPI, app (explorer: Haiku). Root P=/Users/bbartolome/workspace/sequeless-prototype. Condensed by orchestrator; claims kept.

## 1 What it is
Keeps state of typed objects [GLOSSARY.md]. Types come from an Ontology (OWL + SHACL), stored as Turtle, read by Apache Jena [README.md; docs/architecture/01-overview.md]. Ontology defines properties, relationships, validation shapes, state machines, automation. Same five operations for every type: Browse, Read, Edit, Add, Delete; no per-type code [README.md]. Also answers queries and reacts to events with automation. README still says "phase 0"; code goes further.

## 3 Structure: ports and adapters
Hexagonal: core in the middle, talks out only via ports; adapters plug in [01-overview.md]. REST (sequeless-app) -> core (sequeless-core, use cases in org.sequeless.core.api / core.usecase) -> outbound ports in sequeless-spi <- adapters. Each port has one active adapter chosen by `sequeless.<port>.adapter` [README.md].
Ports -> adapters: ObjectStorePort -> PostgresObjectStore; ReadPort/BrowsePort/IndexPreparationPort/QueryPort -> PostgresQueryStore; OutboxPort -> PostgresOutboxPort; ValidationPort -> ShaclValidationPort (sequeless-adapter-ontology-jena); AuthorizationPort -> PermitAllAuthorizationPort; AutomationPort -> InProcessAutomationPort and TemporalAutomationPort. Ontology ports (SchemaSource, BehaviourSource, AutomationSource, OntologyAdministration) come from released libraries; JenaOntologyPort from released ontology-jena-adapter via JenaOntologyAutoConfiguration. ExpressionPort from released expression-api; expression-spring-boot-autoconfig pulls JEXL adapter [~/.m2 pom, 0.1.1].
- Startup: PortRegistry checks four slots (authorization, ontology, persistence, validation); fails on unset, unknown adapter, zero or several beans [sequeless-app/.../PortRegistryConfiguration.java, PortRegistry.java].
- Discovery: Spring Boot auto-config class plus ServiceLoader descriptor per adapter [META-INF/services/org.sequeless.spi.AdapterDescriptorProvider].
- sequeless-spi-testkit: abstract contract test per port; adapter extends it [ObjectStoreContract.java].
- ArchUnit rules [sequeless-app/src/test/.../ArchitectureTest.java]: SPI depends only on Java, kernel, ontology contracts; core only on Java, SPI, kernel, expression and filter APIs, Jackson, SLF4J, ontology contracts; neither uses Spring; core does not use Jena, JDBC, Temporal; no adapter depends on core.
- Core implements ActionExecutor, TriggerEvaluator, DerivationRecomputer callbacks.

## 4 Modules
Root pom: parent sequeless-build-parent 0.1.2, Spring Boot 4.1.1 BOM, sequeless-bom 0.1.3, Jena 6.1.0, Temporal 1.39.0, JEXL 3.6.2.
- sequeless-spi: ports, value records, adapter descriptors; deps kernel-identity, kernel-values, ontology-core, schema-contract.
- sequeless-core: use cases, structural validation, state machine, derivation planner, automation callbacks; deps kernel, spi, ontology-core, schema/behaviour/automation-contract, expression-api, filter-core, jackson-databind, slf4j.
- sequeless-spi-testkit: contract tests, fixtures; deps incl. ontology-testfixtures, JUnit, AssertJ.
- sequeless-app: Spring Boot app: REST, wiring, port check, outbox relay; deps core, five adapters, expression-spring-boot-autoconfig, Spring Web, Actuator, JDBC, springdoc.
Direction: app -> core -> SPI; adapters -> SPI, never core.
External libs: kernel-identity 0.1.0 (Scope, TenantId, Principal); kernel-values 0.1.0 (typed values, ObjectId, ObjectNotFoundException); ontology-core 0.1.0 (OntologyAdministration, OntologyReport, OntologyDocumentStore); schema-contract (Schema, TypeDefinition, PropertyDefinition, OntologyVersion, SchemaSource); behaviour-contract (Behaviour, StateMachineDefinition, Transition, TriggerKind, DerivationRule, BehaviourSource); automation-contract (Automation, AutomationSource); expression-api 0.1.1 (ExpressionPort, ExpressionContext, evaluates guards); filter-core 0.1.3 pinned by BOM (filter tree; FilterNodeToCriterion bridge); ontology-jena-adapter 0.1.0 (JenaOntologyPort).

## 5 Key types
BusinessObject (spi.object): id, type, tenant, version, state, properties, audit, deleted flag. ObjectView (spi.query): Read result. ObjectStorePort: finds objects, commits change sets. ChangeSet: one atomic commit of mutations + outbox entries. Mutation (Create/Update/Delete): glossary calls it Store Command. CommitResult. OutboxEntry: kind, payload, time. Query/Criterion/Operator/Sort. QueryResult/FacetBucket. ReadPort/BrowsePort/QueryPort. AuthorizationPort + Operation (BROWSE, READ, EDIT, ADD, DELETE, TRANSITION, ADMIN). ValidationPort/Violation. AutomationPort. Core: BrowseQuery, BusinessObjectService, TransitionService (fire, fireAutomated, signal), MetaModelService, SchemaAndBehaviour (two halves at one version), StateMachineInterpreter, StructuralValidator, ValueCoercer, DerivationPlanner.

## 6 Flows
### Add (command)
1 POST /objects/{type} -> BusinessObjectService.add. 2 Core reads Schema and Behaviour together, checks one ontology version; retries once, else fails. 3 Resolves type; checks ADD permission. 4 Coerces JSON to typed values. 5 Structural checks (required, cardinality, reference targets, abstract types). 6 SHACL check via ValidationPort, only after structural pass. 7 Builds one ChangeSet: one Create + one ObjectCreated outbox entry; calls ObjectStorePort.commit [DefaultBusinessObjectService.java 249-297]. 8 Store saves both all-or-nothing (port contract; Postgres class comment says one transaction; SQL not read).
- Edit: reads stored object; write names expected version (optimistic locking); StaleObjectException if differs; writes Update + ObjectUpdated. Delete: soft delete (deleted=true); ObjectDeleted holds old snapshot. Transition: TRANSITION permission, interpreter lists available transitions, guards via ExpressionPort; one ChangeSet = Update of state + TransitionFired + one ActionRequest per action + timer rows. Signal: SignalReceived entry only, no mutations.
### Browse
GET /objects/{type} with filter[prop][op]=value, q, sort, facets, page, size [query-port.md]. BROWSE permission; validates filters/sorts/facets, collects problems into InvalidQueryException; expands abstract type to concrete subtypes; BrowsePort.browse returns items, total, facet buckets; derived values computed per page with few aggregate calls; facet counts skip the facet's own filter.
### Read
GET /objects/{type}/{id}; READ permission; ReadPort.read; unrelated type reported as "not found"; derived values; returns ObjectView. Browse and Read never commit. Edit, Delete, transitions read via ObjectStorePort.find (need stored version).
### Automation
Every Store Command writes outbox rows in same commit. OutboxRelay runs every second by default; claims one row; on failure stops for this tick and retries. For object changes it first runs DerivationRecomputer, then AutomationPort. In-process adapter routes: ActionRequest (SetProperty, CreateObject, Log -> ActionExecutor; Webhook via HTTP with retries); ObjectCreated/Updated/Deleted -> TriggerEvaluator.onChange; SignalReceived -> onSignal; TimerScheduled/Cancelled -> timer scheduler; TransitionFired -> nothing. Evaluator never changes objects itself; calls TransitionService.fireAutomated, which no-ops if transition no longer valid. Temporal adapter starts durable workflows asynchronously [automation.md 193-196].

## 7 Glossary vs code
Store Command = `Mutation` in code (glossary says avoid "mutation"). No class named Command side. No Java types for Observable, Domain event, Event type, Integration flow, Template, Dialect, Signature; automation events are OutboxEntry kinds. "Source" in code = SchemaSource etc., differs from glossary sense. BusinessObject = stored form, ObjectView = Read result.

## 8 Docs vs code
README stale (phase 0, only /api/whoami; says only AuthorizationPort bound by property but four slots). 01-overview.md lists sequeless-adapter-expression-jexl module, absent now. spi/core package-info dependency statements understate allowed deps.

## Uncertain
Jar contents from local .m2 only (filter-core 0.1.3 and 0.1.4 both present). Jena adapter and expression autoconfig code not read. Postgres atomicity rests on comment. Query grammar from docs only. Temporal workflow classes, derived-property internals, DefaultActionExecutor, timer scheduler not read. Stale worktree .claude/worktrees/agent-a1b53b1e929eb3492 ignored. Not read: docs/plans, specs, extraction, sequeless-ui, OntologyStartupValidator, index preparation.
