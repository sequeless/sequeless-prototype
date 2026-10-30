import { DataFactory } from 'n3';
import type { Term } from '@rdfjs/types';
import type {
  AttributePropertyView,
  Cardinality,
  ClassView,
  DerivationView,
  PropertyView,
  RelationshipPropertyView,
  StateMachineView,
  StateView,
  TransitionView,
  TriggerView,
} from './model';
import type { RdfStore } from './store';
import { OWL, RDF, RDFS, SQ, xsdToDatatype } from './vocabulary';

const { namedNode } = DataFactory;

// RFC 3023 RDF list container markers, needed to read the `sq:filter` collection on a
// `sq:Rollup` derivation node. Kept locally because the `RDF` table in `vocabulary.ts` does
// not (and must not, per its "isolatedModules: only the vocabulary" scope) carry list verbs.
const RDF_FIRST = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#first';
const RDF_REST = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#rest';
const RDF_NIL = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#nil';

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
 * Reads every named subject asserted as `owl:DatatypeProperty` (kind `'attribute'`) or
 * `owl:ObjectProperty` (kind `'relationship'`) from the default graph and projects it into a
 * {@link PropertyView}, following the OWL -> snapshot mapping table in
 * `docs/architecture/sq-vocabulary.md`:
 *
 * - `label` — first `sq:label` literal, else first `rdfs:label` literal, else `null`;
 * - `domainIri` — the first *named* `rdfs:domain` object;
 * - `datatype` (attribute) — {@link xsdToDatatype} of the first named `rdfs:range` object;
 * - `targetTypeIri` (relationship) — the first named `rdfs:range` object;
 * - `inverseIri` (relationship) — the first named `owl:inverseOf` object, or `null`;
 * - `transitive` (relationship) — `true` when the subject is also typed `owl:TransitiveProperty`;
 * - `order` / `group` / `hidden` / `facet` / `indexed` / `searchable` — the corresponding `sq:` hints;
 * - `readOnly` — `true` when `sq:derivedBy` is present **or** `sq:readOnly` is `'true'` / `'1'`;
 * - `derivation` — a {@link DerivationView} built from the `sq:derivedBy` node (rollup or plugin),
 *   or `null` when absent.
 *
 * The result is sorted by `order` (views without `sq:displayOrder` last), breaking ties by IRI.
 */
