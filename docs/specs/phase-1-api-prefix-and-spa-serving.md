# Phase 1 — API prefix and SPA serving

**Status: done.** Commit `791ed83` (plus the `@ConditionalOnWebApplication` fix folded into the
later Gate D cleanup). Backend-only; no Angular code. 112/112 `sequeless-app` tests passing, full
reactor `mvn verify` green.

## Context

Before this phase the backend's four controllers (`OntologyController`, `TypesController`,
`ObjectsController`, `WhoAmIController`) occupied root paths (`/types`, `/objects`, `/ontology`,
`/whoami`), and there was no static-resource serving at all. For the SPA to own `/` and for client
routes (`/ontology/classes/Project`, `/objects/Task/<id>/edit`) to survive a page reload, the REST
API had to move under a prefix and the app had to learn to fall back to `index.html` for unknown,
non-API paths.

## Design

### The `/api` prefix

One new file, `sequeless-app/src/main/java/org/sequeless/app/config/ApiPathPrefixConfiguration.java`:
a `WebMvcConfigurer` whose `configurePathMatch` calls

```java
configurer.addPathPrefix(basePath, HandlerTypePredicate.forBasePackage("org.sequeless.app.rest"));
```

with `basePath` from `@Value("${sequeless.api.base-path:/api}")`, registering nothing when blank
(restoring pre-prefix behaviour). The predicate is **package-scoped, not annotation-scoped** —
`forAnnotation(RestController.class)` would also prefix springdoc's own internal controllers
(`OpenApiWebMvcResource`, `SwaggerConfigResource`), moving `/v3/api-docs` and `/swagger-ui.html`,
which must stay put. `HandlerTypePredicate` moved packages in Spring Framework 7 (Boot 4.1.1): it
is `org.springframework.web.method.HandlerTypePredicate`, not the old Boot-3-era
`org.springframework.web.servlet.handler` location.

No controller was edited. A fifth controller later added to `org.sequeless.app.rest` is prefixed
automatically — this matters for Phase 7's backend work (`docs/architecture/prompts/phase-7.md`),
which adds `POST /ontology/migrations` etc.

### Two defects this migration exposed, fixed in the same wave

1. **`ObjectsController`'s `Location` header was hard-coded**: `URI.create("/objects/" + type + "/"
   + id)`. `addPathPrefix` only affects routing, not string literals, so this pointed at a path
   that no longer existed. Fixed by deriving it from the current request path
   (`ServletUriComponentsBuilder.fromCurrentRequestUri()`), so it tracks whatever prefix is
   configured and stays host-relative (the Angular dev-server proxy must pass it through
   unchanged).
2. **`TypeResponseMapper.toStateMachineResponse` filtered transitions to `USER_ACTION` only.**
   Lifted: `GET /api/types/{iri}` now returns every `TriggerKind` (`USER_ACTION`, `ON_CHANGE`,
   `TIMER`, `EXTERNAL_SIGNAL`), because a UI needs to diagram the whole state machine including
   transitions it can never fire itself. `ObjectsController#transitions` (the per-object endpoint)
   **still filters to `USER_ACTION`** — that asymmetry is deliberate: it lists only the
   transitions a client may actually call via `POST .../transitions/{name}`. The reference
   `ex:Project` machine went from 4 reported transitions to 7
   (`activate`/`hold`/`resume`/`close` user-action, `autoClose` on-change watching
   `belongsToProject`, `expireHold` timer after `PT72H`, `reopen` external-signal `"reopen"`).

### SPA serving

Two new classes, both in `org.sequeless.app.config` (no new package — `onlyAppRestAndConfigMayDependOnCore`
already whitelists it, and neither imports `org.sequeless.core`):

- **`SpaFallbackResourceResolver extends PathResourceResolver`.** `getResource` tries
  `super.getResource` first (keeps traversal protection for real files); returns `null` if
  disabled; returns `null` if the path matches a reserved prefix (`/api`, `/actuator`,
  `/swagger-ui`, `/v3/api-docs`, `/error`, `/webjars` — exact match or `prefix + "/"`); returns
  `null` if the last path segment contains a `.` (a missing hashed asset must 404, never return
  HTML — otherwise a stale build surfaces as a baffling `Unexpected token '<'` error); otherwise
  returns the index resource if it exists, else `null`.
