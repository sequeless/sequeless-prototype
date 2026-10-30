/*
 * Contract [ONT-PROPFORM] (amended, see the navigator exchange): propertyToForm(p, cardinality) builds a
 * FormGroup with `label`, `min`, `max` (null = unbounded), `derivationKind` ('none' | 'plugin' |
 * 'rollup') and `pluginName`. diffPropertyForm(store, p, cardinality, value) returns one QuadDelta per
 * changed field, using relabel / setCardinality / setDerivation against the store. Datatype and display
 * hints are not editable; rollup derivations can be cleared but not edited.
 */
import { parseOntology, readCardinality, readProperties } from '@sequeless/rdf';
import type { PropertyView, RdfStore } from '@sequeless/rdf';

import { FIXTURE_TTL, SCORE, TITLE, TOTAL } from '../testing/fixture';
import { diffPropertyForm, propertyToForm } from './property-form';
import type { PropertyFormValue } from './property-form';

function load(iri: string): { store: RdfStore; p: PropertyView } {
  const store = parseOntology(FIXTURE_TTL).store;
  return { store, p: readProperties(store).find((x) => x.iri === iri)! };
}

function valueOf(store: RdfStore, p: PropertyView): PropertyFormValue {
  return propertyToForm(p, readCardinality(store, p.domainIri, p.iri)).getRawValue() as PropertyFormValue;
}

describe('propertyToForm', () => {
  it('seeds label, cardinality and derivation from the projections', () => {
    const { store, p } = load(TITLE);
    expect(valueOf(store, p)).toEqual({
      label: 'Title',
      min: 1,
      max: null,
      derivationKind: 'none',
      pluginName: '',
    });
  });

  it('seeds a plugin derivation', () => {
    const { store, p } = load(SCORE);
    expect(valueOf(store, p)).toMatchObject({ derivationKind: 'plugin', pluginName: 'scorer' });
  });

  it('seeds a rollup derivation', () => {
    const { store, p } = load(TOTAL);
    expect(valueOf(store, p).derivationKind).toBe('rollup');
  });

  it('requires a label and a non-negative min', () => {
    const { store, p } = load(TITLE);
    const form = propertyToForm(p, readCardinality(store, p.domainIri, p.iri));
    form.patchValue({ label: '', min: -1 });
    expect(form.controls['label'].invalid).toBe(true);
    expect(form.controls['min'].invalid).toBe(true);
  });

  it('flags max below min on the group', () => {
    const { store, p } = load(TITLE);
    const form = propertyToForm(p, readCardinality(store, p.domainIri, p.iri));
    form.patchValue({ min: 3, max: 2 });
    expect(form.errors).toEqual({ maxBelowMin: true });
    form.patchValue({ max: null });
    expect(form.errors).toBeNull();
  });

  it('requires a plugin name when the derivation is a plugin', () => {
    const { store, p } = load(TITLE);
    const form = propertyToForm(p, readCardinality(store, p.domainIri, p.iri));
    form.patchValue({ derivationKind: 'plugin', pluginName: '' });
    expect(form.controls['pluginName'].invalid).toBe(true);
    form.patchValue({ pluginName: 'x' });
    expect(form.controls['pluginName'].valid).toBe(true);
  });
});

describe('diffPropertyForm', () => {
  it('returns no deltas when nothing changed', () => {
    const { store, p } = load(TITLE);
    expect(diffPropertyForm(store, p, readCardinality(store, p.domainIri, p.iri), valueOf(store, p))).toEqual([]);
  });

  it('relabels', () => {
    const { store, p } = load(TITLE);
    const c = readCardinality(store, p.domainIri, p.iri);
    const deltas = diffPropertyForm(store, p, c, { ...valueOf(store, p), label: 'Name' });
    expect(deltas).toHaveLength(1);
    expect(deltas[0].added[0].object.value).toBe('Name');
  });

  it('sets the cardinality', () => {
    const { store, p } = load(TITLE);
    const c = readCardinality(store, p.domainIri, p.iri);
    const deltas = diffPropertyForm(store, p, c, { ...valueOf(store, p), min: 2, max: 5 });
    expect(deltas).toHaveLength(1);
    for (const d of deltas) store.apply(d);
    expect(readCardinality(store, p.domainIri, p.iri)).toEqual({ min: 2, max: 5 });
  });

  it('switches a plain property to a plugin derivation', () => {
    const { store, p } = load(TITLE);
    const c = readCardinality(store, p.domainIri, p.iri);
    const deltas = diffPropertyForm(store, p, c, { ...valueOf(store, p), derivationKind: 'plugin', pluginName: 'p1' });
    for (const d of deltas) store.apply(d);
    const after = readProperties(store).find((x) => x.iri === TITLE)!;
    expect(after.derivation).toMatchObject({ kind: 'plugin', pluginName: 'p1' });
  });

  it('renames a plugin', () => {
    const { store, p } = load(SCORE);
    const c = readCardinality(store, p.domainIri, p.iri);
    const deltas = diffPropertyForm(store, p, c, { ...valueOf(store, p), pluginName: 'other' });
    for (const d of deltas) store.apply(d);
    expect(readProperties(store).find((x) => x.iri === SCORE)!.derivation).toMatchObject({ pluginName: 'other' });
  });

  it('clears plugin and rollup derivations when set to none', () => {
    for (const iri of [SCORE, TOTAL]) {
      const { store, p } = load(iri);
      const c = readCardinality(store, p.domainIri, p.iri);
      const deltas = diffPropertyForm(store, p, c, { ...valueOf(store, p), derivationKind: 'none', pluginName: '' });
      for (const d of deltas) store.apply(d);
      expect(readProperties(store).find((x) => x.iri === iri)!.derivation).toBeNull();
    }
  });

  it('never edits a rollup into anything else, nor invents one', () => {
    const { store, p } = load(TOTAL);
    const c = readCardinality(store, p.domainIri, p.iri);
    expect(diffPropertyForm(store, p, c, valueOf(store, p))).toEqual([]);
    const plain = load(TITLE);
    const pc = readCardinality(plain.store, plain.p.domainIri, plain.p.iri);
    expect(diffPropertyForm(plain.store, plain.p, pc, { ...valueOf(plain.store, plain.p), derivationKind: 'rollup' })).toEqual([]);
  });
});
