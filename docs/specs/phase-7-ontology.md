# Phase 7 — ontology feature: authoring UI

**Status: not started.** Depends on `core-api` (done), `rdf` (Phase 4), and `ui-kit` (Phase 5,
specifically the Turtle editor and problem banner).

Note the name collision with the *backend's* Phase 7 (`docs/architecture/prompts/phase-7.md`,
meta-model evolution and data migration) — they are different pieces of work in different parts of
the repo, numbered independently. This phase explicitly avoids pre-empting that backend work (see
below).

## Context

There is no structured ontology-authoring endpoint (Phase 1/4's context). This feature is the
Material UI wrapped around Phase 4's `rdf` round-trip: load the whole document, let the user edit
a structured view of classes, properties and state machines, preview the diff, and send the whole
document back.

## Design

### Layout

A type list (from `GET /api/types`, the same lightweight `TypeSummaryResponse[]` Phase 6's type
picker could reuse) driving into a per-type editor: class metadata (label, abstract, super-types),
a property list with an editor per property (datatype, cardinality, display hints, derivation),
and the state machine editor (states, transitions, triggers, guards) sharing the Phase 8 trigger
editors — see that phase's spec for the on-change/timer/signal/guard controls themselves; this
feature just hosts them for the *authoring* direction, where Phase 8 hosts the *inspection* and
*signal-firing* direction against the same underlying data.

A raw-Turtle tab (`ui-kit`'s `sq-turtle-editor`) is always available as an escape hatch alongside
the structured forms — anything the structured UI doesn't yet cover is still editable there,
operating on the same `OntologyDocument` so structured and raw edits compose rather than conflict.

### The import flow

1. User edits something structured or in the raw editor → an `apply*` function from `rdf`
   (Phase 4) produces a `QuadDelta`, applied to the working store, pushed onto the undo journal.
2. **Preview before sending**: a quad-level delta grouped by subject, plus a text diff (`ui-kit`'s
   CodeMirror `@codemirror/merge` integration) of the base document's serialisation against the
   working store's. The diff view's input is a plain view-model, not tied to how it was computed —
   see "Phase 7 forward-compatibility" below for why that indirection matters.
3. `POST /api/ontology` with the working store's full serialisation.
4. **200** (`OntologyImportResponse`): rebase base := working, refetch `GET /api/types` (import
   already rebuilt the server's snapshot — **do not** also call `POST /api/types/reload`, that
   would be redundant).
5. **422** (`OntologyReportResponse`, `{consistent: false, issues}`): route issues back onto the
   form. Each generated form control registers the subject IRI it edits in a lookup map while
   being built; on 422, group `issues` by `subjectIri` → `control.setErrors({server: message})` on
   a match (and scroll the first into view); issues with no matching registered control (or a
   `null`/empty `subjectIri`) go into `ui-kit`'s `sq-problem-banner` instead. Mirror the same list
   as CodeMirror `lint` diagnostics on the raw-Turtle tab, matched by searching the text for the
   subject IRI.

### A backend error shape this feature must defend against

A *syntactically invalid* Turtle body is not handled by `ApiExceptionAdvice` at all — Jena's
`RiotException` is unmapped, so the response is Spring's bare default error JSON with status
**500**, not an `OntologyReportResponse`. Mitigation: this feature must parse with `rdf`'s `N3`
store client-side *before* ever POSTing, and refuse to send anything that fails to parse — so this
path should be unreachable in practice. `core-api`'s error mapper (Phase 3) still has to degrade
gracefully (`kind: 'spring-default'`) if it happens anyway, e.g. from a bug in the client-side
parse-before-send guard.

### Phase 7 (backend) forward-compatibility — do not pre-empt it

The backend's own Phase 7 plans a server-side `POST /ontology?mode=dryRun` returning a structured
diff and classification, which would eventually replace the client-side diff in step 2 above with
a server-computed one. Put the current client-side diff behind
`SEQUELESS_FEATURES.serverSideOntologyDiff` (Phase 3's token, already defaulted to `false`), and
keep the diff preview component's input a plain view-model (`OntologyDiff`, or similar) so a
future server-computed diff can populate the exact same component without a rewrite. **Implement
nothing from the backend's Phase 7 here** — no dry-run call, no migration-plan UI — those wait for
that backend work to land.

## Files (to be created)

- `projects/ontology/src/lib/type-list/`
- `projects/ontology/src/lib/type-editor/` — class metadata, property list and editors.
- `projects/ontology/src/lib/state-machine-editor/` — hosts Phase 8's trigger editors in authoring
  mode.
- `projects/ontology/src/lib/diff-preview/` — the `OntologyDiff` view-model and its dialog/panel.
- `projects/ontology/src/lib/import-result/`
- `projects/ontology/src/lib/ontology.routes.ts` — replaces the Phase 2 placeholder, still
  exported as `ontologyRoutes`.
- `projects/ontology/src/public-api.ts`

## Verification

`npx ng test ontology --watch=false`, `npx ng lint ontology`, `npx ng build sequeless-shell
--configuration production`. The real proof of this feature is end-to-end (Phase 9): edit a
property label → preview the diff → import → see it reflected in the type list and in Phase 6's
browse-grid column headers, since those are driven by the same `GET /api/types` data this feature
just changed.
