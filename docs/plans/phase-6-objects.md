# Phase 6 plan — objects feature (generic BREAD)

Branch: `phase-6-objects` (stacked on the Phase 5 ui-kit work). Spec: `docs/specs/phase-6-objects.md`.
Execution: a queued batch of stateless `claude -p` calls (Phase A writes one spec file per call, Phase B
writes one implementation file per call against it), each followed by a scoped commit. All paths below
are relative to `sequeless-ui/`.

## Findings that shape the plan

- core-api has wire models, `toApiError`, `browse-query`, `query-capabilities`, `ETagRegistry` and
  `etagInterceptor`, but **no HTTP client at all**, and `public-api.ts` does not export the `query/*`
  and `http/*` files.
- The shell's `app.config.ts` never calls `provideSequelessHttp()`, so `etagInterceptor` is not registered and no
  `If-Match` is ever sent. (Measured during implementation: `HttpClient` itself resolves by default in Angular 22, and
  `SqDynamicField` merges `DEFAULT_FIELD_RENDERERS` itself, treating `FIELD_RENDERERS` as optional overrides. So
  `provideUiKit()` is not needed for this feature.)
- `objectsRoutes` is `[]`; the shell already lazy-loads it and must not change `app.routes.ts`.
- Backend: `GET /api/types`, `GET /api/types/{nameOrIri}`, `GET|POST /api/objects/{type}`,
  `GET|PUT|DELETE /api/objects/{type}/{id}` (ETag = `"version"`, `If-Match` optional),
  `GET /api/objects/{type}/{id}/transitions` (USER_ACTION only), `POST .../transitions/{name}`,
  `POST .../signals/{name}` (202, empty body, optional JSON map). Problems are
  `https://sequeless.dev/problems/<slug>`: `validation`, `invalid-query`, `stale-object`
  (`expectedVersion`), `transition-not-available` (`detail` = reason), `unknown-signal`.

## Decisions (each reversible or configurable)

| # | Decision | Why it stays reversible |
|---|---|---|
| D1 | Shared HTTP clients (`TypesClient`, `ObjectsClient`) live in **core-api** as `@Injectable({providedIn:'root'})` classes. | Phases 7–8 reuse them; any consumer swaps them with `{provide: TypesClient, useClass: Fake}`. |
| D2 | Clients rethrow errors as `SEQUELESS_ERROR_MAPPER(toApiError(err))`. The default mapper stays identity. | Error shaping is a DI token, as Phase 3 intended. |
| D3 | New `provideSequelessHttp()` in core-api (HttpClient + `etagInterceptor` + every `SEQUELESS_HTTP_INTERCEPTORS` entry). `provideSequeless()` is untouched. | Additive; the shell opts in with one line. |
| D4 | The shell's `app.config.ts` adds `provideSequelessHttp()` only. `provideUiKit()` is deliberately not applied at the root: measured with production builds it adds ~418 kB (the nine field renderers) to the eager bundle and breaks the 1 MB budget, and the objects feature does not need it. The editor and graph slots are provided at the route that needs them. `app.routes.ts` is unchanged. | One line. |
| D5 | Detail panels share an `ObjectDetailStore` (component-scoped) instead of outputs. The signals panel is resolved through the `OBJECT_SIGNALS_PANEL` token (default `SqSignalsPanel`). | Phase 8 can plug in a richer panel with a single provider, and remove it the same way. |
| D6 | The signal refetch delay is the `OBJECTS_SIGNAL_REFETCH_DELAY_MS` token (default 1500). | Configurable. |
| D7 | Facets are always derived from the type (every facetable property) and never written to the URL. Page resets to 0 when filters, sort or q change. | Kept in one place (`BrowseQueryStateService`). |
| D8 | Read-only and derived properties render disabled and are excluded from POST/PUT bodies. Delete is included (BREAD), behind a confirm. | Local to `formToProperties` and the detail component. |
| D9 | "Known signals" means the type's `EXTERNAL_SIGNAL` transitions, deduplicated by `signalName`. | Replaceable through D5. |

## Shared contract

### [CORE-EXPORTS]
`projects/core-api/src/public-api.ts` additionally re-exports `./lib/query/browse-query`,
`./lib/query/query-capabilities`, `./lib/http/etag-registry`, `./lib/http/etag.interceptor`,
`./lib/http/provide-sequeless-http`, `./lib/clients/api-error-operator`, `./lib/clients/types-client`,
`./lib/clients/objects-client`. Existing exports stay.

