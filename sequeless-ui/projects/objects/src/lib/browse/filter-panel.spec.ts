/**
 * Contract [OBJ-FILTER] (lib/browse/filter-panel.ts, `SqFilterPanel`): presentational. Inputs `type`,
 * `filters`, `facets`, `errors`; output `filtersChange`. One expansion panel per facetable property
 * holding a chip listbox, the active-filter chips (with the error at each chip's index), the add-filter
 * form, the range control and the relationship picker. It never reads or writes the URL: every change
 * leaves through `filtersChange`.
 */
import { TestBed } from '@angular/core/testing';
import { MatChipOption } from '@angular/material/chips';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import type { FacetBucketResponse, FilterClause, TypeDetailResponse } from '@sequeless/core-api';
import { PROJECT_TYPE_DETAIL } from '@sequeless/testing';
import { provideUiKit, RELATIONSHIP_LOOKUP } from '@sequeless/ui-kit';
import { of } from 'rxjs';

import { operatorsFor, visibleColumns } from './browse-columns';
import { SqFilterPanel } from './filter-panel';
import type { FilterErrors } from './violation-mapping';

const TYPE: TypeDetailResponse = PROJECT_TYPE_DETAIL;

const FACETS: Record<string, readonly FacetBucketResponse[]> = {
  status: [
    { value: 'Open', count: 3 },
    { value: 'Closed', count: 1 },
  ],
};

interface Inputs {
  filters?: readonly FilterClause[];
  errors?: FilterErrors;
}

function setup({ filters = [], errors }: Inputs = {}) {
  TestBed.configureTestingModule({
    providers: [
      provideUiKit(),
      provideNoopAnimations(),
      provideRouter([]),
      { provide: RELATIONSHIP_LOOKUP, useValue: { search: () => of([]), targetType: () => of(TYPE) } },
    ],
  });
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(SqFilterPanel);
  fixture.componentRef.setInput('type', TYPE);
  fixture.componentRef.setInput('filters', filters);
  fixture.componentRef.setInput('facets', FACETS);
  if (errors) {
    fixture.componentRef.setInput('errors', errors);
  }
  const emitted: (readonly FilterClause[])[] = [];
  fixture.componentInstance.filtersChange.subscribe((value) => emitted.push(value));
  fixture.detectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement, emitted, navigate };
}

function clickOn(ctx: ReturnType<typeof setup>, selector: string) {
  (ctx.host.querySelector(selector) as HTMLElement).click();
}

