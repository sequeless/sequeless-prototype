# Follow-up prompt: Phase 3

Give the block below verbatim to an AI coding agent in a fresh session.


```
/beacon:plan Phase 3 of Sequeless: query, facets, text search and index hints.

CONTEXT
Sequeless is a Spring Boot web application for modelling and managing business objects whose types
are defined at runtime by an OWL ontology plus a custom `sq:` vocabulary. The core (sequeless-core)
depends only on sequeless-spi; it consumes a MetaModelSnapshot via OntologyPort (Jena adapter) and
stores instances via ObjectStorePort (PostgreSQL adapter, one JSONB table sq_object). Ports have
default adapters chosen by sequeless.<port>.adapter and must pass contract tests in
sequeless-spi-testkit. Properties carry sq:facet, sq:indexed and sq:searchable hints in the
snapshot. Reference domain: Projects, Tasks, People. Read docs/architecture and existing modules
first.

Earlier phases delivered: module structure and boundary tests; OntologyPort and Jena adapter with
reasoner; MetaModelSnapshot; ObjectStorePort and ValidationPort with PostgreSQL and SHACL adapters;
BREAD use cases and REST endpoints with paging only; ontology persisted in the database.

OBJECTIVE
Browse any type with filters, sorting, paging, facet counts and text search, with indexes created
from ontology hints, through a QueryPort whose shape stays implementable by non-SQL adapters.

CONSTRAINTS
- SPI records: Query (type, criteria, text, sorts, page, facetProperties, includeDeleted false),
  Criterion (property, Operator, value) with Operator in EQ, NE, IN, LT, LTE, GT, GTE, CONTAINS,
  STARTS_WITH, IS_NULL, NOT_NULL; Sort (property, direction); QueryResult (items, total, facets);
  FacetBucket (value, count). QueryPort.query(scope, snapshot, query). The port must not expose
  SQL or JSONB specifics.
- Criteria may target subtypes: querying WorkItem returns Tasks and Projects using the snapshot's
  hierarchy (the adapter receives the resolved set of concrete type IRIs from the core).
- PostgreSQL implementation: JSONB operators for criteria, ORDER BY on JSONB paths with datatype-
  aware casts, facet counts via GROUP BY over the filtered set (excluding the facet's own filter
  for the conventional multi-select behaviour), tsvector text search over sq:searchable properties
  (generated column or trigger maintained), expression indexes created idempotently on snapshot
  activation for sq:indexed properties, recorded in a sq_index_registry table.
- QueryContract in the testkit with a fixture dataset and expected counts.
- Core Browse use case builds the Query from the API parameters and validates property names and
  operators against the snapshot before calling the port.

DELIVERABLES
- SPI records and QueryPort; QueryContract.
- PostgreSQL QueryPort implementation, index management, text search; passes QueryContract.
- Core Browse use case extended; property and operator validation errors surfaced as 400.
- REST: GET /objects/{type} with filter[property][op]=value, q=, sort=, page=, size=, facets=
  parameters; response includes items, total, facets. OpenAPI updated.
- Documentation: docs/architecture/query-port.md describing the query model and how a non-SQL
  adapter would implement it.

ACCEPTANCE CRITERIA
- QueryContract passes on PostgreSQL.
- Facet counts for Task by status and by assignedTo match the fixture, including reference facets
  labelled with the referenced object's display label.
- Filtering on a reference property and on a date range works; sorting on a numeric property is
  numeric, not lexical.
- An integration test runs EXPLAIN for an indexed property filter and asserts the expression
  index is used.
- Text search for a word in a Task description returns that Task ranked first.
- Querying WorkItem returns both Tasks and Projects.
- mvn -B verify passes.

OUT OF SCOPE
Aggregations over related objects, derived properties, materialised values, external search
engines, state machines.

PROCESS
Plan before coding: propose the Query record shapes, the filter query-parameter grammar, and the
facet counting semantics, and confirm with me before implementing. Ask via AskUserQuestion when
ambiguous.
```
