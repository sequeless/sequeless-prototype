/*
 * Contract [OBJ-LOOKUP]: ObjectsRelationshipLookup implements ui-kit's
 * RelationshipLookup on top of ObjectsClient/TypesClient. search() delegates
 * to ObjectsClient.browse(targetTypeIri, {page:0,size:20,q:term,sort:[],
 * filters:[],facets:[]}) and returns the response's items. targetType()
 * delegates to TypesClient.describe and caches the observable per IRI
 * (shareReplay), so repeated calls for the same IRI invoke describe once.
 * provideObjectsRelationshipLookup() registers the service and aliases
 * ui-kit's RELATIONSHIP_LOOKUP token to the same instance.
 */
import { TestBed } from '@angular/core/testing';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import type { BusinessObjectResponse, TypeDetailResponse } from '@sequeless/core-api';
import { RELATIONSHIP_LOOKUP } from '@sequeless/ui-kit';
import { firstValueFrom, of } from 'rxjs';

import {
  ObjectsRelationshipLookup,
  provideObjectsRelationshipLookup,
} from './objects-relationship-lookup';

const TARGET_TYPE_IRI = 'https://example.org/ontology#Person';

const ALICE: BusinessObjectResponse = {
  id: 'person-1',
  type: 'Person',
  typeIri: TARGET_TYPE_IRI,
  version: 0,
  state: null,
  properties: { name: 'Alice' },
  audit: {
    createdAt: '2026-09-28T10:15:30Z',
    createdBy: 'anonymous',
    updatedAt: '2026-09-28T10:15:30Z',
    updatedBy: 'anonymous',
  },
};

const PERSON_TYPE_DETAIL: TypeDetailResponse = {
  iri: TARGET_TYPE_IRI,
  name: 'Person',
  label: 'Person',
  abstract: false,
  superTypes: [],
  group: null,
  hidden: false,
  properties: [],
  stateMachine: null,
};

describe('ObjectsRelationshipLookup', () => {
  let browseFn: ReturnType<typeof vi.fn>;
  let describeFn: ReturnType<typeof vi.fn>;
  let lookup: ObjectsRelationshipLookup;

  function setup() {
    browseFn = vi.fn(() =>
      of({ items: [ALICE], page: 0, size: 20, totalItems: 1, totalPages: 1, facets: {} }),
    );
    describeFn = vi.fn(() => of(PERSON_TYPE_DETAIL));

    TestBed.configureTestingModule({
      providers: [
        ...provideObjectsRelationshipLookup(),
        { provide: ObjectsClient, useValue: { browse: browseFn } },
        { provide: TypesClient, useValue: { describe: describeFn } },
      ],
    });
    lookup = TestBed.inject(ObjectsRelationshipLookup);
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  describe('search', () => {
    it('calls ObjectsClient.browse with page 0, size 20, q set to the term, and empty sort/filters/facets', async () => {
      setup();

      await firstValueFrom(lookup.search(TARGET_TYPE_IRI, 'ali'));

      expect(browseFn).toHaveBeenCalledWith(TARGET_TYPE_IRI, {
        page: 0,
        size: 20,
        q: 'ali',
        sort: [],
        filters: [],
        facets: [],
      });
    });

    it('resolves with the items from the browse response', async () => {
      setup();

      const result = await firstValueFrom(lookup.search(TARGET_TYPE_IRI, 'ali'));

      expect(result).toEqual([ALICE]);
    });

    it('issues a fresh browse call for each search() invocation', async () => {
      setup();

      await firstValueFrom(lookup.search(TARGET_TYPE_IRI, 'a'));
      await firstValueFrom(lookup.search(TARGET_TYPE_IRI, 'b'));

      expect(browseFn).toHaveBeenCalledTimes(2);
    });
  });

  describe('targetType', () => {
    it('delegates to TypesClient.describe with the target type IRI', async () => {
      setup();

      await firstValueFrom(lookup.targetType(TARGET_TYPE_IRI));

      expect(describeFn).toHaveBeenCalledWith(TARGET_TYPE_IRI);
    });

    it('resolves with the describe() response', async () => {
      setup();

      const result = await firstValueFrom(lookup.targetType(TARGET_TYPE_IRI));

      expect(result).toEqual(PERSON_TYPE_DETAIL);
    });

    it('caches the result per IRI: two calls for the same IRI only describe() once', async () => {
      setup();

      await firstValueFrom(lookup.targetType(TARGET_TYPE_IRI));
      await firstValueFrom(lookup.targetType(TARGET_TYPE_IRI));

      expect(describeFn).toHaveBeenCalledTimes(1);
    });

    it('describes separately for different target type IRIs', async () => {
      setup();
      const otherIri = 'https://example.org/ontology#Task';

      await firstValueFrom(lookup.targetType(TARGET_TYPE_IRI));
      await firstValueFrom(lookup.targetType(otherIri));

      expect(describeFn).toHaveBeenCalledTimes(2);
      expect(describeFn).toHaveBeenCalledWith(otherIri);
    });
  });

  describe('provideObjectsRelationshipLookup', () => {
    it('resolves RELATIONSHIP_LOOKUP to the same instance as ObjectsRelationshipLookup', () => {
      setup();

      const token = TestBed.inject(RELATIONSHIP_LOOKUP);

      expect(token).toBe(lookup);
    });
  });
});
