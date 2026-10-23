import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { ControlRegistry } from '@sequeless/ontology';

/** The name an external signal is fired by. Collisions with other transitions warn; they never block (D9). */
@Component({
  selector: 'sq-signal-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field>
      <mat-label>Signal name</mat-label>
      <input matInput [formControl]="control" (change)="valueChange.emit(control.value)" />
    </mat-form-field>
    @if (collisions().length) {
      <p data-warning>Also used by {{ collisions().join(', ') }} on this type.</p>
    }
  `,
})
export class SqSignalField {
  readonly value = input('');
  readonly collisions = input<readonly string[]>([]);
  readonly registry = input<ControlRegistry>();
  readonly subjectIri = input<string>();
  readonly valueChange = output<string>();

  protected readonly control = new FormControl('', { nonNullable: true });
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  constructor() {
    effect(() => this.control.setValue(this.value(), { emitEvent: false }));
    effect((onCleanup) => {
      const registry = this.registry();
      const subject = this.subjectIri();
      if (registry && subject) {
        registry.register(subject, this.control, this.element);
        onCleanup(() => registry.unregister(this.control));
      }
    });
    inject(DestroyRef).onDestroy(() => this.registry()?.unregister(this.control));
  }
}
