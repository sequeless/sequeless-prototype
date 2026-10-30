import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import type { StateMachineResponse } from '@sequeless/core-api';
import { SqStateMachineGraph } from '@sequeless/ui-kit';

import { describeTransition } from './transition-format';

/**
 * The default diagram: ui-kit's graph, which cannot be clicked, plus a transition list and a state list
 * that carry the selections (D2).
 */
@Component({
  selector: 'sq-kit-state-machine-diagram',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatListModule, SqStateMachineGraph],
  template: `
    <sq-state-machine-graph [stateMachine]="stateMachine()" />
    <h3>Transitions</h3>
    <mat-nav-list aria-label="Transitions">
      @for (t of stateMachine().transitions; track t.name) {
        <a
          mat-list-item
          href="#"
          [attr.data-transition]="t.name"
          [attr.aria-current]="t.name === selectedTransition() ? 'true' : null"
          (click)="$event.preventDefault(); transitionSelect.emit(t.name)"
        >
          <span matListItemTitle>
            {{ t.name }} <small [attr.data-trigger]="t.trigger">{{ t.trigger }}</small>
          </span>
          <span matListItemLine>{{ describe(t) }}</span>
        </a>
      }
    </mat-nav-list>
    <h3>States</h3>
    <mat-nav-list aria-label="States">
      @for (s of stateMachine().states; track s.iri) {
        <a mat-list-item href="#" [attr.data-state]="s.iri" (click)="$event.preventDefault(); stateSelect.emit(s.iri)">
          {{ s.label }}
        </a>
      }
    </mat-nav-list>
  `,
})
export class SqKitStateMachineDiagram {
  readonly stateMachine = input.required<StateMachineResponse>();
  readonly selectedTransition = input<string | null>(null);
  readonly transitionSelect = output<string>();
  readonly stateSelect = output<string>();

  private readonly labels = computed(
    () => new Map(this.stateMachine().states.map((s) => [s.iri, s.label])),
  );

  protected describe(t: StateMachineResponse['transitions'][number]): string {
    return describeTransition(t, (iri) => this.labels().get(iri) ?? iri);
  }
}
