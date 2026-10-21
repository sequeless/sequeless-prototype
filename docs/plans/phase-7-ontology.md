# Phase 7 plan — ontology feature (authoring UI)

Branch: `phase-7-ontology` (stacked on `phase-6-objects`). Spec: `docs/specs/phase-7-ontology.md`.
**Numbering clash:** this is *frontend* Phase 7. The backend's Phase 7 (`docs/architecture/prompts/phase-7.md`:
meta-model evolution, `POST /ontology?mode=dryRun`, migration plans) is unrelated and **not implemented here**.
Execution: a queued batch of stateless `claude -p` calls (Phase A writes one spec file per call, Phase B writes
one implementation file per call against it), each followed by a scoped commit. All paths below are relative to
`sequeless-ui/`. Phase 6 tags (`[CORE-*]`, `[OBJ-*]`, `[SHELL-CONFIG]`) are defined in `docs/plans/phase-6-objects.md`
and reused, never redefined.

## Findings that shape the plan

- `projects/ontology` is a placeholder: `Ontology` component + `ontologyRoutes = [{path:'', component: Ontology}]`.
  The shell already lazy-loads it at `ontology`; `app.routes.ts` must not change.
- **rdf (Phase 4) is done**: `parseOntology` (sync, throws on invalid Turtle), `serializeOntology(doc)`,
  `OntologyDocument {baseQuads, store, prefixes}`, `RdfStore` (`clone`, `apply`), `DeltaJournal`
  (apply/undo/redo/canUndo/canRedo), projections `readClasses/readProperties/readStateMachine/readCardinality`, edits
  `relabel, addAttributeProperty, addRelationshipProperty, setCardinality, setDerivation, setTransitionTrigger, setGuard`
  (each returns a `QuadDelta`). There is no class-level edit for `abstract`/super-types: only `relabel`.
- **ui-kit (Phase 5) is done**: `SqTurtleEditor` (`value`, `diagnostics: EditorDiagnostic[]`, `valueChange`; adapter
  from `TURTLE_EDITOR`), `SqProblemBanner` (`problems: ApiError | OntologyIssue[]`), `SqStateMachineGraph`,
  `SqDurationField`, `provideUiKit()`. The `@codemirror/merge` diff integration the spec mentions is **not** exported.
- core-api has `OntologyIssue`, `OntologyReportResponse`, `OntologyImportResponse`, `SEQUELESS_FEATURES.serverSideOntologyDiff`
  (Phase 3, default `false`), `RDF_STORE_FACTORY`. After Phase 6 it also has `TypesClient`, `provideSequelessHttp()`.
  It has **no ontology client**. Spec implies `GET /api/ontology` (Turtle) and `POST /api/ontology` (200
  `OntologyImportResponse`, 422 `OntologyReportResponse`); the GET path/content-type must be confirmed against the backend controller in task B1.
- No `projects/automation` trigger editors exist yet (Phase 8), so ontology must not import automation.
- `git show main:GLOSSARY.md` defines **Ontology**, **Ontology version**, **Schema**, **Behaviour** (reuse: the UI says
  "Ontology" for the Turtle document, "Schema" for what `GET /api/types` returns). `docs/adr` does **not exist on main**; no ADR to reuse.

## Decisions (each reversible or configurable)

