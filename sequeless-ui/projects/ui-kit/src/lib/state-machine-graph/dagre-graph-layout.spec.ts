/**
 * Contract under test (lib/state-machine-graph/dagre-graph-layout.ts): `DagreGraphLayout`
 * implements the `GraphLayout` slot (phase-5 spec, "State-machine graph") by delegating to
 * `@dagrejs/dagre` — dynamically imported inside `layout()`, rank direction `'TB'` — to compute a
 * layered node/edge layout from a `StateMachineResponse`.
 *
 * One `GraphNode` per state: `id` is the state's `iri`, `label` its `label`, size fixed at 140x40,
 * `initial` true only for the state whose *name* matches `sm.initialState`. One `GraphEdge` per
 * transition: `id` is `${fromNodeId}->${toNodeId}#${name}` built from the *resolved node ids* —
 * `TransitionSummaryResponse.from`/`to` hold state *names* (confirmed against
 * `PROJECT_TYPE_DETAIL.stateMachine`, e.g. `from: 'Draft'`), not iris, so the layout must resolve
 * each through `states` before building node/edge ids. `trigger`/`hasGuard` are copied verbatim and
 * `label` is delegated to `edgeLabel` from `../util/edge-style`.
 *
 * Node `x`/`y` are dagre's native per-node centers (what `dagre.layout` writes back onto each node
 * after `g.setNode(id, { width, height })`), so a node's box is `[x - width/2, x + width/2] x
 * [y - height/2, y + height/2]`; the result's `width`/`height` are dagre's overall graph bounding
 * box, and every node box must fit inside `[0, width] x [0, height]` with no two boxes overlapping.
 * Top-to-bottom (`rankdir: 'TB'`) ranking puts the lowest rank — the initial state, which has no
 * incoming transition in this fixture — at the minimum `y`.
 */
import type { StateMachineResponse } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { edgeLabel } from '../util/edge-style';
import { DagreGraphLayout } from './dagre-graph-layout';

const stateMachine: StateMachineResponse = PROJECT_TYPE_DETAIL.stateMachine;

interface Box {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

function box(node: { x: number; y: number; width: number; height: number }): Box {
  return {
    minX: node.x - node.width / 2,
    maxX: node.x + node.width / 2,
    minY: node.y - node.height / 2,
    maxY: node.y + node.height / 2,
  };
}

function overlaps(a: Box, b: Box): boolean {
  return a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY;
}

function nodeIdByStateName(name: string): string {
  const state = stateMachine.states.find((s) => s.name === name);
  if (!state) {
    throw new Error(`fixture has no state named "${name}"`);
  }
  return state.iri;
}

describe('DagreGraphLayout', () => {
  describe('nodes', () => {
    it('lays out exactly one node per state, with iri id, label, and fixed 140x40 size', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);

      expect(result.nodes).toHaveLength(stateMachine.states.length);
      for (const state of stateMachine.states) {
        const node = result.nodes.find((n) => n.id === state.iri);
        expect(node).toBeDefined();
        expect(node!.label).toBe(state.label);
        expect(node!.width).toBe(140);
        expect(node!.height).toBe(40);
      }
    });

    it('marks only the initial state as initial', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);
      const initialId = nodeIdByStateName(stateMachine.initialState);

      for (const node of result.nodes) {
        expect(node.initial).toBe(node.id === initialId);
      }
      expect(result.nodes.filter((n) => n.initial)).toHaveLength(1);
    });
  });

  describe('edges', () => {
    it('lays out exactly one edge per transition, ids and from/to built from resolved node ids', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);

      expect(result.edges).toHaveLength(stateMachine.transitions.length);
      for (const t of stateMachine.transitions) {
        const fromId = nodeIdByStateName(t.from);
        const toId = nodeIdByStateName(t.to);
        const edge = result.edges.find((e) => e.id === `${fromId}->${toId}#${t.name}`);
        expect(edge).toBeDefined();
        expect(edge!.from).toBe(fromId);
        expect(edge!.to).toBe(toId);
      }
    });

    it('copies trigger and hasGuard verbatim and delegates the label to edgeLabel', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);

      for (const t of stateMachine.transitions) {
        const fromId = nodeIdByStateName(t.from);
        const toId = nodeIdByStateName(t.to);
        const edge = result.edges.find((e) => e.id === `${fromId}->${toId}#${t.name}`)!;
        expect(edge.trigger).toBe(t.trigger);
        expect(edge.hasGuard).toBe(t.hasGuard);
        expect(edge.label).toBe(edgeLabel(t));
      }
    });

    it('gives every edge a polyline of at least two points', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);

      expect(result.edges.length).toBeGreaterThan(0);
      for (const edge of result.edges) {
        expect(edge.points.length).toBeGreaterThanOrEqual(2);
      }
    });

    it('builds distinct ids for parallel transitions sharing the same from/to pair', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);
      const fromId = nodeIdByStateName('Active');
      const toId = nodeIdByStateName('Closed');

      expect(result.edges.find((e) => e.id === `${fromId}->${toId}#close`)).toBeDefined();
      expect(result.edges.find((e) => e.id === `${fromId}->${toId}#autoClose`)).toBeDefined();
    });
  });

  describe('overall geometry', () => {
    it('reports a positive overall width and height', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);
      expect(result.width).toBeGreaterThan(0);
      expect(result.height).toBeGreaterThan(0);
    });

    it('keeps every node box inside the [0, width] x [0, height] bounds', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);
      for (const node of result.nodes) {
        const b = box(node);
        expect(b.minX).toBeGreaterThanOrEqual(0);
        expect(b.maxX).toBeLessThanOrEqual(result.width);
        expect(b.minY).toBeGreaterThanOrEqual(0);
        expect(b.maxY).toBeLessThanOrEqual(result.height);
      }
    });

    it('never overlaps two node boxes', async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);
      for (let i = 0; i < result.nodes.length; i++) {
        for (let j = i + 1; j < result.nodes.length; j++) {
          expect(overlaps(box(result.nodes[i]), box(result.nodes[j]))).toBe(false);
        }
      }
    });

    it("places the initial state's y at the minimum of all node y values", async () => {
      const result = await new DagreGraphLayout().layout(stateMachine);
      const initialId = nodeIdByStateName(stateMachine.initialState);
      const initialNode = result.nodes.find((n) => n.id === initialId)!;
      const minY = Math.min(...result.nodes.map((n) => n.y));

      expect(initialNode.y).toBe(minY);
    });
  });

  describe('edge cases', () => {
    it('still lays out every state when there are no transitions', async () => {
      const machine: StateMachineResponse = {
        ...stateMachine,
        transitions: [],
      };
      const result = await new DagreGraphLayout().layout(machine);

      expect(result.nodes).toHaveLength(stateMachine.states.length);
      expect(result.edges).toEqual([]);
    });

    it('returns empty nodes and edges for a state machine with no states', async () => {
      const machine: StateMachineResponse = {
        ...stateMachine,
        states: [],
        initialState: '',
        transitions: [],
      };
      const result = await new DagreGraphLayout().layout(machine);

      expect(result.nodes).toEqual([]);
      expect(result.edges).toEqual([]);
    });
  });
});