### [CORE-HTTP]
`projects/core-api/src/lib/http/provide-sequeless-http.ts`
```ts
export function provideSequelessHttp(): EnvironmentProviders;
// = makeEnvironmentProviders([provideHttpClient(withInterceptors([etagInterceptor, sequelessInterceptors]))])
export const sequelessInterceptors: HttpInterceptorFn;
// delegates, in order, to every fn in inject(SEQUELESS_HTTP_INTERCEPTORS, {optional:true}) ?? []
```

### [CORE-ERR]
`projects/core-api/src/lib/clients/api-error-operator.ts`
```ts
export function rethrowAsApiError<T>(mapper: (e: unknown) => unknown): MonoTypeOperatorFunction<T>;
// catchError(e => throwError(() => mapper(e instanceof HttpErrorResponse ? toApiError(e) : e)))
```
Clients take `mapper = inject(SEQUELESS_ERROR_MAPPER, {optional:true}) ?? (e => e)` and
`base = inject(SEQUELESS_API_BASE_URL, {optional:true}) ?? '/api'`.

### [CORE-TYPES]
`projects/core-api/src/lib/clients/types-client.ts`
```ts
@Injectable({ providedIn: 'root' }) export class TypesClient {
  list(): Observable<readonly TypeSummaryResponse[]>;          // GET {base}/types
  describe(nameOrIri: string): Observable<TypeDetailResponse>; // GET {base}/types/{encodeURIComponent(nameOrIri)}
}
```

### [CORE-OBJECTS]
`projects/core-api/src/lib/clients/objects-client.ts` (`type` is always `encodeURIComponent`-ed)
```ts
@Injectable({ providedIn: 'root' }) export class ObjectsClient {
  browse(type: string, state: BrowseQueryState): Observable<QueryResultResponse>; // GET {base}/objects/{type}, params = toHttpParams(state)
  get(type: string, id: string): Observable<BusinessObjectResponse>;
  create(type: string, req: CreateObjectRequest): Observable<BusinessObjectResponse>; // POST
  update(type: string, id: string, properties: Record<string, unknown>): Observable<BusinessObjectResponse>; // PUT body {properties} ONLY, never version (etagInterceptor adds If-Match)
  delete(type: string, id: string): Observable<void>;
  transitions(type: string, id: string): Observable<readonly TransitionResponse[]>;
  fireTransition(type: string, id: string, name: string): Observable<BusinessObjectResponse>; // POST .../transitions/{name}, body {}
  signal(type: string, id: string, name: string, payload?: Record<string, unknown>): Observable<void>; // POST .../signals/{name}, body payload ?? {}, 202
}
```
All methods pipe `rethrowAsApiError(mapper)`.

### [OBJ-LOOKUP]
`projects/objects/src/lib/relationship/objects-relationship-lookup.ts`
```ts
@Injectable() export class ObjectsRelationshipLookup implements RelationshipLookup { // from @sequeless/ui-kit
  search(targetTypeIri: string, term: string): Observable<readonly BusinessObjectResponse[]>;
  // ObjectsClient.browse(targetTypeIri, {page:0,size:20,q:term,sort:[],filters:[],facets:[]}).items
  targetType(targetTypeIri: string): Observable<TypeDetailResponse>; // TypesClient.describe, cached per IRI (shareReplay)
}
export function provideObjectsRelationshipLookup(): Provider[]; // [ObjectsRelationshipLookup, {provide: RELATIONSHIP_LOOKUP, useExisting: ObjectsRelationshipLookup}]
```

### [OBJ-COLUMNS]
`projects/objects/src/lib/browse/browse-columns.ts` (pure)
```ts
export const PAGE_SIZE_OPTIONS: readonly number[]; // [10, 20, 50, 100, 200]
export function visibleColumns(type: TypeDetailResponse): PropertyResponse[]; // !hidden, sorted by order (nulls last, then name)
export function facetableProperties(type: TypeDetailResponse): string[];      // names where queryCapabilities(p).facetable
export function operatorsFor(p: PropertyResponse): readonly FilterOperator[];
// string/anyURI: eq ne contains startswith in isnull notnull; numeric/date/time/duration: eq ne gt gte lt lte isnull notnull;
// boolean: eq isnull notnull; relationship: eq in isnull notnull
```

### [OBJ-VIOLATIONS]
`projects/objects/src/lib/browse/violation-mapping.ts` (pure)
```ts
export interface FilterErrors { byIndex: ReadonlyMap<number, string>; unmatched: readonly string[] }
export function mapViolationsToFilters(filters: readonly FilterClause[], err: ApiError): FilterErrors;
// only for err.type ending 'invalid-query'; violation.property === filter.property -> that index; else unmatched
```

