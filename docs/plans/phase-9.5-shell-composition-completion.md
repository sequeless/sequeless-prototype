# Phase 9.5 plan — shell composition, e2e, CI, docs: completion

Branch: `sequeless-prototype-ui` (stacked on the Phase 9 commits, `38a848c..2a41516`). Spec: still
`docs/specs/phase-9-shell-composition-and-polish.md`; there is no new spec. This plan holds the Phase 9 plan items
(`docs/plans/phase-9-shell-composition-and-polish.md`) that were not finished, plus the live checks Phase 9 could not
run. Frontend paths are relative to `sequeless-ui/` unless they start with `docs/`, `sequeless-app/` or `.github/`.

## State at the start

- Built and committed: A1 `FatJarServesUiAndApiIT` (38a848c); A2 the redirect assertion in `app.routes.spec.ts`
  (50786c3); B2–B6 the e2e harness (`@playwright/test` pinned exactly at 1.64.1, `tsconfig.e2e.json`,
  `e2e/support/test.ts`, `e2e/support/browser-check.ts`, `playwright.config.ts`); B7 root `verify` fix plus `ci:ui`
  and `ci:combined` (bfda7bc); B8 the `ui` and opt-in `combined` CI jobs (c247053); A4–A7 the journeys (A6 happy path
  only); C1–C11 the docs (DR-17..20, READMEs, `/api` URL sweep, UI track section, prompt notes).
- Verified locally: `ng lint sequeless-shell` clean; `ng test` shell 8/8, core-api 188, objects 149, ontology 149,
  automation 97; shell production build ok (initial 757.86 kB, over the 500 kB warning, under the 1 MB error);
  `npm run ci:ui` exit 0; `tsc -p tsconfig.e2e.json` green; `E2E_SKIP_BROWSER_CHECK=1 npx playwright test --list`
  lists 5 tests in 4 files; every Phase 9 grep check empty.
- Backend: A1 green on the backend-only path (2 tests, `/` is 404). Its plan command needs
  `-Dfailsafe.failIfNoSpecifiedTests=false`, because `-am` includes the parent pom, which has no matching test.

## Amendments made during Phase 9 (record only, already agreed)

- `app.routes.ts` composes automation lazily on the `objects` and `ontology` routes (b9c5c41, Phase 8). The plan's
  `[SHELL-FINAL]` (automation providers in `app.config.ts`) and D1 ("app.routes.ts is not edited") were stale.
  B1 became a verify-only gate; A3 (`app.composition.spec.ts`) was dropped because `app.config.spec.ts` and
  `app.routes.spec.ts` already cover every assertion it planned.
- The `/ontology` list has no heading, so A4 asserts `.sq-ontology-toolbar` instead.
- The signal buttons live on the object detail page (`SqObjectSignals`), not on `/automation/<type>`. A7 sets up a
  Closed Project through the API (Person → Project with owner → activate → close) and observes the state change
  through the `reopen` button disappearing, since `knownSignals` only offers it from Closed.
- A6 was split: the happy path is committed, and the forced-422 path is A6b below.

## Left

### R1 — A6b: forced 422 on ontology import
- File: `e2e/ontology-edit-import.spec.ts` (add one test; the happy path stays as is).
- First read which subject IRI the UI registers the Title label control under
  (`projects/ontology/src/lib/issues/control-registry.ts`, `type-editor/property-editor.ts`).
- Route-fulfil `POST **/api/ontology` with 422 and an `OntologyReportResponse` body,
  `{ consistent: false, issues: [{ severity, subjectIri, message }] }`
  (`sequeless-app/.../rest/OntologyReportResponse.java:15,24`). Use one issue whose `subjectIri` matches the control
  and one that matches nothing.
- Assert: `.sq-server-error` on the control shows the first message, and the problem banner lists the unmatched issue.
- Check: `tsc -p tsconfig.e2e.json --noEmit`; `--list` shows 6 tests.

### R2 — live e2e run (manual, never CI)
- Start a stack: either the fat jar on 8080 against Postgres (`docker run postgres:18`, pass
  `--sequeless.persistence.url/username/password`), or `npm start` (ng serve + proxy) with `npm run start:api`.
  Browsers must already be present (`~/.cache/ms-playwright/chromium-*`); never run `playwright install`.
- Run `npm run e2e` (set `E2E_BASE_URL=http://localhost:8080` for the jar).
- Unverified selectors to confirm or fix in this run, one spec file per fix:
  - A5: the create and edit field is `[data-field="title"]`;
  - A6: the Title row locator (`sq-property-editor` filtered by the text "Title");
  - A6: the browse view exposes a `columnheader` role;
  - A7: the `owner` relationship value format (a bare UUID) and the transition response shape.
- A6 mutates the shared ontology and has no reset, so run it against a disposable database.
- Record the result in the commit message, or "skipped: <reason>".

### R3 — A1 UI-present branch
- `npm run build:ui && mvn -B -ntp verify` so `FatJarServesUiAndApiIT` exercises `/` == 200 `text/html`.
  It has only run on the backend-only branch.
- Also correct the A1 command in `docs/plans/phase-9-shell-composition-and-polish.md` to add
  `-Dfailsafe.failIfNoSpecifiedTests=false`.

### R4 — full Verification from the Phase 9 plan
- Backend-only path: `rm -rf sequeless-app/src/main/resources/static sequeless-app/target/classes/static && mvn -B -ntp verify`.
- Combined path: `npm run build:ui && mvn -B -ntp verify`; then `npm run ci:ui`.
- Workflow: run `actionlint .github/workflows/ci.yml` where available. Neither actionlint nor a YAML parser was
  available in Phase 9, so the workflow has not been validated.

### R5 — status (C12)
- `docs/specs/README.md`: the status table still lists phases 6–8 as "Not started", but they are built. Correct those
  rows and the prose above the table. Mark phase 9 Done only after R1–R4 pass; otherwise "Partially done" with the
  gaps named.
- `docs/specs/phase-9-shell-composition-and-polish.md`: the status line, same rule.

## Open items (not blocking)

- `@types/node` 26.6.4 is used directly by `e2e/support/*` but is only a transitive lockfile entry (via
  `@angular/build`). Declare it if a future `npm ci` drops it.
- `FatJarServesUiAndApiIT` discards the child process output, so a startup failure times out without a cause. Redirect
  the output to a temp file if this test ever fails in CI.
- Initial bundle 757.86 kB is over the 500 kB warning budget (pre-existing since Phase 8).

## Order

R3 (fix the doc command, then run) → R1 → R2 → R4 → R5. Commit after each independently verified piece.
