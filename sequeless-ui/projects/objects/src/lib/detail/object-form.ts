import { Component, computed, input } from '@angular/core';
import { AbstractControl, FormArray, FormControl, FormGroup } from '@angular/forms';
import { MatExpansionModule } from '@angular/material/expansion';
import {
  isAttributeProperty,
  type PropertyResponse,
  type TypeDetailResponse,
} from '@sequeless/core-api';
import { controlFor, SqDynamicField, toWireValue } from '@sequeless/ui-kit';

import { visibleColumns } from '../browse/browse-columns';

export interface PropertyGroup {
  group: string | null;
  properties: PropertyResponse[];
}

/** Visible, writable, non-derived properties, in display order. */
export function editableProperties(type: TypeDetailResponse): PropertyResponse[] {
  return visibleColumns(type).filter((p) => !p.readOnly && p.derivation === null);
}

/**
 * Visible properties bucketed by `group`. The ungrouped bucket comes first, then each named group
 * in the order its first member appears. Empty buckets are omitted.
 */
export function groupProperties(type: TypeDetailResponse): PropertyGroup[] {
  const buckets = new Map<string | null, PropertyResponse[]>();
  for (const property of visibleColumns(type)) {
    const key = property.group;
    const bucket = buckets.get(key);
    if (bucket) {
      bucket.push(property);
    } else {
      buckets.set(key, [property]);
    }
  }

  const groups = [...buckets].map(([group, properties]) => ({ group, properties }));
  return groups.sort((a, b) => (a.group === null ? -1 : b.group === null ? 1 : 0));
}

/** One control per visible property, seeded from `initial`; readOnly controls are disabled. */
export function buildObjectForm(
  type: TypeDetailResponse,
  initial: Record<string, unknown> = {},
): FormGroup {
  const controls: Record<string, AbstractControl> = {};
  for (const property of type.properties.filter((p) => !p.hidden)) {
    controls[property.name] = controlFor(property, initial[property.name]);
  }
  return new FormGroup(controls);
}

/** Inverse of buildObjectForm, restricted to editable properties and coerced to wire values. */
export function formToProperties(
  type: TypeDetailResponse,
  form: FormGroup,
): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const property of editableProperties(type)) {
    result[property.name] = wireValueOf(property, form.controls[property.name]);
  }
  return result;
}

function wireValueOf(property: PropertyResponse, control: AbstractControl | undefined): unknown {
  if (!isAttributeProperty(property)) {
    return control?.value;
  }
  if (control instanceof FormArray) {
    return control.controls.map((item) => toWireValue(property.datatype, item.value));
  }
  return toWireValue(property.datatype, control?.value);
}

@Component({
  selector: 'sq-object-form',
  imports: [MatExpansionModule, SqDynamicField],
  template: `
    @for (property of ungrouped(); track property.name) {
      <sq-dynamic-field [property]="property" [control]="controlOf(property)" />
    }
    @for (bucket of namedGroups(); track bucket.group) {
      <mat-expansion-panel>
        <mat-expansion-panel-header>{{ bucket.group }}</mat-expansion-panel-header>
        @for (property of bucket.properties; track property.name) {
          <sq-dynamic-field [property]="property" [control]="controlOf(property)" />
        }
      </mat-expansion-panel>
    }
  `,
})
export class SqObjectForm {
  readonly type = input.required<TypeDetailResponse>();
  readonly form = input.required<FormGroup>();

  protected readonly groups = computed(() => groupProperties(this.type()));
  protected readonly ungrouped = computed(
    () => this.groups().find((g) => g.group === null)?.properties ?? [],
  );
  protected readonly namedGroups = computed(() => this.groups().filter((g) => g.group !== null));

  protected controlOf(property: PropertyResponse): AbstractControl {
    return this.form().get(property.name) ?? new FormControl(null);
  }
}
