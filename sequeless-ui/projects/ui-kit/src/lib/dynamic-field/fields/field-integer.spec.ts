/**
 * Contract under test (lib/dynamic-field/fields/field-integer.ts): `SqFieldInteger` (selector
 * `sq-field-integer`, datatypes `INTEGER` and `LONG`) renders a `mat-form-field` with
 * `<mat-label>` bound to `property().label` and a native
 * `<input matInput type="number" step="1" [formControl]="control()">` tagged
 * `data-field="{{ property().name }}"`, per the dynamic-field field component contract in the
 * phase-5 spec. The control's value is always the wire value — i.e. exactly what
 * `toWireValue(property().datatype, raw)` from `../../util/wire-value` returns — so typing into
 * the input drives the control to a genuine `number` (truncated toward zero for a non-integral
 * value, per the util's own pinned coercion), or `null` when cleared. `LONG` behaves identically
 * to `INTEGER` since both share the same wire coercion. `SqFieldInteger` never adds validators or
 * disables the control; that is the host `sq-dynamic-field`'s job.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { toWireValue } from '../../util/wire-value';
import { SqFieldInteger } from './field-integer';

const PRIORITY_PROPERTY: AttributePropertyResponse = {
  kind: 'attribute',
  iri: 'https://sequeless.dev/ns/ref#priority',
  name: 'priority',
  label: 'Priority',
  order: 1,
  group: null,
  hidden: false,
  facet: false,
  indexed: false,
  searchable: false,
  readOnly: false,
  cardinalityMin: 0,
  cardinalityMax: 1,
  datatype: 'INTEGER',
  derivation: null,
};

const SEQUENCE_NUMBER_PROPERTY: AttributePropertyResponse = {
  ...PRIORITY_PROPERTY,
  iri: 'https://sequeless.dev/ns/ref#sequenceNumber',
  name: 'sequenceNumber',
  label: 'Sequence Number',
  datatype: 'LONG',
};

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldInteger, ReactiveFormsModule],
  template: `<sq-field-integer [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = PRIORITY_PROPERTY;
  control = new FormControl<unknown>(null);
}

@Component({
  selector: 'sq-test-host-long',
  standalone: true,
  imports: [SqFieldInteger, ReactiveFormsModule],
  template: `<sq-field-integer [property]="property" [control]="control" />`,
})
class TestHostLong {
  property: AttributePropertyResponse = SEQUENCE_NUMBER_PROPERTY;
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

describe('SqFieldInteger', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  describe('INTEGER property', () => {
    it('renders the label from property().label', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      expect(labelText(fixture)).toBe('Priority');
    });

    it('renders a native input with type="number" step="1" tagged data-field with the property name', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      const input = inputEl(fixture, 'priority');
      expect(input.getAttribute('type')).toBe('number');
      expect(input.getAttribute('step')).toBe('1');
      expect(input.getAttribute('data-field')).toBe('priority');
    });

    it('sets the control to the wire value, a number, when the user types', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'priority'), '42');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBe(toWireValue('INTEGER', '42'));
      expect(fixture.componentInstance.control.value).toBe(42);
      expect(typeof fixture.componentInstance.control.value).toBe('number');
    });

    it('truncates a non-integral typed value toward zero, matching toWireValue', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'priority'), '7.9');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBe(toWireValue('INTEGER', '7.9'));
      expect(fixture.componentInstance.control.value).toBe(7);
    });

    it('clearing the input sets the control to null, matching toWireValue on empty string', async () => {
      const fixture = TestBed.createComponent(TestHost);
      fixture.componentInstance.control.setValue(42);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'priority'), '');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBe(toWireValue('INTEGER', ''));
      expect(fixture.componentInstance.control.value).toBeNull();
    });

    it('reflects an external control.setValue into the input', async () => {
      const fixture = TestBed.createComponent(TestHost);
      await fixture.whenStable();

      fixture.componentInstance.control.setValue(9);
      await fixture.whenStable();

      expect(inputEl(fixture, 'priority').value).toBe('9');
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

      expect(inputEl(fixture, 'priority').disabled).toBe(true);
    });
  });

  describe('LONG property', () => {
    it('renders the same number input shape as INTEGER, tagged with the LONG property name', async () => {
      const fixture = TestBed.createComponent(TestHostLong);
      await fixture.whenStable();

      const input = inputEl(fixture, 'sequenceNumber');
      expect(input.getAttribute('type')).toBe('number');
      expect(input.getAttribute('step')).toBe('1');
    });

    it('sets the control to the same numeric wire value as INTEGER when the user types', async () => {
      const fixture = TestBed.createComponent(TestHostLong);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'sequenceNumber'), '9007199254740');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBe(
        toWireValue('LONG', '9007199254740'),
      );
      expect(fixture.componentInstance.control.value).toBe(9007199254740);
    });

    it('clearing the input sets the control to null for LONG as well', async () => {
      const fixture = TestBed.createComponent(TestHostLong);
      fixture.componentInstance.control.setValue(5);
      await fixture.whenStable();

      typeInto(inputEl(fixture, 'sequenceNumber'), '');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.value).toBeNull();
    });
  });
});
