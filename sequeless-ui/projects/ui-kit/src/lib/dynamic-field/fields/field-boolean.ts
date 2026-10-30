/**
 * `SqFieldBoolean` (selector `sq-field-boolean`, datatype `BOOLEAN`) renders a
 * `mat-slide-toggle` — no `mat-form-field`, unlike every other field component —
 * whose projected text is `property().label` and which carries
 * `data-field="{{ property().name }}"` on the `mat-slide-toggle` element itself,
 * per the dynamic-field field component contract.
 *
 * The control's value is always the wire value — exactly what `toWireValue('BOOLEAN',
 * raw)` from `../../util/wire-value` returns (a genuine `boolean`, or `null` when
 * the control is seeded empty). `MatSlideToggle` natively writes back real booleans
 * (`checked`) on interaction and coerces any incoming value via `!!value`, so no
 * wire-value normalization is required here: the sequence null/false -> true ->
 * false stays a real boolean, never the strings `'true'`/`'false'`.
 *
 * `SqFieldBoolean` never adds validators or disables the control; that is the host
 * `sq-dynamic-field`'s job. When the host disables the control, the slide toggle
 * inherits the disabled state via the `[formControl]` binding.
 *
 * `MatSlideToggle` provides itself (its `required` validator) as an `NG_VALIDATORS`
 * multi-provider. When `FormControlDirective` runs `setUpValidators`, it composes
 * that built-in validator into `control.validator`, leaving a non-null function
 * even though `required` is false and the validator can never produce an error
 * (`validate` unconditionally returns `null` here). To keep
 * `control.validator === null` per the contract, this component clears the
 * control's validators right after the first render, restoring the state the
 * `FormControl` would have had if the control directive had never merged in the
 * slide toggle's built-in validator.
 */
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';
import type { FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import type { AttributePropertyResponse } from '@sequeless/core-api';

@Component({
  selector: 'sq-field-boolean',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatSlideToggleModule],
  template: `
    <mat-slide-toggle
      [formControl]="control()"
      [attr.data-field]="property().name"
    >
      {{ property().label }}
    </mat-slide-toggle>
  `,
})
export class SqFieldBoolean {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  constructor() {
    afterNextRender(() => {
      const control = this.control();
      // MatSlideToggle registers its `required` validator as an NG_VALIDATORS
      // multi-provider, and FormControlDirective then composes it into
      // control.validator (a non-null function). We never added validators
      // ourselves, so undo the composition to leave control.validator === null.
      if (control && control.validator) {
        control.setValidators(null);
      }
    });
  }
}
