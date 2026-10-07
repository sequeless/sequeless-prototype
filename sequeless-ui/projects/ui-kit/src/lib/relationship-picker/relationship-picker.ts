/**
 * `SqRelationshipPicker` (selector `sq-relationship-picker`) renders a Material
 * autocomplete + chip grid for a single relationship `PropertyResponse`.
 *
 * Cardinality (via `isRepeatable` in `../util/validators`) decides the shape:
 * a single-valued relationship holds the bare target id string in the
 * `FormControl`; a repeatable one holds a `string[]`. Option/chip labels are
 * resolved with `pickLabel` from `../util/labels`, falling back to the raw id.
 */
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import type { FormControl } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  type BusinessObjectResponse,
  type RelationshipPropertyResponse,
  type TypeDetailResponse,
} from '@sequeless/core-api';
import type { Observable } from 'rxjs';
import { RELATIONSHIP_LOOKUP, type RelationshipLookup } from './relationship-lookup';
import { pickLabel } from '../util/labels';
import { isRepeatable } from '../util/validators';

interface DebounceTimerHandle {
  id: number;
}

function scheduleTimer(delay: number, fn: () => void): DebounceTimerHandle {
  const id = setTimeout(fn, delay) as unknown as number;
  return { id };
}

function cancelTimer(handle: DebounceTimerHandle | null): void {
  if (handle) {
    clearTimeout(handle.id);
  }
}

interface SearchSubscription {
  unsubscribe(): void;
}

interface TargetTypeSubscription {
  unsubscribe(): void;
}

@Component({
  selector: 'sq-relationship-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatAutocompleteModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <div data-probe="true"></div>
    @if (isRepeatable()) {
      <mat-chip-grid>
        @for (id of currentIds(); track id) {
          <mat-chip-row [attr.data-id]="id">{{ labelForId(id) }}</mat-chip-row>
        }
      </mat-chip-grid>
    }
    <mat-form-field appearance="outline">
      <mat-label>{{ property().label }}</mat-label>
      <input
        matInput
        [matAutocomplete]="auto"
        [attr.data-field]="property().name"
        (input)="onInput($event)"
      />
      <mat-autocomplete #auto>
        @for (obj of searchResults(); track obj.id) {
          <mat-option [value]="obj">{{ optionLabel(obj) }}</mat-option>
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
})
export class SqRelationshipPicker {
  readonly property = input.required<RelationshipPropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  readonly targetType = signal<TypeDetailResponse | null>(null);
  readonly loadedIri = signal<string | null>(null);
  readonly searchResults = signal<readonly BusinessObjectResponse[]>([]);
  private readonly labelCache = new Map<string, string>();

  readonly isRepeatable = computed(() => isRepeatable(this.property()));

  readonly currentIds = computed<readonly string[]>(() => {
    const value = this.control().value;
    return Array.isArray(value) ? (value as readonly string[]) : [];
  });

  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly lookup: RelationshipLookup = inject(RELATIONSHIP_LOOKUP);
  private debounce: DebounceTimerHandle | null = null;
  private searchSub: SearchSubscription | null = null;
  private targetTypeSub: TargetTypeSubscription | null = null;

  constructor() {
    effect(() => {
      const iri = this.property().targetTypeIri;
      if (this.loadedIri() === iri) {
        return;
      }
      const stream: Observable<TypeDetailResponse> = this.lookup.targetType(iri);
      this.targetTypeSub?.unsubscribe();
      this.targetTypeSub = stream.subscribe({
        next: (detail) => {
          this.targetType.set(detail);
          this.loadedIri.set(iri);
        },
        error: () => {
          this.loadedIri.set(iri);
        },
      });
    });

    this.destroyRef.onDestroy(() => {
      cancelTimer(this.debounce);
      this.debounce = null;
      this.searchSub?.unsubscribe();
      this.searchSub = null;
      this.targetTypeSub?.unsubscribe();
      this.targetTypeSub = null;
    });
  }

  onInput(event: Event): void {
    const el = event.target as HTMLInputElement | null;
    if (!el) {
      return;
    }
    const term = el.value;
    cancelTimer(this.debounce);
    this.debounce = scheduleTimer(300, () => {
      this.debounce = null;
      const iri = this.property().targetTypeIri;
      const stream: Observable<readonly BusinessObjectResponse[]> =
        this.lookup.search(iri, term);
      this.searchSub?.unsubscribe();
      this.searchSub = stream.subscribe({
        next: (results) => {
          this.searchResults.set(results);
        },
      });
    });
  }

  select(obj: BusinessObjectResponse): void {
    if (this.isRepeatable()) {
      const current = (this.control().value as readonly string[] | null) ?? [];
      if (!current.includes(obj.id)) {
        this.control().setValue([...current, obj.id]);
      }
      if (!this.labelCache.has(obj.id)) {
        this.labelCache.set(obj.id, this.optionLabel(obj));
      }
    } else {
      this.control().setValue(obj.id);
    }
  }

  remove(id: string): void {
    if (!this.isRepeatable()) {
      return;
    }
    const current = (this.control().value as readonly string[] | null) ?? [];
    this.control().setValue(current.filter((existing) => existing !== id));
  }

  labelForId(id: string): string {
    const cached = this.labelCache.get(id);
    if (cached !== undefined) {
      return cached;
    }
    return id;
  }

  optionLabel(obj: BusinessObjectResponse): string {
    const detail = this.targetType();
    if (detail) {
      return pickLabel(obj, detail);
    }
    return obj.id;
  }
}
