/**
 * `SqFieldAnyUri` (selector `sq-field-any-uri`, datatype `ANY_URI`) renders a
 * `mat-form-field` with `<mat-label>` bound to `property().label` and a
 * native `<input matInput type="url" [formControl]="control()">` tagged with
 * `data-field="{{ property().name }}"`, per the dynamic-field field component
 * contract. The control's value is always the wire value — exactly what
 * `toWireValue('ANY_URI', raw)` from `../../util/wire-value` returns (a plain
 * string, or `null` when cleared).
 *
 * `SqFieldAnyUri` never adds validators or disables the control; that is the
 * host `sq-dynamic-field`'s job, via `controlFor`/`validatorsFor` from
 * `../../util/validators`. The host-attached `'url'` validator (built by
 * `controlFor` for `ANY_URI` properties) is surfaced here as a `mat-error`
 * once the control is touched, so users see "Enter a valid URI" when they blur
 * an invalid value.
 *
 * Because a `FormControl` bound to a text input pushes the raw native `''`
 * when the user clears the field, we subscribe to `valueChanges` and normalize
 * `''` to `null` so the control's value always matches
 * `toWireValue('ANY_URI', raw)`. The subscription is re-created whenever the
 * `control()` input identity changes, so a host-driven control swap keeps the
 * normalization active.
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
  selector: 'sq-field-any-uri',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline">
      <mat-label>{{ property().label }}</mat-label>
      <input
        matInput
        type="url"
        [formControl]="control()"
        [attr.data-field]="property().name"
      />
      @if (control().hasError('url')) {
        <mat-error>Enter a valid URI</mat-error>
      }
    </mat-form-field>
  `,
})
export class SqFieldAnyUri {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  constructor() {
    let sub: { unsubscribe(): void } | null = null;

    effect(() => {
      // Track the control signal so a host-driven swap re-attaches the
      // normalization, then normalize empty strings to `null` on changes
      // (matching `toWireValue('ANY_URI', raw)`).
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
