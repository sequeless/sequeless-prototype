/*
 * The core-api wire models are hand-written TypeScript interfaces mirroring the Java DTO records
 * in sequeless-app's org.sequeless.app.rest package field-for-field — deliberately not
 * OpenAPI-generated, because PropertyResponse is a sealed Java interface discriminated by a plain
 * `kind` string ("attribute" / "relationship") that springdoc cannot express as a TS union.
 *
 * Most of this spec is compile-time: `expectTypeOf` pins each interface's exact key set and the
 * exact type of every nullable/union-typed field, and typed sample literals rely on excess- and
 * missing-property checks. Nullable Java fields (Integer, String, nested records the mapper fills
 * with `orElse(null)`) are `T | null` — never optional — because Jackson always writes the key.
 * Collections are `readonly T[]` (the `as const` Project fixture only assigns to readonly arrays).
 */
import { PROJECT_TYPE_DETAIL } from '../../../../testing/src/lib/fixtures/project-type-detail';
import {
  AttributePropertyResponse,
  AuditResponse,
  BusinessObjectResponse,
  CreateObjectRequest,
  Datatype,
  DerivationResponse,
  ErrorResponse,
  FacetBucketResponse,
  isAttributeProperty,
  isRelationshipProperty,
  OntologyImportResponse,
  OntologyIssue,
  OntologyReportResponse,
  PropertyResponse,
  QueryResultResponse,
  RelationshipPropertyResponse,
  StateMachineResponse,
  StateResponse,
  TransitionRequest,
  TransitionResponse,
  TransitionSummaryResponse,
  TriggerKind,
  TypeDetailResponse,
  TypeSummaryResponse,
  UpdateObjectRequest,
  ViolationResponse,
  WhoAmIResponse,
} from './index';

const REF = 'https://sequeless.dev/ns/ref#';

