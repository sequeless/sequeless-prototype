import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  forwardRef,
  input,
  output,
  signal,
  viewChildren,
  Children,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatLabel } from '@angular/material/form-field';
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
    @for (part of parts; track part) {
      <mat-form-field appearance="outline">
        <mat-label>{{ labelText(part) }}</mat-label>
        <input
          #durationInput
          matInput
          type="number"
          min="0"
          [attr.data-part]="part"
          [disabled]="disabled()"
          (input)="onInput($event, part)"
        />
      </mat-form-field>
    }
  `,
})
export class SqDurationField implements ControlValueAccessor {
  readonly parts = PARTS;

  readonly value = input<string | null>(null);
  readonly valueChange = output<string>();

  private readonly partsSignal = signal<DurationParts | null>(null);
  readonly disabled = signal(false);

  @viewChildren('durationInput')
  readonly durationInputs: Children<ElementRef<HTMLInputElement>>;

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  constructor() {
    effect(() => {
      this.syncDom(this.value());
    });
    effect(() => {
      this.syncDom(this.partsSignal());
    });
  }

  labelText(part: DurationPart): string {
    return LABELS[part];
  }

  displayFor(part: DurationPart): string {
    const p = this.partsSignal();
    if (p == null) return '';
    return String(p[part]);
  }

  private syncDom(value: string | null): void {
    if (value == null) {
      this.partsSignal.set(null);
      for (const i of this.durationInputs()) {
        i.nativeElement.value = '';
      }
      return;
    }
    try {
      const parts = parseIsoDuration(value);
      this.partsSignal.set(parts);
      const inputs = this.durationInputs();
      for (const i of inputs) {
        const part = (i.nativeElement.getAttribute('data-part') ?? 'days') as DurationPart;
        i.nativeElement.value = String(parts[part]);
      }
    } catch {
      this.partsSignal.set(null);
      for (const i of this.durationInputs()) {
        i.nativeElement.value = '';
      }
    }
  }

  onInput(event: Event, editedPart: DurationPart): void {
    const target = event.target as HTMLInputElement | null;
    if (!target) return;

    const inputs = this.durationInputs();
    const byPart = new Map<DurationPart, HTMLInputElement>();
    for (const i of inputs) {
      const p = (i.nativeElement.getAttribute('data-part') ?? 'days') as DurationPart;
      byPart.set(p, i.nativeElement);
    }

    const base: DurationParts = { days: 0, hours: 0, minutes: 0, seconds: 0 };
    for (const p of PARTS) {
      base[p] = toNumber(byPart.get(p)?.value ?? '');
    }
    // Ensure the event-driven edit wins (in case DOM lags behind our signal sync).
    base[editedPart] = toNumber(target.value);

    this.partsSignal.set(base);
    const iso = formatIsoDuration(base);
    this.valueChange.emit(iso);
    this.onChange(iso);
    this.onTouched();
  }

  // ControlValueAccessor
  writeValue(value: string | null): void {
    this.syncDom(value);
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
