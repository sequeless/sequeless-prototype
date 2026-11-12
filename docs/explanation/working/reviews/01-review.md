# Review 01: drafts/01-core.md (core, SPI, app)

Reviewer check date: 2026-11-12. Repo: /Users/bbartolome/workspace/sequeless-prototype at main 5a6c7fa (clean). Read-only review.

## VERDICT: REVISE

The draft is mostly accurate: 24 of 28 spot checks passed. But it has 1 wrong claim, 3 misleading claims and some important gaps. The most important gap is that automation is off in the default configuration. The draft is also not yet in STE: it uses arrows, undefined jargon, missing articles and words with more than one meaning. A newcomer needs those fixed.

## 1. Accuracy: spot checks (28)

| # | Claim | Result | Evidence |
|---|-------|--------|----------|
| 1 | Root pom: build-parent 0.1.2, Spring Boot 4.1.1, sequeless-bom 0.1.3, Jena 6.1.0, Temporal 1.39.0, JEXL 3.6.2 | OK | pom.xml lines 7-8, 42-47, 63-64 |
| 2 | filter-core 0.1.3 pinned by BOM; expression-api 0.1.1; kernel 0.1.0 | OK | ~/.m2/.../sequeless-bom/0.1.3/*.pom lines 34-66 |
| 3 | DefaultBusinessObjectService.add at lines 249-297 builds one Create plus one ObjectCreated entry and calls commit | OK | sequeless-core/.../usecase/DefaultBusinessObjectService.java 249-297 |
| 4 | Add order: type, ADD permission, coerce, structural check, then SHACL only after structural pass | OK | same file 257-283 |
| 5 | Schema and Behaviour read together; retry once, else fail | OK | sequeless-core/.../ontology/SchemaAndBehaviour.java (`read`, throws IllegalStateException) |
| 6 | PortRegistry checks four slots: authz, ontology, persistence, validation | OK | sequeless-app/.../config/PortRegistryConfiguration.java 35-38 |
| 7 | PortRegistry fails on unset, unknown adapter, zero or several beans | INCOMPLETE | PortRegistry.java `validate`: also fails when the adapter's SPI version range is not compatible (`PortBindingException.incompatibleVersion`) |
| 8 | Discovery: "ServiceLoader descriptor per adapter" | WRONG (partly) | Only 3 modules have `META-INF/services/org.sequeless.spi.AdapterDescriptorProvider`: authz-permitall, ontology-jena (2 providers: Jena ontology and SHACL), persistence-postgres. The two automation adapters have none; they are selected only by `@ConditionalOnProperty(sequeless.automation.adapter)` and PortRegistry does not check them. |
| 9 | "Each port has one active adapter chosen by `sequeless.<port>.adapter`" | MISLEADING | Only 4 slots are checked. The property for authorization is `sequeless.authz.adapter` (not `authorization`). `sequeless.query.adapter` and `sequeless.outbox.adapter` default to `postgres` (`matchIfMissing = true`, PostgresPersistenceAutoConfiguration.java 112, 137). Expression uses `sequeless.expression.adapter: jexl` (application.yaml). |
| 10 | ArchUnit: SPI only on Java, kernel, ontology contracts | OK | ArchitectureTest.java rule `spiDependsOnlyOnJdkAndItself` |
| 11 | ArchUnit: core only on Java, SPI, kernel, expression, filter API, Jackson databind, SLF4J, ontology contracts | OK | rule `coreDependsOnlyOnJdkSpiAndItself` |
| 12 | ArchUnit: no Spring in SPI/core; no Jena/JDBC/Temporal in core; no adapter depends on core | OK, but INCOMPLETE | Draft omits 7 other rules: only app uses Spring MVC; only app.rest and app.config use core; adapters do not depend on each other; no Lombok in SPI/core; only the Temporal adapter uses io.temporal; the query package does not see Behaviour, Automation or ontology admin; only app.config calls `IndexPreparationPort#prepare`. Also the guard test `suiteActuallyAnalyzesEveryModule`. |
| 13 | Ports to adapters list (PostgresObjectStore, PostgresQueryStore for Read/Browse/IndexPreparation/Query, PostgresOutboxPort, ShaclValidationPort, PermitAll, InProcess, Temporal) | OK, INCOMPLETE | PostgresQueryStore.java 66; PostgresObjectStore.java 53 also implements `SearchVectorBackfillPort`; PostgresOntologyDocumentStore implements `OntologyDocumentStore`. SPI also has `derivation.DerivationPlugin` (ServiceLoader plugin). |
| 14 | JenaOntologyPort from released ontology-jena-adapter via JenaOntologyAutoConfiguration | OK | JenaOntologyAutoConfiguration is in local module sequeless-adapter-ontology-jena (META-INF/spring imports); say so. |
| 15 | Module deps (spi, core, testkit, app; app has five adapters) | OK | each module pom.xml |
| 16 | Operation enum: BROWSE, READ, EDIT, ADD, DELETE, TRANSITION, ADMIN | OK | sequeless-spi/.../authz/Operation.java |
| 17 | Edit reads with ObjectStorePort.find; expected version; StaleObjectException | OK | DefaultBusinessObjectService.java ~315, ~363 |
| 18 | Delete names expected version | MISLEADING (by omission) | ObjectsController.delete: `If-Match` is optional; with no header it deletes the current version. Edit needs a body version or `If-Match`, else HTTP 428. |
| 19 | REST paths `POST /objects/{type}` etc. | INCOMPLETE | All controllers are served under prefix `/api` (application.yaml `sequeless.api.base-path: /api`; docs/architecture/query-port.md 98). Full path is `/api/objects/{type}`. |
| 20 | Browse: expands abstract type to concrete subtypes, InvalidQueryException, BrowsePort.browse, derivation per page | OK | DefaultBusinessObjectService.java 203-226 |
| 21 | Facet counts skip the facet's own filter | OK | BrowsePort.java javadoc lines 38-43 ("conventional multi-select") |
| 22 | Read: unrelated type reported as not found | OK | DefaultBusinessObjectService.java 241, 659 (`requireMatchingType`) |
| 23 | Signal: SignalReceived entry only, no mutations | OK, INCOMPLETE | DefaultTransitionService.java 298-300. Signal also checks TRANSITION permission (line 276). |
| 24 | OutboxRelay: every 1 s, "claims one row", stops on failure | MISLEADING | OutboxRelay.pollOnce: `fixedDelay` 1000 ms default (`sequeless.automation.relay.poll-interval-ms`). In each tick it claims rows one at a time in a loop until the queue is empty or a dispatch fails. Also: it does nothing if no AutomationPort bean exists. |
| 25 | Relay runs DerivationRecomputer before AutomationPort for object-lifecycle rows | OK | OutboxRelay.java (OBJECT_LIFECYCLE_KINDS branch) |
| 26 | In-process routing (SetProperty, CreateObject, Log, Webhook with retries; onChange; onSignal; timers; TransitionFired no-op) | OK | InProcessAutomationPort.java 105-132 |
| 27 | fireAutomated returns empty when transition no longer valid | OK | core/api/TransitionService.java 65-87 |
| 28 | Temporal adapter starts workflows asynchronously [automation.md 193-196] | OK | automation.md 193-196; TemporalAutomationPort.java 91 `WorkflowClient.start` |

Other checks that passed: README still says "phase 0" and `/api/whoami` (README.md 7-9); 01-overview.md line 79 lists `sequeless-adapter-expression-jexl`, which is not a module now; spi package-info says SPI imports only JDK and kernel, which understates the ArchUnit rule; jar contents of kernel-values, kernel-identity and ontology-core match the draft; testkit has ObjectStoreContract and 6 other contracts.

### Uncertain items the reviewer resolved
- "Postgres atomicity rests on comment": NOT only a comment. `PostgresObjectStore.commit` runs inside `TransactionTemplate.execute` (PostgresObjectStore.java 68, 119). Remove this item from "Uncertain".
- "Two OntologyAdministration": the draft names `OntologyAdministration` only as a released port. There is also `org.sequeless.core.api.OntologyAdministration`, a core use case (export/import Turtle, ADMIN permission) that delegates to the released port. A newcomer will confuse the two. Name both.

## 2. Wrong or unsupported claims (summary)
1. Discovery uses ServiceLoader "per adapter": wrong for both automation adapters (no descriptor; not in PortRegistry).
2. "Each port ... chosen by `sequeless.<port>.adapter`": overstated; four checked slots, `authz` spelling, query/outbox default to postgres.
3. OutboxRelay "claims one row": it drains the queue one row at a time per tick.
4. REST paths lack the `/api` prefix.
5. Edit and Delete versioning: Delete version is optional; Edit without version returns 428.
6. "Postgres atomicity rests on comment" is now verified in code.

## 3. Missing important topics
1. Automation is OFF by default. `sequeless-app/src/main/resources/application.yaml` sets no `sequeless.automation.adapter`, so OutboxRelay returns at once and no outbox row is dispatched. A newcomer must set `inprocess` or `temporal` to see automation.
2. How to run: Postgres from docker-compose.yml, `mvn -pl sequeless-app spring-boot:run`, Maven needs the GitHub Packages token for released libraries.
3. Other REST endpoints: `/api/types`, `/api/types/{nameOrIri}`, `POST /api/types/reload`, `GET/POST /api/ontology` (Turtle), `GET .../transitions`, `POST .../transitions/{name}`, `POST .../signals/{name}`, `/api/whoami`. ETag and If-Match use.
4. Index preparation after ontology activation is a host job (IndexPreparingOntologyAdministration, ArchUnit rule `onlyHostPreparesIndexes`).
5. `RequiresNewCommitObjectStore` (app.config): automation commits run in a new transaction inside the relay's claim.
6. Poison-pill limit: a row that always fails blocks all rows behind it (OutboxRelay javadoc). No dead-letter.
7. SPA serving: the app serves the Angular UI (sequeless-ui) from the same port (SpaWebConfiguration).
8. Extra SPI types: `SearchVectorBackfillPort`, `DerivationPlugin`, `AggregateRequest/AggregateResult`, `ReadRequest`, `Page/PageResult`.
9. Section numbering skips section 2 (1, 3, 4 ...). Fix the outline.

## 4. STE (ASD-STE100) review

Terse fragments are acceptable after condensing. These systematic problems remain:

1. Arrows and symbols replace verbs ("REST -> core -> SPI <- adapters", "ObjectStorePort -> PostgresObjectStore"). STE needs full sentences with a clear verb, or a table with column headings.
2. Technical nouns used before definition or not defined: port, adapter, hexagonal, outbox, ChangeSet, SHACL, OWL, Turtle, BOM, facet, soft delete, optimistic locking, durable workflow, slot, tick, callback. Define each one time at first use, then use it the same way.
3. Words with more than one meaning:
   - "Read" is a REST operation, a port (ReadPort) and a common verb. Use "Read (the operation)" as a defined term, and use "get" or "load" for the common verb.
   - "state" means object state and state-machine state. Use "state" only for the state machine; use "stored data" for the other sense.
   - "Source" has a code sense (SchemaSource) and a glossary sense. The draft notes this but then uses "source" loosely.
   - "check" means validate, authorize and test. Use "validate", "authorize", "test".
4. Synonyms for one concept: Store Command / Mutation / write / commit / change set; permission / authorization; slot / port / binding; "fails" / "throws" / "rejects". Pick one term per concept and give a mapping table one time.
5. Unapproved or informal verbs and idioms: "plug in", "pulls", "no-ops", "understate", "routes", "claims", "drains", "skip", "stale", "wiring". Replace with approved or defined words ("connect", "adds", "does nothing", "gives less than", "sends", "locks and takes", "ignores", "not current", "configuration").
6. Passive voice: "stored as Turtle, read by Apache Jena", "pinned by BOM", "chosen by", "computed per page". Use active voice: "Apache Jena reads the Turtle files."
7. Articles dropped almost everywhere ("Core reads Schema", "Builds one ChangeSet"). STE keeps "the", "a", "an".
8. Noun clusters: "object-lifecycle outbox row", "port contract", "derivation planner", "index preparation". Break them up or define them as named terms.
9. -ing nouns: "optimistic locking", "Discovery", "routing". Use "The store uses a version number to stop lost updates."
10. Long run-on list sentences (section 4 "External libs" is one 70+ word line). Use a table: Library | Version | Main types.
11. No instructions are given. When run steps are added (topic 2 above), write them as imperative steps, max 25 words each.

### Five concrete rewrites

1. Original: "Keeps state of typed objects [GLOSSARY.md]. Types come from an Ontology (OWL + SHACL), stored as Turtle, read by Apache Jena."
   STE: "Sequeless stores typed objects. An Ontology defines each type. The Ontology is a set of Turtle files in OWL and SHACL. Apache Jena reads these files."

2. Original: "Hexagonal: core in the middle, talks out only via ports; adapters plug in."
   STE: "Sequeless uses a hexagonal design. The core is in the center. The core uses ports to send requests out. An adapter is the code that does the work for one port."

3. Original: "Edit: reads stored object; write names expected version (optimistic locking); StaleObjectException if differs."
   STE: "The Edit operation loads the stored object. The request must give the expected version. If the stored version is different, the core stops the edit with StaleObjectException."

4. Original: "OutboxRelay runs every second by default; claims one row; on failure stops for this tick and retries."
   STE: "By default, OutboxRelay starts one time each second. It takes the outbox rows one at a time. If a row fails, it stops. It tries again at the next start."

5. Original: "Evaluator never changes objects itself; calls TransitionService.fireAutomated, which no-ops if transition no longer valid."
   STE: "The trigger evaluator does not change objects. It calls TransitionService.fireAutomated. If the transition is not available now, fireAutomated does nothing."

## 5. Required changes before APPROVE
1. Fix the 6 items in section 2.
2. Add at minimum missing topics 1, 2, 3 and 6.
3. Name both OntologyAdministration types.
4. Add a short term list (port, adapter, outbox, ChangeSet, Store Command, facet, SHACL) and use each term with one meaning.
5. Replace arrows with sentences or tables; restore articles; use active voice.
