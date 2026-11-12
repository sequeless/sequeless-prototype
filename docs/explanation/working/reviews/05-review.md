# Review of draft 05 (history, status, decisions, design preferences, glossary)

Reviewer checked the draft against /Users/bbartolome/workspace/sequeless-prototype at HEAD 5a6c7fa (2026-11-12). Read-only.

## VERDICT: REVISE

Most facts are correct (about 30 of 34 checked claims). But one rule is reversed (which source wins), and the backend phase status is left vague when the code settles it. A newcomer would learn a wrong rule and a wrong picture of what is built. Fix the items in section 1, add the items in section 2, then the draft can be approved. The STE problems (section 3) are systematic but easy to fix.

## 1. Wrong or unsupported claims

| # | Draft claim | Correct fact | Evidence |
|---|---|---|---|
| W1 (major) | 3.4: "Tracker wins over issues when they disagree [remaining-work.md:123]." | The reverse. The GitHub issues are the source of truth. If the tracker and an issue disagree, the issue wins and you fix the tracker. | remaining-work.md:3 ("The GitHub issues are the source of truth"); remaining-work.md:123 ("If this file and an issue disagree, the issue wins: fix the file.") |
| W2 (major) | 0 and 3.1: phases 1, 2, 4 have "no status"; phases 7, 8 "no evidence found". | The code settles all five. Phases 1, 2 and 4 are built: `TypesController` (/types, /types/reload), `OntologyController` (POST/GET /ontology), `ObjectsController` (BREAD, transitions, signals), `sq_object` and `sq_ontology_document` tables, `spi/derivation/DerivationPlugin`, and derived-properties.md describes phase 4 as built ("Outcome: reading or browsing a Project returns openTaskCount..."). Phases 7 and 8 are NOT built: there is no `sq_meta_migration`, no diff/plan/apply endpoint, no `inmemory` store or query adapter, no OIDC authorization adapter, no event publisher. The only phase-8 item present is the japicmp plugin, and it is a skipped no-op because no SPI artifact was ever published. DR-18 says the UI is "forward-compatible with backend Phase 7", which also implies phase 7 is future work. Say "built" and "not built", and drop uncertain items 1 and 2. | sequeless-app/src/main/java/org/sequeless/app/rest/*.java; frozen-migrations/*/V1__objects_ontology_outbox.sql; sequeless-spi/pom.xml:71-79; 02-decision-records.md DR-18; grep for sq_meta_migration, inmemory, japicmp, Kafka |
| W3 (minor) | 3.5: "Merged: P0, P1, ..." | P0 is "satisfied" by the spec 12 cut-over gate at `29c2492`. It has no PR of its own. | resume-plan-spec13.md:113 |
| W4 (minor) | 4: "Ports: ontology (four read/admin interfaces), validation, object store, query, ..." | The query row now holds four ports: `ReadPort`, `BrowsePort`, `IndexPreparationPort`, `QueryPort` (the split from spec 13 P6a and P7). The catalogue also lists `ClockPort` and `IdGenerator`. The draft hides the change that spec 13 just made. | 01-overview.md port catalogue (updated by 5a6c7fa) |
| W5 (minor) | 3.3: "Repos split out: sequeless-build, sequeless-template, ..." | sequeless-build and sequeless-template are new support repos (build parent, BOM, workflows; repo template). No prototype code moved into them. Only kernel, expression, filter and ontology hold code from the prototype (filter came from spec 6). | remaining-work.md Done list (spec 1, spec 2) |
| W6 (minor) | Uncertain 6 implies commits after 211c91f are docs only. | 5a6c7fa also changes `sequeless-app/.../CoreConfiguration.java` (4 lines). Low risk, but "docs only" is not exact. | `git show --stat 5a6c7fa` |
| W7 (minor) | 3.5: "Gate passes at 684 and 690 tests on JDK 25". | Correct numbers, but they are the gates of P6b and P7 only, not of all merged steps. State that. | resume-plan-spec13.md:158 |

Claims verified as correct (sample): not released or deployed (README:41); phase 0 MET on 2026-09-16 with 85/85 tests, Enforcer plus ArchUnit, unknown adapter stops startup (phase-0-verification.md:4,27,309-312); all nine UI phases Done, phase 9 done 2026-10-25; 19 specs, 1-12 done, 13 open, 14-19 not started with issues #42-#47; spec 3 PR #58 on 2026-10-30; spec 4 done (PR #69); spec 6 issue #34 closed 2026-11-04; spec 7 PR #80; spec 8 0.1.1; spec 9 PR #84; spec 10 PR #85; spec 11 headers still say draft while tracker says done; spec 12 PR #112, issue #40 closed 2026-11-11, 103 files; spec 13 merged P1, P2a, P5a, P6a, P6b (#125), P7 (#124); held P2, P3, P4, P5b on O-3 and O-4; O-3 and O-4 definitions; navigator choices O-8, B2 route 1, B4 (resume-plan-spec13.md:159); spec 13 header still lists O-8, B2, B4 as open (uncertain 5 is correct); dead-letter risk; admin-merge rule; kernel #12 stays open; DR-01 to DR-20 summaries all match 02-decision-records.md; DR-10 stale; derived-properties.md cites DR-11 (authorization) for materialised values (uncertain 12 correct); GLOSSARY has 26 terms; EAD and BR appear only in _Avoid_ lists; no docs/adr directory exists, though CLAUDE.md and docs/agents/domain.md point to it; design-preferences.md title is "Wayfinder Interview Guidelines"; README still describes phase 0 and "five adapter modules are empty shells" (README:191-195).

