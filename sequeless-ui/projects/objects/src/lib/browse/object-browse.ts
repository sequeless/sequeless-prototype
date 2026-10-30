import { Component, computed, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  ApiError,
  BrowseQueryState,
  BusinessObjectResponse,
  FacetBucketResponse,
  FilterClause,
  ObjectsClient,
  queryCapabilities,
  TypeDetailResponse,
  TypesClient,
} from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom, map } from 'rxjs';

import { facetableProperties, PAGE_SIZE_OPTIONS, visibleColumns } from './browse-columns';
import { BrowseQueryStateService } from './browse-query-state.service';
import { SqFilterPanel } from './filter-panel';
import { FilterErrors, mapViolationsToFilters } from './violation-mapping';

/**
 * Generic table of one object type. Columns, facets, operators and paging all derive from
 * TypeDetailResponse; there is no per-type code. Every change to the query goes through the URL via
 * BrowseQueryStateService.
 */
@Component({
  selector: 'sq-object-browse',
  imports: [
    MatButtonModule,
    MatPaginatorModule,
    MatSortModule,
    MatTableModule,
    RouterLink,
    SqProblemBanner,
    SqFilterPanel,
  ],
  providers: [BrowseQueryStateService],
  template: `
    <h2>{{ typeName() }}</h2>
    <a mat-stroked-button [routerLink]="['/objects', typeName(), 'new']">New</a>

    <form (submit)="submitSearch($event, search.value)">
      <input #search data-search [value]="query.state().q ?? ''" />
      <button type="submit" data-search-submit>Search</button>
    </form>

    @if (type(); as type) {
      <sq-filter-panel
        [type]="type"
        [filters]="query.state().filters"
        [facets]="facets()"
        [errors]="filterErrors()"
        (filtersChange)="onFiltersChange($event)"
      />
    }

    <table mat-table [dataSource]="rows()" matSort [matSortActive]="activeSort()" (matSortChange)="onSort($event)">
      @for (column of columns(); track column.name) {
        <ng-container [matColumnDef]="column.name">
          <th mat-header-cell *matHeaderCellDef [mat-sort-header]="column.name" [disabled]="!isSortable(column.name)">
            {{ column.label }}
          </th>
          <td mat-cell *matCellDef="let row">{{ row.properties[column.name] }}</td>
        </ng-container>
      }
      <tr mat-header-row *matHeaderRowDef="columnNames()"></tr>
      <tr mat-row *matRowDef="let row; columns: columnNames()" [routerLink]="['/objects', typeName(), row.id]"></tr>
    </table>
    <mat-paginator
      [length]="total()"
      [pageIndex]="query.state().page"
      [pageSize]="query.state().size"
      [pageSizeOptions]="pageSizes"
      (page)="onPage($event)"
    />
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    }
  `,
})
export class SqObjectBrowse implements OnInit {
  protected readonly query = inject(BrowseQueryStateService);
  private readonly route = inject(ActivatedRoute);
  private readonly objects = inject(ObjectsClient);
  private readonly types = inject(TypesClient);

  private readonly params = toSignal(this.route.paramMap.pipe(map((p) => p.get('type') ?? '')), {
    requireSync: true,
  });
  protected readonly typeName = this.params;
  protected readonly pageSizes = PAGE_SIZE_OPTIONS;

  readonly type = signal<TypeDetailResponse | null>(null);
  readonly rows = signal<readonly BusinessObjectResponse[]>([]);
  readonly total = signal(0);
  readonly facets = signal<Record<string, readonly FacetBucketResponse[]>>({});
  readonly error = signal<ApiError | null>(null);
  readonly filterErrors = signal<FilterErrors>({ byIndex: new Map(), unmatched: [] });

  readonly columns = computed(() => {
    const type = this.type();
    return type ? visibleColumns(type) : [];
  });
  readonly columnNames = computed(() => this.columns().map((c) => c.name));
  readonly facetNames = computed(() => {
    const type = this.type();
    return type ? facetableProperties(type) : [];
  });
  readonly activeSort = computed(() => this.query.state().sort[0]?.property ?? '');
  constructor() {
    // Reload whenever the type or the URL-backed browse state changes; facets need the type first.
    effect(() => {
      const type = this.type();
      const state = this.query.state();
      if (type !== null) {
        untracked(() => void this.load(state));
      }
    });
  }

  async ngOnInit(): Promise<void> {
    const type = await firstValueFrom(this.types.describe(this.typeName()));
    this.type.set(type);
  }

  isSortable(name: string): boolean {
    const property = this.type()?.properties.find((p) => p.name === name);
    return property !== undefined && queryCapabilities(property).sortable;
  }

  onFiltersChange(filters: readonly FilterClause[]): void {
    void this.query.update({ filters });
  }

  /** An empty term yields q undefined, which removes q from the URL; a new term resets the page. */
  submitSearch(event: Event, term: string): void {
    event.preventDefault();
    const trimmed = term.trim();
    void this.query.update({ q: trimmed === '' ? undefined : trimmed });
  }

  onPage(event: PageEvent): void {
    void this.query.update({ page: event.pageIndex, size: event.pageSize });
  }

  onSort(sort: Sort): void {
    const sorted = sort.direction === '' ? [] : [{ property: sort.active, direction: sort.direction }];
    void this.query.update({ sort: sorted });
  }

  private async load(state: BrowseQueryState): Promise<void> {
    try {
      const result = await firstValueFrom(
        this.objects.browse(this.typeName(), this.query.withFacets(state, this.facetNames())),
      );
      this.rows.set(result.items);
      this.total.set(result.totalItems);
      this.facets.set(result.facets);
      this.error.set(null);
      this.filterErrors.set({ byIndex: new Map(), unmatched: [] });
    } catch (err) {
      const error = err as ApiError;
      this.error.set(error);
      this.filterErrors.set(mapViolationsToFilters(state.filters, error));
    }
  }
}
