import { Component, computed, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
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
  FilterOperator,
  ObjectsClient,
  queryCapabilities,
  TypeDetailResponse,
  TypesClient,
} from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom, map } from 'rxjs';

import { facetableProperties, operatorsFor, PAGE_SIZE_OPTIONS, visibleColumns } from './browse-columns';
import { BrowseQueryStateService } from './browse-query-state.service';
import { FilterErrors, mapViolationsToFilters } from './violation-mapping';

const VALUELESS_OPERATORS: readonly FilterOperator[] = ['isnull', 'notnull'];

/**
 * Generic table of one object type. Columns, facets, operators and paging all derive from
 * TypeDetailResponse; there is no per-type code. Every change to the query goes through the URL via
 * BrowseQueryStateService.
 */
@Component({
  selector: 'sq-object-browse',
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatPaginatorModule,
    MatSortModule,
    MatTableModule,
    RouterLink,
    SqProblemBanner,
  ],
  providers: [BrowseQueryStateService],
  template: `
    <h2>{{ typeName() }}</h2>
    <a mat-stroked-button [routerLink]="['/objects', typeName(), 'new']">New</a>

    @for (name of facetNames(); track name) {
      <div class="facet" [attr.data-facet]="name">
        <span>{{ name }}</span>
        <mat-chip-listbox>
          @for (bucket of facetBuckets(name); track bucket.value) {
            <mat-chip-option [attr.data-value]="bucket.value" (click)="addFilter(name, bucket.value)">
              {{ bucket.value }} ({{ bucket.count }})
            </mat-chip-option>
          }
        </mat-chip-listbox>
      </div>
    }

    <mat-chip-set>
      @for (clause of query.state().filters; track $index) {
        <mat-chip [attr.data-filter-index]="$index">
          {{ clause.property }} {{ clause.op }} {{ clause.value }}
          <button type="button" data-remove (click)="removeFilter($index)">×</button>
          @if (filterErrors().byIndex.get($index); as message) {
            <span role="alert">{{ message }}</span>
          }
        </mat-chip>
      }
    </mat-chip-set>

    <div class="add-filter">
      <select data-add-property [value]="newProperty()" (change)="onPropertyChange(valueOf($event))">
        @for (column of columns(); track column.name) {
          <option [value]="column.name">{{ column.name }}</option>
        }
      </select>
      <select data-add-operator [value]="newOperator()" (change)="onOperatorChange(valueOf($event))">
        @for (op of newOperators(); track op) {
          <option [value]="op">{{ op }}</option>
        }
      </select>
      <input data-add-value [value]="newValue()" (input)="newValue.set(valueOf($event))" />
      <button type="button" (click)="addCustomFilter()">Add filter</button>
    </div>

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

  readonly newProperty = signal('');
  readonly newOperator = signal<FilterOperator>('eq');
  readonly newValue = signal('');

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
  readonly newOperators = computed<readonly FilterOperator[]>(() => {
    const column = this.columns().find((c) => c.name === this.newProperty());
    return column ? operatorsFor(column) : [];
  });

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
    const first = visibleColumns(type)[0];
    if (first) {
      this.newProperty.set(first.name);
      this.newOperator.set(operatorsFor(first)[0] ?? 'eq');
    }
  }

  isSortable(name: string): boolean {
    const property = this.type()?.properties.find((p) => p.name === name);
    return property !== undefined && queryCapabilities(property).sortable;
  }

  facetBuckets(name: string): readonly FacetBucketResponse[] {
    return this.facets()[name] ?? [];
  }

  valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  onPropertyChange(name: string): void {
    const column = this.columns().find((c) => c.name === name);
    this.newProperty.set(name);
    this.newOperator.set(column ? (operatorsFor(column)[0] ?? 'eq') : 'eq');
  }

  onOperatorChange(value: string): void {
    const op = this.newOperators().find((candidate) => candidate === value);
    if (op !== undefined) {
      this.newOperator.set(op);
    }
  }

  addFilter(property: string, value: string): void {
    this.addClause({ property, op: 'eq', value });
  }

  addCustomFilter(): void {
    const op = this.newOperator();
    const value = this.newValue();
    if (VALUELESS_OPERATORS.includes(op)) {
      this.addClause({ property: this.newProperty(), op });
    } else if (value.length > 0) {
      this.addClause({ property: this.newProperty(), op, value });
      this.newValue.set('');
    }
  }

  removeFilter(index: number): void {
    const filters = this.query.state().filters.filter((_, i) => i !== index);
    void this.query.update({ filters });
  }

  onPage(event: PageEvent): void {
    void this.query.update({ page: event.pageIndex, size: event.pageSize });
  }

  onSort(sort: Sort): void {
    const sorted = sort.direction === '' ? [] : [{ property: sort.active, direction: sort.direction }];
    void this.query.update({ sort: sorted });
  }

  private addClause(clause: FilterClause): void {
    void this.query.update({ filters: [...this.query.state().filters, clause] });
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
