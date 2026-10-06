import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { GRAPH_LAYOUT } from '@sequeless/core-api';
import type { GraphLayoutResult, StateMachineResponse } from '@sequeless/core-api';
import { edgeStyle, pointsToPath } from '../util/edge-style';
import { DagreGraphLayout } from './dagre-graph-layout';

@Component({
  selector: 'sq-state-machine-graph',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (result(); as r) {
      <svg [attr.viewBox]="'0 0 ' + r.width + ' ' + r.height">
        <defs>
          <marker id="sq-state-machine-arrow" viewBox="0 0 10 10" refX="10" refY="5"
            markerWidth="8" markerHeight="8" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" />
          </marker>
        </defs>

        @for (e of r.edges; track e.id) {
          @let s = edgeStyle(e.trigger);
          <g [attr.data-edge]="e.id" [attr.data-trigger]="e.trigger">
            <path
              [attr.d]="pointsToPath(e.points)"
              [attr.stroke-dasharray]="s.dash"
              fill="none"
              stroke="currentColor"
            />
            @if (s.double) {
              <path class="sq-edge-double" [attr.d]="pointsToPath(e.points)" />
            }
            @if (s.glyph) {
              <text [attr.data-glyph]="s.glyph">{{ s.glyph === 'clock' ? '⏱' : '📡' }}</text>
            }
            @if (e.hasGuard) {
              <text data-glyph="lock">🔒</text>
            }
            <text class="sq-edge-label">{{ e.label }}</text>
          </g>
        }

        @for (n of r.nodes; track n.id) {
          <g [attr.data-state]="n.id" [class.sq-initial]="n.initial">
            <rect [attr.x]="n.x" [attr.y]="n.y" [attr.width]="n.width" [attr.height]="n.height" rx="8" />
            <text>{{ n.label }}</text>
          </g>
        }
      </svg>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    svg {
      width: 100%;
      height: auto;
      color: currentColor;
    }

    .sq-edge-double {
      fill: none;
      stroke: currentColor;
      stroke-dasharray: 2 4;
    }
  `,
})
export class SqStateMachineGraph {
  readonly stateMachine = input.required<StateMachineResponse>();

  readonly layout = inject(GRAPH_LAYOUT, { optional: true }) ?? new DagreGraphLayout();

  readonly result = signal<GraphLayoutResult | null>(null);

  protected readonly edgeStyle = edgeStyle;
  protected readonly pointsToPath = pointsToPath;

  constructor() {
    effect((onCleanup) => {
      const sm = this.stateMachine();
      let stale = false;
      this.layout.layout(sm).then(r => {
        if (!stale) {
          this.result.set(r);
        }
      });
      onCleanup(() => {
        stale = true;
      });
    });
  }
}
