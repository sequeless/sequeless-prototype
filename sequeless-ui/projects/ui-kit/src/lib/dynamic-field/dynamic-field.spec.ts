/**
 * Contract under test (lib/dynamic-field/dynamic-field.ts): `SqDynamicField` (selector
 * `sq-dynamic-field`) takes a `PropertyResponse` and an `AbstractControl` as required signal
 * inputs and renders, via `NgComponentOutlet` passing `{ property, control }` as inputs, whichever
 * component `{ ...DEFAULT_FIELD_RENDERERS, ...inject(FIELD_RENDERERS, { optional: true }) }` maps
 * to the property's key — `property().datatype` for an `AttributePropertyResponse`, the literal
 * `'RELATIONSHIP'` for a `RelationshipPropertyResponse` — so an app-level `FIELD_RENDERERS`
 * provider overrides the default per key without touching this host (phase-5 spec, "Dynamic
 * fields").
 *
 * `hidden: true` renders nothing at all, regardless of datatype or control shape. `readOnly: true`
 * force-disables the given control (independent of whether it already arrived disabled) and, when
 * `derivation` is non-null, renders a `<mat-hint data-hint="derivation">` with `derivation.summary`
 * as its exact text.
 *
 * When `control()` is a `FormArray` (a repeatable attribute — see `controlFor` in
 * `../util/validators`, which never produces a `FormArray` for a relationship regardless of
 * cardinality), the host renders one renderer instance per array element, each wrapped in a
 * `<div data-index="i">`, plus a `<button data-action="add">` that pushes a new item control onto
 * the array and, per item, a `<button data-action="remove">` that removes just that element.
 */
