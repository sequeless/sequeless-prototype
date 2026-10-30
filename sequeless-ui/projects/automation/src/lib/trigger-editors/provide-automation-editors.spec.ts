/**
 * Contract under test (trigger-editors/provide-automation-editors.ts): `provideAutomationEditors()` contributes
 * two panels to Phase 7's ONTOLOGY_EDITOR_PANELS — `automation.trigger` (SqTriggerEditor, slot
 * `transition-trigger`) and `automation.guard` (SqGuardEditor, slot `transition-guard`).
 */
import { TestBed } from '@angular/core/testing';
import { ONTOLOGY_EDITOR_PANELS, panelsFor } from '@sequeless/ontology';

import { SqGuardEditor } from './guard-editor';
import { provideAutomationEditors } from './provide-automation-editors';
import { SqTriggerEditor } from './trigger-editor';

describe('provideAutomationEditors', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('contributes the trigger and guard editors to their slots', () => {
    TestBed.configureTestingModule({ providers: [provideAutomationEditors()] });
    const all = TestBed.inject(ONTOLOGY_EDITOR_PANELS);
    expect(panelsFor(all, 'transition-trigger').map((p) => [p.id, p.component])).toEqual([
      ['automation.trigger', SqTriggerEditor],
    ]);
    expect(panelsFor(all, 'transition-guard').map((p) => [p.id, p.component])).toEqual([
      ['automation.guard', SqGuardEditor],
    ]);
    expect(panelsFor(all, 'type-extra')).toEqual([]);
  });
});
