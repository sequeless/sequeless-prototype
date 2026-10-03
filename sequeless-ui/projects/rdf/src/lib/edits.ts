import { DataFactory } from 'n3';
import type { Quad_Predicate, Quad_Subject } from 'n3';
import type { Quad, Term } from '@rdfjs/types';
import type { AttributePropertySpec, DerivationSpec, QuadDelta, RelationshipPropertySpec, TriggerView } from './model';
import type { RdfStore } from './store';
import { DATATYPE_TO_XSD, OWL, RDF, RDFS, SQ, XSD } from './vocabulary';

const { namedNode, literal, quad } = DataFactory;

/**
 * Pure edit operations over an {@link RdfStore} — each function reads the current store state
 * (without mutating it) and returns a {@link QuadDelta} describing exactly what should change.
 * No quad may ever appear in both `delta.added` and `delta.removed`.
 */

/**
 * Relabel a subject's display name: remove any existing `sq:label` on `iri` — or, when no
 * `sq:label` is asserted on the subject, every existing `rdfs:label` — then add a single
 * `(iri, sq:label, label)` quad.
 */
export function relabel(store: RdfStore, iri: string, label: string): QuadDelta {
  const subject = namedNode(iri);
  const sqLabel = namedNode(SQ.label);
  const hasSqLabel = store.match(subject, sqLabel, null).length > 0;
  const oldPredicate = hasSqLabel ? sqLabel : namedNode(RDFS.label);
  const removed = store.match(subject, oldPredicate, null);
  const added: Quad[] = [quad(subject, sqLabel, literal(label))];
  return { added, removed };
}

/**
 * Mint a fresh `owl:DatatypeProperty` bound to `spec.domainIri`, with an XSD-typed range, plus any
 * of the `sq:` display hints (`label`, `displayOrder` as `xsd:integer`, `displayGroup`) and any
 * boolean flags (`facet`, `indexed`, `searchable`, `hidden`) the caller sets to exactly `true`.
 */
export function addAttributeProperty(store: RdfStore, spec: AttributePropertySpec): QuadDelta {
  const subject = namedNode(spec.iri);
  const added: Quad[] = [
    quad(subject, namedNode(RDF.type), namedNode(OWL.DatatypeProperty)),
    quad(subject, namedNode(RDFS.domain), namedNode(spec.domainIri)),
    quad(subject, namedNode(RDFS.range), namedNode(DATATYPE_TO_XSD[spec.datatype])),
  ];
  if (spec.label !== undefined) {
    added.push(quad(subject, namedNode(SQ.label), literal(spec.label)));
  }
  if (spec.order !== undefined) {
    added.push(quad(subject, namedNode(SQ.displayOrder), literal(String(spec.order), namedNode(XSD.integer))));
  }
  if (spec.group !== undefined) {
    added.push(quad(subject, namedNode(SQ.displayGroup), literal(spec.group)));
  }
  if (spec.facet === true) {
    added.push(quad(subject, namedNode(SQ.facet), literal('true', namedNode(XSD.boolean))));
  }
  if (spec.indexed === true) {
    added.push(quad(subject, namedNode(SQ.indexed), literal('true', namedNode(XSD.boolean))));
  }
  if (spec.searchable === true) {
    added.push(quad(subject, namedNode(SQ.searchable), literal('true', namedNode(XSD.boolean))));
  }
  if (spec.hidden === true) {
    added.push(quad(subject, namedNode(SQ.hidden), literal('true', namedNode(XSD.boolean))));
  }
  return { added, removed: [] as Quad[] };
}

/**
 * Mint a fresh `owl:ObjectProperty` from `spec.domainIri` to `spec.targetTypeIri`, plus — when
 * present on the spec — a `sq:label`, an `owl:inverseOf` partner, and an `owl:TransitiveProperty`
 * typing when `transitive` is exactly `true`.
 */
export function addRelationshipProperty(_store: RdfStore, spec: RelationshipPropertySpec): QuadDelta {
  const subject = namedNode(spec.iri);
  const added: Quad[] = [
    quad(subject, namedNode(RDF.type), namedNode(OWL.ObjectProperty)),
    quad(subject, namedNode(RDFS.domain), namedNode(spec.domainIri)),
    quad(subject, namedNode(RDFS.range), namedNode(spec.targetTypeIri)),
  ];
  if (spec.label !== undefined) {
    added.push(quad(subject, namedNode(SQ.label), literal(spec.label)));
  }
  if (spec.inverseIri !== undefined) {
    added.push(quad(subject, namedNode(OWL.inverseOf), namedNode(spec.inverseIri)));
  }
  if (spec.transitive === true) {
    added.push(quad(subject, namedNode(RDF.type), namedNode(OWL.TransitiveProperty)));
  }
  return { added, removed: [] as Quad[] };
}

