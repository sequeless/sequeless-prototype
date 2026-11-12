# Sequeless: project history, status and glossary (draft 05, explorer: Haiku)
Checked 2026-11-12 against git HEAD 5a6c7fa. Source root: /Users/bbartolome/workspace/sequeless-prototype. Terms marked [general] have no repo source.

## 0 Status
- Not released or deployed [README.md:41].
- Backend phases 0,3,5,6 have status text. Phases 1,2,4 no status line. Phases 7,8 no evidence found [docs/phase-0-verification.md:305-312; docs/architecture/query-port.md:16; automation.md:3-4; derived-properties.md:25-27].
- All nine UI phases done [docs/specs/README.md:15-25].
- CQRS extraction: 19 specs. 1-12 done. 13 partly done, partly held. 14-19 not started [docs/extraction/remaining-work.md:185-205].

## 1 What it is
- Platform for business objects (e.g. Project, Task) [README.md:3-5].
- Object types are described in an OWL ontology stored as Turtle [docs/architecture/01-overview.md:6-8].
- Serves Browse, Read, Edit, Add, Delete for every type with no per-type code [README.md:5-6].
- Types change at runtime, no redeploy [docs/architecture/02-decision-records.md:12-15].

## 2 Why
- Core idea: the shape of data is itself data [01-overview.md:6].
- OWL lacks some concepts; a small custom `sq:` vocabulary adds them [01-overview.md:8-10].
- Meta-model = OWL ontology + `sq:` vocabulary; single source for structure and behaviour [architecture/README.md:15].
- Core never sees Apache Jena; it reads its own records through read ports [01-overview.md:12-14].

## 3 History: three tracks
- Backend phases 0-8 [03-phased-plan.md:9-19]; UI phases 1-9 [docs/specs/README.md:9-11]; extraction specs 1-19 [remaining-work.md:185-209].

### 3.1 Backend phases
- P0 walking skeleton: met. Check ran 2026-09-16, 85 tests passed [phase-0-verification.md:3,27,51]. Maven Enforcer and ArchUnit stop Spring imports in core [:110,170,187]. Unknown adapter name stops startup [:192-225].
- P1 meta-model core: reasoned read-only ontology copy via port; no status [03-phased-plan.md:34-47].
- P2 BREAD over PostgreSQL; no status; later doc says `sq_object` table in place [03-phased-plan.md:49-57; resume-plan-spec13.md:24].
- P3 query and facets: shipped [query-port.md:16].
- P4 derived properties on read; computed each read; no status [derived-properties.md:3-23].
- P5 state machines, Temporal-backed actions; successful transition fires actions [automation.md:3-4; 03-phased-plan.md:89-103].
- P6 event-driven automation and stored derived values; stored values only for roll-up rule [derived-properties.md:25-27].
- P7 meta-model evolution/migration, P8 adapter swap proof and SPI 1.0: no evidence [03-phased-plan.md:118-140].

### 3.2 UI
- Angular 22 with Angular Material [docs/specs/README.md:3-5]. All nine phases Done; phase 9 closed 2026-10-25 [:15-25; phase-9 spec:3]. Jar serves UI at `/`, API under `/api` [03-phased-plan.md:144-147; DR-17].

### 3.3 Extraction
- Splits one repo into several; each owns one part [remaining-work.md:3]. A package moving out must not depend on code that stays; cycles block moves [prefactor-plan.md:7-9].
- Target: CQRS layout (name not spelled out in repo docs) [spec-13:11]. Command side changes state; Query side reads, never changes [GLOSSARY.md:51-68].
- Process: issue, draft, review, PR; review must approve before a step runs [resume-plan-spec13.md:63-71].
- Repos split out: sequeless-build (specs 1,2, shared BOM), sequeless-template, sequeless-kernel, sequeless-expression, sequeless-filter, sequeless-ontology [remaining-work.md:127-171].

