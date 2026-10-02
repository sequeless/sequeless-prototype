/**
 * Contract under test (public-api.ts): the library's public surface re-exports every runtime
 * symbol from lib/vocabulary.ts, lib/store.ts, lib/ontology-document.ts, lib/projections.ts,
 * lib/edits.ts and lib/journal.ts (lib/model.ts contributes types only, so nothing of its own
 * shows up at runtime) — and nothing else, so a consumer never has to reach past `./public-api`
 * into `./lib/*`. Alongside that export-surface check, an end-to-end smoke test exercises the
 * round-trip this library exists for: parse a document, relabel a class through the undo/redo
 * journal, serialise, re-parse, and confirm the new label projects back out.
 */
import * as RdfPublicApi from './public-api';
import { FIXTURE_TTL } from './testing/reference-ttl';

const EX_NS = 'https://sequeless.dev/ns/ref#';
const ex = (local: string) => `${EX_NS}${local}`;

// Every runtime (non-type-only) symbol public-api.ts must re-export, grouped by source file.
const EXPECTED_EXPORTS: Record<string, 'function' | 'object' | 'string'> = {
  // lib/vocabulary.ts
  SQ: 'object',
  SQ_NS: 'string',
  OWL: 'object',
  RDF: 'object',
  RDFS: 'object',
  XSD: 'object',
  DATATYPE_TO_XSD: 'object',
  xsdToDatatype: 'function',
  // lib/store.ts
  N3RdfStore: 'function',
  // lib/ontology-document.ts
  parseOntology: 'function',
  serializeOntology: 'function',
  // lib/projections.ts
  readClasses: 'function',
  readProperties: 'function',
  readCardinality: 'function',
  readStateMachine: 'function',
  // lib/edits.ts
  relabel: 'function',
  addAttributeProperty: 'function',
  addRelationshipProperty: 'function',
  setCardinality: 'function',
  setDerivation: 'function',
  setTransitionTrigger: 'function',
  setGuard: 'function',
  // lib/journal.ts
  invertDelta: 'function',
  DeltaJournal: 'function',
};

describe('public-api exports', () => {
  it('exposes exactly the documented runtime symbols and nothing invented', () => {
    expect(Object.keys(RdfPublicApi).sort()).toEqual(Object.keys(EXPECTED_EXPORTS).sort());
  });

  it('exports each documented symbol as the expected kind of runtime value', () => {
    for (const [name, kind] of Object.entries(EXPECTED_EXPORTS)) {
      const value = (RdfPublicApi as Record<string, unknown>)[name];
      expect(value).toBeDefined();
      expect(typeof value).toBe(kind);
    }
  });

  it('exports DeltaJournal and N3RdfStore as constructible classes', () => {
    expect(() => new RdfPublicApi.N3RdfStore()).not.toThrow();
    expect(() => new RdfPublicApi.DeltaJournal(new RdfPublicApi.N3RdfStore())).not.toThrow();
  });
});

describe('end-to-end round-trip: parse -> relabel via DeltaJournal -> serialise -> parse -> read', () => {
  it('shows the new label after a full edit-then-save cycle', async () => {
    const doc = RdfPublicApi.parseOntology(FIXTURE_TTL);
    const journal = new RdfPublicApi.DeltaJournal(doc.store);

    const delta = RdfPublicApi.relabel(doc.store, ex('Task'), 'Renamed Task');
    journal.apply(delta);

    const serialized = await RdfPublicApi.serializeOntology(doc);
    const reparsed = RdfPublicApi.parseOntology(serialized);

    const classes = RdfPublicApi.readClasses(reparsed.store);
    const task = classes.find((c) => c.iri === ex('Task'));
    if (!task) {
      throw new Error(`no ClassView for ${ex('Task')}`);
    }
    expect(task.label).toBe('Renamed Task');
  });

  it('leaves the journal able to undo the relabel back to the original label', () => {
    const doc = RdfPublicApi.parseOntology(FIXTURE_TTL);
    const journal = new RdfPublicApi.DeltaJournal(doc.store);

    const delta = RdfPublicApi.relabel(doc.store, ex('Task'), 'Renamed Task');
    journal.apply(delta);

    const undone = journal.undo();

    expect(undone).toBe(true);
    const classes = RdfPublicApi.readClasses(doc.store);
    const task = classes.find((c) => c.iri === ex('Task'));
    if (!task) {
      throw new Error(`no ClassView for ${ex('Task')}`);
    }
    expect(task.label).toBe('Task');
  });
});
