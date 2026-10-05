/**
 * `SqRelationshipPicker` (selector `sq-relationship-picker`) backs a `mat-autocomplete` with
 * the injected `RELATIONSHIP_LOOKUP`. On init it resolves
 * `lookup.targetType(property().targetTypeIri)` once, to build option/chip labels via
 * `pickLabel` from `../util/labels`. Typing into the native
 * `<input matInput [matAutocomplete] data-field>` is debounced 300ms before calling
 * `lookup.search(property().targetTypeIri, term)`; the resolved objects render as
 * `mat-option`s.
 *
 * Cardinality (via `isRepeatable`) decides the control shape: a single-valued relationship's
 * `control.value` is the bare target id string (re-selection overwrites it); a repeatable
 * relationship's `control.value` is a `string[]` — `select()` appends the id (skipping
 * duplicates), rendered as a `mat-chip-grid` of `<mat-chip-row data-id>` labelled from a label
 * cache populated at select time, so `remove(id)` never needs to re-fetch. The control only
 * ever holds bare id string(s), never the `BusinessObjectResponse` itself.
 */
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { FormControl } from '@angular/forms';
import { MatAutocompleteModule, type MatAutocomplete } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  type BusinessObjectResponse,
  type RelationshipPropertyResponse,
  type TypeDetailResponse,
} from '@sequeless/core-api';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { pickLabel } from '../util/labels';
import { isRepeatable } from '../util/validators';
import { RELATIONSHIP_LOOKUP } from './relationship-lookup';

@Component({
  selector: 'sq-relationship-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatAutocompleteModule, MatChipsModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline">
      <input
        matInput
        [matAutocomplete]="auto"
        [attr.data-field]="property().name"
        (input)="terms.next($any($event.target).value)"
      />
      <mat-autocomplete #auto (optionSelected)="select($event.option.value)">
        @for (o of options(); track o.id) {
          <mat-option [value]="o">{{ label(o) }}</mat-option>
        }
      </mat-autocomplete>
      @if (repeatable()) {
        <mat-chip-grid>
          @for (id of selectedIds(); track id) {
            <mat-chip-row [attr.data-id]="id" (removed)="remove(id)">
              {{ labels.get(id) ?? id }}
            </mat-chip-row>
          }
        </mat-chip-grid>
      }
    </mat-form-field>
  `,
})
export class SqRelationshipPicker {
  readonly property = input.required<RelationshipPropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  private readonly lookup = inject(RELATIONSHIP_LOOKUP);

  /** Type detail of the relationship target, resolved once on init. */
  readonly targetType = signal<TypeDetailResponse | null>(null);

  /** Resolved search results driving the `mat-option` list. */
  readonly options = signal<readonly BusinessObjectResponse[]>([]);

  /** Id -> display label cache, populated at select time so `remove` never re-fetches. */
  readonly labels = new Map<string, string>();

  /** Raw term stream from the native input; consumed by the debounced search pipeline. */
  readonly terms = new Subject<string>();

  readonly repeatable = computed(() => isRepeatable(this.property()));

  readonly selectedIds = computed<string[]>(() => {
    if (!this.repeatable()) return [];
    const value = this.control().value;
    return Array.isArray(value)
      ? value.filter((item): item is string => typeof item === 'string')
      : [];
  });

  constructor() {
    // Resolve the target type exactly once to power label building.
    let loaded = false;
    effect(() => {
      if (loaded) return;
      const iri = this.property().targetTypeIri;
      loaded = true;
      this.lookup.targetType(iri).pipe(takeUntilDestroyed()).subscribe({
        next: (detail) => this.targetType.set(detail),
      });
    });

    this.terms
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((term) => this.lookup.search(this.property().targetTypeIri, term)),
        takeUntilDestroyed(),
      )
      .subscribe((results) => this.options.set(results));
  }

  /** Display label for a resolved object: the cache if present, else `pickLabel`. */
  label(obj: BusinessObjectResponse): string {
    return this.labels.get(obj.id) ?? this.freshLabel(obj);
  }

  private freshLabel(obj: BusinessObjectResponse): string {
    const detail = this.targetType();
    return detail !== null ? pickLabel(obj, detail) : obj.id;
  }

  /** Caches the object's label and records its id in the control (string or string[]). */
  select(obj: BusinessObjectResponse): void {
    this.labels.set(obj.id, this.freshLabel(obj));
    if (this.repeatable()) {
      const current = this.control().value;
      const ids = Array.isArray(current)
        ? current.filter((item): item is string => typeof item === 'string')
        : [];
      if (!ids.includes(obj.id)) {
        this.control().setValue([...ids, obj.id]);
      }
    } else {
      this.control().setValue(obj.id);
    }
  }

  /** Drops a single id from the repeatable control value; no lookup calls. */
  remove(id: string): void {
    const current = this.control().value;
    if (!Array.isArray(current)) return;
    this.control().setValue(current.filter((item) => item !== id));
  }
}
