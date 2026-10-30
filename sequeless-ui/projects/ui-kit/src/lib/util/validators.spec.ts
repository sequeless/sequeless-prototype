/**
 * Contract under test (lib/util/validators.ts): `validatorsFor(property)` derives Reactive-Forms
 * `ValidatorFn[]` from a `PropertyResponse`'s cardinality/datatype, independent of how the control
 * built from them is shaped — `cardinalityMin >= 1` always contributes `Validators.required`; an
 * `AttributePropertyResponse` additionally contributes an `'integer'`-keyed validator for
 * `INTEGER`/`LONG` and a `'url'`-keyed validator for `ANY_URI`; every other datatype, and every
 * `RelationshipPropertyResponse`, contributes nothing beyond the cardinality check.
 *
 * `isRepeatable(property)` is the single source of truth the dynamic-field table (phase-5 spec)
 * uses to decide between a single control and a list: `cardinalityMax === null || cardinalityMax >
 * 1`.
 *
 * `controlFor(property, wire)` builds the actual control: a single-valued property (of either
 * kind) is always a `FormControl`. A repeatable `AttributePropertyResponse` is a `FormArray` of
 * per-element `FormControl`s, each carrying only the *item* validators (never `required` — an
 * individual array slot isn't itself mandatory), while the array's own `required` error reflects
 * emptiness against `cardinalityMin`. A repeatable `RelationshipPropertyResponse`, by contrast,
 * stays a single `FormControl` whose value is the bare array of target ids — matching the
 * dynamic-field table's rule that relationships always emit id(s), never a per-slot form
 * structure. `readOnly: true` disables whatever control shape results, without touching its value.
 */
import { FormArray, FormControl } from '@angular/forms';
import {
  isAttributeProperty,
  isRelationshipProperty,
  type AttributePropertyResponse,
  type RelationshipPropertyResponse,
  type TypeDetailResponse,
} from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { controlFor, isRepeatable, validatorsFor } from './validators';

const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;
const findAttribute = (name: string): AttributePropertyResponse => {
  const property = detail.properties.find((p) => p.name === name);
  if (!property || !isAttributeProperty(property)) {
    throw new Error(`fixture has no attribute property named "${name}"`);
  }
  return property;
};
const findRelationship = (name: string): RelationshipPropertyResponse => {
  const property = detail.properties.find((p) => p.name === name);
  if (!property || !isRelationshipProperty(property)) {
    throw new Error(`fixture has no relationship property named "${name}"`);
  }
  return property;
};

const STATUS = findAttribute('status'); // attribute, STRING, cardinalityMax 1, cardinalityMin 0
const TITLE = findAttribute('title'); // attribute, STRING, cardinalityMax null, cardinalityMin 0
const OPEN_TASK_COUNT = findAttribute('openTaskCount'); // attribute, INTEGER, readOnly, cardinalityMax 1
const OWNER = findRelationship('owner'); // relationship, cardinalityMax 1, cardinalityMin 0
const HAS_TASK = findRelationship('hasTask'); // relationship, cardinalityMax null, cardinalityMin 0

const requiredString: AttributePropertyResponse = {
  ...STATUS,
  name: 'requiredString',
  cardinalityMin: 1,
};

const optionalInteger: AttributePropertyResponse = {
  ...STATUS,
  name: 'optionalInteger',
  datatype: 'INTEGER',
  cardinalityMin: 0,
};

const optionalUrl: AttributePropertyResponse = {
  ...STATUS,
  name: 'optionalUrl',
  datatype: 'ANY_URI',
  cardinalityMin: 0,
};

const repeatableRequiredInteger: AttributePropertyResponse = {
  ...TITLE,
  name: 'repeatableRequiredInteger',
  datatype: 'INTEGER',
  cardinalityMin: 1,
  cardinalityMax: null,
};

const repeatableUrl: AttributePropertyResponse = {
  ...TITLE,
  name: 'repeatableUrl',
  datatype: 'ANY_URI',
  cardinalityMin: 0,
  cardinalityMax: null,
};

const requiredRelationship: RelationshipPropertyResponse = {
  ...OWNER,
  name: 'requiredRelationship',
  cardinalityMin: 1,
};

const requiredRepeatableRelationship: RelationshipPropertyResponse = {
  ...HAS_TASK,
  name: 'requiredRepeatableRelationship',
  cardinalityMin: 1,
};

const readOnlyRepeatableInteger: AttributePropertyResponse = {
  ...repeatableRequiredInteger,
  name: 'readOnlyRepeatableInteger',
  readOnly: true,
};

function errorsFor(
  validators: ReturnType<typeof validatorsFor>,
  value: unknown,
): Record<string, unknown> | null {
  const control = new FormControl(value, { validators });
  return control.errors;
}

