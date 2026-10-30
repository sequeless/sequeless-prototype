/*
 * Contract [OBJ-VIOLATIONS] (projects/objects/src/lib/browse/violation-mapping.ts, pure):
 * mapViolationsToFilters(filters, err) only inspects violations when err is the
 * 'invalid-query' problem (slug ends 'invalid-query'); every other ApiError yields an
 * empty FilterErrors. Each violation whose `property` matches a filter clause's `property`
 * contributes its message to `byIndex` keyed by that clause's index (first match wins when
 * several clauses share a property); violations matching no filter property land in
 * `unmatched`.
 */
import { ApiError, FilterClause } from '@sequeless/core-api';

import { mapViolationsToFilters } from './violation-mapping';

function apiError(overrides: Partial<ApiError>): ApiError {
  return {
    status: 400,
    kind: 'problem',
    title: 'Invalid query',
    detail: null,
    slug: 'invalid-query',
    violations: [],
    issues: [],
    expectedVersion: null,
    transitionName: null,
    typeIri: null,
    signalName: null,
    raw: null,
    ...overrides,
  };
}

describe('mapViolationsToFilters', () => {
  it('maps a violation to the index of the filter clause with the matching property', () => {
    const filters: readonly FilterClause[] = [
      { property: 'status', op: 'eq', value: 'Open' },
      { property: 'title', op: 'contains', value: 'report' },
    ];
    const err = apiError({
      violations: [{ property: 'title', propertyIri: null, message: 'unknown operator' }],
    });

    const result = mapViolationsToFilters(filters, err);

    expect(result.byIndex.get(1)).toBe('unknown operator');
    expect(result.byIndex.size).toBe(1);
    expect(result.unmatched).toEqual([]);
  });

  it('puts a violation for an unknown property into unmatched', () => {
    const filters: readonly FilterClause[] = [{ property: 'status', op: 'eq', value: 'Open' }];
    const err = apiError({
      violations: [{ property: 'nonexistent', propertyIri: null, message: 'no such property' }],
    });

    const result = mapViolationsToFilters(filters, err);

    expect(result.byIndex.size).toBe(0);
    expect(result.unmatched).toEqual(['no such property']);
  });

  it('returns an empty result for an error that is not the invalid-query problem', () => {
    const filters: readonly FilterClause[] = [{ property: 'status', op: 'eq', value: 'Open' }];
    const err = apiError({
      slug: 'validation',
      violations: [{ property: 'status', propertyIri: null, message: 'should be ignored' }],
    });

    const result = mapViolationsToFilters(filters, err);

    expect(result.byIndex.size).toBe(0);
    expect(result.unmatched).toEqual([]);
  });

  it('gives the first matching filter clause the violation when several clauses share a property', () => {
    const filters: readonly FilterClause[] = [
      { property: 'dueDate', op: 'gte', value: '2026-01-01' },
      { property: 'dueDate', op: 'lte', value: '2026-03-31' },
    ];
    const err = apiError({
      violations: [{ property: 'dueDate', propertyIri: null, message: 'range is invalid' }],
    });

    const result = mapViolationsToFilters(filters, err);

    expect(result.byIndex.get(0)).toBe('range is invalid');
    expect(result.byIndex.has(1)).toBe(false);
  });

  it('splits multiple violations between matched and unmatched', () => {
    const filters: readonly FilterClause[] = [
      { property: 'status', op: 'eq', value: 'Open' },
      { property: 'title', op: 'contains', value: 'report' },
    ];
    const err = apiError({
      violations: [
        { property: 'status', propertyIri: null, message: 'bad status value' },
        { property: 'owner', propertyIri: null, message: 'no such property' },
      ],
    });

    const result = mapViolationsToFilters(filters, err);

    expect(result.byIndex.get(0)).toBe('bad status value');
    expect(result.unmatched).toEqual(['no such property']);
  });

  it('treats an object-level violation (null property) as unmatched', () => {
    const filters: readonly FilterClause[] = [{ property: 'status', op: 'eq', value: 'Open' }];
    const err = apiError({
      violations: [{ property: null, propertyIri: null, message: 'object-level constraint failed' }],
    });

    const result = mapViolationsToFilters(filters, err);

    expect(result.byIndex.size).toBe(0);
    expect(result.unmatched).toEqual(['object-level constraint failed']);
  });

  it('returns an empty result when there are no filters and no violations', () => {
    const result = mapViolationsToFilters([], apiError({}));

    expect(result.byIndex.size).toBe(0);
    expect(result.unmatched).toEqual([]);
  });

  it('returns an empty result for an invalid-query error with no violations', () => {
    const filters: readonly FilterClause[] = [{ property: 'status', op: 'eq', value: 'Open' }];

    const result = mapViolationsToFilters(filters, apiError({ violations: [] }));

    expect(result.byIndex.size).toBe(0);
    expect(result.unmatched).toEqual([]);
  });
});