/** True when two quads have identical subject, predicate, object. */
function sameQuad(a: Quad, b: Quad): boolean {
  return a.subject.equals(b.subject) && a.predicate.equals(b.predicate) && a.object.equals(b.object);
}

/** Removes from `a` any quads that also appear in `b` (by `sameQuad`), returning a new array. */
function dropShared(a: Quad[], b: Quad[]): Quad[] {
  return a.filter((q) => !b.some((r) => sameQuad(q, r)));
}

/**
 * Replace a transition's firing condition. Removes every existing `sq:trigger`, `sq:watch`, `sq:after`
 * and `sq:signalName` triple on the transition (regardless of the previously asserted kind), then re-
 * asserts `sq:trigger` to the new kind individual plus the sibling facts the new kind requires:
 * one `sq:watch` per watched IRIs for `ON_CHANGE`, a single `sq:after` for `TIMER`, and a single
 * `sq:signalName` for `EXTERNAL_SIGNAL`. Any quad that would appear in both `added` and `removed` is
 * dropped from both.
 */
export function setTransitionTrigger(store: RdfStore, transition: Term, trigger: TriggerView): QuadDelta {
  const subject = transition as Quad_Subject;
  const triggerPredicate = namedNode(SQ.trigger);
  const existing = [
    ...store.match(transition, triggerPredicate, null),
    ...store.match(transition, namedNode(SQ.watch), null),
    ...store.match(transition, namedNode(SQ.after), null),
    ...store.match(transition, namedNode(SQ.signalName), null),
  ];

  let kindIri: string;
  if (trigger.kind === 'ON_CHANGE') {
    kindIri = SQ.OnChange;
  } else if (trigger.kind === 'TIMER') {
    kindIri = SQ.Timer;
  } else if (trigger.kind === 'EXTERNAL_SIGNAL') {
    kindIri = SQ.ExternalSignal;
  } else {
    kindIri = SQ.UserAction;
  }
  const additions: Quad[] = [quad(subject, triggerPredicate, namedNode(kindIri))];
  if (trigger.kind === 'ON_CHANGE') {
    for (const iri of trigger.watchIris) {
      additions.push(quad(subject, namedNode(SQ.watch), namedNode(iri)));
    }
  } else if (trigger.kind === 'TIMER') {
    additions.push(quad(subject, namedNode(SQ.after), literal(trigger.after)));
  } else if (trigger.kind === 'EXTERNAL_SIGNAL') {
    additions.push(quad(subject, namedNode(SQ.signalName), literal(trigger.signalName)));
  }

  return {
    added: dropShared(additions, existing),
    removed: dropShared(existing, additions),
  };
}

/** A stable key identifying a quad's (subject, predicate, object) triple, independent of blank-node labels. */
function quadKey(q: Quad): string {
  return [
    `${q.subject.termType}:${q.subject.value}`,
    `${q.predicate.termType}:${q.predicate.value}`,
    `${q.object.termType}:${q.object.value}`,
  ].join('|');
}

/**
 * Set an `owl:Restriction`'s `owl:minCardinality` / `owl:maxCardinality` on a (type, property) pair as
 * `xsd:nonNegativeInteger` bounds. A restriction node is any blank/node subject that a `typeIri
 * rdfs:subClassOf` edge points at, typed `owl:Restriction`, with `owl:onProperty propertyIri`.
 *
 * `c.min` of `0` means no `owl:minCardinality`; `c.max` of `null` means no `owl:maxCardinality`. For a
 * bound that is set, the literal is patched in place on the first node that already has that bound, else
 * on the first matching node, minting a fresh blank node (plus its `rdfs:subClassOf` edge and
 * `owl:onProperty` fact) only when none exists. A restriction node is removed (all its triples plus its
 * `rdfs:subClassOf` edge) only when it is left with no `owl:*Cardinality` AND no other triple still
 * references it. Never mutates `store`; no quad ends up in both `added` and `removed`.
 */
