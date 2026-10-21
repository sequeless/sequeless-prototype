# Phase 9 plan — shell composition, e2e, CI, docs

Branch: `phase-9-shell-composition` (stacked on `phase-8-automation`). Spec: `docs/specs/phase-9-shell-composition-and-polish.md`.
**Numbering:** frontend Phase 9; the backend has no Phase 9 counterpart.
Execution: a queued batch of stateless `claude -p` calls (Phase A writes one test/spec file per call, Phase B writes one
implementation or config file per call, strictly sequential), each followed by a scoped commit. Frontend paths are relative to
`sequeless-ui/` unless they start with `sequeless-app/`, `.github/`, `docs/` or are the root `package.json`/`README.md`.
Phase 6 tags (`[CORE-*]`, `[OBJ-*]`, `[SHELL-CONFIG]`) are defined in `docs/plans/phase-6-objects.md`, Phase 7 tags (`[ONT-*]`) in
`docs/plans/phase-7-ontology.md`, Phase 8 tags (`[AUT-*]`, `[SHELL-AUTOMATION]`) in `docs/plans/phase-8-automation.md`; they are reused, never redefined.

## Findings that shape the plan

**Done (Phase 2, confirmed in code):** `projects/sequeless-shell/src/app/` has `app.ts`, `app.html`, `app.scss`, `app.config.ts`
(`provideBrowserGlobalErrorListeners`, `provideRouter(routes)`, `provideAnimationsAsync`, `provideSequeless()`) and `app.routes.ts`
(`''` → `objects`; three `loadChildren` entries pointing at `objectsRoutes`, `ontologyRoutes`, `automationRoutes` **by name**). The root
`package.json` already has `build:ui`, `build`, `verify`, `e2e`. Phase 1 backend proofs `SpaFallbackWithAssetsTest` and
`SpaFallbackWithoutAssetsTest` exist in `sequeless-app/src/test/java/org/sequeless/app/`.

**Left (nothing below exists):**
- Shell: `app.config.ts` has neither `provideSequelessHttp()`/`provideUiKit()` (Phase 6 `[SHELL-CONFIG]`) nor
  `provideAutomationEditors()`/`provideAutomationSignalsPanel()` (`[SHELL-AUTOMATION]`). Phase 7 needs **no** shell change
  (`[ONT-ROUTES]` provides `OntologySession`/`ImportFlow` at route level, `ObjectsRelationshipLookup` is route-level in `[OBJ-ROUTES]`).
  No `app.config.spec.ts` or routes spec exists yet (Phase 6 A20 / Phase 8 A18 create/append `app.config.spec.ts`; this phase must not rewrite it).
- e2e: no `sequeless-ui/e2e/`, no `playwright.config.ts`, no `@playwright/test` dependency, no `e2e` script in `sequeless-ui/package.json`
  (the root `e2e` script already points at it and would fail). Playwright browsers are **not installed** in the build environment.
- `FatJarServesUiAndApiIT` does not exist; the only `*IT` classes in `sequeless-app` are `AcceptanceCriteriaIT` and
  `OntologyPersistenceAcrossRestartIT`. `sequeless-app/pom.xml` sets `<classifier>exec</classifier>`; Failsafe binding is inherited, not local (B-task confirms).
- CI: `.github/workflows/ci.yml` has one job (`build`: checkout@v7, setup-java@v6 temurin 25, `mvn -B -ntp verify`). No UI job.
- Bug: root `verify` runs `npm --prefix sequeless-ui test -- --run`; `--run` is invalid for `ng test` (Angular 22). Use `--watch=false`.
- Docs: `docs/architecture/02-decision-records.md` ends at DR-16; `README.md` curls use `localhost:8080/whoami` and `/actuator/health`
  (zero `/api/` references); `03-phased-plan.md` has no UI track section.
- Terms/ADRs: `GLOSSARY.md` exists on `main` (Browse, Facet, Facet bucket, Object view, Store, Command…) — e2e specs reuse **Browse** and **Facet**;
  no new term is needed. `docs/adr/` is **absent** on `main` (CLAUDE.md names it, but decision records live in
  `docs/architecture/02-decision-records.md`); DR-17..20 go there and no `docs/adr/` is created.

## Decisions (each reversible or configurable)

