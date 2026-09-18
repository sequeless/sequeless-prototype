# The QueryPort


## In plain language

`ObjectStorePort` (DR-04) commits and reads one object at a time. Listing a type — with filters,
sorting, facet counts, and free-text search, across an abstract type's concrete subtypes — is a
different access pattern with its own indexing and query-planning concerns, so it is its own port:
`QueryPort`. `01-overview.md`'s port catalogue lists it as "Filtered, sorted, paged browse; facet
counts; text search; aggregations for rollups," backed by `postgres` by default, with OpenSearch or
a SPARQL endpoint as plausible alternative adapters. DR-13 decided full-text search would live
inside the default query adapter (`tsvector` over `sq:searchable` properties) rather than behind a
separate search-index port from day one, with a dedicated search adapter only "if facet queries
prove too slow" — this document describes the shape that decision was built against, and the last
section sketches what a future OpenSearch adapter would look like without changing that shape.

Everything below reflects the shipped Phase 3 implementation: the SPI records in
`sequeless-spi/src/main/java/org/sequeless/spi/query`, the core browse use case in
`sequeless-core/src/main/java/org/sequeless/core/usecase/DefaultBusinessObjectService.java`, the
REST layer in `sequeless-app/src/main/java/org/sequeless/app/rest/ObjectsController.java`, and the
default adapter in
`sequeless-adapter-persistence-postgres/src/main/java/org/sequeless/adapter/persistence/postgres/PostgresQueryStore.java`.

## Shapes

`org.sequeless.spi.query` (module `sequeless-spi`):

```java
public record Query(
    Set<String> types,              // resolved, concrete-only type IRIs
    List<Criterion> criteria,       // ANDed
    Optional<String> text,          // free-text search term
    List<Sort> sorts,               // applied in list order
    Page page,
    List<String> facetProperties,   // property IRIs, must be sq:facet=true
    boolean includeDeleted) {}      // core always passes false in phase 3

public record Criterion(String property, Operator operator, Optional<Value> value) {}

public enum Operator { EQ, NE, IN, LT, LTE, GT, GTE, CONTAINS, STARTS_WITH, IS_NULL, NOT_NULL }

public record Sort(String property, Direction direction) {}
public enum Direction { ASC, DESC }

public record QueryResult(List<BusinessObject> items, long total, Map<String, List<FacetBucket>> facets) {}
public record FacetBucket(String value, long count) {}

public interface QueryPort {
    QueryResult query(Scope scope, MetaModelSnapshot snapshot, Query query);
    void ensureIndexes(Scope scope, MetaModelSnapshot snapshot);
}
```

`Criterion` deliberately reuses `org.sequeless.spi.object.Value` — the same sealed value type
`BusinessObject` properties already use (`TextValue`, `IntegerValue`, `DecimalValue`, `BoolValue`,
`DateValue`, `DateTimeValue`, `ReferenceValue`, `ListValue`, built via `Value.text(...)`,
`Value.integer(...)`, ... `Value.list(...)`) — rather than inventing a parallel query-value type.
The shape of `Criterion.value` depends on `operator`:

- `IS_NULL` / `NOT_NULL`: `value` is `Optional.empty()` — neither operator compares against
  anything.
- `IN`: `value` is present and wraps a `ListValue` of candidate scalar values.
- every other operator (`EQ`, `NE`, `LT`, `LTE`, `GT`, `GTE`, `CONTAINS`, `STARTS_WITH`): `value` is
  present and wraps a scalar `Value` matching the property's datatype.

`Criterion`'s own javadoc is explicit that it does *not* enforce this coherence itself (it does not
check that an `IN` criterion's value really is a `ListValue`, or that `value` is empty for
`IS_NULL`) — that check is core's job, performed before a `Criterion` is ever handed to a
`QueryPort`. Likewise `Operator`'s javadoc notes which datatypes each constant is "typically
meaningful for" only informationally; operator/datatype coherence is enforced by core (see
"REST filter grammar" below), not by the SPI types.

