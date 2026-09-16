# Follow-up prompt: Phase 4

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 4 of Sequeless: derived properties computed on read.

CONTEXT
Sequeless is a Spring Boot web application for modelling and managing business objects whose types
are defined at runtime by an OWL ontology plus a custom `sq:` vocabulary. The core depends only on
sequeless-spi; it consumes a MetaModelSnapshot via OntologyPort (Jena adapter), stores instances
via ObjectStorePort and queries them via QueryPort (both PostgreSQL, one JSONB table). Ports have
default adapters chosen by sequeless.<port>.adapter and contract tests in sequeless-spi-testkit.
Decision DR-08: any type may declare derived properties with sq:derivedBy, either a declarative
sq:Rollup or a named sq:Plugin; computed on read in this phase, materialised later. Reference
domain: Projects, Tasks, People; Project gains derived openTaskCount and totalEstimatedHours.
Read docs/architecture and existing modules first.

Earlier phases delivered: module structure and boundary tests; ontology port with reasoner;
BREAD with validation on PostgreSQL; query port with filters, facets, sorting and text search.

OBJECTIVE
A Project's rollup properties are computed from its Tasks whenever the Project is read or browsed,
with a bounded number of queries per page, through the query port's aggregation capability and an
optional code plug-in.

CONSTRAINTS
- Vocabulary in sq-vocabulary.md: sq:derivedBy pointing to a blank node of type sq:Rollup with
  sq:function (sq:count, sq:sum, sq:min, sq:max, sq:avg), sq:over (source type), sq:via (the
  relationship on the source pointing back to the derived object), sq:of (source property for
  sum/min/max/avg), sq:filter (list of sq:Criterion with sq:property, sq:operator, sq:value); or
  of type sq:Plugin with sq:pluginName.
- SPI: DerivationRule filled in the snapshot; AggregateRequest (sourceType, via, targetIds,
  function, ofProperty, criteria) and AggregateResult (map of targetId to Value);
  QueryPort.aggregate; DerivationPlugin interface (name, derive(scope, snapshot, objects) returning
  values per object) discovered by ServiceLoader and registered by name.
- Core derivation planner: for a Read or a Browse page, group derived properties by rule, issue one
  aggregate per rule per page (not per object), and merge values into the returned objects.
  Derived properties are readOnly in the snapshot; Edit and Add reject them with 400.
- PostgreSQL aggregate: one GROUP BY query per request over sq_object filtered by type, via
  reference in targetIds, and criteria.
- The in-memory fallback for plug-ins must not load unbounded data; plug-ins receive the target
  objects and may call QueryPort themselves.

DELIVERABLES
- Vocabulary update and reference ontology update (Project derived properties, plus one sq:Plugin
  example such as Person.workload computed by a sample plug-in in the testkit).
- SPI records, DerivationRule mapping in the Jena adapter, QueryPort.aggregate, DerivationPlugin,
  AggregateContract in the testkit.
- PostgreSQL aggregate implementation passing the contract.
- Core derivation planner integrated in Read and Browse; write rejection for derived properties.
- REST unchanged in shape; type descriptors show derived properties as readOnly with their rule
  summary.
- A test asserting the number of aggregate queries for a browse page of 50 Projects.

ACCEPTANCE CRITERIA
- Reading a Project returns openTaskCount and totalEstimatedHours consistent with its Tasks, with
  no stored value in props.
- Browsing 50 Projects issues at most one aggregate query per derived rule (asserted by counting
  port calls or SQL statements).
- The sample plug-in is discovered by name from the ontology and executed.
- PUT with a derived property in the body returns 400 naming the property.
- Unknown pluginName in the ontology is reported at snapshot activation, not at read time.
- mvn -B verify passes.

OUT OF SCOPE
Materialised derived values, filtering or sorting on derived values, state machines, events.

PROCESS
Plan before coding: propose the sq:Rollup vocabulary shape, the AggregateRequest record and the
planner's batching strategy, and confirm with me before implementing. Ask via AskUserQuestion when
ambiguous.
```
