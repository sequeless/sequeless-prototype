# Phase 8 plan — automation feature (state-machine view, trigger editors, signals)

Branch: `phase-8-automation` (stacked on `phase-7-ontology`). Spec: `docs/specs/phase-8-automation.md`.
**Numbering clash:** this is *frontend* Phase 8. Backend Phase 8 is unrelated and not implemented here.
Execution: a queued batch of stateless `claude -p` calls (Phase A writes one spec file per call, Phase B writes one
implementation file per call against it), each followed by a scoped commit. All paths below are relative to
`sequeless-ui/`. Phase 6 tags (`[CORE-*]`, `[OBJ-*]`, `[SHELL-CONFIG]`) are defined in `docs/plans/phase-6-objects.md`
and Phase 7 tags (`[ONT-*]`) in `docs/plans/phase-7-ontology.md`; they are reused, never redefined.

## Findings that shape the plan

- `projects/automation` is a placeholder: `Automation` component (`automation.ts`, `automation.spec.ts`) +
  `automationRoutes` in `automation.routes.ts`. The shell already lazy-loads it; `app.routes.ts` must not change.
- **core-api** has `StateMachineResponse.transitions: TransitionSummaryResponse[]` with `name, from, to, hasGuard,
  trigger: TriggerKind, after, signalName, watch`; `TypeDetailResponse.stateMachine | null`; `GraphLayout`/`GRAPH_LAYOUT`
  (`ui-contracts.ts`); `ApiError`. After Phase 6: `TypesClient`, `ObjectsClient.signal`, `provideSequelessHttp()`.
- **ui-kit** has `SqStateMachineGraph` (`input.required<StateMachineResponse>()`, **no outputs**: edges and nodes are not
  clickable), `SqDurationField` (a `ControlValueAccessor` emitting ISO-8601), `edgeStyle`, `iso-duration` utils,
  `SqProblemBanner`. The spec's "click an edge/node" therefore needs a seam, not a ui-kit edit (D2).
