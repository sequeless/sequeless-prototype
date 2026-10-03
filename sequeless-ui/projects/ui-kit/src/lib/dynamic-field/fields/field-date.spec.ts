/**
 * Contract under test (lib/dynamic-field/fields/field-date.ts): `SqFieldDate` (selector
 * `sq-field-date`, datatype `DATE`) renders a `mat-form-field` with `<mat-label>` bound to
 * `property().label`, a native `<input matInput [matDatepicker]="picker">` tagged
 * `data-field="{{ property().name }}"`, a `mat-datepicker-toggle`, and the `mat-datepicker`
 * itself, per the dynamic-field field component contract in the phase-5 spec. The component
 * provides `provideNativeDateAdapter()` itself, so no date adapter needs wiring in the test.
 *
 * Picking a date fires `dateChange`, bound to the component's own `setDate(date: Date | null)`,
 * which writes `toWireValue('DATE', date)` into the control — a `YYYY-MM-DD` string built from the
 * date's *local* calendar fields, never a `Date` instance and never the UTC date. The component
 * also exposes the currently-displayed date back out as a `date` signal/computed (`Date | null`),
 * which an external `control.setValue(...)` must keep in sync so the datepicker reflects a value
 * set programmatically (e.g. when seeding the form from a `BusinessObjectResponse`).
 *
 * `SqFieldDate` never adds validators or disables the control itself; that is the host
 * `sq-dynamic-field`'s job.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { toWireValue } from '../../util/wire-value';
import { SqFieldDate } from './field-date';

const DUE_DATE_PROPERTY: AttributePropertyResponse = {
  kind: 'attribute',
  iri: 'https://sequeless.dev/ns/ref#dueDate',
  name: 'dueDate',
  label: 'Due Date',
  order: 1,
  group: null,
  hidden: false,
  facet: false,
  indexed: false,
  searchable: false,
  readOnly: false,
  cardinalityMin: 0,
  cardinalityMax: 1,
  datatype: 'DATE',
  derivation: null,
};

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldDate, ReactiveFormsModule],
  template: `<sq-field-date [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = DUE_DATE_PROPERTY;
  control = new FormControl<unknown>(null);
}

function inputEl(fixture: { nativeElement: HTMLElement }): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(
    'input[data-field="dueDate"]',
  );
  if (!el) {
    throw new Error('no input found for data-field="dueDate"');
  }
  return el;
}

function labelText(fixture: { nativeElement: HTMLElement }): string {
  return fixture.nativeElement.querySelector('mat-label')?.textContent?.trim() ?? '';
}

function fieldInstance(fixture: {
  debugElement: import('@angular/core').DebugElement;
}): SqFieldDate {
  const debug = fixture.debugElement.query(By.directive(SqFieldDate));
  if (!debug) {
    throw new Error('no SqFieldDate found');
  }
  return debug.componentInstance as SqFieldDate;
}

describe('SqFieldDate', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  it('renders the label from property().label', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(labelText(fixture)).toBe('Due Date');
  });

  it('renders a mat-form-field containing a native input tagged data-field with the property name', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-form-field')).not.toBeNull();
    expect(inputEl(fixture).getAttribute('data-field')).toBe('dueDate');
  });

  it('renders a mat-datepicker-toggle', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-datepicker-toggle')).not.toBeNull();
  });

  it('setDate writes the wire value, a YYYY-MM-DD string built from local calendar fields', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    const date = new Date(2026, 0, 2, 23, 30);
    fieldInstance(fixture).setDate(date);
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('DATE', date));
    expect(fixture.componentInstance.control.value).toBe('2026-01-02');
    expect(typeof fixture.componentInstance.control.value).toBe('string');
  });

  it('setDate(null) clears the control to null', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('2026-01-02');
    await fixture.whenStable();

    fieldInstance(fixture).setDate(null);
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('an external control.setValue updates the displayed date signal to a matching local Date', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fixture.componentInstance.control.setValue('2026-03-04');
    await fixture.whenStable();

    const displayed = fieldInstance(fixture).date();
    expect(displayed).not.toBeNull();
    expect(displayed?.getFullYear()).toBe(2026);
    expect(displayed?.getMonth()).toBe(2);
    expect(displayed?.getDate()).toBe(4);
  });

  it('an external control.setValue(null) clears the displayed date signal', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('2026-03-04');
    await fixture.whenStable();

    fixture.componentInstance.control.setValue(null);
    await fixture.whenStable();

    expect(fieldInstance(fixture).date()).toBeNull();
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
