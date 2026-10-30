/**
 * Contract under test (lib/state-machine-graph/state-machine-graph.ts): `SqStateMachineGraph`
 * (selector `sq-state-machine-graph`) takes `stateMachine = input.required<StateMachineResponse>()`
 * and renders it as plain SVG, delegating layout to the `GRAPH_LAYOUT` token
 * (`inject(GRAPH_LAYOUT, { optional: true }) ?? new DagreGraphLayout()`), holding the resolved
 * `GraphLayoutResult` in a signal and re-running `layout()` whenever the `stateMachine` input
 * changes (phase-5 spec, "State-machine graph").
 *
 * The root `<svg [attr.viewBox]="'0 0 ' + width + ' ' + height">` contains one
 * `<g data-state="{{id}}">` per `GraphNode` — a `<rect>` plus `<text>{{label}}</text>`, tagged
 * `sq-initial` only on the initial node — and one `<g data-edge="{{id}}" [attr.data-trigger]="trigger">`
 * per `GraphEdge`: a `<path [attr.d]="pointsToPath(points)" [attr.stroke-dasharray]="edgeStyle(trigger).dash">`,
 * a second `<path class="sq-edge-double">` only when `edgeStyle(trigger).double` (EXTERNAL_SIGNAL),
 * a `<text data-glyph="clock"|"antenna">` for the trigger's glyph, an additional
 * `<text data-glyph="lock">` when `hasGuard`, and a `<text class="sq-edge-label">{{label}}</text>`.
 * `pointsToPath`/`edgeStyle` come from `../util/edge-style` and are pinned by its own spec.
 *
 * This spec pins the component's rendering against a fake `GRAPH_LAYOUT` provider — never the real
 * `DagreGraphLayout` (see dagre-graph-layout.spec.ts for that contract) — with one fixed
 * `GraphLayoutResult` covering all four `TriggerKind`s plus one additionally guarded edge.
 */
import { TestBed } from '@angular/core/testing';
import { GRAPH_LAYOUT } from '@sequeless/core-api';
import type { GraphLayout, GraphLayoutResult, StateMachineResponse } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { SqStateMachineGraph } from './state-machine-graph';

const STATE_MACHINE: StateMachineResponse = PROJECT_TYPE_DETAIL.stateMachine as StateMachineResponse;

const RESULT: GraphLayoutResult = {
  width: 400,
  height: 300,
  nodes: [
    { id: 'n-draft', label: 'Draft', x: 70, y: 20, width: 140, height: 40, initial: true },
    { id: 'n-active', label: 'Active', x: 70, y: 120, width: 140, height: 40, initial: false },
  ],
  edges: [
    {
      id: 'e-user',
      from: 'n-draft',
      to: 'n-active',
      trigger: 'USER_ACTION',
      hasGuard: false,
      label: 'activate',
      points: [{ x: 70, y: 40 }, { x: 70, y: 100 }],
    },
    {
      id: 'e-onchange',
      from: 'n-active',
      to: 'n-draft',
      trigger: 'ON_CHANGE',
      hasGuard: false,
      label: 'autoClose (belongsToProject)',
      points: [{ x: 60, y: 100 }, { x: 60, y: 40 }],
    },
    {
      id: 'e-timer',
      from: 'n-active',
      to: 'n-draft',
      trigger: 'TIMER',
      hasGuard: false,
      label: 'expireHold (PT72H)',
      points: [{ x: 50, y: 100 }, { x: 50, y: 40 }],
    },
    {
      id: 'e-signal',
      from: 'n-draft',
      to: 'n-active',
      trigger: 'EXTERNAL_SIGNAL',
      hasGuard: false,
      label: 'paid (paymentReceived)',
      points: [{ x: 80, y: 40 }, { x: 80, y: 100 }],
    },
    {
      id: 'e-guarded',
      from: 'n-draft',
      to: 'n-active',
      trigger: 'USER_ACTION',
      hasGuard: true,
      label: 'approve',
      points: [{ x: 90, y: 40 }, { x: 90, y: 100 }],
    },
  ],
};

