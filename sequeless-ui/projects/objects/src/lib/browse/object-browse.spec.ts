/**
 * Contract [OBJ-BROWSE] (lib/browse/object-browse.ts, `SqObjectBrowse`): one facet group per
 * facetable property, requested via `facets=`; selecting a facet bucket or adding a filter writes
 * a `filter[p][op]` clause to the URL; removing a chip removes its clause; a 400 invalid-query
 * violation is shown on the chip it refers to; the add-filter operator menu follows the chosen
 * property's datatype (operatorsFor). All state changes go through the URL, never local state.
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

import { operatorsFor, visibleColumns } from './browse-columns';
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

function setup(initialParams: Record<string, string> = {}, browseError?: ApiError, withLookup = false) {
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
      ...(withLookup
        ? [
            {
              provide: RELATIONSHIP_LOOKUP,
              useValue: { search: () => of([]), targetType: () => of(TYPE) },
            },
          ]
        : []),
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

  it('renders one facet group per facetable property', async () => {
    const ctx = await loaded(setup());

    const facets = [...ctx.host.querySelectorAll('[data-facet]')].map((el) =>
      el.getAttribute('data-facet'),
    );

    expect(facets).toEqual(['status']);
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

  it('adding a range writes a gte and lte pair on the same property', async () => {
    const ctx = await loaded(setup());
    const select = ctx.host.querySelector('[data-add-property]') as HTMLSelectElement;
    select.value = 'openTaskCount';
    select.dispatchEvent(new Event('change'));
    ctx.fixture.detectChanges();
    const from = ctx.host.querySelector('[data-range-from]') as HTMLInputElement;
    const to = ctx.host.querySelector('[data-range-to]') as HTMLInputElement;
    from.value = '1';
    from.dispatchEvent(new Event('input'));
    to.value = '5';
    to.dispatchEvent(new Event('input'));
    ctx.fixture.detectChanges();

    clickOn(ctx, '[data-add-range]');

    expect(queryParamsOfLastNavigation(ctx)).toEqual(
      expect.objectContaining({ 'filter[openTaskCount][gte]': '1', 'filter[openTaskCount][lte]': '5' }),
    );
  });

  it('the add-filter button stays disabled until a value is entered', async () => {
    const ctx = await loaded(setup());

    const button = ctx.host.querySelector('[data-add-filter]') as HTMLButtonElement;

    expect(button.disabled).toBe(true);
  });

  it('offers the operators for the chosen property datatype', async () => {
    const ctx = await loaded(setup());
    const select = ctx.host.querySelector('[data-add-property]') as HTMLSelectElement;
    select.value = 'openTaskCount';
    select.dispatchEvent(new Event('change'));
    ctx.fixture.detectChanges();

    const operators = [...ctx.host.querySelectorAll('[data-add-operator] option')].map(
      (o) => (o as HTMLOptionElement).value,
    );

    const openTaskCount = visibleColumns(TYPE).find((p) => p.name === 'openTaskCount')!;
    expect(operators).toEqual([...operatorsFor(openTaskCount)]);
  });

  describe('relationship filters', () => {
    async function choose(ctx: ReturnType<typeof setup>, property: string, operator?: string) {
      const select = ctx.host.querySelector('[data-add-property]') as HTMLSelectElement;
      select.value = property;
      select.dispatchEvent(new Event('change'));
      ctx.fixture.detectChanges();
      if (operator) {
        const op = ctx.host.querySelector('[data-add-operator]') as HTMLSelectElement;
        op.value = operator;
        op.dispatchEvent(new Event('change'));
        ctx.fixture.detectChanges();
      }
    }

    function addButton(ctx: ReturnType<typeof setup>) {
      return ctx.host.querySelector('[data-add-filter]') as HTMLButtonElement;
    }

    it('shows the relationship picker instead of the text input for a relationship property', async () => {
      const ctx = await loaded(setup({}, undefined, true));
      await choose(ctx, 'owner');

      expect(ctx.host.querySelector('sq-relationship-picker')).not.toBeNull();
      expect(ctx.host.querySelector('[data-add-value]')).toBeNull();
    });

    it('keeps the text input for a non-relationship property', async () => {
      const ctx = await loaded(setup({}, undefined, true));
      await choose(ctx, 'status');

      expect(ctx.host.querySelector('[data-add-value]')).not.toBeNull();
      expect(ctx.host.querySelector('sq-relationship-picker')).toBeNull();
    });

    it('eq writes the first selected id to the URL', async () => {
      const ctx = await loaded(setup({}, undefined, true));
      await choose(ctx, 'owner', 'eq');
      ctx.fixture.componentInstance.relationshipControl.setValue(['person-1']);
      ctx.fixture.detectChanges();

      expect(addButton(ctx).disabled).toBe(false);
      clickOn(ctx, '[data-add-filter]');

      expect(queryParamsOfLastNavigation(ctx)).toEqual(
        expect.objectContaining({ 'filter[owner][eq]': 'person-1' }),
      );
    });

    it('eq accepts the bare id a single-valued picker writes', async () => {
      const ctx = await loaded(setup({}, undefined, true));
      await choose(ctx, 'owner', 'eq');
      // A single-valued relationship picker writes the id itself, not an array (relationship-picker.ts select()).
      ctx.fixture.componentInstance.relationshipControl.setValue('person-1' as unknown as string[]);
      ctx.fixture.detectChanges();

      expect(addButton(ctx).disabled).toBe(false);
      clickOn(ctx, '[data-add-filter]');

      expect(queryParamsOfLastNavigation(ctx)).toEqual(
        expect.objectContaining({ 'filter[owner][eq]': 'person-1' }),
      );
    });

    it('in writes all selected ids comma-joined to the URL', async () => {
      const ctx = await loaded(setup({}, undefined, true));
      await choose(ctx, 'hasTask', 'in');
      ctx.fixture.componentInstance.relationshipControl.setValue(['a', 'b']);
      ctx.fixture.detectChanges();

      clickOn(ctx, '[data-add-filter]');

      expect(queryParamsOfLastNavigation(ctx)).toEqual(
        expect.objectContaining({ 'filter[hasTask][in]': 'a,b' }),
      );
    });

    it('keeps the add-filter button disabled until an id is selected', async () => {
      const ctx = await loaded(setup({}, undefined, true));
      await choose(ctx, 'owner', 'eq');

      expect(addButton(ctx).disabled).toBe(true);

      ctx.fixture.componentInstance.relationshipControl.setValue(['person-1']);
      ctx.fixture.detectChanges();

      expect(addButton(ctx).disabled).toBe(false);
    });
  });
});
