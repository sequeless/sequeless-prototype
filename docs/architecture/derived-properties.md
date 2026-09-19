# Derived properties

## In plain language

DR-08 says a type may declare properties that are *computed* rather than *stored*: a declarative
roll-up over a related type (`sq:Rollup`), or a named plug-in implemented in code (`sq:Plugin`).
Every property a caller saw before this phase was a stored property — `ValueCoercer` coerced it in,
`PostgresObjectStore` wrote it to `sq_object.props`, and reads handed it straight back. A derived
property never goes through that path in either direction: it is computed fresh on every `GET
/objects/{type}/{id}` and every page of `GET /objects/{type}`, and a write naming it is rejected
before it ever reaches the store.

The vocabulary terms this phase adds — `sq:derivedBy`, `sq:Rollup`, `sq:Plugin`, `sq:Criterion`,
`sq:function`, `sq:over`, `sq:via`, `sq:of`, `sq:filter`, `sq:property`, `sq:operator`, `sq:value`,
`sq:pluginName`, plus the five function individuals (`sq:count sq:sum sq:min sq:max sq:avg`) and
the eleven operator individuals mirroring `org.sequeless.spi.query.Operator` — are catalogued in
`docs/architecture/sq-vocabulary.md`'s "Terms in use (phase 4)" table; this document describes the
shapes and code paths built on top of them, following the same plain-language-then-shapes structure
as `docs/architecture/query-port.md`.

Outcome: reading or browsing a `Project` returns `openTaskCount` and `totalEstimatedHours` computed
from its `Task`s, with nothing stored in `props`; a sample plug-in computes `Person.workload`;
writing a derived property is a 400 naming it; an unknown plug-in name fails at ontology activation,
including application startup.

Out of scope (unchanged from the plan): materialised derived values (`sq:materialised` stays
reserved), filtering or sorting on derived values, state machines, events.

## SPI shapes

`org.sequeless.spi.meta`:

```java
public sealed interface DerivationRule permits RollupRule, PluginRule {}

public record RollupRule(
    String sourceTypeIri,          // sq:over
    String viaIri,                 // sq:via — relationship on the source pointing back
    AggregateFunction function,    // sq:function
    Optional<String> ofPropertyIri,// sq:of
    List<Criterion> criteria)      // sq:filter, already resolved to SPI criteria
    implements DerivationRule {}

public record PluginRule(String pluginName) implements DerivationRule {}

public enum AggregateFunction { COUNT, SUM, MIN, MAX, AVG }
```

`PropertyDefinition.derivation()` is `Optional<DerivationRule>` — unchanged in shape from the
placeholder that existed before this phase, now actually populated. A property carrying a
derivation is forced `readOnly = true` by `SnapshotMapper` regardless of any `sq:readOnly`
assertion, so `ValueCoercer`'s existing read-only rejection path covers writes (see "Write
rejection" below for the more specific message a derived property gets).

`org.sequeless.spi.query` gains an aggregation capability on `QueryPort`:

```java
public record AggregateRequest(
    Set<String> sourceTypes,       // the rule's sq:over plus its concrete subtypes
    String viaIri,
    Set<ObjectId> targetIds,
    AggregateFunction function,
    Optional<String> ofPropertyIri,
    List<Criterion> criteria) {}

public record AggregateResult(Map<ObjectId, Value> values) {}

// QueryPort gains:
AggregateResult aggregate(Scope scope, MetaModelSnapshot snapshot, AggregateRequest request);
```

`AggregateRequest` carries `Set<String> sourceTypes` rather than a single `sourceType` — subtype
expansion is core's job (`TypeHierarchy.concreteTypeAndSubtypes`, the same helper `browse` already
uses), so the port never does hierarchy reasoning. `Criterion` is the same record `QueryPort.query`
already uses; a rule's `sq:filter` criteria are resolved to SPI `Criterion`s once, at ontology
mapping time, coerced against the criterion property's own declared range.

Density contract (`QueryPort.aggregate`'s interface-level javadoc, honoured by both
`PostgresQueryStore` and every `FakeQueryPort` test double): `COUNT` is dense and zero-filled for
every requested target id; `SUM`/`MIN`/`MAX`/`AVG` are simply absent from the result map for a
target with no matching source rows, so "no tasks" stays distinguishable from "tasks totalling
zero."

`org.sequeless.spi.derivation` is the plug-in SPI:

