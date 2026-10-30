# Phase 3 — core-api library

**Status: done.** Commits `ebd6a02`, `85ec788`, `448a8c1` (plus the `provide-sequeless.ts`/`tokens.ts`
pair from the Gate C fix-up chain). 125/125 tests across 7 spec files, lint clean, full shell build
green.

## Context

Every feature library (Phases 6–8) needs the same handful of things from the backend: typed wire
models, a way to tell the dozen HTTP error shapes apart, the `filter[p][op]` query grammar, and
optimistic-concurrency bookkeeping. `core-api` is where those live, plus the pluggability layer
that lets every one of them be swapped without touching a feature library — the frontend's
analogue of the backend's `sequeless.<port>.adapter` swap.

## Design

### Pluggability layer — `tokens.ts` + `provide-sequeless.ts`

Nine `InjectionToken`s: `SEQUELESS_API_BASE_URL`, `SEQUELESS_HTTP_INTERCEPTORS`,
`RDF_STORE_FACTORY`, `TURTLE_EDITOR`, `GRAPH_LAYOUT`, `SEQUELESS_ERROR_MAPPER`, `FIELD_RENDERERS`,
`SEQUELESS_CLOCK`, `SEQUELESS_FEATURES` (the last carrying a `SequelessFeatures` interface, today
just `{ serverSideOntologyDiff: boolean }`, defaulting to `false` — Phase 7's client-side ontology
diff stays the only implementation until a backend `?mode=dryRun` exists, per
`docs/architecture/prompts/phase-7.md`).

`provideSequeless(config?: SequelessConfig): EnvironmentProviders` fills every unset slot from an
individually-exported `provideDefault*()` helper (`provideDefaultApiBaseUrl`, `provideDefaultClock`,
`provideDefaultFeatures`, `provideDefaultErrorMapper`, `provideDefaultFieldRenderers`), so swapping
one piece — `provideSequeless({ graphLayout: elkLayout() })` — never requires touching a library
that consumes it. Two slots (`RDF_STORE_FACTORY`, `TURTLE_EDITOR`, `GRAPH_LAYOUT`) are typed
`unknown` for now, since their concrete shape is decided by the library that defines it (Phases 4
and 5) — `core-api` only owns the slot, not the contract inside it. **Every object-valued default
(`FIELD_RENDERERS`, `SEQUELESS_FEATURES`) must be constructed fresh on each `provideSequeless()`
call** — merging over a shared module-level object instead of a per-call one leaks configuration
between separate `TestBed`s, which is exactly what `provide-sequeless.spec.ts`'s last test
(`'does not leak configuration between separate TestBeds'`) exists to catch.

### Wire models — `models/index.ts`

Hand-written TypeScript interfaces mirroring every DTO record in
`sequeless-app/src/main/java/org/sequeless/app/rest/` field-for-field: `TypeSummaryResponse`,
`TypeDetailResponse`, the `PropertyResponse` discriminated union
(`AttributePropertyResponse | RelationshipPropertyResponse`, keyed on a literal `kind` field),
`DerivationResponse`, `StateMachineResponse`/`StateResponse`/`TransitionSummaryResponse`,
`BusinessObjectResponse`/`AuditResponse`, `QueryResultResponse`/`FacetBucketResponse`,
`CreateObjectRequest`/`UpdateObjectRequest`/`TransitionRequest`/`TransitionResponse`,
`OntologyIssue`/`OntologyReportResponse`/`OntologyImportResponse`, `WhoAmIResponse`,
`ErrorResponse`/`ViolationResponse`. Every collection is `readonly`; every nullable Java field is
`T | null`, never optional — Jackson always writes the key. The two deliberate exceptions are
`UpdateObjectRequest.version` and `TransitionRequest.expectedVersion`, `?: number | null`, matching
how the backend accepts either an omitted field or an explicit `null` for "use `If-Match` instead."
`isAttributeProperty`/`isRelationshipProperty` type guards narrow the union on `kind`.