### [OBJ-QSTATE]
`projects/objects/src/lib/browse/browse-query-state.service.ts` — provided at component level.
```ts
@Injectable() export class BrowseQueryStateService {
  readonly state: Signal<BrowseQueryState>;                   // from ActivatedRoute.queryParamMap via fromQueryParamMap
  toQueryParams(s: BrowseQueryState): Params;                // via toHttpParams; omits facets; omits defaults page=0,size=20
  fromQueryParams(p: Params): BrowseQueryState;              // via fromQueryParamMap(convertToParamMap(p))
  update(patch: Partial<BrowseQueryState>): Promise<boolean>; // merges; page -> 0 if filters/sort/q change; router.navigate([], {relativeTo, queryParams})
  withFacets(s: BrowseQueryState, facets: readonly string[]): BrowseQueryState;
}
```

### [OBJ-FILTER]
`projects/objects/src/lib/browse/filter-panel.ts` — `SqFilterPanel`, selector `sq-filter-panel`.
Inputs: `type = input.required<TypeDetailResponse>()`, `filters = input<readonly FilterClause[]>([])`,
`facets = input<Record<string, readonly FacetBucketResponse[]>>({})`, `errors = input<ReadonlyMap<number,string>>(new Map())`.
Output: `filtersChange = output<FilterClause[]>()`. Facets: one `mat-expansion-panel` per facet with a
`mat-chip-listbox`; selecting a bucket emits filters + `{property, operator:'eq', value}`. Active filters render as
removable chips (error text shown on the chip at its index). An "add filter" row: property select, operator select
from `operatorsFor`, value input (`sq-relationship-picker` for relationships; for range a second value emits gte+lte).

### [OBJ-BROWSE]
`projects/objects/src/lib/browse/object-browse.ts` — `SqObjectBrowse`, selector `sq-object-browse`,
providers `[BrowseQueryStateService]`. Route param `type`. Loads `TypesClient.describe(type)`, then
`ObjectsClient.browse(type, withFacets(state, facetableProperties))` on every state change. MatTable columns =
`visibleColumns` (+ link to `./{id}`), MatPaginator (`length`=totalItems, pageSizeOptions=PAGE_SIZE_OPTIONS),
matSort header enabled only when `queryCapabilities(p).sortable`, a search box (q), `sq-filter-panel`,
`sq-problem-banner` for unmatched errors, a "New" link to `./new`.

### [OBJ-STORE]
`projects/objects/src/lib/detail/object-detail.store.ts`
```ts
@Injectable() export class ObjectDetailStore {          // provided by SqObjectDetail
  readonly type: Signal<TypeDetailResponse | null>;
  readonly object: Signal<BusinessObjectResponse | null>;
  readonly error: Signal<ApiError | null>;
  load(typeName: string, id: string): Promise<void>;    // TypesClient.describe + ObjectsClient.get
  refetch(): Promise<BusinessObjectResponse | null>;    // ObjectsClient.get again, updates object
  set(object: BusinessObjectResponse): void;
}
```

### [OBJ-FORM]
`projects/objects/src/lib/detail/object-form.ts`
```ts
export interface PropertyGroup { group: string | null; properties: PropertyResponse[] }
export function editableProperties(type: TypeDetailResponse): PropertyResponse[]; // visible, !readOnly, not derived
export function groupProperties(type: TypeDetailResponse): PropertyGroup[];      // visible props, ungrouped (null) first, then by group name; order within
export function buildObjectForm(type: TypeDetailResponse, initial?: Record<string, unknown>): FormGroup; // one control per visible prop via controlFor (ui-kit); non-editable disabled
export function formToProperties(type: TypeDetailResponse, form: FormGroup): Record<string, unknown>;   // editable props only, via toWireValue
@Component({selector: 'sq-object-form'}) export class SqObjectForm { type = input.required<TypeDetailResponse>(); form = input.required<FormGroup>(); }
// renders ungrouped sq-dynamic-field list then one mat-expansion-panel per group
```

### [OBJ-TRANSITIONS]
`projects/objects/src/lib/detail/transitions-panel.ts` — `SqTransitionsPanel`, selector `sq-transitions-panel`.
Injects `ObjectDetailStore`, `ObjectsClient`. Lists `transitions(type,id)` as buttons (disabled when
`!available`, tooltip = reason). Click → `fireTransition` → `store.set(result)` and reload list. On 409
`transition-not-available` → reload list and show the problem `detail` in an inline message. Never retries.

