import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import type { TriggerKind } from '@sequeless/core-api';
import type { OntologyEditorPanelContext } from '@sequeless/ontology';
import { readStateMachine, setTransitionTrigger } from '@sequeless/rdf';

import { SqOnChangeField } from './on-change-field';
import { SqSignalField } from './signal-field';
import { SqTimerField } from './timer-field';
import { signalCollisions, triggerFromForm, triggerToForm } from './trigger-form';
import type { TriggerFormValue } from './trigger-form';

const KINDS: { kind: TriggerKind; label: string }[] = [
  { kind: 'USER_ACTION', label: 'User action' },
  { kind: 'ON_CHANGE', label: 'On change' },
  { kind: 'TIMER', label: 'Timer' },
  { kind: 'EXTERNAL_SIGNAL', label: 'External signal' },
];

const EMPTY: TriggerFormValue = { kind: 'USER_ACTION', watch: [], after: null, signalName: '' };

/**
 * The trigger of the selected transition (the `transition-trigger` slot). Every valid change is one
 * `setTransitionTrigger` delta; rdf removes the previous kind's stale terms (D5).
 */
@Component({
  selector: 'sq-trigger-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatSelectModule, SqOnChangeField, SqTimerField, SqSignalField],
  template: `
    @if (context().transition; as transition) {
      <mat-form-field>
        <mat-label>Trigger</mat-label>
        <mat-select [value]="draft().kind" (selectionChange)="setKind($event.value)">
          @for (k of kinds; track k.kind) {
            <mat-option [value]="k.kind">{{ k.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      @switch (draft().kind) {
        @case ('ON_CHANGE') {
          <sq-on-change-field
            [typeIri]="context().typeIri"
            [value]="draft().watch"
            (valueChange)="update({ watch: $event })"
          />
        }
        @case ('TIMER') {
          <sq-timer-field
            [value]="draft().after"
            [fromLabel]="fromLabel()"
            (valueChange)="update({ after: $event })"
          />
        }
        @case ('EXTERNAL_SIGNAL') {
          <sq-signal-field
            [value]="draft().signalName"
            [collisions]="collisions()"
            [registry]="context().registry"
            [subjectIri]="transition.node.value"
            (valueChange)="update({ signalName: $event })"
          />
        }
      }
      @if (error(); as error) {
        <p data-error>{{ error }}</p>
      }
    } @else {
      <p>Select a transition to edit its trigger.</p>
    }
  `,
})
export class SqTriggerEditor {
  readonly context = input.required<OntologyEditorPanelContext>();

  protected readonly kinds = KINDS;
  protected readonly draft = signal<TriggerFormValue>(EMPTY);
  protected readonly error = signal<string | null>(null);

  private readonly machine = computed(() => {
    const { session, typeIri } = this.context();
    session.revision();
    const store = session.store();
    return store ? readStateMachine(store, typeIri) : null;
  });

  protected readonly fromLabel = computed(() => {
    const from = this.context().transition?.fromIri;
    return this.machine()?.states.find((s) => s.iri === from)?.label ?? from ?? '';
  });

  protected readonly collisions = computed(() => {
    const transition = this.context().transition;
    const machine = this.machine();
    return transition && machine ? signalCollisions(machine, transition, this.draft().signalName) : [];
  });

  private lastStored = '';

  constructor() {
    // Re-read the draft only when the stored transition really changed, not on every re-parse.
    effect(() => {
      const transition = this.context().transition;
      const stored = transition
        ? JSON.stringify([transition.name, transition.fromIri, transition.toIri, triggerToForm(transition.trigger)])
        : '';
      if (stored === this.lastStored) {
        return;
      }
      this.lastStored = stored;
      this.draft.set(transition ? triggerToForm(transition.trigger) : EMPTY);
      this.error.set(null);
    });
  }

  protected setKind(kind: TriggerKind): void {
    this.update({ kind });
  }

  protected update(change: Partial<TriggerFormValue>): void {
    this.draft.update((d) => ({ ...d, ...change }));
    this.commit();
  }

  private commit(): void {
    const { transition, session } = this.context();
    const store = session.store();
    if (!transition || !store) {
      return;
    }
    const trigger = triggerFromForm(this.draft());
    if ('error' in trigger) {
      this.error.set(trigger.error);
      return;
    }
    this.error.set(null);
    const delta = setTransitionTrigger(store, transition.node, trigger);
    if (delta.added.length || delta.removed.length) {
      session.apply(delta);
    }
  }
}
