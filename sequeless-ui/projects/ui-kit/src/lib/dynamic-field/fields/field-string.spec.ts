/**
 * Contract under test (lib/dynamic-field/fields/field-string.ts): `SqFieldString` (selector
 * `sq-field-string`, datatype `STRING`) renders a `mat-form-field` with `<mat-label>` bound to
 * `property().label` and a native `<input matInput type="text" [formControl]="control()">`
 * tagged `data-field="{{ property().name }}"`, per the dynamic-field field component contract in
 * the phase-5 spec. The control's value is always the wire value — i.e. exactly what
 * `toWireValue('STRING', raw)` from `../../util/wire-value` returns — so typing into the input
 * drives the control to a plain string (or `null` when cleared), and an external
 * `control.setValue(...)` is reflected back into the input's displayed value. `SqFieldString`
 * never adds validators or disables the control; that is the host `sq-dynamic-field`'s job.
 */
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type { AttributePropertyResponse, TypeDetailResponse } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { toWireValue } from '../../util/wire-value';
import { SqFieldString } from './field-string';

const PROJECT_DETAIL: TypeDetailResponse = PROJECT_TYPE_DETAIL;
const TITLE_PROPERTY = PROJECT_DETAIL.properties.find(
  (p): p is AttributePropertyResponse => p.kind === 'attribute' && p.name === 'title',
)!;

@Component({
  selector: 'sq-test-host',
  standalone: true,
  imports: [SqFieldString, ReactiveFormsModule],
  template: `<sq-field-string [property]="property" [control]="control" />`,
})
class TestHost {
  property: AttributePropertyResponse = TITLE_PROPERTY;
  control = new FormControl<unknown>(null);
}

function inputEl(fixture: { nativeElement: HTMLElement }): HTMLInputElement {
  const el = fixture.nativeElement.querySelector<HTMLInputElement>('input[data-field="title"]');
  if (!el) {
    throw new Error('no input found for data-field="title"');
  }
  return el;
}

function labelText(fixture: { nativeElement: HTMLElement }): string {
  return fixture.nativeElement.querySelector('mat-label')?.textContent?.trim() ?? '';
}

describe('SqFieldString', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations()],
    });
  });

  it('renders the label from property().label', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    expect(labelText(fixture)).toBe('Title');
  });

  it('renders a native input with type="text" tagged data-field with the property name', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    const input = inputEl(fixture);
    expect(input.getAttribute('type')).toBe('text');
    expect(input.getAttribute('data-field')).toBe('title');
  });

  it('sets the control to the wire value when the user types', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    const input = inputEl(fixture);
    input.value = 'Apollo';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('STRING', 'Apollo'));
    expect(fixture.componentInstance.control.value).toBe('Apollo');
  });

  it('clearing the input sets the control to null, matching toWireValue on empty string', async () => {
    const fixture = TestBed.createComponent(TestHost);
    fixture.componentInstance.control.setValue('Apollo');
    await fixture.whenStable();

    const input = inputEl(fixture);
    input.value = '';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(fixture.componentInstance.control.value).toBe(toWireValue('STRING', ''));
    expect(fixture.componentInstance.control.value).toBeNull();
  });

  it('reflects an external control.setValue into the input', async () => {
    const fixture = TestBed.createComponent(TestHost);
    await fixture.whenStable();

    fixture.componentInstance.control.setValue('X');
    await fixture.whenStable();

    expect(inputEl(fixture).value).toBe('X');
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