`Query.types()` is a **resolved, concrete-only** set of type IRIs, not a single type reference and
not something the port resolves itself. When a request names an abstract type (`WorkItem` in the
reference domain), core expands it to its concrete subtypes (`Task`, `Project`) using the
`MetaModelSnapshot`'s type hierarchy *before* constructing the `Query` — the port never sees an
abstract type IRI and never does hierarchy reasoning. `QueryPort`'s interface-level javadoc is
explicit that `types()` is a union: a matching object's type is any one of the listed IRIs, not
every one at once — the same union semantics `ObjectStorePort#browse`'s `Set<TypeRef>` parameter
already has.

`QueryResult.facets()` is keyed by property **IRI**, matching the IRIs in the originating
`Query.facetProperties()` — the REST layer re-keys this to short names (see below).

## REST filter grammar

```
GET /objects/{type}?filter[property][op]=value&q=...&sort=...&facets=...&page=&size=
```

Implemented by `ObjectsController#browse`. `filter[<property>][<op>]=<value>` entries are picked out
of the full `MultiValueMap<String,String>` of query parameters (bound as `allParams`, since Spring's
native bracket-map binding is too fragile to rely on for this) with the regex:

```java
private static final Pattern FILTER_KEY = Pattern.compile("filter\\[([^\\]]+)\\]\\[([^\\]]+)\\]");
```

| REST `<op>` token | `Operator` constant |
|---|---|
| `eq` | `EQ` |
| `ne` | `NE` |
| `in` | `IN` |
| `lt` | `LT` |
| `lte` | `LTE` |
| `gt` | `GT` |
| `gte` | `GTE` |
| `contains` | `CONTAINS` |
| `startswith` | `STARTS_WITH` |
| `isnull` | `IS_NULL` |
| `notnull` | `NOT_NULL` |

`<op>` tokens are matched case-insensitively by the use case that parses them
(`DefaultBusinessObjectService.parseOperatorToken`), not by the controller regex itself, which only
splits the key into a property and a raw token. `<property>` is a short name or full IRI, resolved
against the browsed type's (and its subtypes') properties.

- `in` takes a single comma-separated value (`filter[status][in]=Open,InProgress`), split and
  trimmed by the use case; blank tokens are rejected.
- `isnull` / `notnull` ignore any value part — `filter[assignedTo][isnull]=anything` behaves
  identically to `filter[assignedTo][isnull]=`.
- Multiple filters on **different** properties AND together (the normal case).
- Multiple filters on the **same** property also AND together — this is how range queries are
  expressed, e.g. `dueDate[gte]=2026-01-01&dueDate[lte]=2026-03-31`. There is no OR combinator.
- `sort=<property>` or `sort=-<property>` — a leading `-` means descending — comma-separated for
  multiple keys, e.g. `sort=-priority,dueDate` (descending priority, then ascending due date,
  applied in that order).
- `facets=<property1>,<property2>` — comma-separated short names (or IRIs).
- `page`/`size` are unchanged from the existing paging convention: 0-based, default `0`/`20`, size
  silently capped at `200` (`Math.min(size, 200)`, never rejected outright).

An invalid filter/sort/facet property or operator — an unknown property, an operator not applicable
to the property's datatype (e.g. `contains` on an integer property, `gt` on a boolean), a facet
property that isn't `sq:facet=true`, or a sort/filter property that is multi-valued — raises
`InvalidQueryException`, carrying one `Violation` per problem, mapped by `ApiExceptionAdvice` to a
400 `ProblemDetail`.

### Response shape

```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "totalItems": 137,
  "totalPages": 7,
  "facets": {
    "status": [ { "value": "Open", "count": 42 }, { "value": "Done", "count": 95 } ]
  }
}
```

`QueryResultResponse(items, page, size, totalItems, totalPages, facets)`. `facets` is
`Map<String, List<FacetBucketResponse(value, count)>>`, keyed by property **short name** — note this
is a translation, not a passthrough: the SPI's `QueryResult.facets()` is keyed by property IRI, but
every other REST-facing shape (object properties, filter/sort/facet request parameters) already uses
short names as its wire convention, so `FacetResponseMapper.toShortNameKeyed` re-keys the map for
consistency at the REST boundary before it ever reaches the client. The mapper resolves each facet
IRI against the *browsed type's own* `properties()` list, falling back to the IRI's own local name
(the same rule `MetaModelSnapshot` uses for short names generally) when a facet property isn't found
there — because `DefaultBusinessObjectService.browse` validates facet/filter/sort properties against
a wider set than `requestType.properties()` (every property attributed to the resolved type *or any
of its transitive subtypes*, via `TypeHierarchy.typeAndSubtypes`), so a facet declared only on a
concrete subtype (not on an abstract type being browsed) can validly appear in `QueryResult.facets()`
without appearing in `requestType.properties()`. Without the fallback, such a facet's key would be
silently dropped from the response.

