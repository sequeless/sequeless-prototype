import { NgComponentOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import type { StateMachineResponse, TriggerKind } from '@sequeless/core-api';
import { readStateMachine } from '@sequeless/rdf';
import type { StateMachineView, TransitionView, TriggerView } from '@sequeless/rdf';
import { SqStateMachineGraph } from '@sequeless/ui-kit';

import { ONTOLOGY_EDITOR_PANELS, panelsFor } from '../extension/editor-panels';
import type { OntologyEditorPanelContext } from '../extension/editor-panels';
import { ControlRegistry } from '../issues/control-registry';
import { OntologySession } from '../session/ontology-session';

/** Adapts the rdf projection to what the shared graph renders. States and endpoints are named by IRI. */
export function toStateMachineResponse(view: StateMachineView): StateMachineResponse {
  return {
    iri: view.iri,
    initialState: view.initialStateIri ?? '',
    states: view.states.map((s, i) => ({ iri: s.iri, name: s.iri, label: s.label ?? s.iri, displayOrder: s.order ?? i })),
    transitions: view.transitions.map((t) => ({
      name: t.name ?? '',
      from: t.fromIri,
      to: t.toIri,
      hasGuard: t.guard !== null,
      trigger: t.trigger.kind as TriggerKind,
      after: t.trigger.kind === 'TIMER' ? t.trigger.after : null,
      signalName: t.trigger.kind === 'EXTERNAL_SIGNAL' ? t.trigger.signalName : null,
      watch: t.trigger.kind === 'ON_CHANGE' ? t.trigger.watchIris : [],
    })),
  };
}

function triggerText(trigger: TriggerView): string {
  switch (trigger.kind) {
    case 'USER_ACTION':
      return 'User action';
    case 'ON_CHANGE':
      return `On change of ${trigger.watchIris.join(', ') || 'any property'}`;
    case 'TIMER':
      return `Timer after ${trigger.after}`;
    case 'EXTERNAL_SIGNAL':
      return `External signal "${trigger.signalName}"`;
  }
}

interface Selection {
  name: string | null;
  fromIri: string;
  toIri: string;
  guard: string | null;
}

/**
 * The state machine of one type: graph, transition list, and, for the selected transition, the trigger and
 * guard editors other features contribute (or a read-only fallback). The selection is kept as a key and
 * re-resolved on every revision, because transitions are blank nodes whose labels change on re-parse.
 */
@Component({
  selector: 'sq-state-machine-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgComponentOutlet, MatButtonModule, SqStateMachineGraph],
  template: `
    @if (view(); as view) {
      <sq-state-machine-graph [stateMachine]="response()!" />
      <ul class="sq-transition-list">
        @for (t of view.transitions; track $index) {
          <li>
            <button
              mat-button
              [attr.data-transition]="t.name"
              [class.sq-selected]="t === selected()"
              (click)="select(t)"
            >
              {{ t.name }}: {{ t.fromIri }} → {{ t.toIri }}
            </button>
          </li>
        }
      </ul>
      @if (selected(); as t) {
        <section class="sq-transition-detail">
          <h3>{{ t.name }}</h3>
          @for (panel of triggerPanels(); track panel.id) {
            <ng-container *ngComponentOutlet="panel.component; inputs: { context: context() }" />
          }
          @for (panel of guardPanels(); track panel.id) {
            <ng-container *ngComponentOutlet="panel.component; inputs: { context: context() }" />
          }
          @if (triggerPanels().length === 0) {
            <p>Trigger: {{ triggerText(t.trigger) }}</p>
          }
          @if (guardPanels().length === 0) {
            <p>
              Guard: {{ t.guard ?? 'none' }}
              @if (t.guardMessage) {
                ({{ t.guardMessage }})
              }
            </p>
          }
          @if (triggerPanels().length === 0 || guardPanels().length === 0) {
            <p class="sq-hint">Edit triggers and guards in the Turtle tab.</p>
          }
        </section>
      }
    } @else {
      <p>No state machine for this type.</p>
    }
  `,
})
export class SqStateMachineEditor {
  readonly typeIri = input.required<string>();

  private readonly session = inject(OntologySession);
  private readonly registry = inject(ControlRegistry);
  private readonly panels = inject(ONTOLOGY_EDITOR_PANELS);
  private readonly selection = signal<Selection | null>(null);

  protected readonly triggerText = triggerText;
  protected readonly triggerPanels = computed(() => panelsFor(this.panels, 'transition-trigger'));
  protected readonly guardPanels = computed(() => panelsFor(this.panels, 'transition-guard'));

  protected readonly view = computed(() => {
    this.session.revision();
    const store = this.session.store();
    return store ? readStateMachine(store, this.typeIri()) : null;
  });

  protected readonly response = computed(() => {
    const view = this.view();
    return view ? toStateMachineResponse(view) : null;
  });

  protected readonly selected = computed<TransitionView | null>(() => {
    const key = this.selection();
    const view = this.view();
    if (!key || !view) {
      return null;
    }
    const matches = view.transitions.filter(
      (t) => t.name === key.name && t.fromIri === key.fromIri && t.toIri === key.toIri,
    );
    return matches.find((t) => t.guard === key.guard) ?? matches[0] ?? null;
  });

  protected readonly context = computed<OntologyEditorPanelContext>(() => {
    const transition = this.selected() ?? undefined;
    return {
      typeIri: this.typeIri(),
      session: this.session,
      registry: this.registry,
      transition,
      transitionIri: transition?.node.termType === 'NamedNode' ? transition.node.value : undefined,
    };
  });

  protected select(t: TransitionView): void {
    this.selection.set({ name: t.name, fromIri: t.fromIri, toIri: t.toIri, guard: t.guard });
  }
}
