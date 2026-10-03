import { DataFactory } from 'n3';
import type { Quad } from '@rdfjs/types';
import type { AttributePropertySpec, QuadDelta, RelationshipPropertySpec } from './model';
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