## Facet counting: conventional multi-select

`QueryPort`'s interface-level javadoc states the rule precisely:

> For a facet property `P` in `query.facetProperties()`, the bucket counts returned for `P` are
> computed against every criterion in `query.criteria()` *except* any criterion whose `property` is
> `P` itself.

In other words: selecting one bucket of a facet (by adding a matching `Criterion` for that property)
does not zero out the counts of that facet's sibling buckets — only *other* facets are narrowed by
the selection. `PostgresQueryStore.buildWhere` implements this directly via an `excludingProperty`
parameter that skips any criterion on the property currently being faceted.

Worked example, using the reference domain: browsing `Task` with `filter[priority][gte]=3` and
`facets=status`. The main query (and `totalItems`) reflects both the priority filter and whatever
`status` filter is also present, if any. But the `status` facet's own bucket counts are computed
with the priority filter still applied and *any existing `status` filter dropped* — so if the
request also carries `filter[status][eq]=Open`, the `status` facet still shows non-zero counts for
`InProgress` and `Done` (how many high-priority tasks are in each other status), not just `Open`.
Only the `priority` filter narrows every facet uniformly, since nothing is faceting on `priority`
here.

Reference-typed facets (e.g. `assignedTo`, a `Person` reference on `Task`) resolve to the target
object's `sq:displayLabel`-marked property value rather than a raw id — `FacetBucket`'s own javadoc
is explicit that `value` "is always a display string, never a raw identifier." `sq:displayLabel`
(`docs/architecture/sq-vocabulary.md`) is a boolean annotation marking a datatype property as its
owning type's display label; in the reference ontology it is set on `Person.name` and
`WorkItem.title`, so both relationship targets in the domain (`Person` via `assignedTo`,
`Project`/`Task` via any work-item reference) resolve to readable text. `computeReferenceFacet`
joins `sq_object` to itself on the reference's `::uuid` cast and groups by the target type's
`sq:displayLabel` property expression, falling back to the target id as text
(`target.id::text`) if the target type declares no `sq:displayLabel` property — which the reference
domain never does, after this vocabulary addition.

## Text search

`q=<text>` matches against every property marked `sq:searchable=true` on the browsed type (in the
reference domain, `WorkItem.title` and `WorkItem.description`, inherited by `Task` and `Project`).

Rather than a single `GENERATED ALWAYS AS` `tsvector` column, `sq_object` carries a plain
`search_vector tsvector` column maintained by a `BEFORE INSERT OR UPDATE` trigger
(`sq_object_update_search_vector`, `V2__query_indexes_and_search.sql`). A generated column's
expression is fixed at DDL time; it cannot depend on a dynamic, ontology-driven set of searchable
properties that changes as the ontology is re-imported. The trigger instead reads a registry table,
`sq_searchable_property (tenant_id, property_iri)`, and recomputes `search_vector` by concatenating
the `'text'`-tagged value of every registered property for the row's tenant:

```sql
SELECT string_agg(NEW.props -> sp.property_iri ->> 'text', ' ')
INTO combined
FROM sq_searchable_property sp
WHERE sp.tenant_id = NEW.tenant_id;

NEW.search_vector := to_tsvector('simple', COALESCE(combined, ''));
```

Querying does `search_vector @@ plainto_tsquery('simple', :searchText)` (the `'simple'` text search
configuration — no stemming/stopword removal — used consistently by the trigger, the query, and the
backfill). When `query.sorts()` is empty and `query.text()` is present, results are ordered by
`ts_rank(search_vector, plainto_tsquery('simple', :searchText)) DESC`, then `created_at`, then `id`
as a stable tiebreak (`PostgresQueryStore.buildOrderBy`) — see "Default ordering" below for how this
interacts with `QueryPort`'s own default-order contract.

## `ensureIndexes` and the index registry

