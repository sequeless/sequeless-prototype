# Phase 6.5 plan — objects feature: completion

Branch: `sequeless-prototype-ui` (stacked on the Phase 6 commits, `4d9d8f3..13b70d4`). Spec: still
`docs/specs/phase-6-objects.md`; there is no new spec. This plan holds the Phase 6 plan items that were not
built (`docs/plans/phase-6-objects.md`), plus the runtime check Phase 6 never had. All paths below are
relative to `sequeless-ui/` unless they start with `docs/`.

## State at the start

- Every section of the Phase 6 **spec** is implemented and committed: browse with facets, filters,
  range and relationship picker; URL state; 400 `invalid-query` mapping; detail/create/edit with ETag
  concurrency and the conflict dialog; delete; transitions; signals with delayed refetch; the
  `OBJECT_SIGNALS_PANEL` plug point; type list; routes; public API.
- Verified: objects 133/133, core-api 182/182, ui-kit 394/394, lint clean on those projects, production
  build initial total 683.69 kB (under the 1 MB error budget; over the 500 kB warning).
- **Never run against the backend.** Every spec mocks the HTTP clients.
- Two failures predate this work: `projects/sequeless-shell/src/app/app.spec.ts` (NG0201
  `ActivatedRoute`, no `provideRouter`) and four `no-empty-function` lint errors in the committed
  `projects/core-api/src/lib/clients/api-error-operator.spec.ts`.

## Findings that shape the plan

- Transitions live inline in `projects/objects/src/lib/detail/object-detail.component.ts` (`transitions`,
  `transitionError`, `loadTransitions()`, `fire()`), with their tests in `object-detail.component.spec.ts`.
- Facets, active-filter chips, the add-filter form, the range control and the relationship picker live
  inline in `projects/objects/src/lib/browse/object-browse.ts`, with their tests in `object-browse.spec.ts`.
  Some of those tests reach into the component (`fixture.componentInstance.relationshipControl`).
- The detail view is edit-only: it always renders an enabled `sq-object-form`.
- Browse has no free-text search box, although `BrowseQueryState.q` and `toHttpParams` already carry `q`.
- Running the full stack needs `docker-compose.yml` (PostgreSQL, Temporal) plus the Spring app in
  `sequeless-app`, with the shell dev server proxying `/api` via `sequeless-ui/proxy.conf.json`.

## Decisions (each reversible)

| # | Decision | Why it stays reversible |
|---|---|---|
| E1 | Extractions are **behaviour-preserving**: the existing tests keep their intent and names, and move to the new component's spec only when they test that component's own behaviour. | Pure refactors; a revert restores the inline version. |
| E2 | `SqTransitionsPanel` keeps today's behaviour, including refetching after a successful transition and the `stale-object` refetch-and-report path. It does **not** switch to the plan's `store.set(result)`. | The refetch path is tested and proven; switching is a one-line change later. |
| E3 | The disabled-reason hint stays the native `title` attribute (no `MatTooltip`). | One binding to swap. |
| E4 | View mode renders the same `sq-object-form` with the whole form disabled. Edit rebuilds the form with `buildObjectForm`, so read-only controls stay disabled. Cancel discards the edits and returns to view mode. | No new rendering path; the form helpers are reused. |
| E5 | The search box writes `q` through `BrowseQueryStateService.update`, on submit (Enter or a Search button), not per keystroke. The service already resets the page when `q` changes. | No debounce logic to maintain; easy to change to live search. |
| E6 | `SqFilterPanel` is presentational: inputs in, `filtersChange` out. It does not inject `BrowseQueryStateService`. Browse owns the URL. | Matches the plan's `[OBJ-FILTER]` contract; the panel can be reused elsewhere. |
| E7 | The two pre-existing failures are fixed only with the user's explicit approval, because both fixes edit committed specs. | Each is a two-line change in one file. |

## Units