export function setCardinality(
  store: RdfStore,
  typeIri: string,
  propertyIri: string,
  c: { min: number; max: number | null },
): QuadDelta {
  const nonNegativeInteger = namedNode(XSD.nonNegativeInteger);
  const minPredicate = namedNode(OWL.minCardinality);
  const maxPredicate = namedNode(OWL.maxCardinality);
  const onProperty = namedNode(OWL.onProperty);
  const owlRestriction = namedNode(OWL.Restriction);
  const rdfType = namedNode(RDF.type);
  const subClassOf = namedNode(RDFS.subClassOf);
  const typeNode = namedNode(typeIri);
  const propertyNode = namedNode(propertyIri);

  const nodes: Term[] = [];
  for (const edge of store.match(typeNode, subClassOf, null)) {
    const node = edge.object;
    if (node.termType !== 'NamedNode' && node.termType !== 'BlankNode') continue;
    if (store.match(node, onProperty, propertyNode).length === 0) continue;
    if (store.match(node, rdfType, owlRestriction).length === 0) continue;
    nodes.push(node);
  }

  const hasBound = (node: Term, predicate: Term) => store.match(node, predicate, null).length > 0;
  const minTarget: Term | 'mint' | null =
    c.min === 0 ? null : nodes.length > 0 ? (nodes.find((n) => hasBound(n, minPredicate)) ?? nodes[0]) : 'mint';
  const maxTarget: Term | 'mint' | null =
    c.max === null
      ? null
      : nodes.length > 0
        ? (nodes.find((n) => hasBound(n, maxPredicate)) ?? nodes[0])
        : 'mint';
  const mintNode = minTarget === 'mint' || maxTarget === 'mint' ? DataFactory.blankNode() : null;
  const minNode = minTarget === 'mint' ? mintNode! : minTarget;
  const maxNode = maxTarget === 'mint' ? mintNode! : maxTarget;

  // Track deltas by value (quadKey) since `store.match` returns fresh Quad objects each call.
  const removedKeys = new Set<string>();
  const removedQuads: Quad[] = [];
  const addedQuads: Quad[] = [];
  const addRemoved = (q: Quad) => {
    const key = quadKey(q);
    if (!removedKeys.has(key)) {
      removedKeys.add(key);
      removedQuads.push(q);
    }
  };
  const addAdded = (q: Quad) => addedQuads.push(q);
  const isRemoved = (q: Quad) => removedKeys.has(quadKey(q));

  const scheduleBound = (node: Term | null, predicate: Quad_Predicate, value: number | null) => {
    if (value === null) {
      for (const n of nodes) for (const q of store.match(n, predicate, null)) addRemoved(q);
      return;
    }
    if (node === null) return;
    for (const q of store.match(node, predicate, null)) if (q.object.value !== String(value)) addRemoved(q);
    const fresh = quad(node as Quad_Subject, predicate, literal(String(value), nonNegativeInteger));
    if (!store.has(fresh)) addAdded(fresh);
  };

  if (mintNode) {
    addAdded(quad(typeNode, subClassOf, mintNode));
    addAdded(quad(mintNode, rdfType, owlRestriction));
    addAdded(quad(mintNode, onProperty, propertyNode));
  }

  scheduleBound(minNode, minPredicate, c.min === 0 ? null : c.min);
  scheduleBound(maxNode, maxPredicate, c.max);

  // A triple that is removed *with* a node (rather than counting as an "other" reference keeping it
  // alive): its own `rdf:type owl:Restriction` / `owl:onProperty` facts, or the `subClassOf` edge from
  // the type (which sits in the node's object position).
  const isStructuralEdge = (node: Term, q: Quad) => {
    if (q.predicate.termType === 'NamedNode' && q.object.termType === 'NamedNode') {
      if (q.predicate.value === RDF.type && q.object.value === OWL.Restriction) return true;
      if (q.predicate.value === OWL.onProperty && q.object.value === propertyIri) return true;
    }
    return q.subject.equals(typeNode) && q.predicate.equals(subClassOf) && q.object.equals(node);
  };
  for (const node of nodes) {
    if (node === minNode || node === maxNode) continue; // still carries a cardinality bound
    // The node is left with no owl:*Cardinality bound. Remove it (all its triples plus its
    // rdfs:subClassOf edge) only when no other *surviving* triple still references it in subject,
    // object, or predicate position — excluding its own (also-removed) structural / edge triples.
    const survivingReference =
      store.match(node, null, null).some((q) => !isRemoved(q) && !isStructuralEdge(node, q)) ||
      store.match(null, null, node).some((q) => !isRemoved(q) && !isStructuralEdge(node, q)) ||
      store.match(null, node, null).some((q) => !isRemoved(q) && !isStructuralEdge(node, q));
    if (survivingReference) continue;
    for (const q of store.match(node, null, null)) addRemoved(q);
    for (const q of store.match(typeNode, subClassOf, node)) addRemoved(q);
  }

  // No quad may appear in both `added` and `removed`.
  const removedKeySet = new Set(removedQuads.map(quadKey));
  const added = addedQuads.filter((q) => !removedKeySet.has(quadKey(q)));
  return { added, removed: removedQuads };
}