function choose(ctx: ReturnType<typeof setup>, property: string, operator?: string) {
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

function facetOption(ctx: ReturnType<typeof setup>, value: string): MatChipOption {
  return ctx.fixture.debugElement
    .queryAll(By.directive(MatChipOption))
    .map((d) => d.componentInstance as MatChipOption)
    .find((o) => o.value === value)!;
}

describe('SqFilterPanel', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders one expansion panel per facetable property', () => {
    const ctx = setup();

    const panels = [...ctx.host.querySelectorAll('mat-expansion-panel[data-facet]')].map((el) =>
      el.getAttribute('data-facet'),
    );

    expect(panels).toEqual(['status']);
  });

  it('selecting a facet bucket emits an eq clause', () => {
    const ctx = setup();

    // selectViaInteraction is the user-originated path, which makes the listbox emit change.
    facetOption(ctx, 'Open').selectViaInteraction();
    ctx.fixture.detectChanges();

    expect(ctx.emitted.at(-1)).toEqual([{ property: 'status', op: 'eq', value: 'Open' }]);
  });

  it('deselecting a facet bucket removes its clause', async () => {
    const ctx = setup({ filters: [{ property: 'status', op: 'eq', value: 'Open' }] });
    await ctx.fixture.whenStable();
    ctx.fixture.detectChanges();

    // The option starts selected from the filters input; a user toggle (isUserInput) turns it off.
    facetOption(ctx, 'Open').toggleSelected(true);
    ctx.fixture.detectChanges();

    expect(ctx.emitted.at(-1)).toEqual([]);
  });

  it('removing a filter chip emits the filters without it', () => {
    const ctx = setup({
      filters: [
        { property: 'status', op: 'eq', value: 'Open' },
        { property: 'name', op: 'eq', value: 'x' },
      ],
    });

    clickOn(ctx, '[data-filter-index="0"] [data-remove]');

    expect(ctx.emitted.at(-1)).toEqual([{ property: 'name', op: 'eq', value: 'x' }]);
  });

  it('shows an error on the chip at its index', () => {
    const ctx = setup({
      filters: [{ property: 'status', op: 'eq', value: 'Open' }],
      errors: { byIndex: new Map([[0, 'bad operator']]), unmatched: [] },
    });

    const chip = ctx.host.querySelector('[data-filter-index="0"]') as HTMLElement;

    expect(chip.textContent).toContain('bad operator');
  });

  it('adding a range emits a gte and lte pair on the same property', () => {
    const ctx = setup();
    choose(ctx, 'openTaskCount');
    const from = ctx.host.querySelector('[data-range-from]') as HTMLInputElement;
    const to = ctx.host.querySelector('[data-range-to]') as HTMLInputElement;
    from.value = '1';
    from.dispatchEvent(new Event('input'));
    to.value = '5';
    to.dispatchEvent(new Event('input'));
    ctx.fixture.detectChanges();

    clickOn(ctx, '[data-add-range]');

    expect(ctx.emitted.at(-1)).toEqual([
      { property: 'openTaskCount', op: 'gte', value: '1' },
      { property: 'openTaskCount', op: 'lte', value: '5' },
    ]);
  });

  it('the add-filter button stays disabled until a value is entered', () => {
    const ctx = setup();

    const button = ctx.host.querySelector('[data-add-filter]') as HTMLButtonElement;

    expect(button.disabled).toBe(true);
  });

  it('offers the operators for the chosen property datatype', () => {
    const ctx = setup();
    choose(ctx, 'openTaskCount');

    const operators = [...ctx.host.querySelectorAll('[data-add-operator] option')].map(
      (o) => (o as HTMLOptionElement).value,
    );

    const openTaskCount = visibleColumns(TYPE).find((p) => p.name === 'openTaskCount')!;
    expect(operators).toEqual([...operatorsFor(openTaskCount)]);
  });

  it('only reports changes through filtersChange and never navigates', () => {
    const ctx = setup({ filters: [{ property: 'status', op: 'eq', value: 'Open' }] });

    clickOn(ctx, '[data-filter-index="0"] [data-remove]');

    expect(ctx.emitted).toHaveLength(1);
    expect(ctx.navigate).not.toHaveBeenCalled();
  });

  describe('relationship filters', () => {
    function addButton(ctx: ReturnType<typeof setup>) {
      return ctx.host.querySelector('[data-add-filter]') as HTMLButtonElement;
    }

    it('shows the relationship picker instead of the text input for a relationship property', () => {
      const ctx = setup();
      choose(ctx, 'owner');

      expect(ctx.host.querySelector('sq-relationship-picker')).not.toBeNull();
      expect(ctx.host.querySelector('[data-add-value]')).toBeNull();
    });

    it('keeps the text input for a non-relationship property', () => {
      const ctx = setup();
      choose(ctx, 'status');

      expect(ctx.host.querySelector('[data-add-value]')).not.toBeNull();
      expect(ctx.host.querySelector('sq-relationship-picker')).toBeNull();
    });

    it('eq emits the first selected id', () => {
      const ctx = setup();
      choose(ctx, 'owner', 'eq');
      ctx.fixture.componentInstance.relationshipControl.setValue(['person-1']);
      ctx.fixture.detectChanges();

      expect(addButton(ctx).disabled).toBe(false);
      clickOn(ctx, '[data-add-filter]');

      expect(ctx.emitted.at(-1)).toEqual([{ property: 'owner', op: 'eq', value: 'person-1' }]);
    });

    it('eq accepts the bare id a single-valued picker writes', () => {
      const ctx = setup();
      choose(ctx, 'owner', 'eq');
      // A single-valued relationship picker writes the id itself, not an array (relationship-picker.ts select()).
      ctx.fixture.componentInstance.relationshipControl.setValue('person-1' as unknown as string[]);
      ctx.fixture.detectChanges();

      expect(addButton(ctx).disabled).toBe(false);
      clickOn(ctx, '[data-add-filter]');

      expect(ctx.emitted.at(-1)).toEqual([{ property: 'owner', op: 'eq', value: 'person-1' }]);
    });

    it('in emits all selected ids comma-joined', () => {
      const ctx = setup();
      choose(ctx, 'hasTask', 'in');
      ctx.fixture.componentInstance.relationshipControl.setValue(['a', 'b']);
      ctx.fixture.detectChanges();

      clickOn(ctx, '[data-add-filter]');

      expect(ctx.emitted.at(-1)).toEqual([{ property: 'hasTask', op: 'in', value: 'a,b' }]);
    });

    it('keeps the add-filter button disabled until an id is selected', () => {
      const ctx = setup();
      choose(ctx, 'owner', 'eq');

      expect(addButton(ctx).disabled).toBe(true);

      ctx.fixture.componentInstance.relationshipControl.setValue(['person-1']);
      ctx.fixture.detectChanges();

      expect(addButton(ctx).disabled).toBe(false);
    });
  });
});
