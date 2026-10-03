/**
 * Contract under test (lib/duration-field/duration-field.ts): `SqDurationField` (selector
 * `sq-duration-field`) is the composite days/hours/minutes/seconds control described by the
 * phase-5 spec's "sq-duration-field" section — four `<input type="number" min="0">` tagged
 * `data-part="days"|"hours"|"minutes"|"seconds"` (each with a Days/Hours/Minutes/Seconds label),
 * backed by `parseIsoDuration`/`formatIsoDuration` (lib/util/iso-duration.ts) for the ISO-8601
 * `PnDTnHnMn.nS` wire format the dynamic-field table and the timer-trigger editor both expect.
 *
 * `value = input<string | null>(null)` drives the four inputs: a non-null value is parsed and
 * split across them, `null` renders all four empty rather than `0`. Editing any single input
 * re-reads all four current input values, builds a `DurationParts` from them, formats it back to
 * ISO via `formatIsoDuration`, and emits the result on `valueChange` — not just the edited part in
 * isolation, since the other three inputs' current values are part of the same composite value.
 *
 * `SqDurationField` also implements `ControlValueAccessor` (providing `NG_VALUE_ACCESSOR`), so it
 * works standalone behind `[formControl]`/`[formControlName]` without the `value`/`valueChange`
 * pair: `writeValue` drives the same display split `value` does, `registerOnChange`'s callback is
 * invoked with the same formatted ISO string `valueChange` emits, and `setDisabledState` disables
 * all four inputs together.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { SqDurationField } from './duration-field';

function partInput(fixture: { nativeElement: HTMLElement }, part: 'days' | 'hours' | 'minutes' | 'seconds'): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(`input[data-part="${part}"]`);
  if (!el) {
    throw new Error(`no input found for data-part="${part}"`);
  }
  return el;
}

function setPartValue(fixture: { nativeElement: HTMLElement }, part: 'days' | 'hours' | 'minutes' | 'seconds', value: string): void {
  const input = partInput(fixture, part);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('SqDurationField', () => {
  describe('standalone value/valueChange', () => {
    it('splits a mixed days+hours value across the four inputs', async () => {
      const fixture = TestBed.createComponent(SqDurationField);
      fixture.componentRef.setInput('value', 'P1DT2H');
      await fixture.whenStable();

      expect(partInput(fixture, 'days').value).toBe('1');
      expect(partInput(fixture, 'hours').value).toBe('2');
      expect(partInput(fixture, 'minutes').value).toBe('0');
      expect(partInput(fixture, 'seconds').value).toBe('0');
    });

    it('renders all four inputs empty when value is null', async () => {
      const fixture = TestBed.createComponent(SqDurationField);
      fixture.componentRef.setInput('value', null);
      await fixture.whenStable();

      expect(partInput(fixture, 'days').value).toBe('');
      expect(partInput(fixture, 'hours').value).toBe('');
      expect(partInput(fixture, 'minutes').value).toBe('');
      expect(partInput(fixture, 'seconds').value).toBe('');
    });

    it('labels each input Days/Hours/Minutes/Seconds', async () => {
      const fixture = TestBed.createComponent(SqDurationField);
      fixture.componentRef.setInput('value', 'P1DT2H');
      await fixture.whenStable();

      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain('Days');
      expect(text).toContain('Hours');
      expect(text).toContain('Minutes');
      expect(text).toContain('Seconds');
    });

    it('emits the composite ISO value, combining the edited input with the other three current values', async () => {
      const fixture = TestBed.createComponent(SqDurationField);
      fixture.componentRef.setInput('value', 'P1DT2H');
      await fixture.whenStable();

      const emitted: string[] = [];
      fixture.componentInstance.valueChange.subscribe((v: string) => emitted.push(v));

      setPartValue(fixture, 'minutes', '5');
      await fixture.whenStable();

      expect(emitted).toEqual(['P1DT2H5M']);
    });

    it('emits PT0S when every input is cleared', async () => {
      const fixture = TestBed.createComponent(SqDurationField);
      fixture.componentRef.setInput('value', 'P1DT2H5M');
      await fixture.whenStable();

      const emitted: string[] = [];
      fixture.componentInstance.valueChange.subscribe((v: string) => emitted.push(v));

      setPartValue(fixture, 'days', '');
      setPartValue(fixture, 'hours', '');
      setPartValue(fixture, 'minutes', '');
      setPartValue(fixture, 'seconds', '');
      await fixture.whenStable();

      expect(emitted.at(-1)).toBe('PT0S');
    });

    it('emits the new value when editing days, preserving the other current values', async () => {
      const fixture = TestBed.createComponent(SqDurationField);
      fixture.componentRef.setInput('value', 'PT30M');
      await fixture.whenStable();

      const emitted: string[] = [];
      fixture.componentInstance.valueChange.subscribe((v: string) => emitted.push(v));

      setPartValue(fixture, 'days', '3');
      await fixture.whenStable();

      expect(emitted).toEqual(['P3DT30M']);
    });
  });

  describe('as a ControlValueAccessor', () => {
    @Component({
      selector: 'sq-test-host',
      standalone: true,
      imports: [SqDurationField, ReactiveFormsModule],
      template: `<sq-duration-field [formControl]="ctrl" />`,
    })
    class TestHost {
      ctrl = new FormControl<string | null>(null);
    }

    it('writes the control value onto the four inputs via writeValue', async () => {
      const fixture = TestBed.createComponent(TestHost);
      fixture.componentInstance.ctrl.setValue('PT30M');
      await fixture.whenStable();

      expect(partInput(fixture, 'minutes').value).toBe('30');
      expect(partInput(fixture, 'hours').value).toBe('0');
    });

    it('propagates an edited input back onto the FormControl value', async () => {
      const fixture = TestBed.createComponent(TestHost);
      fixture.componentInstance.ctrl.setValue('PT30M');
      await fixture.whenStable();

      setPartValue(fixture, 'hours', '1');
      await fixture.whenStable();

      expect(fixture.componentInstance.ctrl.value).toBe('PT1H30M');
    });

    it('disables all four inputs when the FormControl is disabled', async () => {
      const fixture = TestBed.createComponent(TestHost);
      fixture.componentInstance.ctrl.setValue('PT30M');
      fixture.componentInstance.ctrl.disable();
      await fixture.whenStable();

      expect(partInput(fixture, 'days').disabled).toBe(true);
      expect(partInput(fixture, 'hours').disabled).toBe(true);
      expect(partInput(fixture, 'minutes').disabled).toBe(true);
      expect(partInput(fixture, 'seconds').disabled).toBe(true);
    });
  });

  describe('input attributes', () => {
    it('sets type="number" and min="0" on every part input', async () => {
      const fixture = TestBed.createComponent(SqDurationField);
      fixture.componentRef.setInput('value', null);
      await fixture.whenStable();

      for (const part of ['days', 'hours', 'minutes', 'seconds'] as const) {
        const input = partInput(fixture, part);
        expect(input.type).toBe('number');
        expect(input.min).toBe('0');
      }
    });
  });
});
