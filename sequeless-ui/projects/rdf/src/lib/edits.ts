import { DataFactory } from 'n3';
import type { Quad_Subject } from 'n3';
import type { Quad, Term } from '@rdfjs/types';
import type { AttributePropertySpec, QuadDelta, RelationshipPropertySpec, TriggerView } from './model';
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
