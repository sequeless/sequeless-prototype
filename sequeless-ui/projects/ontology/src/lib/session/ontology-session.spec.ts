/*
 * Contract [ONT-SESSION]: OntologySession owns the parsed document, its undo journal and the working
 * store. load() fetches via OntologyClient, apply/applyText/undo/redo bump `revision`, prepareImport()
 * refuses text that does not parse (D6), importSucceeded() rebases base := working and clears the
 * journal (D11), and setRejection() records the issues of a 422 report.
 */
import { TestBed } from '@angular/core/testing';
import { OntologyClient } from '@sequeless/core-api';
import { readClasses, relabel } from '@sequeless/rdf';
import { of, throwError } from 'rxjs';

import { OntologySession } from './ontology-session';

const A = 'http://ex.org/A';
const TTL = `@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
<${A}> a owl:Class ; rdfs:label "A" .
`;

function setup(client: Partial<OntologyClient> = { load: () => of(TTL) }) {
  TestBed.configureTestingModule({
    providers: [OntologySession, { provide: OntologyClient, useValue: client }],
  });
  return TestBed.inject(OntologySession);
}

function labelOf(session: OntologySession): string | null | undefined {
  const store = session.store();
  return store ? readClasses(store).find((c) => c.iri === A)?.label : undefined;
}

describe('OntologySession', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('starts empty', () => {
    const session = setup();
    expect(session.store()).toBeNull();
    expect(session.revision()).toBe(0);
    expect(session.dirty()).toBe(false);
    expect(session.canUndo()).toBe(false);
    expect(session.canRedo()).toBe(false);
    expect(session.lastRejection()).toEqual([]);
  });

  it('load() parses the fetched Turtle and keeps it as the base text', async () => {
    const session = setup();
    await session.load();
    expect(labelOf(session)).toBe('A');
    expect(session.baseText()).toBe(TTL);
    expect(session.loadError()).toBeNull();
  });

  it('load() records an ApiError when the request fails', async () => {
    const error = { status: 503, kind: 'network', title: 'down', issues: [] };
    const session = setup({ load: () => throwError(() => error) });
    await session.load();
    expect(session.loadError()).toBe(error);
    expect(session.store()).toBeNull();
  });

  it('load() records an error when the server text does not parse', async () => {
    const session = setup({ load: () => of('ex:A ex:p') });
    await session.load();
    expect(session.loadError()?.title).toBe('Invalid ontology');
    expect(session.store()).toBeNull();
  });

  it('apply() changes the store, bumps revision and marks dirty', async () => {
    const session = setup();
    await session.load();
    const store = session.store()!;
    session.apply(relabel(store, A, 'Alpha'));
    expect(labelOf(session)).toBe('Alpha');
    expect(session.revision()).toBe(1);
    expect(session.dirty()).toBe(true);
    expect(session.canUndo()).toBe(true);
  });

  it('undo() and redo() move through the journal and bump revision', async () => {
    const session = setup();
    await session.load();
    session.apply(relabel(session.store()!, A, 'Alpha'));
    expect(session.undo()).toBe(true);
    expect(labelOf(session)).toBe('A');
    expect(session.dirty()).toBe(false);
    expect(session.canRedo()).toBe(true);
    expect(session.redo()).toBe(true);
    expect(labelOf(session)).toBe('Alpha');
    expect(session.revision()).toBe(3);
    expect(session.redo()).toBe(false);
  });

  it('applyText() journals a text edit like a structured one', async () => {
    const session = setup();
    await session.load();
    session.applyText(TTL.replace('"A"', '"Alpha"'));
    expect(labelOf(session)).toBe('Alpha');
    expect(session.undo()).toBe(true);
    expect(labelOf(session)).toBe('A');
  });

  it('applyText() throws on invalid Turtle and leaves the store alone', async () => {
    const session = setup();
    await session.load();
    expect(() => session.applyText('ex:A ex:p')).toThrow();
    expect(session.revision()).toBe(0);
  });

  it('applyText() with the text currentText() returned is a no-op', async () => {
    const session = setup();
    await session.load();
    const text = await session.currentText();
    session.applyText(text);
    expect(session.revision()).toBe(0);
  });

  it('baseDocument() exposes the loaded document, whose base quads survive edits', async () => {
    const session = setup();
    expect(session.baseDocument()).toBeNull();
    await session.load();
    const before = session.baseDocument()!.baseQuads.length;
    session.apply(relabel(session.store()!, A, 'Alpha'));
    expect(session.baseDocument()!.baseQuads).toHaveLength(before);
    expect(session.baseDocument()!.store).toBe(session.store());
  });

  it('currentText() serialises the working store', async () => {
    const session = setup();
    await session.load();
    session.apply(relabel(session.store()!, A, 'Alpha'));
    expect(await session.currentText()).toContain('"Alpha"');
  });

  it('prepareImport() returns the text to send', async () => {
    const session = setup();
    await session.load();
    const result = await session.prepareImport();
    expect(result.ok).toBe(true);
    expect(result.ok && result.text).toContain('"A"');
  });

  it('prepareImport() refuses before load', async () => {
    const session = setup();
    expect((await session.prepareImport()).ok).toBe(false);
  });

  it('importSucceeded() rebases the base, clears the journal and bumps importRevision', async () => {
    const session = setup();
    await session.load();
    session.apply(relabel(session.store()!, A, 'Alpha'));
    const sent = await session.prepareImport();
    session.importSucceeded({ accepted: true, typeCount: 1, consistent: true, issues: [] });
    expect(session.dirty()).toBe(false);
    expect(session.canUndo()).toBe(false);
    expect(session.baseText()).toBe(sent.ok ? sent.text : '');
    expect(labelOf(session)).toBe('Alpha');
    expect(session.importRevision()).toBe(1);
    expect(session.lastImport()).toEqual({ accepted: true, typeCount: 1, consistent: true, issues: [] });
    expect(session.lastRejection()).toEqual([]);
  });

  it('setRejection() records the report issues', async () => {
    const session = setup();
    const issues = [{ severity: 'error', subjectIri: A, message: 'bad' }];
    session.setRejection({ consistent: false, issues });
    expect(session.lastRejection()).toEqual(issues);
  });
});
