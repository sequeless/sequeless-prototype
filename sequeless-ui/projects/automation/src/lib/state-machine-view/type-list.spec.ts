/**
 * Contract [AUT-TYPELIST]: SqAutomationTypeList (`sq-automation-type-list`) lists the non-abstract types from
 * TypesClient.list(), sorted by label, each linking to `./{name}` (`a[data-type]`); a failed request shows a
 * problem banner; an empty list says "No types available."
 */
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { TypesClient } from '@sequeless/core-api';
import type { TypeSummaryResponse } from '@sequeless/core-api';
import { of, throwError } from 'rxjs';

import { SqAutomationTypeList } from './type-list';

const summary = (name: string, label: string, isAbstract = false): TypeSummaryResponse => ({
  iri: `urn:${name}`,
  name,
  label,
  abstract: isAbstract,
  superTypes: [],
});

async function setup(list: () => ReturnType<TypesClient['list']>) {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideNoopAnimations(), { provide: TypesClient, useValue: { list } }],
  });
  const fixture = TestBed.createComponent(SqAutomationTypeList);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('SqAutomationTypeList', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('lists non-abstract types by label, linking to ./{name}', async () => {
    const host = await setup(() => of([summary('Task', 'Task'), summary('Thing', 'Thing', true), summary('Order', 'Order')]));
    const links = [...host.querySelectorAll('a[data-type]')];
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Order', 'Task']);
    expect(links.map((a) => a.getAttribute('href')?.split('/').pop())).toEqual(['Order', 'Task']);
  });

  it('says so when there are none', async () => {
    expect((await setup(() => of([]))).textContent).toContain('No types available.');
  });

  it('shows a problem banner when the request fails', async () => {
    const host = await setup(() => throwError(() => ({ status: 500, title: 'boom', violations: [], issues: [] })));
    expect(host.querySelector('sq-problem-banner')).not.toBeNull();
  });
});
