/**
 * Contract under test (lib/edits.ts): every edit function is a PURE computation over the current
 * store — it never mutates the store it is given — and returns a targeted `QuadDelta` describing
 * exactly what should change, per the edit-delta table in docs/specs/phase-4-rdf.md and the `sq:`
 * term mapping in docs/architecture/sq-vocabulary.md. No quad may ever appear in both
 * `delta.added` and `delta.removed`. Every fact below comes from `FIXTURE_TTL` (= the reference
 * ontology plus one stray `rdfs:comment` outside the `sq:` vocabulary that no edit here touches).
 *
 * Every assertion compares quad sets via `sameQuadSet`, never Turtle text, since blank node labels
 * are only stable within one parse (see "Blank nodes: the one accepted wart" in the phase-4 spec).
 * Where an edit mints a brand new blank node (a new cardinality restriction, a new derivation
 * rule), the expected quads below stand in with a freshly created, unrelated blank node —
 * `sameQuadSet` treats every blank node identity as interchangeable, so the stand-in matches
 * whatever node the real implementation actually creates.
 */
import { DataFactory, Parser } from 'n3';
import type { Quad_Subject } from 'n3';
import { N3RdfStore } from './store';
import type { RdfStore } from './store';
import {
  addAttributeProperty,
  addRelationshipProperty,
  relabel,
  setCardinality,
  setDerivation,
  setGuard,
  setTransitionTrigger,
} from './edits';
import type {
  AttributePropertySpec,
  DerivationSpec,
  QuadDelta,
  RelationshipPropertySpec,
  TriggerView,
} from './model';
import { DATATYPE_TO_XSD, OWL, RDF, RDFS, SQ, XSD } from './vocabulary';
import { FIXTURE_TTL } from '../testing/reference-ttl';
import { sameQuadSet } from '../testing/quad-set';

const { namedNode, literal, blankNode, quad } = DataFactory;

const EX_NS = 'https://sequeless.dev/ns/ref#';
const ex = (local: string) => `${EX_NS}${local}`;
const exNode = (local: string) => namedNode(ex(local));

const sqTerm = (local: keyof typeof SQ) => namedNode(SQ[local]);
const owlTerm = (local: keyof typeof OWL) => namedNode(OWL[local]);
const rdfTerm = (local: keyof typeof RDF) => namedNode(RDF[local]);
const rdfsTerm = (local: keyof typeof RDFS) => namedNode(RDFS[local]);
const xsdTerm = (local: keyof typeof XSD) => namedNode(XSD[local]);

const RDF_TYPE = rdfTerm('type');

function buildStore(): N3RdfStore {
  const quads = new Parser({ format: 'text/turtle' }).parse(FIXTURE_TTL);
  return new N3RdfStore(quads);
}

/** Runs `fn`, then asserts `store`'s contents are quad-set-identical to before the call. */
function invokePure<T>(store: RdfStore, fn: () => T): T {
  const before = store.quads();
  const result = fn();
  expect(sameQuadSet(store.quads(), before)).toBe(true);
  return result;
}

/** The invariant every edit must hold: no quad is listed as both added and removed. */
function assertNoOverlap(delta: QuadDelta): void {
  for (const added of delta.added) {
    expect(delta.removed.some((removed) => removed.equals(added))).toBe(false);
  }
}

function findRestrictionNode(
  store: RdfStore,
  typeIri: string,
  propertyIri: string,
  cardinalityPredicate: string,
): Quad_Subject {
  const edges = store.match(namedNode(typeIri), rdfsTerm('subClassOf'), null);
  for (const edge of edges) {
    const node = edge.object as Quad_Subject;
    const onProperty = store.match(node, owlTerm('onProperty'), null);
    const bound = store.match(node, namedNode(cardinalityPredicate), null);
    if (onProperty[0]?.object.value === propertyIri && bound.length > 0) {
      return node;
    }
  }
  throw new Error(`no restriction node for (${typeIri}, ${propertyIri}, ${cardinalityPredicate})`);
}