```java
public interface DerivationPlugin {
    String name();
    Map<ObjectId, Value> derive(DerivationContext context, List<BusinessObject> objects);
}
public record DerivationContext(Scope scope, MetaModelSnapshot snapshot, QueryPort queryPort) {}
```

`DerivationPlugin` is the direct `ServiceLoader` service type — no wrapper interface. A plug-in is
discovered by `META-INF/services/org.sequeless.spi.derivation.DerivationPlugin` and dispatched to
by `sq:pluginName`. `DerivationContext` gives a plug-in `QueryPort` access precisely so it can
follow the same "one request per rule per page" discipline a declarative `sq:Rollup` gets for free
— see `WorkloadDerivationPlugin` below.

## Vocabulary shape (reference ontology)

```turtle
ex:openTaskCount
    a owl:DatatypeProperty ;
    rdfs:domain ex:Project ;
    rdfs:range xsd:integer ;
    sq:derivedBy [
        a sq:Rollup ;
        sq:function sq:count ;
        sq:over ex:Task ;
        sq:via ex:belongsToProject ;
        sq:filter ( [ a sq:Criterion ;
                      sq:property ex:status ;
                      sq:operator sq:ne ;
                      sq:value "done" ] ) ] .

ex:totalEstimatedHours
    a owl:DatatypeProperty ;
    rdfs:domain ex:Project ;
    rdfs:range xsd:decimal ;
    sq:derivedBy [
        a sq:Rollup ;
        sq:function sq:sum ;
        sq:over ex:Task ;
        sq:via ex:belongsToProject ;
        sq:of ex:estimatedHours ] .
```

Both carry an `owl:maxCardinality 1` restriction on `ex:Project` so they render as scalars, the same
way `ex:status` does on `ex:WorkItem`. `ex:estimatedHours` itself gained `owl:maxCardinality 1` on
`ex:Task` in this phase specifically so the Postgres `sum()` below can extract it directly as a bare
tagged JSONB value (`{"decimal": n}`) rather than unnesting a JSON list.

The plug-in form, shipped only in the testkit's `reference-plugin.ttl` fixture (the production
`sequeless-app/.../ontology/reference.ttl` deliberately carries no `sq:Plugin`, since the sample
plug-in lives in `sequeless-spi-testkit`, a test-scope dependency — referencing it from the
production ontology would fail `spring-boot:run` at startup by design):

```turtle
ex:workload
    a owl:DatatypeProperty ;
    rdfs:domain ex:Person ;
    rdfs:range xsd:decimal ;
    sq:derivedBy [ a sq:Plugin ; sq:pluginName "workload" ] .
```

Rule-shape errors (unknown function, missing `sq:over`/`sq:via`, `sq:of` missing for
`sum`/`min`/`max`/`avg` or present for `count`, `sq:via` not a relationship on the source type, a
malformed `sq:filter` criterion, an unresolvable `sq:pluginName`, or both `sq:Rollup` and
`sq:Plugin` on the same node) are ERROR `OntologyIssue`s named on the derived property's own IRI,
merged into `MappingResult.issues` (renamed from `warnings` in this phase, and now able to carry
`ERROR` severity) exactly like every other activation error — unknown imports, reserved terms, and
so on. An unresolved `sq:pluginName` fails with exactly:

```
sq:pluginName 'NAME' has no registered DerivationPlugin on the classpath.
```

This check runs in `JenaOntologyPort.buildStateFrom` — a fresh `ServiceLoader` lookup on every
call, not cached on the port — so it fires on all three activation paths with no new wiring:
startup (`OntologyStartupValidator`'s eager `ApplicationRunner` call), `reload()`, and
`importDocument`. Because `sq:derivedBy` no longer asserts `rdfs:range sq:Rollup` (a leftover
assertion that, under the app's `reasoner: owl` setting, entailed `rdf:type sq:Rollup` onto *every*
derivation node — including `sq:Plugin` nodes — tripping the mutual-exclusion check), a plug-in
form activates cleanly under the real reasoner configuration, not just under `reasoner: none`.

## Planner batching strategy

`org.sequeless.core.derivation.DerivationPlanner(QueryPort, DerivationPluginRegistry)`:

```java
List<BusinessObject> apply(Scope, MetaModelSnapshot, TypeDefinition requestType, List<BusinessObject> objects)
```

`read` calls this with a singleton list, so it shares exactly the same code path and the same bound
as `browse`. The steps:

1. Collect every derived property declared on the resolved type of each object in the page — a
   browse page may mix concrete subtypes, so each object's own type (which already includes every
   inherited property) is walked, not a single shared type.
