# Research: existing index management and materialised-derivation rebuild paths

Issue: sequeless/sequeless-prototype#8. Read-only survey of the code at the branch point; no source changed.

## Summary

| Question | (a) Query indexes | (b) Materialised derivations |
|---|---|---|
| Entry point | `QueryPort.ensureIndexes(Scope, MetaModelSnapshot)` | `DerivationRecomputer.recompute(Scope, OutboxEntry)` |
| Invoked today | Only from `DefaultOntologyAdministration.importTurtle` (`POST /ontology`) | Only per `ObjectCreated/Updated/Deleted` outbox event, via the automation adapter |
| Not invoked on | Startup seed load, `POST /types/reload`, direct `OntologyDocumentStore.activate` | Ontology activation / reload |
| Callable after activation | Yes, as is; covers all of the tenant's rows | No. Event-in and source-object-centric, so the shape is wrong |

## (a) Query indexes

### Code
- Migration `sequeless-adapter-persistence-postgres/src/main/resources/db/migration/sequeless/postgres/V2__query_indexes_and_search.sql`
  - `sq_index_registry` (tenant_id, property_iri, index_name, created_at) L5-11; `sq_searchable_property` L13-17.
  - `sq_object.search_vector tsvector` L19; `sq_extract_searchable_text` L28-48; BEFORE INSERT/UPDATE trigger `sq_object_update_search_vector` L54-70 (rebuilds the vector per row from the tenant's registered searchable properties); GIN index L72.
  - The migration creates no per-property expression indexes. Those are created at runtime.
- Migrations run via `PostgresMigrations.migrate` (`.../postgres/PostgresMigrations.java:23-26`), Flyway location `classpath:db/migration/sequeless/postgres`. There is no separate "index manager" class; the logic lives in `PostgresQueryStore`.
- `PostgresQueryStore.ensureIndexes` (`.../postgres/PostgresQueryStore.java:143-163`), in one `transactionTemplate`:
  - for each property in the snapshot: `indexed()` calls `ensureIndex` (L299-330); `searchable()` calls `registerSearchable` (L349-368);
  - if any property was newly registered as searchable, `backfillSearchVector(tenantId)` (L387-398) runs one `UPDATE sq_object ... WHERE tenant_id = :tenantId`.
  - `ensureIndex` skips if a registry row exists, else `CREATE INDEX IF NOT EXISTS sq_idx_<md5, 16 hex> ON sq_object ((<cast expr>))` (name from `indexNameFor`, L338-347; expression from `castExpressionLiteral`, L595) and inserts the registry row.
- SPI contract: `sequeless-spi/src/main/java/org/sequeless/spi/query/QueryPort.java:46-52, 104` (idempotent; a snapshot with no indexing needs is a no-op).

### How invoked today
- `DefaultOntologyAdministration.importTurtle` (`sequeless-core/src/main/java/org/sequeless/core/usecase/DefaultOntologyAdministration.java:61-72`): `importDocument(REPLACE)`, then `ontologyPort.snapshot(scope)`, then `queryPort.ensureIndexes(scope, snapshot)`. HTTP: `OntologyController.java:66` (`POST /ontology`). Wired in `CoreConfiguration.java:180-184`.
- Not invoked on:
  - startup seed load. `OntologyStartupValidator` only forces a snapshot. `JenaOntologyPort.fromStore` (`JenaOntologyPort.java:193-226`) activates the seed directly (L220). The e2e test documents this gap and works around it by exporting then re-importing (`sequeless-app/src/test/java/org/sequeless/app/QueryEndToEndTest.java:44-51, 76`).
  - `POST /types/reload` (`TypesController.java:73-77`, to `DefaultMetaModelService.reload` L72-75, to `JenaOntologyPort.reload` L235). It re-reads the active document and swaps state but never calls `ensureIndexes`.
  - direct `OntologyDocumentStore.activate` (`PostgresOntologyDocumentStore.java:47`).

### Can it run after an ontology activation to cover all objects of a type?
- Yes for new `sq:indexed`: the expression index is table-wide, so existing rows are covered by the database.
- Yes for new `sq:searchable`: the backfill updates every row of the tenant (not only the type).
- Inputs: `Scope` (only the tenant is used) and a `MetaModelSnapshot` (`OntologyPort.snapshot(scope)`). No type argument.

### Gaps
1. Not wired to startup seed or `/types/reload`; there is exactly one caller, so every activation path must remember to call it.
2. Registry skip means a property whose datatype/cast changes keeps its stale index (`ensureIndex` returns early, L299-312). No drop/rebuild path.
3. Properties removed from the ontology, or no longer `indexed`/`searchable`, are never unregistered. `sq_searchable_property` keeps feeding the trigger; no `DROP INDEX` exists.
4. Backfill fires only when a searchable property is newly registered, and rewrites every row in the tenant (not scoped to a type).
5. Index creation runs inside the transaction, so it is a plain blocking `CREATE INDEX`, not `CONCURRENTLY`.
6. The index lives on `sq_object` with no tenant predicate, but its name hashes tenant + property (L338-347). Two tenants indexing the same property therefore create duplicate identical indexes.

## (b) Materialised derivations

### Code
- Port: `sequeless-spi/src/main/java/org/sequeless/spi/automation/DerivationRecomputer.java:23-52`, `void recompute(Scope, OutboxEntry changeEvent)`.
- Impl: `sequeless-core/src/main/java/org/sequeless/core/derivation/DefaultDerivationRecomputer.java` (the SPI javadoc names it `core.usecase.*`; it is in `core.derivation`). Bean: `CoreConfiguration.java:319-329` (retry `sequeless.automation.recompute.retry.max-attempts`=8, `base-delay`=PT0.02S).
- Algorithm (`recompute`, L155-219):
  1. read `objectId`/`typeIri` from the payload (L159-161); return if the type is unknown or the object is gone;
  2. for ObjectDeleted, decode the `properties` snapshot in the payload (L173, L354-360); otherwise read the object (L175);
  3. over every property with a materialised `RollupRule` whose `sourceTypeIri` is a supertype of the changed type, take the `viaIri` reference value(s) and collect `(targetId, propertyIri)` pairs (L189-213);
  4. `recomputeOne` per pair (L234-296): re-read the target, `DerivationPlanner.computeRule`, commit via `ObjectStorePort.commit` (which writes an `ObjectUpdated` entry) only if the value changed; retry on `StaleObjectException`.
- `PluginRule` cannot be materialised (rejected at ontology mapping, `SnapshotMapper.java` near L636).

### How invoked today
- Event-driven only. `OutboxRelay.pollOnce` (`sequeless-app/src/main/java/org/sequeless/app/automation/OutboxRelay.java:101-129`, `@Scheduled`, default 1s) claims rows of the 7 `DISPATCHABLE_KINDS` (L46-54) and calls `AutomationPort.dispatch(scope, entry)`. Scope is rebuilt from the tenant plus principal `system` (L143, L161-168).
- In-process adapter: `InProcessAutomationPort.dispatch` (`sequeless-adapter-automation-inprocess/.../InProcessAutomationPort.java:112-133`). Lifecycle kinds go to `RecomputeCoordinator.recompute` (modes RETRY_ONLY / SERIALIZE / COALESCE, default COALESCE; `RecomputeCoordinator.java:60-113`), then `triggerEvaluator.onChange`. Dedups by `entry.id()` in an in-memory set (L78, L116).
- Temporal adapter: `TemporalAutomationPort.dispatch` (`sequeless-adapter-automation-temporal/.../TemporalAutomationPort.java:70-78`) starts `ChangeEventWorkflow` (workflow id = `entry.id()`, reject-duplicate; `startChangeEventWorkflow` L98+), which calls `TriggerActivities.recompute` then `onChange` (`ChangeEventWorkflowImpl.java:24`; `TriggerActivitiesImpl.java:36-37`). RETRY_ONLY semantics only.
- Not invoked on startup or ontology activation/reload. Nothing else calls `DerivationRecomputer`.

### Can it be called after an ontology activation to cover all objects of a type?
Not directly.
- It is keyed on a change event for one source object and finds targets via the reference. There is no "all objects of target type T for rule R" mode.
- Input needed per call: `Scope` plus an `OutboxEntry` whose payload has `objectId` and `typeIri` (and `properties` for ObjectDeleted). `kind` must be one of the three lifecycle kinds.
- A backfill could be built from existing pieces: enumerate source objects with `ObjectStorePort.browse(scope, Set<TypeRef>, Page)` (`ObjectStorePort.java:94`; page size max 500, ordered by createdAt,id) or `QueryPort.query`, then call `recompute` with a synthetic `ObjectUpdated` entry per source object. That re-derives all affected targets and commits only on change, so it is idempotent. This only reaches targets that have at least one source (see gap 3).

### Gaps
1. No entry point by rule or target type. A newly activated rule has no event to run on, so existing targets keep an absent or stale materialised value. Filtering on it uses stored props (`DefaultBusinessObjectService.java:389-411`), so results are wrong until a recompute happens. Read-time `DerivationPlanner.apply` still computes the value for display.
2. Synthetic `OutboxEntry` is the only input form, and entries are only written inside `ObjectStorePort.commit(ChangeSet)`. Calling `recompute` directly works but bypasses `OutboxRelay`, so there is no Temporal durability/retry and no coalescing.
3. A target with no sources (e.g. a Project with zero Tasks) gets no event, so a newly activated count rule would never write `0` for it. This needs a target-driven pass, which does not exist.
4. No batching across a page (intentional, `DefaultDerivationRecomputer.java:228-233`). A full backfill is O(sources) recomputes plus a commit per changed target, each writing an `ObjectUpdated` that re-dispatches another recompute (converges, but roughly doubles outbox traffic).
5. The relay uses principal `system` with no roles; a backfill job must construct its own `Scope` (tenant required).
6. The in-process dedup set and coordinator maps grow unbounded (`RecomputeCoordinator.java:19-24`); a bulk backfill through that adapter inherits this.
7. Temporal is RETRY_ONLY only, so bulk bursts rely on activity retry (`ChangeEventWorkflow.java:22-33`).
8. An exhausted retry propagates; in the relay it blocks the queue (poison pill, `OutboxRelay.java:93-99`).

## Suggested minimal shape (for follow-up, not implemented)
- One activation hook, called after `importDocument`/`reload`/seed, that calls `ensureIndexes` and a new backfill use case.
- Backfill use case: for each materialised rule newly present or changed, page target-type objects via `browse` and run the target-side recompute for `(target, property)`. That needs a new method on `DerivationRecomputer` (or core-internal use of the `recomputeOne` logic) so it does not have to synthesise events, and it covers sourceless targets.