function findTransitionNode(store: RdfStore, name: string): Quad_Subject {
  const transitions = store.match(exNode('ProjectLifecycle'), sqTerm('transition'), null);
  for (const transition of transitions) {
    const node = transition.object as Quad_Subject;
    const names = store.match(node, sqTerm('name'), null);
    if (names[0]?.object.value === name) {
      return node;
    }
  }
  throw new Error(`no transition named ${name}`);
}

describe('relabel', () => {
  it('removes the existing sq:label and adds the new one when sq:label is asserted', () => {
    const store = buildStore();

    const delta = invokePure(store, () => relabel(store, ex('Task'), 'Renamed Task'));

    expect(sameQuadSet(delta.removed, [quad(exNode('Task'), sqTerm('label'), literal('Task'))])).toBe(
      true,
    );
    expect(
      sameQuadSet(delta.added, [quad(exNode('Task'), sqTerm('label'), literal('Renamed Task'))]),
    ).toBe(true);
    assertNoOverlap(delta);
  });

  it('falls back to removing rdfs:label when no sq:label is asserted on the subject', () => {
    const store = buildStore();
    const subject = namedNode(`${EX_NS}unlabelledNote`);
    const oldLabel = quad(subject, rdfsTerm('label'), literal('Old Note'));
    store.add(oldLabel);

    const delta = invokePure(store, () => relabel(store, subject.value, 'New Note'));

    expect(sameQuadSet(delta.removed, [oldLabel])).toBe(true);
    expect(sameQuadSet(delta.added, [quad(subject, sqTerm('label'), literal('New Note'))])).toBe(true);
    assertNoOverlap(delta);
  });
});

