/**
 * Contract under test (lib/dynamic-field/fields/field-duration.ts): `SqFieldDuration` (selector
 * `sq-field-duration`, datatype `DURATION`) renders a `<fieldset>` with
 * `<legend>{{ property().label }}</legend>` — not a `mat-form-field`, since `sq-duration-field`
 * is a composite of plain number inputs, not a single Material control — containing a
 * `<sq-duration-field [formControl]="control()" data-field>` (from
 * `../../duration-field/duration-field`) tagged `data-field="{{ property().name }}"`, per the
 * dynamic-field field component contract in the phase-5 spec.
 *
 * `SqDurationField` is itself a full `ControlValueAccessor`, so binding it directly via
 * `[formControl]` is sufficient for the control's value to always be the wire value — exactly
 * what `toWireValue('DURATION', raw)` from `../../util/wire-value` returns, an ISO-8601
 * `PnDTnHnMn.nS` string (or `null`) — with no extra combination logic in `SqFieldDuration` itself.
 * `SqFieldDuration` never adds validators or disables the control; that is the host
 * `sq-dynamic-field`'s job.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { toWireValue } from '../../util/wire-value';
import { SqFieldDuration } from './field-duration';

const ESTIMATED_EFFORT_PROPERTY: AttributePropertyResponse = {
  kind: 'attribute',
  iri: 'https://sequeless.dev/ns/ref#estimatedEffort',
  name: 'estimatedEffort',
  label: 'Estimated Effort',
  order: 1,
  group: null,
  hidden: false,
  facet: false,
  indexed: false,
  searchable: false,
  readOnly: false,
  cardinalityMin: 0,
  cardinalityMax: 1,
  datatype: 'DURATION',
  derivation: null,
};

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldDuration, ReactiveFormsModule],
  template: `<sq-field-duration [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = ESTIMATED_EFFORT_PROPERTY;
  control = new FormControl<unknown>(null);
}

function durationFieldEl(fixture: { nativeElement: HTMLElement }): HTMLElement {
  const el = fixture.nativeElement.querySelector<HTMLElement>(
    'sq-duration-field[data-field="estimatedEffort"]',
  );
  if (!el) {
    throw new Error('no sq-duration-field found for data-field="estimatedEffort"');
  }
  return el;
}

function partInput(
  fixture: { nativeElement: HTMLElement },
  part: 'days' | 'hours' | 'minutes' | 'seconds',
): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(`input[data-part="${part}"]`);
  if (!el) {
    throw new Error(`no input found for data-part="${part}"`);
  }
  return el;
}

function setPartValue(
  fixture: { nativeElement: HTMLElement },
  part: 'days' | 'hours' | 'minutes' | 'seconds',
  value: string,
): void {
  const input = partInput(fixture, part);
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function legendText(fixture: { nativeElement: HTMLElement }): string {
  return fixture.nativeElement.querySelector('legend')?.textContent?.trim() ?? '';
}

describe('SqFieldDuration', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  it('renders a fieldset with a legend bound to property().label', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('fieldset')).not.toBeNull();
    expect(legendText(fixture)).toBe('Estimated Effort');
  });

  it('does not render a mat-form-field', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-form-field')).toBeNull();
  });

  it('renders a sq-duration-field tagged data-field with the property name', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(durationFieldEl(fixture)).not.toBeNull();
  });

  it('typing 2 into the days part sets the control to the wire value P2D', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    setPartValue(fixture, 'days', '2');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('DURATION', 'P2D'));
    expect(fixture.componentInstance.control.value).toBe('P2D');
  });

  it('an external control.setValue(PT45M) shows 45 in the minutes part', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fixture.componentInstance.control.setValue('PT45M');
    await fixture.whenStable();

    expect(partInput(fixture, 'minutes').value).toBe('45');
  });

  it('clearing every part sets the control to PT0S, matching toWireValue', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('P1DT2H');
    await fixture.whenStable();

    setPartValue(fixture, 'days', '');
    setPartValue(fixture, 'hours', '');
    setPartValue(fixture, 'minutes', '');
    setPartValue(fixture, 'seconds', '');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('DURATION', 'PT0S'));
    expect(fixture.componentInstance.control.value).toBe('PT0S');
  });

  it('reflects an external control.setValue(null) back to all parts empty', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('P1DT2H');
    await fixture.whenStable();

    fixture.componentInstance.control.setValue(null);
    await fixture.whenStable();

    expect(partInput(fixture, 'days').value).toBe('');
    expect(partInput(fixture, 'hours').value).toBe('');
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

  it('leaves a host-disabled control disabled and keeps the part inputs in sync', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('P1DT2H');
    fixture.componentInstance.control.disable();
    await fixture.whenStable();

    expect(partInput(fixture, 'days').disabled).toBe(true);
    expect(partInput(fixture, 'hours').disabled).toBe(true);
    expect(partInput(fixture, 'minutes').disabled).toBe(true);
    expect(partInput(fixture, 'seconds').disabled).toBe(true);
  });
});
