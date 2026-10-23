/*
 * Contract [ONT-EXT]: ONTOLOGY_EDITOR_PANELS is a multi-provider token (default []); panelsFor filters
 * panels by slot and sorts them by `order` (missing order last) then `id`; provideOntologyEditorPanel
 * contributes one panel with multi: true.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import {
  ONTOLOGY_EDITOR_PANELS,
  panelsFor,
  provideOntologyEditorPanel,
} from './editor-panels';
import type { OntologyEditorPanel } from './editor-panels';

@Component({ selector: 'sq-stub', template: '' })
class Stub {}

function panel(id: string, slot: OntologyEditorPanel['slot'], order?: number): OntologyEditorPanel {
  return { id, slot, order, component: Stub };
}

describe('panelsFor', () => {
  it('keeps only the requested slot', () => {
    const all = [panel('a', 'transition-guard'), panel('b', 'type-extra')];
    expect(panelsFor(all, 'type-extra').map((p) => p.id)).toEqual(['b']);
  });

  it('sorts by order, then id, with unordered panels last', () => {
    const all = [
      panel('z', 'transition-trigger'),
      panel('b', 'transition-trigger', 2),
      panel('a', 'transition-trigger', 2),
      panel('c', 'transition-trigger', 1),
    ];
    expect(panelsFor(all, 'transition-trigger').map((p) => p.id)).toEqual(['c', 'a', 'b', 'z']);
  });

  it('does not mutate its input', () => {
    const all = [panel('b', 'type-extra'), panel('a', 'type-extra')];
    panelsFor(all, 'type-extra');
    expect(all.map((p) => p.id)).toEqual(['b', 'a']);
  });
});

describe('ONTOLOGY_EDITOR_PANELS', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('defaults to no panels', () => {
    expect(TestBed.inject(ONTOLOGY_EDITOR_PANELS)).toEqual([]);
  });

  it('collects panels contributed with provideOntologyEditorPanel', () => {
    const one = panel('one', 'transition-trigger');
    const two = panel('two', 'transition-guard');
    TestBed.configureTestingModule({
      providers: [provideOntologyEditorPanel(one), provideOntologyEditorPanel(two)],
    });
    expect(TestBed.inject(ONTOLOGY_EDITOR_PANELS)).toEqual([one, two]);
  });
});