describe('addAttributeProperty', () => {
  it('mints only rdf:type, rdfs:domain and rdfs:range for a spec with no optional hints', () => {
    const store = buildStore();
    const spec: AttributePropertySpec = { iri: ex('newAttr'), domainIri: ex('Task'), datatype: 'STRING' };

    const delta = invokePure(store, () => addAttributeProperty(store, spec));

    expect(
      sameQuadSet(delta.added, [
        quad(exNode('newAttr'), RDF_TYPE, owlTerm('DatatypeProperty')),
        quad(exNode('newAttr'), rdfsTerm('domain'), exNode('Task')),
        quad(exNode('newAttr'), rdfsTerm('range'), namedNode(DATATYPE_TO_XSD.STRING)),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('maps the datatype to its xsd: range and types sq:displayOrder as xsd:integer', () => {
    const store = buildStore();
    const spec: AttributePropertySpec = {
      iri: ex('newAttr'),
      domainIri: ex('Task'),
      datatype: 'DATE_TIME',
      label: 'New Attr',
      order: 5,
      group: 'Custom',
    };

    const delta = invokePure(store, () => addAttributeProperty(store, spec));

    expect(
      sameQuadSet(delta.added, [
        quad(exNode('newAttr'), RDF_TYPE, owlTerm('DatatypeProperty')),
        quad(exNode('newAttr'), rdfsTerm('domain'), exNode('Task')),
        quad(exNode('newAttr'), rdfsTerm('range'), namedNode(DATATYPE_TO_XSD.DATE_TIME)),
        quad(exNode('newAttr'), sqTerm('label'), literal('New Attr')),
        quad(exNode('newAttr'), sqTerm('displayOrder'), literal('5', xsdTerm('integer'))),
        quad(exNode('newAttr'), sqTerm('displayGroup'), literal('Custom')),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('emits only the flags explicitly set true, as "true"^^xsd:boolean, leaving the rest absent', () => {
    const store = buildStore();
    const spec: AttributePropertySpec = {
      iri: ex('newAttr'),
      domainIri: ex('Task'),
      datatype: 'BOOLEAN',
      facet: true,
      searchable: true,
    };

    const delta = invokePure(store, () => addAttributeProperty(store, spec));

    expect(
      sameQuadSet(delta.added, [
        quad(exNode('newAttr'), RDF_TYPE, owlTerm('DatatypeProperty')),
        quad(exNode('newAttr'), rdfsTerm('domain'), exNode('Task')),
        quad(exNode('newAttr'), rdfsTerm('range'), namedNode(DATATYPE_TO_XSD.BOOLEAN)),
        quad(exNode('newAttr'), sqTerm('facet'), literal('true', xsdTerm('boolean'))),
        quad(exNode('newAttr'), sqTerm('searchable'), literal('true', xsdTerm('boolean'))),
      ]),
    ).toBe(true);
    expect(delta.added.some((q) => q.predicate.equals(sqTerm('indexed')))).toBe(false);
    expect(delta.added.some((q) => q.predicate.equals(sqTerm('hidden')))).toBe(false);
    assertNoOverlap(delta);
  });

  it('treats an explicitly false flag the same as an omitted one, emitting no triple for it', () => {
    const store = buildStore();
    const spec: AttributePropertySpec = {
      iri: ex('newAttr'),
      domainIri: ex('Task'),
      datatype: 'STRING',
      facet: false,
      indexed: true,
      searchable: false,
      hidden: true,
    };

    const delta = invokePure(store, () => addAttributeProperty(store, spec));

    expect(
      sameQuadSet(delta.added, [
        quad(exNode('newAttr'), RDF_TYPE, owlTerm('DatatypeProperty')),
        quad(exNode('newAttr'), rdfsTerm('domain'), exNode('Task')),
        quad(exNode('newAttr'), rdfsTerm('range'), namedNode(DATATYPE_TO_XSD.STRING)),
        quad(exNode('newAttr'), sqTerm('indexed'), literal('true', xsdTerm('boolean'))),
        quad(exNode('newAttr'), sqTerm('hidden'), literal('true', xsdTerm('boolean'))),
      ]),
    ).toBe(true);
    assertNoOverlap(delta);
  });
});

describe('addRelationshipProperty', () => {
  it('mints only rdf:type, rdfs:domain and rdfs:range when no inverse or transitive flag is set', () => {
    const store = buildStore();
    const spec: RelationshipPropertySpec = {
      iri: ex('newRel'),
      domainIri: ex('Task'),
      targetTypeIri: ex('Person'),
    };

    const delta = invokePure(store, () => addRelationshipProperty(store, spec));

    expect(
      sameQuadSet(delta.added, [
        quad(exNode('newRel'), RDF_TYPE, owlTerm('ObjectProperty')),
        quad(exNode('newRel'), rdfsTerm('domain'), exNode('Task')),
        quad(exNode('newRel'), rdfsTerm('range'), exNode('Person')),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('adds owl:inverseOf and types the property owl:TransitiveProperty when the spec sets them', () => {
    const store = buildStore();
    const spec: RelationshipPropertySpec = {
      iri: ex('newRel'),
      domainIri: ex('Project'),
      targetTypeIri: ex('Task'),
      inverseIri: ex('newRelInverse'),
      transitive: true,
    };

    const delta = invokePure(store, () => addRelationshipProperty(store, spec));

    expect(
      sameQuadSet(delta.added, [
        quad(exNode('newRel'), RDF_TYPE, owlTerm('ObjectProperty')),
        quad(exNode('newRel'), rdfsTerm('domain'), exNode('Project')),
        quad(exNode('newRel'), rdfsTerm('range'), exNode('Task')),
        quad(exNode('newRel'), owlTerm('inverseOf'), exNode('newRelInverse')),
        quad(exNode('newRel'), RDF_TYPE, owlTerm('TransitiveProperty')),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });
});

describe('setCardinality', () => {
  it('patches an existing restriction node in place when replacing one of its bounds', () => {
    const store = buildStore();
    const node = findRestrictionNode(store, ex('WorkItem'), ex('status'), OWL.maxCardinality);

    const delta = invokePure(store, () =>
      setCardinality(store, ex('WorkItem'), ex('status'), { min: 0, max: 2 }),
    );

    expect(
      sameQuadSet(delta.removed, [
        quad(node, owlTerm('maxCardinality'), literal('1', xsdTerm('nonNegativeInteger'))),
      ]),
    ).toBe(true);
    expect(
      sameQuadSet(delta.added, [
        quad(node, owlTerm('maxCardinality'), literal('2', xsdTerm('nonNegativeInteger'))),
      ]),
    ).toBe(true);
    assertNoOverlap(delta);

    const clone = store.clone();
    clone.apply(delta);
    const remaining = clone.match(node, owlTerm('maxCardinality'), null);
    expect(remaining).toHaveLength(1);
    expect(remaining[0].object.value).toBe('2');
  });

  it('adds a new bound to an existing restriction node, leaving its other bound untouched', () => {
    const store = buildStore();
    const node = findRestrictionNode(store, ex('WorkItem'), ex('status'), OWL.maxCardinality);

    const delta = invokePure(store, () =>
      setCardinality(store, ex('WorkItem'), ex('status'), { min: 1, max: 1 }),
    );

    expect(delta.removed).toEqual([]);
    expect(
      sameQuadSet(delta.added, [
        quad(node, owlTerm('minCardinality'), literal('1', xsdTerm('nonNegativeInteger'))),
      ]),
    ).toBe(true);
    assertNoOverlap(delta);
  });

  it('mints a new restriction blank node and rdfs:subClassOf edge when none exists for the pair', () => {
    const store = buildStore();

    const delta = invokePure(store, () =>
      setCardinality(store, ex('Task'), ex('description'), { min: 1, max: 1 }),
    );

    const node = blankNode('stand-in');
    expect(
      sameQuadSet(delta.added, [
        quad(exNode('Task'), rdfsTerm('subClassOf'), node),
        quad(node, RDF_TYPE, owlTerm('Restriction')),
        quad(node, owlTerm('onProperty'), exNode('description')),
        quad(node, owlTerm('minCardinality'), literal('1', xsdTerm('nonNegativeInteger'))),
        quad(node, owlTerm('maxCardinality'), literal('1', xsdTerm('nonNegativeInteger'))),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('mints a restriction and subClassOf edge even when the type has no existing rdfs:subClassOf edges', () => {
    const store = buildStore();
    expect(store.match(exNode('Deliverable'), rdfsTerm('subClassOf'), null)).toEqual([]);

    const delta = invokePure(store, () =>
      setCardinality(store, ex('Deliverable'), ex('title'), { min: 0, max: 1 }),
    );

    const node = blankNode('stand-in');
    expect(
      sameQuadSet(delta.added, [
        quad(exNode('Deliverable'), rdfsTerm('subClassOf'), node),
        quad(node, RDF_TYPE, owlTerm('Restriction')),
        quad(node, owlTerm('onProperty'), exNode('title')),
        quad(node, owlTerm('maxCardinality'), literal('1', xsdTerm('nonNegativeInteger'))),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('removes a restriction node entirely when its only bound is cleared and it is otherwise unreferenced', () => {
    const store = buildStore();
    const node = findRestrictionNode(store, ex('WorkItem'), ex('status'), OWL.maxCardinality);

    const delta = invokePure(store, () =>
      setCardinality(store, ex('WorkItem'), ex('status'), { min: 0, max: null }),
    );

    expect(
      sameQuadSet(delta.removed, [
        quad(exNode('WorkItem'), rdfsTerm('subClassOf'), node),
        quad(node, RDF_TYPE, owlTerm('Restriction')),
        quad(node, owlTerm('onProperty'), exNode('status')),
        quad(node, owlTerm('maxCardinality'), literal('1', xsdTerm('nonNegativeInteger'))),
      ]),
    ).toBe(true);
    expect(delta.added).toEqual([]);
    assertNoOverlap(delta);
  });

  it('removes a single-bound restriction node and edge when clearing it empties the node, leaving the sibling node alone', () => {
    const store = buildStore();
    const minNode = findRestrictionNode(store, ex('Person'), ex('name'), OWL.minCardinality);
    const maxNode = findRestrictionNode(store, ex('Person'), ex('name'), OWL.maxCardinality);
    expect(minNode.equals(maxNode)).toBe(false);

    const delta = invokePure(store, () =>
      setCardinality(store, ex('Person'), ex('name'), { min: 0, max: 1 }),
    );

    expect(
      sameQuadSet(delta.removed, [
        quad(exNode('Person'), rdfsTerm('subClassOf'), minNode),
        quad(minNode, RDF_TYPE, owlTerm('Restriction')),
        quad(minNode, owlTerm('onProperty'), exNode('name')),
        quad(minNode, owlTerm('minCardinality'), literal('1', xsdTerm('nonNegativeInteger'))),
      ]),
    ).toBe(true);
    expect(delta.added).toEqual([]);
    assertNoOverlap(delta);

    const clone = store.clone();
    clone.apply(delta);
    expect(
      clone.has(quad(maxNode, owlTerm('maxCardinality'), literal('1', xsdTerm('nonNegativeInteger')))),
    ).toBe(true);
  });

  it('keeps a restriction node and its edge when another triple still references it', () => {
    const store = buildStore();
    const minNode = findRestrictionNode(store, ex('Person'), ex('name'), OWL.minCardinality);
    const extra = quad(minNode, namedNode('https://example.test/stillReferenced'), literal('keep-me'));
    store.add(extra);

    const delta = invokePure(store, () =>
      setCardinality(store, ex('Person'), ex('name'), { min: 0, max: 1 }),
    );

    expect(
      sameQuadSet(delta.removed, [
        quad(minNode, owlTerm('minCardinality'), literal('1', xsdTerm('nonNegativeInteger'))),
      ]),
    ).toBe(true);
    expect(delta.added).toEqual([]);
    assertNoOverlap(delta);
  });
});

describe('setDerivation', () => {
  it('sets a new rollup derivation on a property with no existing sq:derivedBy', () => {
    const store = buildStore();
    const rule: DerivationSpec = {
      kind: 'rollup',
      function: 'sum',
      overIri: ex('Task'),
      viaIri: ex('belongsToProject'),
      ofIri: ex('estimatedHours'),
      filter: null,
    };

    const delta = invokePure(store, () => setDerivation(store, ex('priority'), rule));

    const node = blankNode('stand-in');
    expect(
      sameQuadSet(delta.added, [
        quad(exNode('priority'), sqTerm('derivedBy'), node),
        quad(node, RDF_TYPE, sqTerm('Rollup')),
        quad(node, sqTerm('function'), sqTerm('sum')),
        quad(node, sqTerm('over'), exNode('Task')),
        quad(node, sqTerm('via'), exNode('belongsToProject')),
        quad(node, sqTerm('of'), exNode('estimatedHours')),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('sets a new plugin derivation on a property with no existing sq:derivedBy', () => {
    const store = buildStore();
    const rule: DerivationSpec = { kind: 'plugin', pluginName: 'computePriority' };

    const delta = invokePure(store, () => setDerivation(store, ex('priority'), rule));

    const node = blankNode('stand-in');
    expect(
      sameQuadSet(delta.added, [
        quad(exNode('priority'), sqTerm('derivedBy'), node),
        quad(node, RDF_TYPE, sqTerm('Plugin')),
        quad(node, sqTerm('pluginName'), literal('computePriority')),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('replaces an existing rollup rule and every one of its triples with a new plugin rule', () => {
    const store = buildStore();
    const rule: DerivationSpec = { kind: 'plugin', pluginName: 'customCalc' };

    const delta = invokePure(store, () => setDerivation(store, ex('totalEstimatedHours'), rule));

    const oldNode = blankNode('old-stand-in');
    const newNode = blankNode('new-stand-in');
    expect(
      sameQuadSet(delta.removed, [
        quad(exNode('totalEstimatedHours'), sqTerm('derivedBy'), oldNode),
        quad(oldNode, RDF_TYPE, sqTerm('Rollup')),
        quad(oldNode, sqTerm('function'), sqTerm('sum')),
        quad(oldNode, sqTerm('over'), exNode('Task')),
        quad(oldNode, sqTerm('via'), exNode('belongsToProject')),
        quad(oldNode, sqTerm('of'), exNode('estimatedHours')),
      ]),
    ).toBe(true);
    expect(
      sameQuadSet(delta.added, [
        quad(exNode('totalEstimatedHours'), sqTerm('derivedBy'), newNode),
        quad(newNode, RDF_TYPE, sqTerm('Plugin')),
        quad(newNode, sqTerm('pluginName'), literal('customCalc')),
      ]),
    ).toBe(true);
    assertNoOverlap(delta);
  });

  it('removes every triple of the existing rule, and adds nothing, when the new rule is null', () => {
    const store = buildStore();

    const delta = invokePure(store, () => setDerivation(store, ex('totalEstimatedHours'), null));

    const oldNode = blankNode('stand-in');
    expect(
      sameQuadSet(delta.removed, [
        quad(exNode('totalEstimatedHours'), sqTerm('derivedBy'), oldNode),
        quad(oldNode, RDF_TYPE, sqTerm('Rollup')),
        quad(oldNode, sqTerm('function'), sqTerm('sum')),
        quad(oldNode, sqTerm('over'), exNode('Task')),
        quad(oldNode, sqTerm('via'), exNode('belongsToProject')),
        quad(oldNode, sqTerm('of'), exNode('estimatedHours')),
      ]),
    ).toBe(true);
    expect(delta.added).toEqual([]);
    assertNoOverlap(delta);
  });
});

describe('setTransitionTrigger', () => {
  it('replaces ON_CHANGE with TIMER, removing the old sq:watch and adding the new sq:after', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'autoClose');
    const trigger: TriggerView = { kind: 'TIMER', after: 'P1D' };

    const delta = invokePure(store, () => setTransitionTrigger(store, transition, trigger));

    expect(
      sameQuadSet(delta.removed, [
        quad(transition, sqTerm('trigger'), sqTerm('OnChange')),
        quad(transition, sqTerm('watch'), exNode('belongsToProject')),
      ]),
    ).toBe(true);
    expect(
      sameQuadSet(delta.added, [
        quad(transition, sqTerm('trigger'), sqTerm('Timer')),
        quad(transition, sqTerm('after'), literal('P1D')),
      ]),
    ).toBe(true);
    assertNoOverlap(delta);
  });

  it('replaces TIMER with EXTERNAL_SIGNAL, removing the old sq:after and adding the new sq:signalName', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'expireHold');
    const trigger: TriggerView = { kind: 'EXTERNAL_SIGNAL', signalName: 'forceClose' };

    const delta = invokePure(store, () => setTransitionTrigger(store, transition, trigger));

    expect(
      sameQuadSet(delta.removed, [
        quad(transition, sqTerm('trigger'), sqTerm('Timer')),
        quad(transition, sqTerm('after'), literal('PT72H')),
      ]),
    ).toBe(true);
    expect(
      sameQuadSet(delta.added, [
        quad(transition, sqTerm('trigger'), sqTerm('ExternalSignal')),
        quad(transition, sqTerm('signalName'), literal('forceClose')),
      ]),
    ).toBe(true);
    assertNoOverlap(delta);
  });

  it('replaces EXTERNAL_SIGNAL with USER_ACTION, removing the old sq:signalName with no replacement sibling term', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'reopen');
    const trigger: TriggerView = { kind: 'USER_ACTION' };

    const delta = invokePure(store, () => setTransitionTrigger(store, transition, trigger));

    expect(
      sameQuadSet(delta.removed, [
        quad(transition, sqTerm('trigger'), sqTerm('ExternalSignal')),
        quad(transition, sqTerm('signalName'), literal('reopen')),
      ]),
    ).toBe(true);
    expect(sameQuadSet(delta.added, [quad(transition, sqTerm('trigger'), sqTerm('UserAction'))])).toBe(
      true,
    );
    assertNoOverlap(delta);
  });

  it('replaces USER_ACTION with ON_CHANGE, adding one sq:watch per watched relationship', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'activate');
    const trigger: TriggerView = { kind: 'ON_CHANGE', watchIris: [ex('status'), ex('priority')] };

    const delta = invokePure(store, () => setTransitionTrigger(store, transition, trigger));

    expect(sameQuadSet(delta.removed, [quad(transition, sqTerm('trigger'), sqTerm('UserAction'))])).toBe(
      true,
    );
    expect(
      sameQuadSet(delta.added, [
        quad(transition, sqTerm('trigger'), sqTerm('OnChange')),
        quad(transition, sqTerm('watch'), exNode('status')),
        quad(transition, sqTerm('watch'), exNode('priority')),
      ]),
    ).toBe(true);
    assertNoOverlap(delta);
  });

  it('adds no sq:watch at all for an ON_CHANGE trigger with an empty watch list ("watch self only")', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'hold');
    const trigger: TriggerView = { kind: 'ON_CHANGE', watchIris: [] };

    const delta = invokePure(store, () => setTransitionTrigger(store, transition, trigger));

    expect(sameQuadSet(delta.removed, [quad(transition, sqTerm('trigger'), sqTerm('UserAction'))])).toBe(
      true,
    );
    expect(sameQuadSet(delta.added, [quad(transition, sqTerm('trigger'), sqTerm('OnChange'))])).toBe(true);
    assertNoOverlap(delta);
  });
});

describe('setGuard', () => {
  it('adds a guard with no message to a transition that has neither', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'hold');

    const delta = invokePure(store, () => setGuard(store, transition, 'self.isReady', null));

    expect(sameQuadSet(delta.added, [quad(transition, sqTerm('guard'), literal('self.isReady'))])).toBe(
      true,
    );
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('adds both a guard and a guard message to a transition that has neither', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'resume');

    const delta = invokePure(store, () => setGuard(store, transition, 'self.ready', 'Not ready yet'));

    expect(
      sameQuadSet(delta.added, [
        quad(transition, sqTerm('guard'), literal('self.ready')),
        quad(transition, sqTerm('guardMessage'), literal('Not ready yet')),
      ]),
    ).toBe(true);
    expect(delta.removed).toEqual([]);
    assertNoOverlap(delta);
  });

  it('replaces an existing guard and message with new text', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'activate');

    const delta = invokePure(store, () =>
      setGuard(
        store,
        transition,
        'self.owner != null && self.title != null',
        'Need an owner and a title',
      ),
    );

    expect(
      sameQuadSet(delta.removed, [
        quad(transition, sqTerm('guard'), literal('self.owner != null')),
        quad(
          transition,
          sqTerm('guardMessage'),
          literal('Project must have an owner before it can be activated'),
        ),
      ]),
    ).toBe(true);
    expect(
      sameQuadSet(delta.added, [
        quad(transition, sqTerm('guard'), literal('self.owner != null && self.title != null')),
        quad(transition, sqTerm('guardMessage'), literal('Need an owner and a title')),
      ]),
    ).toBe(true);
    assertNoOverlap(delta);
  });

  it('removes both the guard and the guard message when both arguments are null', () => {
    const store = buildStore();
    const transition = findTransitionNode(store, 'autoClose');

    const delta = invokePure(store, () => setGuard(store, transition, null, null));

    expect(
      sameQuadSet(delta.removed, [
        quad(transition, sqTerm('guard'), literal('self.openTaskCount == 0')),
        quad(transition, sqTerm('guardMessage'), literal('Project still has open tasks')),
      ]),
    ).toBe(true);
    expect(delta.added).toEqual([]);
    assertNoOverlap(delta);
  });
});
