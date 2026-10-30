/*
 * browse-query mirrors the query grammar ObjectsController#browse accepts on
 * GET /api/objects/{type}:
 *
 *   page (default 0), size (default 20, capped server-side at 200 via Math.min),
 *   q, sort (comma list, leading '-' = descending), facets (comma list), and
 *   repeatable filter[<property>][<op>]=<value> keys matched by the regex
 *   filter\[([^\]]+)\]\[([^\]]+)\].
 *
 * Operators: eq, ne, in, lt, lte, gt, gte, contains, startswith, isnull,
 * notnull. `in` carries a comma-separated value; `isnull`/`notnull` ignore
 * their value. Operator tokens are case-insensitive on the server.
 *
 * Encoding note: Angular's default HttpUrlEncodingCodec percent-encodes '['
 * and ']' in query keys (filter%5Bstatus%5D%5Beq%5D=...). That is fine: the
 * backend test ApiPathPrefixTest.bracketFilterParametersSurvivePercentEncoding
 * proves the server decodes %5B/%5D back into filter[x][y] before its
 * FILTER_KEY regex runs, so no custom codec is required.
 */
import { HttpParams } from '@angular/common/http';

import {
  BrowseQueryState,
  FilterClause,
  FilterOperator,
  MAX_PAGE_SIZE,
  SortKey,
  fromQueryParamMap,
  toHttpParams,
} from './browse-query';

/**
 * Converts HttpParams into the Record shape fromQueryParamMap accepts
 * (single value → string, repeated key → array).
 */
function toParamsRecord(params: HttpParams): Record<string, string | readonly string[]> {
  const record: Record<string, string | readonly string[]> = {};
  for (const key of params.keys()) {
    const all = params.getAll(key) ?? [];
    record[key] = all.length === 1 ? all[0] : all;
  }
  return record;
}

function state(overrides: Partial<BrowseQueryState> = {}): BrowseQueryState {
  return { page: 0, size: 20, sort: [], facets: [], filters: [], ...overrides };
}

