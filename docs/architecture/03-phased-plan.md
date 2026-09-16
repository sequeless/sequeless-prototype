# Phased plan


Reference domain throughout: **Projects, Tasks, People**. Project has lifecycle Draft, Active,
OnHold, Closed. Task belongs to one Project and is assigned to one Person. Project has derived
`openTaskCount` and `totalEstimatedHours`. The reference ontology lives in the SPI testkit so every
adapter is tested against the same fixture.

| Phase | Goal | Depends on |
|---|---|---|
| 0 | Walking skeleton: modules, boundaries, discovery, Compose, one real port | — |
| 1 | Meta-model core: snapshot, `OntologyPort`, Jena adapter with reasoner, `/types` | 0 |
| 2 | BREAD over PostgreSQL with validation and ontology persistence | 1 |
| 3 | Query, facets, text search, index hints | 2 |
| 4 | Derived properties computed on read | 3 |
| 5 | State machines with Temporal-backed actions | 2, 4 |
| 6 | Event-driven automation: on-change triggers, timers, signals, materialised derivations | 5 |
| 7 | Meta-model evolution and data migration | 2, 6 |
| 8 | Adapter swap proof: in-memory store, OIDC authorisation, external events, SPI 1.0 | all |

### Phase 0 — Walking skeleton and enforceable boundaries
Goal: a bootable multi-module build where the port-and-adapter mechanism is proven with one real port.
In: parent POM, all module shells, `sequeless-spi` with `Scope`, `TenantId`, `Principal`,
`AdapterDescriptor`, `SpiVersion`, `AuthorizationPort`; permit-all adapter with auto-configuration;
testkit with `AuthorizationContract`; `sequeless-app` booting with Actuator health and a `/whoami`
endpoint that calls the port; ArchUnit rules; japicmp wired (baseline none); Docker Compose with
PostgreSQL 18 and Temporal (not yet used); CI script (`mvn verify`).
Out: ontology, persistence, any business object.
Exit criteria: `mvn verify` green; ArchUnit tests fail if a test adds a Spring import to core (proved
by a deliberately failing sample removed before merge); `sequeless.authz.adapter=missing` fails
startup with a clear message; `docker compose up` brings all three services healthy.
Risks reduced: boundary drift; adapter discovery design.

### Phase 1 — Meta-model core
Goal: the core can obtain a reasoned, immutable snapshot of the reference ontology through the port.
In: `MetaModelSnapshot` records (types, properties, relationships, cardinality, datatype, display
hints, facet and index hints, placeholders for state machine and derivation rules); `sq:` vocabulary
specification document (`docs/architecture/sq-vocabulary.md`); `OntologyPort`; Jena adapter using
ontapi and the built-in OWL reasoner, selectable via `sequeless.ontology.reasoner=owl|rdfs|none`;
Turtle loaded from a configured path or classpath; `OntologyContract` in the testkit; REST `GET
/types` and `GET /types/{iri}` returning descriptors with display hints; snapshot caching and reload
endpoint.
Out: persistence of the ontology, instances, validation.
Exit criteria: snapshot for the reference ontology shows `Task` as a subclass of `WorkItem` via
inference; inverse relationship inferred; an inconsistent test ontology is rejected with a report;
`/types` lists Project, Task, Person with display hints; ArchUnit proves no `org.apache.jena` in core.
Risks reduced: OWL-to-snapshot mapping; reasoner cost and behaviour.

### Phase 2 — BREAD over PostgreSQL
Goal: create, read, update, delete and list objects of any type defined in the ontology.
In: `BusinessObject`, `Value`, `ChangeSet`, `CommitResult` in SPI; `ObjectStorePort`;
`ValidationPort` with SHACL adapter and core structural checks; PostgreSQL adapter with Flyway
migrations for `sq_object`, `sq_ontology_document`, `sq_outbox` (written, not yet consumed);
optimistic locking, soft delete, audit fields, tenant column; ontology stored in the database with
`POST /ontology` import and `GET /ontology` export, snapshot rebuilt on import; use cases Browse
(paging only), Read, Edit, Add, Delete; REST endpoints; `ObjectStoreContract`; Testcontainers
end-to-end tests.
Out: filtering, facets, derived values, state.
Exit criteria: contract tests pass on PostgreSQL; a Task with a missing required `title` is rejected
by structural validation, and a SHACL-only rule (for example estimated hours between 0 and 1000) is
rejected by the SHACL adapter; stale version update returns 409; delete hides the object from browse
but keeps the row; restart preserves the imported ontology.
Risks reduced: JSONB mapping; validation split; transaction boundary with outbox.

### Phase 3 — Query and facets
Goal: browse any type with filters, sorting, paging, facet counts and text search.
In: `Query`, `Criterion`, `Sort`, `Page`, `QueryResult`, `FacetBucket` in SPI; `QueryPort`;
PostgreSQL implementation with JSONB operators, `tsvector` for `sq:searchable`, expression and GIN
indexes created on snapshot activation from `sq:indexed`; facet counts for `sq:facet` properties
including reference facets; REST query parameters and response shape; `QueryContract`.
Out: aggregation over related objects.
Exit criteria: contract tests pass; facet counts for Task by status and by assignee match a fixture;
filtering on a reference property works; `EXPLAIN` in an integration test shows the expression index
used for an indexed property; text search returns ranked hits.
Risks reduced: query port shape generic enough for non-SQL adapters.

