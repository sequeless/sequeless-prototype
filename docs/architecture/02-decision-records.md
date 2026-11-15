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
ontology plus `sq:` vocabulary is the source of truth; the core consumes the `Schema`, `Behaviour`
and `Automation` records through the read ports `SchemaSource`, `BehaviourSource` and
`AutomationSource`; no Jena type crosses the SPI. Alternatives: internal model with OWL as an
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

### DR-17 API prefix and co-served SPA
Decision: the REST API lives under `/api`, applied by `addPathPrefix` to the `org.sequeless.app.rest` package
(`ApiPathPrefixConfiguration`; a blank property opts out). The Angular build is served from the same jar at `/`,
with client-side deep links answered by `index.html` while real files and reserved server prefixes
(`/api`, Actuator, springdoc) are never answered with the UI shell. Reversibility: easy.

### DR-18 Client-side RDF authoring
Decision: ontology edits round-trip Turtle in the browser (`projects/rdf`). Restriction edits mint blank nodes
on the client (`edits.ts`); `POST /api/ontology` stays authoritative. Forward-compatible with backend Phase 7.
Reversibility: moderate.

### DR-19 Frontend pluggability
Decision: UI extension points are DI tokens with defaults (`OBJECT_SIGNALS_PANEL`, `ONTOLOGY_EDITOR_PANELS`,
`STATE_MACHINE_DIAGRAM`), composed by `provide*()` functions. This mirrors the backend adapter swap of DR-15.
The shell adds automation to a route only when that route loads (`app.routes.ts`). Reversibility: easy.

### DR-20 Hand-written wire models
Decision: TypeScript wire models are hand-written, not OpenAPI-generated, for two reasons recorded in
`docs/specs/phase-3-core-api.md` (Wire models, lines 54-60): `PropertyResponse` is a sealed interface with a
plain `kind` discriminator that springdoc flattens, so a generator loses the discriminated union; and the
`filter[x][y]` family is inexpressible in OpenAPI (`docs/architecture/query-port.md:341-348`). Reversibility: moderate.

### DR-21 Browse result order and id order
Context: `BrowsePort` promised a `(createdAt, id)` order but not how ids compare. PostgreSQL orders
`uuid` as unsigned bytes (checked on 18.6); `InMemoryBrowsePort` used the signed `UUID.compareTo`, so
the two disagreed on half of random id pairs, unseen because no contract test had a tie. Decision: ids,
and relationship values used as sort keys, compare in unsigned byte order of the 16-byte UUID
(lowercase canonical-string order). Explicit sorts tie-break by `id`; text search by relevance, then
`createdAt`, then `id`; the default is `createdAt`, then `id`. Missing values sort last ascending and
first descending. `createdAt` compares as stored, at the adapter's precision. `BrowseContract` pins each
tie-break with fixed ids. Alternatives: Java signed order (a non-native PostgreSQL `ORDER BY`); an
unspecified id order (the contract cannot pin ties, so the double and PostgreSQL can disagree); an order per adapter (the double stops
standing in for PostgreSQL). Consequences: PostgreSQL is unchanged; a keyword sort on the id string, as
an OpenSearch store would use, agrees; no decision is recorded for OpenSearch; facet bucket order is not
covered. Reversibility: easy.

### DR-22 Facet bucket order
Context: DR-21 left facet bucket order uncovered. The scalar facet `ORDER BY` had no tie-break, the
reference facet had no tie-break and no null-key filter, and the in-memory double sorted ties by Java
String order. Decision: buckets sort by count descending, then by bucket value in Unicode code point
order. PostgreSQL uses `COLLATE "C"` (UTF-8 byte order, which equals code point order). Null bucket keys
are excluded in both facets. The in-memory double compares by code point. Alternatives: (a) the database
collation, which orders mixed-case values differently from the double; (b) an id-based key, which does not
apply to scalar buckets and matches neither the double nor the label. Consequences: a target with no label
no longer makes browse throw; it adds no bucket. The scalar and reference facets agree; the double and
PostgreSQL agree for all code points. Reversibility: easy (query and test-double change only).

### DR-23 Relay dead-letter after three failed dispatches
Context: under B2 route 1 (resume plan spec 13, section 3), a row whose dispatch fails is left unprocessed
and retried, so a permanently failing row blocks every row behind it. `OutboxRelay` recorded this as an
accepted limitation with no dead-letter. `sq_outbox.attempts` is write-only, so it cannot count failures.
Decision: B2 route 1 changes from "fail and retry" to "three failed dispatches, then dead-letter". Three V7
columns (`fail_count`, `dead_lettered_at` and `error`) are added to `sq_outbox`. A failed dispatch rolls back its claim, then increments
`fail_count` in its own transaction; the third failure stamps `dead_lettered_at` and `error` in that
transaction. A payload that fails to decode counts as a failed dispatch, with the same threshold. The claim query skips dead-lettered rows, so the queue continues. A dead-lettered row keeps
`processed_at` NULL. Alternatives: skip-ahead, where the claim moves past a failing row (the row is silently
lost); a separate dead-letter table (the row leaves `sq_outbox`, so O-3's count no longer sees it); set
`processed_at` on failure (silently removes the row from the O-3 count).
Consequences: O-3's command still counts dead-lettered rows as unprocessed, so the kind-removal gate sees
them. The P2 to P4 plans must drain or resolve dead-lettered rows before their kinds leave the outbox. A
stamp mismatch under B2 that throws in dispatch now dead-letters after three tries instead of blocking the
queue. The main cost: during a systemic failure (for example, the Temporal server being down), the oldest
row fails on every poll, so a healthy row is dead-lettered every three polls. Nothing retries a
dead-lettered row; recovery is manual: reset `dead_lettered_at`, `fail_count` and `error`.
Reversibility: moderate (three V7 columns are added; the claim query changes).