| # | Decision | Why it stays reversible |
|---|---|---|
| D1 | `app.routes.ts` is **not edited**. A new spec asserts each `loadChildren` resolves to a non-empty `Routes`. **Switch:** if it ever goes red, fix the feature library's export, never the shell. | The spec is the guard; the routes stay name-based. |
| D2 | Shell composition stays a flat provider list in `app.config.ts` (four added lines from Phases 6 and 8). **Switch:** delete a line to unplug that capability; each has a default (`OBJECT_SIGNALS_PANEL` → `SqSignalsPanel`, `ONTOLOGY_EDITOR_PANELS` → empty). | Every line targets a DI token with a default, so removal degrades, never breaks. |
| D3 | A shell composition spec (`app.composition.spec.ts`) resolves tokens from the real `appConfig` providers. | New file; Phase 6/8 specs untouched. |
| D4 | e2e runner is Playwright, hidden behind one seam: every journey imports `test`/`expect`/fixtures from `e2e/support/test.ts`. **Switch:** re-export another runner's equivalents (e.g. Cypress via an adapter) from that file and change the `e2e` npm script. | Journeys never import `@playwright/test` directly (grep-checked). |
| D5 | Target URL is `E2E_BASE_URL` (default `http://localhost:4200`, the `ng serve` + proxy setup; set `http://localhost:8080` to run against the fat jar). Seed type names come from `E2E_OBJECT_TYPE`, `E2E_SIGNAL_TYPE`, `E2E_SIGNAL_NAME`. | Pure env config; no code change to retarget or reseed. |
| D6 | `playwright.config.ts` calls `assertBrowsersInstalled()` and throws a clear message; it never runs or suggests running `playwright install` from code. **Switch:** `PLAYWRIGHT_BROWSERS_PATH` selects where browsers live; the check is one function in `e2e/support/browser-check.ts`. | Check is isolated; replaceable with an external-browser launcher via `E2E_BROWSER_CHANNEL`. |
| D7 | `npm run e2e` is never in `verify` or CI. Enforced by a grep check in Verification. | Opting in later is one added script/job. |
| D8 | CI logic lives in root npm scripts (`ci:ui`, `ci:combined`); YAML only calls them. **Switch:** moving to GitLab/Circle means rewriting thin wrappers; the commands stay. | Provider lock-in is limited to ~10 lines of YAML per job. |
| D9 | Jobs: `build` (backend, **untouched**), new `ui`, optional `combined` (UI build → `mvn verify`). `combined` runs only when repo variable `CI_COMBINED_JOB` is `'true'`. **Switch:** set/unset the variable. | Backend-only and UI-then-backend proofs can never merge into one job. |
| D10 | `FatJarServesUiAndApiIT` finds the jar via system property `sequeless.it.jar` (default: `target/*-exec.jar` glob) and runs live HTTP unless `sequeless.it.live=false`. **Switch:** `-Dsequeless.it.live=false` = the spec's documented fallback (pure `JarFile` assertion; live proof left to `SpaFallbackWithAssetsTest`). | Flakiness fallback is a flag, not a rewrite. |
| D11 | Historical `docs/architecture/prompts/phase-0.md … phase-6.md` are never edited. | Irreversible by design: they are the record. |

## Shared contract

### [SHELL-FINAL]
`projects/sequeless-shell/src/app/app.config.ts` after Phases 6–9 (composition of `[SHELL-CONFIG]` + `[SHELL-AUTOMATION]`; this phase only
verifies/edits it if the composition spec is red):
```ts
export const appConfig: ApplicationConfig = { providers: [
  provideBrowserGlobalErrorListeners(), provideRouter(routes), provideAnimationsAsync(), provideSequeless(),
  provideSequelessHttp(), provideUiKit(),                     // Phase 6
  provideAutomationEditors(), provideAutomationSignalsPanel(), // Phase 8
] };
```
Route-level (not shell-level) providers, asserted by the routes spec: objects → `provideObjectsRelationshipLookup()`;
ontology → `OntologySession`, `ImportFlow`; automation → none.

### [SHELL-ROUTES-SPEC]
`projects/sequeless-shell/src/app/app.routes.spec.ts` asserts: the redirect `''` → `objects`; for `objects|ontology|automation`,
`await route.loadChildren!()` is a non-empty array whose first entry has `path: ''`; the file `app.routes.ts` text contains no `component:`.

