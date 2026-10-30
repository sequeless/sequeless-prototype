/*
 * queryCapabilities() decides which browse controls (filter / sort / facet) the
 * UI offers for a property, from its PropertyResponse alone.
 *
 * It is a BEST-EFFORT mirror of the server rule enforced by
 * DefaultBusinessObjectService.browse (docs/architecture/query-port.md):
 *   - multi-valued properties (cardinalityMax !== 1; null = unbounded) cannot
 *     be filtered, sorted or faceted;
 *   - derived properties are only queryable when materialised;
 *   - a facet requires sq:facet=true.
 * The server additionally rejects operator/datatype mismatches (e.g.
 * `contains` on an integer), which this helper does not model, so the UI must
 * still handle a 400 `invalid-query` problem+json from GET /api/objects/{type}.
 */
import {
  AttributePropertyResponse,
  DerivationResponse,
  PropertyResponse,
  RelationshipPropertyResponse,
} from '../models';

import { queryCapabilities } from './query-capabilities';

function attribute(overrides: Partial<AttributePropertyResponse> = {}): AttributePropertyResponse {
  return {
    kind: 'attribute',
    iri: 'https://example.org/ontology#priority',
    name: 'priority',
    label: 'Priority',
    order: 0,
    group: null,
    hidden: false,
    facet: false,
    indexed: false,
    searchable: false,
    readOnly: false,
    cardinalityMin: 0,
    cardinalityMax: 1,
    datatype: 'INTEGER',
    derivation: null,
    ...overrides,
  };
}

function relationship(
  overrides: Partial<RelationshipPropertyResponse> = {},
): RelationshipPropertyResponse {
  return {
    kind: 'relationship',
    iri: 'https://example.org/ontology#assignedTo',
    name: 'assignedTo',
    label: 'Assigned to',
    order: 1,
    group: null,
    hidden: false,
    facet: false,
    indexed: false,
    searchable: false,
    readOnly: false,
    cardinalityMin: 0,
    cardinalityMax: 1,
    targetTypeIri: 'https://example.org/ontology#Person',
    inverseIri: null,
    transitive: false,
    derivation: null,
    ...overrides,
  };
}

function derivation(materialised: boolean): DerivationResponse {
  return {
    kind: 'rollup',
    summary: "count(Task via belongsToProject where status ne 'done')",
    materialised,
  };
}

const NONE = { filterable: false, sortable: false, facetable: false };

describe('queryCapabilities', () => {
  it('marks a single-valued plain attribute as filterable and sortable but not facetable without sq:facet', () => {
    const property: PropertyResponse = attribute();

    expect(queryCapabilities(property)).toEqual({
      filterable: true,
      sortable: true,
      facetable: false,
    });
  });

  it('marks a single-valued attribute with facet=true as filterable, sortable and facetable', () => {
    expect(queryCapabilities(attribute({ facet: true }))).toEqual({
      filterable: true,
      sortable: true,
      facetable: true,
    });
  });

  it('disallows everything for an unbounded (cardinalityMax: null) attribute, even with facet=true', () => {
    expect(queryCapabilities(attribute({ cardinalityMax: null, facet: true }))).toEqual(NONE);
  });

  it('disallows everything for an attribute with cardinalityMax greater than 1', () => {
    expect(queryCapabilities(attribute({ cardinalityMax: 3, facet: true }))).toEqual(NONE);
  });

  it('disallows everything for a non-materialised derived attribute', () => {
    expect(
      queryCapabilities(
        attribute({
          name: 'openTaskCount',
          readOnly: true,
          facet: true,
          derivation: derivation(false),
        }),
      ),
    ).toEqual(NONE);
  });

  it('allows filtering and sorting on a materialised derived attribute', () => {
    expect(
      queryCapabilities(
        attribute({ name: 'openTaskCount', readOnly: true, derivation: derivation(true) }),
      ),
    ).toEqual({ filterable: true, sortable: true, facetable: false });
  });

  it('allows faceting on a materialised derived attribute marked facet=true', () => {
    expect(
      queryCapabilities(
        attribute({
          name: 'openTaskCount',
          readOnly: true,
          facet: true,
          derivation: derivation(true),
        }),
      ).facetable,
    ).toBe(true);
  });

  it('treats readOnly alone (without a derivation) as no obstacle to querying', () => {
    expect(queryCapabilities(attribute({ readOnly: true })).filterable).toBe(true);
  });

  it('marks a single-valued relationship as filterable (by target id) but not facetable without sq:facet', () => {
    const capabilities = queryCapabilities(relationship());

    expect(capabilities.filterable).toBe(true);
    expect(capabilities.facetable).toBe(false);
  });

  it('marks a single-valued relationship with facet=true as facetable', () => {
    const capabilities = queryCapabilities(relationship({ facet: true }));

    expect(capabilities.filterable).toBe(true);
    expect(capabilities.facetable).toBe(true);
  });

  it('disallows everything for an unbounded relationship such as Project.hasTask', () => {
    expect(
      queryCapabilities(
        relationship({
          name: 'hasTask',
          iri: 'https://example.org/ontology#hasTask',
          targetTypeIri: 'https://example.org/ontology#Task',
          cardinalityMax: null,
          facet: true,
        }),
      ),
    ).toEqual(NONE);
  });

  it('disallows everything for a non-materialised derived relationship', () => {
    expect(
      queryCapabilities(relationship({ facet: true, derivation: derivation(false) })),
    ).toEqual(NONE);
  });
});
