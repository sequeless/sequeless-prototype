# Review of draft 02 (adapters, docker-compose, UI)

Reviewer: Claude (read-only check against /Users/bbartolome/workspace/sequeless-prototype at 5a6c7fa).
P = /Users/bbartolome/workspace/sequeless-prototype

## VERDICT: REVISE

The facts are almost all correct. I checked 27 claims and found no claim that is wrong. Some claims are
incomplete, and one important topic is missing (the expression adapter). The text is not yet in
ASD-STE100 style: it uses arrows and "=" as verbs, passive fragments, unapproved verbs, and jargon with
no definition. These STE problems are systematic, so the draft must be revised before a newcomer reads it.

## 1. Accuracy

### 1.1 Claims verified as correct (27)

| # | Claim | Evidence |
|---|---|---|
| 1 | application.yaml selects authz=permit-all, ontology=jena, persistence=postgres, validation=shacl; automation is not set | P/sequeless-app/src/main/resources/application.yaml |
| 2 | PortRegistry checks four slots: authz, ontology, persistence, validation | P/sequeless-app/src/main/java/org/sequeless/app/config/PortRegistryConfiguration.java:35-38 |
| 3 | PortRegistry fails on unset property, unknown name, incompatible SPI version, zero beans, several beans | P/sequeless-app/src/main/java/org/sequeless/app/port/PortRegistry.java `validate()` |
| 4 | Descriptors are found by ServiceLoader; only permit-all, postgres, jena and shacl have descriptors; automation adapters have none | META-INF/services/org.sequeless.spi.AdapterDescriptorProvider exists only in the authz, postgres and ontology-jena modules |
| 5 | PostgresObjectStore implements ObjectStorePort + SearchVectorBackfillPort; PostgresQueryStore implements ReadPort, BrowsePort, IndexPreparationPort, QueryPort | PostgresObjectStore.java:53, PostgresQueryStore.java:66 |
| 6 | sq_object columns (id, tenant_id, type_iri, version, state, props JSONB, created/updated audit, deleted_at) | postgres/V1__objects_ontology_outbox.sql:6-18 |
| 7 | Row lock with SELECT ... FOR UPDATE for optimistic version check | PostgresObjectStore.java:196 |
| 8 | Outbox claim: one row, FOR UPDATE SKIP LOCKED, handler in the same transaction, throw = rollback, row stays unprocessed | PostgresOutboxPort.java:20-24, 67-68, 81 |
| 9 | Partial index covers eight claimable kinds | postgres/V5__outbox_transition_fired_claimable.sql |
| 10 | Only one active ontology document per tenant (partial unique index) and per-tenant advisory lock | V1:39-41; PostgresOntologyDocumentStore.java:57 (pg_advisory_xact_lock) |
| 11 | Index name = "sq_idx_" + 16 hex chars of MD5 | PostgresQueryStore.java:346-355 |
| 12 | sq_index_registry and sq_searchable_property; GIN index on search_vector in query V2 | postgres-query/V1__index_registry.sql, postgres-query/V2__search_gin_index.sql |
| 13 | search_vector set by a trigger with the 'simple' config (V6) | postgres/V6__search_vector_command.sql:46-64 |
| 14 | Two migration sets; the query set uses history table flyway_schema_history_query | PostgresMigrations.java:21, 33 |
| 15 | Query and outbox beans use matchIfMissing=true; the object store bean does not | PostgresPersistenceAutoConfiguration.java:48, 112, 137 |
| 16 | Temporal retry defaults: 1 s, multiplier 2.0, 5 attempts, 30 s start-to-close | TemporalAutomationProperties.java:72-78; TemporalAutomationAutoConfiguration.java:166 |
| 17 | Workflow id = outbox id (timer key for timers); REJECT_DUPLICATE; AlreadyStarted is a no-op | TemporalAutomationPort.java:70-135, 154 |
| 18 | Temporal dispatch routing, TransitionFired is a no-op, other kinds throw | TemporalAutomationPort.java:70-85 |
| 19 | Task queue default sequeless-actions; target 127.0.0.1:7233 | TemporalAutomationProperties.java:25, 30 |
| 20 | OutboxRelay polls every 1000 ms by default | OutboxRelay.java `@Scheduled(fixedDelayString = "${sequeless.automation.relay.poll-interval-ms:1000}")` |
| 21 | In-process defaults: 3 webhook attempts, 200 ms fixed gap, 100 ms timer tick | InProcessAutomationProperties.java:21, 27; InProcessAutomationAutoConfiguration.java:94 |
| 22 | SHACL validation fails at startup if the ontology bean is not a JenaOntologyPort | ShaclValidationAutoConfiguration.java:38-39 |
| 23 | Stale comments: Jena package-info "No implementation yet"; compose header "no wiring" | ontology-jena package-info.java:6; P/docker-compose.yml:1-5 |
| 24 | docker-compose: postgres:18 and temporalio/temporal:1.9.1, ports 5432 / 7233 / 8233, volumes, health checks | P/docker-compose.yml |
| 25 | Angular 22; ng build writes to sequeless-app/src/main/resources/static; dev proxy of /api, /actuator, /v3/api-docs, /swagger-ui to localhost:8080 | sequeless-ui/package.json:21; angular.json:24-27; proxy config |
| 26 | ETag interceptor adds If-Match to PUT, DELETE and POST .../transitions/...; server answers 428 when no version is given | core-api/src/lib/http/etag.interceptor.ts:19-51; ObjectsController.java:76; ApiExceptionAdvice.java:171 |
| 27 | Signal POST returns 202; UI refetches after a delay | ObjectsController.java:485-492; objects/src/lib/signals/object-signals.ts:91 (setTimeout) |

