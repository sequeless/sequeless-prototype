# Follow-up prompt: Phase 1

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 1 of Sequeless: meta-model core, OntologyPort and the Jena adapter.

CONTEXT
Sequeless is a Spring Boot web application for modelling and managing business objects whose types
are defined at runtime by an OWL ontology. The ontology plus a custom `sq:` annotation vocabulary is
the single source of truth. The domain core (sequeless-core) never depends on Jena or Spring; it
consumes an immutable MetaModelSnapshot through OntologyPort, published in sequeless-spi. Instances
will live in PostgreSQL (later phase). Reference domain: Projects, Tasks, People; Task and Project
are subclasses of WorkItem; Task belongsToProject (inverse: hasTask); Task assignedTo Person.
Decisions in docs/architecture/02-decision-records.md apply, notably DR-03 (OWL behind a port) and
DR-05 (Jena built-in OWL reasoner over the type level only; no maintained OWL 2 DL reasoner exists
for Jena 6).

Phase 0 delivered: the multi-module build, sequeless-spi with Scope/TenantId/Principal/
AdapterDescriptor/SpiVersion/AuthorizationPort, the permit-all adapter, the testkit,
sequeless-app with /whoami, ArchUnit boundary tests, Docker Compose, CI. Read docs/architecture
and the existing modules first.

OBJECTIVE
The core can obtain a reasoned, immutable snapshot of the reference ontology through OntologyPort,
implemented by a Jena 6 adapter, and expose type descriptors with display hints over REST.

CONSTRAINTS
- No org.apache.jena type in sequeless-spi or sequeless-core (extend the ArchUnit rules).
- Snapshot types are records in sequeless-spi: MetaModelSnapshot, TypeDefinition (iri, label,
  superTypes, properties, displayHints, abstract flag), PropertyDefinition (iri, label, datatype
  or target type, cardinality min/max, facet flag, indexed flag, searchable flag, displayHints,
  readOnly flag, derivation placeholder, order), RelationshipDefinition (with inverse),
  Cardinality, Datatype enum, DisplayHints, StateMachineDefinition and DerivationRule as empty
  placeholders to be filled in later phases.
- Reasoner selectable by sequeless.ontology.reasoner=owl|rdfs|none, default owl, using Jena's
  built-in reasoners through jena-ontapi OntModel specifications. Reasoning applies to the ontology
  only.
- Ontology source in this phase: sequeless.ontology.source=classpath:... or file:... . Persistence
  of the ontology comes in Phase 2.
- Snapshot is cached; a reload endpoint rebuilds it. Snapshot construction must be deterministic
  (stable ordering).
- Stable IRIs: every type and property is identified by its full IRI; the REST layer may expose a
  short name derived from the ontology's declared prefix.

DELIVERABLES
- docs/architecture/sq-vocabulary.md: the `sq:` namespace (https://sequeless.dev/ns/meta#) with
  every annotation used in this phase (sq:label overrides, sq:displayOrder, sq:displayGroup,
  sq:facet, sq:indexed, sq:searchable, sq:hidden) and reserved names for later phases (sq:derivedBy,
  sq:StateMachine, sq:permission, sq:materialised).
- Reference ontology as Turtle in sequeless-spi-testkit resources, with owl:imports of the sq
  vocabulary file, plus a deliberately inconsistent variant and a variant exercising inverse and
  transitive properties.
- OntologyPort (snapshot, export, importDocument with ImportMode REPLACE for now) and
  OntologyContract in the testkit.
- sequeless-adapter-ontology-jena: parser, import resolution, reasoner selection, sq: vocabulary
  mapping, consistency check producing an OntologyReport with human-readable messages, Turtle
  export; auto-configuration and descriptor; passes OntologyContract.
- sequeless-core: MetaModelService use case (get snapshot, describe type, reload) consulting
  AuthorizationPort.
- sequeless-app: GET /types, GET /types/{name}, POST /types/reload; OpenAPI via springdoc.
- Tests: adapter unit tests, contract tests, app end-to-end test.

ACCEPTANCE CRITERIA
- The snapshot for the reference ontology shows Task with superTypes containing WorkItem,
  hasTask inferred as inverse of belongsToProject, and label and display hints populated.
- The inconsistent ontology is rejected with an OntologyReport that names the offending class.
- sequeless.ontology.reasoner=none yields a snapshot without inferred superTypes (proving the
  switch works).
- GET /types lists Project, Task, Person and WorkItem; GET /types/Task includes properties in
  display order with facet, indexed and searchable flags.
- ArchUnit fails if org.apache.jena appears in core or spi.
- mvn -B verify passes.

OUT OF SCOPE
Storing the ontology in a database, business object instances, SHACL validation, facets over
data, state machines, derivations.

PROCESS
Plan before coding: propose the snapshot record shapes and the sq: vocabulary mapping table, and
confirm with me before implementing. Ask via AskUserQuestion when a mapping choice is ambiguous
(for example how to represent OWL restrictions as cardinality). Verify Jena 6 ontapi API details
against the current documentation rather than memory.
```
