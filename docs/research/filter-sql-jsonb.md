# Can filter-sql-adapter express the prototype's JSONB reads?

Research for [issue #15](https://github.com/sequeless/sequeless-prototype/issues/15), a child of the
[CQRS decomposition map](https://github.com/sequeless/sequeless-prototype/issues/11).

## Question

Can `sequeless-filter`'s `filter-sql-adapter` SPI (`QueryBuilder` hooks, `ColumnMapping`, `SqlFragment`)
express what the prototype's `PostgresQueryStore` does: tagged-JSONB extraction from `sq_object.props`
with per-datatype casts, subtype IRI sets, and text search over `sq:searchable` properties?

## Sources read

- `~/workspace/sequeless-filter/filter-core/src/main/java/org/sequeless/filter/api/{FieldFilter,OperatorDefinition,ValueShape,FieldDefinition}.java`, `internal/BuiltinOperatorContributor.java`
- `~/workspace/sequeless-filter/filter-sql-adapter/src/main/java/org/sequeless/filter/sql/{api/{ColumnMapping,SqlFragment,DefaultFilterQueryTranslator,FilterQueryTranslator},spi/{QueryBuilder,AnsiQueryBuilder}}.java`
- `~/workspace/sequeless-filter/filter-sql-adapter/src/test/java/org/sequeless/filter/sql/h2/H2QueryBuilder.java`
- `~/workspace/sequeless-filter/docs/sql-adapter.md`
- `sequeless-adapter-persistence-postgres/src/main/java/org/sequeless/adapter/persistence/postgres/PostgresQueryStore.java` (full, 719 lines)
- `sequeless-spi/src/main/java/org/sequeless/spi/query/{Criterion,Operator,Query,QueryPort,AggregateRequest,AggregateResult,FacetBucket,Sort,QueryResult,Direction}.java`
- `sequeless-core/src/main/java/org/sequeless/core/api/BrowseQuery.java`
- `docs/architecture/query-port.md` (full)

## A) Mapping table: prototype `Criterion`/`Operator` → filter-core equivalent

| Prototype `Operator` | SQL rendering in `PostgresQueryStore.criterionSql` | filter-core `OperatorDefinition` | `AnsiQueryBuilder` hook |
|---|---|---|---|
| `EQ` | `expr = :v` | `is` | `renderIs(column, false, value)` |
| `NE` | `expr IS DISTINCT FROM :v` (deliberate, to avoid UNKNOWN on NULL) | `is not` | `renderIs(column, true, value)` — **gap B1**: ships `<>`, not `IS DISTINCT FROM` |
| `IN` | `expr IN (:v)` | `is in` | `renderIn` |
| `LT`/`LTE`/`GT`/`GTE` | `<`,`<=`,`>`,`>=` | `is less than`, etc. | `renderComparison` |
| `CONTAINS` | `expr ILIKE '%v%' ESCAPE '\'` | `contains` | `renderLike` — **gap B2**: ships case-sensitive `LIKE`, no escaping |
| `STARTS_WITH` | `expr ILIKE 'v%' ESCAPE '\'` | `starts with` | `renderLike` — same gap |
| `IS_NULL`/`NOT_NULL` | `IS [NOT] NULL` | unary `exists`/`does not exist` (value-less, matches prototype's `Optional.empty()` shape) | `renderExists` — sound mapping, two different routes to the same SQL (see B3) |
| text search (`sq:searchable`) | `search_vector @@ plainto_tsquery(...)` + rank `ORDER BY` | **no equivalent** | — (B4) |
| subtype IRI set (`type_iri IN (:types)`) | plain-column `IN`, not JSONB | **not actually a gap** — same shape as `is in` over a mapped column | `renderIn` (see B5) |

Value types (`Value` → filter-core):

| Prototype `Value` | Cast in SQL | filter-core equivalent |
|---|---|---|
| `TextValue` | — | `TextNode` → `String`, direct match |
| `IntegerValue`/`LongValue` | `::bigint` | `IntNode`/`LongNode`, direct match (cast is the gap, not the value) |
| `DecimalValue` | `::numeric` | `DoubleNode`/number catch-all; no distinct decimal-precision literal in the grammar |
| `BoolValue` | `::boolean` | `BooleanNode`, direct match |
| `DateValue` | `::date` | **no coercion** — docs say explicitly there is no date/time literal; a date is a plain string, left to the vendor `QueryBuilder`/JDBC |
| `DateTimeValue` | `::timestamptz` | same gap as above |
| `ReferenceValue` | `(props -> :p ->> 'ref')::uuid` | **no equivalent** — filter-core's value model is plain JSON scalars; no reference/IRI/UUID type or extraction-path concept |
| `ListValue` (inside `IN`) | — | `ArrayNode` → `List<Object>`, direct match |

## B) Gaps

- **B1 — `NE` semantics.** `AnsiQueryBuilder.renderIs` renders non-null `is not` as `<>`, which is UNKNOWN (and silently drops rows) against a SQL NULL produced by a JSONB key that's simply absent. The prototype's own code comment calls this out as the reason it uses `IS DISTINCT FROM`. Real behavioral gap, not cosmetic.
- **B2 — `CONTAINS`/`STARTS_WITH`.** `AnsiQueryBuilder.renderLike`'s Javadoc states escaping of literal `%`/`_` is not performed by the default implementation, and it uses case-sensitive `LIKE`. The prototype always escapes and always uses `ILIKE`.
- **B3 — Null-handling is mapped correctly but via two different routes.** The prototype's `IS_NULL`/`NOT_NULL` are value-less (`Criterion.value = Optional.empty()`), which matches filter-core's unary `exists`/`does not exist` pair, not filter-core's alternate "`is` + null JSON literal" path. Not a gap, but subtle enough to misdesign around if unstated.
- **B4 — Full-text search has no filter-core concept at all.** No `Criterion`/`Operator`, no `ColumnMapping` hook, no `QueryBuilder` method for `@@ plainto_tsquery(...)` or rank-based `ORDER BY`. `QueryBuilder`'s contract (`binary`/`unary`/`and`/`or`) only ever produces `WHERE`-fragments; it has no `ORDER BY` capability and no index-prep lifecycle (the prototype's `ensureIndexes`/`sq_searchable_property`/`backfillSearchVector` have zero analog).
- **B5 — Subtype IRI sets are not a JSONB gap.** `type_iri IN (:types)` is a plain-column `IN`, mechanically identical to `is in` over an ordinary mapped column. The issue's framing groups it with the JSONB concerns, but it's the one piece already covered natively by `ColumnMapping`/`renderIn`. It's query-level scope narrowing (always applied), not a user-supplied `Criterion`, which is why it isn't wired that way today — not because the SPI can't express it.
- **B6 — No JSONB path+cast expression hook (the central gap).** `ColumnMapping.columnFor(path)` returns `Optional<String>` — a bare column name. Every `AnsiQueryBuilder` hook receives that already-quoted identifier and splices it into `<column> <op> ?`. There is no seam for an expression like `(props -> 'urn:...' ->> 'decimal')::numeric` in place of a column name. `ColumnMapping`'s Javadoc and `sql-adapter.md`'s "Known limitations" both say explicitly: "no way to express ... a JSON path expression" — a deliberate, documented scope boundary, not an oversight. This is exactly what `PostgresQueryStore.castExpression`'s per-`Datatype` switch exists to do (`STRING`/`ANY_URI`/`TIME`/`DURATION` → `::text`[implicit], `INTEGER`/`LONG` → `::bigint`, `DECIMAL`/`DOUBLE` → `::numeric`, `BOOLEAN` → `::boolean`, `DATE` → `::date`, `DATE_TIME` → `::timestamptz`).
- **B7 — No temporal coercion**, confirmed by filter-core's own docs ("no date/time literal in the grammar"). Tied to B6 since the prototype's date handling is an extraction-expression cast, not a bind-value coercion.
- **B8 — No reference/IRI value type or `::uuid` extraction path.** filter-core's value model is plain JSON scalars only; nothing analogous to `(props -> :p ->> 'ref')::uuid` exists anywhere in `ValueShape`/`OperatorDefinition`/the JSON-to-Java table.
- **B9 — No "translate minus one criterion" concept**, needed for the prototype's facet-exclusion rule (`buildWhere`'s `excludingProperty` parameter, conventional multi-select faceting). `DefaultFilterQueryTranslator.translate` only ever translates one static tree to one `SqlFragment`.

## C) New SPI hooks vs. a separate Postgres-dialect module

- **B1 and B2 are a clean fit for the existing extension seam.** `AnsiQueryBuilder` is a documented Template Method; `renderIs` and `renderLike` are `protected` and overridable for exactly this kind of per-vendor behavior change (`H2QueryBuilder` already demonstrates the pattern for `quoteIdentifier`). A `PostgresQueryBuilder extends AnsiQueryBuilder` overriding only these two methods needs no new hook methods. **Do this regardless of what else is decided.**
- **B6/B7/B8 cannot be solved inside the existing SPI shape.** The seam that would need to change is `ColumnMapping.columnFor`'s contract itself (bare identifier → arbitrary expression), and that contract is also the identifier-quoting / SQL-injection boundary. Returning a cast expression from `columnFor` and having `quoteIdentifier` pass it through unquoted works mechanically but trades away that safety guarantee by construction. Fixing this is either a change to `filter-sql-adapter`'s own `ColumnMapping` contract, or living entirely outside it.
- **B4 (text search) is outside `QueryBuilder`'s 4-method contract entirely**, and not fixable by adding a hook to `AnsiQueryBuilder` — it needs a query-level input channel, `ORDER BY`-producing capability, and an index-lifecycle concept that `QueryBuilder`/`FilterQueryTranslator` have no parallel for at all. This is a concern for whatever sits above `filter-sql-adapter` in `sequeless-query`, not a `QueryBuilder` hook.

## D) Clearly outside filter-sql-adapter's scope

None of these have any filter-core/filter-sql-adapter analog, and none should get one — they're a different layer, already separated in the prototype:

- **Faceting** — `PostgresQueryStore.computeFacet`/`computeScalarFacet`/`computeReferenceFacet`, driven by `Query.facetProperties()`; lives at the `QueryPort`/`QueryResult.facets()` layer (`sequeless-spi/.../{Query,QueryResult,FacetBucket}.java`), above WHERE-clause translation.
- **Sorting** — `Query.sorts()`/`Sort`/`Direction`, `PostgresQueryStore.buildOrderBy`. `QueryBuilder` never touches `ORDER BY`.
- **Paging** — `Query.page()`, `LIMIT`/`OFFSET`. No filter-sql-adapter concept at all.
- **Aggregates** — `QueryPort.aggregate`, `AggregateRequest`/`AggregateResult`, the `GROUP BY`/`SUM`/`MIN`/`MAX`/`AVG`/`COUNT` density-contract logic. Reuses `castExpression`/`criterionSql` internally (so shares B1/B2/B6/B7/B8), but the aggregation mechanics themselves are pure `QueryPort`-layer logic.
- **Index/registry management** — `ensureIndexes`, `sq_index_registry`, `sq_searchable_property`, `backfillSearchVector`: DDL/maintenance lifecycle, not filter translation.
- **Tenant/soft-delete scoping** — `tenant_id = :t AND deleted_at IS NULL`, baked unconditionally into `buildWhere`, never expressed as a `Criterion`.

`docs/architecture/query-port.md`'s "Facet counting," "Default ordering," and "A non-SQL adapter sketch" sections already describe each of these as adapter-specific concerns riding on top of a `Criterion`-driven core — consistent with where filter-sql-adapter draws its own line. `BrowseQuery` (`sequeless-core/.../BrowseQuery.java`) is the use-case-facing shape sitting above `Query`.

## E) Recommendation / lean

This is research, not a decision, but the evidence points to a **hybrid**, not a clean either/or:

1. **Extend the existing SPI for the behavioral-only gaps (B1, B2).** A `PostgresQueryBuilder extends AnsiQueryBuilder` overriding `renderIs` and `renderLike`. Low cost, fits the documented pattern exactly, no contract changes. Worth doing independent of anything else below.
2. **The JSONB path+cast problem (B6/B7/B8) needs more than a hook — a separate Postgres-dialect module, not an SPI extension.** Two sub-options:
   - **(2a) Propose an expression-returning contract upstream in `filter-sql-adapter` itself** (e.g. alongside `ColumnMapping.columnFor`), so a `PostgresJsonbColumnMapping` is a first-class extension of the library's own module. Keeps `sequeless-query`'s Postgres backend inside the sequeless-filter family of concepts, but requires a PR against a shared dependency to change a documented, deliberate scope boundary, and risks weakening `quoteIdentifier`'s injection-safety guarantee if identifiers and expressions aren't given different trust treatment.
   - **(2b) Build a Postgres-JSONB adapter alongside `filter-sql-adapter`** in `sequeless-query` that pre-resolves each field's path+datatype to a cast expression before invoking `DefaultFilterQueryTranslator` — i.e. bypasses the `ColumnMapping`/translator pipeline for JSONB-typed fields and keeps something close to `PostgresQueryStore.castExpression`'s own SQL-string-building, while still reusing filter-core's `Criterion`/`Operator` model for parsing and validation. Avoids touching `filter-sql-adapter` at all, at the cost of only getting filter-core's parsing/validation value for those fields, not its SQL-rendering value.

   **(2b) looks better-supported by the evidence.** `AnsiQueryBuilder`'s own Javadoc states its hook set is "the complete, fixed hook set — not a starting point meant to accrete further overloads," and `ColumnMapping`'s Javadoc frames the join/subquery/JSON-path exclusion as deliberate. Pushing Postgres-JSONB specifics into the shared `sequeless-filter` library risks violating that design intent for the sake of one consumer. A wrapper in `sequeless-query` that reuses the operator/value model but writes its own SQL for the cast concern, while still delegating vendor-syntax concerns (B1, B2) to a `QueryBuilder` subclass, captures most of the reuse value without forcing a scope-boundary change on an upstream shared dependency.
3. **Text search, faceting, sorting, paging, and aggregates (section D) stay outside filter-sql-adapter regardless of 2a/2b** — they belong to whatever `sequeless-query` builds as its own query-port-equivalent layer, exactly where they already live in the prototype.

## F) Notable / surprising findings

- **Java version mismatch**: `sequeless-filter`'s `pom.xml` targets Java 21; the prototype targets Java 25. Not blocking for a binary dependency, but relevant to `sequeless-query`'s own toolchain choice, and to option (2a)'s cost if `sequeless-filter`'s build stays pinned to 21.
- **`AnsiQueryBuilder`'s hook set is explicitly closed**, per its own Javadoc — a strong textual signal against adding further hooks casually, reinforcing the lean toward (2b) for the structural gaps.
- **`ColumnMapping.of(Map)` is already the "safe for untrusted paths" allowlist variant** (vs. `identity()`); any Postgres-JSONB mapping would need the same posture — resolve property IRI → cast expression only for IRIs the caller's `MetaModelSnapshot` actually declares — mirroring how `PostgresQueryStore.castExpression` already takes a resolved `PropertyDefinition`, never a raw string.
- **`QueryPort.aggregate` reuses the same `castExpression`/`criterionSql` machinery as `query`** — any fix for B1/B2/B6/B7/B8 needs to cover both paths; they aren't independent.
- **The subtype-IRI-set case (B5) turned out not to be a JSONB concern at all** — worth correcting in how the issue framed it, since it's fully expressible by the existing `ColumnMapping`/`renderIn` pipeline today with zero extension.
- **The prototype's `NE` → `IS DISTINCT FROM` choice is explained in its own code comment** as a deliberate correctness fix over a naive `<>` — and `AnsiQueryBuilder`'s shipped default has exactly that naive-`<>` behavior, a concrete, citable instance of gap B1.