| # | Decision | Why it stays reversible |
|---|---|---|
| D1 | `OntologyClient` (`load(): Observable<string>`, `import(turtle)`) lives in core-api, `providedIn:'root'`, errors via `rethrowAsApiError` [CORE-ERR]. 422 is **returned as a value** (`ImportOutcome`), not thrown. **Switch:** `{provide: OntologyClient, useClass: Other}`. | Port-like class; fakes in tests. |
| D2 | One `OntologySession` (component-scoped service) owns document, journal, working store, base text. Forms/raw tab are views over it. **Switch:** provide another class at the route. | Single seam; UI never touches `N3` directly. |
| D3 | Raw-Turtle edits are applied as a *replace-all* delta (diff of quad sets) so they journal like structured edits. **Switch:** `computeTextDelta` is a pure function; swap strategy. | Isolated in one file. |
| D4 | The diff view takes a plain `OntologyDiff` view-model. The client-side producer is chosen by `ONTOLOGY_DIFF_SOURCE` (default: client unless `SEQUELESS_FEATURES.serverSideOntologyDiff`, which then throws "not implemented"). **Switch:** flag or token. | Backend Phase 7 plugs in later, no UI rewrite. |
| D5 | Text diff is rendered by a small ontology-local `sq-text-diff` using `@codemirror/merge` through the `TEXT_DIFF_VIEW` token (default CodeMirror impl). Fallback impl: `<pre>` unified diff. **Switch:** token. | ui-kit untouched; replaceable lib. |
| D6 | Parse-before-send guard: import is refused if `parseOntology(text)` throws; error shown in the banner. `kind:'spring-default'` errors from the server are shown as-is. **Switch:** guard lives in `OntologySession.prepareImport`. | One function. |
| D7 | **Extension point** `ONTOLOGY_EDITOR_PANELS`: multi-provider `InjectionToken<readonly OntologyEditorPanel[]>` (default `[]`). Phase 8 contributes trigger editors with `{provide: ONTOLOGY_EDITOR_PANELS, multi: true, useValue: …}`; ontology renders by `slot`. Ontology never imports automation. **Switch:** add/remove a provider. | Without providers the state-machine editor shows a read-only fallback list + raw tab. |
| D8 | Control→subject registry is a plain `ControlRegistry` class (`register(iri, control)`), not form-library magic. **Switch:** replace the class. | Pure, testable. |
| D9 | Issue→editor routing: form errors via `setErrors({server})`; Turtle diagnostics by locating `subjectIri` text (first occurrence; `{from:0,to:0}` if absent). **Switch:** `issuesToDiagnostics` is pure. | Local to one file. |
| D10 | Class-level edits missing from rdf (abstract, super-types) are **not** added to rdf here: they are edited through the raw tab. The class form shows label (via `relabel`) and read-only abstract/super-types. **Switch:** upgrade when rdf grows `setAbstract`/`setSuperTypes`. | Avoids a cross-phase change. |
| D11 | After a 200 import: rebase `base := working`, clear journal, refetch `TypesClient.list()`. **No** call to `/api/types/reload`. | Spec-mandated; in `OntologySession.importSucceeded`. |
| D12 | Navigation: `ontologyRoutes` = list, `:type` editor, `import-result` shown as a dialog, not a route. | Routes in one file. |

## Shared contract

### [CORE-ONTOLOGY]
`projects/core-api/src/lib/clients/ontology-client.ts` (also re-exported from `public-api.ts`)
```ts
export type ImportOutcome =
  | { status: 'accepted'; response: OntologyImportResponse }
  | { status: 'rejected'; report: OntologyReportResponse };      // HTTP 422
@Injectable({ providedIn: 'root' }) export class OntologyClient {
  load(): Observable<string>;                      // GET {base}/ontology, responseType text, Accept text/turtle
  import(turtle: string): Observable<ImportOutcome>; // POST {base}/ontology, Content-Type text/turtle; 422 -> rejected; others rethrown via mapper
}
```

### [ONT-DELTA]
`projects/ontology/src/lib/session/text-delta.ts` (pure)
```ts
export function computeTextDelta(store: RdfStore, turtle: string): QuadDelta; // parseOntology(turtle).store vs store; throws on invalid Turtle
```

### [ONT-SESSION]
`projects/ontology/src/lib/session/ontology-session.ts`
```ts
@Injectable() export class OntologySession {
  readonly store: Signal<RdfStore | null>; readonly revision: Signal<number>; // bumps on every apply/undo/redo
  readonly loadError: Signal<ApiError | null>; readonly dirty: Signal<boolean>;
  load(): Promise<void>;                          // OntologyClient.load -> parseOntology -> DeltaJournal
  apply(delta: QuadDelta): void; applyText(turtle: string): void; // applyText uses computeTextDelta
  undo(): boolean; redo(): boolean; readonly canUndo: Signal<boolean>; readonly canRedo: Signal<boolean>;
  currentText(): Promise<string>; baseText(): string;
  prepareImport(): Promise<{ ok: true; text: string } | { ok: false; error: string }>; // D6 guard
  importSucceeded(r: OntologyImportResponse): void;                                       // D11
  readonly lastRejection: Signal<readonly OntologyIssue[]>; setRejection(report: OntologyReportResponse): void;
}
```

