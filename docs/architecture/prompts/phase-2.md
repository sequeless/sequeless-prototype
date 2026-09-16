# Follow-up prompt: Phase 2

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 2 of Sequeless: BREAD over PostgreSQL with validation and ontology persistence.

CONTEXT
Sequeless is a Spring Boot web application for modelling and managing business objects whose types
are defined at runtime by an OWL ontology plus a custom `sq:` vocabulary. The domain core
(sequeless-core) depends only on sequeless-spi and consumes an immutable MetaModelSnapshot through
OntologyPort (Jena adapter). Instances live in PostgreSQL 18 in one JSONB table (DR-04). Validation
combines structural checks in the core with SHACL shapes evaluated by a ValidationPort (DR-06). The
ontology itself is stored as versioned Turtle documents through the object store (DR-07). Every
port has a default adapter selected by sequeless.<port>.adapter and discovered by Spring Boot
auto-configuration; adapters must pass the contract tests in sequeless-spi-testkit. Reference
domain: Projects, Tasks, People. Read docs/architecture (overview, decision records,
sq-vocabulary.md) and the existing modules first.

Earlier phases delivered: the module structure, ArchUnit rules, Docker Compose with PostgreSQL 18
and Temporal, AuthorizationPort with permit-all adapter, MetaModelSnapshot records, OntologyPort
with the Jena adapter and reasoner, GET /types.

OBJECTIVE
Create, read, update, soft-delete and list (paging only) objects of any type in the ontology, with
validation, optimistic locking and audit fields, on PostgreSQL, and persist the ontology in the
same database so it survives restarts.

CONSTRAINTS
- SPI additions are records: ObjectId (UUID), TypeRef, PropertyRef, Value (sealed: text, integer,
  decimal, bool, dateTime, date, reference(ObjectId), list), BusinessObject (id, type, tenant,
  version, state optional, properties, audit, deleted flag), ChangeSet (mutations plus outbox
  entries), CommitResult, OutboxEntry (id, kind, payload map, occurredAt), Page and PageResult.
- ObjectStorePort.commit is atomic by contract: all mutations and outbox rows in one transaction,
  or none. Optimistic locking: a mutation carries the expected version; mismatch raises
  StaleObjectException. Outbox rows are written in this phase but not consumed.
- ValidationPort validates one BusinessObject against the snapshot and returns a list of violations
  with property path and message. The core runs structural checks first (required, cardinality,
  datatype, reference target type exists) and calls the port only if they pass.
- PostgreSQL adapter: Flyway migrations creating sq_object (id uuid pk, tenant_id text, type_iri
  text, version bigint, state text null, props jsonb, created_at, created_by, updated_at,
  updated_by, deleted_at null), sq_ontology_document (id, tenant_id, version int, format, content
  text, active bool, created_at), sq_outbox (id uuid pk, tenant_id, object_id, kind, payload jsonb,
  created_at, processed_at null, attempts int). GIN index on props. Use Spring JDBC or plain JDBC,
  not JPA.
- The Jena adapter loads the active ontology document via a new OntologyDocumentStore method on
  ObjectStorePort at startup; when no document exists, it seeds from sequeless.ontology.source
  and stores it. Import replaces the active document and rebuilds the snapshot.
- Lombok allowed only in adapter and app modules. No JPA. No Jena in core.

DELIVERABLES
- SPI records and ports above, ObjectStoreContract (CRUD, paging, optimistic locking, soft delete,
  atomic commit with outbox rows, ontology document round trip) and ValidationContract in the
  testkit.
- sequeless-adapter-persistence-postgres passing ObjectStoreContract under Testcontainers, with
  auto-configuration, descriptor, and Flyway wired to the adapter's migration path.
- ValidationPort SHACL implementation in the Jena adapter module (jena-shacl) reading shapes from
  the ontology; passing ValidationContract. Reference ontology gains SHACL shapes (for example
  Task estimatedHours between 0 and 1000, Person email pattern).
- sequeless-core use cases: Browse (paging), Read, Edit, Add, Delete, each consulting
  AuthorizationPort, running validation, and building a ChangeSet with an ObjectCreated,
  ObjectUpdated or ObjectDeleted outbox entry.
- sequeless-app REST: GET /objects/{type}?page=&size=, GET /objects/{type}/{id},
  POST /objects/{type}, PUT /objects/{type}/{id} (with If-Match or body version),
  DELETE /objects/{type}/{id}, POST /ontology (Turtle body, replaces), GET /ontology (Turtle).
  Problem-details error responses with validation violations. OpenAPI updated.
- End-to-end tests with Testcontainers covering the acceptance criteria.

ACCEPTANCE CRITERIA
- Contract tests pass on PostgreSQL.
- Adding a Task without title returns 400 with a structural violation on `title`; adding a Task
  with estimatedHours 5000 returns 400 with a SHACL violation.
- Adding a Task whose belongsToProject references a Person returns 400 (reference type check).
- Updating with a stale version returns 409.
- Deleted objects disappear from browse and read but the row remains with deleted_at set.
- After POST /ontology and an app restart, GET /types reflects the imported ontology without any
  file on disk.
- Each successful commit writes exactly one outbox row (asserted in a test).
- mvn -B verify passes.

OUT OF SCOPE
Filtering, sorting, facets, text search, derived properties, state machines, outbox consumption,
multi-tenancy behaviour, history.

PROCESS
Plan before coding: propose the SPI record shapes, the JSONB encoding of Value, and the REST
request and response formats, and confirm with me before implementing. Ask via AskUserQuestion
when ambiguous (for example how to encode dates in JSONB, or how to name types in URLs).
```
