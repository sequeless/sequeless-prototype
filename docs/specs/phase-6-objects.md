# Phase 6 — objects feature: generic BREAD

**Status: not started.** Depends on `core-api` (done) and `ui-kit` (Phase 5). Does not depend on
`rdf`. This is the first feature a user actually sees, since the shell redirects `/` here.

## Context

Every business object type (Project, Task, Person, …) gets Browse, Read, Edit, Add and Delete
through the same four generic endpoints (`GET/POST/PUT/DELETE /api/objects/{type}[/{id}]`) — there
is no per-type backend code, and there must be no per-type frontend code either. This feature is
driven entirely by `TypeDetailResponse` at runtime: it never hard-codes a property name.

## Design

### Browse

`MatTable` + `MatPaginator` (`length` from `QueryResultResponse.totalItems`; page-size options
`[10, 20, 50, 100, 200]`, hard-capped at 200 to match the server's `Math.min(size, 200)`),
`matSort` driving `core-api`'s `toHttpParams`/`SortKey` serialisation (`-property` for descending).
Columns come from `TypeDetailResponse.properties` filtered to `!hidden`, ordered by `order`;
`queryCapabilities(property).sortable` gates whether a column header is clickable at all.

Facets: request `facets=` for every property where `queryCapabilities(property).facetable`,
render the returned `FacetBucketResponse[]` as a `MatChipListbox` inside an expansion panel per
facetable property; selecting a bucket appends a `filter[p][eq]=value` clause. Filter controls per
clause are chosen by datatype (string → `eq/ne/contains/startswith/in/isnull/notnull`; numeric/date
→ the comparison operators plus a range control that emits a `gte`+`lte` pair on the same
property, which the server ANDs together; relationship → `eq/in/isnull/notnull`, value supplied by
`ui-kit`'s relationship picker).

**All browse state lives in the URL query string**, serialised/deserialised through exactly one
service (`BrowseQueryStateService`, wrapping `core-api`'s `toHttpParams`/`fromQueryParamMap`), so a
deep link and the browser's back/forward both work without any extra plumbing. Unit-test this
service's URL ⇄ state mapping in both directions independently of the grid component itself.

**The capability mirror is best-effort, not authoritative** (Phase 3's own caveat): the UI must
degrade gracefully when the server still rejects a query with 400 `invalid-query` (e.g. a
multi-valued property the client-side mirror missed, or an operator/datatype mismatch the mirror
doesn't model at all) by mapping `violations[].property` back onto the offending filter chip
rather than failing silently or crashing.

### Detail, create, edit

A form built dynamically from `TypeDetailResponse.properties` via `ui-kit`'s `sq-dynamic-field`,
grouped into `mat-expansion-panel`s by `group` (ungrouped fields first). Create is `POST
/api/objects/{type}` with a `CreateObjectRequest`; edit is `PUT /api/objects/{type}/{id}`.

**Optimistic concurrency, the exact protocol** (from Phase 3's `ETagRegistry`/`etagInterceptor`,
already implemented — this feature just has to use it correctly, not reimplement it): rely on the
interceptor to attach `If-Match` automatically; **never** also set `UpdateObjectRequest.version`
in the body — sending both risks the 400 body/header-mismatch path, and the interceptor's whole
design point is making that unreachable. On a 409 `stale-object` (`ApiError.expectedVersion` set),
open `ui-kit`'s `sq-conflict-dialog`: refetch the server's current copy, show the three-way
comparison, and let the user choose "reapply mine" (merge the local edit onto the fresh copy, then
re-`PUT` — the interceptor will have already recorded the fresh ETag from the refetch) or "discard
mine." **Never auto-retry silently.**

### Transitions and signals

`GET /api/objects/{type}/{id}/transitions` lists only the `USER_ACTION` transitions available
*from the object's current state* (the backend still filters this endpoint — see Phase 1) — render
these as buttons; firing one is `POST /api/objects/{type}/{id}/transitions/{name}`, relying on the
interceptor for `If-Match` the same way edit does. A 409 `transition-not-available`
(`ApiError.transitionName` set) means refresh the transitions list and surface `reason`.

Signals are a *separate* concept this feature must not conflate with transitions (Phase 8 owns the
signal *catalogue*, across all types; this feature only needs to let a user fire one that's
already known to apply to the current object). `POST /api/objects/{type}/{id}/signals/{name}`
returns **202 with no body** — show "dispatched — takes effect asynchronously," then refetch the
object after a short delay and again on demand. **Never claim the transition happened** just
because the signal was accepted; the server processes signals out of band.

## Files (to be created)

- `projects/objects/src/lib/browse/` — the grid, filter/facet panel, `BrowseQueryStateService`.
- `projects/objects/src/lib/detail/` — view/edit, the dynamic form, transitions panel.
- `projects/objects/src/lib/create/`
- `projects/objects/src/lib/signals/` — the per-object "fire a known signal" panel (distinct from
  Phase 8's cross-type catalogue).
- `projects/objects/src/lib/objects.routes.ts` — replaces the Phase 2 placeholder with real routes
  (type list → browse → detail/edit → create), still exported as `objectsRoutes` so the shell's
  `app.routes.ts` needs no change.
- `projects/objects/src/public-api.ts`

## Verification

`npx ng test objects --watch=false`, `npx ng lint objects`, `npx ng build sequeless-shell
--configuration production`. Beyond unit tests, this is also where the Phase 9 end-to-end specs
(create → edit → force a stale conflict in two tabs → recover) actually exercise something real —
see `docs/specs/phase-9-shell-composition-and-polish.md`.
