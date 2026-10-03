import type { Datatype } from './model';

export const SQ_NS = 'https://sequeless.dev/ns/meta#';

export const SQ = {
  // Phase 1
  label: 'https://sequeless.dev/ns/meta#label',
  displayOrder: 'https://sequeless.dev/ns/meta#displayOrder',
  displayGroup: 'https://sequeless.dev/ns/meta#displayGroup',
  hidden: 'https://sequeless.dev/ns/meta#hidden',
  facet: 'https://sequeless.dev/ns/meta#facet',
  indexed: 'https://sequeless.dev/ns/meta#indexed',
  searchable: 'https://sequeless.dev/ns/meta#searchable',
  readOnly: 'https://sequeless.dev/ns/meta#readOnly',
  displayLabel: 'https://sequeless.dev/ns/meta#displayLabel',
  abstract: 'https://sequeless.dev/ns/meta#abstract',
  // Phase 4 — derived properties
  derivedBy: 'https://sequeless.dev/ns/meta#derivedBy',
  function: 'https://sequeless.dev/ns/meta#function',
  over: 'https://sequeless.dev/ns/meta#over',
  via: 'https://sequeless.dev/ns/meta#via',
  of: 'https://sequeless.dev/ns/meta#of',
  filter: 'https://sequeless.dev/ns/meta#filter',
  property: 'https://sequeless.dev/ns/meta#property',
  operator: 'https://sequeless.dev/ns/meta#operator',
  value: 'https://sequeless.dev/ns/meta#value',
  pluginName: 'https://sequeless.dev/ns/meta#pluginName',
  // Phase 4 — structural classes
  Rollup: 'https://sequeless.dev/ns/meta#Rollup',
  Plugin: 'https://sequeless.dev/ns/meta#Plugin',
  Criterion: 'https://sequeless.dev/ns/meta#Criterion',
  // Phase 4 — sq:function individuals
  count: 'https://sequeless.dev/ns/meta#count',
  sum: 'https://sequeless.dev/ns/meta#sum',
  min: 'https://sequeless.dev/ns/meta#min',
  max: 'https://sequeless.dev/ns/meta#max',
  avg: 'https://sequeless.dev/ns/meta#avg',
  // Phase 4 — sq:operator individuals
  eq: 'https://sequeless.dev/ns/meta#eq',
  ne: 'https://sequeless.dev/ns/meta#ne',
  in: 'https://sequeless.dev/ns/meta#in',
  lt: 'https://sequeless.dev/ns/meta#lt',
  lte: 'https://sequeless.dev/ns/meta#lte',
  gt: 'https://sequeless.dev/ns/meta#gt',
  gte: 'https://sequeless.dev/ns/meta#gte',
  contains: 'https://sequeless.dev/ns/meta#contains',
  startsWith: 'https://sequeless.dev/ns/meta#startsWith',
  isNull: 'https://sequeless.dev/ns/meta#isNull',
  notNull: 'https://sequeless.dev/ns/meta#notNull',
  // Phase 5 — state machines and automation
  appliesTo: 'https://sequeless.dev/ns/meta#appliesTo',
  initialState: 'https://sequeless.dev/ns/meta#initialState',
  state: 'https://sequeless.dev/ns/meta#state',
  transition: 'https://sequeless.dev/ns/meta#transition',
  name: 'https://sequeless.dev/ns/meta#name',
  from: 'https://sequeless.dev/ns/meta#from',
  to: 'https://sequeless.dev/ns/meta#to',
  trigger: 'https://sequeless.dev/ns/meta#trigger',
  guard: 'https://sequeless.dev/ns/meta#guard',
  guardMessage: 'https://sequeless.dev/ns/meta#guardMessage',
  action: 'https://sequeless.dev/ns/meta#action',
  expression: 'https://sequeless.dev/ns/meta#expression',
  type: 'https://sequeless.dev/ns/meta#type',
  properties: 'https://sequeless.dev/ns/meta#properties',
  url: 'https://sequeless.dev/ns/meta#url',
  method: 'https://sequeless.dev/ns/meta#method',
  body: 'https://sequeless.dev/ns/meta#body',
  message: 'https://sequeless.dev/ns/meta#message',
  // Phase 5 — structural classes
  StateMachine: 'https://sequeless.dev/ns/meta#StateMachine',
  State: 'https://sequeless.dev/ns/meta#State',
  Transition: 'https://sequeless.dev/ns/meta#Transition',
  SetProperty: 'https://sequeless.dev/ns/meta#SetProperty',
  CreateObject: 'https://sequeless.dev/ns/meta#CreateObject',
  PropertyAssignment: 'https://sequeless.dev/ns/meta#PropertyAssignment',
  Webhook: 'https://sequeless.dev/ns/meta#Webhook',
  Log: 'https://sequeless.dev/ns/meta#Log',
  // Phase 6 — event-driven triggers and materialisation
  watch: 'https://sequeless.dev/ns/meta#watch',
  after: 'https://sequeless.dev/ns/meta#after',
  signalName: 'https://sequeless.dev/ns/meta#signalName',
  materialised: 'https://sequeless.dev/ns/meta#materialised',
  // Phase 6 — sq:trigger individuals
  UserAction: 'https://sequeless.dev/ns/meta#UserAction',
  OnChange: 'https://sequeless.dev/ns/meta#OnChange',
  Timer: 'https://sequeless.dev/ns/meta#Timer',
  ExternalSignal: 'https://sequeless.dev/ns/meta#ExternalSignal',
} as const;

