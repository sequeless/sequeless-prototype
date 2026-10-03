/**
 * Pure form-control factories derived from a wire `PropertyResponse`:
 *
 * - `validatorsFor(property)` maps the cardinality/datatype onto `ValidatorFn`s:
 *   `cardinalityMin >= 1` contributes `Validators.required`, an attribute of datatype
 *   `INTEGER`/`LONG` contributes an `'integer'`-keyed validator, and an attribute of datatype
 *   `ANY_URI` contributes a `'url'`-keyed validator. Relationships and every other datatype
 *   contribute nothing beyond the cardinality check.
 * - `isRepeatable(property)` is `cardinalityMax === null || cardinalityMax > 1`.
 * - `controlFor(property, wire)` builds the control: single-valued properties (of either kind)
 *   and repeatable relationships are `FormControl`s (the repeatable relationship holding the
 *   bare array of ids); a repeatable attribute is a `FormArray` of per-item `FormControl`s that
 *   carry only the item validators while the array itself reports `'required'` when empty and
 *   `cardinalityMin >= 1`. `readOnly: true` disables the resulting control without changing its
 *   value.
 */
import { FormArray, FormControl, type AbstractControl, type ValidatorFn, Validators } from '@angular/forms';
import { isAttributeProperty, type PropertyResponse } from '@sequeless/core-api';

const INTEGERY: ReadonlySet<string> = new Set(['INTEGER', 'LONG']);

/**
 * Datatype-specific validators for an attribute, excluding the cardinality check: an
 * `'integer'` flag for `INTEGER`/`LONG` and a `'url'` flag for `ANY_URI`; empty otherwise.
 * Both validators let `null`/`undefined` through, so they are safe on optional items.
 */
function itemValidatorsFor(property: PropertyResponse): ValidatorFn[] {
  if (!isAttributeProperty(property)) {
    return [];
  }
  if (INTEGERY.has(property.datatype)) {
    return [(control): { integer: true } | null =>
      control.value != null && !Number.isInteger(control.value) ? { integer: true } : null];
  }
  if (property.datatype === 'ANY_URI') {
    return [(control): { url: true } | null =>
      !isWellFormedUri(control.value) ? { url: true } : null];
  }
  return [];
}

/**
 * `null`/`undefined` passes (absence is not an invalid URI); otherwise the value must be a
 * string that `new URL()` parses or that carries at least one scheme (`urn:isbn:123`),
 * so scheme-bearing URIs outside http(s) are accepted while free text is not.
 */
function isWellFormedUri(value: unknown): boolean {
  if (value == null) {
    return true;
  }
  if (typeof value !== 'string') {
    return false;
  }
  try {
    // eslint-disable-next-line no-new
    new URL(value);
    return true;
  } catch {
    return /^[A-Za-z][A-Za-z0-9+.-]*:/.test(value);
  }
}

/** Reactive-Forms validators for `property`: required (when mandatory) plus the item validators. */
export function validatorsFor(property: PropertyResponse): ValidatorFn[] {
  const validators = property.cardinalityMin >= 1 ? [Validators.required] : [];
  return validators.concat(itemValidatorsFor(property));
}

/** A property is repeatable when unbounded or bounded above by more than one. */
export function isRepeatable(property: PropertyResponse): boolean {
  return property.cardinalityMax === null || property.cardinalityMax > 1;
}

/** `Validators.required` restated as a named helper shared by single controls and arrays. */
const requiredWhenMinAtLeastOne = (property: PropertyResponse): ValidatorFn[] =>
  property.cardinalityMin >= 1 ? [Validators.required] : [];

/**
 * Builds the actual control for `property` seeded from `wire`:
 * - repeatable attribute → `FormArray` of `FormControl`s with the item validators;
 * - everything else → a single `FormControl` (repeatable relationships hold the id array);
 * - `readOnly` disables the result without touching the value.
 */
export function controlFor(property: PropertyResponse, wire?: unknown): AbstractControl {
  const attribute = isAttributeProperty(property) ? property : null;
  let control: AbstractControl;

  if (attribute !== null && isRepeatable(attribute)) {
    const items = Array.isArray(wire) ? [...wire] : [];
    const itemValidators = itemValidatorsFor(attribute);
    control = new FormArray(
      items.map((item) => new FormControl(item, { validators: itemValidators })),
      { validators: requiredWhenMinAtLeastOne(attribute) },
    );
  } else {
    const isRelationship = attribute === null;
    const value = isRelationship && isRepeatable(property) && wire === undefined
      ? []
      : (wire === undefined ? null : wire);
    control = new FormControl(value, { validators: validatorsFor(property) });
  }

  if (property.readOnly) {
    control.disable();
  }
  return control;
}
