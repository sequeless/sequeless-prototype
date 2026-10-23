/**
 * Contract under test (trigger-editors/timer-field.ts): `SqTimerField` (`sq-timer-field`, inputs `value` — an
 * ISO-8601 duration or null — and `fromLabel`, output `valueChange`) wraps ui-kit's `sq-duration-field` and
 * echoes the delay in words under it (`[data-echo]`, from `timerEcho`) once a positive duration is set.
 * Changing a part emits the ISO duration.
 */
import { TestBed } from '@angular/core/testing';

import { SqTimerField } from './timer-field';

function setup(value: string | null) {
  const fixture = TestBed.createComponent(SqTimerField);
  fixture.componentRef.setInput('value', value);
  fixture.componentRef.setInput('fromLabel', 'OnHold');
  const emitted: string[] = [];
  fixture.componentInstance.valueChange.subscribe((v) => emitted.push(v));
  fixture.detectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement, emitted };
}

describe('SqTimerField', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('echoes the current duration', () => {
    const { host } = setup('PT72H');
    expect(host.querySelector('[data-echo]')?.textContent).toContain('fires 72h after entering OnHold');
  });

  it('has no echo without a duration', () => {
    expect(setup(null).host.querySelector('[data-echo]')).toBeNull();
  });

  it('emits the ISO duration when a part changes', () => {
    const { host, emitted } = setup(null);
    const hours = host.querySelector('input[data-part="hours"]') as HTMLInputElement;
    hours.value = '5';
    hours.dispatchEvent(new Event('input'));
    expect(emitted).toEqual(['PT5H']);
  });
});
