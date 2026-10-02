/**
 * Contract under test (lib/ontology-document.ts): `parseOntology` synchronously parses a Turtle
 * document into an `OntologyDocument` — the base quads as fetched, a captured prefix map (from the
 * parser's `onPrefix` callback), and a working `RdfStore` seeded from a *copy* of those base quads,
 * so mutating the store never reaches back into `baseQuads`. Invalid Turtle throws synchronously.
 * `serializeOntology` writes the whole working store back out, re-using the captured prefix map, so
 * parse -> serialize -> parse is quad-set-preserving and any triple outside the `sq:` vocabulary
 * (the fixture's stray `rdfs:comment`) survives an edit-then-save cycle untouched. Per
 * docs/specs/phase-4-rdf.md, every assertion here is quad-set equality, never Turtle string
 * equality, since `N3.Writer` relabels blank nodes on every round-trip.
 */
import { DataFactory } from 'n3';
import type { Quad } from '@rdfjs/types';
import { parseOntology, serializeOntology } from './ontology-document';
import { FIXTURE_TTL } from '../testing/reference-ttl';
import { sameQuadSet } from '../testing/quad-set';

const { namedNode, literal, quad } = DataFactory;

const EX = (local: string) => namedNode(`https://sequeless.dev/ns/ref#${local}`);
const SQ_LABEL = namedNode('https://sequeless.dev/ns/meta#label');
const RDFS_COMMENT = namedNode('http://www.w3.org/2000/01/rdf-schema#comment');

describe('parseOntology', () => {
  it('captures the prefix map declared by the parsed document', () => {
    const doc = parseOntology(FIXTURE_TTL);
    expect(doc.prefixes['ex']).toBe('https://sequeless.dev/ns/ref#');
    expect(doc.prefixes['sq']).toBe('https://sequeless.dev/ns/meta#');
  });

  it('seeds the store from a copy, so adding a quad to it leaves baseQuads untouched', () => {
    const doc = parseOntology(FIXTURE_TTL);
    const baseCount = doc.baseQuads.length;
    const intruder: Quad = quad(EX('s'), EX('p'), EX('o'));

    doc.store.add(intruder);

    expect(doc.baseQuads.length).toBe(baseCount);
    expect(doc.baseQuads.some((q) => q.equals(intruder))).toBe(false);
  });

  it('seeds the store from a copy, so deleting a quad from it leaves baseQuads untouched', () => {
    const doc = parseOntology(FIXTURE_TTL);
    const victim = doc.baseQuads[0];

    doc.store.delete(victim);

    expect(doc.baseQuads.some((q) => q.equals(victim))).toBe(true);
  });

  it('throws an Error when given syntactically invalid Turtle', () => {
    const invalid = '@prefix ex: <https://example.test/> .\nex:s ex:p "unterminated .';
    expect(() => parseOntology(invalid)).toThrow(Error);
  });
});

describe('parseOntology -> serializeOntology -> parseOntology round-trip', () => {
  it('preserves the exact quad set of an untouched document', async () => {
    const doc = parseOntology(FIXTURE_TTL);

    const serialized = await serializeOntology(doc);
    const reparsed = parseOntology(serialized);

    expect(sameQuadSet(doc.baseQuads, reparsed.baseQuads)).toBe(true);
  });

  it('keeps a stray triple and reflects a hand-built edit after edit-then-save', async () => {
    const doc = parseOntology(FIXTURE_TTL);

    const oldLabel = quad(EX('Task'), SQ_LABEL, literal('Task'));
    const newLabel = quad(EX('Task'), SQ_LABEL, literal('Renamed Task'));
    doc.store.apply({ removed: [oldLabel], added: [newLabel] });

    const expectedQuads = doc.baseQuads.filter((q) => !q.equals(oldLabel)).concat(newLabel);

    const serialized = await serializeOntology(doc);
    const reparsed = parseOntology(serialized);

    const hasStrayComment = reparsed.baseQuads.some(
      (q) => q.subject.equals(EX('Task')) && q.predicate.equals(RDFS_COMMENT),
    );
    expect(hasStrayComment).toBe(true);
    expect(sameQuadSet(reparsed.baseQuads, expectedQuads)).toBe(true);
  });
});
