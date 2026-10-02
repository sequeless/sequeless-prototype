import type { Quad, Term } from '@rdfjs/types';

/**
 * The eleven scalar datatypes a `rdfs:range` can map to on an attribute property. Copied verbatim
 * from `@sequeless/core-api`'s `Datatype` (projects/core-api/src/lib/models/index.ts) — this
 * library must not import that package, so the union is duplicated here instead.
 */
export type Datatype =
  | 'STRING'
  | 'BOOLEAN'
  | 'INTEGER'
  | 'LONG'
  | 'DECIMAL'
  | 'DOUBLE'
  | 'DATE'
  | 'TIME'
  | 'DATE_TIME'
  | 'DURATION'
  | 'ANY_URI';

/** A targeted set of quads to remove and add; applying it must never leave a quad in both sets. */
export interface QuadDelta {
  readonly added: readonly Quad[];
  readonly removed: readonly Quad[];
}

/** An OWL cardinality restriction on a (type, property) pair. `max: null` means unbounded. */
export interface Cardinality {
  min: number;
  max: number | null;
}

/** A read-only projection of an `owl:Class` and its `sq:` display hints. */
export interface ClassView {
  iri: string;
  label: string | null;
  abstract: boolean;
  superClassIris: string[];
  order: number | null;
  group: string | null;
  hidden: boolean;
}

/** A rollup derivation — `sq:Rollup` — aggregating a related type's property via a relationship. */
export interface RollupDerivationView {
  kind: 'rollup';
  function: string;
  overIri: string | null;
  viaIri: string | null;
  ofIri: string | null;
  filter: string | null;
  materialised: boolean;
}

/** A plugin derivation — `sq:Plugin` — delegating computation to a named server-side plugin. */
export interface PluginDerivationView {
  kind: 'plugin';
  pluginName: string;
  materialised: boolean;
}

/** A property's `sq:derivedBy` rule, discriminated by shape (`sq:Rollup` vs `sq:Plugin`). */
export type DerivationView = RollupDerivationView | PluginDerivationView;

/** Fields shared by both kinds of `PropertyView`. */
interface PropertyViewBase {
  iri: string;
  label: string | null;
  domainIri: string;
  order: number | null;
  group: string | null;
  hidden: boolean;
  facet: boolean;
  indexed: boolean;
  searchable: boolean;
  /** Effective read-only: true if `sq:derivedBy` is present, or `sq:readOnly` is true. */
  readOnly: boolean;
  derivation: DerivationView | null;
}

/** A read-only projection of an `owl:DatatypeProperty`. */
export interface AttributePropertyView extends PropertyViewBase {
  kind: 'attribute';
  datatype: Datatype;
}

/** A read-only projection of an `owl:ObjectProperty`. */
export interface RelationshipPropertyView extends PropertyViewBase {
  kind: 'relationship';
  targetTypeIri: string;
  inverseIri: string | null;
  transitive: boolean;
}

/** A read-only projection of a property, discriminated by `kind`. */
export type PropertyView = AttributePropertyView | RelationshipPropertyView;

/** A transition's firing condition, discriminated by `kind`; mirrors `sq:trigger`'s four values. */
export type TriggerView =
  | { kind: 'USER_ACTION' }
  | { kind: 'ON_CHANGE'; watchIris: string[] }
  | { kind: 'TIMER'; after: string }
  | { kind: 'EXTERNAL_SIGNAL'; signalName: string };

/** A read-only projection of a `sq:Transition` node. `node` identifies it for targeted edits. */
export interface TransitionView {
  node: Term;
  name: string | null;
  fromIri: string;
  toIri: string;
  trigger: TriggerView;
  guard: string | null;
  guardMessage: string | null;
}

/** A read-only projection of a `sq:State` individual. */
export interface StateView {
  iri: string;
  label: string | null;
  order: number | null;
}

/** A read-only projection of a type's `sq:StateMachine`, if it declares one. */
export interface StateMachineView {
  iri: string;
  appliesToIri: string;
  initialStateIri: string | null;
  states: StateView[];
  transitions: TransitionView[];
}

/** Input to `addAttributeProperty`: the minimum to mint a new `owl:DatatypeProperty`. */
export interface AttributePropertySpec {
  iri: string;
  domainIri: string;
  datatype: Datatype;
  label?: string;
  order?: number;
  group?: string;
  facet?: boolean;
  indexed?: boolean;
  searchable?: boolean;
  hidden?: boolean;
}

/** Input to `addRelationshipProperty`: the minimum to mint a new `owl:ObjectProperty`. */
export interface RelationshipPropertySpec {
  iri: string;
  domainIri: string;
  targetTypeIri: string;
  label?: string;
  inverseIri?: string;
  transitive?: boolean;
}

/** Input to `setDerivation`: a `DerivationView` shape without the read-only `materialised` flag. */
export type DerivationSpec =
  | Omit<RollupDerivationView, 'materialised'>
  | Omit<PluginDerivationView, 'materialised'>;