`ensureIndexes(scope, snapshot)` does two things, both idempotent, both driven by hint flags already
on the `MetaModelSnapshot`'s properties:

- For every property with `sq:indexed=true` not already present in `sq_index_registry`
  `(tenant_id, property_iri, index_name, created_at)` (PK `(tenant_id, property_iri)`): runs
  `CREATE INDEX IF NOT EXISTS <name> ON sq_object ((<cast-expression>))` using the same
  datatype-aware cast expression `query`'s own `WHERE`/`ORDER BY` clauses use (`castExpressionLiteral`,
  a DDL-time twin of `castExpression` with the property IRI embedded as an escaped literal instead of
  a bind parameter, since `CREATE INDEX` cannot take bind parameters), then registers the row.
  Index names are deterministic hashes — `"sq_idx_"` plus 16 hex characters of an MD5 digest of
  `tenantId + "|" + propertyIri` — chosen to stay well under PostgreSQL's 63-byte identifier limit
  regardless of how long the property IRI is.
- For every property with `sq:searchable=true` not already present in `sq_searchable_property`:
  inserts it, and if any new searchable property was registered, backfills `search_vector` for every
  existing row of the tenant (a statement kept textually in sync with the trigger's own logic — see
  the comment on `PostgresQueryStore.backfillSearchVector`).

Both loops skip already-registered properties, so a repeat call with an unchanged snapshot is a
no-op beyond the registry lookups, and a snapshot with no `sq:indexed`/`sq:searchable` properties at
all does nothing.

`ensureIndexes` is called from `DefaultOntologyAdministration.importTurtle`, right after a
successful `OntologyPort.importDocument` call, using the freshly-reloaded snapshot
(`ontologyPort.snapshot(scope)`). `importDocument`'s own contract is to either return an accepted
`ImportReport` or throw, so reaching the `ensureIndexes` call always means the import succeeded.

**Operational note — a real gap, not covered by this phase.** The ontology loaded at application
startup via the `sequeless.ontology.source` classpath/file source does **not** go through
`DefaultOntologyAdministration.importTurtle`, and therefore never triggers `ensureIndexes` on its
own. Only an explicit `POST /ontology` import does. An operator who only ever boots the application
with a static ontology and never calls the import endpoint will find text search and
indexed-property filtering silently underperforming (sequential scan instead of an expression index)
or, for text search, returning nothing at all — `search_vector` is never populated for rows inserted
before any property was registered searchable, and the column simply stays `NULL`/empty. This is not
addressed anywhere in Phase 3's scope; it is worth flagging for a future phase or a deployment
runbook (e.g. an explicit "prime indexes on startup" hook, or having the startup ontology loader call
`ensureIndexes` too).

## Default ordering

When `query.sorts()` is empty:

- and `query.text()` is absent — ascending by `created_at`, then `id` (a stable tiebreak). This
  matches `ObjectStorePort#browse`'s own documented default order, and `QueryPort`'s own
  interface-level javadoc states it unconditionally as "the" default order.
- and `query.text()` is present — descending by `ts_rank(search_vector, plainto_tsquery('simple',
  :searchText))`, then ascending by `created_at`, then `id`. This is what `PostgresQueryStore`
  actually implements and what the `QueryContract` test suite's `textSearchRanksBestMatchFirst`
  exercises (it asserts the top-ranked item is the expected best match, though it does not pin down
  full ordering beyond that). Note this is a narrower, more specific rule than `QueryPort`'s
  interface-level javadoc literally states — that javadoc's "ascending `(createdAt, id)`" default is
  written without an explicit carve-out for active text search. Treat the rule above (explicit sort
  wins; otherwise text search ranks by relevance; otherwise `(createdAt, id)` ascending) as the real
  contract a conforming adapter should follow, since it is what the shipped adapter and its contract
  tests actually establish.

Any explicit `Sort` in `query.sorts()` always wins over both defaults; `id` is still appended as a
final, stable tiebreak after every explicit sort key.

## A non-SQL adapter sketch

DR-13 names OpenSearch as the plausible alternative if Postgres facet/text-search performance ever
becomes a bottleneck. Nothing about `QueryPort`'s shape is Postgres-specific, so an OpenSearch-backed
implementation could sit behind the exact same interface without `sequeless-core` — or any use case
calling `BusinessObjectService.browse` — ever knowing the difference:

