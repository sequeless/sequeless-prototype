/**
 * Contract under test ([AUT-DIAGRAM-KIT], state-machine-view/kit-state-machine-diagram.ts):
 * `SqKitStateMachineDiagram` (`sq-kit-state-machine-diagram`) takes `stateMachine` and `selectedTransition`
 * inputs and renders ui-kit's `sq-state-machine-graph` plus two lists, because the graph itself is not
 * clickable: a transition list (`[data-transition=name]`, its description from `describeTransition`, a
 * `[data-trigger]` kind badge, `aria-current` on the selected one) emitting `transitionSelect(name)`, and a
 * state list (`[data-state=iri]`, the state label) emitting `stateSelect(iri)`.
 */
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { StateMachineResponse } from '@sequeless/core-api';

import { SqKitStateMachineDiagram } from './kit-state-machine-diagram';

const MACHINE: StateMachineResponse = {
  iri: 'ex:SM',
  initialState: 'ex:A',
  states: [
    { iri: 'ex:A', name: 'A', label: 'Draft', displayOrder: 1 },
    { iri: 'ex:B', name: 'B', label: 'Done', displayOrder: 2 },
  ],
  transitions: [
    { name: 'finish', from: 'ex:A', to: 'ex:B', hasGuard: false, trigger: 'USER_ACTION', after: null, signalName: null, watch: [] },
    { name: 'expire', from: 'ex:A', to: 'ex:B', hasGuard: false, trigger: 'TIMER', after: 'PT72H', signalName: null, watch: [] },
  ],
};

@Component({
  imports: [SqKitStateMachineDiagram],
  template: `<sq-kit-state-machine-diagram
    [stateMachine]="machine"
    [selectedTransition]="selected()"
    (transitionSelect)="picked.push($event)"
    (stateSelect)="states.push($event)"
  />`,
})
class Host {
  machine = MACHINE;
  selected = signal<string | null>(null);
  picked: string[] = [];
  states: string[] = [];
}

function setup() {
  TestBed.configureTestingModule({ providers: [provideNoopAnimations()] });
  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement, cmp: fixture.componentInstance };
}

describe('SqKitStateMachineDiagram', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('renders the ui-kit graph', () => {
    expect(setup().host.querySelector('sq-state-machine-graph')).not.toBeNull();
  });

  it('lists each transition with its description and trigger kind', () => {
    const { host } = setup();
    const expire = host.querySelector('[data-transition="expire"]');
    expect(expire?.textContent).toContain('expire');
    expect(expire?.textContent).toContain('fires 72h after entering Draft');
    expect(expire?.querySelector('[data-trigger]')?.getAttribute('data-trigger')).toBe('TIMER');
    expect(host.querySelector('[data-transition="finish"]')?.textContent).toContain('fired by a user');
  });

  it('emits the clicked transition and state', () => {
    const { host, cmp } = setup();
    (host.querySelector('[data-transition="expire"]') as HTMLElement).click();
    (host.querySelector('[data-state="ex:B"]') as HTMLElement).click();
    expect(cmp.picked).toEqual(['expire']);
    expect(cmp.states).toEqual(['ex:B']);
  });

  it('marks the selected transition', () => {
    const { fixture, host, cmp } = setup();
    cmp.selected.set('finish');
    fixture.detectChanges();
    expect(host.querySelector('[data-transition="finish"]')?.getAttribute('aria-current')).toBe('true');
    expect(host.querySelector('[data-transition="expire"]')?.getAttribute('aria-current')).toBeNull();
  });

  it('shows state labels', () => {
    const { host } = setup();
    expect(host.querySelector('[data-state="ex:A"]')?.textContent).toContain('Draft');
  });
});
