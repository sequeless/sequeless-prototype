import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
} from '@angular/core';
import {
  FormArray,
  FormControl,
  ReactiveFormsModule,
  Validators,
  type AbstractControl,
} from '@angular/forms';
import { NgComponentOutlet } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FIELD_RENDERERS, type PropertyResponse } from '@sequeless/core-api';

import { DEFAULT_FIELD_RENDERERS } from './default-field-renderers';
import { validatorsFor } from '../util/validators';

@Component({
  selector: 'sq-dynamic-field',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, NgComponentOutlet],
  template: `
    @if (!property().hidden) {
      @if (items(); as list) {
        @for (c of list; track $index) {
          <div [attr.data-index]="$index">
            <ng-container *ngComponentOutlet="renderer(); inputs: { property: property(), control: c }" />
            <button type="button" data-action="remove" (click)="removeAt($index)">Remove</button>
          </div>
        }
        <button type="button" data-action="add" (click)="add()">Add</button>
      } @else {
        <ng-container *ngComponentOutlet="renderer(); inputs: { property: property(), control: control() }" />
      }

      @if (property().readOnly && property().derivation) {
        <mat-hint data-hint="derivation">{{ property().derivation!.summary }}</mat-hint>
      }
    }
  `,
})
export class SqDynamicField {
  readonly property = input.required<PropertyResponse>();
  readonly control = input.required<AbstractControl>();

  readonly renderers = {
    ...DEFAULT_FIELD_RENDERERS,
    ...(inject(FIELD_RENDERERS, { optional: true }) ?? {}),
  };

  readonly renderer = computed(() => {
    const p = this.property();
    const key = p.kind === 'attribute' ? p.datatype : 'RELATIONSHIP';
    return this.renderers[key] ?? null;
  });

  readonly items = computed<AbstractControl[] | null>(() => {
    const control = this.control();
    return control instanceof FormArray ? [...control.controls] : null;
  });

  constructor() {
    effect(() => {
      if (this.property().readOnly && this.control().enabled) {
        this.control().disable();
      }
    });
  }

  add(): void {
    const control = this.control();
    if (!(control instanceof FormArray)) {
      return;
    }
    // Item validators without the cardinality `required` check: reuse
    // `validatorsFor` and drop `Validators.required`.
    const itemValidators = validatorsFor(this.property()).filter(
      (v) => v !== Validators.required,
    );
    control.push(new FormControl(null, { validators: itemValidators }));
  }

  removeAt(index: number): void {
    const control = this.control();
    if (control instanceof FormArray) {
      control.removeAt(index);
    }
  }
}
