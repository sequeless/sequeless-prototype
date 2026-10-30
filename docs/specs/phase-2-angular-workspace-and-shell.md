# Phase 2 — Angular workspace and shell

**Status: done.** Commit `3b3e5bd` plus the C1–C3 fix-up chain. One application, seven libraries,
build/lint green across every project.

## Context

Nothing Angular existed in the repo. This phase stands up the workspace shape everything else in
Phases 3–8 lives inside, wires its production build to land in the Spring Boot app's static
resources directory (Phase 1), and gives the shell a real (if still mostly empty) layout and
routing skeleton so later phases attach features rather than build the scaffold themselves.

## Design

### Workspace shape

`sequeless-ui/` holds one application and seven libraries, each importable as `@sequeless/<name>`:

```
sequeless-ui/
  angular.json  package.json  package-lock.json  tsconfig.json  proxy.conf.json
  projects/
    sequeless-shell/   (application — the composition root, no business logic)
    core-api/          (library — DI tokens, wire models, HTTP plumbing; see Phase 3)
    rdf/               (library — ontology round-trip; see Phase 4)
    ui-kit/            (library — presentational components; see Phase 5)
    objects/           (library — generic BREAD feature; see Phase 6)
    ontology/          (library — authoring feature; see Phase 7)
    automation/        (library — triggers/signals feature; see Phase 8)
    testing/           (library — fakes and fixtures shared by the other libraries' specs)
```

`tsconfig.json`'s `compilerOptions.paths` map every `@sequeless/<name>` to
`./projects/<name>/src/public-api.ts` — the **source** file, not a `dist/` build output. This is
why `ng build sequeless-shell` compiles the whole dependency graph in one pass with no ng-packagr
pre-build step and no stale-dist class of bug. Each library still carries an `ng-package.json`
(for a hypothetical future `npm publish`), but that path is never exercised by `npm run build`.

Tooling choices, all Angular 22 defaults rather than something chosen against the grain: Vitest as
the unit test runner (`@angular/build:unit-test`; Karma is deprecated, do not opt into it),
zoneless bootstrap, ESLint via `angular-eslint`.

### Production build → the backend's static resources

`angular.json`'s `sequeless-shell` build target:

```json
"outputPath": { "base": "../sequeless-app/src/main/resources/static", "browser": "" },
"baseHref": "/"
```

**`"browser": ""` is mandatory.** Without it, Angular 17+'s default layout writes into
`<base>/browser/`, which `classpath:/static/index.html` would never find — this is the single
easiest thing to get wrong when generating this config and was in fact gotten wrong once in this
project's own history before being caught by the build-output check. `deleteOutputPath` defaults
to `true`, so every `ng build` **wipes** `sequeless-app/src/main/resources/static/` first; that's
desirable (the directory is gitignored and holds nothing else), but it's why the root `npm run
build` script always runs `build:ui` before `mvn package`, never the reverse.

### Local dev proxy

`sequeless-ui/proxy.conf.json` proxies `/api`, `/actuator`, `/v3/api-docs` and `/swagger-ui` to
`http://localhost:8080` (`secure: false`, `changeOrigin: true`), wired via `sequeless-ui/package.json`'s
`start` script: `ng serve sequeless-shell --proxy-config proxy.conf.json`.

### Root npm delegation

The repo root has a thin `package.json` (no dependencies) whose scripts delegate: `build` runs
`build:ui` (→ `npm --prefix sequeless-ui run build`) then `mvn -B -ntp package`, in that order,
because the Angular build wipes and repopulates `static/` and Maven must copy it afterward. `start`
delegates to `sequeless-ui`'s dev server; `start:api` runs `mvn -pl sequeless-app spring-boot:run`.

### Shell composition root

`provideSequeless()` (Phase 3) and `provideAnimationsAsync()` (required by Angular Material's
ripple/overlay animations, and **not** pulled in transitively by `@angular/material` — it needed
an explicit `@angular/animations` dependency added) are wired into `app.config.ts` alongside
`provideRouter`. `app.routes.ts` lazy-loads the three feature libraries via `loadChildren`, with a
default redirect into `objects`:

```ts
export const routes: Routes = [
  { path: '', redirectTo: 'objects', pathMatch: 'full' },
  { path: 'objects', loadChildren: () => import('@sequeless/objects').then(m => m.objectsRoutes) },
  { path: 'ontology', loadChildren: () => import('@sequeless/ontology').then(m => m.ontologyRoutes) },
  { path: 'automation', loadChildren: () => import('@sequeless/automation').then(m => m.automationRoutes) },
];
```

`app.ts`/`app.html` replace the generated CLI placeholder with a `mat-sidenav-container` layout: a
side nav (`mat-nav-list`) listing the three sections by `{ path, label, icon }`, and a
`mat-toolbar` above the routed content.

Each of the three feature libraries currently exports only a placeholder-backed `Routes` constant
(`objectsRoutes`, `ontologyRoutes`, `automationRoutes`), each with one route (`path: ''`) pointing
at the library's generated placeholder component. Phases 6–8 replace these with real feature
routes; the shell does not need to change when they do.

## Files

- `sequeless-ui/angular.json`, `tsconfig.json`, `proxy.conf.json`, `package.json`
- `sequeless-ui/projects/sequeless-shell/src/app/{app.ts,app.html,app.scss,app.routes.ts,app.config.ts}`
- `sequeless-ui/projects/{objects,ontology,automation}/src/lib/*.routes.ts` and `public-api.ts`
- Root `package.json`

## Verification

From `sequeless-ui/`: `npx ng build sequeless-shell --configuration production` must produce
`sequeless-app/src/main/resources/static/index.html` directly (no `browser/` subdirectory);
`npx ng lint <project>` for every project must be clean. From the repo root: `npm run build`
must produce the `-exec.jar` with `BOOT-INF/classes/static/index.html` inside it. Remember to
delete `sequeless-app/src/main/resources/static/` **and** `sequeless-app/target/classes/static/`
before any subsequent `mvn verify` that's meant to prove the backend-only, no-UI-built case (see
Phase 1's gotcha).
