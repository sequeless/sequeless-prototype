import { HttpParams } from '@angular/common/http';

export const MAX_PAGE_SIZE = 200;

export type FilterOperator =
  | 'eq'
  | 'ne'
  | 'in'
  | 'lt'
  | 'lte'
  | 'gt'
  | 'gte'
  | 'contains'
  | 'startswith'
  | 'isnull'
  | 'notnull';

export interface FilterClause {
  property: string;
  op: FilterOperator;
  value?: string | readonly string[];
}

export interface SortKey {
  property: string;
  direction: 'asc' | 'desc';
}

export interface BrowseQueryState {
  page: number;
  size: number;
  q?: string;
  sort: readonly SortKey[];
  facets: readonly string[];
  filters: readonly FilterClause[];
}

const FILTER_OPERATORS: readonly string[] = [
  'eq',
  'ne',
  'in',
  'lt',
  'lte',
  'gt',
  'gte',
  'contains',
  'startswith',
  'isnull',
  'notnull',
];

const FILTER_KEY_PATTERN = /^filter\[([^\]]+)\]\[([^\]]+)\]$/;

function isFilterOperator(token: string): token is FilterOperator {
  return FILTER_OPERATORS.includes(token);
}

function clampSize(size: number): number {
  return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
}

function clauseValue(clause: FilterClause): string {
  const value = clause.value;
  if (clause.op === 'isnull' || clause.op === 'notnull') {
    return '';
  }
  if (Array.isArray(value)) {
    return value.join(',');
  }
  return typeof value === 'string' ? value : '';
}

/**
 * Serialises a BrowseQueryState into the repeated-filter grammar accepted by
 * GET /api/objects/{type}. Angular's default codec percent-encodes the
 * brackets in filter keys; the server decodes them before its regex runs.
 */
export function toHttpParams(state: BrowseQueryState): HttpParams {
  let params = new HttpParams()
    .set('page', String(state.page))
    .set('size', String(clampSize(state.size)));

  if (state.q !== undefined && state.q.trim().length > 0) {
    params = params.set('q', state.q);
  }

  if (state.sort.length > 0) {
    params = params.set(
      'sort',
      state.sort
        .map((key) => (key.direction === 'desc' ? '-' + key.property : key.property))
        .join(','),
    );
  }

  if (state.facets.length > 0) {
    params = params.set('facets', state.facets.join(','));
  }

  for (const clause of state.filters) {
    params = params.append(`filter[${clause.property}][${clause.op}]`, clauseValue(clause));
  }

  return params;
}

function parsePage(raw: string | readonly string[] | undefined): number {
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) {
    return 0;
  }
  return Number(raw);
}

function parseSize(raw: string | readonly string[] | undefined): number {
  if (typeof raw !== 'string') {
    return 20;
  }
  const size = Number(raw);
  if (!Number.isFinite(size)) {
    return 20;
  }
  return clampSize(size);
}

function parseSort(raw: string | readonly string[] | undefined): readonly SortKey[] {
  if (typeof raw !== 'string' || raw.length === 0) {
    return [];
  }
  return raw.split(',').map((segment): SortKey =>
    segment.startsWith('-')
      ? { property: segment.slice(1), direction: 'desc' }
      : { property: segment, direction: 'asc' },
  );
}

function parseFacets(raw: string | readonly string[] | undefined): readonly string[] {
  if (typeof raw !== 'string' || raw.length === 0) {
    return [];
  }
  return raw
    .split(',')
    .map((facet) => facet.trim())
    .filter((facet) => facet.length > 0);
}

function parseFilters(params: Record<string, string | readonly string[]>): readonly FilterClause[] {
  const clauses: FilterClause[] = [];

  for (const key of Object.keys(params)) {
    const match = FILTER_KEY_PATTERN.exec(key);
    if (match === null) {
      continue;
    }

    const property = match[1];
    const op = match[2].toLowerCase();
    if (!isFilterOperator(op)) {
      continue;
    }

    const raw = params[key];
    if (raw === undefined) {
      continue;
    }

    for (const value of Array.isArray(raw) ? raw : [raw]) {
      if (op === 'isnull' || op === 'notnull') {
        clauses.push({ property, op });
      } else if (op === 'in') {
        clauses.push({ property, op, value: value.split(',') });
      } else {
        clauses.push({ property, op, value });
      }
    }
  }

  return clauses;
}

/**
 * Parses the same query grammar back into a BrowseQueryState. Unknown keys are
 * ignored, malformed filter keys and unknown operators are silently dropped,
 * and an empty q omits the field entirely.
 */
export function fromQueryParamMap(params: Record<string, string | readonly string[]>): BrowseQueryState {
  const state: BrowseQueryState = {
    page: parsePage(params["page"]),
    size: parseSize(params["size"]),
    sort: parseSort(params["sort"]),
    facets: parseFacets(params["facets"]),
    filters: parseFilters(params),
  };

  const q = params["q"];
  if (typeof q === 'string' && q.length > 0) {
    state.q = q;
  }

  return state;
}
