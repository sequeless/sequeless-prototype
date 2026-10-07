import { ChangeDetectionStrategy, Component, effect, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { FormControl } from '@angular/forms';
import type { BusinessObjectResponse, RelationshipPropertyResponse } from '@sequeless/core-api';

@Component({
  selector: 'sq-relationship-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div data-probe="true"></div>`,
})
export class SqRelationshipPicker {
  readonly property = input.required<RelationshipPropertyResponse>();
  readonly control = input.required<FormControl<unknown>>();
  readonly name = signal('');
  select(_obj: BusinessObjectResponse): void {}
  remove(_id: string): void {}

  constructor() {
    try {
      console.log('[DIAG] entering constructor');
      effect(() => {
        console.log('[DIAG] effect running, property =', this.property()?.name);
      });
      console.log('[DIAG] ctor done');
    } catch (e) {
      console.log('[DIAG] ctor error', e);
    }
  }
}
