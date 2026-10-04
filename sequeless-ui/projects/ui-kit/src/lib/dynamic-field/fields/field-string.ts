/**
 * `SqFieldString` (selector `sq-field-string`, datatype `STRING`) renders a
 * `mat-form-field` with `<mat-label>` bound to `property().label` and a
 * native `<input matInput type="text" [formControl]="control()">` tagged with
 * `data-field="{{ property().name }}"`, per the dynamic-field field component
 * contract. The control's value is always the wire value — exactly what
 * `toWireValue('STRING', raw)` returns (a plain string, or `null` when cleared).
 *
 * `SqFieldString` never adds validators or disables the control; that is the
 * host `sq-dynamic-field`'s job. When the host disables the control, the native
 * input inherits the disabled state via the `[formControl]` binding.
 *
 * Because a `FormControl` bound to a text input pushes the raw native `''`
 * when the user clears the field, we subscribe to `valueChanges` and normalize
 * `''` to `null` so the control's value is always the `toWireValue('STRING',
 * raw)` shape. The subscription is re-created whenever the `control()` input
 * identity changes, so a host-driven control swap keeps the normalization
 * active.
 */
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
} from '@angular/core';
import type { FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { AttributePropertyResponse } from '@sequeless/core-api';

@Component({
  selector: 'sq-field-string',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline">
      <mat-label>{{ property().label }}</mat-label>
      <input
        matInput
        type="text"
        [formControl]="control()"
        [attr.data-field]="property().name"
      />
    </mat-form-field>
  `,
})
export class SqFieldString {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  constructor() {
    let sub: { unsubscribe(): void } | null = null;

    effect(() => {
      // Track the control signal so a host-driven swap re-attaches the
      // normalization, then normalize empty strings to `null` on changes.
      const control = this.control();
      sub?.unsubscribe();
      sub = control.valueChanges.subscribe((v) => {
        if (v === '') {
          control.setValue(null);
        }
      });
    });

    inject(DestroyRef).onDestroy(() => {
      sub?.unsubscribe();
    });
  }
}