/**
 * Replace a transition's guard expression and its user-facing failure message. Passing `null` for
 * either argument deletes the corresponding triple; passing a string asserts (or re-asserts) it.
 * Quads that would end up in both `added` and `removed` are dropped from both, so a no-op call
 * returns an empty delta.
 */
export function setGuard(store: RdfStore, transition: Term, guard: string | null, message: string | null): QuadDelta {
  const subject = transition as Quad_Subject;
  const guardPredicate = namedNode(SQ.guard);
  const messagePredicate = namedNode(SQ.guardMessage);

  const existing: Quad[] = [...store.match(transition, guardPredicate, null), ...store.match(transition, messagePredicate, null)];

  const additions: Quad[] = [];
  if (guard !== null) {
    additions.push(quad(subject, guardPredicate, literal(guard)));
  }
  if (message !== null) {
    additions.push(quad(subject, messagePredicate, literal(message)));
  }

  return {
    added: dropShared(additions, existing),
    removed: dropShared(existing, additions),
  };
}

/**
 * Replace a property's `sq:derivedBy` rule with `rule`, or remove the rule entirely when `rule`
 * is `null`. Removes the existing `(propertyIri sq:derivedBy N)` edge, every triple whose subject
 * is the old rule node `N`, and any RDF list / `sq:Criterion` blank nodes `N` owns (reached by
 * following blank-object and blank-predicate edges transitively; shared named individuals are never
 * "owned" and so are left untouched). When `rule` is non-null, mints a fresh, unrelated blank node
 * carrying either the rollup shape — `rdf:type sq:Rollup`, `sq:function`, and `sq:over` / `sq:via`
 * / `sq:of` / `sq:filter` each as present — or the plugin shape — `rdf:type sq:Plugin`,
 * `sq:pluginName`. Never mutates `store`; no quad ends up in both `added` and `removed`.
 */
export function setDerivation(store: RdfStore, propertyIri: string, rule: DerivationSpec | null): QuadDelta {
  const propertyNode = namedNode(propertyIri);
  const derivedBy = namedNode(SQ.derivedBy);
  const rdfType = namedNode(RDF.type);

  // Collect the removed set: the `sq:derivedBy` edge(s) plus every triple on the old rule node (and
  // any blank-node sub-node it owns). Deduped by `quadKey` since `store.match` returns fresh Quads.
  const removedKeys = new Set<string>();
  const removed: Quad[] = [];
  const addRemoved = (q: Quad) => {
    const key = quadKey(q);
    if (!removedKeys.has(key)) {
      removedKeys.add(key);
      removed.push(q);
    }
  };

  const visited = new Set<string>();
  const visit = (node: Term) => {
    const label = `${node.termType}:${node.value}`;
    if (visited.has(label)) return;
    visited.add(label);
    for (const t of store.match(node, null, null)) {
      addRemoved(t);
      if (t.object.termType === 'BlankNode') visit(t.object);
    }
  };

  for (const edge of store.match(propertyNode, derivedBy, null)) {
    addRemoved(edge);
    visit(edge.object);
  }

  // Build the added set from a freshly minted blank node.
  const added: Quad[] = [];
  if (rule !== null) {
    const node = DataFactory.blankNode();
    added.push(quad(propertyNode, derivedBy, node));
    if (rule.kind === 'rollup') {
      added.push(quad(node, rdfType, namedNode(SQ.Rollup)));
      added.push(quad(node, namedNode(SQ.function), namedNode(SQ[rule.function as keyof typeof SQ])));
      if (rule.overIri !== null) {
        added.push(quad(node, namedNode(SQ.over), namedNode(rule.overIri)));
      }
      if (rule.viaIri !== null) {
        added.push(quad(node, namedNode(SQ.via), namedNode(rule.viaIri)));
      }
      if (rule.ofIri !== null) {
        added.push(quad(node, namedNode(SQ.of), namedNode(rule.ofIri)));
      }
      if (rule.filter !== null) {
        added.push(quad(node, namedNode(SQ.filter), literal(rule.filter)));
      }
    } else {
      added.push(quad(node, rdfType, namedNode(SQ.Plugin)));
      added.push(quad(node, namedNode(SQ.pluginName), literal(rule.pluginName)));
    }
  }

  return {
    added: dropShared(added, removed),
    removed: dropShared(removed, added),
  };
}
