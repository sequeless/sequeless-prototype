/**
 * `DagreGraphLayout` implements the `GraphLayout` slot (phase-5 spec, "State-machine graph") by
 * delegating to `@dagrejs/dagre` — dynamically imported inside `layout()` so it stays out of the
 * main bundle — to compute a layered node/edge layout from a `StateMachineResponse`.
 *
 * One `GraphNode` per state: `id` is the state's `iri`, `label` its `label`, fixed 140x40 size,
 * `initial` true only for the state whose *name* matches `sm.initialState`. One `GraphEdge` per
 * transition: `id` is `${from}->${to}#${name}` built from resolved node ids — `TransitionSummary
 * Response.from`/`to` hold state names, not iris, so each is resolved through `states` first.
 * `trigger`/`hasGuard` are copied verbatim and `label` is delegated to `edgeLabel`.
 *
 * All dagre value-level typing (the `any`s) is confined to this file; the public surface only
 * exposes the `GraphNode`/`GraphEdge`/`GraphLayoutResult` contract from `@sequeless/core-api`.
 */
import type {
  GraphLayout,
  GraphLayoutResult,
  StateMachineResponse,
  StateResponse,
  TransitionSummaryResponse,
} from '@sequeless/core-api';
import { edgeLabel } from '../util/edge-style';

const NODE_WIDTH = 140;
const NODE_HEIGHT = 40;

export class DagreGraphLayout implements GraphLayout {
  async layout(stateMachine: StateMachineResponse): Promise<GraphLayoutResult> {
    if (stateMachine.states.length === 0) {
      return { width: 0, height: 0, nodes: [], edges: [] };
    }

    const dagre = (await import('@dagrejs/dagre')).default ?? await import('@dagrejs/dagre');
    const g: any = new dagre.graphlib.Graph({ multigraph: true });
    g.setGraph({ rankdir: 'TB', nodesep: 40, ranksep: 60, marginx: 20, marginy: 20 });
    g.setDefaultEdgeLabel(() => ({}));

    const iriByName = new Map<string, StateResponse>();
    for (const state of stateMachine.states) {
      g.setNode(state.iri, { width: NODE_WIDTH, height: NODE_HEIGHT });
      iriByName.set(state.name, state);
    }

    for (const transition of stateMachine.transitions) {
      const from = iriByName.get(transition.from)?.iri ?? transition.from;
      const to = iriByName.get(transition.to)?.iri ?? transition.to;
      g.setEdge(from, to, {}, transition.name);
    }

    dagre.layout(g);

    const nodes = stateMachine.states.map((state) => {
      const placed: any = g.node(state.iri);
      return {
        id: state.iri,
        label: state.label,
        x: placed.x,
        y: placed.y,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
        initial: state.name === stateMachine.initialState,
      };
    });

    const edges = stateMachine.transitions.map((transition: TransitionSummaryResponse) => {
      const from = iriByName.get(transition.from)?.iri ?? transition.from;
      const to = iriByName.get(transition.to)?.iri ?? transition.to;
      const placed: any = g.edge(from, to, transition.name);
      const points = (placed?.points ?? []).map((p: any) => ({ x: p.x, y: p.y }));
      return {
        id: `${from}->${to}#${transition.name}`,
        from,
        to,
        points,
        trigger: transition.trigger,
        hasGuard: transition.hasGuard,
        label: edgeLabel(transition),
      };
    });

    return {
      width: g.graph().width,
      height: g.graph().height,
      nodes,
      edges,
    };
  }
}