### [SHELL-COMPOSITION-SPEC]
`projects/sequeless-shell/src/app/app.composition.spec.ts` builds `TestBed` with `appConfig.providers` and asserts:
`inject(HttpClient)` resolves; `inject(FIELD_RENDERERS)` is non-empty; `inject(OBJECT_SIGNALS_PANEL)` is `SqAutomationSignalsPanel`;
`panelsFor('transition-trigger')` includes id `automation.trigger` and `panelsFor('transition-guard')` includes `automation.guard`
(slot names per `[AUT-EDITORS]`; use the exact slot strings from that plan).

### [E2E-SEAM]
`e2e/support/test.ts`
```ts
export { test, expect } from '@playwright/test';           // only file allowed to import the runner
export interface E2eEnv { baseUrl: string; objectType: string; signalType: string; signalName: string }
export function e2eEnv(env?: NodeJS.ProcessEnv): E2eEnv;   // reads E2E_* with defaults from D5
export async function openSecondTab(page: Page): Promise<Page>; // same context, new page (two-tab journeys)
```

### [E2E-BROWSERS]
`e2e/support/browser-check.ts`
```ts
export function assertBrowsersInstalled(env?: NodeJS.ProcessEnv, exists?: (p: string) => boolean): void;
// resolves PLAYWRIGHT_BROWSERS_PATH (else the Playwright default cache dir); throws Error('Playwright browsers not found at <dir>. Install them out of band; this config never downloads.') when no chromium-* dir exists
```

### [E2E-CONFIG]
`playwright.config.ts` (in `sequeless-ui/`): `testDir: 'e2e'`, `use.baseURL = e2eEnv().baseUrl`, one `chromium` project, `retries: 0`,
calls `assertBrowsersInstalled()` at load unless `E2E_SKIP_BROWSER_CHECK=1` (used only for `--list`). `tsconfig.e2e.json` (new) covers `e2e/**` and `playwright.config.ts`;
`ng test`/`ng lint` projects do not include it.

### [E2E-JOURNEYS]
All under `e2e/`, all `import { test, expect, e2eEnv } from './support/test'`:
- `deep-link.spec.ts` — `goto('/ontology')`, reload, URL and heading persist; `goto('/objects/<type>?page=1')`, reload keeps the query (Phase 6 `[OBJ-QSTATE]`).
- `objects-stale-conflict.spec.ts` — Phase 6: create an object via `/objects/<type>/new`; open detail in tab A and tab B; B edits and saves; A edits the same
  field and saves → conflict dialog (`[OBJ-DETAIL]`, `openConflictDialog`) appears; choose **reapply** → saved once; assert no third PUT (count via `page.route`).
- `ontology-edit-import.spec.ts` — Phase 7: edit a property label → preview diff → import → `/ontology` list and `/objects/<type>` column header show the new label;
  a forced 422 (route-fulfilled) shows `server` errors on the control and unmatched issues in the banner.
- `automation-signals.spec.ts` — Phase 8: open `/automation/<signalType>`; fire `E2E_SIGNAL_NAME` → 202 dispatched message shown, state **unchanged** immediately,
  changed only after the delayed refetch (`OBJECTS_SIGNAL_REFETCH_DELAY_MS`).

### [IT-FATJAR]
`sequeless-app/src/test/java/org/sequeless/app/FatJarServesUiAndApiIT.java` — Failsafe `*IT`.
```java
class FatJarServesUiAndApiIT {
  @Test void jarServesApiAndConditionallyUi(); // free port, ProcessBuilder java -jar, poll /actuator/health until UP (60s cap), GET /api/whoami == 200,
                                               // GET / == 200 text/html iff JarFile has BOOT-INF/classes/static/index.html, else 404
  @Test void jarContentMatchesUiPresence();    // sequeless.it.live=false path: JarFile assertion only
}
```
Skips live test when `sequeless.it.live=false`; always destroys the process in `@AfterEach`.

