import type { ApiError, FilterClause } from '@sequeless/core-api';

export interface FilterErrors {
  /** Violation message per filter clause index. */
  byIndex: Map<number, string>;
  /** Messages that do not belong to any filter clause (object-level or unknown property). */
  unmatched: string[];
}

/**
 * Maps a 400 `invalid-query` problem's violations back onto the filter clauses that caused them.
 * The server's capability checks are authoritative and the client mirror is best-effort, so this
 * degrades to an unmatched message instead of failing when a violation names no known clause.
 */
export function mapViolationsToFilters(
  filters: readonly FilterClause[],
  err: ApiError,
): FilterErrors {
  const result: FilterErrors = { byIndex: new Map(), unmatched: [] };

  if (!err.slug?.endsWith('invalid-query')) {
    return result;
  }

  for (const violation of err.violations) {
    const index =
      violation.property === null
        ? -1
        : filters.findIndex((clause) => clause.property === violation.property);

    if (index === -1 || result.byIndex.has(index)) {
      // A second violation on an already-marked clause is kept rather than dropped.
      result.unmatched.push(violation.message);
    } else {
      result.byIndex.set(index, violation.message);
    }
  }

  return result;
}
