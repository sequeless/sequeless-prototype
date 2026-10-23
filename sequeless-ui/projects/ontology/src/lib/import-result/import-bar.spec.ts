/*
 * Contract [ONT-IMPORT] (import-bar.ts): SqImportBar offers `data-action="undo" | "redo" | "import"`
 * buttons. Undo/redo are disabled when the session cannot undo/redo and call session.undo()/redo(); import
 * is disabled while the session is clean and runs ImportFlow.run(). After 'accepted' it opens the import
 * result dialog. ImportFlow.problems() are shown in an sq-problem-banner.
 */
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { OntologySession } from '../session/ontology-session';
import { ImportFlow } from './import-flow';
import { SqImportBar } from './import-bar';
import { SqImportResult } from './import-result';

const RESPONSE = { accepted: true, typeCount: 1, consistent: true, issues: [] };

function setup(result: 'accepted' | 'rejected' | 'cancelled' | 'blocked' = 'accepted') {
  const canUndo = signal(false);
  const canRedo = signal(false);
  const dirty = signal(false);
  const session = {
    canUndo,
    canRedo,
    dirty,
    lastImport: signal(RESPONSE),
    undo: vi.fn(() => true),
    redo: vi.fn(() => true),
  };
  const problems = signal<unknown>(null);
  const flow = { problems, run: vi.fn(() => Promise.resolve(result)) };
  const dialog = { open: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      { provide: OntologySession, useValue: session },
      { provide: ImportFlow, useValue: flow },
      { provide: MatDialog, useValue: dialog },
    ],
  });
  const fixture = TestBed.createComponent(SqImportBar);
  fixture.detectChanges();
  const button = (action: string) =>
    fixture.nativeElement.querySelector(`[data-action="${action}"]`) as HTMLButtonElement;
  return { fixture, session, flow, dialog, problems, button, canUndo, canRedo, dirty };
}

describe('SqImportBar', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('disables undo, redo and import on a clean session', () => {
    const { button } = setup();
    expect(button('undo').disabled).toBe(true);
    expect(button('redo').disabled).toBe(true);
    expect(button('import').disabled).toBe(true);
  });

  it('enables the buttons from the session state and delegates undo and redo', () => {
    const { fixture, button, canUndo, canRedo, dirty, session } = setup();
    canUndo.set(true);
    canRedo.set(true);
    dirty.set(true);
    fixture.detectChanges();
    expect(button('import').disabled).toBe(false);
    button('undo').click();
    button('redo').click();
    expect(session.undo).toHaveBeenCalled();
    expect(session.redo).toHaveBeenCalled();
  });

  it('runs the flow and opens the result dialog after an accepted import', async () => {
    const { fixture, button, dirty, flow, dialog } = setup('accepted');
    dirty.set(true);
    fixture.detectChanges();
    button('import').click();
    await fixture.whenStable();
    expect(flow.run).toHaveBeenCalled();
    expect(dialog.open).toHaveBeenCalledWith(SqImportResult, { data: RESPONSE });
  });

  it('does not open the result dialog when the import is rejected', async () => {
    const { fixture, button, dirty, dialog } = setup('rejected');
    dirty.set(true);
    fixture.detectChanges();
    button('import').click();
    await fixture.whenStable();
    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('shows the flow problems in a banner', () => {
    const { fixture, problems } = setup();
    problems.set([{ severity: 'error', subjectIri: null, message: 'Unexpected token' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('sq-problem-banner')?.textContent).toContain('Unexpected token');
  });
});