### [CI-SCRIPTS]
Root `package.json` scripts (replace `verify`'s `-- --run`):
```json
"verify": "npm run build:ui && npm --prefix sequeless-ui test -- --watch=false && mvn -B -ntp verify",
"ci:ui": "npm --prefix sequeless-ui ci && npm --prefix sequeless-ui run lint && npm --prefix sequeless-ui test -- --watch=false && npm --prefix sequeless-ui run build",
"ci:combined": "npm run ci:ui && mvn -B -ntp verify"
```
`sequeless-ui/package.json` gains `"e2e": "playwright test"` and devDependency `@playwright/test`.

### [CI-WORKFLOW]
`.github/workflows/ci.yml` — job `build` byte-for-byte unchanged; appended jobs `ui` (checkout@v7, `actions/setup-node@v6`, node `24`,
`cache: npm`, `cache-dependency-path: sequeless-ui/package-lock.json`, `run: npm run ci:ui`) and `combined`
(`if: ${{ vars.CI_COMBINED_JOB == 'true' }}`; setup-node + setup-java@v6 temurin 25 `cache: maven`, `run: npm run ci:combined`).
No step runs `e2e` or `playwright`.

### [DOCS-SET]
Content per file is fixed by the spec's "Docs to update" list: DR-17 (`/api` prefix + co-served SPA, `addPathPrefix`), DR-18 (client-side RDF
round-trip authoring, blank-node reformatting, forward-compatible with backend Phase 7), DR-19 (frontend pluggability mirrors the backend adapter
swap: tokens + `provide*()`), DR-20 (hand-written wire models, two reasons from `docs/specs/phase-3-core-api.md`). DR-18/19 also reference the
extension points `[ONT-EXT]`, `OBJECT_SIGNALS_PANEL`, `STATE_MACHINE_DIAGRAM`.

## Task order

**Phase A — tests/specs (written directly, one file per call, each states its target path; none depend on later tasks):**
- A1 `sequeless-app/src/test/java/org/sequeless/app/FatJarServesUiAndApiIT.java` ([IT-FATJAR]). Run: `mvn -B -ntp -pl sequeless-app -am verify -Dit.test=FatJarServesUiAndApiIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false` (from repo root; clean `static/` first, see Part B).
- A2 `projects/sequeless-shell/src/app/app.routes.spec.ts` ([SHELL-ROUTES-SPEC])
- A3 `projects/sequeless-shell/src/app/app.composition.spec.ts` ([SHELL-COMPOSITION-SPEC])
- A4 `e2e/deep-link.spec.ts` · A5 `e2e/objects-stale-conflict.spec.ts` · A6 `e2e/ontology-edit-import.spec.ts` · A7 `e2e/automation-signals.spec.ts` ([E2E-JOURNEYS]; authored, not run in CI)

**Phase B — implementations/config (strictly sequential, one file per call; append-only splits only if a file exceeds ~150 lines).
Test command for shell files: `npx ng test sequeless-shell --watch=false --include='**/<file>.spec.ts'`:**
- B1 `projects/sequeless-shell/src/app/app.config.ts` — **verify first**: run A2/A3 specs; edit only to add whichever `[SHELL-FINAL]` lines are missing (expected: no-op once Phases 6 and 8 landed). Paired specs: `app.routes.spec.ts`, `app.composition.spec.ts`.
- B2 `package.json` (`sequeless-ui/`) — add `@playwright/test` devDependency (`npm i -D @playwright/test --ignore-scripts`; **never** `playwright install`) and the `e2e` script.
- B3 `tsconfig.e2e.json` (`sequeless-ui/`) · B4 `e2e/support/browser-check.ts` · B5 `e2e/support/test.ts` · B6 `playwright.config.ts`.
  Paired check for B4–B6 (no browsers needed): `E2E_SKIP_BROWSER_CHECK=1 npx playwright test --list`, which must list all four journeys (A4–A7) with no type errors; plus `npx tsc -p tsconfig.e2e.json --noEmit`.
- B7 `package.json` (repo root) — `verify` fix, `ci:ui`, `ci:combined` ([CI-SCRIPTS]). Check: `npm run ci:ui`.
- B8 `.github/workflows/ci.yml` ([CI-WORKFLOW]). Check: `git diff` shows only added lines below `build`; `actionlint .github/workflows/ci.yml` when installed (record "not installed" otherwise).

