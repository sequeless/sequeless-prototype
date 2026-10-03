import {
  ChangeDetectionStrategy,
  Component,
  effect,
  forwardRef,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import {
  type DurationParts,
  formatIsoDuration,
  parseIsoDuration,
} from '../util/iso-duration';

const PARTS = ['days', 'hours', 'minutes', 'seconds'] as const;
type DurationPart = (typeof PARTS)[number];

const LABELS: Record<DurationPart, string> = {
  days: 'Days',
  hours: 'Hours',
  minutes: 'Minutes',
  seconds: 'Seconds',
};

function toNumber(raw: string): number {
  if (raw === '') return 0;
  const n = Number(raw);
  return Number.isFinite(n) ? n : 0;
}

const ZERO: DurationParts = { days: 0, hours: 0, minutes: 0, seconds: 0 };

@Component({
  selector: 'sq-duration-field',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormField, MatLabel, MatInput],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SqDurationField),
      multi: true,
    },
  ],
  template: `
    @for (part of partNames; track part) {
      <mat-form-field appearance="outline">
        <mat-label>{{ labels[part] }}</mat-label>
        <input
          matInput
          type="number"
          min="0"
          [attr.data-part]="part"
          [value]="parts() !== null ? parts()![part] : ''"
          [disabled]="disabled()"
          (input)="onPart(part, $event)"
        />
      </mat-form-field>
    }
  `,
})
export class SqDurationField implements ControlValueAccessor {
  // Template helpers.
  readonly partNames = PARTS;
  readonly labels = LABELS;

  // Public signal API.
  readonly value = input<string | null>(null);
  readonly valueChange = output<string>();
  readonly parts = signal<DurationParts | null>(null);
  readonly disabled = signal(false);

  // CVA tracking.
  private _cvaActive = false;
  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  constructor() {
    // When driven by the `value` input (standalone mode), sync it to `parts`.
    // When CVA is active, the effect stays out of the way and `writeValue`
    // is the sole source of truth for `parts`.
    effect(() => {
      if (this._cvaActive) return;
      const v = this.value();
      if (v === null) {
        this.parts.set(null);
      } else {
        try {
          this.parts.set(parseIsoDuration(v));
        } catch {
          this.parts.set(null);
        }
      }
    });
  }

  onPart(part: DurationPart, event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    const prev = this.parts() ?? ZERO;
    const next: DurationParts = { ...prev, [part]: toNumber(raw) };
    this.parts.set(next);
    const iso = formatIsoDuration(next);
    this.valueChange.emit(iso);
    this.onChange(iso);
    this.onTouched();
  }

  // ── ControlValueAccessor ────────────────────────────────────────────────────

  writeValue(value: string | null): void {
    this._cvaActive = true;
    if (value === null) {
      this.parts.set(null);
    } else {
      try {
        this.parts.set(parseIsoDuration(value));
      } catch {
        this.parts.set(null);
      }
    }
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled === true);
  }
}
