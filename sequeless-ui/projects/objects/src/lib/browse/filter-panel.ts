import { Component, computed, effect, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import {
  FacetBucketResponse,
  FilterClause,
  FilterOperator,
  isRelationshipProperty,
  RelationshipPropertyResponse,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { SqRelationshipPicker } from '@sequeless/ui-kit';

import { facetableProperties, operatorsFor, visibleColumns } from './browse-columns';
import { FilterErrors } from './violation-mapping';

const VALUELESS_OPERATORS: readonly FilterOperator[] = ['isnull', 'notnull'];

/**
 * Facets, active-filter chips, the add-filter form, the range control and the relationship picker for one
 * object type. Presentational: the filters come in as an input and every change leaves through
 * `filtersChange`; it never touches the URL (the browse view owns that).
 */
@Component({
  selector: 'sq-filter-panel',
  imports: [MatChipsModule, MatExpansionModule, SqRelationshipPicker],
  template: `
    @for (name of facetNames(); track name) {
      <mat-expansion-panel expanded class="facet" [attr.data-facet]="name">
        <mat-expansion-panel-header>{{ name }}</mat-expansion-panel-header>
        <mat-chip-listbox
          multiple
          [value]="selectedFacetValues(name)"
          (change)="onFacetChange(name, $event.value)"
        >
          @for (bucket of facetBuckets(name); track bucket.value) {
            <mat-chip-option [value]="bucket.value" [attr.data-value]="bucket.value">
              {{ bucket.value }} ({{ bucket.count }})
            </mat-chip-option>
          }
        </mat-chip-listbox>
      </mat-expansion-panel>
    }

    <mat-chip-set>
      @for (clause of filters(); track $index) {
        <mat-chip [attr.data-filter-index]="$index">
          {{ clause.property }} {{ clause.op }} {{ clause.value }}
          <button type="button" data-remove (click)="removeFilter($index)">×</button>
          @if (errors().byIndex.get($index); as message) {
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
      @if (relationshipProperty(); as property) {
        <sq-relationship-picker [property]="property" [control]="relationshipControl" data-add-relationship />
      } @else {
        <input data-add-value [value]="newValue()" (input)="newValue.set(valueOf($event))" />
      }
      <button type="button" data-add-filter [disabled]="!canAddFilter()" (click)="addCustomFilter()">
        Add filter
      </button>
      @if (canAddRange()) {
        <input data-range-from [value]="rangeFrom()" (input)="rangeFrom.set(valueOf($event))" />
        <input data-range-to [value]="rangeTo()" (input)="rangeTo.set(valueOf($event))" />
        <button type="button" data-add-range [disabled]="!canSubmitRange()" (click)="addRange()">
          Add range
        </button>
      }
    </div>
  `,
})
export class SqFilterPanel {
  readonly type = input.required<TypeDetailResponse>();
  readonly filters = input<readonly FilterClause[]>([]);
  readonly facets = input<Record<string, readonly FacetBucketResponse[]>>({});
  readonly errors = input<FilterErrors>({ byIndex: new Map(), unmatched: [] });
  readonly filtersChange = output<readonly FilterClause[]>();

  readonly newProperty = signal('');
  readonly newOperator = signal<FilterOperator>('eq');
  readonly newValue = signal('');
  readonly rangeFrom = signal('');
  readonly rangeTo = signal('');

  readonly columns = computed(() => visibleColumns(this.type()));
  readonly facetNames = computed(() => facetableProperties(this.type()));
  readonly newOperators = computed<readonly FilterOperator[]>(() => {
    const column = this.columns().find((c) => c.name === this.newProperty());
    return column ? operatorsFor(column) : [];
  });
  /** The chosen property when it is a relationship; `in` needs a multi-select picker, so it is shown unbounded. */
  readonly relationshipProperty = computed<RelationshipPropertyResponse | null>(() => {
    const column = this.columns().find((c) => c.name === this.newProperty());
    if (column === undefined || !isRelationshipProperty(column)) {
      return null;
    }
    return this.newOperator() === 'in' ? { ...column, cardinalityMax: null } : column;
  });
  readonly relationshipControl = new FormControl<string[]>([], { nonNullable: true });
  private readonly relationshipValue = toSignal(this.relationshipControl.valueChanges, {
    initialValue: this.relationshipControl.value,
  });
  /** The picker holds a bare id for a single-valued property and an array for a repeatable one. */
  private readonly relationshipIds = computed<readonly string[]>(() => {
    const value: unknown = this.relationshipValue();
    if (Array.isArray(value)) {
      return value as string[];
    }
    return typeof value === 'string' && value.length > 0 ? [value] : [];
  });
  readonly canAddFilter = computed(() => {
    if (VALUELESS_OPERATORS.includes(this.newOperator())) {
      return true;
    }
    return this.relationshipProperty() !== null
      ? this.relationshipIds().length > 0
      : this.newValue().length > 0;
  });
  readonly canAddRange = computed(() => this.newOperators().includes('gte'));
  readonly canSubmitRange = computed(() => this.rangeFrom().length > 0 && this.rangeTo().length > 0);

  constructor() {
    // The add-filter form starts on the first column and that column's first operator.
    effect(() => {
      const first = this.columns()[0];
      if (first && this.newProperty() === '') {
        this.newProperty.set(first.name);
        this.newOperator.set(operatorsFor(first)[0] ?? 'eq');
      }
    });
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
    this.relationshipControl.setValue([]);
    this.newOperator.set(column ? (operatorsFor(column)[0] ?? 'eq') : 'eq');
  }

  onOperatorChange(value: string): void {
    const op = this.newOperators().find((candidate) => candidate === value);
    if (op !== undefined) {
      this.newOperator.set(op);
    }
  }

  selectedFacetValues(name: string): string[] {
    return this.filters()
      .filter((c) => c.property === name && c.op === 'eq')
      .map((c) => String(c.value));
  }

  /** The listbox is the selection source: the eq clauses for this facet mirror what is selected. */
  onFacetChange(name: string, values: readonly string[]): void {
    const others = this.filters().filter((c) => !(c.property === name && c.op === 'eq'));
    const selected = values.map((value): FilterClause => ({ property: name, op: 'eq', value }));
    this.filtersChange.emit([...others, ...selected]);
  }

  addCustomFilter(): void {
    if (!this.canAddFilter()) {
      return;
    }
    const op = this.newOperator();
    const value = this.newValue();
    if (VALUELESS_OPERATORS.includes(op)) {
      this.addClauses([{ property: this.newProperty(), op }]);
    } else if (this.relationshipProperty() !== null) {
      const ids = this.relationshipIds();
      this.addClauses([{ property: this.newProperty(), op, value: op === 'eq' ? ids[0] : ids.join(',') }]);
      this.relationshipControl.setValue([]);
    } else {
      this.addClauses([{ property: this.newProperty(), op, value }]);
      this.newValue.set('');
    }
  }

  /** A range is one control that emits the gte and lte pair on the same property. */
  addRange(): void {
    const property = this.newProperty();
    this.addClauses([
      { property, op: 'gte', value: this.rangeFrom() },
      { property, op: 'lte', value: this.rangeTo() },
    ]);
    this.rangeFrom.set('');
    this.rangeTo.set('');
  }

  removeFilter(index: number): void {
    this.filtersChange.emit(this.filters().filter((_, i) => i !== index));
  }

  private addClauses(clauses: readonly FilterClause[]): void {
    this.filtersChange.emit([...this.filters(), ...clauses]);
  }
}