describe('validatorsFor', () => {
  it('contributes a required validator when cardinalityMin >= 1', () => {
    expect(errorsFor(validatorsFor(requiredString), null)?.['required']).toBe(true);
  });

  it('contributes no required validator when cardinalityMin is 0', () => {
    expect(errorsFor(validatorsFor(STATUS), null)).toBeNull();
  });

  it('flags a non-integral value on an INTEGER/LONG attribute with an "integer" error', () => {
    expect(errorsFor(validatorsFor(optionalInteger), 2.5)?.['integer']).toBe(true);
    expect(errorsFor(validatorsFor({ ...optionalInteger, datatype: 'LONG' }), 2.5)?.['integer']).toBe(
      true,
    );
  });

  it('passes an integral value or null through an INTEGER/LONG validator unflagged', () => {
    expect(errorsFor(validatorsFor(optionalInteger), 2)).toBeNull();
    expect(errorsFor(validatorsFor(optionalInteger), null)).toBeNull();
  });

  it('flags a non-URL value on an ANY_URI attribute with a "url" error', () => {
    expect(errorsFor(validatorsFor(optionalUrl), 'not a url')?.['url']).toBe(true);
  });

  it('passes well-formed URIs or null through an ANY_URI validator unflagged', () => {
    expect(errorsFor(validatorsFor(optionalUrl), 'https://example.org/x')).toBeNull();
    expect(errorsFor(validatorsFor(optionalUrl), 'urn:isbn:123')).toBeNull();
    expect(errorsFor(validatorsFor(optionalUrl), null)).toBeNull();
  });

  it('contributes no datatype-specific validators for STRING or relationship properties', () => {
    expect(errorsFor(validatorsFor(STATUS), 'anything')).toBeNull();
    expect(errorsFor(validatorsFor(OWNER), 'anything')).toBeNull();
  });
});

describe('isRepeatable', () => {
  it('is false when cardinalityMax is 1', () => {
    expect(isRepeatable(STATUS)).toBe(false);
  });

  it('is true when cardinalityMax is greater than 1', () => {
    expect(isRepeatable({ ...STATUS, cardinalityMax: 3 })).toBe(true);
  });

  it('is true when cardinalityMax is null', () => {
    expect(isRepeatable(TITLE)).toBe(true);
  });
});

describe('controlFor', () => {
  describe('single-valued attribute', () => {
    it('builds a FormControl seeded from the wire value', () => {
      const control = controlFor(STATUS, 'active');
      expect(control).toBeInstanceOf(FormControl);
      expect(control.value).toBe('active');
    });

    it('defaults an absent wire value to null', () => {
      expect(controlFor(STATUS, undefined).value).toBeNull();
    });

    it('applies validatorsFor to the control', () => {
      expect(controlFor(requiredString).errors?.['required']).toBe(true);
    });
  });

  describe('repeatable attribute', () => {
    it('builds a FormArray with one FormControl per wire element', () => {
      const control = controlFor(repeatableRequiredInteger, [1, 2]);
      expect(control).toBeInstanceOf(FormArray);
      const array = control as FormArray;
      expect(array.length).toBe(2);
      expect(array.at(0).value).toBe(1);
      expect(array.at(1).value).toBe(2);
    });

    it('builds an empty FormArray when the wire value is absent', () => {
      const array = controlFor(repeatableRequiredInteger, undefined) as FormArray;
      expect(array.length).toBe(0);
    });

    it('applies item validators to each element but not "required"', () => {
      const array = controlFor(repeatableUrl, ['not a url']) as FormArray;
      expect(array.at(0).errors?.['url']).toBe(true);
      expect(array.at(0).errors?.['required']).toBeUndefined();
    });

    it('reports "required" on the array itself when empty and cardinalityMin >= 1', () => {
      const array = controlFor(repeatableRequiredInteger, undefined) as FormArray;
      expect(array.errors?.['required']).toBe(true);
    });

    it('reports no "required" error on the array once it has elements', () => {
      const array = controlFor(repeatableRequiredInteger, [1]) as FormArray;
      expect(array.errors?.['required']).toBeUndefined();
    });
  });

  describe('relationship', () => {
    it('builds a single-valued FormControl seeded from the wire value', () => {
      const control = controlFor(OWNER, 'person-1');
      expect(control).toBeInstanceOf(FormControl);
      expect(control.value).toBe('person-1');
    });

    it('defaults an absent single-valued wire value to null', () => {
      expect(controlFor(OWNER, undefined).value).toBeNull();
    });

    it('builds a FormControl (not a FormArray) holding the array of ids for a repeatable relationship', () => {
      const control = controlFor(HAS_TASK, ['task-1', 'task-2']);
      expect(control).toBeInstanceOf(FormControl);
      expect(control.value).toEqual(['task-1', 'task-2']);
    });

    it('defaults an absent repeatable relationship wire value to an empty array', () => {
      expect(controlFor(HAS_TASK, undefined).value).toEqual([]);
    });

    it('reports "required" for a null single-valued relationship when cardinalityMin >= 1', () => {
      expect(controlFor(requiredRelationship, undefined).errors?.['required']).toBe(true);
      expect(controlFor(requiredRelationship, 'person-1').errors).toBeNull();
    });

    it('reports "required" for an empty repeatable relationship when cardinalityMin >= 1', () => {
      expect(controlFor(requiredRepeatableRelationship, undefined).errors?.['required']).toBe(true);
      expect(controlFor(requiredRepeatableRelationship, ['task-1']).errors).toBeNull();
    });
  });

  describe('readOnly', () => {
    it('disables a single-valued control while keeping its wire value', () => {
      const control = controlFor(OPEN_TASK_COUNT, 5);
      expect(control.disabled).toBe(true);
      expect(control.value).toBe(5);
    });

    it('disables a repeatable attribute FormArray while keeping its wire values', () => {
      const array = controlFor(readOnlyRepeatableInteger, [1, 2]) as FormArray;
      expect(array.disabled).toBe(true);
      expect(array.value).toEqual([1, 2]);
    });
  });
});
