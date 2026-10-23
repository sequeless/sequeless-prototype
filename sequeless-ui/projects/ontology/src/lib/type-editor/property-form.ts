import { FormControl, FormGroup, Validators } from '@angular/forms';
import type { ValidationErrors } from '@angular/forms';
import { relabel, setCardinality, setDerivation } from '@sequeless/rdf';
import type { Cardinality, PropertyView, QuadDelta, RdfStore } from '@sequeless/rdf';

export type DerivationKind = 'none' | 'plugin' | 'rollup';

export interface PropertyFormValue {
  label: string;
  min: number;
  max: number | null;
  derivationKind: DerivationKind;
  pluginName: string;
}

export type PropertyForm = FormGroup<{
  label: FormControl<string>;
  min: FormControl<number>;
  max: FormControl<number | null>;
  derivationKind: FormControl<DerivationKind>;
  pluginName: FormControl<string>;
}>;

function maxAtLeastMin(group: unknown): ValidationErrors | null {
  const { min, max } = (group as PropertyForm).getRawValue();
  return max !== null && max !== undefined && max < min ? { maxBelowMin: true } : null;
}

/** The form's value for `p` as it currently stands in the store. */
export function propertyFormValue(p: PropertyView, cardinality: Cardinality): PropertyFormValue {
  const derivation = p.derivation;
  return {
    label: p.label ?? '',
    min: cardinality.min,
    max: cardinality.max,
    derivationKind: derivation ? derivation.kind : 'none',
    pluginName: derivation?.kind === 'plugin' ? derivation.pluginName : '',
  };
}

export function propertyToForm(p: PropertyView, cardinality: Cardinality): PropertyForm {
  const value = propertyFormValue(p, cardinality);
  const form: PropertyForm = new FormGroup(
    {
      label: new FormControl(value.label, { nonNullable: true, validators: [Validators.required] }),
      min: new FormControl(value.min, {
        nonNullable: true,
        validators: [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)],
      }),
      max: new FormControl<number | null>(value.max, [Validators.min(0), Validators.pattern(/^\d+$/)]),
      derivationKind: new FormControl<DerivationKind>(value.derivationKind, { nonNullable: true }),
      pluginName: new FormControl(value.pluginName, { nonNullable: true }),
    },
    { validators: [maxAtLeastMin] },
  );
  const requirePlugin = () => {
    const needed = form.controls.derivationKind.value === 'plugin';
    form.controls.pluginName.setValidators(needed ? [Validators.required] : []);
    form.controls.pluginName.updateValueAndValidity({ emitEvent: false });
  };
  requirePlugin();
  form.controls.derivationKind.valueChanges.subscribe(requirePlugin);
  return form;
}

/**
 * One delta per changed field, computed against the same store (they touch disjoint quads). Rollup
 * derivations can be cleared but not edited or created here.
 */
export function diffPropertyForm(
  store: RdfStore,
  p: PropertyView,
  cardinality: Cardinality,
  value: PropertyFormValue,
): QuadDelta[] {
  const before = propertyFormValue(p, cardinality);
  const deltas: QuadDelta[] = [];

  const label = value.label.trim();
  if (label !== '' && label !== before.label) {
    deltas.push(relabel(store, p.iri, label));
  }

  if (value.min !== before.min || value.max !== before.max) {
    deltas.push(setCardinality(store, p.domainIri, p.iri, { min: value.min, max: value.max }));
  }

  if (value.derivationKind === 'none' && before.derivationKind !== 'none') {
    deltas.push(setDerivation(store, p.iri, null));
  } else if (value.derivationKind === 'plugin' && value.pluginName.trim() !== '') {
    const pluginName = value.pluginName.trim();
    if (before.derivationKind !== 'plugin' || pluginName !== before.pluginName) {
      deltas.push(setDerivation(store, p.iri, { kind: 'plugin', pluginName }));
    }
  }
  return deltas;
}
