# Phase 5 — ui-kit library: presentational components

**Status: not started.** Depends only on `core-api` (done); does not depend on `rdf`. Needed by
Phase 6 (dynamic fields, relationship picker), Phase 7 (Turtle editor, problem banner), and
Phase 8 (state-machine graph).

## Context

Every feature library needs a handful of the same Material-based building blocks: a way to render
an input for an arbitrary `PropertyResponse` without knowing its datatype ahead of time, a way to
pick a related object, a way to show a server error inline, a way to resolve a version conflict,
and — for the two authoring-heavy features — a Turtle text editor and a state-machine diagram.
`ui-kit` holds all of it, with no HTTP calls of its own: everything here takes data as inputs and
emits events, so these components are trivially testable without a backend.

## Design

### Dynamic fields — `sq-dynamic-field` + one component per datatype

A host component `sq-dynamic-field` takes a `PropertyResponse` and a `FormControl`/`FormRecord`
entry, and renders the right Material control via the `FIELD_RENDERERS` token (Phase 3) so the
mapping from datatype to control can be overridden without touching this library:

| `datatype` | Control | Notes |
|---|---|---|
| `STRING` | `matInput` (textarea if `searchable` with no apparent length hint) | |
| `BOOLEAN` | `mat-slide-toggle` | |
| `INTEGER`, `LONG` | number input, integer validator, `step="1"` | |
| `DECIMAL`, `DOUBLE` | number input, free step | |
| `DATE` | `matDatepicker` | emits `YYYY-MM-DD` |
| `TIME` | time input | emits `HH:mm[:ss]` |
| `DATE_TIME` | datepicker + time | emits an ISO-8601 instant string |
| `DURATION` | `sq-duration-field` (below) | emits `PnDTnHnMn.nS` |
| `ANY_URI` | url input + URL validator | |
| relationship (any `RelationshipPropertyResponse`) | `sq-relationship-picker` (below) | |

Cross-cutting rules, independent of datatype: `cardinalityMin >= 1` → `Validators.required`;
`cardinalityMax > 1 || cardinalityMax === null` → a repeatable `FormArray`/chip-list rendering
instead of a single control; `readOnly: true` → the control renders disabled, with
`derivation.summary` shown as an explanatory hint (recall from Phase 3/the backend that
`sq:derivedBy` *forces* `readOnly`, so this case is common, not an edge case); `hidden: true` →
the field is omitted from the form entirely, not just visually hidden.

**Every emitted value must match `ObjectPropertyMapper.toWireValue`'s coercion table** (the
backend's inverse operation): a `DATE`/`DATE_TIME` field emits an ISO string, not a `Date` object;
a relationship field emits the bare target UUID string (or an array of them for a multi-valued
relationship), never a nested object. This is the single most important thing each per-datatype
spec in this phase must assert.

**Forms technology: classic Reactive Forms** (`FormRecord`/`FormArray`, built dynamically from the
`TypeDetailResponse.properties` array), **not Signal Forms.** Signal Forms are newly stable in
Angular 22 and are schema-first/statically-typed by design — the opposite of a form whose shape is
only known at runtime from the ontology. Record this as a decision in this library rather than
re-debating it per component: if a Signal Forms renderer is ever wanted, it's a new value behind
`FIELD_RENDERERS`, not a rewrite of this library.

### Relationship picker — `sq-relationship-picker`

`mat-autocomplete` backed by `GET /api/objects/{targetTypeIri}?q=<term>&size=20` (debounced on
input), with the visible label built from the target's first non-hidden `STRING` property,
falling back to the raw id if none exists. **The emitted value is the bare target UUID string**,
multi-valued relationships render as a `mat-chip-grid` of picked UUIDs with their resolved labels
cached locally so removing a chip doesn't require a re-fetch.

### Turtle editor — `sq-turtle-editor`, behind `TURTLE_EDITOR`