2. **Group by rule, not by property or object.** `RollupRule`/`PluginRule` are records, so two
   properties (possibly on different types) carrying an identical rule collapse into one
   `AggregateRequest` (or one plug-in call); the result is fanned back out to every property IRI
   that shares the rule. `targetIds` is every object whose type declares that property — so a page
   of 50 `Project`s issues exactly two aggregates (one `count`, one `sum`), never 50 or 100. A core
   unit test pins this bound directly against a counting `QueryPort` double.
3. `PluginRule`s are grouped by plug-in name and each plug-in is called once per page with all of
   its target objects, via `DerivationPluginRegistry.fromServiceLoader()` — an eager, built-once
   `ServiceLoader` factory; `.get(name)` throws `IllegalStateException` for an unregistered name,
   which should be unreachable in practice since T4's activation-time check already rejects an
   ontology naming an unregistered plug-in before any object is ever read.
4. Merge into a copy of each object's property map: any stored value ever found under a derived
   property's IRI is dropped first (defensive — nothing should ever write one, but this makes the
   guarantee unconditional rather than incidental), `COUNT` defaults to `0` for every target, and
   other functions are simply omitted when the aggregate returned no value for that target.

`DefaultBusinessObjectService` gained a 7-arg constructor taking the planner directly; the existing
6-arg constructor delegates to it with a `ServiceLoader`-backed planner, so `CoreConfiguration` and
every pre-existing caller are untouched.

`WorkloadDerivationPlugin` (`sequeless-spi-testkit`, `org.sequeless.testkit.derivation`) is written
to demonstrate the pattern a real plug-in should follow: rather than loading every `Task` assigned
to any target `Person` into memory and summing in Java, it issues one `QueryPort.aggregate` call
(`SUM` of `estimatedHours` over `Task` via `assignedTo`) for the whole batch of target ids at once.
A plug-in that queried once per object, or pulled an unbounded working set into memory, would defeat
the entire point of routing plug-ins through `DerivationContext.queryPort()` rather than ad hoc
storage access — plug-ins must self-bound their own `QueryPort` usage the same way the planner
self-bounds its own aggregate calls; nothing enforces this for a plug-in author.

## PostgreSQL aggregate SQL shape

One `GROUP BY` per `AggregateRequest`, reusing `PostgresQueryStore`'s existing
`castExpression(...)`/`criterionSql(...)` helpers (the same ones `query()` already uses to resolve
property IRIs into JSONB extraction expressions) and its existing `indexProperties(snapshot,
sourceTypes)` helper to resolve `PropertyDefinition`s for `ofPropertyIri` and every criterion
property — no new snapshot-walking method was needed:

```sql
SELECT (props -> :via ->> 'ref')::uuid AS target_id, count(*)        -- or sum/min/max/avg of the
FROM sq_object                                                       -- :of cast expression
WHERE tenant_id = :tenantId
  AND type_iri IN (:sourceTypes)
  AND deleted_at IS NULL
  AND (props -> :via ->> 'ref')::uuid IN (:targetIds)
  AND <criteria>
GROUP BY 1
```

