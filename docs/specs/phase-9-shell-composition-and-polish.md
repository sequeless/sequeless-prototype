# Phase 9 — shell composition, e2e, CI, and the operating process

**Status: done (2026-10-25).** The shell's composition root, layout and lazy routing were built
in Phase 2; the end-to-end proof, the CI wiring and the documentation updates were completed under
docs/plans/phase-9.5-shell-composition-completion.md. The live e2e run passed 6 of 6 and the R4
gates passed; actionlint was not run because it is not installed, so the workflow is unvalidated. This phase also carries **the process section**, which is not optional reading:
it's the distilled, empirically-learned protocol for driving the agentic CLI that built Phases
1–3, and every one of Phases 4–8 should follow it rather than rediscovering the same failure
modes.

## Part A — remaining build-out

### Final shell wiring

Once Phases 6–8 replace their placeholder routes with real ones, re-verify the shell needs no
change at all — `app.routes.ts`'s three `loadChildren` entries already point at
`objectsRoutes`/`ontologyRoutes`/`automationRoutes` by name, not by component, so a feature
library can grow arbitrarily without the shell being touched. If that stops being true at some
point, that's a sign a feature library leaked something it shouldn't have into the shell.

### End-to-end proof

Two layers, deliberately different weights:

1. **The authoritative layer stays on the Java side.** `FatJarServesUiAndApiIT` (an actual `*IT`
   class under Failsafe, so it runs during `mvn verify` after `package` — meaning the `-exec` jar
   already exists): launch the fat jar as a real process on a free port, poll `/actuator/health`
   until `UP`, then assert `GET /api/whoami` returns 200 and — **conditionally** on whether
   `BOOT-INF/classes/static/index.html` exists inside the jar (check via `java.util.jar.JarFile`,
   not by assuming) — assert `GET /` is 200 HTML if it does, or 404 if it doesn't. The
   conditional is the point: this test must stay green whether or not the UI was built, exactly
   like `SpaFallbackWithoutAssetsTest` (Phase 1). If this proves flaky in CI (process launch, port
   allocation, and health polling are all real sources of flakiness), the documented fallback is
   to narrow it to a pure `JarFile`-content assertion and leave the live-HTTP proof entirely to
   Phase 1's already-passing `SpaFallbackWithAssetsTest`.
2. **Playwright specs, authored but explicitly out of CI.** `sequeless-ui/e2e/` covering the four
   journeys only a real browser session can prove: a deep-link reload lands on the right route; a
   create → edit → stale-conflict recovery sequence across two tabs (Phase 6); an ontology edit →
   preview → import → type-list-reflects-it sequence (Phase 7); firing a signal and seeing the
   state change only after a refetch, not immediately (Phase 8). **Before writing these, confirm
   Playwright's browsers are actually installed in whatever environment will run them** — they
   were *not* present in the environment this was built in (`PLAYWRIGHT_BROWSERS_PATH` empty,
   no `/opt/pw-browsers`), despite an earlier assumption that they would be. **Never run
   `playwright install`** to fix that — `playwright.config.ts` must check for the browsers and
   fail with a clear message rather than attempting a download. `npm run e2e` must never be wired
   into CI or into the root `npm run verify` script.

### CI

Add a **second** job to `.github/workflows/ci.yml` (Node 24, `actions/setup-node@v6`,
`cache-dependency-path: sequeless-ui/package-lock.json`, then `npm ci --prefix sequeless-ui`,
lint, `test -- --watch=false` — **not** `-- --run`, which isn't a valid flag for `ng test` in
Angular 22; the root `package.json`'s own `verify` script had exactly this bug once already —
build). **Do not touch the existing `mvn -B -ntp verify` job** — it must keep passing with no UI
build, which is the entire point of Phase 1's empty-`static/` design. An optional third job can
run the UI build *then* `mvn verify` to prove the combined path, but the two existing proofs
(backend-only, and UI-build-then-backend) must never merge into one job that could mask either
one going red independently.

### Docs to update once Phases 4–8 land

- `docs/architecture/02-decision-records.md`: append DR-17 (`/api` prefix + SPA co-served from one
  port, `addPathPrefix` chosen over per-controller mappings), DR-18 (client-side RDF round-trip
  authoring, the blank-node reformatting consequence, forward-compatibility with the backend's own
  Phase 7), DR-19 (frontend pluggability mirrors the backend's adapter swap), DR-20 (hand-written
  wire models, not generated, with the two concrete reasons from `docs/specs/phase-3-core-api.md`).
- `README.md`: a "Build with the UI" section (`npm install && npm run build`, `npm start` +
  `npm run start:api`), every `curl localhost:8080/...` example updated to `/api/...`, and a note
  that `mvn verify` alone still works and produces an API-only jar.
- `docs/architecture/03-phased-plan.md`: a "UI track (parallel to Phases 7–8)" section — not a
  numbered phase, deliberately, so the backend's own Phase 7/8 numbering is never disturbed.
- `docs/architecture/{01-overview,query-port,derived-properties,automation,sq-vocabulary}.md`:
  `/api` URL references.
