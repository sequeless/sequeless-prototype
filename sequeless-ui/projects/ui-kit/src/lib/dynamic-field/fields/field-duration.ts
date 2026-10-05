/**
 * `SqFieldDuration` (selector `sq-field-duration`, datatype `DURATION`) renders a
 * `<fieldset>` with `<legend>{{ property().label }}</legend>` — not a `mat-form-field`,
 * since `sq-duration-field` is a composite of plain number inputs, not a single Material
 * control — containing a `<sq-duration-field [formControl]="control()" data-field>` (from
 * `../../duration-field/duration-field`) tagged `data-field="{{ property().name }}"`, per
 * the dynamic-field field component contract in the phase-5 spec.
 *
 * `SqDurationField` is itself a full `ControlValueAccessor`, so binding it directly via
 * `[formControl]` is sufficient for the control's value to always be the wire value —
 * exactly what `toWireValue('DURATION', raw)` from `../../util/wire-value` returns, an
 * ISO-8601 `PnDTnHnMn.nS` string (or `null`) — with no extra combination logic in
 * `SqFieldDuration` itself.
 *
 * `SqFieldDuration` never adds validators or disables the control; that is the host
 * `sq-dynamic-field`'s job.
 */
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { type FormControl, ReactiveFormsModule } from '@angular/forms';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { SqDurationField } from '../../duration-field/duration-field';

@Component({
  selector: 'sq-field-duration',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, SqDurationField],
  template: `
    <fieldset>
      <legend>{{ property().label }}</legend>
      <sq-duration-field
        [formControl]="control()"
        [attr.data-field]="property().name"
      />
    </fieldset>
  `,
})
export class SqFieldDuration {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();
}
