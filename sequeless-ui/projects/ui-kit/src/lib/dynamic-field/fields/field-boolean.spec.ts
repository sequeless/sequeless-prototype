/**
 * Contract under test (lib/dynamic-field/fields/field-boolean.ts): `SqFieldBoolean` (selector
 * `sq-field-boolean`, datatype `BOOLEAN`) renders a `mat-slide-toggle` — no `mat-form-field`,
 * unlike every other field component — whose projected text is `property().label` and which
 * carries `data-field="{{ property().name }}"` on the `mat-slide-toggle` element itself, per the
 * dynamic-field field component contract in the phase-5 spec. The control's value is always the
 * wire value — i.e. exactly what `toWireValue('BOOLEAN', raw)` from `../../util/wire-value`
 * returns — so clicking the toggle's inner switch button flips `control.value` through the
 * sequence null/false -> true -> false, always as a genuine boolean, never the strings `'true'`/
 * `'false'`. An external `control.setValue(true)` checks the toggle; `SqFieldBoolean` never adds
 * validators or disables the control itself; that is the host `sq-dynamic-field`'s job.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse } from '@sequeless/core-api';
import { toWireValue } from '../../util/wire-value';
import { SqFieldBoolean } from './field-boolean';

const URGENT_PROPERTY: AttributePropertyResponse = {
  kind: 'attribute',
  iri: 'https://sequeless.dev/ns/ref#urgent',
  name: 'urgent',
  label: 'Urgent',
  order: 1,
  group: null,
  hidden: false,
  facet: false,
  indexed: false,
  searchable: false,
  readOnly: false,
  cardinalityMin: 0,
  cardinalityMax: 1,
  datatype: 'BOOLEAN',
  derivation: null,
};

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldBoolean, ReactiveFormsModule],
  template: `<sq-field-boolean [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = URGENT_PROPERTY;
  control = new FormControl<unknown>(null);
}

function toggleEl(fixture: { nativeElement: HTMLElement }): HTMLElement {
  const el = fixture.nativeElement.querySelector<HTMLElement>(
    'mat-slide-toggle[data-field="urgent"]',
  );
  if (!el) {
    throw new Error('no mat-slide-toggle found for data-field="urgent"');
  }
  return el;
}

function switchButton(fixture: { nativeElement: HTMLElement }): HTMLButtonElement {
  const el = toggleEl(fixture).querySelector<HTMLButtonElement>('button[role="switch"]');
  if (!el) {
    throw new Error('no switch button found inside mat-slide-toggle');
  }
  return el;
}

describe('SqFieldBoolean', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  it('renders a mat-slide-toggle whose text is the label from property().label', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(toggleEl(fixture).textContent?.trim()).toBe('Urgent');
  });

  it('tags the mat-slide-toggle with data-field set to the property name', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(toggleEl(fixture).getAttribute('data-field')).toBe('urgent');
  });

  it('does not render a mat-form-field', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-form-field')).toBeNull();
  });

  it('starts unchecked when the control value is null', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(switchButton(fixture).getAttribute('aria-checked')).toBe('false');
  });

  it('clicking the switch from a null control sets control.value to true, as a boolean', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    switchButton(fixture).click();
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('BOOLEAN', true));
    expect(fixture.componentInstance.control.value).toBe(true);
  });

  it('clicking the switch again flips control.value to false, never the string "false"', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    switchButton(fixture).click();
    await fixture.whenStable();
    switchButton(fixture).click();
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(false);
    expect(typeof fixture.componentInstance.control.value).toBe('boolean');
  });

  it('clicking the switch from a false control flips control.value to true', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue(false);
    await fixture.whenStable();

    switchButton(fixture).click();
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(true);
  });

  it('reflects an external control.setValue(true) by checking the toggle', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fixture.componentInstance.control.setValue(true);
    await fixture.whenStable();

    expect(switchButton(fixture).getAttribute('aria-checked')).toBe('true');
  });

  it('reflects an external control.setValue(false) by unchecking the toggle', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue(true);
    await fixture.whenStable();

    fixture.componentInstance.control.setValue(false);
    await fixture.whenStable();

    expect(switchButton(fixture).getAttribute('aria-checked')).toBe('false');
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

  it('leaves a host-disabled control disabled and keeps the switch button in sync', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.disable();
    await fixture.whenStable();

    expect(switchButton(fixture).disabled).toBe(true);
  });
});
