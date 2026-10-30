/**
 * Contract under test (lib/dynamic-field/fields/field-any-uri.ts): `SqFieldAnyUri` (selector
 * `sq-field-any-uri`, datatype `ANY_URI`) renders a `mat-form-field` with `<mat-label>` bound to
 * `property().label` and a native `<input matInput type="url" [formControl]="control()">` tagged
 * `data-field="{{ property().name }}"`, per the dynamic-field field component contract in the
 * phase-5 spec. The control's value is always the wire value — i.e. exactly what
 * `toWireValue('ANY_URI', raw)` from `../../util/wire-value` returns — so typing into the input
 * drives the control to a plain string (or `null` when cleared), and an external
 * `control.setValue(...)` is reflected back into the input's displayed value. `SqFieldAnyUri`
 * never adds validators or disables the control itself — that is the host `sq-dynamic-field`'s
 * job, via `controlFor`/`validatorsFor` from `../../util/validators` — but the component still
 * surfaces whatever `'url'` error such an externally-attached validator produces as a `mat-error`
 * once the control is touched, so a control built by `controlFor` for an `ANY_URI` property shows
 * a validation message once the user blurs an invalid value.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { toWireValue } from '../../util/wire-value';
import { controlFor } from '../../util/validators';
import { SqFieldAnyUri } from './field-any-uri';

const WEBSITE_PROPERTY: AttributePropertyResponse = {
  kind: 'attribute',
  iri: 'https://sequeless.dev/ns/ref#website',
  name: 'website',
  label: 'Website',
  order: 1,
  group: null,
  hidden: false,
  facet: false,
  indexed: false,
  searchable: false,
  readOnly: false,
  cardinalityMin: 0,
  cardinalityMax: 1,
  datatype: 'ANY_URI',
  derivation: null,
};

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldAnyUri, ReactiveFormsModule],
  template: `<sq-field-any-uri [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = WEBSITE_PROPERTY;
  control = new FormControl<unknown>(null);
}

@Component({
  selector: 'sq-test-host-validated',
  standalone: true,
  imports: [SqFieldAnyUri, ReactiveFormsModule],
  template: `<sq-field-any-uri [property]="property" [control]="control" />`,
})
class TestHostValidated {
  property: AttributePropertyResponse = WEBSITE_PROPERTY;
  control = controlFor(WEBSITE_PROPERTY) as FormControl<unknown>;
}

function inputEl(fixture: { nativeElement: HTMLElement }): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>(
    'input[data-field="website"]',
  );
  if (!el) {
    throw new Error('no input found for data-field="website"');
  }
  return el;
}

function labelText(fixture: { nativeElement: HTMLElement }): string {
  return fixture.nativeElement.querySelector('mat-label')?.textContent?.trim() ?? '';
}

function errorText(fixture: { nativeElement: HTMLElement }): string {
  return fixture.nativeElement.querySelector('mat-error')?.textContent?.trim() ?? '';
}

function typeInto(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('SqFieldAnyUri', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  it('renders the label from property().label', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(labelText(fixture)).toBe('Website');
  });

  it('renders a native input with type="url" tagged data-field with the property name', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    const input = inputEl(fixture);
    expect(input.getAttribute('type')).toBe('url');
    expect(input.getAttribute('data-field')).toBe('website');
  });

  it('sets the control to the wire value when the user types a URI', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    typeInto(inputEl(fixture), 'https://example.org/a');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(
      toWireValue('ANY_URI', 'https://example.org/a'),
    );
    expect(fixture.componentInstance.control.value).toBe('https://example.org/a');
  });

  it('clearing the input sets the control to null, matching toWireValue on empty string', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('https://example.org/a');
    await fixture.whenStable();

    typeInto(inputEl(fixture), '');
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('ANY_URI', ''));
    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('reflects an external control.setValue into the input', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fixture.componentInstance.control.setValue('https://example.org/x');
    await fixture.whenStable();

    expect(inputEl(fixture).value).toBe('https://example.org/x');
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

  describe('with a controlFor-built validator', () => {
    it('shows no mat-error before the invalid value is touched', async () => {
      const fixture = TestBed.createComponent(TestHostValidated);
      await fixture.whenStable();

      typeInto(inputEl(fixture), 'nope');
      await fixture.whenStable();

      expect(fixture.nativeElement.querySelector('mat-error')).toBeNull();
    });

    it("shows a mat-error containing 'valid URI' once an invalid value is typed and the input is blurred", async () => {
      const fixture = TestBed.createComponent(TestHostValidated);
      await fixture.whenStable();

      typeInto(inputEl(fixture), 'nope');
      inputEl(fixture).dispatchEvent(new Event('blur'));
      await fixture.whenStable();

      expect(fixture.componentInstance.control.invalid).toBe(true);
      expect(errorText(fixture)).toContain('valid URI');
    });

    it('clears the mat-error once a well-formed URI replaces the invalid value', async () => {
      const fixture = TestBed.createComponent(TestHostValidated);
      await fixture.whenStable();
      typeInto(inputEl(fixture), 'nope');
      inputEl(fixture).dispatchEvent(new Event('blur'));
      await fixture.whenStable();
      expect(errorText(fixture)).toContain('valid URI');

      typeInto(inputEl(fixture), 'https://example.org/a');
      await fixture.whenStable();

      expect(fixture.componentInstance.control.valid).toBe(true);
      expect(fixture.nativeElement.querySelector('mat-error')).toBeNull();
    });
  });
});