CodeMirror 6 (`codemirror@6` + `@codemirror/legacy-modes`'s bundled Turtle `StreamLanguage` — no
grammar needs writing — plus `@codemirror/lint` for issue gutters and `@codemirror/merge` for the
before/after diff view Phase 7 needs). **Lazy-loaded only on the ontology route** — this is
~250–400 KB and must never land in the main bundle. Rejected: Monaco (3 MB+, needs web-worker
plumbing that fights `@angular/build`'s esbuild pipeline, and its Turtle support is no better than
CodeMirror's). The component implements a small `TextEditorAdapter` interface
(`mount/getValue/setValue/setDiagnostics/dispose`) so a different editor can be substituted behind
`TURTLE_EDITOR` without the `ontology` feature library knowing which one is mounted.

### State-machine graph — `sq-state-machine-graph`, behind `GRAPH_LAYOUT`

`@dagrejs/dagre@3` (~90 KB, synchronous, no worker — a layered/Sugiyama layout is exactly right
for a state machine) computing node/edge positions from a `StateMachineResponse`, rendered as plain
SVG by this component. Edge styling encodes `TransitionSummaryResponse.trigger`: solid for
`USER_ACTION`, dashed for `ON_CHANGE`, dotted with a clock glyph for `TIMER`, double-stroke with an
antenna glyph for `EXTERNAL_SIGNAL`; a lock glyph when `hasGuard`. Edge labels combine `name` with
whichever of `after`/`signalName`/`watch.join(', ')` applies to that trigger kind. Rejected as the
*default*: `@swimlane/ngx-graph` (still on major version 13, historically lags Angular majors — a
real risk against v22's zoneless/OnPush defaults) and `elkjs` (~600 KB, async/worker — kept in mind
as the natural upgrade behind `GRAPH_LAYOUT` if orthogonal edge routing is ever worth the weight,
since the token's shape is already async for exactly this reason).

### `sq-duration-field`

A small composite control (days/hours/minutes/seconds number inputs) that parses and emits an
ISO-8601 duration string, shared by the dynamic-field table above (for a `DURATION`-typed
property) and Phase 8's timer-trigger editor (for `sq:after`).

### `sq-problem-banner`

Renders a list of server-reported problems with severity styling. Takes either an `ApiError`
(Phase 3's shape, for a generic request failure) or a list of `OntologyIssue`s (for an ontology
import's `issues` array) and renders them uniformly — Phase 7 is the main consumer, routing
per-field issues onto form controls and anything left over into this banner.

### `sq-conflict-dialog`

A `MatDialog` component for the 409 `stale-object` recovery flow (Phase 6): given the local edit,
the server's current copy, and the common base, renders a per-field three-way comparison and
offers "reapply my changes onto the server version" or "discard mine" — never auto-resolves
silently.

## Files (to be created)

- `projects/ui-kit/src/lib/dynamic-field/` — the host component plus one component per datatype
  row above, and the default `FIELD_RENDERERS` map.
- `projects/ui-kit/src/lib/relationship-picker/`
- `projects/ui-kit/src/lib/duration-field/`
- `projects/ui-kit/src/lib/turtle-editor/` — the component plus the default `TURTLE_EDITOR` value
  and the `TextEditorAdapter` interface.
- `projects/ui-kit/src/lib/state-machine-graph/` — the component plus the default `GRAPH_LAYOUT`
  value.
- `projects/ui-kit/src/lib/problem-banner/`
- `projects/ui-kit/src/lib/conflict-dialog/`
- `projects/ui-kit/src/public-api.ts`

Test-first per component. The dynamic-field specs are the highest-value ones in this phase — one
per datatype, each asserting the emitted wire value matches the coercion table above exactly.

## Verification

`npx ng test ui-kit --watch=false`, `npx ng lint ui-kit`, `npx ng build sequeless-shell
--configuration production` (confirms the CodeMirror/dagre imports are actually lazy and don't
bloat the main bundle — check the build's reported chunk sizes, not just that it succeeds).
