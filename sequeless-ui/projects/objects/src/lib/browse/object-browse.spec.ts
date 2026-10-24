/**
 * Contract [OBJ-BROWSE] (lib/browse/object-browse.ts, `SqObjectBrowse`): facets are requested via
 * `facets=`; a change in the filter panel (selecting a facet bucket, removing a chip) is written as a
 * `filter[p][op]` clause to the URL; a 400 invalid-query violation is shown on the chip it refers to.
 * All state changes go through the URL, never local state. The filter behaviour itself (facet groups,
 * range, add-filter form, relationship picker) is specified in filter-panel.spec.ts.
 */
import { TestBed } from '@angular/core/testing';
import { MatChipOption } from '@angular/material/chips';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import type {
  ApiError,
  BrowseQueryState,
  QueryResultResponse,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { ObjectsClient, TypesClient } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { provideUiKit, RELATIONSHIP_LOOKUP } from '@sequeless/ui-kit';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';

import { SqObjectBrowse } from './object-browse';

const TYPE: TypeDetailResponse = PROJECT_TYPE_DETAIL;

const RESULT: QueryResultResponse = {
  items: [],
  page: 0,
  size: 20,
  totalItems: 0,
  totalPages: 0,
  facets: { status: [{ value: 'Open', count: 3 }, { value: 'Closed', count: 1 }] },
};

function setup(initialParams: Record<string, string> = {}, browseError?: ApiError) {
  const objects = {
    browse: vi.fn<(type: string, state: BrowseQueryState) => Observable<QueryResultResponse>>(() =>
      browseError ? throwError(() => browseError) : of(RESULT),
    ),
  };
  const queryParamMap = new BehaviorSubject(convertToParamMap(initialParams));

  TestBed.configureTestingModule({
    providers: [
      provideUiKit(),
      provideNoopAnimations(),
      provideRouter([]),
      { provide: ObjectsClient, useValue: objects },
      { provide: TypesClient, useValue: { describe: vi.fn(() => of(TYPE)) } },
      { provide: RELATIONSHIP_LOOKUP, useValue: { search: () => of([]), targetType: () => of(TYPE) } },
      {
        provide: ActivatedRoute,
        useValue: {
          paramMap: of(convertToParamMap({ type: 'Project' })),
          queryParamMap,
        },
      },
    ],
  });

  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(SqObjectBrowse);
  return { fixture, host: fixture.nativeElement as HTMLElement, objects, navigate };
}

async function loaded(ctx: ReturnType<typeof setup>) {
  ctx.fixture.detectChanges();
  await ctx.fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve));
  ctx.fixture.detectChanges();
  return ctx;
}

function clickOn(ctx: ReturnType<typeof setup>, selector: string) {
  (ctx.host.querySelector(selector) as HTMLElement).click();
}

function queryParamsOfLastNavigation(ctx: ReturnType<typeof setup>): Record<string, unknown> {
  const [, extras] = ctx.navigate.mock.calls.at(-1) as unknown as [unknown, { queryParams: Record<string, unknown> }];
  return extras.queryParams;
}

describe('SqObjectBrowse', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('requests facets for every facetable property', async () => {
    const ctx = await loaded(setup());

    const state = ctx.objects.browse.mock.calls.at(-1)![1] as BrowseQueryState;

    expect(state.facets).toEqual(['status']);
  });

  it('selecting a facet bucket writes an eq filter clause to the URL', async () => {
    const ctx = await loaded(setup());

    // selectViaInteraction is the user-originated path, which makes the listbox emit change.
    const option = ctx.fixture.debugElement
      .queryAll(By.directive(MatChipOption))
      .map((d) => d.componentInstance as MatChipOption)
      .find((o) => o.value === 'Open')!;
    option.selectViaInteraction();
    ctx.fixture.detectChanges();

    expect(queryParamsOfLastNavigation(ctx)).toEqual(
      expect.objectContaining({ 'filter[status][eq]': 'Open' }),
    );
  });

  it('removing a filter chip removes its clause from the URL', async () => {
    const ctx = await loaded(setup({ 'filter[status][eq]': 'Open' }));

    clickOn(ctx, '[data-filter-index="0"] [data-remove]');

    expect(queryParamsOfLastNavigation(ctx)).not.toHaveProperty('filter[status][eq]');
  });

  it('shows a 400 invalid-query violation on the chip it refers to', async () => {
    const ctx = await loaded(
      setup(
        { 'filter[status][eq]': 'Open' },
        {
          status: 400,
          kind: 'problem',
          title: 'Invalid query',
          detail: null,
          slug: 'invalid-query',
          violations: [{ property: 'status', propertyIri: null, message: 'bad operator' }],
          issues: [],
          expectedVersion: null,
          transitionName: null,
          typeIri: null,
          signalName: null,
          raw: null,
        },
      ),
    );

    const chip = ctx.host.querySelector('[data-filter-index="0"]') as HTMLElement;
    expect(chip.textContent).toContain('bad operator');
  });

  describe('search box', () => {
    function submitSearch(ctx: ReturnType<typeof setup>, term: string) {
      const input = ctx.host.querySelector('[data-search]') as HTMLInputElement;
      input.value = term;
      (ctx.host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
      ctx.fixture.detectChanges();
    }

    it('submitting a term writes q to the URL and resets the page', async () => {
      const ctx = await loaded(setup({ page: '2' }));

      submitSearch(ctx, 'cat');

      const params = queryParamsOfLastNavigation(ctx);
      expect(params).toEqual(expect.objectContaining({ q: 'cat' }));
      expect(params).not.toHaveProperty('page', '2');
      expect(params['page']).toBeUndefined();
    });

    it('submitting an empty box removes q from the URL', async () => {
      const ctx = await loaded(setup({ q: 'cat' }));

      submitSearch(ctx, '');

      expect(queryParamsOfLastNavigation(ctx)).not.toHaveProperty('q');
    });

    it('the box shows the q from the initial URL', async () => {
      const ctx = await loaded(setup({ q: 'cat' }));

      const input = ctx.host.querySelector('[data-search]') as HTMLInputElement;

      expect(input.value).toBe('cat');
    });
  });
});