`COUNT` returns `IntegerValue`; `SUM`/`MIN`/`MAX`/`AVG` return a `Value` matching the `sq:of`
property's own datatype. The `WHERE` clause filters only on the *source* row's own `tenant_id` and
never joins to or inspects the target's tenant, so there is no cross-tenant leak path via the `via`
reference even though a source row can (in principle) point at a target in another tenant. No new
migration was needed: `sq_object_props_gin_idx` and the per-property indexes `ensureIndexes`
already creates cover this access pattern the same way they cover `query()`'s own `WHERE`/`ORDER
BY` clauses.

Postgres's planner chooses between `GroupAggregate` and `HashAggregate` as a cost decision depending
on data volume — the contract test's `EXPLAIN`-based bounded-query proof accepts either, rather than
pinning one. `AVG(numeric)` returns a `NUMERIC` with expanded scale beyond either input's own scale;
any test comparing an `AVG` result compares numerically (`compareTo`/`isEqualByComparingTo`), never
by `DecimalValue` object equality.

## REST surface

`GET /types/{name}` — `TypeResponseMapper` appends a `derivation` field as the last entry on each
attribute/relationship property, `null` for an ordinary property or a `{"kind": ..., "summary":
...}` object for a derived one:

```json
{
  "name": "openTaskCount",
  "iri": "https://sequeless.dev/ns/ref#openTaskCount",
  "readOnly": true,
  "derivation": { "kind": "rollup", "summary": "count(Task via belongsToProject where status ne 'done')" }
}
```

Exact summary strings, verified against the real reference ontology:

- `Project.openTaskCount` → `count(Task via belongsToProject where status ne 'done')`
- `Project.totalEstimatedHours` → `sum(Task via belongsToProject of estimatedHours)`
- a plug-in-backed property → `plugin(<pluginName>)`, e.g. `plugin(workload)`

The criterion operator token inside a rollup summary uses the same mapping REST filters use
(`TypeResponseMapper.OPERATOR_TOKENS`, confirmed identical to
`DefaultBusinessObjectService.parseOperatorToken`): `eq ne in lt lte gt gte contains startswith
isnull notnull`. `readOnly` required no mapper change beyond the existing pass-through, since a
derived property's `PropertyDefinition.readOnly()` was already forced `true` at the snapshot level.

`GET /objects/{type}/{id}` and `GET /objects/{type}` (browse) — object payload shape is unchanged:
merged derived values flow through `ObjectPropertyMapper` like any other property, keyed by short
name, so a client cannot tell from the object payload alone whether a value was stored or computed.
Nothing is ever present in `props` for a derived property's IRI; browsing or reading the same
`Project` twice recomputes both roll-ups from the current state of its `Task`s each time.

`POST`/`PUT /objects/{type}` — naming a derived property in the request body is rejected with a 400
`ProblemDetail`, `source: "structural"` (the same `ValidationException.Source` a plain
`readOnly=true` violation carries, since `ValueCoercer.coerce` is what raises this, not
`StructuralValidator`), and a violation naming the property:

```json
{
  "source": "structural",
  "violations": [
    {
      "property": "openTaskCount",
      "propertyIri": "https://sequeless.dev/ns/ref#openTaskCount",
      "message": "Property 'openTaskCount' is a derived property and cannot be set directly"
    }
  ]
}
```

This is a more specific message than a plain read-only property gets (`"...is read-only"`) —
`ValueCoercer.coerce` checks `property.derivation().isPresent()` to choose between the two wordings.
`StructuralValidator` carries its own defensive canary (`assertNoDerivedPropertyPresent`) guarding
against a derived IRI ever reaching the store even if `ValueCoercer` were bypassed some other way;
it is confirmed dead code on every tested path today, since `ValueCoercer`'s all-or-nothing
coercion always short-circuits first.

## Known limitations

- **No materialised values.** Every derived property is recomputed on every read/browse; `sq:
  materialised` stays reserved and unimplemented. A page of many objects sharing a roll-up still
  costs one aggregate query per rule per request — cheap relative to a naive per-object query, but
  not free, and not cached across requests.
- **No filtering, sorting, or faceting on derived properties.** `Query`/`Criterion`/`Sort`/
  `facetProperties` only ever address stored properties; a derived property cannot appear on either
  side of a `filter[...]`, `sort=`, or `facets=` request parameter. This mirrors Phase 3's existing
  "multi-valued properties are out of scope for filter/sort/facet/index" limitation in spirit: both
  are values `QueryPort.query` cannot resolve as a plain JSONB expression.
- **Plug-ins must self-bound their own `QueryPort` usage.** Nothing in `DerivationPlanner` or
  `DerivationContext` prevents a plug-in from issuing one query per object, or from loading an
  unbounded working set into memory instead of pushing the aggregation down to `QueryPort`. The
  "one call per rule per page" bound the planner enforces for itself is a discipline a plug-in
  author must also follow by hand — `WorkloadDerivationPlugin` is written explicitly to demonstrate
  the correct pattern, not the simplest one.
- **A derived property's own datatype/cardinality restrictions are declarative only.** Nothing
  checks at activation time that a roll-up's computed value would actually satisfy the property's
  own `rdfs:range`/cardinality restriction (e.g. a `SUM` over a property whose range doesn't match
  the derived property's declared range) — a mismatch would only surface as a mapping oddity in the
  wire response, not an activation error.