### U1 — [OBJ-SEARCH] browse search box (small)
`projects/objects/src/lib/browse/object-browse.ts` adds a search input (`data-search`) and a submit control.
Submitting writes `q` via `query.update({ q })`; an empty submission removes `q`. The input shows
`query.state().q` on load.
Spec: add to `object-browse.spec.ts`:
- submitting a term writes `q` to the URL and resets the page;
- submitting an empty box removes `q` from the URL;
- the box shows the `q` from the initial URL.

### U2 — [OBJ-VIEW] detail view mode (medium)
`projects/objects/src/lib/detail/object-detail.component.ts`: the view opens in **view mode** (the form
fully disabled; Edit, Delete, transitions and signals available; no Save). **Edit** rebuilds an enabled form
(E4) and shows Save and Cancel. Cancel returns to view mode without saving. A successful save returns to
view mode. The conflict flow is unchanged and only reachable from edit mode.
Spec: add to `object-detail.component.spec.ts` (existing tests may need to click Edit first; keep their intent):
- opens in view mode with every control disabled and no Save button;
- Edit enables the editable controls and keeps read-only ones disabled;
- Cancel discards the edits and returns to view mode;
- a successful save returns to view mode;
- Delete and the transitions are available in view mode.

### U3 — [OBJ-TRANSITIONS] extract `SqTransitionsPanel` (medium)
New `projects/objects/src/lib/detail/transitions-panel.ts`, selector `sq-transitions-panel`. It injects
`ObjectDetailStore` and `ObjectsClient` and takes no inputs. It moves `transitions`, `transitionError`,
`loadTransitions()` and `fire()` out of the detail component, with today's behaviour (E2, E3). The detail
component renders `<sq-transitions-panel />`. Depends on U2 (same file).
Spec: new `transitions-panel.spec.ts`, moving the transition tests from the detail spec with the same intent:
- lists transitions as buttons, disabled when unavailable, with the reason as the title;
- a successful fire refetches the object and reloads the list;
- a 409 `transition-not-available` reloads the list and shows the detail inline, with no dialog;
- a 409 `stale-object` refetches the object and the list and reports the error, without retrying.
Keep one detail-spec test proving the panel is rendered.

