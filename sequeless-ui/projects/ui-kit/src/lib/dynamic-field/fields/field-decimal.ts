/**
 * `SqFieldDecimal` (selector `sq-field-decimal`, datatypes `DECIMAL` and `DOUBLE`)
 * renders a `mat-form-field` with `<mat-label>` bound to `property().label` and a
 * native `<input matInput type="number" step="any" [formControl]="control()">`
 * tagged with `data-field="{{ property().name }}"`, per the dynamic-field field
 * component contract.
 *
 * The control's value is always the wire value — exactly what `toWireValue(
 * datatype, raw)` from `../../util/wire-value` returns — i.e. a genuine floating
 * `number` (not truncated toward zero the way `INTEGER`/`LONG` truncate), or
 * `null` when cleared. `DOUBLE` shares the same wire coercion as `DECIMAL`.
 *
 * The default control value accessor yields the raw `''` string when the input
 * is cleared and a numeric `string` when the user types into a `type="number"`
 * input; neither is guaranteed to be the wire shape. We subscribe to
 * `valueChanges` and normalize via `toWireValue`, only calling `setValue` when
 * the coerced value differs from the raw value (and is a finite number, so we
 * never write `NaN`). The subscription is re-created whenever the `control()`
 * or `property()` signal identity changes, so a host-driven control/property
 * swap keeps the normalization active with the correct datatype.
 *
 * `SqFieldDecimal` never adds validators or disables the control; that is the
 * host `sq-dynamic-field`'s job. When the host disables the control, the native
 * input inherits the disabled state via the `[formControl]` binding.
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
  selector: 'sq-field-decimal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline">
      <mat-label>{{ property().label }}</mat-label>
      <input
        matInput
        type="number"
        step="any"
        [formControl]="control()"
        [attr.data-field]="property().name"
      />
    </mat-form-field>
  `,
})
export class SqFieldDecimal {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  constructor() {
    let sub: { unsubscribe(): void } | null = null;

    effect(() => {
      // Track both signals so a host-driven swap of either re-attaches the
      // normalization, then coerce raw values to the numeric wire shape.
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