### [ONT-REGISTRY]
`projects/ontology/src/lib/issues/control-registry.ts` (pure)
```ts
export class ControlRegistry { register(subjectIri: string, control: AbstractControl): void; clear(): void;
  routeIssues(issues: readonly OntologyIssue[]): { matchedFirst: AbstractControl | null; unmatched: OntologyIssue[] }; }
// matches -> control.setErrors({server: message}); null/empty subjectIri or unregistered -> unmatched
export function issuesToDiagnostics(text: string, issues: readonly OntologyIssue[]): EditorDiagnostic[];
```

### [ONT-DIFF]
`projects/ontology/src/lib/diff-preview/ontology-diff.ts` (pure)
```ts
export interface SubjectChange { subject: string; added: readonly string[]; removed: readonly string[] } // N-Triples-ish strings
export interface OntologyDiff { subjects: readonly SubjectChange[]; baseText: string; workingText: string }
export interface OntologyDiffSource { diff(base: OntologyDocument, working: RdfStore): Promise<OntologyDiff> }
export const ONTOLOGY_DIFF_SOURCE: InjectionToken<OntologyDiffSource>; // root factory: client-side impl (D4)
export function clientSideDiff(): OntologyDiffSource;
export function groupBySubject(delta: QuadDelta): SubjectChange[];
```

### [ONT-TEXTDIFF]
`projects/ontology/src/lib/diff-preview/text-diff.ts` — `SqTextDiff`, selector `sq-text-diff`; inputs `base = input.required<string>()`,
`working = input.required<string>()`. `TEXT_DIFF_VIEW: InjectionToken<TextDiffFactory>` where
`type TextDiffFactory = (host: HTMLElement) => { update(base: string, working: string): void; dispose(): void }`; default
uses `@codemirror/merge` `MergeView`.

### [ONT-PREVIEW]
`projects/ontology/src/lib/diff-preview/diff-preview.ts` — `SqDiffPreview` (`diff = input.required<OntologyDiff>()`): quads grouped by
subject (`mat-expansion-panel`) + `sq-text-diff`. `projects/ontology/src/lib/diff-preview/diff-preview-dialog.ts`:
`openDiffPreview(dialog: MatDialog, diff: OntologyDiff): Observable<boolean>` ("Import" resolves `true`).

### [ONT-EXT]
`projects/ontology/src/lib/extension/editor-panels.ts` — **the Phase 8 seam**
```ts
export interface OntologyEditorPanelContext {            // passed as inputs via ngComponentOutlet / inputBinding
  readonly typeIri: string; readonly session: OntologySession; readonly registry: ControlRegistry;
  readonly transitionIri?: string;
}
export interface OntologyEditorPanel { id: string; slot: 'transition-trigger' | 'transition-guard' | 'type-extra'; order?: number;
  component: Type<unknown>; /* contract: inputs `context = input.required<OntologyEditorPanelContext>()` */ }
export const ONTOLOGY_EDITOR_PANELS: InjectionToken<readonly OntologyEditorPanel[]>; // multi, providedIn root factory () => []
export function panelsFor(all: readonly OntologyEditorPanel[], slot: OntologyEditorPanel['slot']): OntologyEditorPanel[]; // sorted by order, then id
export function provideOntologyEditorPanel(p: OntologyEditorPanel): Provider; // { provide: ONTOLOGY_EDITOR_PANELS, multi: true, useValue: p }
```
Phase 8 uses `provideOntologyEditorPanel` at the shell root or in its own route providers; the dependency points automation → ontology only (types), never the reverse.

### [ONT-TYPELIST]
`projects/ontology/src/lib/type-list/type-list.ts` — `SqOntologyTypeList`, selector `sq-ontology-type-list`. Uses `TypesClient.list()`
(includes abstract types), `routerLink` `./{name}`, `mat-nav-list`, a toolbar with "Raw Turtle" link and `sq-import-bar` (undo/redo/preview/import).