### U4 — [OBJ-FILTER] extract `SqFilterPanel` (largest)
New `projects/objects/src/lib/browse/filter-panel.ts`, selector `sq-filter-panel`, per the Phase 6 plan's
`[OBJ-FILTER]`: inputs `type`, `filters`, `facets`, `errors`; output `filtersChange`. One
`mat-expansion-panel` per facet, each holding a `mat-chip-listbox`. It also moves the active-filter chips
(with the error at each chip's index), the add-filter form, the range control and the relationship picker
out of browse. Browse binds the panel and writes `filtersChange` to the URL (E6).
Spec: new `filter-panel.spec.ts` driving inputs and outputs (move the filter-behaviour tests from
`object-browse.spec.ts`, same intent). `object-browse.spec.ts` keeps the integration tests that prove a panel
change reaches the URL and that facets are requested.
- one expansion panel per facetable property;
- selecting a bucket emits the eq clause; deselecting removes it;
- removing a chip emits the filters without it; an error shows on its chip;
- the add-filter form offers the operators for the property's datatype, the range emits gte+lte, and the
  relationship picker supplies ids for eq/in;
- `filtersChange` is the only way the panel changes filters (no URL access).

### U5 — [HOUSEKEEPING] pre-existing failures (needs user approval, E7)
- `projects/sequeless-shell/src/app/app.spec.ts`: add `provideRouter([])` to its TestBed.
- `projects/core-api/src/lib/clients/api-error-operator.spec.ts`: replace the four empty arrow functions
  (`.catch(() => {})`) with a non-empty no-op, without changing what the tests assert.

### U6 — [RUNTIME-CHECK] run the real app once
Start the stack (`docker compose up -d`, the Spring app, `npx ng serve` with the proxy). Drive Chromium with
Playwright (pre-installed; launch with `executablePath: '/opt/pw-browsers/chromium'` if the pinned version
differs) through: type list → browse → create → edit → save → open the same object in a second page,
edit both, save the second → conflict dialog → reapply → delete. Record what happened and keep screenshots
outside the repo. If the stack cannot start here, report exactly what blocked it. Do not fake the check.
This unit writes no product code. A failure it finds becomes a new unit, with a failing spec first.

### U7 — docs
Mark the moved items in `docs/plans/phase-6-objects.md` as done in 6.5, and record any decision that changed.

## Not included (with the reason)

- **Request-race guard on browse:** in neither the spec nor the plan; it was a review suggestion.
- **Dirty-form prompt before delete:** rejected; deleting discards the edits anyway, and the confirm already
  guards the action.
- **`provideUiKit()` at the root:** decided against in Phase 6 (D4 in the Phase 6 plan, as amended).

## Task order

U1 → U2 → U3 → U4 (U2 before U3 because both edit the detail component), then U5 if approved, then U6, then
U7. Each unit: navigator review of the unit (one question) → failing spec → implementation → targeted spec,
whole project, lint → driver re-verification → commit.

## Verification

`npx ng test objects --watch=false`, `npx ng lint objects`, `npx ng test core-api --watch=false`,
`npx ng test sequeless-shell --watch=false`, `npx ng build sequeless-shell --configuration production`
(the initial total must stay under the 1 MB error budget), then U6's runtime check.

## How it was built

All seven units are done. U1-U3 were committed before the final pass (`4f60e09`, `8811550`, `ecc3b5a`); U4 is
`d771763`, U5 is `5deea0e`; U6 wrote no product code; U7 is this note.

- **U4:** `SqFilterPanel` takes `type`, `filters`, `facets`, `errors` and emits `filtersChange`; it injects neither the
  query service nor the router. Each facet is an expanded `mat-expansion-panel[data-facet]`, so facets stay visible as
  before (E1). The add-filter and range state moved into the panel; browse keeps the search form, table, paging and the URL.
  - **Contract change:** `errors` is `FilterErrors` (`byIndex` and `unmatched`), the type `violation-mapping` already
    produces, instead of the plan's `ReadonlyMap<number, string>`. Browse still shows `unmatched` problems itself.
  - **Tests moved** from `object-browse.spec.ts` to `filter-panel.spec.ts` with the same names: the facet-group test
    (now asserts `mat-expansion-panel`), the range pair, add-filter disabled, operators by datatype and the five
    relationship-filter tests (now asserting the emitted `filtersChange` instead of the URL). New in the panel spec:
    deselecting a bucket, chip removal, the error on a chip, and "never navigates". Browse keeps the integration tests.
- **U5 (user-approved):** `app.spec.ts` provides a router; `api-error-operator.spec.ts` uses `() => undefined` instead of
  empty functions. The router fix exposed a second failure the plan did not foresee: `should render title` asserted an
  `h1` ("Hello, sequeless-shell") from the Angular scaffold that the app never renders. The user chose to delete that test.
- **The "view mode" test noted as failing in the Phase 7 and 8 plans passes** at HEAD; those notes were stale.
- **U6 result (real run):** Postgres from `docker-compose.yml`, the Spring exec jar and `ng serve` with the proxy, driven
  by Playwright Chromium. Passed: type list, browse, create (201), view mode read-only with no Save, edit, a second page
  edits the same object, first save (200), second save returns 409 and the conflict dialog shows the field table,
  "Reapply my changes" sends exactly one more PUT (200) and no third, delete (204). The filter panel also ran for real:
  facet counts, a facet click writing `filter[status][eq]=Open`, chip removal and the add-filter form.
  - To reach Postgres from this sandbox the app needed `SEQUELESS_PERSISTENCE_URL=jdbc:postgresql://host.docker.internal:5432/sequeless`.
  - Observations, not bugs: the reference ontology's `title` and `description` have no maximum cardinality, so the form shows
    them as a repeatable list with a bare "Add" button and no label, inside a collapsed "General" group. The dev form is unstyled.
  - Screenshots are outside the repo.

## Verification result

objects 149 tests, core-api 188, sequeless-shell 8 pass; `ng lint` clean on objects, core-api and sequeless-shell;
production build initial total 757.86 kB (under the 1 MB error budget, over the 500 kB warning).
