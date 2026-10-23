/**
 * Contract under test ([AUT-GAPS], state-machine-view/gaps-note.ts): `AutomationGapsNote`
 * (`sq-automation-gaps-note`, input `sm`, default null) always says that timers show their duration only (no
 * countdown) and that no outbox or history view exists. When `sm` is given and has no automated transition it
 * adds `[data-hidden-hint]`: "This type shows no automated transitions; the server may be hiding them."
 */
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { StateMachineResponse } from '@sequeless/core-api';

import { AutomationGapsNote } from './gaps-note';

@Component({ imports: [AutomationGapsNote], template: `<sq-automation-gaps-note [sm]="sm()" />` })
class Host {
  sm = input<StateMachineResponse | null>(null);
}

function machine(trigger: 'USER_ACTION' | 'TIMER'): StateMachineResponse {
  return {
    iri: 'ex:SM',
    initialState: 'ex:A',
    states: [],
    transitions: [{ name: 't', from: 'ex:A', to: 'ex:B', hasGuard: false, trigger, after: null, signalName: null, watch: [] }],
  };
}

function render(sm: StateMachineResponse | null): HTMLElement {
  const fixture = TestBed.createComponent(Host);
  fixture.componentRef.setInput('sm', sm);
  fixture.detectChanges();
  return fixture.nativeElement;
}

describe('AutomationGapsNote', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('always states the known gaps', () => {
    const text = render(null).textContent ?? '';
    expect(text).toContain('no countdown');
    expect(text).toContain('outbox');
    expect(text).toContain('history');
  });

  it('adds the hidden-transitions hint when no transition is automated', () => {
    expect(render(machine('USER_ACTION')).querySelector('[data-hidden-hint]')?.textContent).toContain(
      'the server may be hiding them',
    );
  });

  it('omits the hint when something is automated, or no machine is given', () => {
    expect(render(machine('TIMER')).querySelector('[data-hidden-hint]')).toBeNull();
    expect(render(null).querySelector('[data-hidden-hint]')).toBeNull();
  });
});