**Hand-written, not OpenAPI-generated**, for two concrete reasons, not just general caution:
`PropertyResponse` is a sealed Java interface discriminated by a plain `kind` string with no
`@JsonTypeInfo`/`@Schema(oneOf=…)`, so springdoc emits a flattened schema and a generator would
produce a type that defeats the discriminated union this frontend actually needs; and the
`filter[x][y]` family (see below) is inexpressible in OpenAPI at all
(`docs/architecture/query-port.md:341-347`), so a generator could never cover the query layer
regardless. (Pending write-up as DR-20 in `docs/architecture/02-decision-records.md`.)

### Error mapper — `errors/api-error.ts`

`toApiError(err: HttpErrorResponse): ApiError` normalises every shape `ApiExceptionAdvice` can
emit into one `ApiError { status, kind, title, detail, slug, violations, issues, expectedVersion,
transitionName, typeIri, signalName, raw }`, checked in this exact order (each branch returns
immediately — order matters because the Spring-default shape can also carry a `message` field
that must not be mistaken for the single-field legacy shape):

1. `status === 0` → `kind: 'network'` (no response reached the client).
2. Body has a string `type` starting with `https://sequeless.dev/problems/` → `kind: 'problem'`,
   slug from the last path segment, lifting `violations`/`expectedVersion`/`transitionName`/
   `typeIri`/`signalName` by shape — detected from the body alone, regardless of `Content-Type`.
3. Body has `timestamp`, `status`, `error`, `path` → `kind: 'spring-default'` (Spring Boot's own
   default error JSON — an unparseable Turtle POST, a malformed UUID path variable).
4. Body has `consistent: boolean` and an `issues` array → `kind: 'ontology-report'` (the legacy
   raw `OntologyReportResponse` on a 422).
5. Body has a string `message` → `kind: 'legacy-message'` (`TypeNotFoundException`,
   `AuthorizationException`).
6. Otherwise → `kind: 'unknown'`, `raw` preserved exactly (including `null`).

**`PreconditionMismatchException`'s slug is `bad-request`, not `precondition-mismatch`** — there
is no such slug on the wire, and the mapper reports what the server actually sent rather than
inventing one. Consumers must key off status + request context, never that slug.

### Query grammar — `query/browse-query.ts`

`toHttpParams(state: BrowseQueryState): HttpParams` / `fromQueryParamMap(params): BrowseQueryState`
mirror `ObjectsController#browse`'s grammar exactly: `page` (default 0), `size` (capped at 200,
floored at 1, matching the server's `Math.min`), `q` (omitted if blank), `sort` (comma list,
leading `-` for descending), `facets` (comma list), and repeatable `filter[<property>][<op>]=<value>`
for all eleven operators (`eq ne in lt lte gt gte contains startswith isnull notnull`), parsed
case-insensitively, with `isnull`/`notnull` carrying no value and `in` splitting/joining on commas.
Malformed filter keys and unknown operators are silently dropped on parse. Relies on Angular's
default `HttpParams` codec to percent-encode the brackets — `ApiPathPrefixTest.
bracketFilterParametersSurvivePercentEncoding` already proves the server decodes them correctly,
so no custom codec is needed.

**A genuine TypeScript rough edge hit here, worth remembering:** `Array.isArray` does not reliably
narrow a `readonly T[]` branch *out* of a union in the negative (`else`) case. A function returning
`string` from a `string | readonly string[] | undefined` input, structured as `if (Array.isArray(v))
return v.join(','); return v ?? '';`, fails to compile at that last line even though the logic is
correct — TypeScript still sees the full union there. Fix: narrow the fallback with an explicit
`typeof v === 'string' ? v : ''` instead of relying on the negated array check.

### Query capability gating — `query/query-capabilities.ts`