### 3.4 Specs
Tracker wins over issues when they disagree [remaining-work.md:123].
- 1,2 build parent, template repo: done. 3 prototype adopts build parent: done (PR #58, 2026-10-30). 4 `Principal` drops `displayName`: done. 5 sequeless-kernel (identity, values): done, 0.1.0 released 2026-11-04. 6 sequeless-filter: done, issue #34 closed 2026-11-04. 7 prototype consumes kernel: done (PR #80). 8 sequeless-expression (four modules): done, 0.1.1. 9 prototype deletes its expression code: done (PR #84). 10 ontology split into Schema/Behaviour/Automation halves: done (PR #85). 11 sequeless-ontology: done per tracker, 0.1.0 on 2026-11-11 (spec header still says draft). 12 prototype consumes released ontology: done (PR #112, issue #40 closed 2026-11-11, 103 paths deleted).
- 13 outbox, automation ingress, Read port: in progress. 14 sequeless-query (#42), 15 sequeless-automation (#43), 16 ontology drops facet-core (#44), 17 prototype stage 4 cut-over (#45), 18 final fold (#46), 19 sequeless-knowledge (#47): not started.

### 3.5 Spec 13
- Outbox = table of pending work written in the same transaction as the change.
- Merged: P0, P1, P2a, P5a, P6a (Read and Browse ports), P6b (Object view), P7 (search index separation). Gate passes at 684 and 690 tests on JDK 25 [resume-plan-spec13.md:111-158].
- Held: P2 (actions leave outbox), P3 (timers leave outbox), P4 (signals become Store Commands), P5b (Store-event ingress). P5b changes ChangeEventWorkflow; running workflows could fail on Temporal replay [remaining-work.md:274].
- Owner checks blocking: O-3 (non-dev DB holds unprocessed outbox rows of four removed kinds?), O-4 (any Temporal workflows running?). Agent cannot check [resume-plan-spec13.md:32-34,161].
- Navigator-settled choices owner may confirm: O-8 keep state and audit in Object view and REST JSON; B2 read active ontology version, fail and retry on mismatch; B4 search column, function, trigger stay Command side.

### 3.6 Risks
- Repeatedly failing row blocks the outbox relay; no dead-letter path [remaining-work.md:273]. Admin merges only for one ruleset rule when checks green [:65-68]. sequeless-kernel release PR #12 stays open pending snapshot decision [:31,263].

## 4 Decisions (docs/architecture/02-decision-records.md)
DR-01 one dataset now, tenant ID in every call. DR-02 types are runtime data, Turtle import/export. DR-03 OWL meta-model behind port; no Jena type crosses SPI. DR-04 PostgreSQL, one JSONB table `sq_object`. DR-05 Jena built-in reasoner, type level only. DR-06 SHACL rules plus structural checks. DR-07 Turtle docs stored in DB, loaded at startup. DR-08 derived properties via `sq:derivedBy`, computed on read first. DR-09 state machines as ontology data, JEXL guards, Temporal runs actions. DR-10 REST only (stale: UI exists). DR-11 authorization port, default allows all. DR-12 events and actions to outbox in same transaction. DR-13 PostgreSQL full-text search first. DR-14 soft delete, optimistic locking, audit fields. DR-15 Java 25, Spring Boot 4.1.x, Maven, records, no Lombok in SPI/core. DR-16 breaking ontology change needs migration plan. DR-17 `/api`, UI served from same jar. DR-18 Turtle round-trip in browser, backend authoritative. DR-19 UI extension points are DI tokens with defaults. DR-20 hand-written TS wire models.
Ports: ontology (four read/admin interfaces), validation, object store, query, expression, automation, authorization, derivation plugin [01-overview.md:96-108].

## 5 Design preferences (docs/agents/design-preferences.md)
- Goals: minimal and modular, maintainable, open to evolve. Hexagonal: core holds logic, ports are interfaces, adapters are technology.
- Layout: sequeless-spi ports; sequeless-core logic; adapter modules; sequeless-app Spring Boot host [01-overview.md:71-83].
- Build enforces: core imports no Spring or Jena; adapters import neither each other nor core [:85-91].
- Adapter chosen by config property e.g. `sequeless.ontology.adapter=jena`. Each port has contract test kit. Avoid early unification.

## 6 Glossary (GLOSSARY.md, 26 terms)
Ontology: authored OWL+SHACL source. Ontology version: numbered activation. Schema: object types compiled from active ontology. Behaviour: state machines, derivations, automation compiled from it. Browse: objects of a type matching a filter, with facets, sort, page. Read: one object by ID. Field: property reached by a path, with what Query side allows (filter, facet, search, sort). Facet: breakdown of a Field's values with counts. Facet bucket: one value and its count. Object view: what Browse/Read returns per object. Store: where object state is kept; only Store Commands change it. Query side: answers Browse/Read, never changes a Store. Command: request to one target, may be accepted or rejected. Store Command: Command that adds, edits, deletes or fires a transition. Command side: accepts Store Commands. Observable: named stream of domain events sharing one Event type. Domain event: record of something that happened. Store event: made when a Store accepts a Store Command; carries ontology version. Event type: ontology-defined shape. Source: external thing an integration flow connects to. Integration flow: turns source input into an Observable. Side effect: action automation takes after an event. Expression: small formula giving one value. Template: text with expressions. Signature: names an expression may use at its place. Dialect: versioned rules for expressions; one per ontology version.
Term clash: architecture glossary uses "Business object", "Type", "Meta-model", "Projection"; root GLOSSARY wins.

## 7 Read next
remaining-work.md; 02-decision-records.md; 03-phased-plan.md; design-preferences.md; GLOSSARY.md.

## Uncertain
1 Phases 7,8 no evidence. 2 Phases 1,2,4 no status. 3 README stale (describes phase 0; calls five adapters empty shells). 4 Specs 11,12 headers say draft vs tracker done. 5 Spec 13 header out of date on O-8, B2; no owner confirmation recorded. 6 Status from docs only; tracker cites 211c91f, checkout 5a6c7fa. 7 No spec 6 file here. 8 No docs/adr/ directory though CLAUDE.md points to it. 9 DR-10 stale. 10 GLOSSARY mentions undefined "EAD","BR". 11 Spec 8 has two status lines. 12 derived-properties.md cites DR-11 where DR-08 fits. 13 Spring Statemachine archived-July-2026 claim unverified. 14 design-preferences title "Wayfinder Interview Guidelines" stale. 15 Open owner decisions: kernel skip-snapshot, sequeless-filter ruleset, filter 0.1.2 republish. 16 CQRS expansion, BOM, dead-letter, Temporal, Lombok, JSONB, SHACL, JEXL are general definitions.
