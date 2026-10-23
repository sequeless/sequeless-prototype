/*
 * Contract [ONT-SMEDITOR]: SqStateMachineEditor (`sq-state-machine-editor`, input `typeIri`) shows the type's
 * state machine as a graph and a transition list. Selecting a transition shows its trigger and guard; panels
 * contributed to ONTOLOGY_EDITOR_PANELS for 'transition-trigger' / 'transition-guard' are rendered with a
 * `context` input (session, registry, typeIri, the selected TransitionView). Without panels it shows the
 * trigger and guard read-only with a hint pointing to the raw Turtle tab. The selection survives edits that
 * re-parse the store, and a type without a state machine says so.
 */
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { GRAPH_LAYOUT, OntologyClient } from '@sequeless/core-api';
import type { StateMachineResponse } from '@sequeless/core-api';
import { of } from 'rxjs';

import { provideOntologyEditorPanel } from '../extension/editor-panels';
import type { OntologyEditorPanelContext } from '../extension/editor-panels';
import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';
import { EX, FIXTURE_TTL, LIFECYCLE, TASK } from '../testing/fixture';
import { SqStateMachineEditor, toStateMachineResponse } from './state-machine-editor';

@Component({ selector: 'sq-stub-panel', template: `<p class="stub">{{ context().transition?.name }}</p>` })
class StubPanel {
  readonly context = input.required<OntologyEditorPanelContext>();
}

@Component({ imports: [SqStateMachineEditor], template: `<sq-state-machine-editor [typeIri]="iri" />` })
class Host {
  iri = TASK;
}

const layoutCalls: StateMachineResponse[] = [];

async function setup(options: { panels?: boolean; iri?: string } = {}) {
  layoutCalls.length = 0;
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      OntologySession,
      ControlRegistry,
      { provide: OntologyClient, useValue: { load: () => of(FIXTURE_TTL) } },
      {
        provide: GRAPH_LAYOUT,
        useValue: { layout: (sm: StateMachineResponse) => (layoutCalls.push(sm), Promise.resolve({ width: 1, height: 1, nodes: [], edges: [] })) },
      },
      ...(options.panels
        ? [
            provideOntologyEditorPanel({ id: 't', slot: 'transition-trigger', component: StubPanel }),
            provideOntologyEditorPanel({ id: 'g', slot: 'transition-guard', component: StubPanel }),
          ]
        : []),
    ],
  });
  const session = TestBed.inject(OntologySession);
  await session.load();
  const fixture = TestBed.createComponent(Host);
  if (options.iri) fixture.componentInstance.iri = options.iri;
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const select = (name: string) => {
    (el.querySelector(`[data-transition="${name}"]`) as HTMLButtonElement).click();
    fixture.detectChanges();
  };
  return { fixture, el, session, select, registry: TestBed.inject(ControlRegistry) };
}

describe('toStateMachineResponse', () => {
  it('names states and transition endpoints by IRI', async () => {
    const { session } = await setup();
    const { readStateMachine } = await import('@sequeless/rdf');
    const sm = toStateMachineResponse(readStateMachine(session.store()!, TASK)!);
    expect(sm.iri).toBe(LIFECYCLE);
    expect(sm.initialState).toBe(`${EX}Open`);
    expect(sm.states.map((s) => [s.iri, s.name, s.label])).toEqual([
      [`${EX}Open`, `${EX}Open`, 'Open'],
      [`${EX}Done`, `${EX}Done`, 'Done'],
    ]);
    const complete = sm.transitions.find((t) => t.name === 'complete')!;
    expect(complete).toMatchObject({ from: `${EX}Open`, to: `${EX}Done`, hasGuard: true, trigger: 'USER_ACTION' });
    expect(sm.transitions.find((t) => t.name === 'expire')).toMatchObject({ trigger: 'TIMER', after: 'PT1H', hasGuard: false });
  });
});

describe('SqStateMachineEditor', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('lays out the state machine and lists its transitions', async () => {
    const { el } = await setup();
    await new Promise((r) => setTimeout(r));
    expect(layoutCalls).toHaveLength(1);
    expect(el.querySelector('sq-state-machine-graph')).not.toBeNull();
    expect([...el.querySelectorAll('[data-transition]')].map((b) => b.getAttribute('data-transition'))).toEqual([
      'complete',
      'expire',
    ]);
  });

  it('says so when the type has no state machine', async () => {
    const { el } = await setup({ iri: `${EX}Nothing` });
    expect(el.textContent).toContain('No state machine');
    expect(el.querySelector('sq-state-machine-graph')).toBeNull();
  });

  it('shows the trigger and guard read-only, with a raw Turtle hint, when no panels are registered', async () => {
    const { el, select } = await setup();
    select('complete');
    const detail = el.querySelector('.sq-transition-detail')!;
    expect(detail.textContent).toContain('User action');
    expect(detail.textContent).toContain('self.title != null');
    expect(detail.textContent).toContain('Needs a title');
    expect(detail.textContent).toContain('Turtle');
    select('expire');
    expect(el.querySelector('.sq-transition-detail')!.textContent).toContain('PT1H');
  });

  it('renders contributed panels with the transition in their context', async () => {
    const { el, select } = await setup({ panels: true });
    select('expire');
    const stubs = [...el.querySelectorAll('.stub')].map((s) => s.textContent);
    expect(stubs).toEqual(['expire', 'expire']);
    expect(el.querySelector('.sq-transition-detail')!.textContent).not.toContain('Turtle');
  });

  it('keeps the selection after an edit re-parses the store', async () => {
    const { el, select, session, fixture } = await setup();
    select('expire');
    session.applyText((await session.currentText()).replace('"Open"', '"Opened"'));
    fixture.detectChanges();
    expect(el.querySelector('.sq-transition-detail')!.textContent).toContain('PT1H');
  });
});
