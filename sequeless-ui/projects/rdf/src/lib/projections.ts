import { DataFactory } from 'n3';
import type { Term } from '@rdfjs/types';
import type { Cardinality, ClassView, PropertyView, StateMachineView } from './model';
import type { RdfStore } from './store';
import { OWL, RDF, RDFS, SQ } from './vocabulary';

const { namedNode } = DataFactory;

/**
 * Reads every named subject asserted as `rdf:type owl:Class` from the default graph and projects it
 * into a {@link ClassView}, following the OWL -> snapshot mapping table in
 * `docs/architecture/sq-vocabulary.md`:
 *
 * - `label` — first `sq:label` literal, else first `rdfs:label` literal, else `null`;
 * - `superClassIris` — the IRI values of every *named* `rdfs:subClassOf` object (blank-node
 *   restriction objects are excluded);
 * - `order` — the integer value of `sq:displayOrder`, or `null` when absent;
 * - `group` — the lexical value of `sq:displayGroup`, or `null`;
 * - `hidden` — `true` when `sq:hidden` is the literal `'true'` or `'1'`;
 * - `abstract` — `true` when `sq:abstract` is the literal `'true'` or `'1'`.
 *
 * The result is sorted by `order` (views without `sq:displayOrder` last), breaking ties by IRI.
 */
export function readClasses(store: RdfStore): ClassView[] {
  const classType = namedNode(OWL.Class);
  const seen = new Set<string>();
  for (const q of store.match(null, namedNode(RDF.type), classType)) {
    if (q.subject.termType === 'NamedNode') {
      seen.add(q.subject.value);
    }
  }

  const views: ClassView[] = [];
  for (const iri of seen) {
    views.push(buildClassView(store, iri));
  }

  views.sort((a, b) => {
    if (a.order !== null && b.order !== null && a.order !== b.order) {
      return a.order - b.order;
    }
    if (a.order !== null && b.order === null) {
      return -1;
    }
    if (a.order === null && b.order !== null) {
      return 1;
    }
    if (a.iri < b.iri) {
      return -1;
    }
    if (a.iri > b.iri) {
      return 1;
    }
    return 0;
  });

  return views;
}

/**
 * Reads the effective `owl:` cardinality restriction a type asserts on a property.
 *
 * A restriction is any blank node `N` for which **all three** of the following hold in the default
 * graph:
 *
 *   1. `(typeIri rdfs:subClassOf N)`;
 *   2. `(N rdf:type owl:Restriction)`;
 *   3. `(N owl:onProperty propertyIri)`.
 *
 * Across every matching `N`, the projection combines `owl:cardinality` (which pins both `min` and
 * `max`), `owl:minCardinality` and `owl:maxCardinality`: the effective `min` is the maximum of any
 * `min` bound seen, and the effective `max` is the minimum of any `max` bound seen (or `null`, i.e.
 * unbounded, when none). When no restriction node matches, the default is `{ min: 0, max: null }`.
 */
export function readCardinality(
  store: RdfStore,
  typeIri: string,
  propertyIri: string,
): Cardinality {
  const typeSubject = namedNode(typeIri);
  const propertySubject = namedNode(propertyIri);
  const restrictionType = namedNode(OWL.Restriction);
  const onPropertyPred = namedNode(OWL.onProperty);
  const subClassOfPred = namedNode(RDFS.subClassOf);

  let min = 0;
  let max: number | null = null;

  for (const q of store.match(typeSubject, subClassOfPred, null)) {
    if (q.object.termType !== 'BlankNode') {
      continue;
    }
    const node = q.object;
    if (store.match(node, namedNode(RDF.type), restrictionType).length === 0) {
      continue;
    }
    if (store.match(node, onPropertyPred, propertySubject).length === 0) {
      continue;
    }

    for (const card of store.match(node, namedNode(OWL.cardinality), null)) {
      const v = parseNonNegativeInteger(card.object.value);
      min = Math.max(min, v);
      max = max === null ? v : Math.min(max, v);
    }
    for (const card of store.match(node, namedNode(OWL.minCardinality), null)) {
      const v = parseNonNegativeInteger(card.object.value);
      min = Math.max(min, v);
    }
    for (const card of store.match(node, namedNode(OWL.maxCardinality), null)) {
      const v = parseNonNegativeInteger(card.object.value);
      max = max === null ? v : Math.min(max, v);
    }
  }

  return { min, max };
}

/** Builds the {@link ClassView} for a single `owl:Class` subject. */
function buildClassView(store: RdfStore, iri: string): ClassView {
  const subject = namedNode(iri);

  const label =
    firstLiteral(store, subject, namedNode(SQ.label)) ??
    firstLiteral(store, subject, namedNode(RDFS.label));

  const superClassIris: string[] = [];
  for (const q of store.match(subject, namedNode(RDFS.subClassOf), null)) {
    if (q.object.termType === 'NamedNode') {
      superClassIris.push(q.object.value);
    }
  }

  const orderRaw = firstLiteral(store, subject, namedNode(SQ.displayOrder));
  const order = orderRaw === null ? null : parseNonNegativeInteger(orderRaw);

  const group = firstLiteral(store, subject, namedNode(SQ.displayGroup));

  const hiddenRaw = firstLiteral(store, subject, namedNode(SQ.hidden));
  const hidden = hiddenRaw === 'true' || hiddenRaw === '1';

  const abstractRaw = firstLiteral(store, subject, namedNode(SQ.abstract));
  const abstract = abstractRaw === 'true' || abstractRaw === '1';

  return {
    iri,
    label,
    abstract,
    superClassIris,
    order,
    group,
    hidden,
  };
}

/**
 * Returns the lexical value of the first `rdf:Literal` object matching `(subject, predicate, ?)` in
 * the store's default graph, or `null` when no such literal is present.
 */
export function firstLiteral(store: RdfStore, subject: Term, predicate: Term): string | null {
  for (const q of store.match(subject, predicate, null)) {
    if (q.object.termType === 'Literal') {
      return q.object.value;
    }
  }
  return null;
}

/**
 * Parses a numeric RDF literal's lexical value as an integer. The store contract only ever asserts
 * non-negative integers for the `sq:` vocabulary's numeric hints, so a value that fails `parseInt`
 * is treated as `0` rather than throwing (matching the "default to 0" stance the snapshot layer
 * takes on unparseable cardinality literals).
 */
function parseNonNegativeInteger(value: string): number {
  const n = parseInt(value, 10);
  return Number.isFinite(n) ? n : 0;
}

/**
 * Placeholder for the attribute/relationship property projection. Implemented in a follow-up task
 * (B6b). The stub returns an empty array so the spec's top-level describe callback does not throw at
 * collection time — the individual `it` assertions inside `describe('readProperties', ...)` will fail
 * until the real implementation lands, which is the expected state after task B6a.
 */
export function readProperties(_store: RdfStore): PropertyView[] {
  return [];
}

/**
 * Placeholder for the `sq:StateMachine` projection. Implemented in a follow-up task (B6c). The stub
 * returns `null` for the "no machine" case (which the spec asserts for most types) so the describe
 * callback does not throw at collection time; the state-machine-specific assertions will fail until
 * the real implementation lands, which is the expected state after task B6a.
 */
export function readStateMachine(_store: RdfStore, _typeIri: string): StateMachineView | null {
  return null;
}
