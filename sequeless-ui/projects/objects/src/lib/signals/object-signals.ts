import { Component, computed, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  ApiError,
  BusinessObjectResponse,
  ObjectsClient,
  TypeDetailResponse,
} from '@sequeless/core-api';
import { SqProblemBanner } from '@sequeless/ui-kit';
import { firstValueFrom } from 'rxjs';

/**
 * Fires the signals that the object's type says apply from its current state. The catalogue of all
 * signals belongs to another feature; this panel only offers the ones known for this object.
 */
@Component({
  selector: 'sq-object-signals',
  imports: [MatButtonModule, SqProblemBanner],
  template: `
    @for (name of signalNames(); track name) {
      <button mat-stroked-button type="button" [attr.data-signal]="name" (click)="fire(name)">
        {{ name }}
      </button>
    }
    @if (dispatched(); as name) {
      <p role="status">Signal "{{ name }}" dispatched — takes effect asynchronously.</p>
    }
    @if (error(); as error) {
      <sq-problem-banner [problems]="error" />
    }
    <button mat-button type="button" (click)="refresh.emit()">Refresh</button>
  `,
})
export class SqObjectSignals {
  private readonly objects = inject(ObjectsClient);

  readonly type = input.required<TypeDetailResponse>();
  readonly object = input.required<BusinessObjectResponse>();
  readonly refresh = output<void>();

  readonly dispatched = signal<string | null>(null);
  readonly error = signal<ApiError | null>(null);

  protected readonly signalNames = computed(() => {
    const transitions = this.type().stateMachine?.transitions ?? [];
    const names = transitions
      .filter((t) => t.trigger === 'EXTERNAL_SIGNAL' && t.from === this.object().state)
      .flatMap((t) => (t.signalName ? [t.signalName] : []));
    return [...new Set(names)];
  });

  async fire(name: string): Promise<void> {
    this.dispatched.set(null);
    this.error.set(null);
    try {
      await firstValueFrom(this.objects.signal(this.type().name, this.object().id, name));
      this.dispatched.set(name);
    } catch (err) {
      this.error.set(err as ApiError);
    }
  }
}