- **rdf** has `readStateMachine(store, typeIri): StateMachineView | null` (`TransitionView {node, name, fromIri, toIri,
  trigger: TriggerView, guard, guardMessage}`), `setTransitionTrigger(store, node, TriggerView)` (the dedicated
  edit that also drops the previous kind's sibling term) and `setGuard(store, node, guard, message)`.
- **ontology (Phase 7)** exposes the extension point `[ONT-EXT]` (`provideOntologyEditorPanel`,
  `OntologyEditorPanelContext {typeIri, session, registry, transitionIri?}`, slots `transition-trigger` /
  `transition-guard`). Automation depends on ontology (types + that provider function); ontology never imports automation.
- **objects (Phase 6)** exposes `ObjectDetailStore` and `OBJECT_SIGNALS_PANEL` ([OBJ-STORE], [OBJ-SIGNALS], D5).
- **Doc/spec conflict to verify in task B1:** `docs/specs/phase-8-automation.md` says `GET /api/types/{iri}` already
  returns every trigger kind (lifted in backend Phase 1), but `docs/architecture/automation.md` §12 still says the
  descriptor filters to `USER_ACTION`. The diagram is built from the type endpoint regardless; if the filter is still on,
  the UI shows an "automated transitions hidden by server" note (D8) instead of switching to the per-object endpoint.
- **Never** use `ObjectsClient.transitions` (`GET /api/objects/{type}/{id}/transitions`) in this project: it is the
  user-callable list only. Enforced by a grep check in Verification.
- No endpoint exposes scheduled timers, `sq_outbox` or per-object automation history; none is built (spec "Known gaps").
- `git show main:GLOSSARY.md` has **Store Command** and **Domain event** (a transition fired) but no entries for
  Trigger, Signal or Timer; UI copy says "Transition", "Trigger", "Signal". `docs/adr` does **not exist on main**: no ADR to reuse.

## Decisions (each reversible or configurable)

| # | Decision | Why it stays reversible |
|---|---|---|
| D1 | The diagram renderer is a component **port**: `STATE_MACHINE_DIAGRAM` token holding a `Type<unknown>` honouring `[AUT-DIAGRAM]` (default `SqKitStateMachineDiagram`, wrapping ui-kit's `SqStateMachineGraph`). **Switch:** `provideStateMachineDiagram(Other)` or `{provide: STATE_MACHINE_DIAGRAM, useValue: Other}`. | The view only knows the contract; the renderer (and `GRAPH_LAYOUT`) can be replaced independently. |
| D2 | Edge/node selection is delivered by the port's outputs. The default adapter renders the ui-kit graph plus an accessible transition list and state list next to it that emit the selections (graph itself stays non-interactive). **Switch:** a richer adapter (clickable SVG) swaps in via D1; ui-kit gains outputs later without touching the view. | No ui-kit edit now; interaction lives in the adapter. |
| D3 | The diagram is fed only by `TypesClient.describe(typeIri).stateMachine` (type endpoint). **Switch:** none by design; the data source is the injectable `TypesClient`. | Spec-mandated asymmetry; grep guard prevents regressions. |
| D4 | Trigger editors are standalone components contributed through Phase 7's `provideOntologyEditorPanel` by one function `provideAutomationEditors()` (slots `transition-trigger`, `transition-guard`). **Switch:** remove the provider call from the shell config: ontology falls back to read-only text. | Uses Phase 7's seam unchanged. |
| D5 | Editors never write quads themselves: trigger kind/value changes call `setTransitionTrigger`; guard changes call `setGuard`; both go through `session.apply(delta)` (journal, undo/redo). A pure `triggerFromForm` maps form → `TriggerView`. **Switch:** change one mapper file. | Stale-sibling cleanup stays in rdf (spec rule). |
| D6 | The signal catalogue scans every non-abstract type with a bounded concurrency (`SIGNAL_CATALOGUE_CONCURRENCY`, default 4), caches per session, and exposes `refresh()`. **Switch:** token value, or `{provide: SignalCatalogue, useClass: BatchedCatalogue}` once a batched endpoint exists. | Hides the N+1 behind one class. |
| D7 | The firing panel replaces Phase 6's panel via `OBJECT_SIGNALS_PANEL` (`provideAutomationSignalsPanel()`); the Phase 6 `knownSignals` panel remains the default. Post-fire refetch reuses `OBJECTS_SIGNAL_REFETCH_DELAY_MS`. **Switch:** drop the provider. | Phase 6 D5 designed this exact plug point. |
| D8 | Honest-gaps copy is a single `AutomationGapsNote` component (timer/outbox/history unavailable; plus a "server may be hiding automated transitions" hint when a machine has none, see [AUT-GAPS]). **Switch:** component is optional in templates. | Removing it is one tag; no fake data is ever produced. |
| D9 | Signal-name uniqueness is checked within one type's transitions only, as a warning (never blocks). Pure function. **Switch:** replace `signalCollisions`. | Isolated. |
| D10 | Node click navigates to Phase 6 browse with a `state` equality filter built by pure `stateBrowseLink` using core-api's `toHttpParams`. **Switch:** change that function if the backend filters state differently. | One function; see open question 1. |
| D11 | Guard editor is a plain textarea labelled "validated server-side only"; no validation call exists. **Switch:** a future `ExpressionValidator` token could be injected; not added now. | Avoids inventing backend surface. |
| D12 | Routes: `''` type picker (lists types with a state machine), `:type` state-machine view. The signal catalogue has no route of its own; it feeds the panel. **Switch:** routes in one file. | Cheap to add a catalogue page later. |

## Shared contract

### [AUT-DIAGRAM]
`projects/automation/src/lib/state-machine-view/state-machine-diagram.ts` (port; pure types + token)
```ts
export interface StateMachineDiagramContract {
  // the component bound to the token must declare these members
  stateMachine: InputSignal<StateMachineResponse>;
  selectedTransition: InputSignal<string | null>;
  transitionSelect: OutputEmitterRef<string>;   // transition name
  stateSelect: OutputEmitterRef<string>;        // state IRI
}
export const STATE_MACHINE_DIAGRAM: InjectionToken<Type<unknown>>; // providedIn root, factory () => SqKitStateMachineDiagram
export function provideStateMachineDiagram(component: Type<unknown>): Provider;
```

### [AUT-DIAGRAM-KIT]
`projects/automation/src/lib/state-machine-view/kit-state-machine-diagram.ts` — `SqKitStateMachineDiagram`, selector
`sq-kit-state-machine-diagram`. Renders `<sq-state-machine-graph [stateMachine]>` plus a `mat-nav-list` of transitions
(label `describeTransition`, a trigger-kind icon/badge using `edgeStyle` kind names) and of states; clicks emit the outputs.

### [AUT-FORMAT]
`projects/automation/src/lib/state-machine-view/transition-format.ts` (pure)
```ts
export function describeTransition(t: TransitionSummaryResponse, stateLabel: (iri: string) => string): string;
// ON_CHANGE: "fires when <watch> changes" (self when empty) · TIMER: "fires 72h after entering <from label>" (via iso-duration) ·
// EXTERNAL_SIGNAL: "fires on signal <name>" · USER_ACTION: "fired by a user"
export function hasAutomatedTransitions(sm: StateMachineResponse): boolean; // any trigger !== 'USER_ACTION'
export function stateBrowseLink(typeName: string, stateName: string): { commands: string[]; queryParams: Params }; // D10
```

### [AUT-VIEW]
`projects/automation/src/lib/state-machine-view/state-machine-view.ts` — `SqStateMachineView`, selector
`sq-state-machine-view`. Route param `type`. `TypesClient.describe(type)`; `stateMachine === null` → "no state machine" message.
Hosts `*ngComponentOutlet="inject(STATE_MACHINE_DIAGRAM)"` with inputs/outputs; selecting a transition opens a side panel
containing the read-only trigger text plus a "Edit in ontology" `routerLink` to `/ontology/{type}` (editing itself lives in
Phase 7's editor hosting [AUT-EDITORS]); selecting a state navigates via `stateBrowseLink`. Shows `sq-problem-banner` on `ApiError`
and `AutomationGapsNote`. Never imports `ObjectsClient.transitions`.

### [AUT-GAPS]
`projects/automation/src/lib/state-machine-view/gaps-note.ts` — `AutomationGapsNote` (`sm = input<StateMachineResponse | null>(null)`).
Static copy: timers show their duration only (no countdown), no outbox/history view exists. If `sm` is given and
`!hasAutomatedTransitions(sm)` add: "This type shows no automated transitions; the server may be hiding them."

### [AUT-TRIGGERMAP]
`projects/automation/src/lib/trigger-editors/trigger-form.ts` (pure)
```ts
export interface TriggerFormValue { kind: TriggerKind; watch: string[]; after: string | null; signalName: string }
export function triggerToForm(t: TriggerView): TriggerFormValue;
export function triggerFromForm(v: TriggerFormValue): TriggerView | { error: string }; // TIMER needs a positive duration, signal needs non-blank name
export function relationshipWatchOptions(type: TypeDetailResponse): { iri: string; label: string }[]; // properties kind==='relationship'
export function signalCollisions(sm: StateMachineView, transition: TransitionView, name: string): string[]; // other transitions on same type with the same signal name (D9)
export function timerEcho(after: string, fromLabel: string): string; // "fires 72h after entering OnHold"
```

### [AUT-EDITORS]
`projects/automation/src/lib/trigger-editors/` — components honouring `[ONT-EXT]` (`context = input.required<OntologyEditorPanelContext>()`),
each finds its `TransitionView` with `readStateMachine(context.session.store()!, context.typeIri)` and applies deltas with `session.apply`:
- `trigger-editor.ts` `SqTriggerEditor` (slot `transition-trigger`): kind `mat-select`; kind-specific child below; saves `setTransitionTrigger`.
- `on-change-field.ts` `SqOnChangeField`: `mat-select multiple` over `relationshipWatchOptions` (type from `TypesClient.describe`).
- `timer-field.ts` `SqTimerField`: `sq-duration-field` + `timerEcho`.
- `signal-field.ts` `SqSignalField`: text input; warning via `signalCollisions`; registers its control with `context.registry`.
- `guard-editor.ts` `SqGuardEditor` (slot `transition-guard`): textarea + message input, hint "validated server-side only"; saves `setGuard`.
- `provide-automation-editors.ts`
```ts
export function provideAutomationEditors(): Provider[]; // provideOntologyEditorPanel × 2 ({id:'automation.trigger', slot:'transition-trigger'}, {id:'automation.guard', slot:'transition-guard'})
```

### [AUT-CATALOGUE]
`projects/automation/src/lib/signal-catalogue/signal-catalogue.ts`
```ts
export interface SignalEntry { typeIri: string; typeName: string; signalName: string; from: string; to: string }
export const SIGNAL_CATALOGUE_CONCURRENCY: InjectionToken<number>; // providedIn root, factory () => 4
export function collectSignals(type: TypeDetailResponse): SignalEntry[]; // pure: trigger==='EXTERNAL_SIGNAL' && signalName != null
export function signalsForState(entries: readonly SignalEntry[], typeIri: string, stateIri: string | null):
  { enabled: SignalEntry[]; disabled: (SignalEntry & { requires: string })[] }; // disabled = from !== stateIri
@Injectable({ providedIn: 'root' }) export class SignalCatalogue {
  readonly entries: Signal<readonly SignalEntry[]>; readonly loading: Signal<boolean>; readonly error: Signal<ApiError | null>;
  ensureLoaded(): Promise<void>;  // TypesClient.list() (non-abstract) → mergeMap(describe, concurrency) → collectSignals; cached
  refresh(): Promise<void>;
}
```

### [AUT-PAYLOAD]
`projects/automation/src/lib/signal-catalogue/payload.ts` (pure)
```ts
export function parsePayload(text: string): { ok: true; value: Record<string, unknown> | undefined } | { ok: false; error: string };
// blank → undefined; must be a JSON object (not array/primitive)
```

### [AUT-SIGNALS-PANEL]
`projects/automation/src/lib/signal-catalogue/signals-panel.ts` — `SqAutomationSignalsPanel`, selector `sq-automation-signals-panel`.
No required inputs; injects `ObjectDetailStore`, `SignalCatalogue`, `ObjectsClient`. Lists `signalsForState` (enabled = button, disabled
greyed with "requires state X"), JSON payload textarea (`parsePayload`), fire → `ObjectsClient.signal` → "dispatched — takes effect
asynchronously" → `store.refetch()` after `OBJECTS_SIGNAL_REFETCH_DELAY_MS`; "Refresh" refetches on demand and calls
`catalogue.refresh()`. 400 `unknown-signal` (`ApiError.signalName`) → field error on the signal row + `catalogue.refresh()`; panel never throws.
`provideAutomationSignalsPanel(): Provider` = `{provide: OBJECT_SIGNALS_PANEL, useValue: SqAutomationSignalsPanel}`.

### [AUT-TYPELIST]
`projects/automation/src/lib/state-machine-view/type-list.ts` — `SqAutomationTypeList`: `TypesClient.list()` non-abstract, `routerLink ./{name}`.

### [AUT-ROUTES]
`projects/automation/src/lib/automation.routes.ts`
```ts
export const automationRoutes: Routes = [{ path: '', children: [
  { path: '', component: SqAutomationTypeList }, { path: ':type', component: SqStateMachineView } ] }];
```
Replaces the placeholder; `automation.ts` and `automation.spec.ts` are deleted.

### [AUT-PUBLIC]
`projects/automation/src/public-api.ts` exports `automationRoutes`, `STATE_MACHINE_DIAGRAM`, `provideStateMachineDiagram`,
`StateMachineDiagramContract`, `provideAutomationEditors`, `provideAutomationSignalsPanel`, `SignalCatalogue`,
`SIGNAL_CATALOGUE_CONCURRENCY`, `SignalEntry`, `collectSignals`, `signalsForState`, `parsePayload`, all `Sq*` components.

### [SHELL-AUTOMATION]
`projects/sequeless-shell/src/app/app.config.ts` keeps its providers and adds `provideAutomationEditors()` and
`provideAutomationSignalsPanel()` (one line each). This is the only shell change; the shell imports `@sequeless/automation`'s public API.

## Task order

Dependencies: `@sequeless/ontology`, `@sequeless/objects`, `@sequeless/rdf`, `@sequeless/ui-kit`, `@sequeless/core-api` path aliases already exist;
no new npm package.

**Phase A — specs (one `*.spec.ts` per call; each states its target path):**
A1 `projects/automation/src/lib/state-machine-view/transition-format.spec.ts` · A2 `…/trigger-editors/trigger-form.spec.ts` ·
A3 `…/signal-catalogue/payload.spec.ts` · A4 `…/signal-catalogue/signal-catalogue.spec.ts` (pure fns + service with fake `TypesClient`, concurrency bound) ·
A5 `…/state-machine-view/state-machine-diagram.spec.ts` (token default, override) · A6 `…/state-machine-view/kit-state-machine-diagram.spec.ts` ·
A7 `…/state-machine-view/gaps-note.spec.ts` · A8 `…/state-machine-view/state-machine-view.spec.ts` (asserts `ObjectsClient.transitions` is never called;
swapping the token renders a stub diagram) · A9 `…/state-machine-view/type-list.spec.ts` · A10 `…/trigger-editors/on-change-field.spec.ts` ·
A11 `…/trigger-editors/timer-field.spec.ts` · A12 `…/trigger-editors/signal-field.spec.ts` · A13 `…/trigger-editors/guard-editor.spec.ts` ·
A14 `…/trigger-editors/trigger-editor.spec.ts` (kind change applies exactly one `setTransitionTrigger` delta; undo restores) ·
A15 `…/trigger-editors/provide-automation-editors.spec.ts` (with Phase 7 `panelsFor`) · A16 `…/signal-catalogue/signals-panel.spec.ts`
(enabled/disabled, 400 unknown-signal shows field error, no state change on 202, refetch only after delay) · A17 `…/automation.routes.spec.ts` ·
A18 `projects/sequeless-shell/src/app/app.config.spec.ts` (append case for SHELL-AUTOMATION; one spec file edit).

**Phase B — implementations (strictly sequential, one file per call; large files in append-only splits: part 1 types/skeleton, part 2 appends
methods/template). Each runs `npx ng test automation --watch=false --include='**/<file>.spec.ts'` (shell task: `npx ng test sequeless-shell …`):**
- B1 first, before any code: read `GET /api/types/{iri}` controller + `automation.md` §12; record the finding as a comment-free note in the commit
  message (no file); resolves the doc conflict above and open question 1.
- B2 `…/state-machine-view/transition-format.ts` · B3 `…/trigger-editors/trigger-form.ts` · B4 `…/signal-catalogue/payload.ts` ·
  B5 `…/signal-catalogue/signal-catalogue.ts` (part 1 pure fns; part 2 service)
- B6 `…/state-machine-view/state-machine-diagram.ts` · B7 `…/state-machine-view/kit-state-machine-diagram.ts` · B8 `…/state-machine-view/gaps-note.ts`
- B9 `…/state-machine-view/state-machine-view.ts` · B10 `…/state-machine-view/type-list.ts`
- B11 `…/trigger-editors/on-change-field.ts` · B12 `…/trigger-editors/timer-field.ts` · B13 `…/trigger-editors/signal-field.ts` ·
  B14 `…/trigger-editors/guard-editor.ts` · B15 `…/trigger-editors/trigger-editor.ts` · B16 `…/trigger-editors/provide-automation-editors.ts`
- B17 `…/signal-catalogue/signals-panel.ts` (part 1 component, part 2 appends fire/error handling; `provideAutomationSignalsPanel` in same file)
- B18 `projects/automation/src/public-api.ts` (before B19's test) · B19 `…/automation.routes.ts`; delete `automation.ts`/`automation.spec.ts`
- B20 `projects/sequeless-shell/src/app/app.config.ts` (SHELL-AUTOMATION)

**Finish:** `npx ng lint automation --fix`, `npx ng lint sequeless-shell --fix`, then manual fixes per offending file (one file per call); then Verification.

## Verification

Run from `sequeless-ui/`:
`npx ng test automation --watch=false`, `npx ng test sequeless-shell --watch=false`, `npx ng lint automation`, `npx ng lint sequeless-shell`,
`npx ng build sequeless-shell --configuration production`; regression: `npx ng test ontology --watch=false`, `npx ng test objects --watch=false`.
Grep checks (must return nothing): `.transitions(` / `fireTransition` under `projects/automation/src`; `from '@sequeless/automation'` under `projects/ontology/src`;
`/api/types/reload`. Phase 9 e2e (`automation-signals.spec.ts`): view a type with all four trigger kinds and see distinct edge styles; fire a known signal and
confirm the state changes only after a refetch, never on the 202.

## Open questions (not reversible without backend input)

1. Whether browse accepts `state` as a filter property for the node-click shortcut (D10 degrades to plain navigation to browse if rejected).
2. Whether `GET /api/types/{iri}` actually includes non-`USER_ACTION` transitions (spec says yes, architecture doc says no); decided in B1, UI stays correct either way via D8.
