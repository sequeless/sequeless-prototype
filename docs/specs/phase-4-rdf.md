# Phase 4 — rdf library: the ontology round-trip

**Status: not started.** No specs, no implementation. Needed by Phase 7 (ontology authoring) and
Phase 8 (automation's trigger editors, which also author `sq:` terms).

## Context

The backend's only ontology write surface is `POST /api/ontology`, which replaces the **entire**
active ontology document from a raw Turtle body (`OntologyController`). There is no per-type,
per-property, or per-transition authoring endpoint, and none is planned before Phase 7's backend
work (`docs/architecture/prompts/phase-7.md`), which only adds a dry-run diff mode — the whole-
document replace stays the write model. So authoring in the UI means: fetch the whole document,
let the user edit a structured view of part of it, and send the whole document back. `rdf` is
where that round-trip lives, kept deliberately separate from the `ontology` feature library
(Phase 7) so the parsing/diffing machinery is independently testable without any Material UI.

## The critical invariant

**The form model is never the source of truth for the graph.** Rebuilding the graph from a form
model is exactly the design that silently drops triples the UI doesn't understand — `rdfs:comment`,
`owl:disjointWith`, SHACL shapes, anything a future phase adds to the vocabulary. Instead:

- Projections (graph → form model) are **read-only pure functions** over a quad store.
- Every edit is a **targeted delta** — e.g. relabelling a type is "remove exactly
  `(typeIri, rdfs:label, "old")`, add `(typeIri, rdfs:label, "new")`", nothing else.
- The serialised output is the **whole working store** with the captured prefix map, so every
  quad nobody touched is emitted verbatim.

## Design

### Store and vocabulary

`RdfStore` is an RDF/JS-shaped interface (typed against `@rdfjs/types`, so the abstraction is
RDF/JS-shaped, not tied to one library's own types), with `N3RdfStore` as the default
implementation (`n3@2.7`, ~90 KB, an RDF/JS-compliant `Parser`/`Writer`/`Store`). This is the
concrete value that lands behind `core-api`'s `RDF_STORE_FACTORY` token as its default — Phase 3
only reserved the slot.

`OntologyDocument` wraps a parse result: the base quads (as fetched), a working `RdfStore` seeded
from a copy of them, and the captured prefix map (from `N3.Parser`'s `onPrefix` callback, needed
to re-serialise with the same abbreviations the document arrived with).

A `SQ` constants module exports every `sq:` term as a full IRI
(`https://sequeless.dev/ns/meta#<term>`) plus the standard `OWL`/`RDF`/`RDFS`/`XSD` namespaces.
**Every `sq:` term this library reads or writes must appear in
`docs/architecture/sq-vocabulary.md`** — that document is the single source of truth for the
mapping; do not invent terms, and if a term there is missing from `SQ`, that's a bug in this
library, not license to use an undocumented one.

### Reading: pure projections

Given a store, pure functions project it into form-friendly shapes: `readClasses`, `readProperties`
(including resolving a property's cardinality — see below — and its `sq:derivedBy` rollup/plugin
shape if present), `readStateMachine` (states by `sq:displayOrder`, transitions with their
`sq:trigger` individual mapped to one of the four kinds and that kind's specific term:
`sq:watch`/`sq:after`/`sq:signalName`). These mirror `SnapshotMapper`'s own mapping rules
one-for-one (`docs/architecture/sq-vocabulary.md`'s big table is the checklist); they are *not*
required to reproduce the backend's reasoning (no OWL inference needed client-side — the UI
always starts from what the server last accepted, so inferred facts aren't being re-derived, only
asserted ones are being edited).

### Writing: targeted deltas

Every edit function takes the current store plus a small, specific edit description and returns a
`QuadDelta { added: Quad[]; removed: Quad[] }`, which is then applied to the working store and
pushed onto an undo/redo journal. Representative cases:

| Edit | Delta |
|---|---|
| Relabel a type or property | Remove `(iri, sq:label, "old")` (or `rdfs:label` if that's what's actually asserted — check both per `sq:label`'s documented fallback priority), add `(iri, sq:label, "new")`. |
| Add an attribute property | `(iri, rdf:type, owl:DatatypeProperty)`, `rdfs:domain typeIri`, `rdfs:range xsd:<datatype>` (mapped from the `Datatype` enum), plus whichever of `sq:displayOrder`/`sq:displayGroup`/`sq:facet`/`sq:indexed`/`sq:searchable`/`sq:hidden` the form sets. |
| Add a relationship property | `owl:ObjectProperty` + `rdfs:range targetTypeIri`, optionally `owl:inverseOf` and/or `owl:TransitiveProperty`. |
| **Change a property's cardinality** | **Not a simple annotation triple.** Cardinality is an `owl:Restriction` blank node under the *type's* `rdfs:subClassOf` list, shaped `[ a owl:Restriction ; owl:onProperty <prop> ; owl:minCardinality "n"^^xsd:nonNegativeInteger ]` (see `sequeless-spi-testkit/.../ontology/reference.ttl` for worked examples). Editing this means: find the restriction blank node for that `(type, property)` pair if one exists (walk the type's `rdfs:subClassOf` objects, filter to blank nodes typed `owl:Restriction` with a matching `owl:onProperty`), and either patch its `owl:minCardinality`/`owl:maxCardinality` literal in place, or — if none exists yet — mint a new blank node and add `(type, rdfs:subClassOf, newBlankNode)` alongside the restriction's own triples. Removing a cardinality constraint means removing the restriction node and the `rdfs:subClassOf` edge to it, *only if* no other triple still references that blank node. |
| Mark a property derived | `(iri, sq:derivedBy, _:rule)` plus the rollup (`sq:function`/`sq:over`/`sq:via`/`sq:of`/`sq:filter`) or plugin (`sq:pluginName`) shape on `_:rule`. The UI must surface that **declaring `sq:derivedBy` forces `readOnly` to `true`** server-side regardless of any `sq:readOnly` triple, and disable that control once a derivation is set. |
| Change a transition's trigger kind | Replace the `sq:trigger` object (one of `sq:UserAction`/`sq:OnChange`/`sq:Timer`/`sq:ExternalSignal`) **and** remove whichever of `sq:watch`/`sq:after`/`sq:signalName` belonged to the *previous* kind, since those terms are only meaningful for one kind each and a stale one left behind would misrepresent the transition. This "clean up the sibling terms" step is exactly the kind of thing to unit-test explicitly — it's an easy thing to get half-right. |
| Edit a guard | `(transition, sq:guard, "<jexl-text>")`, optionally `sq:guardMessage`. |

### Blank nodes: the one accepted wart

`reference.ttl` models restrictions, transitions and derivation rules as inline blank nodes
(`[ a owl:Restriction ; ... ]`). N3's parser assigns stable per-parse labels to these, so editing
by label is correct within one session. But `N3.Writer` re-emits them as labelled `_:b0` nodes
rather than inline `[...]`, so **the first round-trip through this library produces a semantically
identical, textually reformatted document** — anonymous nodes move, but every triple survives.

**Decision: accept this.** `GET /api/ontology` already returns Jena's own serialisation, not the
hand-authored file in `sequeless-spi-testkit`, so the canonical over-the-wire text is already
machine-formatted; `OntologyFixtureDriftTest` compares repo *files* to each other, never server
output, so nothing in the existing test suite is affected. The rejected alternative — surgical
text edits that preserve the original formatting — is fragile and can't stay consistent with an
undo/redo journal. **Tests in this library must assert quad-set equality, never string equality**,
for exactly this reason.

### Serialising

`N3.Writer`, re-using the captured prefix map from the original parse, producing the full document
text sent as the body of `POST /api/ontology`.

## What this library does *not* do

- No OWL reasoning. The server's reasoner (Jena, `sequeless.ontology.reasoner`) is the only
  authority on inferred facts; this library only ever reads/writes asserted triples.
- No SHACL validation. A 422 from the server is the only validation signal; see Phase 7 for how
  its issues get routed back onto the right form field by `subjectIri`.
- No partial PATCH semantics. Every `POST /api/ontology` is a full-document replace; this library's
  job is making sure that replacement is a faithful superset-preserving edit of what was there.

## Files (to be created)

- `projects/rdf/src/lib/store.ts` — `RdfStore` interface, `N3RdfStore`.
- `projects/rdf/src/lib/vocabulary.ts` — `SQ`/`OWL`/`RDF`/`RDFS`/`XSD` constants.
- `projects/rdf/src/lib/ontology-document.ts` — `OntologyDocument`, parse/serialise.
- `projects/rdf/src/lib/projections.ts` — `readClasses`, `readProperties`, `readStateMachine`.
- `projects/rdf/src/lib/edits.ts` — the `apply*` delta functions, one per row in the table above.
- `projects/rdf/src/lib/journal.ts` — undo/redo over a sequence of `QuadDelta`s.
- `projects/rdf/src/public-api.ts`

Test-first, as in Phases 1–3: a `*.spec.ts` per source file above, written against this document,
before any implementation exists. The round-trip test (parse `reference.ttl`, serialise, re-parse,
compare quad sets) belongs with `ontology-document.spec.ts`; include at least one triple outside
the `sq:` vocabulary (e.g. a stray `rdfs:comment`) in the test fixture specifically to prove it
survives an edit-then-save cycle untouched.

## Verification

`npx ng test rdf --watch=false`, `npx ng lint rdf`, then `npx ng build sequeless-shell --configuration
production` to confirm nothing elsewhere in the dependency graph breaks (this library has no
consumers yet, so that build check is largely a no-op until Phase 7, but run it anyway for
consistency with every other phase's verification step).