import { Component } from '@angular/core';
import type { Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { AbstractControl } from '@angular/forms';
import { FormArray, FormControl } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import type {
  AttributePropertyResponse,
  Datatype,
  PropertyResponse,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { FIELD_RENDERERS } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { of } from 'rxjs';
import { RELATIONSHIP_LOOKUP, type RelationshipLookup } from '../relationship-picker/relationship-lookup';
import { controlFor } from '../util/validators';
import { SqDynamicField } from './dynamic-field';

const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;
const findAttribute = (name: string): AttributePropertyResponse => {
  const property = detail.properties.find((p) => p.name === name);
  if (!property || property.kind !== 'attribute') {
    throw new Error(`fixture has no attribute property named "${name}"`);
  }
  return property;
};
const findRelationship = (name: string) => {
  const property = detail.properties.find((p) => p.name === name);
  if (!property || property.kind !== 'relationship') {
    throw new Error(`fixture has no relationship property named "${name}"`);
  }
  return property;
};

const STATUS = findAttribute('status'); // STRING, cardinalityMax 1, not readOnly/hidden
const TITLE = findAttribute('title'); // STRING, cardinalityMax null (repeatable), not readOnly/hidden
const OPEN_TASK_COUNT = findAttribute('openTaskCount'); // INTEGER, readOnly, derivation set, cardinalityMax 1
const CREATED_AT = findAttribute('createdAt'); // DATE_TIME, hidden, readOnly
const OWNER = findRelationship('owner'); // single-valued relationship, targetTypeIri Person

const withDatatype = (datatype: Datatype, name: string): AttributePropertyResponse => ({
  ...STATUS,
  name,
  datatype,
});

const LONG_PROPERTY = withDatatype('LONG', 'longField');
const DECIMAL_PROPERTY = withDatatype('DECIMAL', 'decimalField');
const DOUBLE_PROPERTY = withDatatype('DOUBLE', 'doubleField');
const DATE_PROPERTY = withDatatype('DATE', 'dateField');
const TIME_PROPERTY = withDatatype('TIME', 'timeField');
const DATE_TIME_PROPERTY = withDatatype('DATE_TIME', 'dateTimeField');
const DURATION_PROPERTY = withDatatype('DURATION', 'durationField');
const ANY_URI_PROPERTY = withDatatype('ANY_URI', 'anyUriField');
const BOOLEAN_PROPERTY = withDatatype('BOOLEAN', 'booleanField');
const INTEGER_PROPERTY = withDatatype('INTEGER', 'integerField');

const PERSON_TYPE_DETAIL: TypeDetailResponse = {
  iri: OWNER.targetTypeIri,
  name: 'Person',
  label: 'Person',
  abstract: false,
  superTypes: [],
  group: null,
  hidden: false,
  properties: [],
  stateMachine: null,
};

const fakeLookup: RelationshipLookup = {
  targetType: () => of(PERSON_TYPE_DETAIL),
  search: () => of([]),
};

@Component({
  selector: 'sq-test-string-override',
  standalone: true,
  template: '',
})
class OverrideStringField {
  property!: PropertyResponse;
  control!: AbstractControl;
}

function createFixture(
  property: PropertyResponse,
  control: AbstractControl,
  providers: Provider[] = [],
) {
  TestBed.configureTestingModule({ providers: [provideNoopAnimations(), ...providers] });
  const fixture = TestBed.createComponent(SqDynamicField);
  fixture.componentRef.setInput('property', property);
  fixture.componentRef.setInput('control', control);
  return fixture;
}

describe('SqDynamicField', () => {
  describe('datatype -> renderer resolution', () => {
    it('resolves STRING to sq-field-string', async () => {
      const fixture = createFixture(STATUS, new FormControl<unknown>(null));
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('sq-field-string')).not.toBeNull();
    });

    it('resolves BOOLEAN to sq-field-boolean', async () => {
      const fixture = createFixture(BOOLEAN_PROPERTY, new FormControl<unknown>(null));
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('sq-field-boolean')).not.toBeNull();
    });

    it('resolves INTEGER and LONG to sq-field-integer', async () => {
      const integerFixture = createFixture(INTEGER_PROPERTY, new FormControl<unknown>(null));
      await integerFixture.whenStable();
      expect(integerFixture.nativeElement.querySelector('sq-field-integer')).not.toBeNull();

      const longFixture = createFixture(LONG_PROPERTY, new FormControl<unknown>(null));
      await longFixture.whenStable();
      expect(longFixture.nativeElement.querySelector('sq-field-integer')).not.toBeNull();
    });

    it('resolves DECIMAL and DOUBLE to sq-field-decimal', async () => {
      const decimalFixture = createFixture(DECIMAL_PROPERTY, new FormControl<unknown>(null));
      await decimalFixture.whenStable();
      expect(decimalFixture.nativeElement.querySelector('sq-field-decimal')).not.toBeNull();

      const doubleFixture = createFixture(DOUBLE_PROPERTY, new FormControl<unknown>(null));
      await doubleFixture.whenStable();
      expect(doubleFixture.nativeElement.querySelector('sq-field-decimal')).not.toBeNull();
    });

    it('resolves DATE to sq-field-date', async () => {
      const fixture = createFixture(DATE_PROPERTY, new FormControl<unknown>(null));
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('sq-field-date')).not.toBeNull();
    });

    it('resolves TIME to sq-field-time', async () => {
      const fixture = createFixture(TIME_PROPERTY, new FormControl<unknown>(null));
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('sq-field-time')).not.toBeNull();
    });

    it('resolves DATE_TIME to sq-field-date-time', async () => {
      const fixture = createFixture(DATE_TIME_PROPERTY, new FormControl<unknown>(null));
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('sq-field-date-time')).not.toBeNull();
    });

    it('resolves DURATION to sq-field-duration', async () => {
      const fixture = createFixture(DURATION_PROPERTY, new FormControl<unknown>(null));
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('sq-field-duration')).not.toBeNull();
    });

    it('resolves ANY_URI to sq-field-any-uri', async () => {
      const fixture = createFixture(ANY_URI_PROPERTY, new FormControl<unknown>(null));
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('sq-field-any-uri')).not.toBeNull();
    });

    it('resolves a relationship property to sq-relationship-picker', async () => {
      const fixture = createFixture(OWNER, new FormControl<unknown>(null), [
        { provide: RELATIONSHIP_LOOKUP, useValue: fakeLookup },
      ]);
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('sq-relationship-picker')).not.toBeNull();
    });
  });

  it('a FIELD_RENDERERS override for STRING wins over the default renderer', async () => {
    const fixture = createFixture(STATUS, new FormControl<unknown>(null), [
      { provide: FIELD_RENDERERS, useValue: { STRING: OverrideStringField } },
    ]);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('sq-test-string-override')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('sq-field-string')).toBeNull();
  });

  it('renders nothing when property().hidden is true', async () => {
    const fixture = createFixture(CREATED_AT, new FormControl<unknown>(null));
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent?.trim()).toBe('');
    expect(fixture.nativeElement.querySelector('sq-field-date-time')).toBeNull();
  });

  it('force-disables the control and shows the derivation summary as a hint when readOnly', async () => {
    const control = new FormControl<unknown>(5); // deliberately enabled, to prove the host disables it
    const fixture = createFixture(OPEN_TASK_COUNT, control);
    await fixture.whenStable();

    expect(control.disabled).toBe(true);
    const hint = fixture.nativeElement.querySelector('[data-hint="derivation"]');
    expect(hint?.textContent?.trim()).toBe(
      "count(Task via belongsToProject where status ne 'done')",
    );
    expect(fixture.nativeElement.querySelector('sq-field-integer')).not.toBeNull();
  });

  describe('repeatable attribute (FormArray control)', () => {
    it('renders one renderer per item, each wrapped in a data-index div', async () => {
      const array = controlFor(TITLE, ['Apollo', 'Artemis']) as FormArray;
      const fixture = createFixture(TITLE, array);
      await fixture.whenStable();

      const items = fixture.nativeElement.querySelectorAll('[data-index]');
      expect(items.length).toBe(2);
      expect(items[0].getAttribute('data-index')).toBe('0');
      expect(items[1].getAttribute('data-index')).toBe('1');
      expect(items[0].querySelector('sq-field-string')).not.toBeNull();
      expect(items[1].querySelector('sq-field-string')).not.toBeNull();
    });

    it('data-action="add" pushes a new item control onto the FormArray', async () => {
      const array = controlFor(TITLE, ['Apollo']) as FormArray;
      const fixture = createFixture(TITLE, array);
      await fixture.whenStable();

      const addButton = fixture.nativeElement.querySelector<HTMLButtonElement>(
        'button[data-action="add"]',
      );
      addButton?.click();
      await fixture.whenStable();

      expect(array.length).toBe(2);
    });

    it('data-action="remove" on an item removes just that element from the FormArray', async () => {
      const array = controlFor(TITLE, ['Apollo', 'Artemis']) as FormArray;
      const fixture = createFixture(TITLE, array);
      await fixture.whenStable();

      const removeButton = fixture.nativeElement.querySelector<HTMLButtonElement>(
        '[data-index="0"] button[data-action="remove"]',
      );
      removeButton?.click();
      await fixture.whenStable();

      expect(array.length).toBe(1);
      expect(array.at(0).value).toBe('Artemis');
    });
  });
});
