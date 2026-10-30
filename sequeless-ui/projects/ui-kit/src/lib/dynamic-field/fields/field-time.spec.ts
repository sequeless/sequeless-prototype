/**
 * Contract under test (lib/dynamic-field/fields/field-time.ts): `SqFieldTime` (selector
 * `sq-field-time`, datatype `TIME`) renders a `mat-form-field` with `<mat-label>` bound to
 * `property().label` and a native `<input matInput type="time" step="1" [formControl]="control()">`
 * tagged `data-field="{{ property().name }}"`, per the dynamic-field field component contract in
 * the phase-5 spec. The control's value is always the wire value — i.e. exactly what
 * `toWireValue('TIME', raw)` from `../../util/wire-value` returns — which is an identity mapping
 * for a non-empty `HH:mm`/`HH:mm:ss` string, so typing `09:30` or `09:30:15` into the input drives
 * the control to that exact string, while clearing the input drives the control to `null`, never
 * the empty string. An external `control.setValue(...)` is reflected back into the input's
 * displayed value. `SqFieldTime` never adds validators or disables the control; that is the host
 * `sq-dynamic-field`'s job.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { toWireValue } from '../../util/wire-value';
import { SqFieldTime } from './field-time';

const REMINDER_TIME_PROPERTY: AttributePropertyResponse = {
  kind: 'attribute',
  iri: 'https://sequeless.dev/ns/ref#reminderTime',
  name: 'reminderTime',
  label: 'Reminder Time',
  order: 1,
  group: null,
  hidden: false,
  facet: false,
  indexed: false,
  searchable: false,
  readOnly: false,
  cardinalityMin: 0,
  cardinalityMax: 1,
  datatype: 'TIME',
  derivation: null,
};

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldTime, ReactiveFormsModule],
  template: `<sq-field-time [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = REMINDER_TIME_PROPERTY;
  control = new FormControl<unknown>(null);
}

function inputEl(fixture: { nativeElement: HTMLElement }): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(
    'input[data-field="reminderTime"]',
  );
  if (!el) {
    throw new Error('no input found for data-field="reminderTime"');
  }
  return el;
}

function labelText(fixture: { nativeElement: HTMLElement }): string {
  return fixture.nativeElement.querySelector('mat-label')?.textContent?.trim() ?? '';
}

function typeInto(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('SqFieldTime', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  it('renders the label from property().label', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(labelText(fixture)).toBe('Reminder Time');
  });

  it('renders a mat-form-field containing a native input with type="time" step="1" tagged data-field with the property name', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-form-field')).not.toBeNull();
    const input = inputEl(fixture);
    expect(input.getAttribute('type')).toBe('time');
    expect(input.getAttribute('step')).toBe('1');
    expect(input.getAttribute('data-field')).toBe('reminderTime');
  });

  it('sets the control to the wire value when the user types an HH:mm time', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    typeInto(inputEl(fixture), '09:30');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('TIME', '09:30'));
    expect(fixture.componentInstance.control.value).toBe('09:30');
  });

  it('sets the control to the wire value when the user types an HH:mm:ss time with seconds', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    typeInto(inputEl(fixture), '09:30:15');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('TIME', '09:30:15'));
    expect(fixture.componentInstance.control.value).toBe('09:30:15');
  });

  it('clearing the input sets the control to null, matching toWireValue on empty string, never the empty string', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('09:30');
    await fixture.whenStable();

    typeInto(inputEl(fixture), '');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('TIME', ''));
    expect(fixture.componentInstance.control.value).toBeNull();
    expect(fixture.componentInstance.control.value).not.toBe('');
  });

  it('reflects an external control.setValue into the input', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fixture.componentInstance.control.setValue('14:05:00');
    await fixture.whenStable();

    expect(inputEl(fixture).value).toBe('14:05:00');
  });

  it('reflects an external control.setValue(null) by clearing the input', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('14:05:00');
    await fixture.whenStable();

    fixture.componentInstance.control.setValue(null);
    await fixture.whenStable();

    expect(inputEl(fixture).value).toBe('');
  });

  it('does not add validators to the control', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.componentInstance.control.validator).toBeNull();
  });

  it('does not disable the control', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.componentInstance.control.disabled).toBe(false);
  });

  it('leaves a host-disabled control disabled and keeps the native input in sync', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.disable();
    await fixture.whenStable();

    expect(inputEl(fixture).disabled).toBe(true);
  });
});