## 2. Missing important topics

1. **Source-of-truth order** (after W1 is fixed): issues, then tracker, then spec headers. Spec headers lag (specs 11, 12, 13).
2. **Built vs not built, in one table**: backend phases 0-6 built; 7-8 not built. Today the reader must assemble this from five places.
3. **What the prototype now consumes**: core and SPI import released `sequeless-kernel`, `sequeless-ontology`, `sequeless-expression` and `sequeless-filter` artifacts (01-overview.md module rules). This is the main visible effect of specs 1-12.
4. **Module list**: the 11 Maven modules (01-overview.md:68-81) and what each holds. The draft gives only four groups.
5. **How to build**: JDK 25, Maven 3.9+, `mvn -B verify`, GitHub Packages token for the parent POM, `npm run build:ui` for the UI jar. One line is enough.
6. **Work roles**: Opus reviews (read-only), Haiku implements, the Navigator settles scope questions, owner keeps owner decisions; plan approved only on Opus APPROVE with zero blocking findings (resume-plan-spec13.md:63-71).
7. **The `sq:` vocabulary file** (docs/architecture/sq-vocabulary.md) and **docs/plans/** (UI phase build records) are not in "Read next".
8. **Spec 13 open risk detail**: until P5b lands, a Temporal change event recomputes twice (no-op second time). Newcomers will see this in code.

## 3. STE problems

### Systematic problems

1. **Verbless fragments carry the facts.** Accepted for a condensed draft, but some fragments are ambiguous, for example "Phases 7,8 no evidence found" (no evidence of what: docs or code?) and "P6 ... stored values only for roll-up rule".
2. **Undefined abbreviations.** CQRS, BOM, SPI, DR, P0..P7, O-3, B2, JSONB, SHACL, JEXL, TS, DI, ID are used before (or without) a definition. STE needs each technical noun defined at first use. The draft defers several to "general definitions" but never gives them.
3. **One word, many meanings.** "phase" (backend phase, UI phase), "stage" (extraction stage), "step" (spec 13 step) and "spec" all name units of work. "done", "met", "shipped", "merged", "satisfied" all name completion. "held", "blocking", "gates" all name waiting. "Read" is a glossary term (capital R) and also a plain verb ("reads its own records", "Query side reads"). Choose one word per meaning and keep "Read" for the glossary term only.
4. **Passive voice.** "Object types are described in an OWL ontology", "Types change at runtime", "Adapter chosen by config property", "Status from docs only". STE wants active voice with a clear agent.
5. **Non-STE marks and Latin words.** "e.g.", slashes ("read/admin", "Browse/Read", "evolution/migration"), "+", "=", "vs" and semicolons that join two topics. Use "for example", "and", "or", and two sentences.
6. **Two topics in one sentence.** For example the P5b line joins what P5b changes and the replay risk; the risks line joins three unrelated risks.
7. **Words outside the STE approved style**: "proof", "fold", "cut-over", "spawn", "pending", "authoritative", "breakdown". Keep the project terms (define them once), and replace the rest with approved words ("show", "last merge", "waiting", "is the reference").

### Five rewrite examples

1. Draft: "Tracker wins over issues when they disagree."
   STE: "The GitHub issues are the reference. If the tracker file does not agree with an issue, the issue is correct. Then, change the tracker file."
2. Draft: "P7 meta-model evolution/migration, P8 adapter swap proof and SPI 1.0: no evidence."
   STE: "The team did not build backend phase 7 (ontology migration). The team did not build backend phase 8 (adapter swap and SPI 1.0). The code has no migration table and no in-memory adapter."
3. Draft: "Object types are described in an OWL ontology stored as Turtle."
   STE: "An OWL ontology describes the object types. The system keeps the ontology as Turtle text."
4. Draft: "Held: ... P5b (Store-event ingress). P5b changes ChangeEventWorkflow; running workflows could fail on Temporal replay."
   STE: "Step P5b waits for owner check O-4. Step P5b changes the ChangeEventWorkflow code. A workflow that runs during this change can fail when Temporal replays it."
5. Draft: "Adapter chosen by config property e.g. `sequeless.ontology.adapter=jena`."
   STE: "A configuration property selects the adapter for each port. For example, `sequeless.ontology.adapter=jena` selects the Jena adapter."
