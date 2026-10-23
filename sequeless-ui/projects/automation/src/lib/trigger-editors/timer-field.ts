import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { SqDurationField, parseIsoDuration } from '@sequeless/ui-kit';

import { timerEcho } from './trigger-form';

function isPositive(iso: string | null): iso is string {
  if (!iso) {
    return false;
  }
  try {
    const { days, hours, minutes, seconds } = parseIsoDuration(iso);
    return days + hours + minutes + seconds > 0;
  } catch {
    return false;
  }
}

/** How long a timer waits after its transition's from-state is entered. */
@Component({
  selector: 'sq-timer-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SqDurationField],
  template: `
    <sq-duration-field [value]="value()" (valueChange)="valueChange.emit($event)" />
    @if (echo(); as echo) {
      <p data-echo>{{ echo }}</p>
    }
  `,
})
export class SqTimerField {
  readonly value = input<string | null>(null);
  readonly fromLabel = input('');
  readonly valueChange = output<string>();

  protected readonly echo = computed(() => {
    const value = this.value();
    return isPositive(value) ? timerEcho(value, this.fromLabel()) : null;
  });
}
