/**
 * Contract under test (lib/dynamic-field/fields/field-date-time.ts): `SqFieldDateTime` (selector
 * `sq-field-date-time`, datatype `DATE_TIME`) renders a `mat-form-field` with `<mat-label>` bound
 * to `property().label`, a `matDatepicker`-backed native input tagged
 * `data-field="{{ property().name }}" data-part="date"`, and a native
 * `<input matInput type="time" step="1">` tagged
 * `data-field="{{ property().name }}" data-part="time"`, per the dynamic-field field component
 * contract in the phase-5 spec. The component provides `provideNativeDateAdapter()` itself, so no
 * date adapter needs wiring in the test.
 *
 * The two parts are combined into a single wire value — the control's value is always exactly
 * what `toWireValue('DATE_TIME', raw)` from `../../util/wire-value` returns, an ISO-8601 instant
 * string built from the *local* date and time components, never a `Date` instance. The control is
 * only non-null once both a date and a time are present; either part alone leaves the control
 * `null`, and clearing either part after both were set clears the control back to `null`. The
 * component exposes the public methods `setDate(date: Date | null)` and
 * `setTime(time: string | null)` (accepting `'HH:mm'` or `'HH:mm:ss'`, defaulting missing seconds
 * to `:00`); typing into the native time input drives the same combination logic as calling
 * `setTime` directly. Mirroring `SqFieldDate`'s own `date` signal, the component also exposes the
 * currently-displayed date back out as a `date` signal/computed (`Date | null`), which an external
 * `control.setValue(iso)` keeps in sync (together with the time input's displayed `HH:mm:ss`
 * value), so the widgets reflect a value set programmatically (e.g. when seeding the form from a
 * `BusinessObjectResponse`).
 *
 * `SqFieldDateTime` never adds validators or disables the control itself; that is the host
 * `sq-dynamic-field`'s job.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse, TypeDetailResponse } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { toWireValue } from '../../util/wire-value';
import { SqFieldDateTime } from './field-date-time';

const PROJECT_DETAIL: TypeDetailResponse = PROJECT_TYPE_DETAIL;
const CREATED_AT_PROPERTY = PROJECT_DETAIL.properties.find(
  (p): p is AttributePropertyResponse => p.kind === 'attribute' && p.name === 'createdAt',
)!;

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldDateTime, ReactiveFormsModule],
  template: `<sq-field-date-time [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = CREATED_AT_PROPERTY;
  control = new FormControl<unknown>(null);
}

function dateInput(fixture: { nativeElement: HTMLElement }): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(
    'input[data-field="createdAt"][data-part="date"]',
  );
  if (!el) {
    throw new Error('no input found for data-field="createdAt"][data-part="date"');
  }
  return el;
}

function timeInput(fixture: { nativeElement: HTMLElement }): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(
    'input[data-field="createdAt"][data-part="time"]',
  );
  if (!el) {
    throw new Error('no input found for data-field="createdAt"][data-part="time"');
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

function fieldInstance(fixture: {
  debugElement: import('@angular/core').DebugElement;
}): SqFieldDateTime {
  const debug = fixture.debugElement.query(By.directive(SqFieldDateTime));
  if (!debug) {
    throw new Error('no SqFieldDateTime found');
  }
  return debug.componentInstance as SqFieldDateTime;
}

describe('SqFieldDateTime', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  it('renders the label from property().label', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(labelText(fixture)).toBe('Created At');
  });

  it('renders a mat-form-field containing the date part input tagged data-field and data-part="date"', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-form-field')).not.toBeNull();
    expect(dateInput(fixture).getAttribute('data-field')).toBe('createdAt');
    expect(dateInput(fixture).getAttribute('data-part')).toBe('date');
  });

  it('renders the time part input with type="time" step="1" tagged data-field and data-part="time"', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    const input = timeInput(fixture);
    expect(input.getAttribute('type')).toBe('time');
    expect(input.getAttribute('step')).toBe('1');
    expect(input.getAttribute('data-field')).toBe('createdAt');
    expect(input.getAttribute('data-part')).toBe('time');
  });

  it('combines setDate and setTime into the wire value, an ISO instant string from local fields', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fieldInstance(fixture).setDate(new Date(2026, 0, 2));
    await fixture.whenStable();
    fieldInstance(fixture).setTime('03:04:05');
    await fixture.whenStable();

    const expected = new Date(2026, 0, 2, 3, 4, 5).toISOString();
    expect(fixture.componentInstance.control.value).toBe(
      toWireValue('DATE_TIME', new Date(2026, 0, 2, 3, 4, 5)),
    );
    expect(fixture.componentInstance.control.value).toBe(expected);
  });

  it('defaults missing seconds to :00 when setTime receives an HH:mm string', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fieldInstance(fixture).setDate(new Date(2026, 0, 2));
    await fixture.whenStable();
    fieldInstance(fixture).setTime('03:04');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(
      new Date(2026, 0, 2, 3, 4, 0).toISOString(),
    );
  });

  it('leaves the control null when only the date part is set', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fieldInstance(fixture).setDate(new Date(2026, 0, 2));
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('leaves the control null when only the time part is set', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fieldInstance(fixture).setTime('03:04:05');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('clears the control back to null when the date part is cleared after both were set', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();
    fieldInstance(fixture).setDate(new Date(2026, 0, 2));
    fieldInstance(fixture).setTime('03:04:05');
    await fixture.whenStable();

    fieldInstance(fixture).setDate(null);
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('clears the control back to null when the time part is cleared after both were set', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();
    fieldInstance(fixture).setDate(new Date(2026, 0, 2));
    fieldInstance(fixture).setTime('03:04:05');
    await fixture.whenStable();

    fieldInstance(fixture).setTime(null);
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('typing into the time input drives the control through the same combination logic as setTime', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();
    fieldInstance(fixture).setDate(new Date(2026, 0, 2));
    await fixture.whenStable();

    typeInto(timeInput(fixture), '03:04:05');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(
      new Date(2026, 0, 2, 3, 4, 5).toISOString(),
    );
  });

  it('clearing the time input by typing an empty string clears the control to null', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();
    fieldInstance(fixture).setDate(new Date(2026, 0, 2));
    fieldInstance(fixture).setTime('03:04:05');
    await fixture.whenStable();

    typeInto(timeInput(fixture), '');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('an external control.setValue(iso) populates the displayed date signal with matching local calendar fields', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();
    const iso = new Date(2026, 0, 2, 3, 4, 5).toISOString();

    fixture.componentInstance.control.setValue(iso);
    await fixture.whenStable();

    const displayed = fieldInstance(fixture).date();
    expect(displayed).not.toBeNull();
    expect(displayed?.getFullYear()).toBe(2026);
    expect(displayed?.getMonth()).toBe(0);
    expect(displayed?.getDate()).toBe(2);
  });

  it('an external control.setValue(iso) populates the time input with the matching local HH:mm:ss', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();
    const iso = new Date(2026, 0, 2, 3, 4, 5).toISOString();

    fixture.componentInstance.control.setValue(iso);
    await fixture.whenStable();

    expect(timeInput(fixture).value).toBe('03:04:05');
  });

  it('an external control.setValue(null) clears both the displayed date signal and the time input', async () => {
    const fixture = TestBed.createComponent(TestHost);
    const iso = new Date(2026, 0, 2, 3, 4, 5).toISOString();
    fixture.componentInstance.control.setValue(iso);
    await fixture.whenStable();

    fixture.componentInstance.control.setValue(null);
    await fixture.whenStable();

    expect(fieldInstance(fixture).date()).toBeNull();
    expect(timeInput(fixture).value).toBe('');
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

  it('leaves a host-disabled control disabled and keeps both native inputs in sync', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.disable();
    await fixture.whenStable();

    expect(dateInput(fixture).disabled).toBe(true);
    expect(timeInput(fixture).disabled).toBe(true);
  });
});
