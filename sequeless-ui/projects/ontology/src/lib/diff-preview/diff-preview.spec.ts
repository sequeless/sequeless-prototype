/*
 * Contract [ONT-PREVIEW]: SqDiffPreview (`diff = input.required<OntologyDiff>()`) renders one
 * `mat-expansion-panel[data-subject]` per changed subject listing its removed and added lines, plus an
 * `sq-text-diff` of baseText vs workingText; with no changed subjects it says there are no changes.
 * openDiffPreview(dialog, diff) opens SqDiffPreviewDialog and resolves true when the user picks Import,
 * false on Cancel or dismissal.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import type { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { firstValueFrom, of } from 'rxjs';

import type { OntologyDiff } from './ontology-diff';
import { SqDiffPreview } from './diff-preview';
import { openDiffPreview, SqDiffPreviewDialog } from './diff-preview-dialog';
import { TEXT_DIFF_VIEW } from './text-diff-view';

const DIFF: OntologyDiff = {
  subjects: [
    { subject: 'http://ex.org/A', added: ['<a> <p> "Alpha" .'], removed: ['<a> <p> "A" .'] },
    { subject: 'http://ex.org/B', added: ['<b> <p> "B" .'], removed: [] },
  ],
  baseText: 'base',
  workingText: 'working',
};

const noopView = () => ({ update: () => undefined, dispose: () => undefined });

@Component({ imports: [SqDiffPreview], template: `<sq-diff-preview [diff]="diff" />` })
class Host {
  diff: OntologyDiff = DIFF;
}

function providers() {
  return [provideNoopAnimations(), { provide: TEXT_DIFF_VIEW, useValue: noopView }];
}

describe('SqDiffPreview', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('renders one panel per changed subject with its lines', () => {
    TestBed.configureTestingModule({ providers: providers() });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const panels = el.querySelectorAll('mat-expansion-panel[data-subject]');
    expect([...panels].map((p) => p.getAttribute('data-subject'))).toEqual([
      'http://ex.org/A',
      'http://ex.org/B',
    ]);
    expect(panels[0].querySelector('.sq-removed')?.textContent).toContain('"A"');
    expect(panels[0].querySelector('.sq-added')?.textContent).toContain('"Alpha"');
    expect(panels[1].querySelector('.sq-removed')).toBeNull();
  });

  it('includes the text diff', () => {
    TestBed.configureTestingModule({ providers: providers() });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('sq-text-diff')).not.toBeNull();
  });

  it('says so when nothing changed', () => {
    TestBed.configureTestingModule({ providers: providers() });
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.diff = { subjects: [], baseText: 'x', workingText: 'x' };
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No changes');
    expect(fixture.nativeElement.querySelector('mat-expansion-panel')).toBeNull();
  });
});

describe('SqDiffPreviewDialog', () => {
  afterEach(() => TestBed.resetTestingModule());

  function open() {
    const close = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        ...providers(),
        { provide: MAT_DIALOG_DATA, useValue: DIFF },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    const fixture = TestBed.createComponent(SqDiffPreviewDialog);
    fixture.detectChanges();
    return { fixture, close };
  }

  it('closes with true on Import', () => {
    const { fixture, close } = open();
    fixture.nativeElement.querySelector('[data-action="import"]').click();
    expect(close).toHaveBeenCalledWith(true);
  });

  it('closes with false on Cancel', () => {
    const { fixture, close } = open();
    fixture.nativeElement.querySelector('[data-action="cancel"]').click();
    expect(close).toHaveBeenCalledWith(false);
  });
});

describe('openDiffPreview', () => {
  it('opens the dialog with the diff and maps dismissal to false', async () => {
    const open = vi.fn().mockReturnValue({ afterClosed: () => of(undefined) });
    const result = await firstValueFrom(openDiffPreview({ open } as unknown as MatDialog, DIFF));
    expect(result).toBe(false);
    expect(open).toHaveBeenCalledWith(SqDiffPreviewDialog, expect.objectContaining({ data: DIFF }));
  });

  it('resolves true when the dialog closes with true', async () => {
    const open = vi.fn().mockReturnValue({ afterClosed: () => of(true) });
    expect(await firstValueFrom(openDiffPreview({ open } as unknown as MatDialog, DIFF))).toBe(true);
  });
});
