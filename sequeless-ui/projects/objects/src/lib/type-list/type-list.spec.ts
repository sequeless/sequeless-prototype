/**
 * Contract [OBJ-TYPELIST] (lib/type-list/type-list.ts, `SqTypeList`): lists the concrete (non-abstract)
 * types from `TypesClient.list()` sorted by label, each linking to `./{name}`. With no types it shows
 * an empty-state message; a failed request is surfaced through the problem banner.
 */
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import type { TypeSummaryResponse } from '@sequeless/core-api';
import { TypesClient } from '@sequeless/core-api';
import { provideUiKit } from '@sequeless/ui-kit';
import { of } from 'rxjs';

import { SqTypeList } from './type-list';

function summary(name: string, label: string, isAbstract = false): TypeSummaryResponse {
  return { iri: `urn:type:${name}`, name, label, abstract: isAbstract, superTypes: [] };
}

const TYPES: TypeSummaryResponse[] = [
  summary('Task', 'Task'),
  summary('Thing', 'Abstract Thing', true),
  summary('Project', 'Project'),
  summary('Milestone', 'Milestone'),
];

async function setup(types: TypeSummaryResponse[] = TYPES) {
  const client = { list: vi.fn<() => ReturnType<TypesClient['list']>>(() => of(types)) };

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideUiKit(),
      provideNoopAnimations(),
      { provide: TypesClient, useValue: client },
    ],
  });

  const fixture = TestBed.createComponent(SqTypeList);
  fixture.detectChanges();
  await fixture.whenStable();
  await new Promise((r) => setTimeout(r));
  fixture.detectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement };
}

describe('SqTypeList', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('lists non-abstract types sorted by label', async () => {
    const { host } = await setup();

    const labels = [...host.querySelectorAll('a')].map((a) => a.textContent?.trim());

    expect(labels).toEqual(['Milestone', 'Project', 'Task']);
  });

  it('hides abstract types', async () => {
    const { host } = await setup();

    expect(host.textContent ?? '').not.toContain('Abstract Thing');
  });

  it('links each type to its name', async () => {
    const { host } = await setup();

    const hrefs = [...host.querySelectorAll('a')].map((a) => a.getAttribute('href') ?? '');

    expect(hrefs).toHaveLength(3);
    ['Milestone', 'Project', 'Task'].forEach((name, i) => {
      expect(hrefs[i].endsWith(`/${name}`)).toBe(true);
    });
  });

  it('shows an empty-state message when there are no types', async () => {
    const { host } = await setup([]);

    expect(host.querySelectorAll('a')).toHaveLength(0);
    expect(host.textContent ?? '').toContain('No types');
  });
});