function createFixture(layout: GraphLayout, stateMachine: StateMachineResponse = STATE_MACHINE) {
  TestBed.configureTestingModule({
    providers: [{ provide: GRAPH_LAYOUT, useValue: layout }],
  });
  const fixture = TestBed.createComponent(SqStateMachineGraph);
  fixture.componentRef.setInput('stateMachine', stateMachine);
  return fixture;
}

function stateGroup(fixture: { nativeElement: HTMLElement }, id: string): HTMLElement {
  const el = fixture.nativeElement.querySelector<HTMLElement>(`g[data-state="${id}"]`);
  if (!el) {
    throw new Error(`no state group found for data-state="${id}"`);
  }
  return el;
}

function edgeGroup(fixture: { nativeElement: HTMLElement }, id: string): HTMLElement {
  const el = fixture.nativeElement.querySelector<HTMLElement>(`g[data-edge="${id}"]`);
  if (!el) {
    throw new Error(`no edge group found for data-edge="${id}"`);
  }
  return el;
}

describe('SqStateMachineGraph', () => {
  let layoutFn: ReturnType<typeof vi.fn<(stateMachine: StateMachineResponse) => Promise<GraphLayoutResult>>>;
  let layout: GraphLayout;

  beforeEach(() => {
    layoutFn = vi.fn<(stateMachine: StateMachineResponse) => Promise<GraphLayoutResult>>().mockResolvedValue(RESULT);
    layout = { layout: layoutFn };
  });

  it('calls the injected GRAPH_LAYOUT once with the stateMachine input', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(layoutFn).toHaveBeenCalledTimes(1);
    expect(layoutFn).toHaveBeenCalledWith(STATE_MACHINE);
  });

  it('sets the svg viewBox from the resolved layout width/height', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    const svg = fixture.nativeElement.querySelector('svg');
    expect(svg?.getAttribute('viewBox')).toBe('0 0 400 300');
  });

  it('renders exactly one <g data-state> per node', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('g[data-state]')).toHaveLength(2);
  });

  it('renders exactly one <g data-edge> per edge', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('g[data-edge]')).toHaveLength(5);
  });

  it('marks only the initial node with the sq-initial class', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(stateGroup(fixture, 'n-draft').classList.contains('sq-initial')).toBe(true);
    expect(stateGroup(fixture, 'n-active').classList.contains('sq-initial')).toBe(false);
  });

  it('renders each node as a rect plus a text label matching GraphNode.label', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    const group = stateGroup(fixture, 'n-active');
    expect(group.querySelector('rect')).not.toBeNull();
    expect(group.querySelector('text')?.textContent).toBe('Active');
  });

  it('tags each edge group with its trigger via data-trigger', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(edgeGroup(fixture, 'e-user').getAttribute('data-trigger')).toBe('USER_ACTION');
    expect(edgeGroup(fixture, 'e-onchange').getAttribute('data-trigger')).toBe('ON_CHANGE');
    expect(edgeGroup(fixture, 'e-timer').getAttribute('data-trigger')).toBe('TIMER');
    expect(edgeGroup(fixture, 'e-signal').getAttribute('data-trigger')).toBe('EXTERNAL_SIGNAL');
  });

  it('renders the edge path d via pointsToPath(points)', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    const path = edgeGroup(fixture, 'e-user').querySelector('path');
    expect(path?.getAttribute('d')).toBe('M 70 40 L 70 100');
  });

  it('applies no stroke-dasharray for the USER_ACTION and EXTERNAL_SIGNAL edges', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(
      edgeGroup(fixture, 'e-user').querySelector('path')?.getAttribute('stroke-dasharray'),
    ).toBeNull();
    expect(
      edgeGroup(fixture, 'e-signal').querySelector('path')?.getAttribute('stroke-dasharray'),
    ).toBeNull();
  });

  it('applies the dashed dasharray for ON_CHANGE and the dotted dasharray for TIMER', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(
      edgeGroup(fixture, 'e-onchange').querySelector('path')?.getAttribute('stroke-dasharray'),
    ).toBe('6 4');
    expect(
      edgeGroup(fixture, 'e-timer').querySelector('path')?.getAttribute('stroke-dasharray'),
    ).toBe('2 4');
  });

  it('renders a second sq-edge-double path only for the EXTERNAL_SIGNAL edge', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(edgeGroup(fixture, 'e-signal').querySelector('path.sq-edge-double')).not.toBeNull();
    expect(edgeGroup(fixture, 'e-user').querySelector('path.sq-edge-double')).toBeNull();
    expect(edgeGroup(fixture, 'e-onchange').querySelector('path.sq-edge-double')).toBeNull();
    expect(edgeGroup(fixture, 'e-timer').querySelector('path.sq-edge-double')).toBeNull();
  });

  it('renders the clock glyph for TIMER and the antenna glyph for EXTERNAL_SIGNAL', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(edgeGroup(fixture, 'e-timer').querySelector('text[data-glyph="clock"]')).not.toBeNull();
    expect(
      edgeGroup(fixture, 'e-signal').querySelector('text[data-glyph="antenna"]'),
    ).not.toBeNull();
  });

  it('renders no clock/antenna glyph for the USER_ACTION or ON_CHANGE edges', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    const noGlyph = 'text[data-glyph="clock"], text[data-glyph="antenna"]';
    expect(edgeGroup(fixture, 'e-user').querySelector(noGlyph)).toBeNull();
    expect(edgeGroup(fixture, 'e-onchange').querySelector(noGlyph)).toBeNull();
  });

  it('renders a lock glyph only on the guarded edge', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(edgeGroup(fixture, 'e-guarded').querySelector('text[data-glyph="lock"]')).not.toBeNull();
    expect(edgeGroup(fixture, 'e-user').querySelector('text[data-glyph="lock"]')).toBeNull();
    expect(edgeGroup(fixture, 'e-signal').querySelector('text[data-glyph="lock"]')).toBeNull();
  });

  it('renders the edge label text from GraphEdge.label', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    expect(edgeGroup(fixture, 'e-timer').querySelector('text.sq-edge-label')?.textContent).toBe(
      'expireHold (PT72H)',
    );
    expect(edgeGroup(fixture, 'e-guarded').querySelector('text.sq-edge-label')?.textContent).toBe(
      'approve',
    );
  });

  it('re-runs layout() with the new stateMachine when the input changes', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();
    layoutFn.mockClear();

    const otherMachine: StateMachineResponse = { ...STATE_MACHINE, initialState: 'Active' };
    fixture.componentRef.setInput('stateMachine', otherMachine);
    await fixture.whenStable();

    expect(layoutFn).toHaveBeenCalledTimes(1);
    expect(layoutFn).toHaveBeenCalledWith(otherMachine);
  });

  it('re-renders the svg from the newly resolved layout after an input change', async () => {
    const fixture = createFixture(layout);
    await fixture.whenStable();

    const smallerResult: GraphLayoutResult = {
      width: 100,
      height: 80,
      nodes: [{ id: 'n-solo', label: 'Solo', x: 50, y: 40, width: 140, height: 40, initial: true }],
      edges: [],
    };
    layoutFn.mockResolvedValue(smallerResult);
    fixture.componentRef.setInput('stateMachine', { ...STATE_MACHINE, initialState: 'Active' });
    await fixture.whenStable();

    const svg = fixture.nativeElement.querySelector('svg');
    expect(svg?.getAttribute('viewBox')).toBe('0 0 100 80');
    expect(fixture.nativeElement.querySelectorAll('g[data-state]')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('g[data-edge]')).toHaveLength(0);
  });
});
