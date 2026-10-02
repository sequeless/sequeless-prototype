# Phase 8 — automation feature: triggers and signals

**Status: not started.** Depends on `core-api` (done), `rdf` (Phase 4, for the trigger-editing
deltas), and `ui-kit` (Phase 5, specifically the state-machine graph).

## Context

A type's state machine can have four kinds of transition trigger (`USER_ACTION`, `ON_CHANGE`,
`TIMER`, `EXTERNAL_SIGNAL`), but only `USER_ACTION` ones are things a client can actually call.
This feature is where a user sees the *whole* machine — including the automated kinds they can
never fire themselves — and where cross-type signal firing lives, since a signal only makes sense
in the context of "which types declare it, and from which state."

## Design

### The asymmetry this feature must render correctly, not conflate

`GET /api/types/{iri}` (Phase 1 already lifted the filter here) returns **every** trigger kind in
`StateMachineResponse.transitions` — that's what this feature's diagram is built from.
`GET /api/objects/{type}/{id}/transitions` **still filters to `USER_ACTION` only** — that's what
Phase 6's per-object action buttons are built from. **This feature renders the diagram from the
type endpoint and must never use the per-object endpoint for it** — the two serve genuinely
different purposes (diagram the whole machine vs. list what's callable right now), and mixing
them up would either hide real automation from the diagram or offer the user a button for
something that will 409.

### State-machine view

`ui-kit`'s `sq-state-machine-graph` (Phase 5), fed `TypeDetailResponse.stateMachine` directly — no
extra data needed, it's already all in one response. Clicking an edge opens that transition's
trigger editor (below); clicking a node is a shortcut into Phase 6's browse grid, pre-filtered to
objects currently in that state.

### Trigger editors (authoring, via `rdf`'s Phase-4 edit functions)

These are shared with Phase 7's state-machine editor (which hosts them in "editing this type's
machine" mode) — defined here because they're conceptually automation's domain, not ontology
authoring's:

- **On-change**: a multi-select over the type's own forward relationship properties (from
  `TypeDetailResponse.properties` where `kind === 'relationship'`), writing `sq:watch`.
- **Timer**: `ui-kit`'s `sq-duration-field`, writing an ISO-8601 `sq:after`, with a plain-language
  echo ("fires 72h after entering OnHold") computed from the current from-state's label.
- **Signal**: a free-text `sq:signalName` field with a client-side uniqueness check *within this
  type's own transitions* (not global — two different types may each declare the same signal name
  for unrelated purposes) and a warning, not a hard block, if it collides.
- **Guard**: a plain JEXL text field, labelled clearly **"validated server-side only"** — there is
  no expression-validation endpoint, and inventing one here would collide with the SPI's own
  `ExpressionPort` abstraction rather than reuse it.

Changing a transition's trigger kind must go through `rdf`'s dedicated edit function (Phase 4's
table), which also removes the previous kind's now-stale sibling term — this feature must not
reimplement that cleanup itself.

### Signal catalogue and firing

Scan **every** type's `StateMachineResponse.transitions` (one `GET /api/types/{iri}` per type, or
a batched view if `GET /api/types` is extended to include it later — not currently, so this is
N+1 against today's API, acceptable at this feature's scale) for `trigger === 'EXTERNAL_SIGNAL'`,
producing a flat catalogue of `{ typeIri, signalName, from, to }`. On an object's detail page
(hosted inside Phase 6's detail view, but populated from this feature's catalogue), show signals
whose `from` matches the object's current state as enabled and the rest greyed out with "requires
state X." Firing is `POST /api/objects/{type}/{id}/signals/{name}` with an optional JSON payload
editor for the signal's arbitrary body — **this is the same call Phase 6's per-object signal panel
makes**; the distinction is that *this* feature is the one that knows a signal exists at all
(Phase 6 only renders a button for a signal it's told about). A 400 `unknown-signal`
(`ApiError.typeIri`/`signalName` set) is a real, reachable error — the catalogue can go stale
between page loads if the ontology changes underneath it — and must surface as a field error, not
crash the panel.

### Known gaps — display honestly, do not paper over

No endpoint exposes *scheduled* timers or the outbox (`sq_outbox`, `docs/architecture/
automation.md`), so a timer trigger is rendered statically from its `sq:after` duration — the UI
cannot say "this will fire in 6 hours," only "this fires 72h after entering OnHold." There is no
per-object automation history endpoint either. State both of these directly in the UI (a small
note near the timer/signal controls) rather than inventing a fake history or a countdown that
isn't actually backed by anything — and don't build either: an outbox-inspection endpoint or a
history endpoint would be new backend surface area that collides with the backend's own Phase 7/8
plans, not something to add unilaterally from the frontend side.

## Files (to be created)

- `projects/automation/src/lib/state-machine-view/` — hosts `ui-kit`'s graph against one type.
- `projects/automation/src/lib/trigger-editors/` — on-change, timer, signal, guard — shared with
  Phase 7.
- `projects/automation/src/lib/signal-catalogue/` — the cross-type scan and the per-object firing
  panel's data source.
- `projects/automation/src/lib/automation.routes.ts` — replaces the Phase 2 placeholder, still
  exported as `automationRoutes`.
- `projects/automation/src/public-api.ts`

## Verification

`npx ng test automation --watch=false`, `npx ng lint automation`, `npx ng build sequeless-shell
--configuration production`. End-to-end (Phase 9): view a type with on-change/timer/signal
transitions and confirm all four trigger kinds render with distinct edge styling; fire a known
signal on an object and confirm the state changes only after a refetch, never immediately on the
202 response.
