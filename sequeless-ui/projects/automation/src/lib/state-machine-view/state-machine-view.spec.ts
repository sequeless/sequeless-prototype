/**
 * Contract [AUT-VIEW]: SqStateMachineView (`sq-state-machine-view`) shows one type's whole state machine. The
 * type comes from the optional `type` input or else the route's `:type` parameter. It calls
 * TypesClient.describe(type) and never ObjectsClient.transitions (that endpoint lists callable actions only).
 * It hosts the component bound to STATE_MACHINE_DIAGRAM, passing `stateMachine` and `selectedTransition`:
 * - a type without a state machine shows "no state machine" and no diagram;
 * - a diagram `transitionSelect(name)` opens `[data-transition-panel]` with the transition's description and
 *   an "Edit in ontology" link to `/ontology/{type}`;
 * - a diagram `stateSelect(iri)` navigates to Phase 6 browse filtered by that state (`stateBrowseLink`);
 * - an ApiError shows a problem banner; `sq-automation-gaps-note` is always shown.
 */
import { Component, input, output } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import type { StateMachineResponse, TypeDetailResponse } from '@sequeless/core-api';
import { of, throwError } from 'rxjs';

import { STATE_MACHINE_DIAGRAM, provideStateMachineDiagram } from './state-machine-diagram';
import { SqStateMachineView } from './state-machine-view';

@Component({
  selector: 'sq-stub-diagram',
  template: `<span data-stub [attr.data-selected]="selectedTransition()">{{ stateMachine().transitions.length }}</span>`,
})
class StubDiagram {
  readonly stateMachine = input.required<StateMachineResponse>();
  readonly selectedTransition = input<string | null>(null);
  readonly transitionSelect = output<string>();
  readonly stateSelect = output<string>();
}

const MACHINE: StateMachineResponse = {
  iri: 'ex:SM',
  initialState: 'ex:A',
  states: [
    { iri: 'ex:A', name: 'Draft', label: 'Draft', displayOrder: 1 },
    { iri: 'ex:B', name: 'Done', label: 'Done', displayOrder: 2 },
  ],
  transitions: [
    { name: 'expire', from: 'ex:A', to: 'ex:B', hasGuard: false, trigger: 'TIMER', after: 'PT72H', signalName: null, watch: [] },
  ],
};

function detail(stateMachine: StateMachineResponse | null): TypeDetailResponse {
  return { iri: 'ex:Order', name: 'Order', label: 'Order', abstract: false, superTypes: [], group: null, hidden: false, properties: [], stateMachine };
}

async function setup(describe: () => ReturnType<TypesClient['describe']>) {
  const objects = { transitions: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideNoopAnimations(),
      provideStateMachineDiagram(StubDiagram),
      { provide: TypesClient, useValue: { describe: vi.fn(describe) } },
      { provide: ObjectsClient, useValue: objects },
    ],
  });
  const fixture = TestBed.createComponent(SqStateMachineView);
  fixture.componentRef.setInput('type', 'Order');
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  const diagram = fixture.debugElement.query((d) => d.componentInstance instanceof StubDiagram)
    ?.componentInstance as StubDiagram | undefined;
  return { fixture, host: fixture.nativeElement as HTMLElement, diagram, objects, router: TestBed.inject(Router) };
}

describe('SqStateMachineView', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('defaults the diagram to the ui-kit adapter', () => {
    TestBed.configureTestingModule({});
    expect(TestBed.inject(STATE_MACHINE_DIAGRAM)).toBeDefined();
  });

  it('renders the bound diagram with the type machine and never asks for per-object transitions', async () => {
    const { host, diagram, objects } = await setup(() => of(detail(MACHINE)));
    expect(host.querySelector('[data-stub]')?.textContent).toBe('1');
    expect(diagram).toBeDefined();
    expect(objects.transitions).not.toHaveBeenCalled();
  });

  it('says so when the type has no state machine', async () => {
    const { host, diagram } = await setup(() => of(detail(null)));
    expect(host.textContent).toContain('no state machine');
    expect(diagram).toBeUndefined();
  });

  it('opens a panel for the selected transition with a link to the ontology editor', async () => {
    const { fixture, host, diagram } = await setup(() => of(detail(MACHINE)));
    diagram!.transitionSelect.emit('expire');
    fixture.detectChanges();
    const panel = host.querySelector('[data-transition-panel]');
    expect(panel?.textContent).toContain('expire');
    expect(panel?.textContent).toContain('fires 72h after entering Draft');
    expect(panel?.querySelector('a')?.getAttribute('href')).toBe('/ontology/Order');
    expect(host.querySelector('[data-stub]')?.getAttribute('data-selected')).toBe('expire');
  });

  it('navigates to browse filtered by the selected state', async () => {
    const { diagram, router } = await setup(() => of(detail(MACHINE)));
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    diagram!.stateSelect.emit('ex:B');
    expect(navigate).toHaveBeenCalledWith(['/objects', 'Order'], { queryParams: { 'filter[state][eq]': 'Done' } });
  });

  it('shows a problem banner on failure and always the gaps note', async () => {
    const { host } = await setup(() => throwError(() => ({ status: 500, title: 'boom', violations: [], issues: [] })));
    expect(host.querySelector('sq-problem-banner')).not.toBeNull();
    expect(host.querySelector('sq-automation-gaps-note')).not.toBeNull();
  });
});
