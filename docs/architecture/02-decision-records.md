# Decision records


Format: context, decision, alternatives, consequences, reversibility.

### DR-01 Audience and tenancy
Context: first version must prove the core idea fast. Decision: single dataset; a `TenantId` is part
of `Scope` and every port call, but only one value is used. Alternatives: team with permissions;
multi-tenant now. Consequences: no isolation logic yet; adapters can add tenant scoping without a
core change. Reversibility: moderate.

### DR-02 Types defined at runtime, seeded from files
Context: meta-model-driven behaviour only pays off if types change without redeploy. Decision: the
ontology is runtime data, importable and exportable as Turtle. Alternatives: build-time only; runtime
only. Consequences: all storage, validation and API paths are generic. Reversibility: hard.

### DR-03 OWL ontology is the runtime meta-model, behind a port
Context: user chose OWL as the core; hexagonal principle forbids Jena in the core. Decision: the OWL
ontology plus `sq:` vocabulary is the source of truth; the core consumes a `MetaModelSnapshot`
through `OntologyPort`; no Jena type crosses the SPI. Alternatives: internal model with OWL as an
export adapter; core using Jena directly. Consequences: a mapping layer and a custom vocabulary to
maintain; open-world reasoning stays in the adapter; closed-world validation via SHACL and structural
checks. Reversibility: moderate.

### DR-04 Instances in PostgreSQL, one JSONB table
Context: relational stores excel at list, filter, page, aggregate. Decision: `sq_object` table with
identity, type, tenant, version, state, audit columns and a JSONB `props` column; GIN index plus
expression indexes created from `sq:indexed` hints. Alternatives: RDF store for everything; table per
type; hybrid columns. Consequences: no runtime DDL; property types enforced by validation, not the
database; column promotion is an adapter-internal optimisation. Reversibility: easy to moderate.

### DR-05 Reasoning
Context: user wants full OWL reasoning; no complete OWL 2 DL reasoner is maintained for Jena 6
(Openllet last released 2019). Decision: Jena's built-in OWL rules reasoner over the ontology (type
level) only, selected by `sequeless.ontology.reasoner`. Alternatives: Openllet; RDFS only.
Consequences: subclass, equivalence, inverse, transitive, domain/range inference and basic
consistency checks; incomplete for DL; no reasoning over instances. Reversibility: easy.

### DR-06 Validation
Decision: SHACL shapes in the ontology evaluated by `ValidationPort`; the core performs structural
checks (required, cardinality, datatype, reference type) from the snapshot before calling the port.
Consequence: closed-world rules despite OWL. Reversibility: easy.

### DR-07 Ontology storage
Decision: Turtle documents stored through `ObjectStorePort` (table `sq_ontology_document`, versioned),
loaded into an in-memory Jena model at startup and on import. Consequence: one database, snapshot
rebuilt on change. Reversibility: easy.

### DR-08 Derived properties
Decision: any type may declare derived properties with `sq:derivedBy` (a `sq:Rollup` with function,
source type, relationship and filters, or a `sq:Plugin` by name). `QueryPort.aggregate` executes
rollups; on-read first, materialised mode via events later. Stateful derived objects are not a
separate concept. Alternatives: expression strings; code only. Reversibility: easy.

### DR-09 State machines and automation
Context: Spring Statemachine archived July 2026; Drools is a rules engine, not a state engine.
Decision: state machines are `sq:StateMachine` data in the ontology, interpreted in the core; guards
via `ExpressionPort` (JEXL default); state change and outbox rows committed atomically; Temporal is
the default `AutomationPort` adapter from the start, with an in-process adapter for tests.
Alternatives: in-process only first; Temporal plus Drools. Consequences: Temporal server is required
infrastructure; durable, retried, idempotent actions. Reversibility: moderate.

### DR-10 API only, REST
Decision: no UI in this plan; generic REST resources (`/types`, `/objects/{type}`,
`/objects/{type}/{id}`, `/objects/{type}/{id}/transitions`, `/ontology`); display hints are
returned in type descriptors. GraphQL possible later as an inbound adapter. Reversibility: easy.

### DR-11 Authorisation port first
Decision: `AuthorizationPort` with permit-all default from phase 0; every use case consults it;
Spring Security with OIDC and `sq:permission` annotations arrive in phase 8. Reversibility: easy.

### DR-12 Events and outbox
Decision: the core emits `DomainEvent`s and `ActionRequest`s into the `ChangeSet`; the store writes
them to `sq_outbox` in the same transaction; the automation adapter relays them. External publishing
is a later adapter. Reversibility: easy.

### DR-13 Search
Decision: PostgreSQL full-text search (`tsvector` over `sq:searchable` properties) inside the default
query adapter; a separate search index adapter only if facet queries prove too slow. Reversibility: easy.

### DR-14 History and deletion
Decision: soft delete (`deleted_at`), optimistic locking (`version`), created/updated audit fields;
full history deferred and addable inside the persistence adapter. Reversibility: moderate.

### DR-15 Platform and discovery
Decision: Java 25, Spring Boot 4.1.x, Maven; records and no Lombok in SPI and core; Lombok allowed
in adapters and app; Spring Boot auto-configuration keyed by `sequeless.<port>.adapter` with
ServiceLoader descriptors for listing; ArchUnit, japicmp, Testcontainers, Flyway, Docker Compose.
Reversibility: easy.

### DR-16 Meta-model evolution
Decision: ontology documents are versioned; additive changes take effect on import; breaking changes
(property removed or retyped, cardinality tightened, state removed) are detected by diffing snapshots
and require a recorded migration plan executed against JSONB data before the new version activates.
Reversibility: moderate.