### [OBJ-SIGNALS]
`projects/objects/src/lib/signals/signals-panel.ts` — `SqSignalsPanel`, selector `sq-signals-panel`.
```ts
export const OBJECTS_SIGNAL_REFETCH_DELAY_MS: InjectionToken<number>; // providedIn root, factory () => 1500
export const OBJECT_SIGNALS_PANEL: InjectionToken<Type<unknown>>;   // providedIn root, factory () => SqSignalsPanel
// Contract for any component bound to OBJECT_SIGNALS_PANEL: no required inputs; it injects ObjectDetailStore.
// Override at the app root: { provide: OBJECT_SIGNALS_PANEL, useValue: OtherPanel }.
export function knownSignals(type: TypeDetailResponse): string[];      // EXTERNAL_SIGNAL transitions' signalName, unique, sorted
```
Injects `ObjectDetailStore`. One button per known signal → `ObjectsClient.signal` → shows
"dispatched — takes effect asynchronously" (never claims a transition happened) → `store.refetch()` after the delay;
a "Refresh" button refetches on demand. 400 `unknown-signal` → inline error.

### [OBJ-DETAIL]
`projects/objects/src/lib/detail/object-detail.ts` — `SqObjectDetail`, selector `sq-object-detail`,
providers `[ObjectDetailStore]`. Route params `type`, `id`. View mode shows values; Edit mode builds
`buildObjectForm(type, object.properties)`. Save → `ObjectsClient.update(type,id, formToProperties(...))`. On 409
`stale-object`: `refetch()`, then `openConflictDialog(dialog, {base: lastLoaded.properties, local: formValue,
server: refetched.properties})`; `reapply` → PUT `reapplyLocal`-merged value once; `discard` → reset form to server;
never auto-retry. 400 `validation` → `sq-problem-banner`. Delete (confirm via `window.confirm`) → navigate to `..`.
Hosts `sq-transitions-panel` and `*ngComponentOutlet` of `inject(OBJECT_SIGNALS_PANEL)`.

### [OBJ-CREATE]
`projects/objects/src/lib/create/object-create.ts` — `SqObjectCreate`, selector `sq-object-create`. Route param
`type`. `buildObjectForm(type)` + `sq-object-form`; submit → `ObjectsClient.create(type, {properties:
formToProperties(...)})` → `router.navigate(['..', created.id], {relativeTo})`. 400 → `sq-problem-banner`.

### [OBJ-TYPELIST]
`projects/objects/src/lib/type-list/type-list.ts` — `SqTypeList`, selector `sq-type-list`. `TypesClient.list()`
filtered to `!abstract`, sorted by label, each a `routerLink` to `./{name}` in a `mat-nav-list`.

### [OBJ-ROUTES]
`projects/objects/src/lib/objects.routes.ts`
```ts
export const objectsRoutes: Routes = [{ path: '', providers: [provideObjectsRelationshipLookup()], children: [
  { path: '', component: SqTypeList }, { path: ':type', component: SqObjectBrowse },
  { path: ':type/new', component: SqObjectCreate }, { path: ':type/:id', component: SqObjectDetail } ] }];
```
`:type/new` must precede `:type/:id`.

### [OBJ-PUBLIC]
`projects/objects/src/public-api.ts` exports routes, `BrowseQueryStateService`, `ObjectDetailStore`,
`OBJECT_SIGNALS_PANEL`, `OBJECTS_SIGNAL_REFETCH_DELAY_MS`, all `Sq*` components, the pure helpers, and
`provideObjectsRelationshipLookup`.

### [SHELL-CONFIG]
`projects/sequeless-shell/src/app/app.config.ts` keeps its providers and adds `provideSequelessHttp()`
(core-api) only. A spec proves a PUT carries `If-Match` once a GET recorded the ETag. `provideUiKit()` is not applied
at the root (see D4).

## Task order

1. Phase A specs (one per call): CORE-HTTP, CORE-ERR, CORE-TYPES, CORE-OBJECTS, CORE-EXPORTS (public surface),
   OBJ-LOOKUP, OBJ-COLUMNS, OBJ-VIOLATIONS, OBJ-QSTATE, OBJ-FORM, OBJ-STORE, OBJ-TRANSITIONS, OBJ-SIGNALS,
   OBJ-FILTER, OBJ-BROWSE, OBJ-DETAIL, OBJ-CREATE, OBJ-TYPELIST, OBJ-ROUTES, SHELL-CONFIG.
2. Phase B implementations in the same order, with OBJ-PUBLIC before OBJ-ROUTES' test. Each runs its targeted spec.
3. Lint fixes per offending file, then verification.

## Verification

`npx ng test core-api --watch=false`, `npx ng test objects --watch=false`, `npx ng test sequeless-shell --watch=false`,
`npx ng lint core-api`, `npx ng lint objects`, `npx ng build sequeless-shell --configuration production`.
The Phase 9 e2e test `objects-stale-conflict.spec.ts` covers create → edit → stale conflict in two tabs.
