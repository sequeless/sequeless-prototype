/**
 * `SqFieldDate` (selector `sq-field-date`, datatype `DATE`) renders a
 * `mat-form-field` with `<mat-label>` bound to `property().label`, a native
 * `<input matInput [matDatepicker]="picker">` tagged with
 * `data-field="{{ property().name }}"`, a `mat-datepicker-toggle`, and a
 * `mat-datepicker`, per the dynamic-field field component contract.
 *
 * The control value is always the wire value — a `YYYY-MM-DD` string built from
 * the date's local calendar fields, or `null` when cleared. The component
 * exposes the currently-displayed date back out as a `date` signal
 * (`Date | null`) and stays in sync with external `control.setValue(...)`.
 *
 * `SqFieldDate` provides `provideNativeDateAdapter()` itself so no date
 * adapter needs wiring by the host, and never adds validators or disables the
 * control; that is the host `sq-dynamic-field`'s job.
 */
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  input,
  inject,
  signal,
} from '@angular/core';
import type { FormControl } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { fromWireValue, toWireValue } from '../../util/wire-value';

@Component({
  selector: 'sq-field-date',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter()],
  imports: [MatFormFieldModule, MatInputModule, MatDatepickerModule],
  template: `
    <mat-form-field appearance="outline">
      <mat-label>{{ property().label }}</mat-label>
      <input
        matInput
        [matDatepicker]="picker"
        [value]="date()"
        [disabled]="control().disabled"
        [attr.data-field]="property().name"
        (dateChange)="setDate($event.value)"
      />
      <mat-datepicker-toggle matIconSuffix [for]="picker" />
      <mat-datepicker #picker />
    </mat-form-field>
  `,
})
export class SqFieldDate {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  private readonly wire = signal<unknown>(null);

  readonly date = computed<Date | null>(() => {
    const v = fromWireValue('DATE', this.wire());
    return v === null ? null : (v as Date | null);
  });

  constructor() {
    let sub: { unsubscribe(): void } | null = null;

    effect(() => {
      // Track the control signal so a host-driven swap re-attaches the
      // sync, then mirror external changes onto the displayed date.
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

  setDate(date: Date | null): void {
    this.control().setValue(toWireValue('DATE', date));
  }
}
