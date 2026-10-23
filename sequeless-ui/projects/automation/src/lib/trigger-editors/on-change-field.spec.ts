/**
 * Contract under test (trigger-editors/on-change-field.ts): `SqOnChangeField` (`sq-on-change-field`, inputs
 * `typeIri` and `value` — the watched property IRIs, output `valueChange`) is a multi-select over the type's
 * forward relationship properties, read with `TypesClient.describe(typeIri)` and `relationshipWatchOptions`.
 * Choosing an option emits the new list of IRIs; a failed describe shows a problem banner and no options.
 */
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TypesClient } from '@sequeless/core-api';
import type { TypeDetailResponse } from '@sequeless/core-api';
import { of, throwError } from 'rxjs';

import { SqOnChangeField } from './on-change-field';

const TYPE = {
  iri: 'ex:Task',
  properties: [
    { kind: 'attribute', iri: 'ex:title', label: 'Title' },
    { kind: 'relationship', iri: 'ex:parent', label: 'Parent' },
    { kind: 'relationship', iri: 'ex:owner', label: 'Owner' },
  ],
} as unknown as TypeDetailResponse;

async function setup(describe = () => of(TYPE), value: string[] = []) {
  const describeFn = vi.fn(describe);
  TestBed.configureTestingModule({
    providers: [provideNoopAnimations(), { provide: TypesClient, useValue: { describe: describeFn } }],
  });
  const fixture = TestBed.createComponent(SqOnChangeField);
  fixture.componentRef.setInput('typeIri', 'ex:Task');
  fixture.componentRef.setInput('value', value);
  const emitted: string[][] = [];
  fixture.componentInstance.valueChange.subscribe((v) => emitted.push(v));
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  const open = async () => {
    (fixture.nativeElement.querySelector('.mat-mdc-select-trigger') as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    return [...TestBed.inject(OverlayContainer).getContainerElement().querySelectorAll<HTMLElement>('mat-option')];
  };
  return { fixture, host: fixture.nativeElement as HTMLElement, emitted, describeFn, open };
}

describe('SqOnChangeField', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('describes the given type', async () => {
    const { describeFn } = await setup();
    expect(describeFn).toHaveBeenCalledWith('ex:Task');
  });

  it('offers relationship properties only', async () => {
    const { open } = await setup();
    const options = await open();
    expect(options.map((o) => o.textContent?.trim())).toEqual(['Parent', 'Owner']);
  });

  it('emits the chosen IRIs, keeping the current ones', async () => {
    const { open, emitted } = await setup(() => of(TYPE), ['ex:parent']);
    const options = await open();
    options.find((o) => o.textContent?.trim() === 'Owner')!.click();
    expect(emitted.at(-1)).toEqual(['ex:parent', 'ex:owner']);
  });

  it('shows a problem banner when the type cannot be read', async () => {
    const { host } = await setup(() => throwError(() => ({ status: 500, title: 'boom', violations: [], issues: [] })));
    expect(host.querySelector('sq-problem-banner')).not.toBeNull();
  });
});
