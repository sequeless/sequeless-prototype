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

export type TriggerKind = 'USER_ACTION' | 'ON_CHANGE' | 'TIMER' | 'EXTERNAL_SIGNAL';

export interface DerivationResponse {
  kind: 'rollup' | 'plugin';
  summary: string;
  materialised: boolean;
}

export interface AttributePropertyResponse {
  kind: 'attribute';
  iri: string;
  name: string;
  label: string;
  order: number;
  group: string | null;
  hidden: boolean;
  facet: boolean;
  indexed: boolean;
  searchable: boolean;
  readOnly: boolean;
  cardinalityMin: number;
  cardinalityMax: number | null;
  datatype: Datatype;
  derivation: DerivationResponse | null;
}

export interface RelationshipPropertyResponse {
  kind: 'relationship';
  iri: string;
  name: string;
  label: string;
  order: number;
  group: string | null;
  hidden: boolean;
  facet: boolean;
  indexed: boolean;
  searchable: boolean;
  readOnly: boolean;
  cardinalityMin: number;
  cardinalityMax: number | null;
  targetTypeIri: string;
  inverseIri: string | null;
  transitive: boolean;
  derivation: DerivationResponse | null;
}

export type PropertyResponse = AttributePropertyResponse | RelationshipPropertyResponse;

export function isAttributeProperty(p: PropertyResponse): p is AttributePropertyResponse {
  return p.kind === 'attribute';
}

export function isRelationshipProperty(p: PropertyResponse): p is RelationshipPropertyResponse {
  return p.kind === 'relationship';
}

export interface StateResponse {
  iri: string;
  name: string;
  label: string;
  displayOrder: number;
}

export interface TransitionSummaryResponse {
  name: string;
  from: string;
  to: string;
  hasGuard: boolean;
  trigger: TriggerKind;
  after: string | null;
  signalName: string | null;
  watch: readonly string[];
}

export interface StateMachineResponse {
  iri: string;
  states: readonly StateResponse[];
  initialState: string;
  transitions: readonly TransitionSummaryResponse[];
}

export interface TypeSummaryResponse {
  iri: string;
  name: string;
  label: string;
  abstract: boolean;
  superTypes: readonly string[];
}

export interface TypeDetailResponse {
  iri: string;
  name: string;
  label: string;
  abstract: boolean;
  superTypes: readonly string[];
  group: string | null;
  hidden: boolean;
  properties: readonly PropertyResponse[];
  stateMachine: StateMachineResponse | null;
}

export interface AuditResponse {
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
}

export interface BusinessObjectResponse {
  id: string;
  type: string;
  typeIri: string;
  version: number;
  state: string | null;
  properties: Record<string, unknown>;
  audit: AuditResponse;
}

export interface FacetBucketResponse {
  value: string;
  count: number;
}

export interface QueryResultResponse {
  items: readonly BusinessObjectResponse[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  facets: Record<string, readonly FacetBucketResponse[]>;
}

export interface CreateObjectRequest {
  properties: Record<string, unknown>;
}

export interface UpdateObjectRequest {
  version?: number | null;
  properties: Record<string, unknown>;
}

export interface TransitionRequest {
  expectedVersion?: number | null;
}

export interface TransitionResponse {
  name: string;
  available: boolean;
  reason: string | null;
}

export interface OntologyIssue {
  severity: string;
  subjectIri: string | null;
  message: string;
}

export interface OntologyReportResponse {
  consistent: boolean;
  issues: readonly OntologyIssue[];
}

export interface OntologyImportResponse {
  accepted: boolean;
  typeCount: number;
  consistent: boolean;
  issues: readonly OntologyIssue[];
}

export interface WhoAmIResponse {
  tenant: string;
  principal: string;
  displayName: string;
  roles: readonly string[];
  decision: {
    allowed: boolean;
    reason: string;
  };
}

export interface ErrorResponse {
  message: string;
}

export interface ViolationResponse {
  property: string | null;
  propertyIri: string | null;
  message: string;
}
