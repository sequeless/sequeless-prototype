# Draft 02: adapters and UI (explorer: Haiku). Condensed by orchestrator; claims kept. P=/Users/bbartolome/workspace/sequeless-prototype. Paths abbreviated: AD = sequeless-adapter-.

## 1-2 Picking adapters
- Core holds rules; adapter = real technology behind a port (Java interface in SPI). One active adapter per port [sequeless-spi .../ObjectStorePort.java].
- sequeless-app/src/main/resources/application.yaml sets: sequeless.authz.adapter=permit-all; ontology.adapter=jena; persistence.adapter=postgres; validation.adapter=shacl; automation.adapter NOT set.
- Each adapter has an auto-config class; beans created only when property matches. Adapter may publish a descriptor (name, port, property, SPI version range) found by ServiceLoader. PortRegistry checks four slots (authz, ontology, persistence, validation); fails on unset, unknown name, incompatible version, zero or several beans. It does NOT check automation, query, outbox slots. CoreConfiguration wires use cases to port beans.

## 3 Postgres (AD persistence-postgres)
Default ObjectStorePort and QueryPort adapter: PostgreSQL 18 JSONB, Flyway, transactional outbox [pom.xml].
- PostgresObjectStore: ObjectStorePort + SearchVectorBackfillPort. PostgresQueryStore: ReadPort, BrowsePort, IndexPreparationPort, QueryPort. PostgresOutboxPort: OutboxPort. PostgresOntologyDocumentStore: OntologyDocumentStore (not in SPI; reached via ObjectStorePort.ontologyDocuments()). Only ObjectStorePort in descriptor.
- Tech: Spring JdbcClient, HikariCP, Flyway; compose uses PG 18.
- Table sq_object: id, tenant_id, type_iri, version, state, props (JSONB), created/updated audit, deleted_at [V1__objects_ontology_outbox.sql]. Properties keyed by full IRI; values tagged: text, integer, decimal, bool, dateTime, date, ref, list [ValueJsonCodec.java]. Every read/write filters tenant_id. Soft delete sets deleted_at + new version; reads skip deleted. Optimistic locking: write names expected version; StaleObjectException on mismatch; row locked SELECT ... FOR UPDATE. A commit is one DB transaction: check versions, write changes and outbox rows; any failure saves nothing.
- Outbox table sq_outbox, rows in same commit as change. Claim takes one unprocessed row FOR UPDATE SKIP LOCKED; handler runs in same transaction; throw => rollback, row stays unprocessed. Partial index covers eight claimable kinds [V5].
- sq_ontology_document stores versions; each activation becomes the only active one (partial unique index); per-tenant advisory lock.
- Browse: COUNT then page query; filters, sorts, paging, facets become SQL over props. `sq:indexed` properties get expression index sq_idx_+16 hex of MD5, recorded in sq_index_registry. `sq:searchable` registered in sq_searchable_property. Full-text: search_vector tsvector set by trigger on insert/update ('simple' config) [V6]; GIN index [postgres-query/V2]. New searchable property triggers backfill of tenant's search_vector. Aggregates (count,sum,min,max,avg) use GROUP BY for rollups.
- Two migration sets: command (db/migration/sequeless/postgres) and query (.../postgres-query, history table flyway_schema_history_query). Adapter runs its own Flyway, on first real connection (PostgresMigratingDataSource).
- Config: persistence.url/username/password (checked-in: localhost:5432, db/user sequeless). Own DataSource bean sequelessPersistenceDataSource. Query and outbox beans default to postgres when their properties unset; object store bean has no default.

## 4 Jena and SHACL (AD ontology-jena)
- ShaclValidationPort implements ValidationPort (sequeless.validation.adapter=shacl). Ontology bean is JenaOntologyPort from external library implementing external OntologyAdministration (not an SPI port); core wraps it in its own OntologyAdministration. Two descriptors: jena, shacl.
- Real Jena and SHACL code is in external artifacts ontology-jena-adapter, ontology-core. Checked-in ontology classpath:ontology/reference.ttl. REST speaks Turtle. Reasoner default owl; only owl catches the inconsistency test case.
- With a Postgres store, loads active ontology document from it; if none active, seeds store from source file. Without a store reads source file.
- Validation: converts object to values keyed by property IRI; passes object and ontology shapes to SHACL engine; each violation becomes Violation (property IRI + message). ValidationSchemaAdapter answers: type exists, supertypes, XSD datatype of property (copies rules from external class).
- GET /api/ontology returns Turtle; POST imports; inconsistent import returns 422 with report [OntologyController, ApiExceptionAdvice].
- Config: ontology.adapter=jena, ontology.reasoner=owl, ontology.source. Validation bean fails at startup if ontology bean is not a Jena port.

