/**
 * `SqFieldTime` (selector `sq-field-time`, datatype `TIME`) renders a
 * `mat-form-field` with `<mat-label>` bound to `property().label` and a native
 * `<input matInput type="time" step="1">` tagged with
 * `data-field="{{ property().name }}"`, per the dynamic-field field component
 * contract.
 *
 * The control's value is always the wire value — exactly what
 * `toWireValue('TIME', raw)` from `../../util/wire-value` returns — which is an
 * identity mapping for a non-empty `HH:mm`/`HH:mm:ss` string (so typing `09:30`
 * or `09:30:15` drives the control to that exact string), while clearing the
 * input drives the control to `null`, never the empty string.
 *
 * Rather than binding the `FormControl` directly — which would push a raw `''`
 * when the field is cleared, not the wire shape — we bind the native input's
 * `[value]` to a local `wire` signal and write `toWireValue('TIME', value)`
 * back on `(input)`. The `wire` signal mirrors the control via a `valueChanges`
 * subscription (re-created whenever the `control()` input identity changes),
 * so external `control.setValue(...)` — including `null` — is reflected into
 * the displayed value even under `OnPush` detection.
 *
 * `SqFieldTime` never adds validators or disables the control; that is the host
 * `sq-dynamic-field`'s job. When the host disables the control, the native
 * input inherits the disabled state via `[disabled]="control().disabled"`.
 */
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import type { FormControl } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { toWireValue } from '../../util/wire-value';

@Component({
  selector: 'sq-field-time',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline">
      <mat-label>{{ property().label }}</mat-label>
      <input
        matInput
        type="time"
        step="1"
        [value]="wire() ?? ''"
        [disabled]="control().disabled"
        [attr.data-field]="property().name"
        (input)="onInput($event)"
      />
    </mat-form-field>
  `,
})
export class SqFieldTime {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  private readonly wire = signal<unknown>(null);

  constructor() {
    let sub: { unsubscribe(): void } | null = null;

    effect(() => {
      // Track the control signal so a host-driven swap re-attaches the sync,
      // then mirror external changes onto the displayed value.
      const control = this.control();
      sub?.unsubscribe();
      this.wire.set(control.value ?? null);
      sub = control.valueChanges.subscribe((v) => {
        this.wire.set(v);
      });
    });

    inject(DestroyRef).onDestroy(() => {
      sub?.unsubscribe();
    });
  }

  onInput(event: InputEvent): void {
    const value = (event.target as HTMLInputElement).value;
    this.control().setValue(toWireValue('TIME', value));
  }
}
