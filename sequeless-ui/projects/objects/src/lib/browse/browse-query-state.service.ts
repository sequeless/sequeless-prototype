import { inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, type ParamMap } from '@angular/router';
import {
  fromQueryParamMap,
  toHttpParams,
  type BrowseQueryState,
} from '@sequeless/core-api';
import { map } from 'rxjs';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

/**
 * Keeps all browse state in the URL query string. Serialisation goes through core-api's
 * toHttpParams/fromQueryParamMap, so the URL grammar has one definition. Facets are deliberately
 * not written to the URL.
 */
@Injectable()
export class BrowseQueryStateService {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly state = toSignal(
    this.route.queryParamMap.pipe(map((params) => fromQueryParamMap(toRecord(params)))),
    { requireSync: true },
  );

  toQueryParams(state: BrowseQueryState): Params {
    const http = toHttpParams(state);
    const params: Params = {};

    for (const key of http.keys()) {
      if (key === 'facets') {
        continue;
      }
      const values = http.getAll(key) ?? [];
      params[key] = values.length === 1 ? values[0] : values;
    }

    if (state.page === DEFAULT_PAGE) {
      delete params['page'];
    }
    if (state.size === DEFAULT_SIZE) {
      delete params['size'];
    }

    return params;
  }

  fromQueryParams(params: Params): BrowseQueryState {
    return fromQueryParamMap(params as Record<string, string | readonly string[]>);
  }

  withFacets(state: BrowseQueryState, facets: readonly string[]): BrowseQueryState {
    return { ...state, facets: [...facets] };
  }

  /**
   * Merges `patch` into the current state and navigates. Changing filters, sort or q resets the
   * page to 0 unless the patch sets a page itself.
   */
  update(patch: Partial<BrowseQueryState>): Promise<boolean> {
    const touchesResultSet = 'filters' in patch || 'sort' in patch || 'q' in patch;
    const next: BrowseQueryState = { ...this.state(), ...patch };
    if (touchesResultSet && !('page' in patch)) {
      next.page = DEFAULT_PAGE;
    }

    const params = this.toQueryParams(next);
    // The explicit `page: undefined` key clears a previous page param on navigation.
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...params, page: params['page'] },
    });
  }
}

function toRecord(params: ParamMap): Record<string, string | readonly string[]> {
  const record: Record<string, string | readonly string[]> = {};
  for (const key of params.keys) {
    const values = params.getAll(key);
    record[key] = values.length === 1 ? values[0] : values;
  }
  return record;
}