**Phase C — docs (written directly, one file per call):**
- C1 `docs/architecture/02-decision-records.md` (append DR-17..20) · C2 `README.md` (Build-with-the-UI section; every `curl localhost:8080/…` → `/api/…` except `/actuator/health`; note that `mvn verify` alone still yields an API-only jar; e2e how-to: browsers must be pre-installed, env vars from D5)
- C3 `docs/architecture/03-phased-plan.md` ("UI track (parallel to Phases 7–8)", unnumbered) · C4 `docs/architecture/01-overview.md` · C5 `docs/architecture/query-port.md` · C6 `docs/architecture/derived-properties.md` · C7 `docs/architecture/automation.md` · C8 `docs/architecture/sq-vocabulary.md` (each: `/api` URLs only)
- C9 `docs/architecture/prompts/phase-7.md` · C10 `docs/architecture/prompts/phase-8.md` (URLs → `/api/...` + one line: new controllers under `org.sequeless.app.rest` are prefixed by `ApiPathPrefixConfiguration`)
- C11 `sequeless-ui/README.md` (e2e section, env vars) · C12 `docs/specs/README.md` and `docs/specs/phase-9-shell-composition-and-polish.md` (status → Done, once Verification passes)

**Finish:** `npx ng lint sequeless-shell --fix`, then manual fixes per offending file (one file per call); then Verification.

## Part B — operating process (record only)

The spec's Part B is the binding protocol for every call above; it is summarised here, not re-specified:
production/config files only via the agentic CLI (`opencode run`), tests and docs written directly; one or two files per call phrased as a contract
pointing at the spec; verify facts locally (`unzip -l`, `javap`, `.d.ts`) before correcting; never `--resume`/`--continue`; state "do not use WebFetch,
websearch, or the internet" in every prompt; exactly one `opencode` process at a time (`pgrep -af "opencode run"`); launch via
`script -qec /tmp/oc-call.sh /dev/null >> "$LOG" 2>&1` with `run_in_background: true`; write each prompt via a quoted heredoc (`<<'OCCALL'`), never an inline string;
ignore the `cleanup prune=7.days` log line; delete sessions after each lane script; `rm -rf sequeless-app/src/main/resources/static sequeless-app/target/classes/static`
before any backend-only `mvn verify`; commit after each independently verified piece. Per project memory, namespace batch scripts under `/tmp` (e.g. `/tmp/p9-*`) to avoid collisions with concurrent sessions.

## Verification

Run from `sequeless-ui/`:
`npx ng test sequeless-shell --watch=false`, `npx ng lint sequeless-shell`, `npx ng build sequeless-shell --configuration production`;
regression: `npx ng test core-api --watch=false`, `npx ng test objects --watch=false`, `npx ng test ontology --watch=false`, `npx ng test automation --watch=false`;
`E2E_SKIP_BROWSER_CHECK=1 npx playwright test --list` and `npx tsc -p tsconfig.e2e.json --noEmit`.
From the repo root: `rm -rf sequeless-app/src/main/resources/static sequeless-app/target/classes/static && mvn -B -ntp verify` (backend-only path, UI not built, incl. A1 IT),
then `npm run build:ui && mvn -B -ntp verify` (combined path), then `npm run ci:ui`.
Grep checks (must return nothing): `-- --run` in root `package.json`; `playwright|npm run e2e|e2e` in `.github/workflows/ci.yml` and in the root `verify` script;
`@playwright/test` imports under `e2e/` outside `e2e/support/test.ts`; `playwright install` anywhere in the repo outside docs; diff touching
`docs/architecture/prompts/phase-[0-6].md`; any change to `app.routes.ts`.
Manual (needs browsers, out of CI): `npm run e2e` once in an environment with browsers pre-installed; record the result, or "skipped: browsers absent", in the final commit message.

## Open questions (not reversible without outside input)

1. Which sample ontology/data the e2e target serves: journeys need one non-abstract type with an editable property and one type with an `EXTERNAL_SIGNAL`
   transition. Fixed by env vars (D5) but the defaults must match whatever the fat jar's seed provides — confirm when A5/A7 are written.
2. Whether Failsafe is bound for `sequeless-app` in the parent POM (so a `*IT` runs in `mvn verify`). If not, A1 needs a pom edit, which is a separate, explicitly-approved task.
3. Whether the CI runner should ever install Playwright browsers (currently decided: no; revisit only if the team opts e2e into CI, which D7 forbids today).
