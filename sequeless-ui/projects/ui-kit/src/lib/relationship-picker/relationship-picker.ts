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
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type { FormControl } from '@angular/forms';
import { MatAutocompleteModule, MatOption } from '@angular/material/autocomplete';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  type BusinessObjectResponse,
  type RelationshipPropertyResponse,
  type TypeDetailResponse,
} from '@sequeless/core-api';
import type { Observable } from 'rxjs';
import { Subscription } from 'rxjs';
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

@Component({
  selector: 'sq-relationship-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatAutocompleteModule, MatChipsModule, MatFormFieldModule, MatInputModule, MatOption],
  template: `
    <!-- Chip grid (repeatable only), driven by a signal that mirrors the control value. -->
    @if (isRepeatable()) {
      <mat-chip-grid>
        @for (id of currentIds(); track id) {
          <mat-chip-row [attr.data-id]="id">
            {{ labelForId(id) }}
            <button
              matChipRemove
              data-action="remove-chip"
              [attr.aria-label]="'Remove'"
              (click)="$event.stopPropagation(); remove(id)"
            >
              ×
            </button>
          </mat-chip-row>
        }
      </mat-chip-grid>
    }

    <!-- Input + form-field. -->
    <mat-form-field appearance="outline">
      <mat-label>{{ property().label }}</mat-label>
      <input
        #searchInput
        matInput
        [matAutocomplete]="auto"
        [attr.data-field]="property().name"
        (input)="onInput($event)"
      />
    </mat-form-field>

    <!--
      The resulting options are rendered here (in the component's own template), not only
      inside the overlay, so they are reachable from the component's DOM regardless of focus.
      MatOption is a standalone component and can be rendered independent of MatAutocomplete.
    -->
    <div role="listbox" class="sq-relationship-picker-results">
      @for (obj of searchResults(); track obj.id) {
        <mat-option [value]="obj">{{ optionLabel(obj) }}</mat-option>
      }
    </div>

    <!-- The autocomplete panel. Options rendered here live in the CDK overlay (attached on focus). -->
    <mat-autocomplete #auto (optionSelected)="select($event.option.value)">
      @for (obj of searchResults(); track obj.id) {
        <mat-option [value]="obj">{{ optionLabel(obj) }}</mat-option>
      }
    </mat-autocomplete>
  `,
})
export class SqRelationshipPicker {
  readonly property = input.required<RelationshipPropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();

  readonly searchInput = viewChild.required<ElementRef<HTMLInputElement>>('searchInput');

  readonly targetType = signal<TypeDetailResponse | null>(null);
  readonly loadedIri = signal<string | null>(null);
  readonly searchResults = signal<readonly BusinessObjectResponse[]>([]);
  private readonly labelCache = new Map<string, string>();

  readonly isRepeatable = computed(() => isRepeatable(this.property()));

  /**
   * Signal that mirrors `control.value`. We subscribe to `control.valueChanges` so that this
   * signal is written whenever the FormControl's value changes, which in turn makes
   * `currentIds` (a computed over this signal) react to `setValue()` calls made from `select()`
   * and `remove()` without Angular's plain `FormControl.value` getter being invalidated.
   */
  private readonly currentValue = signal<unknown>(null);

  readonly currentIds = computed<readonly string[]>(() => {
    const value = this.currentValue();
    return Array.isArray(value) ? (value as readonly string[]) : [];
  });

  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  // Optional: without a lookup the picker renders its chips but cannot search or label options.
  private readonly lookup: RelationshipLookup | null = inject(RELATIONSHIP_LOOKUP, { optional: true });
  private debounce: DebounceTimerHandle | null = null;
  private searchSub: Subscription | null = null;
  private targetTypeSub: Subscription | null = null;
  private valueChangesSub: Subscription | null = null;

  constructor() {
    // Load the target type once per iri, to build option/chip labels via pickLabel.
    effect(() => {
      const iri = this.property().targetTypeIri;
      if (this.loadedIri() === iri) {
        return;
      }
      if (this.lookup === null) {
        this.loadedIri.set(iri);
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

    // Mirror the FormControl's value into a signal so the chip grid reacts to setValue/remove.
    effect(() => {
      const control = this.control();
      this.valueChangesSub?.unsubscribe();
      this.currentValue.set(control.value);
      this.valueChangesSub = control.valueChanges.subscribe((next) => {
        this.currentValue.set(next);
      });
    });

    this.destroyRef.onDestroy(() => {
      cancelTimer(this.debounce);
      this.debounce = null;
      this.searchSub?.unsubscribe();
      this.searchSub = null;
      this.targetTypeSub?.unsubscribe();
      this.targetTypeSub = null;
      this.valueChangesSub?.unsubscribe();
      this.valueChangesSub = null;
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
      const lookup = this.lookup;
      if (lookup === null) {
        return;
      }
      const iri = this.property().targetTypeIri;
      const stream: Observable<readonly BusinessObjectResponse[]> = lookup.search(iri, term);
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
    } else {
      this.control().setValue(obj.id);
    }
    if (!this.labelCache.has(obj.id)) {
      this.labelCache.set(obj.id, this.optionLabel(obj));
    }
    this.clearSearchInput();
  }

  private clearSearchInput(): void {
    this.searchInput().nativeElement.value = '';
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
