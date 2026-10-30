/*
 * Contract [ONT-IMPORT]: ImportFlow.run() resolves 'blocked' when the working text does not parse or the
 * diff/request fails (reason in `problems`), 'cancelled' when the user declines the preview, 'accepted'
 * after a 200 (session.importSucceeded), and 'rejected' after a 422: the report is recorded on the session,
 * routed onto registered controls (the first is revealed) and unmatched issues land in `problems`.
 */
import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { OntologyClient } from '@sequeless/core-api';
import { relabel } from '@sequeless/rdf';
import { of, throwError } from 'rxjs';

import { ControlRegistry } from '../issues/control-registry';
import { ONTOLOGY_DIFF_SOURCE } from '../diff-preview/ontology-diff';
import { OntologySession } from '../session/ontology-session';
import { ImportFlow } from './import-flow';

const A = 'http://ex.org/A';
const TTL = `@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
<${A}> a owl:Class ; rdfs:label "A" .
`;
const ACCEPTED = { accepted: true, typeCount: 1, consistent: true, issues: [] };

async function setup(options: {
  confirm?: boolean;
  import?: ReturnType<OntologyClient['import']>;
  diffFails?: boolean;
} = {}) {
  const client = {
    load: () => of(TTL),
    import: vi.fn(() => options.import ?? of({ status: 'accepted' as const, response: ACCEPTED })),
  };
  const diffSource = {
    diff: vi.fn(() =>
      options.diffFails
        ? Promise.reject(new Error('Server-side ontology diff is not implemented'))
        : Promise.resolve({ subjects: [], baseText: 'b', workingText: 'w' }),
    ),
  };
  const dialog = {
    open: vi.fn(() => ({ afterClosed: () => of(options.confirm ?? true) })),
  };
  TestBed.configureTestingModule({
    providers: [
      OntologySession,
      ControlRegistry,
      ImportFlow,
      { provide: OntologyClient, useValue: client },
      { provide: ONTOLOGY_DIFF_SOURCE, useValue: diffSource },
      { provide: MatDialog, useValue: dialog },
    ],
  });
  const session = TestBed.inject(OntologySession);
  await session.load();
  session.apply(relabel(session.store()!, A, 'Alpha'));
  return { flow: TestBed.inject(ImportFlow), session, registry: TestBed.inject(ControlRegistry), client, diffSource, dialog };
}

describe('ImportFlow', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('is blocked before the ontology is loaded', async () => {
    TestBed.configureTestingModule({
      providers: [OntologySession, ControlRegistry, ImportFlow, { provide: OntologyClient, useValue: {} }],
    });
    const flow = TestBed.inject(ImportFlow);
    expect(await flow.run()).toBe('blocked');
    expect(flow.problems()).not.toBeNull();
  });

  it('accepted: previews the diff, posts the working text and rebases the session', async () => {
    const { flow, session, client, diffSource, dialog } = await setup();
    expect(await flow.run()).toBe('accepted');
    const [base, working] = diffSource.diff.mock.calls[0] as unknown as [{ store: unknown }, unknown];
    expect(base.store).toBe(session.store());
    expect(working).toBe(session.store());
    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(client.import).toHaveBeenCalledWith(expect.stringContaining('"Alpha"'));
    expect(session.importRevision()).toBe(1);
    expect(session.dirty()).toBe(false);
    expect(flow.problems()).toBeNull();
  });

  it('cancelled: the user declines the preview and nothing is sent', async () => {
    const { flow, client, session } = await setup({ confirm: false });
    expect(await flow.run()).toBe('cancelled');
    expect(client.import).not.toHaveBeenCalled();
    expect(session.dirty()).toBe(true);
  });

  it('blocked: the diff source fails', async () => {
    const { flow, client } = await setup({ diffFails: true });
    expect(await flow.run()).toBe('blocked');
    expect(client.import).not.toHaveBeenCalled();
    expect(JSON.stringify(flow.problems())).toContain('not implemented');
  });

  it('blocked: a transport failure is surfaced as the ApiError', async () => {
    const error = { status: 500, kind: 'spring-default', title: 'Boom', issues: [] };
    const { flow, session } = await setup({ import: throwError(() => error) });
    expect(await flow.run()).toBe('blocked');
    expect(flow.problems()).toBe(error);
    expect(session.dirty()).toBe(true);
  });

  it('rejected: routes issues onto controls, reveals the first and banners the rest', async () => {
    const issues = [
      { severity: 'error', subjectIri: A, message: 'bad label' },
      { severity: 'error', subjectIri: null, message: 'global' },
    ];
    const { flow, session, registry } = await setup({
      import: of({ status: 'rejected' as const, report: { consistent: false, issues } }),
    });
    const control = new FormControl('');
    const element = document.createElement('div');
    element.scrollIntoView = vi.fn();
    registry.register(A, control, element);
    expect(await flow.run()).toBe('rejected');
    expect(control.errors).toEqual({ server: 'bad label' });
    expect(element.scrollIntoView).toHaveBeenCalled();
    expect(flow.problems()).toEqual([issues[1]]);
    expect(session.lastRejection()).toEqual(issues);
    expect(session.dirty()).toBe(true);
  });

  it('clears the previous problems when it starts again', async () => {
    const { flow } = await setup({ confirm: false });
    await flow.run();
    expect(flow.problems()).toBeNull();
  });
});