export const OWL = {
  Class: 'http://www.w3.org/2002/07/owl#Class',
  DatatypeProperty: 'http://www.w3.org/2002/07/owl#DatatypeProperty',
  ObjectProperty: 'http://www.w3.org/2002/07/owl#ObjectProperty',
  Restriction: 'http://www.w3.org/2002/07/owl#Restriction',
  onProperty: 'http://www.w3.org/2002/07/owl#onProperty',
  cardinality: 'http://www.w3.org/2002/07/owl#cardinality',
  minCardinality: 'http://www.w3.org/2002/07/owl#minCardinality',
  maxCardinality: 'http://www.w3.org/2002/07/owl#maxCardinality',
  inverseOf: 'http://www.w3.org/2002/07/owl#inverseOf',
  TransitiveProperty: 'http://www.w3.org/2002/07/owl#TransitiveProperty',
} as const;

export const RDF = {
  type: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type',
} as const;

export const RDFS = {
  domain: 'http://www.w3.org/2000/01/rdf-schema#domain',
  range: 'http://www.w3.org/2000/01/rdf-schema#range',
  subClassOf: 'http://www.w3.org/2000/01/rdf-schema#subClassOf',
  label: 'http://www.w3.org/2000/01/rdf-schema#label',
} as const;

export const XSD = {
  nonNegativeInteger: 'http://www.w3.org/2001/XMLSchema#nonNegativeInteger',
  string: 'http://www.w3.org/2001/XMLSchema#string',
  boolean: 'http://www.w3.org/2001/XMLSchema#boolean',
  integer: 'http://www.w3.org/2001/XMLSchema#integer',
  long: 'http://www.w3.org/2001/XMLSchema#long',
  decimal: 'http://www.w3.org/2001/XMLSchema#decimal',
  double: 'http://www.w3.org/2001/XMLSchema#double',
  date: 'http://www.w3.org/2001/XMLSchema#date',
  time: 'http://www.w3.org/2001/XMLSchema#time',
  dateTime: 'http://www.w3.org/2001/XMLSchema#dateTime',
  duration: 'http://www.w3.org/2001/XMLSchema#duration',
  anyURI: 'http://www.w3.org/2001/XMLSchema#anyURI',
} as const;

export const DATATYPE_TO_XSD: Readonly<Record<Datatype, string>> = {
  STRING: 'http://www.w3.org/2001/XMLSchema#string',
  BOOLEAN: 'http://www.w3.org/2001/XMLSchema#boolean',
  INTEGER: 'http://www.w3.org/2001/XMLSchema#integer',
  LONG: 'http://www.w3.org/2001/XMLSchema#long',
  DECIMAL: 'http://www.w3.org/2001/XMLSchema#decimal',
  DOUBLE: 'http://www.w3.org/2001/XMLSchema#double',
  DATE: 'http://www.w3.org/2001/XMLSchema#date',
  TIME: 'http://www.w3.org/2001/XMLSchema#time',
  DATE_TIME: 'http://www.w3.org/2001/XMLSchema#dateTime',
  DURATION: 'http://www.w3.org/2001/XMLSchema#duration',
  ANY_URI: 'http://www.w3.org/2001/XMLSchema#anyURI',
};

const XSD_TO_DATATYPE: Record<string, Datatype> = (() => {
  const result: Record<string, Datatype> = {};
  for (const [datatype, iri] of Object.entries(DATATYPE_TO_XSD)) {
    result[iri] = datatype as Datatype;
  }
  return result;
})();

export function xsdToDatatype(iri: string): Datatype {
  return XSD_TO_DATATYPE[iri] ?? 'STRING';
}
