import { Component, computed, effect, inject, signal, untracked, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  ApiError,
  BusinessObjectResponse,
  ObjectsClient,
  queryCapabilities,
  TypeDetailResponse,
  TypesClient,
} from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom, map } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';

import { PAGE_SIZE_OPTIONS, visibleColumns } from './browse-columns';
import { BrowseQueryStateService } from './browse-query-state.service';
import { FilterErrors, mapViolationsToFilters } from './violation-mapping';

/**
 * Generic table of one object type. Columns, sorting and paging all come from TypeDetailResponse and
 * the URL-backed BrowseQueryStateService; there is no per-type code. Facet and filter controls are
 * not yet built, so filters can only arrive through the URL.
 */
@Component({
  selector: 'sq-object-browse',
  imports: [MatButtonModule, MatPaginatorModule, MatSortModule, MatTableModule, RouterLink, SqProblemBanner],
  providers: [BrowseQueryStateService],
  template: `
    <h2>{{ typeName() }}</h2>
    <a mat-stroked-button [routerLink]="['/objects', typeName(), 'new']">New</a>
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
    @for (clause of query.state().filters; track $index) {
      @if (filterErrors().byIndex.get($index); as message) {
        <p role="alert">{{ clause.property }}: {{ message }}</p>
      }
    }
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
  readonly error = signal<ApiError | null>(null);
  readonly filterErrors = signal<FilterErrors>({ byIndex: new Map(), unmatched: [] });

  readonly columns = computed(() => (this.type() ? visibleColumns(this.type()!) : []));
  readonly columnNames = computed(() => this.columns().map((c) => c.name));
  readonly activeSort = computed(() => this.query.state().sort[0]?.property ?? '');

  constructor() {
    // Reload whenever the URL-backed browse state changes.
    effect(() => {
      const state = this.query.state();
      untracked(() => void this.load(state));
    });
  }

  async ngOnInit(): Promise<void> {
    this.type.set(await firstValueFrom(this.types.describe(this.typeName())));
  }

  isSortable(name: string): boolean {
    const property = this.type()?.properties.find((p) => p.name === name);
    return property !== undefined && queryCapabilities(property).sortable;
  }

  onPage(event: PageEvent): void {
    void this.query.update({ page: event.pageIndex, size: event.pageSize });
  }

  onSort(sort: Sort): void {
    const sorted = sort.direction === '' ? [] : [{ property: sort.active, direction: sort.direction }];
    void this.query.update({ sort: sorted });
  }

  private async load(state: ReturnType<BrowseQueryStateService['state']>): Promise<void> {
    try {
      const result = await firstValueFrom(this.objects.browse(this.typeName(), state));
      this.rows.set(result.items);
      this.total.set(result.totalItems);
      this.error.set(null);
      this.filterErrors.set({ byIndex: new Map(), unmatched: [] });
    } catch (err) {
      const error = err as ApiError;
      this.error.set(error);
      this.filterErrors.set(mapViolationsToFilters(state.filters, error));
    }
  }
}