### Phase 4 — Derived properties on read
Goal: a Project's `openTaskCount` and `totalEstimatedHours` are computed from Tasks when read or browsed.
In: `sq:derivedBy`, `sq:Rollup`, `sq:Plugin` vocabulary; `DerivationRule` in the snapshot;
`AggregateRequest`/`AggregateResult`; `QueryPort.aggregate` in PostgreSQL; derivation planner in core
that batches aggregates for a browse page; `DerivationPlugin` SPI discovered by ServiceLoader;
derived properties marked read-only in type descriptors and rejected on write.
Out: materialisation, filtering on derived values.
Exit criteria: reading a Project returns correct rollups after Task changes with no stored value;
browse of 50 Projects issues a bounded number of aggregate queries (asserted); a sample plug-in
derivation is discovered and executed; writing a derived property returns 400.
Risks reduced: rollup vocabulary expressiveness; N+1 on browse.

### Phase 5 — State machines with Temporal-backed actions
Goal: a Project moves through its lifecycle via user-triggered transitions whose actions run durably.
In: `sq:StateMachine`, `sq:State`, `sq:Transition`, `sq:guard`, `sq:action` vocabulary with action
kinds `sq:SetProperty`, `sq:CreateObject`, `sq:Webhook`, `sq:Log`; `StateMachineDefinition` in
snapshot; interpreter in core; `ExpressionPort` with JEXL adapter (sandboxed, no side effects);
`ActionRequest` outbox rows; `AutomationPort`; Temporal adapter (outbox relay, one workflow per
outbox entry keyed by entry id, activities calling core use cases, retry policy); in-process adapter
for tests; REST `GET /objects/{type}/{id}/transitions` and `POST …/transitions/{name}`;
`AutomationContract`; Temporal added to the Testcontainers setup.
Out: on-change triggers, timers, external signals, Drools.
Exit criteria: illegal transition returns 409 with the guard reason; a transition with a failing
webhook is retried by Temporal and eventually succeeds when the stub recovers; killing the app between
commit and dispatch loses no action (outbox replay test); `sequeless.automation.adapter=inprocess`
passes the same end-to-end tests.
Risks reduced: consistency between state and actions; Temporal operational fit.

### Phase 6 — Event-driven automation and materialised derivations
Goal: transitions can fire from data changes, timers and external signals, and derived values can be stored.
In: `sq:OnChange`, `sq:Timer`, `sq:ExternalSignal` triggers; domain events for create, update,
delete, transition in the outbox; Temporal workflows for timers (per object and state) and signals
(`POST /objects/{type}/{id}/signals/{name}`); `sq:materialised true` on a derivation rule causing
recompute on source events and storage in `props`; materialised derived values become filterable and
sortable.
Out: external publishing, meta-model change handling.
Exit criteria: closing the last open Task auto-transitions a Project when the ontology says so; a
Project left in OnHold for a test-configurable duration transitions via timer; a materialised
`openTaskCount` is filterable in browse and stays consistent under concurrent Task edits (test).
Risks reduced: event storms and recompute cost; timer correctness across restarts.

### Phase 7 — Meta-model evolution and migration
Goal: change the ontology safely while data exists.
In: ontology document versions; snapshot diff (added, removed, retyped properties; cardinality and
state changes); classification as additive or breaking; migration plan records
(`sq_meta_migration`) with steps (rename, default fill, type convert, drop, state remap) executed
against JSONB; activation blocked until the plan completes; REST endpoints for dry-run diff, plan
and apply; rollback by reactivating the previous version.
Exit criteria: additive import activates immediately; renaming a property with data requires a plan
and migrates all rows; removing a state with live objects is blocked until remapped; the previous
version can be reactivated.
Risks reduced: the hard-to-reverse decision to have runtime types.

### Phase 8 — Adapter swap proof and SPI 1.0
Goal: demonstrate that adapters swap by configuration only and freeze the SPI.
In: `inmemory` ObjectStore and Query adapter passing the contracts; Spring Security OIDC
`AuthorizationPort` adapter with `sq:permission` annotations for per-type and per-field access;
external event publisher adapter (webhook or Kafka, choose one) fed from the outbox; SPI 1.0.0
release with japicmp baseline; adapter authoring guide.
Exit criteria: full end-to-end suite passes with `persistence=inmemory` and `query=inmemory`; a
field marked `sq:permission "admin"` is hidden from a non-admin principal; the japicmp check fails
on a deliberately breaking SPI change; the guide is enough for a fresh agent to write a compliant
adapter (verified by a dry run).
Risks reduced: hidden coupling to PostgreSQL; SPI stability.
