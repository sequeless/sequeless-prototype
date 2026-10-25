# Angular 22 + Material UI — phased specs

The backend (`docs/architecture/`) has no UI at all. This directory specifies the Angular 22 +
Angular Material frontend added on top of it: one workspace, a thin composition-root application,
and seven libraries, serving the same purpose the backend's own hexagon serves on the Java side —
a stable core, swappable adapters, and a generic surface driven by the ontology rather than
per-type code.

Each file below is one phase. Phases 1–4 are built; their specs double as a record of what exists
and why. Phase 4 landed on branch `phase-4-rdf` and is not yet merged. Phases 5–8 are specified but
not yet implemented — each is detailed enough to hand to an agent (or a person) as a standalone
unit of work, the same way `docs/architecture/prompts/phase-*.md` serve the backend.

## Phase order and status

| Phase | Title | Status |
|---|---|---|
| 1 | [API prefix and SPA serving](phase-1-api-prefix-and-spa-serving.md) | **Done** |
| 2 | [Angular workspace and shell](phase-2-angular-workspace-and-shell.md) | **Done** |
| 3 | [core-api library](phase-3-core-api.md) | **Done** |
| 4 | [rdf library — ontology round-trip](phase-4-rdf.md) | **Done** |
| 5 | [ui-kit library — presentational components](phase-5-ui-kit.md) | Done |
| 6 | [objects feature — generic BREAD](phase-6-objects.md) | **Done** |
| 7 | [ontology feature — authoring UI](phase-7-ontology.md) | **Done** |
| 8 | [automation feature — triggers and signals](phase-8-automation.md) | **Done** |
| 9 | [Shell composition, e2e, CI, docs](phase-9-shell-composition-and-polish.md) | **Done** |

## Dependency order

```
Phase 1 (backend) ──┐
                     ├──► Phase 3 (core-api) ──┬──► Phase 6 (objects)  ──┐
Phase 2 (workspace)──┘        │                 ├──► Phase 7 (ontology) ─┼──► Phase 9 (polish)
                               │                 └──► Phase 8 (automation)┘
                               └──► Phase 4 (rdf) ──► Phase 7, Phase 8
                                    Phase 5 (ui-kit) ──► Phase 6, 7, 8
```

Phases 1–9 are all built. Phase 9 closed on 2026-10-25: its live e2e run passed 6 of 6 and the R4
verification gates passed, with `actionlint` not run (not installed; the workflow is unvalidated).
See `docs/plans/phase-9.5-shell-composition-completion.md`.

## Conventions every phase follows

- **Runtime code only through an agentic CLI** (`opencode run`, currently), driven by an
  orchestrator that reviews every diff. Test/spec files are written directly. See
  `docs/specs/phase-9-shell-composition-and-polish.md` §Process for the exact operating protocol
  that emerged doing Phases 1–3, including the pty-wrapping and quoting fixes — read that section
  before running any phase 4–8 work, it is not optional.
- **Test-first.** Every source file in Phases 4–8 below is preceded by its spec file, written
  against the exact contract in this document, before any implementation exists.
- **No new Maven module, no `frontend-maven-plugin`.** The Angular build writes straight into
  `sequeless-app/src/main/resources/static/` (gitignored); `mvn verify` alone must always stay
  green with that directory absent.
- **Hand-written wire models, not OpenAPI-generated** — recorded as DR-20 (pending write-up in
  `docs/architecture/02-decision-records.md`; see Phase 3's spec for the reasoning).
- **Pluggability mirrors the backend's `sequeless.<port>.adapter` swap.** Every phase that adds a
  swappable concern (RDF store, Turtle editor, graph layout, error mapper, field renderers) adds
  or extends a DI token in `core-api` and a `provideDefault*()` alongside it, never hard-wiring the
  concrete implementation into a feature library.