`queryCapabilities(property: PropertyResponse): { filterable, sortable, facetable }`, a
best-effort mirror of `DefaultBusinessObjectService`'s browse rule
(`docs/architecture/query-port.md`): a property with `cardinalityMax !== 1` (including `null`,
meaning unbounded) or a derivation that isn't materialised can't be filtered, sorted or faceted at
all; otherwise `filterable`/`sortable` are `true` and `facetable` passes through the property's
`facet` flag. The rule is identical for attributes and relationships. **This mirror does not model
operator/datatype mismatches** (e.g. `contains` on an integer) — the server rejects those too, so
a consuming feature must still handle a 400 `invalid-query` from the real endpoint.

### ETag registry and interceptor — `http/etag-registry.ts`, `http/etag.interceptor.ts`

`ETagRegistry` (root-provided) remembers the latest version per object, **keyed by the object's id
alone, not by type** — ids are globally unique UUIDs, so the `{type}` path segment (a short name,
an encoded full IRI, or a supertype name) never matters for the lookup; `set()` strips the wire
ETag's surrounding quotes on the way in. `etagInterceptor` (a functional `HttpInterceptorFn`):

- **Records** the unquoted version from any response carrying both an `ETag` header and a body
  with a string `id` (GET, POST-create, PUT, and transition-POST responses all qualify).
- **Adds** a quoted `If-Match` to outgoing `PUT`, `DELETE`, and `POST .../transitions/{name}`
  requests when the registry has a version for the URL's UUID-shaped path segment (found by
  scanning segments for the UUID pattern, not by assuming a fixed position — this is what makes
  it work identically whether `{type}` is `Task`, an encoded IRI, or the supertype `WorkItem`).
  Deliberately **excludes** `GET`, the create-`POST` (no id segment exists yet), and `POST
  .../signals/{name}` even though a signal URL *does* contain a UUID segment — eligibility for
  `POST` requires the URL to contain `/transitions/` specifically, not just any UUID.
- **Never overwrites** an `If-Match` header the caller set explicitly, and **never injects
  `version` into a request body** — this is deliberate design, not an oversight: a `PUT` sends
  `If-Match` and leaves the body exactly as given, which makes both the 428
  `precondition-required` and 400 body/header-mismatch responses structurally unreachable from
  this client.

## Files

- `projects/core-api/src/lib/tokens.ts`, `provide-sequeless.ts`
- `projects/core-api/src/lib/models/index.ts`
- `projects/core-api/src/lib/errors/api-error.ts`
- `projects/core-api/src/lib/query/browse-query.ts`, `query-capabilities.ts`
- `projects/core-api/src/lib/http/etag-registry.ts`, `etag.interceptor.ts`
- `projects/core-api/src/public-api.ts` (re-exports all of the above)
- `projects/testing/src/lib/fixtures/project-type-detail.ts` — a realistic `ex:Project`
  `TypeDetailResponse` fixture (derived properties, a relationship, all 7 transitions) used across
  the model and query-capability specs; not yet re-exported from `testing`'s own `public-api.ts`.

## Verification

`npx ng test core-api --watch=false` — 125/125 across all 7 spec files. `npx ng lint core-api` —
clean. `npx ng build sequeless-shell --configuration production` — succeeds (exercises the whole
dependency graph, so a type error anywhere in `core-api` fails this build even if that spec file
wasn't touched).

## What's still a stub, deliberately

`provideDefaultErrorMapper()`'s actual function is an identity placeholder (returns its input
unchanged) — `toApiError` exists as a standalone exported function in `errors/api-error.ts`, but
wiring it as the *default* value behind `SEQUELESS_ERROR_MAPPER` is feature-library work, done when
a consumer (Phase 6 or 7) actually needs to inject the token rather than import `toApiError`
directly. `RDF_STORE_FACTORY`, `TURTLE_EDITOR` and `GRAPH_LAYOUT` have no default implementation
yet at all — their defaults land in Phases 4 and 5 respectively.