### [ONT-CLASS]
`projects/ontology/src/lib/type-editor/class-form.ts` — `SqClassForm` (`typeIri = input.required<string>()`): label field → `relabel`, abstract and
super-types read-only (D10); registers its control with `ControlRegistry`.

### [ONT-PROPFORM]
`projects/ontology/src/lib/type-editor/property-form.ts` (pure helpers + component `SqPropertyEditor`)
```ts
export function propertyToForm(p: PropertyView): FormGroup;             // label, datatype, cardinality min/max, display hints, derivation kind
export function diffPropertyForm(p: PropertyView, value: unknown): QuadDelta[]; // via relabel, setCardinality, setDerivation
```
Component inputs `property = input.required<PropertyView>()`; emits deltas through `OntologySession.apply`. Registers each control by property IRI.

### [ONT-PROPLIST]
`projects/ontology/src/lib/type-editor/property-list.ts` — `SqPropertyList` (`typeIri` input): `readProperties` filtered by domain, one
`SqPropertyEditor` per row, "Add attribute" / "Add relationship" dialogs calling `addAttributeProperty` / `addRelationshipProperty`.

### [ONT-SMEDITOR]
`projects/ontology/src/lib/state-machine-editor/state-machine-editor.ts` — `SqStateMachineEditor` (`typeIri` input). Renders `SqStateMachineGraph`
from `readStateMachine`, a transition list, and for each selected transition the panels from `panelsFor(inject(ONTOLOGY_EDITOR_PANELS), …)`
via `ngComponentOutlet` with `context` input. If no panels are registered: read-only trigger/guard text and a hint pointing to the raw tab.

### [ONT-TYPEEDITOR]
`projects/ontology/src/lib/type-editor/type-editor.ts` — `SqTypeEditor`, selector `sq-type-editor`, route `:type`. Providers
`[ControlRegistry]`. Tabs: "Class" (`SqClassForm`, `SqPropertyList`), "State machine" (`SqStateMachineEditor`), "Turtle" (`SqRawTurtleTab`).

### [ONT-RAW]
`projects/ontology/src/lib/raw/raw-turtle-tab.ts` — `SqRawTurtleTab`: `sq-turtle-editor` bound to `session.currentText()`; on blur/debounce
(300 ms) `session.applyText`; parse errors become a `error` diagnostic and block applying; `lastRejection` rendered via `issuesToDiagnostics`.

### [ONT-IMPORT]
`projects/ontology/src/lib/import-result/import-flow.ts`
```ts
@Injectable() export class ImportFlow {
  run(): Promise<'cancelled' | 'accepted' | 'rejected' | 'blocked'>;
  // prepareImport (blocked on parse error -> banner) -> ONTOLOGY_DIFF_SOURCE -> openDiffPreview -> OntologyClient.import ->
  // accepted: session.importSucceeded + TypesClient.list refresh; rejected: session.setRejection + registry.routeIssues + scroll first
}
```
`projects/ontology/src/lib/import-result/import-bar.ts` — `SqImportBar` (undo, redo, "Review & import"; `sq-problem-banner` for unmatched issues / `ApiError`).
`import-result.ts` — `SqImportResult` (dialog: typeCount, consistent, warnings from `OntologyImportResponse.issues`).

### [ONT-ROUTES]
`projects/ontology/src/lib/ontology.routes.ts`
```ts
export const ontologyRoutes: Routes = [{ path: '', providers: [OntologySession, ImportFlow], children: [
  { path: '', component: SqOntologyTypeList }, { path: ':type', component: SqTypeEditor } ] }];
```
Replaces the placeholder; `ontology.ts` and its spec are deleted (the old `Ontology` export is dropped).

### [ONT-PUBLIC]
`projects/ontology/src/public-api.ts` exports routes, `OntologySession`, `ImportFlow`, `ControlRegistry`, `ONTOLOGY_EDITOR_PANELS`,
`OntologyEditorPanel`, `OntologyEditorPanelContext`, `provideOntologyEditorPanel`, `panelsFor`, `OntologyDiff`, `ONTOLOGY_DIFF_SOURCE`,
`TEXT_DIFF_VIEW`, all `Sq*` components.