Also confirmed from the Uncertain list: item 3 (no AutomationPort bean means the relay returns at once,
OutboxRelay.java `pollOnce()`), item 7 (poison pill, documented in OutboxRelay javadoc), item 9
(attempts is write-only, V3__outbox_processed_at.sql:5; PostgresObjectStore.java:304).

### 1.2 The in-process bug claim (Uncertain item 2): REAL

Trace:
1. OutboxRelay.pollOnce() calls outboxPort.claimNext(); the handler calls automationPort.dispatch()
   inside the claim transaction (OutboxRelay.java).
2. InProcessAutomationPort.dispatch() runs `if (!dispatched.add(entry.id())) return;` BEFORE the
   switch that runs the action (InProcessAutomationPort.java, `dispatch`).
3. If the action throws (for example WebhookDeliveryException after 3 failed attempts, or an
   exception from ActionExecutor), the exception goes to claimNext. The transaction rolls back, and
   the row stays unprocessed (PostgresOutboxPort.java:20-24). The id stays in `dispatched`.
4. On the next tick the relay claims the same row. dispatch() finds the id in `dispatched` and returns
   normally. claimNext then sets processed_at (PostgresOutboxPort.java:81) and commits.
5. Result: the action never runs, and no error is logged. For lifecycle kinds the relay still runs
   the recompute again (it runs before dispatch), but onChange never runs.

Test coverage: AutomationContract.redispatchWithSameEntryIdIsIdempotent tests only success-then-redispatch.
No test covers fail-then-redispatch (P/sequeless-spi-testkit/src/main/java/org/sequeless/testkit/automation/AutomationContract.java:147).
The Temporal adapter does not have this problem in the same way, because a failed start does not
create a workflow id.
Impact is limited: the in-process adapter is for tests and local development, and no checked-in
config selects it. The draft should move this from "Uncertain" to a confirmed defect, and say that the
fix is to remove the id from the set when the action throws.

### 1.3 Incomplete or imprecise claims (correct, but need a fix)

1. Section 1-2: "It does NOT check automation, query, outbox slots." Also the **expression** slot is
   not checked. application.yaml sets `sequeless.expression.adapter: jexl`; the draft omits this line.
2. Section 3 "Query and outbox beans default to postgres when their properties unset": true only when
   `sequeless.persistence.adapter=postgres`, because the class-level @ConditionalOnProperty gates every
   bean (PostgresPersistenceAutoConfiguration.java:48).
3. Section 3 migration layout: the command set has no V2. V2__query_indexes_and_search.sql was deleted in
   commit 9b6e316 (P7). V6 recreates the command part idempotently, and the query set owns the rest.
   Flyway ignores missing and future entries (PostgresMigrations.java:91-98). A newcomer who sees
   V1, V3, V4, V5, V6 needs this fact.
4. Section 3 table list: the search_vector column is not in V1. V6 adds it (V6:9).
5. Section 5/6 recompute: the relay runs DerivationRecomputer.recompute() before dispatch for lifecycle
   rows (OutboxRelay.java). With Temporal, ChangeEventWorkflowImpl.java:23 runs recompute again, so
   recompute runs twice. With in-process, the adapter does not recompute. So "routing mirrors Temporal"
   is not exact for lifecycle kinds.
6. Section 8: the compose header "no wiring to either yet" is stale for both services. A Temporal adapter
   now exists; only the selection property is missing.
7. Section 9: the UI library list omits the `testing` library (sequeless-ui/projects/testing). The draft
   mentions it only in Uncertain item 11.
8. Section 9 ETag: the interceptor keys the cache by object id from the response body, and it adds
   If-Match only when it has a cached version (etag.interceptor.ts:30-33). If no version is cached, the
   request goes without If-Match and the server answers 428. Say this so the 428 statement is clear.

No claim was found to be false.

## 2. Missing important topics

