import { ChangeDetectionStrategy, Component, computed, inject, input, output, resource } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TypesClient } from '@sequeless/core-api';
import type { ApiError } from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

import { relationshipWatchOptions } from './trigger-form';

/** Which of the type's own relationships an on-change transition watches. */
@Component({
  selector: 'sq-on-change-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatSelectModule, SqProblemBanner],
  template: `
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    }
    <mat-form-field>
      <mat-label>Watch</mat-label>
      <mat-select multiple [value]="value()" (selectionChange)="valueChange.emit($event.value)">
        @for (option of options(); track option.iri) {
          <mat-option [value]="option.iri">{{ option.label }}</mat-option>
        }
      </mat-select>
      <mat-hint>Leave empty to fire when the object itself changes.</mat-hint>
    </mat-form-field>
  `,
})
export class SqOnChangeField {
  readonly typeIri = input.required<string>();
  /** The watched property IRIs. */
  readonly value = input<readonly string[]>([]);
  readonly valueChange = output<string[]>();

  private readonly types = inject(TypesClient);
  private readonly type = resource({
    params: () => this.typeIri(),
    loader: ({ params }) => firstValueFrom(this.types.describe(params)),
  });

  protected readonly options = computed(() => {
    const type = this.type.hasValue() ? this.type.value() : undefined;
    return type ? relationshipWatchOptions(type) : [];
  });
  protected readonly error = computed(() => {
    const failure = this.type.error() as (Error & { cause?: unknown }) | undefined;
    return failure ? ((failure.cause ?? failure) as ApiError) : null;
  });
}
