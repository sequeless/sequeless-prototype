/**
 * `SqFieldDateTime` (selector `sq-field-date-time`, datatype `DATE_TIME`)
 * renders a `mat-form-field` with `<mat-label>` bound to `property().label`,
 * a `matDatepicker`-backed native input tagged with
 * `data-field="{{ property().name }}" data-part="date"`, and a native
 * `<input matInput type="time" step="1">` tagged
 * `data-field="{{ property().name }}" data-part="time"`, each in its own
 * `mat-form-field`, per the dynamic-field field component contract.
 *
 * The two parts are combined into a single wire value — the control's value is
 * always exactly what `toWireValue('DATE_TIME', raw)` from
 * `../../util/wire-value` returns: an ISO-8601 instant string built from the
 * local date and time components, never a `Date` instance. The control is only
 * non-null once both a date and a time are present; either part alone leaves
 * the control `null`, and clearing either part after both were set clears the
 * control back to `null`.
 *
 * The component exposes the public methods `setDate(date: Date | null)` and
 * `setTime(time: string | null)` (accepting `'HH:mm'` or `'HH:mm:ss'`,
 * defaulting missing seconds to `:00`), and mirrors the currently-displayed
 * date back out as a `date` signal (`Date | null`). A `valueChanges`
 * subscription populates both parts when the control is set from outside
 * (e.g. when seeding the form from a `BusinessObjectResponse`), and an
 * internal-write flag prevents re-entrant `setValue` loops.
 *
 * `SqFieldDateTime` provides `provideNativeDateAdapter()` itself so no date
 * adapter needs wiring by the host, and never adds validators or disables the
 * control; that is the host `sq-dynamic-field`'s job.
 */
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import type { FormControl } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { fromWireValue, toWireValue } from '../../util/wire-value';

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

function formatHms(date: Date): string {
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
}

@Component({
  selector: 'sq-field-date-time',
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
        [value]="datePart()"
        [disabled]="control().disabled"
        [attr.data-field]="property().name"
        data-part="date"
        (dateChange)="setDate($event.value)"
      />
      <mat-datepicker-toggle matIconSuffix [for]="picker" />
      <mat-datepicker #picker />
    </mat-form-field>
    <mat-form-field appearance="outline">
      <input
        matInput
        type="time"
        step="1"
        [value]="timePart() ?? ''"
        [disabled]="control().disabled"
        [attr.data-field]="property().name"
        data-part="time"
        (input)="onTimeInput($event)"
      />
    </mat-form-field>
  `,
})
export class SqFieldDateTime {
  readonly property = input.required<AttributePropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  readonly datePart = signal<Date | null>(null);
  readonly timePart = signal<string | null>(null);

  /** The currently-displayed date (the wire `DATE_TIME` value's date part). */
  readonly date = computed<Date | null>(() => this.datePart());

  /** True while `setValue` was triggered by our own `setDate`/`setTime`. */
  private writing = false;

  constructor() {
    let sub: { unsubscribe(): void } | null = null;

    effect(() => {
      // Track the control signal so a host-driven swap re-attaches the sync,
      // then mirror external changes onto the displayed date/time parts.
      const control = this.control();
      sub?.unsubscribe();
      this.applyWire(control.value ?? null);
      sub = control.valueChanges.subscribe((v) => {
        // Skip changes we initiated ourselves; those already updated both
        // parts. A second write with an identical value would loop forever.
        if (this.writing) {
          this.writing = false;
          return;
        }
        this.applyWire(v ?? null);
      });
    });

    inject(DestroyRef).onDestroy(() => {
      sub?.unsubscribe();
    });
  }

  setDate(date: Date | null): void {
    this.datePart.set(date);
    this.writeWire();
  }

  setTime(time: string | null): void {
    this.timePart.set(time);
    this.writeWire();
  }

  private onTimeInput(event: InputEvent): void {
    const raw = ((event.target as HTMLInputElement).value ?? '').trim();
    this.setTime(raw === '' ? null : raw);
  }

  private writeWire(): void {
    const date = this.datePart();
    const time = this.timePart();
    const next =
      date !== null && time !== null
        ? toWireValue('DATE_TIME', this.combine(date, time))
        : null;
    this.writing = true;
    this.control().setValue(next);
  }

  private combine(date: Date, time: string): Date {
    // Accept both a full `'HH:mm:ss'` and a bare `'HH:mm'` (seconds default
    // to 0), per the `setTime` contract.
    const parts = time.split(':').map((p) => Number(p));
    const h = parts[0] ?? 0;
    const m = parts[1] ?? 0;
    const s = parts[2] ?? 0;
    return new Date(
      date.getFullYear(),
      date.getMonth(),
      date.getDate(),
      h,
      m,
      s,
      0,
    );
  }

  private applyWire(wire: unknown): void {
    const parsed = fromWireValue('DATE_TIME', wire);
    if (parsed instanceof Date && !Number.isNaN(parsed.getTime())) {
      this.datePart.set(parsed);
      this.timePart.set(formatHms(parsed));
    } else {
      this.datePart.set(null);
      this.timePart.set(null);
    }
  }
}