export function readProperties(store: RdfStore): PropertyView[] {
  const typePred = namedNode(RDF.type);

  const datatypeProps = new Set<string>();
  const objectProps = new Set<string>();

  for (const q of store.match(null, typePred, null)) {
    if (q.subject.termType !== 'NamedNode' || q.object.termType !== 'NamedNode') continue;
    if (q.object.value === OWL.DatatypeProperty) {
      datatypeProps.add(q.subject.value);
    } else if (q.object.value === OWL.ObjectProperty) {
      objectProps.add(q.subject.value);
    }
  }

  const views: PropertyView[] = [];
  for (const iri of datatypeProps) {
    views.push(buildAttributeView(store, iri));
  }
  for (const iri of objectProps) {
    views.push(buildRelationshipView(store, iri));
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

/** Builds the {@link AttributePropertyView} for a single `owl:DatatypeProperty` subject. */
function buildAttributeView(store: RdfStore, iri: string): AttributePropertyView {
  const subject = namedNode(iri);

  const label =
    firstLiteral(store, subject, namedNode(SQ.label)) ??
    firstLiteral(store, subject, namedNode(RDFS.label));

  const domainIri = firstNamed(store, subject, namedNode(RDFS.domain)) ?? '';

  const rangeIri = firstNamed(store, subject, namedNode(RDFS.range)) ?? '';

  const order = readOrder(store, subject);
  const group = firstLiteral(store, subject, namedNode(SQ.displayGroup));
  const hidden = readBoolean(store, subject, SQ.hidden);
  const facet = readBoolean(store, subject, SQ.facet);
  const indexed = readBoolean(store, subject, SQ.indexed);
  const searchable = readBoolean(store, subject, SQ.searchable);

  const derivedByNode = firstObject(store, subject, namedNode(SQ.derivedBy));
  const derivedByPresent = derivedByNode !== null;
  const readOnly = derivedByPresent || readBoolean(store, subject, SQ.readOnly);

  const derivation = derivedByNode === null ? null : readDerivation(store, derivedByNode);

  return {
    iri,
    kind: 'attribute',
    label,
    domainIri,
    datatype: xsdToDatatype(rangeIri),
    order,
    group,
    hidden,
    facet,
    indexed,
    searchable,
    readOnly,
    derivation,
  };
}

/** Builds the {@link RelationshipPropertyView} for a single `owl:ObjectProperty` subject. */
function buildRelationshipView(store: RdfStore, iri: string): RelationshipPropertyView {
  const subject = namedNode(iri);

  const label =
    firstLiteral(store, subject, namedNode(SQ.label)) ??
    firstLiteral(store, subject, namedNode(RDFS.label));

  const domainIri = firstNamed(store, subject, namedNode(RDFS.domain)) ?? '';

  const targetTypeIri = firstNamed(store, subject, namedNode(RDFS.range)) ?? '';

  const inverseIri = firstNamed(store, subject, namedNode(OWL.inverseOf));

  const transitive = store.match(subject, namedNode(RDF.type), namedNode(OWL.TransitiveProperty)).length > 0;

  const order = readOrder(store, subject);
  const group = firstLiteral(store, subject, namedNode(SQ.displayGroup));
  const hidden = readBoolean(store, subject, SQ.hidden);
  const facet = readBoolean(store, subject, SQ.facet);
  const indexed = readBoolean(store, subject, SQ.indexed);
  const searchable = readBoolean(store, subject, SQ.searchable);

  const derivedByNode = firstObject(store, subject, namedNode(SQ.derivedBy));
  const derivedByPresent = derivedByNode !== null;
  const readOnly = derivedByPresent || readBoolean(store, subject, SQ.readOnly);

  const derivation = derivedByNode === null ? null : readDerivation(store, derivedByNode);

  return {
    iri,
    kind: 'relationship',
    label,
    domainIri,
    targetTypeIri,
    inverseIri,
    transitive,
    order,
    group,
    hidden,
    facet,
    indexed,
    searchable,
    readOnly,
    derivation,
  };
}

/**
 * Reads a `sq:derivedBy`'s target node and returns the corresponding {@link DerivationView}. A node
 * typed `sq:Rollup` projects to a rollup rule with `sq:function` / `sq:over` / `sq:via` / `sq:of` /
 * `sq:filter` / `sq:materialised`; a node typed `sq:Plugin` projects to a plugin rule with
 * `sq:pluginName` / `sq:materialised`. A node typed as neither defaults to a rollup with no fields,
 * matching the "unrecognised → fall back" stance the snapshot layer takes.
 */
function readDerivation(store: RdfStore, node: Term): DerivationView {
  const rollupType = namedNode(SQ.Rollup);
  const pluginType = namedNode(SQ.Plugin);

  const isRollup = store.match(node, namedNode(RDF.type), rollupType).length > 0;
  const isPlugin = store.match(node, namedNode(RDF.type), pluginType).length > 0;

  const materialised = readBoolean(store, node, SQ.materialised);

  if (isPlugin && !isRollup) {
    return {
      kind: 'plugin',
      pluginName: firstLiteral(store, node, namedNode(SQ.pluginName)) ?? '',
      materialised,
    };
  }

  // Rollup (or unrecognised / untyped — falls back to a rollup with null fields).
  const functionIri = firstNamed(store, node, namedNode(SQ.function));
  const function_ = functionIri === null ? 'count' : localName(functionIri);

  const overIri = firstNamed(store, node, namedNode(SQ.over));
  const viaIri = firstNamed(store, node, namedNode(SQ.via));
  const ofIri = firstNamed(store, node, namedNode(SQ.of));

  const filterNode = firstObject(store, node, namedNode(SQ.filter));
  const filter = filterNode === null ? null : readFilter(store, filterNode);

  return {
    kind: 'rollup',
    function: function_,
    overIri,
    viaIri,
    ofIri,
    filter,
    materialised,
  };
}

/**
 * Reads the `sq:filter` RDF list on a `sq:Rollup` node. Each element is itself a `sq:Criterion`
 * with `sq:property` / `sq:operator` / `sq:value`; the human-readable summary format
 * `property operator 'value'` is what `TypeResponseMapper`'s rollup summaries use, so that is what
 * we emit here (one per criterion, joined by commas — the spec's single-criterion fixture produces
 * `status ne 'done'`).
 */
function readFilter(store: RdfStore, head: Term): string {
  const firstPred = namedNode(RDF_FIRST);
  const restPred = namedNode(RDF_REST);

  const criteria: string[] = [];
  let cur: Term | null = head;
  while (cur !== null) {
    if (cur.termType === 'NamedNode' && cur.value === RDF_NIL) {
      break;
    }
    const firstQuads = store.match(cur, firstPred, null);
    const firstQuad = firstQuads.length > 0 ? firstQuads[0] : null;
    if (firstQuad === null) {
      break;
    }
    const criterionNode: Term = firstQuad.object;

    const propIri = firstNamed(store, criterionNode, namedNode(SQ.property));
    const opNamed = firstNamed(store, criterionNode, namedNode(SQ.operator));
    const op = opNamed === null ? '' : localName(opNamed);
    const value = firstLiteral(store, criterionNode, namedNode(SQ.value));
    const valueText = value === null ? "" : `'${value}'`;
    criteria.push(`${propIri === null ? '' : localName(propIri)} ${op} ${valueText}`);

    const restQuads = store.match(cur, restPred, null);
    cur = restQuads.length > 0 ? restQuads[0].object : null;
  }

  return criteria.join(', ');
}

/**
 * Reads a boolean `sq:` hint. The store contract asserts `sq:facet` / `sq:indexed` / `sq:searchable`
 * / `sq:readOnly` / `sq:hidden` / `sq:materialised` only as the literal `'true'` (or `'1'`); any
 * other value — including absent — is treated as `false`.
 */
function readBoolean(store: RdfStore, subject: Term, predicateIri: string): boolean {
  const raw = firstLiteral(store, subject, namedNode(predicateIri));
  return raw === 'true' || raw === '1';
}

/**
 * Returns the lexical value of the first `rdf:NamedNode` object matching `(subject, predicate, ?)`
 * or `null`. Distinct from {@link firstLiteral}, which only accepts `rdf:Literal` objects.
 */
function firstNamed(store: RdfStore, subject: Term, predicate: Term): string | null {
  for (const q of store.match(subject, predicate, null)) {
    if (q.object.termType === 'NamedNode') {
      return q.object.value;
    }
  }
  return null;
}

/**
 * Returns the first object (any term type) matching `(subject, predicate, ?)` or `null`. Used to
 * read `sq:derivedBy`, whose object is a blank node.
 */
function firstObject(store: RdfStore, subject: Term, predicate: Term): Term | null {
  for (const q of store.match(subject, predicate, null)) {
    return q.object;
  }
  return null;
}

/** Reads `sq:displayOrder` as a non-negative integer, or `null` when absent. */
function readOrder(store: RdfStore, subject: Term): number | null {
  const raw = firstLiteral(store, subject, namedNode(SQ.displayOrder));
  if (raw === null) return null;
  return parseNonNegativeInteger(raw);
}

/** Strips the namespace off an IRI, returning the local name (text after the last `#` or last `/`). */
function localName(iri: string): string {
  const hashIdx = iri.lastIndexOf('#');
  if (hashIdx >= 0 && hashIdx < iri.length - 1) {
    return iri.substring(hashIdx + 1);
  }
  const slashIdx = iri.lastIndexOf('/');
  if (slashIdx >= 0 && slashIdx < iri.length - 1) {
    return iri.substring(slashIdx + 1);
  }
  return iri;
}

/**
 * Reads the `sq:StateMachine` that applies to `typeIri` and projects it into a
 * {@link StateMachineView}, following the OWL -> snapshot mapping table in
 * `docs/architecture/sq-vocabulary.md`:
 *
 * - a type's machine is the named `sq:StateMachine` node whose `sq:appliesTo` names `typeIri`
 *   (line "sq:appliesTo ... attaches StateMachineDefinition to that type"); `null` when no such
 *   machine exists;
 * - `initialStateIri` — the first named `sq:initialState` object, or `null`;
 * - `states` — the named `sq:state` objects, each projected via its `sq:label` /
 *   `sq:displayOrder`, sorted by `order` (missing last) then IRI;
 * - `transitions` — the blank-node `sq:transition` objects, sorted by their `sq:name`
 *   (nulls last), each projected per the transition rules below.
 *
 * A transition's `trigger` is derived from its `sq:trigger` object: `sq:UserAction` -> `USER_ACTION`,
 * `sq:OnChange` -> `ON_CHANGE` (with `watchIris` from every named `sq:watch`), `sq:Timer` -> `TIMER`
 * (with `after` the `sq:after` literal), `sq:ExternalSignal` -> `EXTERNAL_SIGNAL` (with `signalName`
 * the `sq:signalName` literal). `guard` / `guardMessage` come from `sq:guard` / `sq:guardMessage`.
 */
export function readStateMachine(store: RdfStore, typeIri: string): StateMachineView | null {
  const machineType = namedNode(SQ.StateMachine);
  const typeSubject = namedNode(typeIri);

  let machineNode: Term | null = null;
  for (const q of store.match(null, namedNode(RDF.type), machineType)) {
    if (q.subject.termType !== 'NamedNode') {
      continue;
    }
    if (store.match(q.subject, namedNode(SQ.appliesTo), typeSubject).length > 0) {
      machineNode = q.subject;
      break;
    }
  }
  if (machineNode === null) {
    return null;
  }

  const initialStateIri = firstNamed(store, machineNode, namedNode(SQ.initialState));

  const stateNodes: Term[] = [];
  for (const q of store.match(machineNode, namedNode(SQ.state), null)) {
    stateNodes.push(q.object);
  }
  const states = stateNodes
    .map((node) => buildStateView(store, node))
    .sort(compareOrderThenIri);

  const transitionNodes: Term[] = [];
  for (const q of store.match(machineNode, namedNode(SQ.transition), null)) {
    transitionNodes.push(q.object);
  }
  const transitions = transitionNodes
    .map((node) => buildTransitionView(store, node))
    .sort(compareTransitionByName);

  return {
    iri: machineNode.value,
    appliesToIri: typeIri,
    initialStateIri,
    states,
    transitions,
  };
}

/**
 * Compares two state-like views by `order` (views without `sq:displayOrder` last), breaking ties by
 * IRI. Mirrors the inline comparators `readClasses` / `readProperties` already use.
 */
function compareOrderThenIri(a: { order: number | null; iri: string }, b: { order: number | null; iri: string }): number {
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
}

/** Compares two transitions by their `sq:name` (un-named transitions last), breaking ties by node IRI. */
function compareTransitionByName(a: TransitionView, b: TransitionView): number {
  if (a.name !== null && b.name !== null && a.name !== b.name) {
    return a.name < b.name ? -1 : 1;
  }
  if (a.name !== null && b.name === null) {
    return -1;
  }
  if (a.name === null && b.name !== null) {
    return 1;
  }
  return a.node.value < b.node.value ? -1 : a.node.value > b.node.value ? 1 : 0;
}

/** Builds the {@link StateView} for a single `sq:State` subject. */
function buildStateView(store: RdfStore, node: Term): StateView {
  const label =
    firstLiteral(store, node, namedNode(SQ.label)) ??
    firstLiteral(store, node, namedNode(RDFS.label));
  return {
    iri: node.value,
    label,
    order: readOrder(store, node),
  };
}

/** Builds the {@link TransitionView} for a single `sq:Transition` blank node. */
function buildTransitionView(store: RdfStore, node: Term): TransitionView {
  const name = firstLiteral(store, node, namedNode(SQ.name));
  const fromIri = firstNamed(store, node, namedNode(SQ.from)) ?? '';
  const toIri = firstNamed(store, node, namedNode(SQ.to)) ?? '';
  const guard = firstLiteral(store, node, namedNode(SQ.guard));
  const guardMessage = firstLiteral(store, node, namedNode(SQ.guardMessage));

  return {
    node,
    name,
    fromIri,
    toIri,
    trigger: readTrigger(store, node),
    guard,
    guardMessage,
  };
}

/**
 * Derives the {@link TriggerView} for a transition from its `sq:trigger` object (a named `sq:`
 * individual) together with the auxiliary literals that individual implies
 * (`sq:watch`, `sq:after`, `sq:signalName`). An absent or unrecognised trigger falls back to
 * `USER_ACTION`, matching the "unrecognised → fall back" stance used elsewhere in this file.
 */
function readTrigger(store: RdfStore, node: Term): TriggerView {
  const triggerObj = firstObject(store, node, namedNode(SQ.trigger));
  const triggerIri =
    triggerObj !== null && triggerObj.termType === 'NamedNode' ? triggerObj.value : null;

  if (triggerIri === SQ.OnChange) {
    const watchIris: string[] = [];
    for (const q of store.match(node, namedNode(SQ.watch), null)) {
      if (q.object.termType === 'NamedNode') {
        watchIris.push(q.object.value);
      }
    }
    return { kind: 'ON_CHANGE', watchIris };
  }

  if (triggerIri === SQ.Timer) {
    return { kind: 'TIMER', after: firstLiteral(store, node, namedNode(SQ.after)) ?? '' };
  }

  if (triggerIri === SQ.ExternalSignal) {
    return { kind: 'EXTERNAL_SIGNAL', signalName: firstLiteral(store, node, namedNode(SQ.signalName)) ?? '' };
  }

  // UserAction (default and the unrecognised fallback).
  return { kind: 'USER_ACTION' };
}