describe('core-api wire models', () => {
  describe('string unions', () => {
    it('Datatype is exactly the eleven org.sequeless.spi.meta.Datatype enum names', () => {
      expectTypeOf<Datatype>().toEqualTypeOf<
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
        | 'ANY_URI'
      >();
    });

    it('TriggerKind is exactly the four TriggerKind#name() values TransitionSummaryResponse carries', () => {
      expectTypeOf<TriggerKind>().toEqualTypeOf<
        'USER_ACTION' | 'ON_CHANGE' | 'TIMER' | 'EXTERNAL_SIGNAL'
      >();
    });
  });

  describe('type descriptors', () => {
    it('TypeSummaryResponse uses the JSON field name "abstract" (from @JsonProperty), never "isAbstract"', () => {
      expectTypeOf<keyof TypeSummaryResponse>().toEqualTypeOf<
        'iri' | 'name' | 'label' | 'abstract' | 'superTypes'
      >();
      expectTypeOf<TypeSummaryResponse['abstract']>().toEqualTypeOf<boolean>();
      expectTypeOf<TypeSummaryResponse>().not.toHaveProperty('isAbstract');
      expectTypeOf<TypeSummaryResponse['superTypes']>().toEqualTypeOf<readonly string[]>();

      const summary: TypeSummaryResponse = {
        iri: `${REF}Deliverable`,
        name: 'Deliverable',
        label: 'Deliverable',
        abstract: true,
        superTypes: [],
      };
      expect(summary.abstract).toBe(true);
    });

    it('TypeDetailResponse has "abstract", a nullable group and a nullable stateMachine', () => {
      expectTypeOf<keyof TypeDetailResponse>().toEqualTypeOf<
        | 'iri'
        | 'name'
        | 'label'
        | 'abstract'
        | 'superTypes'
        | 'group'
        | 'hidden'
        | 'properties'
        | 'stateMachine'
      >();
      expectTypeOf<TypeDetailResponse>().not.toHaveProperty('isAbstract');
      expectTypeOf<TypeDetailResponse['group']>().toEqualTypeOf<string | null>();
      expectTypeOf<TypeDetailResponse['stateMachine']>().toEqualTypeOf<StateMachineResponse | null>();
      expectTypeOf<TypeDetailResponse['properties']>().toEqualTypeOf<readonly PropertyResponse[]>();
    });

    it('accepts the realistic ex:Project fixture (derived properties, relationships, 7 transitions) as a TypeDetailResponse', () => {
      // The assignment itself is the contract: it fails to compile if any field name, literal
      // union or nullability in the models drifts from the wire shape the fixture encodes.
      const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;

      expect(detail.name).toBe('Project');
      expect(detail.abstract).toBe(false);
      expect(detail.properties.map((p) => p.name)).toEqual([
        'title',
        'hasTask',
        'status',
        'openTaskCount',
        'totalEstimatedHours',
        'createdAt',
        'owner',
        'description',
      ]);
      expect(detail.stateMachine?.initialState).toBe('Draft');
      expect(detail.stateMachine?.states.map((s) => s.name)).toEqual([
        'Draft',
        'Active',
        'OnHold',
        'Closed',
      ]);
      expect(detail.stateMachine?.transitions).toHaveLength(7);
    });

    it('accepts a type with no state machine (stateMachine: null), like ex:Task', () => {
      const task: TypeDetailResponse = {
        iri: `${REF}Task`,
        name: 'Task',
        label: 'Task',
        abstract: false,
        superTypes: ['WorkItem', 'Deliverable'],
        group: null,
        hidden: false,
        properties: [],
        stateMachine: null,
      };
      expect(task.stateMachine).toBeNull();
    });
  });

  describe('PropertyResponse discriminated union', () => {
    it('PropertyResponse is exactly AttributePropertyResponse | RelationshipPropertyResponse, discriminated by kind', () => {
      expectTypeOf<PropertyResponse>().toEqualTypeOf<
        AttributePropertyResponse | RelationshipPropertyResponse
      >();
      expectTypeOf<AttributePropertyResponse['kind']>().toEqualTypeOf<'attribute'>();
      expectTypeOf<RelationshipPropertyResponse['kind']>().toEqualTypeOf<'relationship'>();
      expectTypeOf<PropertyResponse['kind']>().toEqualTypeOf<'attribute' | 'relationship'>();
    });

    it('AttributePropertyResponse mirrors the Java record: typed datatype, nullable group/cardinalityMax/derivation', () => {
      expectTypeOf<keyof AttributePropertyResponse>().toEqualTypeOf<
        | 'kind'
        | 'iri'
        | 'name'
        | 'label'
        | 'order'
        | 'group'
        | 'hidden'
        | 'facet'
        | 'indexed'
        | 'searchable'
        | 'readOnly'
        | 'cardinalityMin'
        | 'cardinalityMax'
        | 'datatype'
        | 'derivation'
      >();
      expectTypeOf<AttributePropertyResponse['datatype']>().toEqualTypeOf<Datatype>();
      expectTypeOf<AttributePropertyResponse['group']>().toEqualTypeOf<string | null>();
      expectTypeOf<AttributePropertyResponse['cardinalityMin']>().toEqualTypeOf<number>();
      expectTypeOf<AttributePropertyResponse['cardinalityMax']>().toEqualTypeOf<number | null>();
      expectTypeOf<AttributePropertyResponse['derivation']>().toEqualTypeOf<DerivationResponse | null>();
    });

    it('RelationshipPropertyResponse mirrors the Java record: targetTypeIri, nullable inverseIri, transitive, no datatype', () => {
      expectTypeOf<keyof RelationshipPropertyResponse>().toEqualTypeOf<
        | 'kind'
        | 'iri'
        | 'name'
        | 'label'
        | 'order'
        | 'group'
        | 'hidden'
        | 'facet'
        | 'indexed'
        | 'searchable'
        | 'readOnly'
        | 'cardinalityMin'
        | 'cardinalityMax'
        | 'targetTypeIri'
        | 'inverseIri'
        | 'transitive'
        | 'derivation'
      >();
      expectTypeOf<RelationshipPropertyResponse['targetTypeIri']>().toEqualTypeOf<string>();
      expectTypeOf<RelationshipPropertyResponse['inverseIri']>().toEqualTypeOf<string | null>();
      expectTypeOf<RelationshipPropertyResponse['transitive']>().toEqualTypeOf<boolean>();
      expectTypeOf<RelationshipPropertyResponse['cardinalityMax']>().toEqualTypeOf<number | null>();
      expectTypeOf<RelationshipPropertyResponse>().not.toHaveProperty('datatype');
    });

    it('accepts cardinalityMax: null for an unbounded property (ex:title on Project)', () => {
      const title: AttributePropertyResponse = {
        kind: 'attribute',
        iri: `${REF}title`,
        name: 'title',
        label: 'Title',
        order: 1,
        group: 'General',
        hidden: false,
        facet: false,
        indexed: false,
        searchable: true,
        readOnly: false,
        cardinalityMin: 0,
        cardinalityMax: null,
        datatype: 'STRING',
        derivation: null,
      };
      expect(title.cardinalityMax).toBeNull();

      const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;
      expect(detail.properties.find((p) => p.name === 'title')?.cardinalityMax).toBeNull();
      expect(detail.properties.find((p) => p.name === 'status')?.cardinalityMax).toBe(1);
    });

    it('DerivationResponse.kind is "rollup" | "plugin" and the fixture carries both derived Project properties', () => {
      expectTypeOf<keyof DerivationResponse>().toEqualTypeOf<'kind' | 'summary' | 'materialised'>();
      expectTypeOf<DerivationResponse['kind']>().toEqualTypeOf<'rollup' | 'plugin'>();
      expectTypeOf<DerivationResponse['materialised']>().toEqualTypeOf<boolean>();

      const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;
      const derived = detail.properties.filter((p) => p.derivation !== null);
      expect(derived.map((p) => p.name)).toEqual(['openTaskCount', 'totalEstimatedHours']);
      expect(derived.every((p) => p.readOnly)).toBe(true);
      expect(derived.map((p) => p.derivation?.materialised)).toEqual([true, false]);

      const plugin: DerivationResponse = {
        kind: 'plugin',
        summary: 'plugin(riskScore)',
        materialised: false,
      };
      expect(plugin.kind).toBe('plugin');
    });
  });

  describe('isAttributeProperty / isRelationshipProperty', () => {
    it('are type guards narrowing PropertyResponse on kind', () => {
      expectTypeOf(isAttributeProperty).guards.toEqualTypeOf<AttributePropertyResponse>();
      expectTypeOf(isRelationshipProperty).guards.toEqualTypeOf<RelationshipPropertyResponse>();
      expectTypeOf(isAttributeProperty).parameter(0).toEqualTypeOf<PropertyResponse>();
      expectTypeOf(isRelationshipProperty).parameter(0).toEqualTypeOf<PropertyResponse>();
    });

    it('narrow each fixture property so datatype is reachable only on attributes and targetTypeIri only on relationships', () => {
      const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;
      const datatypes: Datatype[] = [];
      const targets: string[] = [];

      for (const p of detail.properties) {
        if (isAttributeProperty(p)) {
          // Compiles only if the guard narrows to AttributePropertyResponse.
          const datatype: Datatype = p.datatype;
          datatypes.push(datatype);
        }
        if (isRelationshipProperty(p)) {
          // Compiles only if the guard narrows to RelationshipPropertyResponse.
          const target: string = p.targetTypeIri;
          targets.push(target);
        }
      }

      expect(datatypes).toEqual(['STRING', 'STRING', 'INTEGER', 'DECIMAL', 'DATE_TIME', 'STRING']);
      expect(targets).toEqual([`${REF}Task`, `${REF}Person`]);
    });

    it('return true for exactly one of the two guards per property, keyed off kind', () => {
      const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;

      for (const p of detail.properties) {
        expect(isAttributeProperty(p)).toBe(p.kind === 'attribute');
        expect(isRelationshipProperty(p)).toBe(p.kind === 'relationship');
        expect(isAttributeProperty(p)).not.toBe(isRelationshipProperty(p));
      }
    });
  });

  describe('state machine descriptors', () => {
    it('StateMachineResponse and StateResponse mirror the Java records', () => {
      expectTypeOf<keyof StateMachineResponse>().toEqualTypeOf<
        'iri' | 'states' | 'initialState' | 'transitions'
      >();
      expectTypeOf<StateMachineResponse['states']>().toEqualTypeOf<readonly StateResponse[]>();
      expectTypeOf<StateMachineResponse['transitions']>().toEqualTypeOf<
        readonly TransitionSummaryResponse[]
      >();
      expectTypeOf<keyof StateResponse>().toEqualTypeOf<'iri' | 'name' | 'label' | 'displayOrder'>();
      expectTypeOf<StateResponse['displayOrder']>().toEqualTypeOf<number>();
    });

    it('TransitionSummaryResponse carries trigger kind plus nullable after/signalName and a non-null watch array', () => {
      expectTypeOf<keyof TransitionSummaryResponse>().toEqualTypeOf<
        'name' | 'from' | 'to' | 'hasGuard' | 'trigger' | 'after' | 'signalName' | 'watch'
      >();
      expectTypeOf<TransitionSummaryResponse['trigger']>().toEqualTypeOf<TriggerKind>();
      expectTypeOf<TransitionSummaryResponse['after']>().toEqualTypeOf<string | null>();
      expectTypeOf<TransitionSummaryResponse['signalName']>().toEqualTypeOf<string | null>();
      expectTypeOf<TransitionSummaryResponse['watch']>().toEqualTypeOf<readonly string[]>();
    });

    it('fixture transitions render one trigger kind each for the automated ones, and USER_ACTION for the rest', () => {
      const detail: TypeDetailResponse = PROJECT_TYPE_DETAIL;
      const transitions = detail.stateMachine?.transitions ?? [];
      const byName = (name: string): TransitionSummaryResponse | undefined =>
        transitions.find((t) => t.name === name);

      expect(byName('autoClose')).toMatchObject({
        trigger: 'ON_CHANGE',
        watch: ['belongsToProject'],
        after: null,
        signalName: null,
      });
      expect(byName('expireHold')).toMatchObject({
        trigger: 'TIMER',
        after: 'PT72H',
        signalName: null,
        watch: [],
      });
      expect(byName('reopen')).toMatchObject({
        trigger: 'EXTERNAL_SIGNAL',
        signalName: 'reopen',
        after: null,
        watch: [],
      });
      for (const name of ['activate', 'hold', 'resume', 'close']) {
        expect(byName(name)?.trigger).toBe('USER_ACTION');
      }
    });
  });

  describe('objects, queries and transitions', () => {
    it('BusinessObjectResponse has a nullable state and a nested AuditResponse with ISO-8601 instant strings', () => {
      expectTypeOf<keyof BusinessObjectResponse>().toEqualTypeOf<
        'id' | 'type' | 'typeIri' | 'version' | 'state' | 'properties' | 'audit'
      >();
      expectTypeOf<BusinessObjectResponse['state']>().toEqualTypeOf<string | null>();
      expectTypeOf<BusinessObjectResponse['version']>().toEqualTypeOf<number>();
      expectTypeOf<BusinessObjectResponse['audit']>().toEqualTypeOf<AuditResponse>();
      expectTypeOf<keyof AuditResponse>().toEqualTypeOf<
        'createdAt' | 'createdBy' | 'updatedAt' | 'updatedBy'
      >();
      expectTypeOf<AuditResponse['createdAt']>().toEqualTypeOf<string>();

      const task: BusinessObjectResponse = {
        id: '3f1c2a8e-5b7d-4e0f-9a61-2c4d8e6f0b13',
        type: 'Task',
        typeIri: `${REF}Task`,
        version: 0,
        state: null,
        properties: { title: ['Write specs'], estimatedHours: 2.5, assignedTo: null },
        audit: {
          createdAt: '2026-09-28T10:15:30Z',
          createdBy: 'anonymous',
          updatedAt: '2026-09-28T10:15:30Z',
          updatedBy: 'anonymous',
        },
      };
      expect(task.state).toBeNull();
      expect(task.properties['estimatedHours']).toBe(2.5);
    });

    it('QueryResultResponse carries paging numbers and facets keyed by property short name', () => {
      expectTypeOf<keyof QueryResultResponse>().toEqualTypeOf<
        'items' | 'page' | 'size' | 'totalItems' | 'totalPages' | 'facets'
      >();
      expectTypeOf<QueryResultResponse['items']>().toEqualTypeOf<readonly BusinessObjectResponse[]>();
      expectTypeOf<QueryResultResponse['facets'][string]>().toEqualTypeOf<
        readonly FacetBucketResponse[]
      >();
      expectTypeOf<keyof FacetBucketResponse>().toEqualTypeOf<'value' | 'count'>();

      const result: QueryResultResponse = {
        items: [],
        page: 0,
        size: 20,
        totalItems: 0,
        totalPages: 0,
        facets: { status: [{ value: 'open', count: 3 }] },
      };
      expect(result.facets['status']?.[0]?.count).toBe(3);
    });

    it('CreateObjectRequest and UpdateObjectRequest mirror the Java records, with an optional nullable version', () => {
      expectTypeOf<keyof CreateObjectRequest>().toEqualTypeOf<'properties'>();
      expectTypeOf<keyof UpdateObjectRequest>().toEqualTypeOf<'version' | 'properties'>();
      expectTypeOf<UpdateObjectRequest['version']>().toEqualTypeOf<number | null | undefined>();

      const create: CreateObjectRequest = { properties: { title: ['Kickoff'] } };
      const viaIfMatch: UpdateObjectRequest = { properties: { title: ['Kickoff'] } };
      const explicitNull: UpdateObjectRequest = { version: null, properties: {} };
      const withVersion: UpdateObjectRequest = { version: 4, properties: {} };
      expect([create, viaIfMatch, explicitNull, withVersion]).toHaveLength(4);
    });

    it('TransitionRequest accepts a numeric or null expectedVersion; TransitionResponse has a nullable reason', () => {
      expectTypeOf<keyof TransitionRequest>().toEqualTypeOf<'expectedVersion'>();
      expectTypeOf<NonNullable<TransitionRequest['expectedVersion']>>().toEqualTypeOf<number>();
      const withVersion: TransitionRequest = { expectedVersion: 2 };
      const viaIfMatch: TransitionRequest = { expectedVersion: null };
      expect([withVersion, viaIfMatch]).toHaveLength(2);

      expectTypeOf<keyof TransitionResponse>().toEqualTypeOf<'name' | 'available' | 'reason'>();
      expectTypeOf<TransitionResponse['reason']>().toEqualTypeOf<string | null>();
    });
  });

  describe('ontology, identity and errors', () => {
    it('OntologyIssue has a nullable subjectIri; OntologyReportResponse and OntologyImportResponse carry issues', () => {
      expectTypeOf<keyof OntologyIssue>().toEqualTypeOf<'severity' | 'subjectIri' | 'message'>();
      expectTypeOf<OntologyIssue['subjectIri']>().toEqualTypeOf<string | null>();
      expectTypeOf<OntologyIssue['severity']>().toExtend<string>();

      expectTypeOf<keyof OntologyReportResponse>().toEqualTypeOf<'consistent' | 'issues'>();
      expectTypeOf<OntologyReportResponse['issues']>().toEqualTypeOf<readonly OntologyIssue[]>();

      expectTypeOf<keyof OntologyImportResponse>().toEqualTypeOf<
        'accepted' | 'typeCount' | 'consistent' | 'issues'
      >();
      expectTypeOf<OntologyImportResponse['issues']>().toEqualTypeOf<readonly OntologyIssue[]>();

      const report: OntologyReportResponse = {
        consistent: false,
        issues: [{ severity: 'ERROR', subjectIri: null, message: 'Ontology is inconsistent' }],
      };
      expect(report.issues[0]?.subjectIri).toBeNull();
    });

    it('WhoAmIResponse carries tenant, principal, displayName, roles and a nested decision', () => {
      expectTypeOf<keyof WhoAmIResponse>().toEqualTypeOf<
        'tenant' | 'principal' | 'displayName' | 'roles' | 'decision'
      >();
      expectTypeOf<WhoAmIResponse['roles']>().toEqualTypeOf<readonly string[]>();
      expectTypeOf<keyof WhoAmIResponse['decision']>().toEqualTypeOf<'allowed' | 'reason'>();
      expectTypeOf<WhoAmIResponse['decision']['allowed']>().toEqualTypeOf<boolean>();
    });

    it('ErrorResponse is the legacy {message} body and ViolationResponse has nullable property/propertyIri', () => {
      expectTypeOf<keyof ErrorResponse>().toEqualTypeOf<'message'>();
      expectTypeOf<ErrorResponse['message']>().toEqualTypeOf<string>();

      expectTypeOf<keyof ViolationResponse>().toEqualTypeOf<'property' | 'propertyIri' | 'message'>();
      expectTypeOf<ViolationResponse['property']>().toEqualTypeOf<string | null>();
      expectTypeOf<ViolationResponse['propertyIri']>().toEqualTypeOf<string | null>();

      const objectLevel: ViolationResponse = {
        property: null,
        propertyIri: null,
        message: 'Object-level constraint failed',
      };
      expect(objectLevel.property).toBeNull();
    });
  });
});
