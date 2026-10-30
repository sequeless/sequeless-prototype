/**
 * Contract under test ([AUT-DIAGRAM], state-machine-view/state-machine-diagram.ts): `STATE_MACHINE_DIAGRAM` is
 * a root-provided token holding the component that draws a state machine; its default is
 * `SqKitStateMachineDiagram`. `provideStateMachineDiagram(Other)` replaces it.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { SqKitStateMachineDiagram } from './kit-state-machine-diagram';
import { STATE_MACHINE_DIAGRAM, provideStateMachineDiagram } from './state-machine-diagram';

@Component({ selector: 'sq-other-diagram', template: '' })
class OtherDiagram {}

describe('STATE_MACHINE_DIAGRAM', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('defaults to the ui-kit adapter', () => {
    expect(TestBed.inject(STATE_MACHINE_DIAGRAM)).toBe(SqKitStateMachineDiagram);
  });

  it('can be replaced', () => {
    TestBed.configureTestingModule({ providers: [provideStateMachineDiagram(OtherDiagram)] });
    expect(TestBed.inject(STATE_MACHINE_DIAGRAM)).toBe(OtherDiagram);
  });
});
