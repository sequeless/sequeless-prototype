/*
 * Contract [OBJ-COLUMNS] (projects/objects/src/lib/browse/browse-columns.ts, pure):
 * PAGE_SIZE_OPTIONS is the fixed page-size menu [10,20,50,100,200]. visibleColumns(type)
 * returns the type's non-hidden properties sorted by `order` ascending (null order last,
 * then by name). facetableProperties(type) returns the names of properties for which
 * queryCapabilities(p).facetable is true (not the raw `facet` flag — queryCapabilities
 * also requires cardinalityMax === 1 and a materialised derivation). operatorsFor(p)
 * returns the FilterOperator set for a property's datatype/kind: string/anyURI get
 * eq/ne/contains/startswith/in/isnull/notnull; numeric/date/time/duration get
 * eq/ne/gt/gte/lt/lte/isnull/notnull; boolean gets eq/isnull/notnull; relationship gets
 * eq/in/isnull/notnull.
 */
import type {
  AttributePropertyResponse,
  FilterOperator,
  PropertyResponse,
  RelationshipPropertyResponse,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';

import {
  PAGE_SIZE_OPTIONS,
  facetableProperties,
  operatorsFor,
  visibleColumns,
} from './browse-columns';

const PROJECT_DETAIL: TypeDetailResponse = PROJECT_TYPE_DETAIL;

function attribute(
  overrides: Partial<AttributePropertyResponse> & Pick<AttributePropertyResponse, 'name' | 'datatype'>,
): AttributePropertyResponse {
  return {
    kind: 'attribute',
    iri: `https://example.org/ontology#${overrides.name}`,
    label: overrides.name,
    order: 1,
    group: null,
    hidden: false,
    facet: false,
    indexed: false,
    searchable: false,
    readOnly: false,
    cardinalityMin: 0,
    cardinalityMax: 1,
    derivation: null,
    ...overrides,
  };
}

function relationship(
  overrides: Partial<RelationshipPropertyResponse> &
    Pick<RelationshipPropertyResponse, 'name' | 'targetTypeIri'>,
): RelationshipPropertyResponse {
  return {
    kind: 'relationship',
    iri: `https://example.org/ontology#${overrides.name}`,
    label: overrides.name,
    order: 1,
    group: null,
    hidden: false,
    facet: false,
    indexed: false,
    searchable: false,
    readOnly: false,
    cardinalityMin: 0,
    cardinalityMax: 1,
    inverseIri: null,
    transitive: false,
    derivation: null,
    ...overrides,
  };
}

describe('PAGE_SIZE_OPTIONS', () => {
  it('is the fixed page-size menu', () => {
    expect(PAGE_SIZE_OPTIONS).toEqual([10, 20, 50, 100, 200]);
  });
});

describe('visibleColumns', () => {
  it('drops hidden properties and orders the rest by `order`, tie-broken by name', () => {
    // title(1), hasTask(2), status(2), openTaskCount(3), totalEstimatedHours(4),
    // createdAt(6, hidden), owner(6), description(9) — hasTask sorts before status at order 2.
    const names = visibleColumns(PROJECT_DETAIL).map((p) => p.name);

    expect(names).toEqual([
      'title',
      'hasTask',
      'status',
      'openTaskCount',
      'totalEstimatedHours',
      'owner',
      'description',
    ]);
  });

  it('sorts a null `order` after every numeric order', () => {
    const type: TypeDetailResponse = {
      ...PROJECT_DETAIL,
      properties: [
        attribute({ name: 'zzz', datatype: 'STRING', order: null as unknown as number }),
        attribute({ name: 'bbb', datatype: 'STRING', order: 1 }),
        attribute({ name: 'aaa', datatype: 'STRING', order: 2 }),
      ],
    };

    expect(visibleColumns(type).map((p) => p.name)).toEqual(['bbb', 'aaa', 'zzz']);
  });

  it('breaks a tie between two null orders by name', () => {
    const type: TypeDetailResponse = {
      ...PROJECT_DETAIL,
      properties: [
        attribute({ name: 'zzz', datatype: 'STRING', order: null as unknown as number }),
        attribute({ name: 'aaa', datatype: 'STRING', order: null as unknown as number }),
      ],
    };

    expect(visibleColumns(type).map((p) => p.name)).toEqual(['aaa', 'zzz']);
  });
});

describe('facetableProperties', () => {
  it('returns the names of the project type properties that queryCapabilities marks facetable', () => {
    // Only `status` is facet:true with cardinalityMax 1; openTaskCount is facet:false.
    expect(facetableProperties(PROJECT_DETAIL)).toEqual(['status']);
  });

  it('excludes a facet:true property whose cardinalityMax is not 1, per queryCapabilities', () => {
    const type: TypeDetailResponse = {
      ...PROJECT_DETAIL,
      properties: [
        attribute({ name: 'single', datatype: 'STRING', facet: true, cardinalityMax: 1 }),
        attribute({ name: 'multi', datatype: 'STRING', facet: true, cardinalityMax: null }),
      ],
    };

    expect(facetableProperties(type)).toEqual(['single']);
  });
});

describe('operatorsFor', () => {
  function expectOperators(p: PropertyResponse, expected: readonly FilterOperator[]): void {
    expect(operatorsFor(p)).toEqual(expected);
  }

  it('returns string operators for a STRING attribute', () => {
    expectOperators(attribute({ name: 'title', datatype: 'STRING' }), [
      'eq',
      'ne',
      'contains',
      'startswith',
      'in',
      'isnull',
      'notnull',
    ]);
  });

  it('returns string operators for an ANY_URI attribute', () => {
    expectOperators(attribute({ name: 'homepage', datatype: 'ANY_URI' }), [
      'eq',
      'ne',
      'contains',
      'startswith',
      'in',
      'isnull',
      'notnull',
    ]);
  });

  it('returns numeric operators for an INTEGER attribute', () => {
    expectOperators(attribute({ name: 'openTaskCount', datatype: 'INTEGER' }), [
      'eq',
      'ne',
      'gt',
      'gte',
      'lt',
      'lte',
      'isnull',
      'notnull',
    ]);
  });

  it('returns numeric operators for a DATE attribute', () => {
    expectOperators(attribute({ name: 'dueDate', datatype: 'DATE' }), [
      'eq',
      'ne',
      'gt',
      'gte',
      'lt',
      'lte',
      'isnull',
      'notnull',
    ]);
  });

  it('returns eq/isnull/notnull for a BOOLEAN attribute', () => {
    expectOperators(attribute({ name: 'archived', datatype: 'BOOLEAN' }), [
      'eq',
      'isnull',
      'notnull',
    ]);
  });

  it('returns eq/in/isnull/notnull for a relationship property', () => {
    expectOperators(
      relationship({ name: 'owner', targetTypeIri: 'https://example.org/ontology#Person' }),
      ['eq', 'in', 'isnull', 'notnull'],
    );
  });
});
