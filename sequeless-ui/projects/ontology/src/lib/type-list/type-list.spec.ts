/*
 * Contract [ONT-TYPELIST]: SqOntologyTypeList (`sq-ontology-type-list`) loads the ontology into the session
 * and lists every type from TypesClient.list() — abstract ones included, sorted by label — each linking to
 * `./{name}`. It has a "Raw Turtle" link, an sq-import-bar, a problem banner for a failed types request or
 * ontology load, and refetches the types whenever the session's importRevision changes.
 */
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TypesClient } from '@sequeless/core-api';
import type { TypeSummaryResponse } from '@sequeless/core-api';
import { of, throwError } from 'rxjs';

import { ImportFlow } from '../import-result/import-flow';
import { OntologySession } from '../session/ontology-session';
import { SqOntologyTypeList } from './type-list';

function summary(name: string, label: string, isAbstract = false): TypeSummaryResponse {
  return { iri: `urn:type:${name}`, name, label, abstract: isAbstract, superTypes: [] };
}

const TYPES = [summary('Task', 'Task'), summary('Thing', 'Abstract Thing', true), summary('Project', 'Project')];

async function setup(list: () => ReturnType<TypesClient['list']> = () => of(TYPES), loadError: unknown = null) {
  const importRevision = signal(0);
  const session = {
    importRevision,
    loadError: signal(loadError),
    ensureLoaded: vi.fn(() => Promise.resolve()),
    canUndo: signal(false),
    canRedo: signal(false),
    dirty: signal(false),
    lastImport: signal(null),
  };
  const client = { list: vi.fn(list) };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideNoopAnimations(),
      { provide: TypesClient, useValue: client },
      { provide: OntologySession, useValue: session },
      { provide: ImportFlow, useValue: { problems: signal(null), run: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(SqOntologyTypeList);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement, client, session, importRevision };
}

describe('SqOntologyTypeList', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('lists every type, abstract included, sorted by label', async () => {
    const { host } = await setup();
    const labels = [...host.querySelectorAll('a[data-type] .sq-type-label')].map((a) => a.textContent?.trim());
    expect(labels).toEqual(['Abstract Thing', 'Project', 'Task']);
  });

  it('links each type to ./{name}', async () => {
    const { host } = await setup();
    const hrefs = [...host.querySelectorAll('a[data-type]')].map((a) => a.getAttribute('href'));
    expect(hrefs.map((h) => h?.split('/').pop())).toEqual(['Thing', 'Project', 'Task']);
  });

  it('marks abstract types', async () => {
    const { host } = await setup();
    expect(host.querySelector('a[data-type="Thing"]')?.textContent).toContain('abstract');
  });

  it('links to the raw Turtle editor', async () => {
    const { host } = await setup();
    expect(host.querySelector('a[data-action="raw-turtle"]')?.getAttribute('href')).toMatch(/raw$/);
  });

  it('hosts the import bar', async () => {
    const { host } = await setup();
    expect(host.querySelector('sq-import-bar')).not.toBeNull();
  });

  it('loads the ontology into the session', async () => {
    const { session } = await setup();
    expect(session.ensureLoaded).toHaveBeenCalledTimes(1);
  });

  it('shows an empty state when there are no types', async () => {
    const { host } = await setup(() => of([]));
    expect(host.textContent).toContain('No types');
  });

  it('surfaces a failed types request in the banner', async () => {
    const error = { status: 500, kind: 'problem', title: 'Types failed', detail: null, violations: [], issues: [] };
    const { host } = await setup(() => throwError(() => error));
    expect(host.querySelector('sq-problem-banner')?.textContent).toContain('Types failed');
  });

  it('refetches after an import', async () => {
    const { fixture, client, importRevision } = await setup();
    expect(client.list).toHaveBeenCalledTimes(1);
    importRevision.set(1);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(client.list).toHaveBeenCalledTimes(2);
  });
});