1. The expression adapter: ExpressionPort, JEXL, external `expression-spring-boot-autoconfig`
   (sequeless-app/pom.xml:87), property `sequeless.expression.adapter=jexl`, not checked by PortRegistry.
   ActionExecutor and guards depend on it (CoreConfiguration.java:298).
2. How to select automation locally: the exact property line (`sequeless.automation.adapter: temporal`
   or `inprocess`) and the `docker compose up -d` step. Without this, a newcomer gets no automation and
   no error (Uncertain item 3).
3. The double recompute under Temporal (see 1.3 item 5).
4. The deleted command V2 migration (see 1.3 item 3).
5. e2e test names (automation-signals, deep-link, objects-stale-conflict, ontology-edit-import in
   sequeless-ui/e2e) and the `npm run e2e` command (Playwright). One line is enough.

## 3. STE problems

### 3.1 Systematic problems

1. **Symbols used as verbs.** "adapter = real technology", "throw => rollback", "ActionRequest ->
   ActionWorkflow", "`npm start` = ng serve". STE needs full sentences with a verb.
2. **Passive fragments with no agent.** "beans created only when property matches", "row locked SELECT
   ... FOR UPDATE", "registered in sq_searchable_property", "set by trigger". Use active voice and name
   the agent (Spring, the adapter, PostgreSQL).
3. **Unapproved or multi-meaning verbs.** "holds", "picks", "speaks", "seeds", "mirrors", "skip",
   "claims", "becomes", "lands". Use approved words: "contains", "selects", "uses", "writes",
   "is the same as", "does not read", "gets". "Claim" is a technical noun here: define it once
   ("to claim a row is to lock it for one relay") and then use it only in that meaning.
4. **Sentences too long and with several topics.** Section 3 bullet 4 (soft delete, optimistic lock,
   commit) and section 5 bullet 2 (seven routings in one sentence, more than 50 words). Use one
   sentence or one table row for each topic.
5. **Technical nouns not defined.** SPI, bean, slot, descriptor, ETag, If-Match, JSONB, Flyway, GIN,
   tsvector, advisory lock, SKIP LOCKED, IRI, SHACL, Turtle, ServiceLoader, outbox, relay. Add a short
   term list at the top, or define each term the first time.
6. **Inconsistent terms.** "row", "entry" and "outbox row" mean the same thing. "store" has three
   meanings (object store, ontology document store, working store in the UI). "port", "slot" and
   "property" overlap in section 1-2. Select one term for each concept.
7. **Articles omitted.** "Core holds rules", "Worker starts at boot", "Browser parses Turtle". STE
   keeps "the", "a", "an". (Do not penalise terse fragments by themselves, but a newcomer text must
   keep articles.)
8. **Abbreviations defined only in the header.** "AD", "P", "PG". Write the full module name.
9. **Instructions not in imperative.** "Dev: `npm start` = ng serve ..." must be a procedure:
   "Run `npm start`."
10. **Hedges in the Uncertain list.** "Possible bug", "(inferred)". Where the fact is now verified, write
    it as a fact in present tense.

### 3.2 Rewrite examples

1. Original: "Core holds rules; adapter = real technology behind a port (Java interface in SPI)."
   STE: "The core contains the business rules. A port is a Java interface in the SPI module. An adapter
   is the code that connects a port to a real technology."

2. Original: "Claim takes one unprocessed row FOR UPDATE SKIP LOCKED; handler runs in same transaction;
   throw => rollback, row stays unprocessed."
   STE: "The outbox port locks one unprocessed row. Other relays do not wait for this row. The handler
   runs in the same transaction. If the handler fails, PostgreSQL cancels the transaction. The row then
   stays unprocessed."

3. Original: "Each adapter has an auto-config class; beans created only when property matches."
   STE: "Each adapter has an auto-configuration class. Spring creates the adapter beans only when the
   selection property has the adapter name."

4. Original: "ObjectCreated/Updated/Deleted -> ChangeEventWorkflow (recompute derived values, then onChange)"
   STE (procedure-style table row): "Kind: ObjectCreated, ObjectUpdated or ObjectDeleted. Workflow:
   ChangeEventWorkflow. Result: The workflow calculates the derived values again. Then it calls onChange."

5. Original: "Dev: `npm start` = ng serve with proxy of /api, /actuator, /v3/api-docs, /swagger-ui to localhost:8080."
   STE: "To start the UI for development, run `npm start` in sequeless-ui. This command starts the
   Angular development server. The server sends requests for /api, /actuator, /v3/api-docs and
   /swagger-ui to the backend on localhost:8080."

6. (Bug item) Original: "Possible bug (not run): in-process adapter adds id to dispatched set before
   running action; ..."
   STE: "The in-process adapter has a defect. It records the row id before it runs the action. If the
   action fails, the relay tries the row again. The adapter finds the id and stops. The relay then marks
   the row as processed. The action does not run."
