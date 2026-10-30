/*
 * Contract [ONT-DIFF]: groupBySubject folds a QuadDelta into per-subject N-Triples-ish lines;
 * clientSideDiff() diffs the base document's original quads against a working store and carries the
 * serialised text of both sides; ONTOLOGY_DIFF_SOURCE defaults to the client-side source, and to a
 * source that rejects as "not implemented" when SEQUELESS_FEATURES.serverSideOntologyDiff is on.
 */
import { TestBed } from '@angular/core/testing';
import { SEQUELESS_FEATURES } from '@sequeless/core-api';
import { N3RdfStore, parseOntology, relabel } from '@sequeless/rdf';

import { clientSideDiff, groupBySubject, ONTOLOGY_DIFF_SOURCE } from './ontology-diff';

const TTL = `@prefix ex: <http://ex.org/> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
ex:A rdfs:label "A" .
ex:B rdfs:label "B"@en .
`;
const LABEL = 'http://www.w3.org/2000/01/rdf-schema#label';

describe('groupBySubject', () => {
  it('returns nothing for an empty delta', () => {
    expect(groupBySubject({ added: [], removed: [] })).toEqual([]);
  });

  it('groups added and removed quads by subject, sorted by subject', () => {
    const doc = parseOntology(TTL);
    const a = doc.store.match(null, null, null).filter((q) => q.subject.value.endsWith('A'));
    const b = doc.store.match(null, null, null).filter((q) => q.subject.value.endsWith('B'));
    const changes = groupBySubject({ added: [...b, ...a], removed: a });
    expect(changes.map((c) => c.subject)).toEqual(['http://ex.org/A', 'http://ex.org/B']);
    expect(changes[0]).toEqual({
      subject: 'http://ex.org/A',
      added: [`<http://ex.org/A> <${LABEL}> "A" .`],
      removed: [`<http://ex.org/A> <${LABEL}> "A" .`],
    });
    expect(changes[1].added).toEqual([`<http://ex.org/B> <${LABEL}> "B"@en .`]);
    expect(changes[1].removed).toEqual([]);
  });
});

describe('clientSideDiff', () => {
  it('is empty with identical base and working text when nothing changed', async () => {
    const doc = parseOntology(TTL);
    const diff = await clientSideDiff().diff(doc, doc.store);
    expect(diff.subjects).toEqual([]);
    expect(diff.workingText).toBe(diff.baseText);
  });

  it('reports a relabel against the original base quads, leaving the base untouched', async () => {
    const doc = parseOntology(TTL);
    const working = new N3RdfStore(doc.store.quads());
    working.apply(relabel(working, 'http://ex.org/A', 'Alpha'));
    const diff = await clientSideDiff().diff(doc, working);
    expect(diff.subjects).toEqual([
      {
        subject: 'http://ex.org/A',
        added: [expect.stringContaining('"Alpha"')],
        removed: [expect.stringContaining('"A"')],
      },
    ]);
    expect(diff.baseText).toContain('"A"');
    expect(diff.workingText).toContain('"Alpha"');
    expect(diff.workingText).not.toContain('"A"');
  });
});

describe('ONTOLOGY_DIFF_SOURCE', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('defaults to the client-side source', async () => {
    const doc = parseOntology(TTL);
    const diff = await TestBed.inject(ONTOLOGY_DIFF_SOURCE).diff(doc, doc.store);
    expect(diff.subjects).toEqual([]);
  });

  it('rejects as not implemented when serverSideOntologyDiff is on', async () => {
    TestBed.configureTestingModule({
      providers: [{ provide: SEQUELESS_FEATURES, useValue: { serverSideOntologyDiff: true } }],
    });
    const doc = parseOntology(TTL);
    await expect(TestBed.inject(ONTOLOGY_DIFF_SOURCE).diff(doc, doc.store)).rejects.toThrow(/not implemented/i);
  });
});
