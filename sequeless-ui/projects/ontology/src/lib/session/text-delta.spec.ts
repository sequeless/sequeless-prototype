/*
 * Contract [ONT-DELTA]: computeTextDelta(store, turtle) parses `turtle` and returns the QuadDelta that,
 * applied to `store`, makes the store hold exactly the parsed quads. It never mutates `store`, never
 * lists a quad in both sets, and throws on invalid Turtle.
 */
import { parseOntology } from '@sequeless/rdf';
import type { Quad } from '@rdfjs/types';

import { computeTextDelta } from './text-delta';

const PREFIX = '@prefix ex: <http://ex.org/> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n';
const BASE = `${PREFIX}ex:A rdfs:label "A" .\nex:B rdfs:label "B" .\n`;

function keys(quads: readonly Quad[]): string[] {
  return quads.map((q) => `${q.subject.value} ${q.predicate.value} ${q.object.value}`).sort();
}

describe('computeTextDelta', () => {
  it('returns an empty delta for identical text', () => {
    const store = parseOntology(BASE).store;
    const delta = computeTextDelta(store, BASE);
    expect(delta.added).toEqual([]);
    expect(delta.removed).toEqual([]);
  });

  it('is insensitive to prefixes and ordering', () => {
    const store = parseOntology(BASE).store;
    const reordered =
      '@prefix e: <http://ex.org/> .\n@prefix r: <http://www.w3.org/2000/01/rdf-schema#> .\n' +
      'e:B r:label "B" .\ne:A r:label "A" .\n';
    const delta = computeTextDelta(store, reordered);
    expect(delta.added).toEqual([]);
    expect(delta.removed).toEqual([]);
  });

  it('reports a changed literal as one removal and one addition', () => {
    const store = parseOntology(BASE).store;
    const delta = computeTextDelta(store, BASE.replace('"A"', '"Alpha"'));
    expect(keys(delta.removed)).toEqual(['http://ex.org/A http://www.w3.org/2000/01/rdf-schema#label A']);
    expect(keys(delta.added)).toEqual(['http://ex.org/A http://www.w3.org/2000/01/rdf-schema#label Alpha']);
  });

  it('reports a deleted statement and a new statement', () => {
    const store = parseOntology(BASE).store;
    const text = `${PREFIX}ex:A rdfs:label "A" .\nex:C rdfs:label "C" .\n`;
    const delta = computeTextDelta(store, text);
    expect(keys(delta.removed)).toEqual(['http://ex.org/B http://www.w3.org/2000/01/rdf-schema#label B']);
    expect(keys(delta.added)).toEqual(['http://ex.org/C http://www.w3.org/2000/01/rdf-schema#label C']);
  });

  it('applying the delta makes the store hold exactly the parsed quads, including blank nodes', () => {
    const withBlank = `${BASE}ex:A ex:p [ ex:q "x" ] .\n`;
    const store = parseOntology(withBlank).store;
    const next = `${BASE.replace('"B"', '"Bee"')}ex:A ex:p [ ex:q "y" ] .\n`;
    const delta = computeTextDelta(store, next);
    store.apply(delta);
    const expected = parseOntology(next).store;
    expect(store.size).toBe(expected.size);
    const ground = (quads: readonly Quad[]) =>
      quads.filter((q) => q.subject.termType === 'NamedNode' && q.object.termType !== 'BlankNode');
    expect(keys(ground(store.quads()))).toEqual(keys(ground(expected.quads())));
    expect(store.quads().some((q) => q.object.value === 'y')).toBe(true);
    expect(store.quads().some((q) => q.object.value === 'x')).toBe(false);
  });

  it('does not mutate the store and never lists a quad in both sets', () => {
    const store = parseOntology(BASE).store;
    const before = keys(store.quads());
    const delta = computeTextDelta(store, `${PREFIX}ex:A rdfs:label "Z" .\n`);
    expect(keys(store.quads())).toEqual(before);
    const added = new Set(keys(delta.added));
    expect(keys(delta.removed).some((k) => added.has(k))).toBe(false);
  });

  it('throws on invalid Turtle', () => {
    const store = parseOntology(BASE).store;
    expect(() => computeTextDelta(store, 'ex:A ex:p')).toThrow();
  });
});
