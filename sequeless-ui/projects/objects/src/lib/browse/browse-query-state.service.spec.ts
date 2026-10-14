/*
 * Contract [OBJ-QSTATE]: BrowseQueryStateService, provided at component level.
 * `state` is a Signal<BrowseQueryState> derived from ActivatedRoute.queryParamMap
 * via fromQueryParamMap. toQueryParams/fromQueryParams round-trip a
 * BrowseQueryState through Params via toHttpParams/fromQueryParamMap, but
 * toQueryParams always omits facets and omits page=0/size=20 when they're the
 * defaults. update(patch) merges the patch into the current state and
 * navigates via router.navigate([], {relativeTo, queryParams}); page resets to
 * 0 whenever filters, sort or q change, but not for a bare page/size change.
 * withFacets(s, facets) returns a copy of s with facets replaced.
 */
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
  Router,
  type ParamMap,
  type Params,
} from '@angular/router';
import { TestBed } from '@angular/core/testing';
import type { BrowseQueryState } from '@sequeless/core-api';
import { BehaviorSubject } from 'rxjs';

import { BrowseQueryStateService } from './browse-query-state.service';

function paramMap$(params: Params): BehaviorSubject<ParamMap> {
  return new BehaviorSubject<ParamMap>(convertToParamMap(params));
}

function setup(initialParams: Params = {}) {
  const queryParamMap = paramMap$(initialParams);
  const route = { queryParamMap } as unknown as ActivatedRoute;

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      BrowseQueryStateService,
      { provide: ActivatedRoute, useValue: route },
    ],
  });

  const router = TestBed.inject(Router);
  const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  const service = TestBed.inject(BrowseQueryStateService);

  return { service, queryParamMap, navigateSpy, route };
}

describe('BrowseQueryStateService', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  describe('state', () => {
    it('reflects page, size, sort and a filter from the current query params', () => {
      const { service } = setup({
        page: '2',
        size: '50',
        sort: '-name',
        'filter[name][contains]': 'x',
      });

      expect(service.state()).toEqual({
        page: 2,
        size: 50,
        sort: [{ property: 'name', direction: 'desc' }],
        facets: [],
        filters: [{ property: 'name', op: 'contains', value: 'x' }],
      });
    });

    it('reflects defaults when the query params are empty', () => {
      const { service } = setup({});

      expect(service.state()).toEqual({
        page: 0,
        size: 20,
        sort: [],
        facets: [],
        filters: [],
      });
    });

    it('updates when the underlying queryParamMap emits a new value', () => {
      const { service, queryParamMap } = setup({ page: '0' });

      queryParamMap.next(convertToParamMap({ page: '3' }));

      expect(service.state().page).toBe(3);
    });
  });

  describe('round trip', () => {
    it('fromQueryParams(toQueryParams(s)) deep-equals s, aside from facets', () => {
      const { service } = setup({});
      const state: BrowseQueryState = {
        page: 2,
        size: 50,
        q: 'overdue',
        sort: [{ property: 'priority', direction: 'desc' }],
        facets: ['status'],
        filters: [{ property: 'status', op: 'eq', value: 'Open' }],
      };

      const params = service.toQueryParams(state);
      const roundTripped = service.fromQueryParams(params);

      expect(roundTripped).toEqual({ ...state, facets: [] });
    });

    it('toQueryParams omits facets', () => {
      const { service } = setup({});
      const state: BrowseQueryState = {
        page: 0,
        size: 20,
        sort: [],
        facets: ['status', 'priority'],
        filters: [],
      };

      const params = service.toQueryParams(state);

      expect(params['facets']).toBeUndefined();
    });

    it('toQueryParams omits page and size when they are the defaults (0 and 20)', () => {
      const { service } = setup({});
      const state: BrowseQueryState = {
        page: 0,
        size: 20,
        sort: [],
        facets: [],
        filters: [],
      };

      const params = service.toQueryParams(state);

      expect(params['page']).toBeUndefined();
      expect(params['size']).toBeUndefined();
    });

    it('toQueryParams includes page and size when they differ from the defaults', () => {
      const { service } = setup({});
      const state: BrowseQueryState = {
        page: 2,
        size: 50,
        sort: [],
        facets: [],
        filters: [],
      };

      const params = service.toQueryParams(state);

      expect(params['page']).toBe('2');
      expect(params['size']).toBe('50');
    });
  });

  describe('update', () => {
    it('resets page to 0 when filters change', async () => {
      const { service, navigateSpy, route } = setup({
        page: '2',
        'filter[name][contains]': 'x',
      });

      await service.update({ filters: [{ property: 'name', op: 'contains', value: 'y' }] });

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          relativeTo: route,
          queryParams: expect.objectContaining({ page: undefined }),
        }),
      );
    });

    it('keeps the existing filters when only page changes', async () => {
      const { service, navigateSpy, route } = setup({
        'filter[name][contains]': 'x',
      });

      await service.update({ page: 2 });

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          relativeTo: route,
          queryParams: expect.objectContaining({
            page: '2',
            'filter[name][contains]': 'x',
          }),
        }),
      );
    });

    it('merges the patch into the current state before navigating', async () => {
      const { service, navigateSpy, route } = setup({ sort: '-name' });

      await service.update({ q: 'overdue' });

      expect(navigateSpy).toHaveBeenCalledWith(
        [],
        expect.objectContaining({
          relativeTo: route,
          queryParams: expect.objectContaining({ sort: '-name', q: 'overdue' }),
        }),
      );
    });

    it('resolves the promise returned by router.navigate', async () => {
      const { service } = setup({});

      await expect(service.update({ page: 1 })).resolves.toBe(true);
    });
  });

  describe('withFacets', () => {
    it('returns a copy of the state with facets replaced', () => {
      const { service } = setup({});
      const state: BrowseQueryState = {
        page: 0,
        size: 20,
        sort: [],
        facets: [],
        filters: [],
      };

      const result = service.withFacets(state, ['status', 'priority']);

      expect(result).toEqual({ ...state, facets: ['status', 'priority'] });
    });

    it('does not mutate the state passed in', () => {
      const { service } = setup({});
      const state: BrowseQueryState = {
        page: 0,
        size: 20,
        sort: [],
        facets: ['original'],
        filters: [],
      };

      service.withFacets(state, ['replaced']);

      expect(state.facets).toEqual(['original']);
    });
  });
});