- `docs/architecture/prompts/phase-7.md` and `phase-8.md` (the backend's own, still-future prompts):
  update their URLs to `/api/...` and add one line noting new controllers under
  `org.sequeless.app.rest` are prefixed automatically by `ApiPathPrefixConfiguration`.
- `docs/architecture/prompts/phase-0.md` … `phase-6.md`: **do not touch** — historical records of
  completed backend work; rewriting their URLs would falsify the record of what was actually run
  at the time.

## Part B — the operating process (read before running Phases 4–8)

This section exists because Phases 1–3 burned real time rediscovering the same handful of issues
more than once. It is the accumulated fix, not a theory.

### Runtime code only through the agentic CLI; tests and docs are yours to write directly

Every production file (Java, TS, HTML, SCSS, `*.json` config) is written by `opencode run`, driven
by an orchestrator that reviews every diff — never by directly editing a production file. Test
files, spec files, and documentation (including every file in this directory) are the
orchestrator's to write directly; they don't need the CLI round-trip.

### Keep each `opencode` call to one or two files, phrased as a contract

State the observable behaviour and point at the exact spec file to read first — never paste file
contents as the instruction. When a call fails on something structural (a wrong import path, a
wrong method name, a type-narrowing rough edge), **resolve the fact locally first** — `unzip -l`
or `javap` against the actual jar in `~/.m2` for a Java API question, reading the actual generated
`.d.ts` for a TypeScript one — and put the verified fact directly in the next corrective call,
rather than letting the model guess again or go research it itself.

### Never use `--resume`/`--continue`. Forbid internet access explicitly, every time

Every call is fresh and self-contained. Without an explicit instruction, the model will try
`WebFetch`/`websearch` to verify an API detail, and on a slow/unreliable network that can burn an
entire turn for nothing — state "do not use WebFetch, websearch, or access the internet" in every
prompt, not just the ones that seem risky.

### Exactly one `opencode` process at a time, repo-wide

The backend here is a shared, slow, local model — running two calls concurrently just slows both
down. Check `pgrep -af "opencode run"` before launching another one.

### Launch in the background, wrapped in a pty — never redirect raw stdout/stderr to a file

**This is the single most important fix in this list.** `opencode` (a Node process) fully
block-buffers its stdout/stderr when it's piped to a plain file, so a genuinely-working call can
show *zero* visible output for its entire duration — which, on this backend, can be 10–40+
minutes per turn. That silence is indistinguishable from a real hang unless you force the
buffering off, and a `SIGKILL` sent to a "stuck-looking" process erases whatever was silently
buffered, discarding real progress. The fix, confirmed empirically (a trivial call wrapped this
way streamed ~20 structured log lines within the first second; the same call piped raw showed
nothing until exit):

```bash
script -qec /tmp/oc-call.sh /dev/null >> "$LOG" 2>&1
```

where `/tmp/oc-call.sh` is a small standalone script containing the actual
`opencode run --print-logs --log-level DEBUG --auto -m ollama/qwen-agent:latest '<contract>'`
invocation. Always launch the whole wrapping shell command via the harness's own
`run_in_background: true` parameter (not a trailing shell `&`, which the harness can't track for
a completion notification) — never nest backgrounding inside backgrounding.

### Never embed a long prompt directly in a quoted shell string — write it to a nested heredoc file instead

A prompt with an apostrophe (`Spring Boot's default error`) inside a single-quoted shell string
silently breaks the quoting and produces a confusing downstream syntax error far from the actual
cause. The durable fix is structural, not "be careful with apostrophes": write each `opencode`
call into its own small script file via a **quoted heredoc** (`<<'OCCALL'` … `OCCALL`), which takes
its body completely literally — no escaping needed for anything, ever — then invoke that file by
its plain path. Nested heredocs (an outer one building the whole lane script, an inner one per
`opencode` call inside it) work perfectly well with distinct delimiter tags; there is no need to
flatten everything into one escaped string.

### A hang you might see once and shouldn't chase: the internal session-prune log line

`opencode` logs `message=cleanup prune=7.days` at a fixed ~60-second offset from its own startup,
every single run, regardless of session-table size (confirmed by checking row counts directly in
`~/.local/share/opencode/opencode.db` via Python's `sqlite3` module — they were all zero, and the
same apparent stall still happened). It is an unrelated scheduled background task, not the actual
point of failure; before the pty fix above, this log line often appeared to be "the last thing
before it got stuck" purely because it happened to log right before the real (buffered, invisible)
model call started. Don't spend time on this log line specifically; if a call looks stuck, check
whether its *pty-wrapped* log has advanced recently before concluding anything.

### Clean up `opencode` sessions after each lane script, as hygiene

`opencode session list` / `opencode session delete <id>` — run the latter for every session after
a script's `opencode` calls finish (a one-line loop is enough; see any `/tmp/lane-*.sh` from
Phases 1–3 for the exact snippet). This did **not** turn out to be the fix for the prune-related
confusion above, but it's harmless, keeps the local session database small, and costs nothing to
keep doing.

### Delete stale build artifacts before trusting a "no UI built" test result

Covered in Phase 1's own gotcha section — repeated here because it's exactly the kind of thing
that resurfaces the moment a new phase runs an Angular build for any reason. Always
`rm -rf sequeless-app/src/main/resources/static sequeless-app/target/classes/static` before a
`mvn verify` that's meant to prove the backend-only path.

### Commit often, at a finer grain than "one commit per phase"

Each phase above has several independently-verifiable pieces (per the "Files" list in each phase
document) — commit each one as soon as it's implemented and verified, rather than batching a
whole phase into one commit. This kept Phases 1–3 recoverable at every step and made it trivial
to tell, after the fact, exactly which commit introduced which behaviour.