## Task order

Dependency tags: `@angular/material` dialog/tabs already used by Phase 6; add `@codemirror/merge` to the root `package.json` in task B-DEP (single dependency edit, runs `npm i`).

**Phase A — specs (one `*.spec.ts` per call; each states target path):**
A1 `projects/core-api/src/lib/clients/ontology-client.spec.ts` · A2 `projects/ontology/src/lib/session/text-delta.spec.ts` ·
A3 `…/issues/control-registry.spec.ts` · A4 `…/diff-preview/ontology-diff.spec.ts` · A5 `…/extension/editor-panels.spec.ts` ·
A6 `…/session/ontology-session.spec.ts` · A7 `…/diff-preview/text-diff.spec.ts` · A8 `…/diff-preview/diff-preview.spec.ts` ·
A9 `…/type-list/type-list.spec.ts` · A10 `…/type-editor/class-form.spec.ts` · A11 `…/type-editor/property-form.spec.ts` ·
A12 `…/type-editor/property-list.spec.ts` · A13 `…/state-machine-editor/state-machine-editor.spec.ts` ·
A14 `…/raw/raw-turtle-tab.spec.ts` · A15 `…/import-result/import-flow.spec.ts` · A16 `…/import-result/import-bar.spec.ts` ·
A17 `…/type-editor/type-editor.spec.ts` · A18 `…/ontology.routes.spec.ts` (includes: `:type` after list route; extension point works with a stub panel).

**Phase B — implementations (strictly sequential, one file per call; large files such as `ontology-session.ts` and
`property-form.ts` are written in append-only splits: B-part-1 types/skeleton, then part 2 appends methods). Each runs
`npx ng test <proj> --watch=false --include='**/<file>.spec.ts'`; proj is `core-api` for B1, otherwise `ontology`:**
- B-DEP `package.json` (+ `@codemirror/merge`, `npm i`)
- B1 `projects/core-api/src/lib/clients/ontology-client.ts` (+ re-export in `projects/core-api/src/public-api.ts`; confirm GET path)
- B2 `…/session/text-delta.ts` · B3 `…/issues/control-registry.ts` · B4 `…/diff-preview/ontology-diff.ts` · B5 `…/extension/editor-panels.ts`
- B6 `…/session/ontology-session.ts` · B7 `…/diff-preview/text-diff.ts` · B8 `…/diff-preview/diff-preview.ts` and `diff-preview-dialog.ts`
- B9 `…/type-list/type-list.ts` · B10 `…/type-editor/class-form.ts` · B11 `…/type-editor/property-form.ts`
- B12 `…/type-editor/property-list.ts` · B13 `…/state-machine-editor/state-machine-editor.ts` · B14 `…/raw/raw-turtle-tab.ts`
- B15 `…/import-result/import-flow.ts` · B16 `…/import-result/import-bar.ts` + `import-result.ts` · B17 `…/type-editor/type-editor.ts`
- B18 `projects/ontology/src/public-api.ts` (before B19's test) · B19 `…/ontology.routes.ts`; delete `ontology.ts`/`ontology.spec.ts`.

**Finish:** `npx ng lint ontology --fix` and `npx ng lint core-api --fix`, then manual fixes per offending file (one file per call); then Verification.

## Verification

Run from `sequeless-ui/`:
`npx ng test core-api --watch=false`, `npx ng test ontology --watch=false`, `npx ng test sequeless-shell --watch=false`,
`npx ng lint core-api`, `npx ng lint ontology`, `npx ng build sequeless-shell --configuration production`.
Phase 9 e2e (`ontology-edit-import.spec.ts`): edit a property label → preview diff → import → type list and Phase 6 browse headers
reflect the change; a forced 422 shows `server` errors on the matching control and unmatched issues in the banner.
No call to `/api/types/reload` and no dry-run call may exist (grep check).

## Open questions (not reversible without backend input)

- Exact `GET /api/ontology` path and content type (plan assumes `text/turtle`; B1 verifies against the controller).
- Whether the backend rejects a `POST` whose Turtle lacks the prefixes the original had (affects D3/`serializeOntology` reuse of captured prefixes).