describe('browse-query', () => {
  describe('MAX_PAGE_SIZE', () => {
    it('equals the server-side cap of 200 applied by ObjectsController (Math.min(size, 200))', () => {
      expect(MAX_PAGE_SIZE).toBe(200);
    });
  });

  describe('toHttpParams', () => {
    it('always emits page and size as strings', () => {
      const params = toHttpParams(state({ page: 3, size: 50 }));

      expect(params.get('page')).toBe('3');
      expect(params.get('size')).toBe('50');
    });

    it('clamps size above MAX_PAGE_SIZE down to 200', () => {
      expect(toHttpParams(state({ size: 500 })).get('size')).toBe('200');
      expect(toHttpParams(state({ size: 200 })).get('size')).toBe('200');
    });

    it('floors size at 1 when given zero or a negative size', () => {
      expect(toHttpParams(state({ size: 0 })).get('size')).toBe('1');
      expect(toHttpParams(state({ size: -5 })).get('size')).toBe('1');
    });

    it('emits a non-empty q verbatim', () => {
      expect(toHttpParams(state({ q: 'invoice overdue' })).get('q')).toBe('invoice overdue');
    });

    it('omits q entirely when it is undefined, empty, or whitespace-only', () => {
      expect(toHttpParams(state()).has('q')).toBe(false);
      expect(toHttpParams(state({ q: '' })).has('q')).toBe(false);
      expect(toHttpParams(state({ q: '   ' })).has('q')).toBe(false);
    });

    it('serialises sort keys as one comma list with a leading "-" for descending keys', () => {
      const sort: readonly SortKey[] = [
        { property: 'priority', direction: 'desc' },
        { property: 'dueDate', direction: 'asc' },
      ];

      expect(toHttpParams(state({ sort })).get('sort')).toBe('-priority,dueDate');
    });

    it('omits sort when there are no sort keys', () => {
      expect(toHttpParams(state({ sort: [] })).has('sort')).toBe(false);
    });

    it('joins facets with commas into a single facets parameter', () => {
      expect(toHttpParams(state({ facets: ['status', 'priority'] })).get('facets')).toBe(
        'status,priority',
      );
    });

    it('omits facets when the facet list is empty', () => {
      expect(toHttpParams(state({ facets: [] })).has('facets')).toBe(false);
    });

    it('emits one filter[<property>][<op>] entry per clause', () => {
      const filters: readonly FilterClause[] = [
        { property: 'status', op: 'eq', value: 'Open' },
        { property: 'title', op: 'contains', value: 'report' },
      ];

      const params = toHttpParams(state({ filters }));

      expect(params.getAll('filter[status][eq]')).toEqual(['Open']);
      expect(params.getAll('filter[title][contains]')).toEqual(['report']);
    });

    it('emits a gte + lte range on the same property as two separate filter keys', () => {
      const filters: readonly FilterClause[] = [
        { property: 'dueDate', op: 'gte', value: '2026-01-01' },
        { property: 'dueDate', op: 'lte', value: '2026-03-31' },
      ];

      const params = toHttpParams(state({ filters }));

      expect(params.getAll('filter[dueDate][gte]')).toEqual(['2026-01-01']);
      expect(params.getAll('filter[dueDate][lte]')).toEqual(['2026-03-31']);
    });

    it('repeats the same filter key when two clauses share both property and operator', () => {
      const filters: readonly FilterClause[] = [
        { property: 'status', op: 'ne', value: 'Done' },
        { property: 'status', op: 'ne', value: 'Cancelled' },
      ];

      expect(toHttpParams(state({ filters })).getAll('filter[status][ne]')).toEqual([
        'Done',
        'Cancelled',
      ]);
    });

    it('joins an array value for the "in" operator with commas', () => {
      const filters: readonly FilterClause[] = [
        { property: 'status', op: 'in', value: ['Open', 'InProgress', 'Blocked'] },
      ];

      expect(toHttpParams(state({ filters })).getAll('filter[status][in]')).toEqual([
        'Open,InProgress,Blocked',
      ]);
    });

    it('passes a pre-joined string value for the "in" operator through unchanged', () => {
      const filters: readonly FilterClause[] = [
        { property: 'status', op: 'in', value: 'Open,Done' },
      ];

      expect(toHttpParams(state({ filters })).get('filter[status][in]')).toBe('Open,Done');
    });

    it('emits isnull and notnull with an empty value, ignoring any supplied value', () => {
      const filters: readonly FilterClause[] = [
        { property: 'assignedTo', op: 'isnull' },
        { property: 'dueDate', op: 'notnull', value: 'ignored' },
      ];

      const params = toHttpParams(state({ filters }));

      expect(params.getAll('filter[assignedTo][isnull]')).toEqual(['']);
      expect(params.getAll('filter[dueDate][notnull]')).toEqual(['']);
    });

    it('serialises through the default codec with percent-encoded brackets and literal commas', () => {
      const params = toHttpParams(
        state({
          sort: [
            { property: 'priority', direction: 'desc' },
            { property: 'dueDate', direction: 'asc' },
          ],
          filters: [{ property: 'status', op: 'eq', value: 'Open' }],
        }),
      );

      const query = params.toString();

      expect(query).toContain('filter%5Bstatus%5D%5Beq%5D=Open');
      expect(query).toContain('sort=-priority,dueDate');
    });
  });

  describe('fromQueryParamMap', () => {
    it('defaults to page 0, size 20 and empty sort/facets/filters for an empty map', () => {
      const parsed = fromQueryParamMap({});

      expect(parsed).toEqual({ page: 0, size: 20, sort: [], facets: [], filters: [] });
      expect(parsed.q).toBeUndefined();
    });

    it('parses page and size from strings', () => {
      const parsed = fromQueryParamMap({ page: '4', size: '50' });

      expect(parsed.page).toBe(4);
      expect(parsed.size).toBe(50);
    });

    it('clamps a parsed size to MAX_PAGE_SIZE and floors it at 1', () => {
      expect(fromQueryParamMap({ size: '1000' }).size).toBe(200);
      expect(fromQueryParamMap({ size: '0' }).size).toBe(1);
    });

    it('falls back to the defaults for non-numeric page and size', () => {
      const parsed = fromQueryParamMap({ page: 'abc', size: 'lots' });

      expect(parsed.page).toBe(0);
      expect(parsed.size).toBe(20);
    });

    it('parses a sort comma list, treating a leading "-" as descending', () => {
      expect(fromQueryParamMap({ sort: '-priority,dueDate' }).sort).toEqual([
        { property: 'priority', direction: 'desc' },
        { property: 'dueDate', direction: 'asc' },
      ]);
    });

    it('parses facets from a comma list, trimming whitespace and dropping empty entries', () => {
      expect(fromQueryParamMap({ facets: 'status, priority,,' }).facets).toEqual([
        'status',
        'priority',
      ]);
    });

    it('keeps a non-empty q and drops an empty one', () => {
      expect(fromQueryParamMap({ q: 'overdue' }).q).toBe('overdue');
      expect(fromQueryParamMap({ q: '' }).q).toBeUndefined();
    });

    it('parses filter[<property>][<op>] keys into filter clauses', () => {
      expect(
        fromQueryParamMap({
          'filter[status][eq]': 'Open',
          'filter[dueDate][gte]': '2026-01-01',
        }).filters,
      ).toEqual([
        { property: 'status', op: 'eq', value: 'Open' },
        { property: 'dueDate', op: 'gte', value: '2026-01-01' },
      ]);
    });

    it('yields one clause per value when a filter key is repeated', () => {
      expect(
        fromQueryParamMap({ 'filter[status][ne]': ['Done', 'Cancelled'] }).filters,
      ).toEqual([
        { property: 'status', op: 'ne', value: 'Done' },
        { property: 'status', op: 'ne', value: 'Cancelled' },
      ]);
    });

    it('splits the value of an "in" filter on commas into an array', () => {
      expect(fromQueryParamMap({ 'filter[status][in]': 'Open,Done' }).filters).toEqual([
        { property: 'status', op: 'in', value: ['Open', 'Done'] },
      ]);
    });

    it('drops the value of isnull and notnull filters', () => {
      expect(
        fromQueryParamMap({
          'filter[assignedTo][isnull]': '',
          'filter[dueDate][notnull]': 'anything',
        }).filters,
      ).toEqual([
        { property: 'assignedTo', op: 'isnull' },
        { property: 'dueDate', op: 'notnull' },
      ]);
    });

    it('parses operator tokens case-insensitively and normalises them to lower case', () => {
      const parsed = fromQueryParamMap({
        'filter[status][EQ]': 'Open',
        'filter[title][StartsWith]': 'Q3',
        'filter[assignedTo][IsNull]': '',
      });

      const ops: readonly FilterOperator[] = parsed.filters.map((clause) => clause.op);
      expect(ops).toEqual(['eq', 'startswith', 'isnull']);
    });

    it('ignores unknown parameters', () => {
      expect(fromQueryParamMap({ view: 'table', utm_source: 'mail' })).toEqual({
        page: 0,
        size: 20,
        sort: [],
        facets: [],
        filters: [],
      });
    });

    it('ignores malformed filter keys and unknown operators', () => {
      const parsed = fromQueryParamMap({
        'filter[status]': 'Open',
        'filter[][eq]': 'Open',
        'filter[status][]': 'Open',
        'filterstatus[eq]': 'Open',
        'filter[status][between]': '1,2',
        'filter[priority][gt]': '2',
      });

      expect(parsed.filters).toEqual([{ property: 'priority', op: 'gt', value: '2' }]);
    });
  });

  describe('round-trip', () => {
    it('recovers a rich state from fromQueryParamMap(toParamsRecord(toHttpParams(state)))', () => {
      const rich: BrowseQueryState = {
        page: 2,
        size: 50,
        q: 'quarterly report',
        sort: [
          { property: 'priority', direction: 'desc' },
          { property: 'dueDate', direction: 'asc' },
        ],
        facets: ['status', 'priority'],
        filters: [
          { property: 'status', op: 'in', value: ['Open', 'InProgress'] },
          { property: 'dueDate', op: 'gte', value: '2026-01-01' },
          { property: 'dueDate', op: 'lte', value: '2026-03-31' },
          { property: 'title', op: 'contains', value: 'report' },
          { property: 'assignedTo', op: 'isnull' },
          { property: 'estimate', op: 'notnull' },
        ],
      };

      expect(fromQueryParamMap(toParamsRecord(toHttpParams(rich)))).toEqual(rich);
    });

    it('round-trips a minimal state with no q, sort, facets or filters', () => {
      const minimal = state();

      expect(fromQueryParamMap(toParamsRecord(toHttpParams(minimal)))).toEqual(minimal);
    });
  });
});
