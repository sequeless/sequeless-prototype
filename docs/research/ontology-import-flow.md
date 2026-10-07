# Ontology import, versioning and activation flow (issue #7)

Findings from reading the source at HEAD `50cef17`. Paths are relative to the repo root.

## End-to-end flow of `POST /ontology`

1. `OntologyController.importOntology` (`sequeless-app/src/main/java/org/sequeless/app/rest/OntologyController.java:64-68`) accepts `text/turtle` and calls `importTurtle(currentScope(), turtle)`. The scope is hard-wired to `TenantId.DEFAULT` + `Principal.ANONYMOUS` (`:83-85`).
2. `DefaultOntologyAdministration.importTurtle` (`sequeless-core/src/main/java/org/sequeless/core/usecase/DefaultOntologyAdministration.java:60-72`):
   - authorizes `Operation.ADMIN` on `EVERYTHING` (`:64`, `:74-79`);
   - wraps the text in `OntologyDocument(content, TURTLE)` (`:65`);
   - calls `ontologyPort.importDocument(scope, document, ImportMode.REPLACE)` (`:66`). `REPLACE` is the only mode (`sequeless-spi/.../ontology/ImportMode.java:9`);
   - then `ontologyPort.snapshot(scope)` and `queryPort.ensureIndexes(scope, snapshot)` (`:68-69`).
3. `JenaOntologyPort.importDocument` (`sequeless-adapter-ontology-jena/.../JenaOntologyPort.java:259-274`):
   - `buildState(...)` on a fresh `PortState` (`:269`);
   - `requireConsistent(fresh)` throws `OntologyException` if inconsistent (`:270`, `:296-302`). The controller maps this to HTTP 422 via `ApiExceptionAdvice`;
   - `documentStore.ifPresent(store -> store.activate(scope, document))` (`:271`);
   - `current = fresh` (`:272`), then returns `ImportReport` (`:273`).
4. Snapshot build: `buildState` (`:313-330`) -> `OntModelBuilder.build` -> `buildStateFrom` (`:336-387`). It merges the reasoner consistency report, unresolved `owl:imports`, reserved-vocabulary check, `SnapshotMapper` issues and unknown-plugin issues into one `OntologyReport`. It then builds an immutable `MetaModelSnapshot` (`:379-384`; record at `sequeless-spi/.../meta/MetaModelSnapshot.java:38-63`). Parse or mapping failures become a failure state, not a thrown exception (`:319-326`, `:434-440`).
5. Swap and cache: the active snapshot lives in `private volatile PortState current` (`JenaOntologyPort.java:132`), a record of `(OntModel, MetaModelSnapshot)` (`:454`). It is swapped by single reference assignment (`:272`, `:292`). `snapshot()` just returns `requireConsistent(current).snapshot()` (`:229-232`). Nothing else caches it: `DefaultMetaModelService` and the other services call `ontologyPort.snapshot(scope)` each time. The constructor adopts its first state, consistent or not (`:139`).
6. Persistence: `PostgresOntologyDocumentStore` (`sequeless-adapter-persistence-postgres/.../PostgresOntologyDocumentStore.java`), table `sq_ontology_document` (`.../db/migration/sequeless/postgres/V1__objects_ontology_outbox.sql:26-41`). The SPI is `OntologyDocumentStore` (`sequeless-spi/.../object/OntologyDocumentStore.java:35-63`) with only `active(Scope)` and `activate(Scope, OntologyDocument)`. `StoredOntologyDocument(id, version, document, createdAt)` is at `.../StoredOntologyDocument.java:83`; it has no `active` flag.
7. Startup: `JenaOntologyAutoConfiguration.ontologyPort` (`.../JenaOntologyAutoConfiguration.java:50-62`) uses `JenaOntologyPort.fromStore` (`JenaOntologyPort.java:193-226`) when a store bean exists. It loads the active document, or seeds from `sequeless.ontology.source` after a consistency probe and activates it (`:200-221`). `reload()` re-reads the store's active document, or `sourceLoader` when there is no store (`:235-241`, `:243-249`), and swaps only on success. `POST /types/reload` (`TypesController.java:74`) uses this.

## Answers

### (1) Can a document be stored without being active?
Not through the SPI. `activate` is the only write method, and it always inserts with `active = true` (`PostgresOntologyDocumentStore.java:80-81`). The schema allows inactive rows (`active boolean NOT NULL DEFAULT false`, V1 `:32`), and `activate` flips older rows to `false` (`:62`), so superseded versions remain as inactive history. But no code path stores a new row as inactive. There is also no way to list versions or fetch one by number.

### (2) Can a `MetaModelSnapshot` be built from a candidate document without affecting the live one?
The mechanism exists but is private. `buildState(Supplier<InputStream>)` builds an independent `PortState` and touches nothing published (`JenaOntologyPort.java:313`). The public surface offers no dry run, though. `importDocument` builds fresh, but if it is consistent it activates in the store and swaps `current` immediately (`:269-272`). `fromDocument` builds a separate port (`:149-154`) and could be used as a probe, as `fromStore` does (`:218-219`), but that is a construction-time workaround, not an `OntologyPort` method. A rejected import leaves `current` untouched, so "validate only" is effectively available only for failures.

### (3) Are versions monotonic per tenant, and how are they assigned?
Yes. Inside one transaction, `activate` takes `pg_advisory_xact_lock(hashtext(tenantId))` (`:57`), deactivates the active row (`:62`), and computes `COALESCE(MAX(version), 0) + 1` for that tenant (`:66-73`). The first version is 1. Unique indexes enforce `(tenant_id, version)` and at most one active row per tenant (V1 `:36-41`). Versions are never reused, since rows are never deleted. Note this store version is unrelated to the ontology's own `owl:versionIRI` (`MetaModelSnapshot.versionIri`, `JenaOntologyPort.java:379-381`).

### (4) What would need to change to stage a candidate and activate it, or an older version?
- **SPI** (`OntologyDocumentStore`): add `stage(Scope, OntologyDocument)` that inserts an inactive row with the next version, `activate(Scope, int version)` that flips the active flag in the same advisory-locked transaction, and `get`/`list` of versions. `StoredOntologyDocument` needs an `active` flag (and `createdAt` currently means "activated at"). Update the javadoc contract and `ObjectStoreContract` in `sequeless-spi-testkit`, plus the in-memory test doubles.
- **Postgres adapter**: implement the above. The schema needs no migration, because `active` and the unique indexes already support it. Version assignment moves to `stage`, and activating an existing version must not renumber.
- **`OntologyPort`**: add a non-mutating `validate/preview(scope, document)` returning a snapshot or report. In Jena this is `buildState` plus `requireConsistent`, with no swap and no store write. Add an `activate(scope, version)` that loads that stored document, builds, checks consistency, activates in the store, and swaps `current`. Today `importDocument` hard-wires store-write plus swap.
- **Ordering**: `importDocument` activates in the store before swapping `current` (`:271-272`); if the store throws, memory stays on the old state. Keep that order.
- **Core and REST**: new `OntologyAdministration` methods plus controller endpoints. They would call `queryPort.ensureIndexes` after activation, as `importTurtle` does.
- **Multi-tenancy gap**: `JenaOntologyPort` holds one `current` state and ignores `Scope.tenantId` in `snapshot`/`importDocument` (`:229-232`, `:259`). The store is per-tenant but the live snapshot is process-global. The controller only uses `DEFAULT`, so this is latent. Per-tenant staging and activation would need a per-tenant state map.
- **Multi-instance**: other instances only see an activation on their next `reload` (`:235-241`), so cross-node activation would need a reload trigger.