- **`query(scope, snapshot, query)`**: translate `query.criteria()` into a `bool` query's `filter`
  clauses — `EQ`/`IN` as `term`/`terms`, `LT`/`LTE`/`GT`/`GTE` as `range`, `CONTAINS`/`STARTS_WITH`
  as `wildcard`/`match_phrase_prefix`, `IS_NULL`/`NOT_NULL` as `must_not exists`/`exists`. Translate
  `query.text()` into a `match` query over a tenant- and type-scoped set of the type's
  `sq:searchable` fields, with OpenSearch's own relevance score standing in for `ts_rank`. Translate
  `query.facetProperties()` into `terms` aggregations, one per requested facet, each built the same
  "conventional multi-select" way the Postgres adapter builds its per-facet `WHERE` clause: the
  aggregation for facet `P` runs inside a `filter` aggregation (or a sibling query) that includes
  every criterion in `query.criteria()` except any on `P` itself — the same exclusion rule, just
  expressed as an aggregation-level filter instead of a SQL `WHERE`. A reference-typed facet still
  needs the *target* object's `sq:displayLabel` value as its bucket key, which OpenSearch cannot join
  for at query time the way a self-join can in Postgres — the practical answer is to denormalize the
  target's display-label value onto the referencing document at index time (updated whenever the
  target's label-bearing property changes), rather than resolving it per-query.
- **`ensureIndexes(scope, snapshot)`**: rather than one Postgres expression index per `sq:indexed`
  property, create or update an OpenSearch index template/mapping keyed off the snapshot's types and
  properties — mapping each `sq:indexed` property to an appropriately-typed, indexed field (`keyword`
  for exact-match/facet-friendly strings, `date`, numeric types, etc.), and concatenating every
  `sq:searchable` property into a `text` field analyzed for full-text search (OpenSearch's analogue
  of `search_vector`). Idempotence here means checking the current mapping before attempting to add
  a field OpenSearch would otherwise reject as a conflicting redefinition, and reindexing existing
  documents when a property's mapping needs to change in an incompatible way (OpenSearch, unlike
  Postgres's `ALTER TABLE ADD COLUMN`, cannot always evolve a mapping in place).

Because `Query`/`QueryResult`/`Criterion`/`FacetBucket` never mention SQL, JSONB, or any
Postgres-specific concept, this OpenSearch adapter is a genuine drop-in: swapping
`sequeless.query.adapter=postgres` for `sequeless.query.adapter=opensearch` (once such an adapter
module exists) changes nothing above the port boundary. This is the proof-of-concept the port's
shape was designed to satisfy, not just an aspiration.

## Known limitations (phase 3 scope)

- **Multi-valued properties are out of scope for filter/sort/facet/index.** A property with
  cardinality `max > 1` (or unbounded) cannot be filtered, sorted, faceted, or indexed in this phase.
  The reference domain's only such property, `hasTask` (`Project` → `Task`, unbounded), is never
  filtered/sorted/faceted by any Phase 3 acceptance criterion, and `DefaultBusinessObjectService`'s
  property resolution explicitly rejects a multi-valued property used in a filter/sort/facet
  position. This may be revisited in a later phase (e.g. `CONTAINS`-style "any element matches"
  semantics over an array-valued extraction).
- **The `ensureIndexes`-on-startup gap** described above: a statically-loaded startup ontology never
  primes indexes or the searchable-property registry on its own; only an explicit `POST /ontology`
  import does.
- **OpenAPI schema quality for the bracket-map filter parameter is inherently degraded.** The
  `filter[<property>][<op>]=<value>` parameters are bound as a single raw `MultiValueMap<String,
  String>` (`allParams` in `ObjectsController#browse`), which the OpenAPI generator can only render
  as an untyped object — OpenAPI's parameter model has no native way to express a bracket-map
  parameter family like `filter[x][y]`. The grammar is instead documented in the parameter's
  `@Parameter(description = ...)` text. This is an accepted trade-off, not a bug: modeling
  `filter[x][y]` faithfully in OpenAPI 3.x would require a non-standard extension or an unreadably
  generic `additionalProperties` schema either way.
