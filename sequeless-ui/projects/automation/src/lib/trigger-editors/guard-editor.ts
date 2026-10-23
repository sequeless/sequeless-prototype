import { ChangeDetectionStrategy, Component, effect, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { OntologyEditorPanelContext } from '@sequeless/ontology';
import { setGuard } from '@sequeless/rdf';

const blankToNull = (text: string): string | null => (text.trim() === '' ? null : text);

/**
 * The guard expression and failure message of the selected transition (the `transition-guard` slot). There is
 * no validation endpoint, so nothing here checks the expression (D11).
 */
@Component({
  selector: 'sq-guard-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    @if (context().transition) {
      <mat-form-field>
        <mat-label>Guard</mat-label>
        <textarea matInput rows="2" [formControl]="guard" (change)="commit()"></textarea>
        <mat-hint data-hint>JEXL expression, validated server-side only.</mat-hint>
      </mat-form-field>
      <mat-form-field>
        <mat-label>Failure message</mat-label>
        <input matInput [formControl]="message" (change)="commit()" />
      </mat-form-field>
    } @else {
      <p>Select a transition to edit its guard.</p>
    }
  `,
})
export class SqGuardEditor {
  readonly context = input.required<OntologyEditorPanelContext>();

  protected readonly guard = new FormControl('', { nonNullable: true });
  protected readonly message = new FormControl('', { nonNullable: true });

  constructor() {
    effect(() => {
      const transition = this.context().transition;
      this.guard.setValue(transition?.guard ?? '', { emitEvent: false });
      this.message.setValue(transition?.guardMessage ?? '', { emitEvent: false });
    });
  }

  protected commit(): void {
    const { transition, session } = this.context();
    const store = session.store();
    if (!transition || !store) {
      return;
    }
    const delta = setGuard(store, transition.node, blankToNull(this.guard.value), blankToNull(this.message.value));
    if (delta.added.length || delta.removed.length) {
      session.apply(delta);
    }
  }
}
