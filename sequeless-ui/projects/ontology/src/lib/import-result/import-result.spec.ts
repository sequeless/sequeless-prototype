/*
 * Contract [ONT-IMPORT] (import-result.ts): SqImportResult is the dialog shown after an accepted import. It
 * injects MAT_DIALOG_DATA (an OntologyImportResponse), shows the type count and whether the ontology is
 * consistent, lists the issues as warnings, and closes on `data-action="close"`.
 */
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { OntologyImportResponse } from '@sequeless/core-api';

import { SqImportResult } from './import-result';

function render(data: OntologyImportResponse) {
  const close = vi.fn();
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatDialogRef, useValue: { close } },
    ],
  });
  const fixture = TestBed.createComponent(SqImportResult);
  fixture.detectChanges();
  return { el: fixture.nativeElement as HTMLElement, close };
}

describe('SqImportResult', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows the type count and consistency', () => {
    const { el } = render({ accepted: true, typeCount: 7, consistent: true, issues: [] });
    expect(el.textContent).toContain('7 types');
    expect(el.textContent).toContain('consistent');
    expect(el.querySelector('.sq-import-warning')).toBeNull();
  });

  it('lists warnings with their subjects', () => {
    const { el } = render({
      accepted: true,
      typeCount: 1,
      consistent: false,
      issues: [{ severity: 'warning', subjectIri: 'http://ex.org/A', message: 'check this' }],
    });
    const warning = el.querySelector('.sq-import-warning');
    expect(warning?.textContent).toContain('check this');
    expect(warning?.textContent).toContain('http://ex.org/A');
    expect(el.textContent).toContain('not consistent');
  });

  it('closes from the close button', () => {
    const { el, close } = render({ accepted: true, typeCount: 1, consistent: true, issues: [] });
    (el.querySelector('[data-action="close"]') as HTMLButtonElement).click();
    expect(close).toHaveBeenCalled();
  });
});