## 5 Temporal (AD automation-temporal)
- TemporalAutomationPort implements AutomationPort. Temporal = durable workflow engine (survives restarts, retries). Temporal Java SDK without Spring starter (Spring Boot clash); client and worker built by hand. Compose: temporalio/temporal:1.9.1 dev mode.
- dispatch by outbox kind: ActionRequest -> ActionWorkflow (SetProperty, CreateObject, Log, Webhook); ObjectCreated/Updated/Deleted -> ChangeEventWorkflow (recompute derived values, then onChange); TimerScheduled -> TimerWorkflow keyed by timer key (waits; if no cancel signal, onTimerElapsed); TimerCancelled -> cancel signal; SignalReceived -> SignalEventWorkflow (onSignal); TransitionFired nothing; other kinds throw.
- Idempotency: workflow id = outbox id (or timer key); duplicate id refused and treated as success; reuse policy REJECT_DUPLICATE (also blocks rerun after finish).
- Activities call core beans ActionExecutor, TriggerEvaluator, DerivationRecomputer (injected). Webhook: JDK HTTP client; non-2xx or network error throws; Temporal retries. Retry defaults: 1 s first wait, multiplier 2.0, 5 attempts, 30 s per attempt.
- Worker starts at boot; if server unreachable, logs warning, app continues, no worker polls until restart.
- OutboxRelay polls every 1000 ms by default.
- Config: sequeless.automation.adapter=temporal; automation.temporal.target default 127.0.0.1:7233; task-queue default sequeless-actions; retry.*. Checked-in application.yaml sets none. No descriptor, so PortRegistry skips it.

## 6 In-process (AD automation-inprocess)
- InProcessAutomationPort implements AutomationPort; for tests and local dev; not durable. Runs actions at once on relay thread; routing mirrors Temporal. Dispatched ids in in-memory set (unbounded, lost on restart). Timers in memory; tick every 100 ms default. Webhook: 3 attempts, fixed 200 ms gap. Config: adapter=inprocess; keys retry-attempts, retry-delay, timer-tick-ms. No descriptor.

## 7 Permit-all (AD authz-permitall)
PermitAllAuthorizationPort implements AuthorizationPort; decide always permits; local dev only. authz.adapter=permit-all, set in checked-in config. Has descriptor; PortRegistry checks it. Port class has no Spring imports.

## 8 docker-compose.yml
Services postgres (postgres:18; user/password/db sequeless; port 5432; volume sequeless-pgdata; pg_isready health check) and temporal (temporalio/temporal:1.9.1; server start-dev; gRPC 7233; web UI 8233; SQLite in volume sequeless-temporal). Header claims app has no wiring (stale for Postgres). Temporal adapter on classpath; no checked-in config selects it.

## 9 UI (sequeless-ui)
- Angular 22 workspace; one app sequeless-shell plus libraries: core-api (backend clients), rdf (browser ontology model), ui-kit (widgets), objects (browse, detail, create), ontology (type editor, Turtle tab, import), automation (signal panel, state machine view, trigger editors). Lazy routes /objects (default), /ontology, /automation.
- Build: `ng build sequeless-shell` writes to sequeless-app/src/main/resources/static; Spring serves it, index.html fallback skipping reserved prefixes like /api [SpaWebConfiguration]. Dev: `npm start` = ng serve with proxy of /api, /actuator, /v3/api-docs, /swagger-ui to localhost:8080.
- Backend: base URL /api. ObjectsClient (browse, get, create, update, delete, list/fire transitions, signal); OntologyClient (load/import Turtle); TypesClient. ETag interceptor keeps last ETag per object id, adds If-Match for PUT, DELETE, transition POST; server returns 428 if no version. Signal = POST /api/objects/{type}/{id}/signals/{name}, 202; UI refetches after delay. Browse state in URL query string (not facet selections). Columns, facets, operators, paging from type metadata; no per-type code. State machine diagram from type endpoint; per-object transitions endpoint lists only currently possible actions.
- OntologySession: parsed document, undo journal, working store; forms and Turtle tab are views of it. Browser parses Turtle with n3. Edits are pure functions returning delta of added/removed triples; undo swaps sets. Import 422 with report becomes "rejected" outcome. Dagre lays out state machine; CodeMirror is Turtle editor. e2e tests in sequeless-ui/e2e.

## Uncertain
1 Production automation choice: automation.md calls temporal "production default per DR-09" but config unset. 2 Possible bug (not run): in-process adapter adds id to dispatched set before running action; if action throws, relay rolls back claim, retry sees id and returns early, row marked processed, action never runs; no redelivery test. 3 Typo in automation.adapter leaves no AutomationPort bean; relay does nothing; PortRegistry does not check (inferred). 4 External code (Jena port, SHACL engine, OntologyDocumentStore, kernel, expression) inferred from call sites. 5 Stale comments: Jena package-info "No implementation yet"; compose header. 6 Index DDL embeds IRIs in SQL text with quote escaping; untrusted source unchecked. 7 Poison-pill row blocks relay (documented limitation). 8 Temporal worker not started if server down at boot (documented). 9 sq_outbox.attempts written never read. 10 Nothing was run. 11 UI testing project and e2e bodies not read.
