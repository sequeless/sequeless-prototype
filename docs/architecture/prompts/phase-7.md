# Follow-up prompt: Phase 7

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 7 of Sequeless: meta-model evolution and data migration.

CONTEXT
Sequeless is a Spring Boot web application for modelling and managing business objects whose types
are defined at runtime by an OWL ontology plus a custom `sq:` vocabulary. The ontology is stored as
versioned Turtle documents in PostgreSQL (sq_ontology_document) and loaded by the Jena adapter into
an immutable MetaModelSnapshot consumed by the core through OntologyPort. Instances live in one
JSONB table (sq_object) and are read, queried, validated, derived and transitioned by the core via
ports with PostgreSQL, JEXL and Temporal default adapters. Decision DR-16: additive ontology
changes activate immediately; breaking changes require a recorded migration plan executed before
activation. Reference domain: Projects, Tasks, People. Read docs/architecture and existing modules
first.

Earlier phases delivered the full runtime: BREAD, query and facets, on-read and materialised
derivations, state machines with user, on-change, timer and signal triggers, durable actions.
POST /ontology currently replaces the active document and rebuilds the snapshot without checking
existing data.

OBJECTIVE
Ontology changes are classified as additive or breaking by diffing snapshots; breaking changes are
blocked until a migration plan is recorded and applied to existing data; previous versions can be
reactivated.

CONSTRAINTS
- Snapshot diff lives in the core (pure function over two snapshots): added or removed types and
  properties, datatype changes, cardinality tightening, required-ness added, target type changes,
  removed or renamed states, derivation and state machine changes, facet or index hint changes.
- Classification: additive (activate now), index-only (activate now, adapters rebuild indexes),
  breaking (needs a plan). Removing a property with no data in any row is additive.
- Migration plan: a record with ordered steps chosen from RenameProperty, FillDefault (constant or
  expression via ExpressionPort), ConvertType (with a defined conversion table), DropProperty,
  RemapState (old to new), DeleteObjectsOfType (requires explicit confirmation flag). Steps are
  executed through ObjectStorePort in batches, recorded in sq_meta_migration with progress, and
  are resumable. Validation against the new snapshot runs on migrated rows; failures stop the plan
  and report offending ids.
- Activation: the new document becomes active only after the plan completes; reactivating the
  previous version is allowed if no step was destructive, otherwise blocked with an explanation.
- Materialised derivations and indexes are rebuilt after activation through the existing
  automation and index management paths.

DELIVERABLES
- Core: SnapshotDiff, ChangeClassification, MigrationPlan records and use cases (dry run, submit
  plan, apply, status, reactivate).
- SPI: batch iteration and bulk update methods on ObjectStorePort with contract tests; migration
  record storage.
- PostgreSQL adapter: sq_meta_migration table and batch operations.
- REST: POST /ontology?mode=dryRun returning the diff and classification; POST /ontology
  (additive applies immediately; breaking returns 409 with the diff and a plan template);
  POST /ontology/migrations with the plan; GET /ontology/migrations/{id}; POST /ontology/versions/
  {version}/activate.
- docs/architecture/meta-model-evolution.md with the classification rules and step semantics.
- Tests using the reference ontology: add a property (additive), rename Task.estimatedHours to
  effortHours with data, tighten cardinality on a property with violating rows, remove the OnHold
  state with Projects in it.

ACCEPTANCE CRITERIA
- Adding Task.priority activates immediately and existing Tasks read with priority absent.
- Renaming estimatedHours with data is classified breaking; the dry run lists it; a plan with
  RenameProperty migrates every row and activates the new version; on-read rollups still work.
- Tightening cardinality with violating rows fails the plan with the offending ids and leaves the
  old version active.
- Removing OnHold with Projects in that state is blocked until a RemapState step is provided.
- Reactivating the previous version after a non-destructive plan succeeds and the diff is empty
  after re-applying.
- mvn -B verify passes.

OUT OF SCOPE
Multi-tenant ontologies, UI, new adapters, authorisation beyond permit-all.

PROCESS
Plan before coding: propose the diff model, the classification table and the step semantics, and
confirm with me before implementing. Ask via AskUserQuestion when ambiguous, especially about
what counts as destructive.
```
