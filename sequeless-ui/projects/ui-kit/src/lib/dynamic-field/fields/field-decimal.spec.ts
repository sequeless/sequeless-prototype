/**
 * Contract under test (lib/dynamic-field/fields/field-decimal.ts): `SqFieldDecimal` (selector
 * `sq-field-decimal`, datatypes `DECIMAL` and `DOUBLE`) renders a `mat-form-field` with
 * `<mat-label>` bound to `property().label` and a native
 * `<input matInput type="number" step="any" [formControl]="control()">` tagged
 * `data-field="{{ property().name }}"`, per the dynamic-field field component contract in the
 * phase-5 spec. The control's value is always the wire value — i.e. exactly what
 * `toWireValue(property().datatype, raw)` from `../../util/wire-value` returns — so typing into
 * the input drives the control to a genuine `number` (not truncated, unlike INTEGER/LONG), or
 * `null` when cleared. `DOUBLE` behaves identically to `DECIMAL` since both share the same wire
 * coercion. `SqFieldDecimal` never adds validators or disables the control; that is the host
 * `sq-dynamic-field`'s job.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse, TypeDetailResponse } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { toWireValue } from '../../util/wire-value';
import { SqFieldDecimal } from './field-decimal';

const PROJECT_DETAIL: TypeDetailResponse = PROJECT_TYPE_DETAIL;
const TOTAL_ESTIMATED_HOURS_PROPERTY = PROJECT_DETAIL.properties.find(
  (p): p is AttributePropertyResponse =>
    p.kind === 'attribute' && p.name === 'totalEstimatedHours',
)!;

const CONFIDENCE_SCORE_PROPERTY: AttributePropertyResponse = {
  kind: 'attribute',
  iri: 'https://sequeless.dev/ns/ref#confidenceScore',
  name: 'confidenceScore',
  label: 'Confidence Score',
  order: 1,
  group: null,
  hidden: false,
  facet: false,
  indexed: false,
  searchable: false,
  readOnly: false,
  cardinalityMin: 0,
  cardinalityMax: 1,
  datatype: 'DOUBLE',
  derivation: null,
};

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldDecimal, ReactiveFormsModule],
  template: `<sq-field-decimal [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = TOTAL_ESTIMATED_HOURS_PROPERTY;
  control = new FormControl<unknown>(null);
}

@Component({
  selector: 'sq-test-host-double',
  standalone: true,
  imports: [SqFieldDecimal, ReactiveFormsModule],
  template: `<sq-field-decimal [property]="property" [control]="control" />`,
})
class TestHostDouble {
  property: AttributePropertyResponse = CONFIDENCE_SCORE_PROPERTY;
  control = new FormControl<unknown>(null);
}

function inputEl(fixture: { nativeElement: HTMLElement }, fieldName: string): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(
    `input[data-field="${fieldName}"]`,
  );
  if (!el) {
    throw new Error(`no input found for data-field="${fieldName}"`);
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

describe('SqFieldDecimal', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  describe('DECIMAL property', () => {
    it('renders the label from property().label', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      expect(labelText(fixture)).toBe('Total Estimated Hours');
    });

    it('renders a native input with type="number" step="any" tagged data-field with the property name', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      const input = inputEl(fixture, 'totalEstimatedHours');
      expect(input.getAttribute('type')).toBe('number');
      expect(input.getAttribute('step')).toBe('any');
      expect(input.getAttribute('data-field')).toBe('totalEstimatedHours');
    });

    it('sets the control to the wire value, a number, when the user types a decimal', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'totalEstimatedHours'), '3.25');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBe(toWireValue('DECIMAL', '3.25'));
      expect(fixture.componentInstance.control.value).toBe(3.25);
      expect(typeof fixture.componentInstance.control.value).toBe('number');
    });

    it('clearing the input sets the control to null, matching toWireValue on empty string', async () => {
      const fixture = TestBed.createComponent(TestHost);
      fixture.componentInstance.control.setValue(3.25);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'totalEstimatedHours'), '');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBe(toWireValue('DECIMAL', ''));
      expect(fixture.componentInstance.control.value).toBeNull();
    });

    it('reflects an external control.setValue into the input', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      fixture.componentInstance.control.setValue(12.5);
      await fixture.whenStable();

      expect(inputEl(fixture, 'totalEstimatedHours').value).toBe('12.5');
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

      expect(inputEl(fixture, 'totalEstimatedHours').disabled).toBe(true);
    });
  });

  describe('DOUBLE property', () => {
    it('renders the same number input shape as DECIMAL, tagged with the DOUBLE property name', async () => {
      const fixture = TestBed.createComponent(TestHostDouble);
      await fixture.whenStable();

      const input = inputEl(fixture, 'confidenceScore');
      expect(input.getAttribute('type')).toBe('number');
      expect(input.getAttribute('step')).toBe('any');
    });

    it('sets the control to the same numeric wire value as DECIMAL when the user types', async () => {
      const fixture = TestBed.createComponent(TestHostDouble);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'confidenceScore'), '3.25');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBe(toWireValue('DOUBLE', '3.25'));
      expect(fixture.componentInstance.control.value).toBe(3.25);
    });

    it('clearing the input sets the control to null for DOUBLE as well', async () => {
      const fixture = TestBed.createComponent(TestHostDouble);
      fixture.componentInstance.control.setValue(0.1);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'confidenceScore'), '');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBeNull();
    });
  });
});