- **`SpaWebConfiguration implements WebMvcConfigurer`**, **`@ConditionalOnWebApplication`**.
  Registers a `/**` resource handler over the locations from `WebProperties.Resources#getStaticLocations()`
  (falling back to Boot's own four default locations if that list is empty), `resourceChain(true)`,
  terminated by the resolver above. The `@ConditionalOnWebApplication` annotation is load-bearing:
  without it, this bean's constructor (which requires a `WebProperties` bean) fails for any
  `SpringApplicationBuilder` built with `WebApplicationType.NONE` — exactly what three existing
  startup-failure tests do (`InconsistentOntologyStartupTest`, `PortBindingStartupFailureTest`,
  `UnknownDerivationPluginStartupTest`), because `WebProperties` is never auto-configured outside
  a servlet web application context.

Properties, in `application.yaml`:

```yaml
sequeless:
  api:
    base-path: /api
  web:
    spa:
      enabled: true
      location: classpath:/static/
      index: index.html
```

**An absent `static/` is a first-class case**, not a degraded one: with no UI built, `index.exists()`
is `false`, the resolver returns `null`, and Spring's normal 404 applies — so plain `mvn verify`
stays green with no Node toolchain involved at all.

## Gotcha discovered twice: stale build artifacts

Maven's `resources:resources` goal copies `sequeless-app/src/main/resources/static/` into
`target/classes/static/` but never cleans stale output when the source directory is later removed
without a `mvn clean`. Running an Angular build (even once, for any reason) and then deleting the
source `static/` directory is **not enough** — `target/classes/static/` must also be removed, or
a subsequent `mvn verify` finds a real `index.html` there and the SPA fallback legitimately serves
it for `/whoami`, `/types` and other unprefixed legacy paths, breaking
`ApiPathPrefixTest.legacyUnprefixedPathsAreNoLongerServed`. This is gitignored generated output,
not source — safe to delete directly, no agentic-CLI step required — but easy to forget.

## Files

- `sequeless-app/src/main/java/org/sequeless/app/config/ApiPathPrefixConfiguration.java` (new)
- `sequeless-app/src/main/java/org/sequeless/app/config/SpaFallbackResourceResolver.java` (new)
- `sequeless-app/src/main/java/org/sequeless/app/config/SpaWebConfiguration.java` (new)
- `sequeless-app/src/main/java/org/sequeless/app/rest/ObjectsController.java` (Location fix)
- `sequeless-app/src/main/java/org/sequeless/app/rest/TypeResponseMapper.java` (filter lift)
- `sequeless-app/src/main/resources/application.yaml` (`sequeless.api.*`, `sequeless.web.spa.*`)
- Every `*EndToEndTest`/`*IT` in `sequeless-app/src/test/java/org/sequeless/app/` rewritten to
  `/api/...` paths (one file, `TemporalAutomationEndToEndTest`, was missed in the first pass and
  had to be caught by a full-tree grep sweep — worth re-sweeping if more test files are added
  later: `grep -rn '"/\(types\|objects\|ontology\|whoami\)' sequeless-app/src/test` should return
  only IRIs, classpath paths, and the two deliberately-unprefixed `ApiPathPrefixTest` assertions).
- New tests: `ApiPathPrefixTest`, `SpaFallbackResourceResolverTest`, `SpaFallbackWithAssetsTest`,
  `SpaFallbackWithoutAssetsTest`, plus fixtures under `sequeless-app/src/test/resources/test-spa/`.

## Verification

`mvn -B -ntp verify` from the repo root — all 11 modules, including `sequeless-app`'s full
112-test suite and the module-boundary `ArchitectureTest` — with `sequeless-app/src/main/resources/static/`
absent. This must stay green with zero Node/Angular involvement.
