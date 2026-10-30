/**
 * `SqFieldInteger` (selector `sq-field-integer`, datatypes `INTEGER` and `LONG`)
 * renders a `mat-form-field` with `<mat-label>` bound to `property().label` and
 * a native `<input matInput type="number" step="1" [formControl]="control()">`
 * tagged with `data-field="{{ property().name }}"`, per the dynamic-field field
 * component contract.
 *
 * The control's value is always the wire value — exactly what `toWireValue(
 * datatype, raw)` from `../util/wire-value` returns — i.e. a genuine integral
 * `number` (truncated toward zero), or `null` when cleared. `LONG` shares the
 * same wire coercion as `INTEGER`.
 *
 * The Angular number accessor yields a floating `number` for a non-integral
 * typed value (e.g. `7.9`) and the raw `''` string when the input is cleared,
 * neither of which is the wire shape. We subscribe to `valueChanges` and
 * normalize via `toWireValue`, comparing against the current value so the
 * normalizing `setValue` fires exactly once per user change (a second pass
 * over an already-integral value is a no-op and cannot loop). The
 * subscription is re-created whenever the `control()` or `property()` signal
 * identity changes, so a host-driven control/property swap keeps the
 * normalization active with the correct datatype.
 *
 * `SqFieldInteger` never adds validators or disables the control; that is the
 * host `sq-dynamic-field`'s job. When the host disables the control, the
 * native input inherits the disabled state via the `[formControl]` binding.
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
import { toWireValue } from '../../util/wire-value';

@Component({
  selector: 'sq-field-integer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline">
      <mat-label>{{ property().label }}</mat-label>
      <input
        matInput
        type="number"
        step="1"
        [formControl]="control()"
        [attr.data-field]="property().name"
      />
    </mat-form-field>
  `,
})
export class SqFieldInteger {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  constructor() {
    let sub: { unsubscribe(): void } | null = null;

    effect(() => {
      // Track both signals so a host-driven swap of either re-attaches the
      // normalization, then normalize raw values to the wire shape.
      const control = this.control();
      const datatype = this.property().datatype;
      sub?.unsubscribe();
      sub = control.valueChanges.subscribe((raw: unknown) => {
        const wire = toWireValue(datatype, raw);
        if (wire !== raw && !(typeof wire === 'number' && !Number.isFinite(wire))) {
          control.setValue(wire);
        }
      });
    });

    inject(DestroyRef).onDestroy(() => {
      sub?.unsubscribe();
    });
  }
}
